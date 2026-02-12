package com.simmc.blacksmith.forge.display;

/**
 * Display settings for forge recipe - configurable per recipe.
 * Controls the model shown on anvil and its position.
 */
public record ForgeDisplaySettings(
        String displayItemType,    // "ce", "smc", "minecraft" or null to use frames
        String displayItemId,      // Item ID for display model
        double offsetX,            // X offset from anvil center (default 0.5)
        double offsetY,            // Y offset - height above anvil (default 0.9)
        double offsetZ,            // Z offset from anvil center (default 0.5)
        float baseScale,           // Starting scale (default 0.5)
        float maxScale,            // Max scale at full heat (default 0.7)
        boolean layFlat            // Lay flat on surface or stand upright
) {
    // Default settings
    public static final ForgeDisplaySettings DEFAULT = new ForgeDisplaySettings(
            null, null, 0.5, 0.9, 0.5, 0.5f, 0.7f, true
    );

    public boolean hasCustomDisplayItem() {
        return displayItemId != null && !displayItemId.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String displayItemType = null;
        private String displayItemId = null;
        private double offsetX = 0.5;
        private double offsetY = 0.9;
        private double offsetZ = 0.5;
        private float baseScale = 0.5f;
        private float maxScale = 0.7f;
        private boolean layFlat = true;

        public Builder displayItem(String type, String id) {
            this.displayItemType = type;
            this.displayItemId = id;
            return this;
        }

        public Builder offset(double x, double y, double z) {
            this.offsetX = x;
            this.offsetY = y;
            this.offsetZ = z;
            return this;
        }

        public Builder offsetY(double y) {
            this.offsetY = y;
            return this;
        }

        public Builder scale(float base, float max) {
            this.baseScale = base;
            this.maxScale = max;
            return this;
        }

        public Builder layFlat(boolean flat) {
            this.layFlat = flat;
            return this;
        }

        public ForgeDisplaySettings build() {
            return new ForgeDisplaySettings(
                    displayItemType, displayItemId,
                    offsetX, offsetY, offsetZ,
                    baseScale, maxScale, layFlat
            );
        }
    }
}