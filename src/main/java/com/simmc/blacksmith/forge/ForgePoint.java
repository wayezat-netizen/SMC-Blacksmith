package com.simmc.blacksmith.forge;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Professional forge hit point with ring indicator and timing feedback.
 */
public class ForgePoint {

    private final UUID id;
    private final Location location;
    private final long spawnTime;
    private final long duration;

    private Interaction hitbox;
    private TextDisplay ringDisplay;
    private boolean hit;
    private boolean expired;
    private boolean removed;
    private int tickCount;

    // Timing windows
    private static final double PERFECT_WINDOW = 0.25;
    private static final double GREAT_WINDOW = 0.45;
    private static final double GOOD_WINDOW = 0.65;

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

    public void spawn() {
        World world = location.getWorld();
        if (world == null) return;

        try {
            // Larger hitbox for easier clicking
            hitbox = world.spawn(location, Interaction.class, entity -> {
                entity.setInteractionWidth(0.7f);
                entity.setInteractionHeight(0.7f);
                entity.setResponsive(true);
            });

            // Ring indicator using text display with special character
            ringDisplay = world.spawn(location.clone().add(0, 0.1, 0), TextDisplay.class, display -> {
                display.setText("§e●");  // Start yellow
                display.setBillboard(Display.Billboard.CENTER);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setDefaultBackground(false);
                display.setSeeThrough(true);

                Transformation t = new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(2.0f, 2.0f, 2.0f),
                        new AxisAngle4f(0, 0, 0, 1)
                );
                display.setTransformation(t);
            });

            // Spawn effect
            world.spawnParticle(Particle.ELECTRIC_SPARK, location, 5, 0.1, 0.05, 0.1, 0.02);
            world.playSound(location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f);

        } catch (Exception e) {
            remove();
        }
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

        World world = location.getWorld();
        if (world == null) return;

        double progress = (double) elapsed / duration;

        // Update ring color and size based on timing
        updateRingDisplay(progress);

        // Particle effects
        spawnTimingParticles(progress);
    }

    private void updateRingDisplay(double progress) {
        if (ringDisplay == null || ringDisplay.isDead()) return;

        String color;
        float scale;

        if (progress < PERFECT_WINDOW) {
            color = "§a";  // Green - perfect window
            scale = 2.5f - (float) (progress / PERFECT_WINDOW) * 0.5f;
        } else if (progress < GREAT_WINDOW) {
            color = "§e";  // Yellow - great window
            scale = 2.0f - (float) ((progress - PERFECT_WINDOW) / (GREAT_WINDOW - PERFECT_WINDOW)) * 0.3f;
        } else if (progress < GOOD_WINDOW) {
            color = "§6";  // Orange - good window
            scale = 1.7f - (float) ((progress - GREAT_WINDOW) / (GOOD_WINDOW - GREAT_WINDOW)) * 0.2f;
        } else {
            color = "§c";  // Red - late
            scale = 1.5f - (float) ((progress - GOOD_WINDOW) / (1.0 - GOOD_WINDOW)) * 0.3f;

            // Pulsing effect when late
            if (tickCount % 4 < 2) {
                color = "§4";
            }
        }

        ringDisplay.setText(color + "●");

        Transformation t = new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 0, 1)
        );
        ringDisplay.setTransformation(t);
        ringDisplay.setInterpolationDuration(2);
    }

    private void spawnTimingParticles(double progress) {
        World world = location.getWorld();
        if (world == null) return;

        // Ambient heat particles
        if (tickCount % 3 == 0) {
            world.spawnParticle(Particle.SMALL_FLAME, location, 1, 0.08, 0.03, 0.08, 0.003);
        }

        // Urgency particles when running out of time
        if (progress > 0.7 && tickCount % 2 == 0) {
            world.spawnParticle(Particle.SMOKE, location, 1, 0.05, 0.02, 0.05, 0.01);
        }
    }

    public double hit() {
        if (hit || expired || removed) return 0.0;

        hit = true;
        long elapsed = System.currentTimeMillis() - spawnTime;
        double progress = (double) elapsed / duration;

        // Calculate accuracy based on timing window
        double accuracy;
        String rating;

        if (progress < PERFECT_WINDOW) {
            accuracy = 1.0;
            rating = "PERFECT";
        } else if (progress < GREAT_WINDOW) {
            accuracy = 0.85;
            rating = "GREAT";
        } else if (progress < GOOD_WINDOW) {
            accuracy = 0.65;
            rating = "GOOD";
        } else {
            accuracy = 0.35;
            rating = "LATE";
        }

        playHitEffect(accuracy, rating);
        return accuracy;
    }

    private void playHitEffect(double accuracy, String rating) {
        World world = location.getWorld();
        if (world == null) return;

        if (accuracy >= 0.9) {
            // Perfect - big satisfying effect
            world.spawnParticle(Particle.ELECTRIC_SPARK, location, 20, 0.2, 0.15, 0.2, 0.08);
            world.spawnParticle(Particle.FLAME, location, 8, 0.15, 0.1, 0.15, 0.03);
            world.spawnParticle(Particle.ENCHANTED_HIT, location, 10, 0.2, 0.2, 0.2, 0.1);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 1.0f, 1.4f);
            world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.8f);
        } else if (accuracy >= 0.7) {
            // Great
            world.spawnParticle(Particle.ELECTRIC_SPARK, location, 12, 0.15, 0.1, 0.15, 0.05);
            world.spawnParticle(Particle.FLAME, location, 5, 0.1, 0.08, 0.1, 0.02);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.9f, 1.2f);
        } else if (accuracy >= 0.5) {
            // Good
            world.spawnParticle(Particle.CRIT, location, 8, 0.12, 0.08, 0.12, 0.04);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.7f, 1.0f);
        } else {
            // Late/weak
            world.spawnParticle(Particle.SMOKE, location, 6, 0.1, 0.08, 0.1, 0.02);
            world.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.8f);
        }
    }

    private void playExpireEffect() {
        World world = location.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.SMOKE, location, 8, 0.15, 0.1, 0.15, 0.03);
        world.spawnParticle(Particle.LARGE_SMOKE, location, 2, 0.1, 0.05, 0.1, 0.01);
        world.playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.2f);
    }

    public void remove() {
        if (removed) return;
        removed = true;

        if (hitbox != null) {
            try { if (!hitbox.isDead()) hitbox.remove(); } catch (Exception ignored) {}
            hitbox = null;
        }

        if (ringDisplay != null) {
            try { if (!ringDisplay.isDead()) ringDisplay.remove(); } catch (Exception ignored) {}
            ringDisplay = null;
        }
    }

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