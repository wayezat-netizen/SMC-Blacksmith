package com.simmc.blacksmith.forge;

import java.util.HashMap;
import java.util.Map;

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
    private final Map<Integer, StarModifier> starModifiers;
    private final String baseItemId;
    private final String baseItemType;

    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       String condition, int hits, double bias, double targetSize,
                       String runAfterCommand, String inputId, String inputType,
                       int inputAmount, Map<Integer, ForgeResult> results,
                       Map<Integer, StarThreshold> starThresholds,
                       Map<Integer, StarModifier> starModifiers,
                       String baseItemId, String baseItemType) {
        this.id = id;
        this.frames = frames != null ? frames : new HashMap<>();
        this.permission = permission != null ? permission : "";
        this.condition = condition != null ? condition : "";
        this.hits = hits;
        this.bias = bias;
        this.targetSize = targetSize;
        this.runAfterCommand = runAfterCommand;
        this.inputId = inputId != null ? inputId : "";
        this.inputType = inputType != null ? inputType : "minecraft";
        this.inputAmount = inputAmount;
        this.results = results != null ? results : new HashMap<>();
        this.starThresholds = starThresholds != null ? starThresholds : new HashMap<>();
        this.starModifiers = starModifiers != null ? starModifiers : new HashMap<>();
        this.baseItemId = baseItemId != null ? baseItemId : "";
        this.baseItemType = baseItemType != null ? baseItemType : "smc";
    }

    // Backwards compatibility constructor
    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       String condition, int hits, double bias, double targetSize,
                       String runAfterCommand, String inputId, String inputType,
                       int inputAmount, Map<Integer, ForgeResult> results,
                       Map<Integer, StarThreshold> starThresholds) {
        this(id, frames, permission, condition, hits, bias, targetSize, runAfterCommand,
                inputId, inputType, inputAmount, results, starThresholds, null, "", "");
    }

    public ForgeFrame getFrame(int index) {
        return frames.get(index);
    }

    public ForgeResult getResult(int starRating) {
        int clamped = Math.max(0, Math.min(5, starRating));
        ForgeResult result = results.get(clamped);

        if (result == null) {
            for (int i = clamped; i >= 0; i--) {
                result = results.get(i);
                if (result != null) break;
            }
        }

        return result;
    }

    public int getFrameForProgress(double progress) {
        if (progress < 0.33) return 0;
        if (progress < 0.66) return 1;
        return 2;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasCondition() {
        return condition != null && !condition.isEmpty();
    }

    public boolean hasInput() {
        return inputId != null && !inputId.isEmpty();
    }

    public boolean hasStarThresholds() {
        return starThresholds != null && !starThresholds.isEmpty();
    }

    public boolean hasStarModifiers() {
        return starModifiers != null && !starModifiers.isEmpty();
    }

    public boolean usesBaseItem() {
        return baseItemId != null && !baseItemId.isEmpty();
    }

    public StarThreshold getStarThreshold(int star) {
        return starThresholds.get(star);
    }

    public StarModifier getStarModifier(int star) {
        return starModifiers.get(star);
    }

    // Getters
    public String getId() { return id; }
    public Map<Integer, ForgeFrame> getFrames() { return frames; }
    public String getPermission() { return permission; }
    public String getCondition() { return condition; }
    public int getHits() { return hits; }
    public double getBias() { return bias; }
    public double getTargetSize() { return targetSize; }
    public String getRunAfterCommand() { return runAfterCommand; }
    public String getInputId() { return inputId; }
    public String getInputType() { return inputType; }
    public int getInputAmount() { return inputAmount; }
    public Map<Integer, ForgeResult> getResults() { return results; }
    public Map<Integer, StarThreshold> getStarThresholds() { return starThresholds; }
    public Map<Integer, StarModifier> getStarModifiers() { return starModifiers; }
    public String getBaseItemId() { return baseItemId; }
    public String getBaseItemType() { return baseItemType; }
}