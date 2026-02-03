package com.simmc.blacksmith.forge;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a forge recipe configuration.
 * Contains all settings for a forging minigame including frames, inputs, outputs,
 * permissions, PAPI conditions, and configurable star thresholds.
 */
public class ForgeRecipe {

    private final String id;
    private final Map<Integer, ForgeFrame> frames;
    private final String permission;
    private final String condition;
    private final int hits;
    private final double bias;
    private final double targetSize;
    private final String runAfterCommand;
    private final String inputId;
    private final String inputType;
    private final int inputAmount;
    private final Map<Integer, ForgeResult> results;
    private final Map<Integer, StarThreshold> starThresholds;

    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       String condition, int hits, double bias, double targetSize,
                       String runAfterCommand, String inputId, String inputType,
                       int inputAmount, Map<Integer, ForgeResult> results) {
        this(id, frames, permission, condition, hits, bias, targetSize, runAfterCommand,
                inputId, inputType, inputAmount, results, null);
    }

    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       String condition, int hits, double bias, double targetSize,
                       String runAfterCommand, String inputId, String inputType,
                       int inputAmount, Map<Integer, ForgeResult> results,
                       Map<Integer, StarThreshold> starThresholds) {
        this.id = id;
        this.frames = frames;
        this.permission = permission != null ? permission : "";
        this.condition = condition != null ? condition : "";
        this.hits = hits;
        this.bias = bias;
        this.targetSize = targetSize;
        this.runAfterCommand = runAfterCommand;
        this.inputId = inputId != null ? inputId : "";
        this.inputType = inputType != null ? inputType : "minecraft";
        this.inputAmount = inputAmount;
        this.results = results;
        this.starThresholds = starThresholds != null ? starThresholds : new HashMap<>();
    }

    /**
     * Gets the frame for the specified index.
     */
    public ForgeFrame getFrame(int index) {
        return frames.get(index);
    }

    /**
     * Gets the result item for the specified star rating.
     */
    public ForgeResult getResult(int starRating) {
        int clamped = Math.max(0, Math.min(5, starRating));
        ForgeResult result = results.get(clamped);

        // Fallback to nearest result if not found
        if (result == null) {
            for (int i = clamped; i >= 0; i--) {
                result = results.get(i);
                if (result != null) break;
            }
        }

        return result;
    }

    /**
     * Gets the frame index for the given progress.
     */
    public int getFrameForProgress(double progress) {
        if (progress < 0.33) return 0;
        if (progress < 0.66) return 1;
        return 2;
    }

    /**
     * Checks if this recipe has a permission requirement.
     */
    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    /**
     * Checks if this recipe has a PAPI condition requirement.
     */
    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    /**
     * Checks if this recipe requires input materials.
     */
    public boolean hasInput() {
        return inputId != null && !inputId.isEmpty();
    }

    /**
     * Checks if this recipe has custom star thresholds.
     */
    public boolean hasStarThresholds() {
        return starThresholds != null && !starThresholds.isEmpty();
    }

    /**
     * Gets the star threshold for a specific star rating.
     */
    public StarThreshold getStarThreshold(int star) {
        return starThresholds.get(star);
    }

    // ==================== GETTERS ====================

    public String getId() {
        return id;
    }

    public Map<Integer, ForgeFrame> getFrames() {
        return frames;
    }

    public String getPermission() {
        return permission;
    }

    public String getCondition() {
        return condition;
    }

    public int getHits() {
        return hits;
    }

    public double getBias() {
        return bias;
    }

    public double getTargetSize() {
        return targetSize;
    }

    public String getRunAfterCommand() {
        return runAfterCommand;
    }

    public String getInputId() {
        return inputId;
    }

    public String getInputType() {
        return inputType;
    }

    public int getInputAmount() {
        return inputAmount;
    }

    public Map<Integer, ForgeResult> getResults() {
        return results;
    }

    public Map<Integer, StarThreshold> getStarThresholds() {
        return starThresholds;
    }
}