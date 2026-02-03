package com.simmc.blacksmith.forge;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Represents an active forging session for a player.
 * Tracks hits, accuracy, and calculates star rating based on configurable thresholds.
 */
public class ForgeSession {

    private static final double PERFECT_HIT_THRESHOLD = 0.9;

    private final UUID sessionId;
    private final UUID playerId;
    private final ForgeRecipe recipe;
    private final Location anvilLocation;
    private final long startTime;
    private final List<HitRecord> hitRecords;
    private final Random random;

    private int currentFrame;
    private int hitsCompleted;
    private int perfectHits;
    private double currentTargetPosition;
    private boolean active;

    public ForgeSession(UUID playerId, ForgeRecipe recipe, Location anvilLocation) {
        this.sessionId = UUID.randomUUID();
        this.playerId = playerId;
        this.recipe = recipe;
        this.anvilLocation = anvilLocation.clone();
        this.startTime = System.currentTimeMillis();
        this.hitRecords = new ArrayList<>();
        this.random = new Random();

        this.currentFrame = 0;
        this.hitsCompleted = 0;
        this.perfectHits = 0;
        this.active = true;

        randomizeTarget();
    }

    /**
     * Processes a hammer hit at the given position.
     *
     * @param hitPosition the position of the hit (0.0 - 1.0)
     * @return true if forging is complete
     */
    public boolean processHit(double hitPosition) {
        if (!active) return false;

        double accuracy = calculateAccuracy(hitPosition);
        hitRecords.add(new HitRecord(hitPosition, currentTargetPosition, accuracy, System.currentTimeMillis()));
        hitsCompleted++;

        // Track perfect hits
        if (accuracy >= PERFECT_HIT_THRESHOLD) {
            perfectHits++;
        }

        randomizeTarget();
        updateFrame();

        return hitsCompleted >= recipe.getHits();
    }

    /**
     * Calculates accuracy based on hit position relative to target.
     */
    public double calculateAccuracy(double hitPosition) {
        double distance = Math.abs(hitPosition - currentTargetPosition);
        double halfTarget = recipe.getTargetSize() / 2.0;

        if (distance <= halfTarget) {
            // Inside target zone - high accuracy
            return 1.0 - (distance / halfTarget) * 0.3;
        } else {
            // Outside target zone - reduced accuracy
            return Math.max(0.0, 0.7 - (distance - halfTarget));
        }
    }

    /**
     * Randomizes the target position for the next hit.
     */
    public void randomizeTarget() {
        double margin = recipe.getTargetSize() / 2.0;
        currentTargetPosition = margin + random.nextDouble() * (1.0 - 2 * margin);
    }

    private void updateFrame() {
        currentFrame = recipe.getFrameForProgress(getProgress());
    }

    /**
     * Gets the current progress (0.0 - 1.0).
     */
    public double getProgress() {
        if (recipe.getHits() <= 0) return 1.0;
        return (double) hitsCompleted / recipe.getHits();
    }

    /**
     * Calculates the final score based on average accuracy and bias.
     */
    public double calculateFinalScore() {
        if (hitRecords.isEmpty()) return 0.0;

        double avgAccuracy = hitRecords.stream()
                .mapToDouble(HitRecord::accuracy)
                .average()
                .orElse(0.0);

        return Math.min(1.0, avgAccuracy + recipe.getBias());
    }

    /**
     * Gets the average accuracy of all hits.
     */
    public double getAverageAccuracy() {
        if (hitRecords.isEmpty()) return 0.0;

        return hitRecords.stream()
                .mapToDouble(HitRecord::accuracy)
                .average()
                .orElse(0.0);
    }

    /**
     * Calculates the star rating based on configurable thresholds.
     * Uses recipe-specific thresholds if available, otherwise uses score-based calculation.
     */
    public int calculateStarRating() {
        // Check if recipe has custom star thresholds
        Map<Integer, StarThreshold> thresholds = recipe.getStarThresholds();

        if (thresholds != null && !thresholds.isEmpty()) {
            // Use configurable thresholds
            return calculateStarRatingFromThresholds(thresholds);
        }

        // Fallback to score-based calculation
        return calculateStarRatingFromScore();
    }

    /**
     * Calculates star rating using configurable thresholds.
     * Checks from highest star (5) to lowest (0).
     */
    private int calculateStarRatingFromThresholds(Map<Integer, StarThreshold> thresholds) {
        double avgAccuracy = getAverageAccuracy();

        // Check from highest to lowest
        for (int star = 5; star >= 0; star--) {
            StarThreshold threshold = thresholds.get(star);
            if (threshold != null && threshold.isMet(perfectHits, avgAccuracy)) {
                return star;
            }
        }

        return 0;
    }

    /**
     * Calculates star rating using the traditional score-based method.
     */
    private int calculateStarRatingFromScore() {
        double score = calculateFinalScore();

        if (score >= 0.95) return 5;
        if (score >= 0.85) return 4;
        if (score >= 0.70) return 3;
        if (score >= 0.50) return 2;
        if (score >= 0.30) return 1;
        return 0;
    }

    /**
     * Gets the result item based on the calculated star rating.
     */
    public ForgeResult getResultItem() {
        return recipe.getResult(calculateStarRating());
    }

    /**
     * Cancels the forging session.
     */
    public void cancel() {
        this.active = false;
    }

    // ==================== GETTERS ====================

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ForgeRecipe getRecipe() {
        return recipe;
    }

    public Location getAnvilLocation() {
        return anvilLocation.clone();
    }

    public long getStartTime() {
        return startTime;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getHitsCompleted() {
        return hitsCompleted;
    }

    public int getTotalHits() {
        return recipe.getHits();
    }

    public int getPerfectHits() {
        return perfectHits;
    }

    public double getCurrentTargetPosition() {
        return currentTargetPosition;
    }

    public boolean isActive() {
        return active;
    }

    public List<HitRecord> getHitRecords() {
        return new ArrayList<>(hitRecords);
    }

    /**
     * Gets session statistics as a formatted string.
     */
    public String getStats() {
        return String.format(
                "Hits: %d/%d, Perfect: %d, Avg Accuracy: %.1f%%, Score: %.2f",
                hitsCompleted, recipe.getHits(), perfectHits,
                getAverageAccuracy() * 100, calculateFinalScore()
        );
    }

    // ==================== INNER CLASSES ====================

    /**
     * Record of a single hit during forging.
     */
    public record HitRecord(
            double hitPosition,
            double targetPosition,
            double accuracy,
            long timestamp
    ) {
        /**
         * Checks if this was a perfect hit.
         */
        public boolean isPerfect() {
            return accuracy >= PERFECT_HIT_THRESHOLD;
        }

        /**
         * Gets the distance from target.
         */
        public double getDistance() {
            return Math.abs(hitPosition - targetPosition);
        }
    }
}