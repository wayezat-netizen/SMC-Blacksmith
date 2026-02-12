package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.forge.display.ForgeDisplaySettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a forge recipe with frames, materials, results, and scoring thresholds.
 *
 * ADDED:
 * - Display settings for CE items on anvil
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

    // Input material
    private final String inputId;
    private final String inputType;
    private final int inputAmount;

    // Results and scoring
    private final Map<Integer, ForgeResult> results;
    private final Map<Integer, StarThreshold> starThresholds;
    private final Map<Integer, StarModifier> starModifiers;

    // Base item mode
    private final String baseItemId;
    private final String baseItemType;

    // Display settings (CE item on anvil)
    private final ForgeDisplaySettings displaySettings;

    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       String condition, int hits, double bias, double targetSize,
                       String runAfterCommand, String inputId, String inputType,
                       int inputAmount, Map<Integer, ForgeResult> results,
                       Map<Integer, StarThreshold> starThresholds,
                       Map<Integer, StarModifier> starModifiers,
                       String baseItemId, String baseItemType,
                       ForgeDisplaySettings displaySettings) {
        this.id = id;
        this.frames = frames != null ? new HashMap<>(frames) : new HashMap<>();
        this.permission = nullToEmpty(permission);
        this.condition = nullToEmpty(condition);
        this.hits = Math.max(1, hits);
        this.bias = bias;
        this.targetSize = Math.max(0.1, Math.min(1.0, targetSize));
        this.runAfterCommand = nullToEmpty(runAfterCommand);
        this.inputId = nullToEmpty(inputId);
        this.inputType = inputType != null ? inputType : "minecraft";
        this.inputAmount = Math.max(0, inputAmount);
        this.results = results != null ? new HashMap<>(results) : new HashMap<>();
        this.starThresholds = starThresholds != null ? new HashMap<>(starThresholds) : new HashMap<>();
        this.starModifiers = starModifiers != null ? new HashMap<>(starModifiers) : new HashMap<>();
        this.baseItemId = nullToEmpty(baseItemId);
        this.baseItemType = baseItemType != null ? baseItemType : "smc";
        this.displaySettings = displaySettings;
    }

    // Backwards compatibility constructor
    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       String condition, int hits, double bias, double targetSize,
                       String runAfterCommand, String inputId, String inputType,
                       int inputAmount, Map<Integer, ForgeResult> results,
                       Map<Integer, StarThreshold> starThresholds) {
        this(id, frames, permission, condition, hits, bias, targetSize, runAfterCommand,
                inputId, inputType, inputAmount, results, starThresholds, null, "", "", null);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    // ==================== FRAME ACCESS ====================

    public ForgeFrame getFrame(int index) {
        return frames.get(index);
    }

    public int getFrameForProgress(double progress) {
        if (progress < 0.33) return 0;
        if (progress < 0.66) return 1;
        return 2;
    }

    // ==================== RESULT ACCESS ====================

    public ForgeResult getResult(int starRating) {
        int clamped = Math.max(0, Math.min(5, starRating));

        ForgeResult result = results.get(clamped);
        if (result != null) return result;

        for (int i = clamped - 1; i >= 0; i--) {
            result = results.get(i);
            if (result != null) return result;
        }

        return null;
    }

    public StarThreshold getStarThreshold(int star) {
        return starThresholds.get(star);
    }

    public StarModifier getStarModifier(int star) {
        return starModifiers.get(star);
    }

    // ==================== DISPLAY SETTINGS ====================

    public ForgeDisplaySettings getDisplaySettings() {
        return displaySettings;
    }

    public ForgeDisplaySettings getDisplaySettingsOrDefault() {
        return displaySettings != null ? displaySettings : ForgeDisplaySettings.DEFAULT;
    }

    // ==================== BOOLEAN CHECKS ====================

    public boolean hasPermission() { return !permission.isEmpty(); }
    public boolean hasCondition() { return !condition.isEmpty(); }
    public boolean hasInput() { return !inputId.isEmpty(); }
    public boolean hasStarThresholds() { return !starThresholds.isEmpty(); }
    public boolean hasStarModifiers() { return !starModifiers.isEmpty(); }
    public boolean usesBaseItem() { return !baseItemId.isEmpty(); }
    public boolean hasDisplaySettings() { return displaySettings != null; }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public Map<Integer, ForgeFrame> getFrames() { return Collections.unmodifiableMap(frames); }
    public String getPermission() { return permission; }
    public String getCondition() { return condition; }
    public int getHits() { return hits; }
    public double getBias() { return bias; }
    public double getTargetSize() { return targetSize; }
    public String getRunAfterCommand() { return runAfterCommand; }
    public String getInputId() { return inputId; }
    public String getInputType() { return inputType; }
    public int getInputAmount() { return inputAmount; }
    public Map<Integer, ForgeResult> getResults() { return Collections.unmodifiableMap(results); }
    public Map<Integer, StarThreshold> getStarThresholds() { return Collections.unmodifiableMap(starThresholds); }
    public Map<Integer, StarModifier> getStarModifiers() { return Collections.unmodifiableMap(starModifiers); }
    public String getBaseItemId() { return baseItemId; }
    public String getBaseItemType() { return baseItemType; }
}