package com.simmc.blacksmith.forge;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ForgeSession {

    private final UUID sessionId;
    private final UUID playerId;
    private final ForgeRecipe recipe;
    private final Location anvilLocation;
    private final long startTime;
    private final List<HitRecord> hitRecords;
    private final Random random;

    private int currentFrame;
    private int hitsCompleted;
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
        this.active = true;

        randomizeTarget();
    }

    public boolean processHit(double hitPosition) {
        if (!active) return false;

        double accuracy = calculateAccuracy(hitPosition);
        hitRecords.add(new HitRecord(hitPosition, currentTargetPosition, accuracy));
        hitsCompleted++;

        randomizeTarget();
        updateFrame();

        return hitsCompleted >= recipe.getHits();
    }

    public double calculateAccuracy(double hitPosition) {
        double distance = Math.abs(hitPosition - currentTargetPosition);
        double halfTarget = recipe.getTargetSize() / 2.0;

        if (distance <= halfTarget) {
            return 1.0 - (distance / halfTarget) * 0.3;
        } else {
            return Math.max(0.0, 0.7 - (distance - halfTarget));
        }
    }

    public void randomizeTarget() {
        double margin = recipe.getTargetSize() / 2.0;
        currentTargetPosition = margin + random.nextDouble() * (1.0 - 2 * margin);
    }

    private void updateFrame() {
        currentFrame = recipe.getFrameForProgress(getProgress());
    }

    public double getProgress() {
        if (recipe.getHits() <= 0) return 1.0;
        return (double) hitsCompleted / recipe.getHits();
    }

    public double calculateFinalScore() {
        if (hitRecords.isEmpty()) return 0.0;

        double avgAccuracy = hitRecords.stream()
                .mapToDouble(HitRecord::accuracy)
                .average()
                .orElse(0.0);

        return Math.min(1.0, avgAccuracy + recipe.getBias());
    }

    public int calculateStarRating() {
        double score = calculateFinalScore();

        if (score >= 0.95) return 5;
        if (score >= 0.85) return 4;
        if (score >= 0.70) return 3;
        if (score >= 0.50) return 2;
        if (score >= 0.30) return 1;
        return 0;
    }

    public ForgeResult getResultItem() {
        return recipe.getResult(calculateStarRating());
    }

    public void cancel() {
        this.active = false;
    }

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

    public int getCurrentFrame() {
        return currentFrame;
    }

    public int getHitsCompleted() {
        return hitsCompleted;
    }

    public int getTotalHits() {
        return recipe.getHits();
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

    public record HitRecord(
            double hitPosition,
            double targetPosition,
            double accuracy
    ) {}
}