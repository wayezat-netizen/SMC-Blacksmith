package com.simmc.blacksmith.forge.display;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Display settings for forge recipe - configurable per recipe.
 */
public record ForgeDisplaySettings(
        Map<Integer, DisplayItem> stageItems,  // Stage-specific display items (0, 1, 2)
        String displayItemType,                 // Legacy: "ce", "smc", "minecraft" or null
        String displayItemId,                   // Legacy: Item ID for display model
        double offsetX,                         // X offset (default 0.5 = center)
        double offsetY,                         // Y offset - height above anvil (default 1.0)
        double offsetZ,                         // Z offset (default 0.5 = center)
        float baseScale,                        // Starting scale (default 0.6)
        float maxScale,                         // Max scale at full heat (default 0.9)
        boolean layFlat                         // Lay flat on surface or stand upright
) {

    /**
     * Represents a display item for a specific stage.
     */
    public record DisplayItem(String type, String id) {
        public boolean isValid() {
            return id != null && !id.isEmpty();
        }
    }

    // Default settings - item ON TOP of anvil
    public static final ForgeDisplaySettings DEFAULT = new ForgeDisplaySettings(
            Collections.emptyMap(),
            null, null,
            0.5, 1.0, 0.5,
            0.6f, 0.9f, true
    );

    /**
     * Check if this has stage-specific items configured.
     */
    public boolean hasStageItems() {
        return stageItems != null && !stageItems.isEmpty();
    }

    /**
     * Check if legacy single display item is configured.
     */
    public boolean hasCustomDisplayItem() {
        return displayItemId != null && !displayItemId.isEmpty();
    }

    /**
     * Gets the display item for a specific stage (0, 1, or 2).
     * Falls back to legacy single item if stages not defined.
     */
    public DisplayItem getStageItem(int stage) {
        // First try stage specific item
        if (stageItems != null && stageItems.containsKey(stage)) {
            return stageItems.get(stage);
        }

        // Fallback to legacy single item
        if (hasCustomDisplayItem()) {
            return new DisplayItem(displayItemType, displayItemId);
        }

        return null;
    }

    /**
     * Gets display item type for a stage, with fallback.
     */
    public String getItemTypeForStage(int stage) {
        DisplayItem item = getStageItem(stage);
        return item != null ? item.type() : displayItemType;
    }

    /**
     * Gets display item ID for a stage, with fallback.
     */
    public String getItemIdForStage(int stage) {
        DisplayItem item = getStageItem(stage);
        return item != null ? item.id() : displayItemId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Map<Integer, DisplayItem> stageItems = new HashMap<>();
        private String displayItemType = null;
        private String displayItemId = null;
        private double offsetX = 0.5;
        private double offsetY = 1.0;
        private double offsetZ = 0.5;
        private float baseScale = 0.6f;
        private float maxScale = 0.9f;
        private boolean layFlat = true;

        public Builder stageItems(Map<Integer, DisplayItem> items) {
            this.stageItems = items != null ? new HashMap<>(items) : new HashMap<>();
            return this;
        }

        public Builder addStageItem(int stage, String type, String id) {
            this.stageItems.put(stage, new DisplayItem(type, id));
            return this;
        }

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

        public Builder offsetX(double x) {
            this.offsetX = x;
            return this;
        }

        public Builder offsetY(double y) {
            this.offsetY = y;
            return this;
        }

        public Builder offsetZ(double z) {
            this.offsetZ = z;
            return this;
        }

        public Builder scale(float base, float max) {
            this.baseScale = base;
            this.maxScale = max;
            return this;
        }

        public Builder baseScale(float scale) {
            this.baseScale = scale;
            return this;
        }

        public Builder maxScale(float scale) {
            this.maxScale = scale;
            return this;
        }

        public Builder layFlat(boolean flat) {
            this.layFlat = flat;
            return this;
        }

        public ForgeDisplaySettings build() {
            return new ForgeDisplaySettings(
                    Collections.unmodifiableMap(new HashMap<>(stageItems)),
                    displayItemType, displayItemId,
                    offsetX, offsetY, offsetZ,
                    baseScale, maxScale, layFlat
            );
        }
    }
}