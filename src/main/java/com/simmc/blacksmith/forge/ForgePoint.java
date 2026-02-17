package com.simmc.blacksmith.forge;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.logging.Level;

/**
 * A clickable hit target that appears during forging minigame.
 */
public class ForgePoint {

    // Timing windows (percentage of duration)
    private static final double PERFECT_WINDOW = 0.35;
    private static final double GREAT_WINDOW = 0.55;
    private static final double GOOD_WINDOW = 0.75;

    // Accuracy scores for each window
    private static final double PERFECT_ACCURACY = 1.0;
    private static final double GREAT_ACCURACY = 0.88;
    private static final double GOOD_ACCURACY = 0.70;
    private static final double POOR_ACCURACY = 0.40;

    private final UUID id;
    private final Location location;
    private final long spawnTime;
    private final long duration;

    private Interaction hitbox;
    private BlockDisplay targetDisplay;
    private boolean hit;
    private boolean expired;
    private boolean removed;
    private int tickCount;

    public ForgePoint(Location location, long durationMs) {
        this.id = UUID.randomUUID();
        this.location = location.clone();
        this.spawnTime = System.currentTimeMillis();
        this.duration = durationMs;
        this.hit = false;
        this.expired = false;
        this.removed = false;
        this.tickCount = 0;
    }

    // ==================== LIFECYCLE ====================

    public void spawn() {
        World world = location.getWorld();
        if (world == null) return;

        try {
            spawnHitbox(world);
            spawnTargetDisplay(world);
            playSpawnEffects(world);
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[SMCBlacksmith] Failed to spawn forge point", e);
            remove();
        }
    }

    private void spawnHitbox(World world) {
        hitbox = world.spawn(location.clone().add(0, 0.15, 0), Interaction.class, entity -> {
            entity.setInteractionWidth(0.4f);
            entity.setInteractionHeight(0.4f);
            entity.setResponsive(true);
        });
    }

    private void spawnTargetDisplay(World world) {
        targetDisplay = world.spawn(location, BlockDisplay.class, display -> {
            display.setBlock(Bukkit.createBlockData(Material.RED_CONCRETE));
            display.setBrightness(new Display.Brightness(15, 15));
            display.setGlowing(true);
            display.setGlowColorOverride(Color.RED);
            display.setTransformation(createTransformation(0.3f));
        });
    }

    private void playSpawnEffects(World world) {
        Location effectLoc = location.clone().add(0, 0.1, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, effectLoc, 3, 0.08, 0.04, 0.08, 0.02);
        world.playSound(location, Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.5f);
    }

    public void tick() {
        if (hit || expired || removed) return;
        tickCount++;

        long elapsed = System.currentTimeMillis() - spawnTime;
        if (elapsed >= duration) {
            expired = true;
            playExpireEffect();
            return;
        }

        double progress = (double) elapsed / duration;
        updateTargetDisplay(progress);
        spawnBeaconParticle();
    }

    public void remove() {
        if (removed) return;
        removed = true;

        safeRemove(hitbox);
        safeRemove(targetDisplay);
        hitbox = null;
        targetDisplay = null;
    }

    private void safeRemove(org.bukkit.entity.Entity entity) {
        if (entity != null && !entity.isDead()) {
            try {
                entity.remove();
            } catch (Exception ignored) {}
        }
    }

    // ==================== HIT PROCESSING ====================

    public double hit() {
        if (hit || expired || removed) return 0.0;

        hit = true;
        long elapsed = System.currentTimeMillis() - spawnTime;
        double progress = (double) elapsed / duration;

        double accuracy = calculateAccuracy(progress);
        playHitEffect(accuracy);
        return accuracy;
    }

    private double calculateAccuracy(double progress) {
        if (progress < PERFECT_WINDOW) return PERFECT_ACCURACY;
        if (progress < GREAT_WINDOW) return GREAT_ACCURACY;
        if (progress < GOOD_WINDOW) return GOOD_ACCURACY;
        return POOR_ACCURACY;
    }

    // ==================== VISUAL UPDATES ====================

    private void updateTargetDisplay(double progress) {
        if (targetDisplay == null || targetDisplay.isDead()) return;

        TargetState state = getTargetState(progress);

        targetDisplay.setBlock(Bukkit.createBlockData(state.material));
        targetDisplay.setGlowColorOverride(state.color);
        targetDisplay.setTransformation(createTransformation(state.scale));
        targetDisplay.setInterpolationDuration(2);
    }

    private TargetState getTargetState(double progress) {
        if (progress < PERFECT_WINDOW) {
            return new TargetState(Material.RED_CONCRETE, Color.RED, 0.35f);
        } else if (progress < GREAT_WINDOW) {
            return new TargetState(Material.ORANGE_CONCRETE, Color.ORANGE, 0.30f);
        } else if (progress < GOOD_WINDOW) {
            return new TargetState(Material.YELLOW_CONCRETE, Color.YELLOW, 0.25f);
        } else {
            // Pulse when almost expired
            float scale = (tickCount % 3 < 2) ? 0.15f : 0.20f;
            return new TargetState(Material.GRAY_CONCRETE, Color.GRAY, scale);
        }
    }

    private record TargetState(Material material, Color color, float scale) {}

    private Transformation createTransformation(float scale) {
        return new Transformation(
                new Vector3f(-scale / 2, 0.0f, -scale / 2),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, 0.05f, scale),
                new AxisAngle4f(0, 0, 0, 1)
        );
    }

    private void spawnBeaconParticle() {
        if (tickCount % 6 != 0) return;
        if (targetDisplay == null || targetDisplay.isDead()) return;

        World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.SMALL_FLAME, location.clone().add(0, 0.15, 0),
                    1, 0.02, 0.05, 0.02, 0.001);
        }
    }

    // ==================== EFFECTS ====================

    private void playHitEffect(double accuracy) {
        World world = location.getWorld();
        if (world == null) return;

        Location effectLoc = location.clone().add(0, 0.1, 0);

        if (accuracy >= 0.9) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, effectLoc, 15, 0.12, 0.08, 0.12, 0.05);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.3f);
        } else if (accuracy >= 0.7) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, effectLoc, 10, 0.1, 0.06, 0.1, 0.03);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        } else if (accuracy >= 0.5) {
            world.spawnParticle(Particle.CRIT, effectLoc, 6, 0.08, 0.05, 0.08, 0.02);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.6f, 0.9f);
        } else {
            world.spawnParticle(Particle.SMOKE, effectLoc, 4, 0.06, 0.04, 0.06, 0.01);
            world.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f);
        }
    }

    private void playExpireEffect() {
        World world = location.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.SMOKE, location.clone().add(0, 0.1, 0), 6, 0.1, 0.06, 0.1, 0.02);
        world.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 1.0f);
    }

    // ==================== GETTERS ====================

    public UUID getId() { return id; }
    public Location getLocation() { return location.clone(); }
    public boolean isHit() { return hit; }
    public boolean isExpired() { return expired; }
    public boolean isActive() { return !hit && !expired && !removed; }
    public Interaction getHitbox() { return hitbox; }

    public boolean matchesHitbox(UUID entityId) {
        return hitbox != null && !hitbox.isDead() && hitbox.getUniqueId().equals(entityId);
    }
}