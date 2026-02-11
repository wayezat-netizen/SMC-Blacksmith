package com.simmc.blacksmith.forge;

import org.bukkit.Location;

import java.util.*;

/**
 * Manages state for an active forging session.
 * Handles point spawning, hit tracking, and score calculation.
 */
public class ForgeSession {

    // Timing constants
    private static final long INITIAL_INTERVAL = 1200;
    private static final long MIN_INTERVAL = 500;
    private static final long POINT_DURATION = 1800;
    private static final int SPEEDUP_EVERY_N_HITS = 3;

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
    private int pointsToSpawn;
    private int hitsCompleted;
    private int perfectHits;
    private int missedPoints;
    private int currentFrame;
    private boolean active;

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
        this.pointsToSpawn = 1;

        this.hitsCompleted = 0;
        this.perfectHits = 0;
        this.missedPoints = 0;
        this.currentFrame = 0;
        this.active = true;

        this.hammerSpeedBonus = 0.0;
        this.hammerAccuracyBonus = 0.0;
    }

    // ==================== TICK ====================

    public void tick() {
        if (!active) return;

        long now = System.currentTimeMillis();

        // Update and cleanup points
        updateActivePoints();

        // Spawn new points if needed
        if (shouldSpawnPoints(now)) {
            spawnPoints();
            lastPointSpawn = now;
            updateRhythm();
        }
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
        return now - lastPointSpawn >= currentInterval && hitsCompleted < recipe.getHits();
    }

    private void spawnPoints() {
        int remaining = recipe.getHits() - hitsCompleted;
        int count = Math.min(pointsToSpawn, remaining);

        for (int i = 0; i < count; i++) {
            Location pointLoc = generatePointLocation();
            ForgePoint point = new ForgePoint(pointLoc, POINT_DURATION);
            point.spawn();
            activePoints.add(point);
        }
    }

    private void updateRhythm() {
        // Speed up every N hits
        if (hitsCompleted > 0 && hitsCompleted % SPEEDUP_EVERY_N_HITS == 0) {
            currentInterval = Math.max(MIN_INTERVAL, currentInterval - 100);
        }

        // Rhythm variation - sometimes spawn multiple points
        double progress = getProgress();
        double roll = random.nextDouble();

        if (progress > 0.75 && roll < 0.10) {
            pointsToSpawn = 3;
        } else if (progress > 0.5 && roll < 0.20) {
            pointsToSpawn = 2;
        } else {
            pointsToSpawn = 1;
        }
    }

    private Location generatePointLocation() {
        double offsetX = (random.nextDouble() - 0.5) * 0.6;
        double offsetZ = (random.nextDouble() - 0.5) * 0.4;

        return anvilLocation.clone().add(0.5 + offsetX, 1.0, 0.5 + offsetZ);
    }

    // ==================== HIT PROCESSING ====================

    public double processHit(UUID hitboxId) {
        long hitTime = System.currentTimeMillis();

        for (int i = 0; i < activePoints.size(); i++) {
            ForgePoint point = activePoints.get(i);
            if (point.matchesHitbox(hitboxId) && point.isActive()) {
                double accuracy = point.hit();

                // Apply hammer bonus
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

        // Default: combined accuracy + speed score
        double combinedScore = calculateFinalScore();

        // Penalty for missed points
        double missedPenalty = missedPoints * 0.05;
        combinedScore = Math.max(0, combinedScore - missedPenalty);

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

    public ForgeResult getResultItem() {
        return recipe.getResult(calculateStarRating());
    }
}