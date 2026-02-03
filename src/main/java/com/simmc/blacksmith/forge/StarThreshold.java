package com.simmc.blacksmith.forge;

/**
 * Represents the threshold requirements for achieving a specific star rating.
 * Supports both hit-count based and accuracy-based thresholds.
 */
public class StarThreshold {

    private final int minPerfectHits;
    private final double minAccuracy;

    /**
     * Creates a new star threshold.
     *
     * @param minPerfectHits minimum number of perfect hits (accuracy >= 0.9) required
     * @param minAccuracy    minimum average accuracy required (0.0 - 1.0)
     */
    public StarThreshold(int minPerfectHits, double minAccuracy) {
        this.minPerfectHits = Math.max(0, minPerfectHits);
        this.minAccuracy = Math.max(0.0, Math.min(1.0, minAccuracy));
    }

    /**
     * Checks if the given performance meets this threshold.
     *
     * @param perfectHits number of perfect hits achieved
     * @param accuracy    average accuracy achieved
     * @return true if threshold is met
     */
    public boolean isMet(int perfectHits, double accuracy) {
        return perfectHits >= minPerfectHits && accuracy >= minAccuracy;
    }

    /**
     * Checks if the given accuracy meets this threshold (ignoring hit count).
     *
     * @param accuracy average accuracy achieved
     * @return true if accuracy threshold is met
     */
    public boolean isAccuracyMet(double accuracy) {
        return accuracy >= minAccuracy;
    }

    /**
     * Checks if the given hit count meets this threshold (ignoring accuracy).
     *
     * @param perfectHits number of perfect hits achieved
     * @return true if hit count threshold is met
     */
    public boolean isHitCountMet(int perfectHits) {
        return perfectHits >= minPerfectHits;
    }

    public int getMinPerfectHits() {
        return minPerfectHits;
    }

    public double getMinAccuracy() {
        return minAccuracy;
    }

    @Override
    public String toString() {
        return String.format("StarThreshold{minPerfectHits=%d, minAccuracy=%.2f}", minPerfectHits, minAccuracy);
    }

    /**
     * Creates default thresholds for all star ratings.
     */
    public static StarThreshold[] createDefaults() {
        return new StarThreshold[] {
                new StarThreshold(0, 0.0),   // 0 stars
                new StarThreshold(0, 0.30),  // 1 star
                new StarThreshold(0, 0.50),  // 2 stars
                new StarThreshold(0, 0.70),  // 3 stars
                new StarThreshold(0, 0.85),  // 4 stars
                new StarThreshold(0, 0.95)   // 5 stars
        };
    }
}