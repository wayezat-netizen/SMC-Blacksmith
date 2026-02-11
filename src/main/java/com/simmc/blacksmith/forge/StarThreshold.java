package com.simmc.blacksmith.forge;

/**
 * Threshold requirements for achieving a specific star rating.
 */
public record StarThreshold(int minPerfectHits, double minAccuracy) {

    public StarThreshold {
        minPerfectHits = Math.max(0, minPerfectHits);
        minAccuracy = Math.max(0.0, Math.min(1.0, minAccuracy));
    }

    /**
     * Checks if the performance meets this threshold.
     */
    public boolean isMet(int perfectHits, double accuracy) {
        return perfectHits >= minPerfectHits && accuracy >= minAccuracy;
    }

    public boolean isAccuracyMet(double accuracy) {
        return accuracy >= minAccuracy;
    }

    public boolean isHitCountMet(int perfectHits) {
        return perfectHits >= minPerfectHits;
    }

    // Compatibility getters
    public int getMinPerfectHits() { return minPerfectHits; }
    public double getMinAccuracy() { return minAccuracy; }

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