package com.simmc.blacksmith.forge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ForgeSession {

    private final UUID playerId;
    private final ForgeRecipe recipe;
    private final Location anvilLocation;
    private final Random random;
    private final long startTime;

    // Point management - use ArrayList with initial capacity
    private final List<ForgePoint> activePoints;
    private final List<Double> hitAccuracies;

    // Cached point item to avoid recreation
    private final ItemStack cachedPointItem;

    // Rhythm control
    private long lastPointSpawn;
    private long currentInterval;
    private int pointsToSpawn;

    // Progress
    private int hitsCompleted;
    private int perfectHits;
    private int missedPoints;
    private int currentFrame;
    private boolean active;

    // Rhythm settings
    private static final long INITIAL_INTERVAL = 1200;
    private static final long MIN_INTERVAL = 400;
    private static final long POINT_DURATION = 2000;
    private static final int SPEEDUP_EVERY_N_HITS = 3;
    private static final int INITIAL_POINTS_CAPACITY = 8;

    public ForgeSession(UUID playerId, ForgeRecipe recipe, Location anvilLocation) {
        this.playerId = playerId;
        this.recipe = recipe;
        this.anvilLocation = anvilLocation.clone();
        this.random = new Random();
        this.startTime = System.currentTimeMillis();

        // Pre-size collections
        this.activePoints = new ArrayList<>(INITIAL_POINTS_CAPACITY);
        this.hitAccuracies = new ArrayList<>(recipe.getHits());

        this.lastPointSpawn = startTime;
        this.currentInterval = INITIAL_INTERVAL;
        this.pointsToSpawn = 1;

        this.hitsCompleted = 0;
        this.perfectHits = 0;
        this.missedPoints = 0;
        this.currentFrame = 0;
        this.active = true;

        // Cache point item once
        this.cachedPointItem = createPointItem();
    }

    public void tick() {
        if (!active) return;

        long now = System.currentTimeMillis();

        // Use iterator to safely remove expired points
        Iterator<ForgePoint> iterator = activePoints.iterator();
        while (iterator.hasNext()) {
            ForgePoint point = iterator.next();
            point.tick();

            if (point.isExpired() && !point.isHit()) {
                missedPoints++;
                point.remove();
                iterator.remove();
            }
        }

        // Spawn new points based on rhythm
        if (now - lastPointSpawn >= currentInterval && hitsCompleted < recipe.getHits()) {
            spawnPoints();
            lastPointSpawn = now;
            updateRhythm();
        }
    }

    private void spawnPoints() {
        int remaining = recipe.getHits() - hitsCompleted;
        int count = Math.min(pointsToSpawn, remaining);

        for (int i = 0; i < count; i++) {
            Location pointLoc = generatePointLocation();
            ForgePoint point = new ForgePoint(pointLoc, POINT_DURATION);
            point.spawn(cachedPointItem);
            activePoints.add(point);
        }
    }

    private Location generatePointLocation() {
        double angle = random.nextDouble() * Math.PI * 2;
        double radius = 0.8 + random.nextDouble() * 0.5;
        double height = 1.0 + random.nextDouble() * 0.6;

        return anvilLocation.clone().add(
                Math.cos(angle) * radius,
                height,
                Math.sin(angle) * radius
        );
    }

    private ItemStack createPointItem() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lStrike!");
            item.setItemMeta(meta);
        }
        return item;
    }

    private void updateRhythm() {
        // Speed up every N hits
        if (hitsCompleted > 0 && hitsCompleted % SPEEDUP_EVERY_N_HITS == 0) {
            currentInterval = Math.max(MIN_INTERVAL, currentInterval - 150);
        }

        // Occasionally spawn multiple points at higher progress
        double progress = getProgress();
        double roll = random.nextDouble();

        if (progress > 0.75 && roll < 0.2) {
            pointsToSpawn = 3;
        } else if (progress > 0.5 && roll < 0.3) {
            pointsToSpawn = 2;
        } else {
            pointsToSpawn = 1;
        }
    }

    public double processHit(UUID hitboxId) {
        // Use indexed loop to avoid CME when removing
        for (int i = 0; i < activePoints.size(); i++) {
            ForgePoint point = activePoints.get(i);
            if (point.matchesHitbox(hitboxId) && point.isActive()) {
                double accuracy = point.hit();
                hitAccuracies.add(accuracy);
                hitsCompleted++;

                if (accuracy >= 0.9) {
                    perfectHits++;
                }

                updateFrame();
                point.remove();
                activePoints.remove(i);

                return accuracy;
            }
        }
        return -1;
    }

    private void updateFrame() {
        currentFrame = recipe.getFrameForProgress(getProgress());
    }

    public boolean isComplete() {
        return hitsCompleted >= recipe.getHits();
    }

    public double getProgress() {
        int totalHits = recipe.getHits();
        return totalHits > 0 ? (double) hitsCompleted / totalHits : 1.0;
    }

    public double getAverageAccuracy() {
        if (hitAccuracies.isEmpty()) return 0.0;

        double sum = 0.0;
        for (double acc : hitAccuracies) {
            sum += acc;
        }
        return sum / hitAccuracies.size();
    }

    public int calculateStarRating() {
        Map<Integer, StarThreshold> thresholds = recipe.getStarThresholds();

        if (thresholds != null && !thresholds.isEmpty()) {
            double avg = getAverageAccuracy();
            for (int star = 5; star >= 0; star--) {
                StarThreshold t = thresholds.get(star);
                if (t != null && t.isMet(perfectHits, avg)) {
                    return star;
                }
            }
            return 0;
        }

        double score = Math.min(1.0, getAverageAccuracy() + recipe.getBias());
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
        active = false;
        cleanup();
    }

    public void cleanup() {
        for (ForgePoint point : activePoints) {
            point.remove();
        }
        activePoints.clear();
    }

    public UUID getSessionId() {
        return playerId;
    }

    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    public double calculateFinalScore() {
        return Math.min(1.0, getAverageAccuracy() + recipe.getBias());
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public ForgeRecipe getRecipe() { return recipe; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
    public int getHitsCompleted() { return hitsCompleted; }
    public int getTotalHits() { return recipe.getHits(); }
    public int getPerfectHits() { return perfectHits; }
    public int getMissedPoints() { return missedPoints; }
    public int getCurrentFrame() { return currentFrame; }
    public boolean isActive() { return active; }
    public List<ForgePoint> getActivePoints() { return activePoints; }
}