package com.simmc.blacksmith.forge;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

public class ForgePoint {

    private final UUID id;
    private final Location location;
    private final long spawnTime;
    private final long duration;

    private ItemDisplay display;
    private Interaction hitbox;
    private boolean hit;
    private boolean expired;
    private boolean removed;

    // Cached transformation components to reduce object creation
    private final Vector3f translationVector = new Vector3f();
    private final Vector3f scaleVector = new Vector3f();
    private final AxisAngle4f rotationAngle = new AxisAngle4f();
    private final AxisAngle4f zeroAngle = new AxisAngle4f(0, 0, 0, 1);

    public ForgePoint(Location location, long durationMs) {
        this.id = UUID.randomUUID();
        this.location = location.clone();
        this.spawnTime = System.currentTimeMillis();
        this.duration = durationMs;
        this.hit = false;
        this.expired = false;
        this.removed = false;
    }

    public void spawn(ItemStack pointItem) {
        World world = location.getWorld();
        if (world == null) return;

        try {
            // Spawn visual indicator
            display = world.spawn(location, ItemDisplay.class, entity -> {
                entity.setItemStack(pointItem.clone());
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setGlowing(true);
                entity.setGlowColorOverride(Color.YELLOW);

                Transformation t = new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f(0, 0, 0, 1),
                        new Vector3f(0.6f, 0.6f, 0.6f),
                        new AxisAngle4f(0, 0, 0, 1)
                );
                entity.setTransformation(t);
            });

            // Spawn clickable hitbox
            hitbox = world.spawn(location, Interaction.class, entity -> {
                entity.setInteractionWidth(0.8f);
                entity.setInteractionHeight(0.8f);
                entity.setResponsive(true);
            });

            // Spawn particles (limited count)
            world.spawnParticle(Particle.ELECTRIC_SPARK, location, 8, 0.15, 0.15, 0.15, 0.03);
            world.playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f);

        } catch (Exception e) {
            // Clean up on spawn failure
            remove();
        }
    }

    public void tick() {
        if (hit || expired || removed) return;

        long elapsed = System.currentTimeMillis() - spawnTime;
        if (elapsed >= duration) {
            expired = true;
            return;
        }

        // Only update display if it exists and is valid
        if (display == null || display.isDead()) return;

        float progress = (float) elapsed / duration;
        float scale = 0.5f + 0.2f * (float) Math.sin(elapsed * 0.01);

        // Update glow color based on time remaining
        if (progress > 0.7f) {
            display.setGlowColorOverride(Color.RED);
        } else if (progress > 0.5f) {
            display.setGlowColorOverride(Color.ORANGE);
        }

        // Reuse cached vectors
        translationVector.set(0, (float) Math.sin(elapsed * 0.005) * 0.05f, 0);
        rotationAngle.set((float) (elapsed * 0.003), 0, 1, 0);
        scaleVector.set(scale, scale, scale);

        Transformation t = new Transformation(translationVector, rotationAngle, scaleVector, zeroAngle);
        display.setTransformation(t);
        display.setInterpolationDuration(2);
    }

    public double hit() {
        if (hit || expired || removed) return 0.0;

        hit = true;
        long elapsed = System.currentTimeMillis() - spawnTime;
        double timeRatio = (double) elapsed / duration;

        // Calculate accuracy based on timing
        double accuracy;
        if (timeRatio < 0.3) {
            accuracy = 1.0;
        } else if (timeRatio < 0.5) {
            accuracy = 0.9;
        } else if (timeRatio < 0.7) {
            accuracy = 0.7;
        } else {
            accuracy = 0.4;
        }

        playHitEffect(accuracy);
        return accuracy;
    }

    private void playHitEffect(double accuracy) {
        World world = location.getWorld();
        if (world == null) return;

        if (accuracy >= 0.9) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, location, 12, 0.25, 0.25, 0.25, 0.08);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.9f, 1.4f);
            world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
        } else if (accuracy >= 0.7) {
            world.spawnParticle(Particle.CRIT, location, 8, 0.2, 0.2, 0.2, 0.08);
            world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.9f, 1.1f);
        } else {
            world.spawnParticle(Particle.SMOKE, location, 6, 0.15, 0.15, 0.15, 0.03);
            world.playSound(location, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
        }
    }

    public void remove() {
        if (removed) return;
        removed = true;

        if (display != null) {
            try {
                if (!display.isDead()) display.remove();
            } catch (Exception ignored) {}
            display = null;
        }

        if (hitbox != null) {
            try {
                if (!hitbox.isDead()) hitbox.remove();
            } catch (Exception ignored) {}
            hitbox = null;
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