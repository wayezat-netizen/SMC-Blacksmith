package com.simmc.blacksmith.forge;

import java.util.Map;

public class ForgeRecipe {

    private final String id;
    private final Map<Integer, ForgeFrame> frames;
    private final String permission;
    private final int hits;
    private final double bias;
    private final double targetSize;
    private final String runAfterCommand;
    private final String inputId;
    private final String inputType;
    private final int inputAmount;
    private final Map<Integer, ForgeResult> results;

    public ForgeRecipe(String id, Map<Integer, ForgeFrame> frames, String permission,
                       int hits, double bias, double targetSize, String runAfterCommand,
                       String inputId, String inputType, int inputAmount,
                       Map<Integer, ForgeResult> results) {
        this.id = id;
        this.frames = frames;
        this.permission = permission;
        this.hits = hits;
        this.bias = bias;
        this.targetSize = targetSize;
        this.runAfterCommand = runAfterCommand;
        this.inputId = inputId;
        this.inputType = inputType;
        this.inputAmount = inputAmount;
        this.results = results;
    }

    public ForgeFrame getFrame(int index) {
        return frames.get(index);
    }

    public ForgeResult getResult(int starRating) {
        int clamped = Math.max(0, Math.min(5, starRating));
        return results.get(clamped);
    }

    public int getFrameForProgress(double progress) {
        if (progress < 0.33) return 0;
        if (progress < 0.66) return 1;
        return 2;
    }

    public String getId() {
        return id;
    }

    public Map<Integer, ForgeFrame> getFrames() {
        return frames;
    }

    public String getPermission() {
        return permission;
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
}