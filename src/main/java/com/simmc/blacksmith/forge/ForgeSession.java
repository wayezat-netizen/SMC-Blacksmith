package com.simmc.blacksmith.forge;

import org.bukkit.Location;

import java.util.*;

/**
 * Manages state for an active forging session.
 */
public class ForgeSession {

    // ==================== TIMING CONSTANTS (CONFIGURABLE) ====================

    // Point spawn timing
    private static final long INITIAL_INTERVAL = 1500;   // 1.5 seconds between spawns (was 1.8)
    private static final long MIN_INTERVAL = 800;        // 0.8 second minimum (was 1.0)
    private static final long POINT_DURATION = 2200;     // 2.2 seconds visible (was 2.5)
    private static final int SPEEDUP_EVERY_N_HITS = 4;   // Speed up every 4 hits (was 5)
    private static final long INTERVAL_DECREASE = 60;    // Decrease by 60ms each time (was 50)

    // Timeout constants
    private static final long INACTIVITY_TIMEOUT_MS = 25000;    // 25 seconds no activity (was 30)
    private static final long MAX_SESSION_DURATION_MS = 180000; // 3 minutes max (was 5)
    private static final long NO_HIT_TIMEOUT_MS = 12000;        // 12 seconds with NO hits (was 15)
    private static final int MAX_MISSED_POINTS = 4;             // Auto-fail after 4 consecutive misses (was 5)

    // Speed scoring thresholds
    private static final long FAST_HIT_MS = 350;    // Fast hit threshold (was 400)
    private static final long NORMAL_HIT_MS = 700;  // Normal hit threshold (was 800)

    private final UUID playerId;
    private final ForgeRecipe recipe;
    private final Location anvilLocation;
    private final Random random;
    private final long startTime;

    // Pre-allocated lists
    private final List<ForgePoint> activePoints;
    private final List<Double> hitAccuracies;
    private final List<Long> hitTimes;

    // Session state
    private long lastPointSpawn;
    private long currentInterval;
    private int hitsCompleted;
    private int perfectHits;
    private int missedPoints;
    private int consecutiveMisses;
    private int currentFrame;
    private boolean active;

    // Timeout tracking
    private long lastActivityTime;
    private long firstHitTime;
    private boolean hasFirstHit;
    private boolean timedOut;

    // Hammer bonuses
    private double hammerSpeedBonus;
    private double hammerAccuracyBonus;

    // Hit target position settings (configurable)
    private double hitTargetOffsetY;
    private double hitTargetSpreadX;
    private double hitTargetSpreadZ;

    public ForgeSession(UUID playerId, ForgeRecipe recipe, Location anvilLocation) {
        this(playerId, recipe, anvilLocation, 1.0, 0.6, 0.4);
    }

    public ForgeSession(UUID playerId, ForgeRecipe recipe, Location anvilLocation,
                        double hitTargetOffsetY, double hitTargetSpreadX, double hitTargetSpreadZ) {
        this.playerId = playerId;
        this.recipe = recipe;
        this.anvilLocation = anvilLocation.clone();
        this.random = new Random();
        this.startTime = System.currentTimeMillis();

        // Hit target position settings
        this.hitTargetOffsetY = hitTargetOffsetY;
        this.hitTargetSpreadX = hitTargetSpreadX;
        this.hitTargetSpreadZ = hitTargetSpreadZ;

        // Pre-allocate with expected capacity
        this.activePoints = new ArrayList<>(4);
        this.hitAccuracies = new ArrayList<>(recipe.getHits());
        this.hitTimes = new ArrayList<>(recipe.getHits());

        this.lastPointSpawn = startTime;
        this.currentInterval = INITIAL_INTERVAL;

        this.hitsCompleted = 0;
        this.perfectHits = 0;
        this.missedPoints = 0;
        this.consecutiveMisses = 0;
        this.currentFrame = 0;
        this.active = true;

        // Initialize timeout tracking
        this.lastActivityTime = startTime;
        this.firstHitTime = 0;
        this.hasFirstHit = false;
        this.timedOut = false;

        this.hammerSpeedBonus = 0.0;
        this.hammerAccuracyBonus = 0.0;
    }

    // ==================== TICK ====================

    public void tick() {
        if (!active) return;

        long now = System.currentTimeMillis();

        //Check all timeout conditions
        if (checkTimeout(now)) {
            return;
        }

        updateActivePoints();

        if (shouldSpawnPoints(now)) {
            spawnPoint();
            lastPointSpawn = now;
            updateRhythm();
        }
    }

    /**
     * Comprehensive timeout checking.
     */
    private boolean checkTimeout(long now) {
        // Check max session duration (3 minutes)
        if (now - startTime > MAX_SESSION_DURATION_MS) {
            timedOut = true;
            active = false;
            return true;
        }

        // Check if player never started hitting (12 second grace period)
        if (!hasFirstHit && (now - startTime > NO_HIT_TIMEOUT_MS)) {
            timedOut = true;
            active = false;
            return true;
        }

        // Check inactivity timeout (only after first hit)
        if (hasFirstHit) {
            long timeSinceActivity = now - lastActivityTime;
            if (timeSinceActivity > INACTIVITY_TIMEOUT_MS) {
                timedOut = true;
                active = false;
                return true;
            }
        }

        // Auto-fail after too many consecutive misses
        if (consecutiveMisses >= MAX_MISSED_POINTS) {
            timedOut = true;
            active = false;
            return true;
        }

        return false;
    }

    private void updateActivePoints() {
        Iterator<ForgePoint> iterator = activePoints.iterator();
        while (iterator.hasNext()) {
            ForgePoint point = iterator.next();
            point.tick();

            if (point.isExpired() && !point.isHit()) {
                missedPoints++;
                consecutiveMisses++;
                // Update activity time on miss (player is engaged but missing)
                lastActivityTime = System.currentTimeMillis();
                point.remove();
                iterator.remove();
            }
        }
    }

    private boolean shouldSpawnPoints(long now) {
        // Only spawn if no active points and session not complete
        return now - lastPointSpawn >= currentInterval
                && hitsCompleted < recipe.getHits()
                && activePoints.isEmpty()
                && active;
    }

    private void spawnPoint() {
        Location pointLoc = generatePointLocation();
        ForgePoint point = new ForgePoint(pointLoc, POINT_DURATION);
        point.spawn();
        activePoints.add(point);
    }

    private void updateRhythm() {
        if (hitsCompleted > 0 && hitsCompleted % SPEEDUP_EVERY_N_HITS == 0) {
            currentInterval = Math.max(MIN_INTERVAL, currentInterval - INTERVAL_DECREASE);
        }
    }

    private Location generatePointLocation() {
        double offsetX = (random.nextDouble() - 0.5) * hitTargetSpreadX;
        double offsetZ = (random.nextDouble() - 0.5) * hitTargetSpreadZ;

        return anvilLocation.clone().add(0.5 + offsetX, hitTargetOffsetY, 0.5 + offsetZ);
    }

    // ==================== HIT PROCESSING ====================

    public double processHit(UUID hitboxId) {
        long hitTime = System.currentTimeMillis();
        lastActivityTime = hitTime;

        // Track first hit for timeout logic
        if (!hasFirstHit) {
            hasFirstHit = true;
            firstHitTime = hitTime;
        }

        for (int i = 0; i < activePoints.size(); i++) {
            ForgePoint point = activePoints.get(i);
            if (point.matchesHitbox(hitboxId) && point.isActive()) {
                double accuracy = point.hit();

                accuracy = Math.min(1.0, accuracy + hammerAccuracyBonus);

                hitAccuracies.add(accuracy);
                hitTimes.add(hitTime);
                hitsCompleted++;

                if (accuracy >= 0.9) {
                    perfectHits++;
                }

                // Reset consecutive miss counter on successful hit
                consecutiveMisses = 0;

                updateFrame();
                point.remove();
                activePoints.remove(i);

                return accuracy;
            }
        }
        return -1;
    }

    private void updateFrame() {
        currentFrame = recipe.getFrameForProgress(getProgress());
    }

    // ==================== SCORING ====================

    public int calculateStarRating() {
        if (timedOut && hitsCompleted == 0) {
            return 0;
        }

        // Check custom thresholds first
        Map<Integer, StarThreshold> thresholds = recipe.getStarThresholds();
        if (thresholds != null && !thresholds.isEmpty()) {
            double avg = getAverageAccuracy();
            for (int star = 5; star >= 0; star--) {
                StarThreshold t = thresholds.get(star);
                if (t != null && t.isMet(perfectHits, avg)) {
                    return star;
                }
            }
            return 0;
        }

        // Default scoring system
        double combinedScore = calculateFinalScore();

        // Penalty for missed points (reduced penalty for fairness)
        double missedPenalty = missedPoints * 0.03;
        combinedScore = Math.max(0, combinedScore - missedPenalty);

        // Timeout penalty
        if (timedOut) {
            combinedScore *= 0.6;
        }

        // Calculate hit completion ratio bonus
        double completionRatio = (double) hitsCompleted / recipe.getHits();
        if (completionRatio < 1.0) {
            // Penalize incomplete forging
            combinedScore *= (0.5 + completionRatio * 0.5);
        }

        // Star thresholds (adjusted for better distribution)
        if (combinedScore >= 0.90) return 5;
        if (combinedScore >= 0.75) return 4;
        if (combinedScore >= 0.60) return 3;
        if (combinedScore >= 0.45) return 2;
        if (combinedScore >= 0.25) return 1;
        return 0;
    }

    public double calculateFinalScore() {
        double accuracyScore = getAverageAccuracy();
        double speedScore = getAverageSpeedScore();

        // Weight accuracy more heavily (70% accuracy, 30% speed)
        double baseScore = (accuracyScore * 0.7) + (speedScore * 0.3);

        // Apply recipe bias (can make recipe easier or harder)
        return Math.min(1.0, baseScore + recipe.getBias());
    }

    public double getAverageAccuracy() {
        if (hitAccuracies.isEmpty()) return 0.0;
        return hitAccuracies.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    public double getAverageSpeedScore() {
        if (hitTimes.size() < 2) return 0.5;

        double totalScore = 0.0;
        for (int i = 1; i < hitTimes.size(); i++) {
            long timeBetween = hitTimes.get(i) - hitTimes.get(i - 1);
            totalScore += calculateSpeedScore(timeBetween);
        }

        double avgSpeed = totalScore / (hitTimes.size() - 1);
        return Math.min(1.0, avgSpeed + hammerSpeedBonus);
    }

    private double calculateSpeedScore(long timeBetweenHits) {
        if (timeBetweenHits <= FAST_HIT_MS) {
            return 1.0;
        } else if (timeBetweenHits <= NORMAL_HIT_MS) {
            double ratio = (double) (timeBetweenHits - FAST_HIT_MS) / (NORMAL_HIT_MS - FAST_HIT_MS);
            return 1.0 - ratio * 0.3;
        } else if (timeBetweenHits <= POINT_DURATION) {
            double ratio = (double) (timeBetweenHits - NORMAL_HIT_MS) / (POINT_DURATION - NORMAL_HIT_MS);
            return 0.7 - ratio * 0.4;
        }
        return 0.3;
    }

    // ==================== LIFECYCLE ====================

    public void setHammerBonuses(double speedBonus, double accuracyBonus) {
        this.hammerSpeedBonus = speedBonus;
        this.hammerAccuracyBonus = accuracyBonus;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void cancel() {
        active = false;
        cleanup();
    }

    public void cleanup() {
        activePoints.forEach(ForgePoint::remove);
        activePoints.clear();
    }

    // ==================== GETTERS ====================

    public UUID getPlayerId() { return playerId; }
    public ForgeRecipe getRecipe() { return recipe; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
    public int getHitsCompleted() { return hitsCompleted; }
    public int getTotalHits() { return recipe.getHits(); }
    public int getPerfectHits() { return perfectHits; }
    public int getMissedPoints() { return missedPoints; }
    public int getCurrentFrame() { return currentFrame; }
    public boolean isActive() { return active; }
    public boolean isTimedOut() { return timedOut; }
    public boolean hasStartedHitting() { return hasFirstHit; }
    public List<ForgePoint> getActivePoints() { return new ArrayList<>(activePoints); }

    public double getProgress() {
        int total = recipe.getHits();
        return total > 0 ? (double) hitsCompleted / total : 1.0;
    }

    public boolean isComplete() {
        return hitsCompleted >= recipe.getHits();
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Return correct timeout based on session state
     */
    public long getTimeUntilTimeout() {
        if (timedOut) return 0;

        // If no first hit yet, show time until no-hit timeout
        if (!hasFirstHit) {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.max(0, NO_HIT_TIMEOUT_MS - elapsed);
        }

        // Otherwise show inactivity timeout
        long timeSinceActivity = System.currentTimeMillis() - lastActivityTime;
        return Math.max(0, INACTIVITY_TIMEOUT_MS - timeSinceActivity);
    }

    public long getTimeUntilMaxDuration() {
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.max(0, MAX_SESSION_DURATION_MS - elapsed);
    }

    public ForgeResult getResultItem() {
        return recipe.getResult(calculateStarRating());
    }
}