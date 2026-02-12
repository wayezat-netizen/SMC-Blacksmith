package com.simmc.blacksmith.forge;

import org.bukkit.Location;

import java.util.*;

/**
 * Manages state for an active forging session.
 */
public class ForgeSession {

    // Timing constants - FIXED: Slower, single block spawns
    private static final long INITIAL_INTERVAL = 1800;   // 1.8 seconds (was 1.2)
    private static final long MIN_INTERVAL = 1000;       // 1.0 second minimum (was 0.5)
    private static final long POINT_DURATION = 2500;     // 2.5 seconds visible (was 1.8)
    private static final int SPEEDUP_EVERY_N_HITS = 5;   // Every 5 hits (was 3)
    private static final long INTERVAL_DECREASE = 50;    // Decrease by 50ms (was 100)

    // Timeout constants
    private static final long INACTIVITY_TIMEOUT_MS = 30000;
    private static final long MAX_SESSION_DURATION_MS = 300000;

    // Speed scoring thresholds
    private static final long FAST_HIT_MS = 400;
    private static final long NORMAL_HIT_MS = 800;

    private final UUID playerId;
    private final ForgeRecipe recipe;
    private final Location anvilLocation;
    private final Random random;
    private final long startTime;

    // Point tracking
    private final List<ForgePoint> activePoints;
    private final List<Double> hitAccuracies;
    private final List<Long> hitTimes;

    // Session state
    private long lastPointSpawn;
    private long currentInterval;
    private int hitsCompleted;
    private int perfectHits;
    private int missedPoints;
    private int currentFrame;
    private boolean active;

    // Timeout tracking
    private long lastActivityTime;
    private boolean timedOut;

    // Hammer bonuses
    private double hammerSpeedBonus;
    private double hammerAccuracyBonus;

    public ForgeSession(UUID playerId, ForgeRecipe recipe, Location anvilLocation) {
        this.playerId = playerId;
        this.recipe = recipe;
        this.anvilLocation = anvilLocation.clone();
        this.random = new Random();
        this.startTime = System.currentTimeMillis();

        this.activePoints = new ArrayList<>(8);
        this.hitAccuracies = new ArrayList<>(recipe.getHits());
        this.hitTimes = new ArrayList<>(recipe.getHits());

        this.lastPointSpawn = startTime;
        this.currentInterval = INITIAL_INTERVAL;

        this.hitsCompleted = 0;
        this.perfectHits = 0;
        this.missedPoints = 0;
        this.currentFrame = 0;
        this.active = true;

        this.lastActivityTime = startTime;
        this.timedOut = false;

        this.hammerSpeedBonus = 0.0;
        this.hammerAccuracyBonus = 0.0;
    }

    // ==================== TICK ====================

    public void tick() {
        if (!active) return;

        long now = System.currentTimeMillis();

        if (checkTimeout(now)) {
            return;
        }

        updateActivePoints();

        if (shouldSpawnPoints(now)) {
            spawnPoint();  // FIXED: Always spawn single point
            lastPointSpawn = now;
            updateRhythm();
        }
    }

    private boolean checkTimeout(long now) {
        if (now - startTime > MAX_SESSION_DURATION_MS) {
            timedOut = true;
            active = false;
            return true;
        }

        long timeSinceActivity = now - lastActivityTime;
        if (timeSinceActivity > INACTIVITY_TIMEOUT_MS) {
            if (missedPoints >= 3 || hitsCompleted == 0) {
                timedOut = true;
                active = false;
                return true;
            }
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
                point.remove();
                iterator.remove();
            }
        }
    }

    private boolean shouldSpawnPoints(long now) {
        // FIXED: Only spawn if no active points (one at a time)
        return now - lastPointSpawn >= currentInterval
                && hitsCompleted < recipe.getHits()
                && activePoints.isEmpty();
    }

    /**
     * Spawns a single point. FIXED: Always one at a time.
     */
    private void spawnPoint() {
        Location pointLoc = generatePointLocation();
        ForgePoint point = new ForgePoint(pointLoc, POINT_DURATION);
        point.spawn();
        activePoints.add(point);
    }

    /**
     * Updates spawn rhythm. FIXED: Slower speed increase.
     */
    private void updateRhythm() {
        // Speed up every N hits (slower progression)
        if (hitsCompleted > 0 && hitsCompleted % SPEEDUP_EVERY_N_HITS == 0) {
            currentInterval = Math.max(MIN_INTERVAL, currentInterval - INTERVAL_DECREASE);
        }
        // REMOVED: Multi-spawn logic - always 1 block now
    }

    private Location generatePointLocation() {
        double offsetX = (random.nextDouble() - 0.5) * 0.6;
        double offsetZ = (random.nextDouble() - 0.5) * 0.4;

        return anvilLocation.clone().add(0.5 + offsetX, 1.0, 0.5 + offsetZ);
    }

    // ==================== HIT PROCESSING ====================

    public double processHit(UUID hitboxId) {
        long hitTime = System.currentTimeMillis();
        lastActivityTime = hitTime;

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

        double combinedScore = calculateFinalScore();

        double missedPenalty = missedPoints * 0.05;
        combinedScore = Math.max(0, combinedScore - missedPenalty);

        if (timedOut) {
            combinedScore *= 0.5;
        }

        if (combinedScore >= 0.95) return 5;
        if (combinedScore >= 0.85) return 4;
        if (combinedScore >= 0.70) return 3;
        if (combinedScore >= 0.50) return 2;
        if (combinedScore >= 0.30) return 1;
        return 0;
    }

    public double calculateFinalScore() {
        double accuracyScore = getAverageAccuracy();
        double speedScore = getAverageSpeedScore();
        return Math.min(1.0, (accuracyScore * 0.5) + (speedScore * 0.5) + recipe.getBias());
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

    public long getTimeUntilTimeout() {
        if (timedOut) return 0;
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