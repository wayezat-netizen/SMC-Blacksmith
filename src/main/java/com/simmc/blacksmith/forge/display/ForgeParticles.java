package com.simmc.blacksmith.forge.display;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Premium particle effects for the forge system.
 */
public class ForgeParticles {

    private static final Random random = new Random();

    /**
     * Ambient fire/heat particles around the anvil.
     */
    public static void spawnAmbientHeat(Location anvilLocation, double intensity) {
        World world = anvilLocation.getWorld();
        if (world == null) return;

        Location center = anvilLocation.clone().add(0.5, 1.2, 0.5);

        // Fire particles
        int fireCount = (int) (3 * intensity);
        for (int i = 0; i < fireCount; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.3;
            double offsetZ = (random.nextDouble() - 0.5) * 0.3;
            world.spawnParticle(Particle.FLAME,
                    center.clone().add(offsetX, random.nextDouble() * 0.2, offsetZ),
                    0, 0, 0.02, 0, 0.01);
        }

        // Smoke particles
        if (random.nextDouble() < 0.3 * intensity) {
            world.spawnParticle(Particle.SMOKE,
                    center.clone().add(0, 0.3, 0),
                    1, 0.1, 0.1, 0.1, 0.01);
        }

        // Occasional ember
        if (random.nextDouble() < 0.1 * intensity) {
            world.spawnParticle(Particle.LAVA,
                    center,
                    1, 0.2, 0.1, 0.2, 0);
        }
    }

    /**
     * Strike particles based on hit accuracy.
     */
    public static void spawnStrikeEffect(Location location, double accuracy) {
        World world = location.getWorld();
        if (world == null) return;

        Location center = location.clone().add(0.5, 1.3, 0.5);

        if (accuracy >= 0.9) {
            // Perfect hit - dramatic sparks
            spawnPerfectHitEffect(center);
        } else if (accuracy >= 0.7) {
            // Good hit - nice sparks
            spawnGoodHitEffect(center);
        } else if (accuracy >= 0.4) {
            // Okay hit - some sparks
            spawnOkayHitEffect(center);
        } else {
            // Miss - dull effect
            spawnMissEffect(center);
        }
    }

    /**
     * Perfect hit - golden sparks flying everywhere.
     */
    private static void spawnPerfectHitEffect(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Radial spark burst
        for (int i = 0; i < 30; i++) {
            double angle = (2 * Math.PI / 30) * i;
            double speed = 0.15 + random.nextDouble() * 0.1;
            Vector velocity = new Vector(
                    Math.cos(angle) * speed,
                    0.1 + random.nextDouble() * 0.15,
                    Math.sin(angle) * speed
            );

            world.spawnParticle(Particle.FLAME,
                    center, 0,
                    velocity.getX(), velocity.getY(), velocity.getZ(),
                    1);
        }

        // Central flash
        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);

        // Golden dust
        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
        world.spawnParticle(Particle.DUST, center, 20, 0.3, 0.3, 0.3, 0, goldDust);

        // Lava sparks
        world.spawnParticle(Particle.LAVA, center, 10, 0.2, 0.1, 0.2, 0);
    }

    /**
     * Good hit - orange sparks.
     */
    private static void spawnGoodHitEffect(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Spark burst
        for (int i = 0; i < 15; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double speed = 0.08 + random.nextDouble() * 0.08;
            Vector velocity = new Vector(
                    Math.cos(angle) * speed,
                    0.05 + random.nextDouble() * 0.1,
                    Math.sin(angle) * speed
            );

            world.spawnParticle(Particle.FLAME,
                    center, 0,
                    velocity.getX(), velocity.getY(), velocity.getZ(),
                    1);
        }

        // Orange dust
        Particle.DustOptions orangeDust = new Particle.DustOptions(Color.fromRGB(255, 140, 0), 1.2f);
        world.spawnParticle(Particle.DUST, center, 12, 0.25, 0.25, 0.25, 0, orangeDust);

        world.spawnParticle(Particle.LAVA, center, 3, 0.15, 0.1, 0.15, 0);
    }

    /**
     * Okay hit - small sparks.
     */
    private static void spawnOkayHitEffect(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Few sparks
        world.spawnParticle(Particle.FLAME, center, 8, 0.15, 0.1, 0.15, 0.02);

        // Gray smoke
        world.spawnParticle(Particle.SMOKE, center, 5, 0.1, 0.1, 0.1, 0.02);
    }

    /**
     * Miss - dull thud effect.
     */
    private static void spawnMissEffect(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Just smoke - no sparks
        world.spawnParticle(Particle.SMOKE, center, 10, 0.2, 0.15, 0.2, 0.03);

        // Gray dust
        Particle.DustOptions grayDust = new Particle.DustOptions(Color.GRAY, 1.0f);
        world.spawnParticle(Particle.DUST, center, 8, 0.2, 0.2, 0.2, 0, grayDust);
    }

    /**
     * Completion celebration effect.
     */
    public static void spawnCompletionEffect(Location location, int starRating) {
        World world = location.getWorld();
        if (world == null) return;

        Location center = location.clone().add(0.5, 1.5, 0.5);

        // Base celebration
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 50, 0.3, 0.5, 0.3, 0.3);

        // Star-based effects
        if (starRating >= 5) {
            // 5 star - legendary effect
            spawnLegendaryCompletion(center);
        } else if (starRating >= 4) {
            // 4 star - epic effect
            spawnEpicCompletion(center);
        } else if (starRating >= 3) {
            // 3 star - good effect
            spawnGoodCompletion(center);
        } else {
            // 1-2 star - basic effect
            spawnBasicCompletion(center);
        }
    }

    private static void spawnLegendaryCompletion(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Golden spiral
        for (int i = 0; i < 100; i++) {
            double t = i * 0.1;
            double radius = 0.5 + t * 0.05;
            double x = Math.cos(t * 2) * radius;
            double z = Math.sin(t * 2) * radius;
            double y = t * 0.05;

            Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
            world.spawnParticle(Particle.DUST,
                    center.clone().add(x, y, z),
                    1, 0, 0, 0, 0, goldDust);
        }

        // Firework burst
        world.spawnParticle(Particle.FIREWORK, center.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

        // End rod sparkles
        world.spawnParticle(Particle.END_ROD, center, 40, 0.5, 0.5, 0.5, 0.05);
    }

    private static void spawnEpicCompletion(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Purple and gold
        Particle.DustOptions purpleDust = new Particle.DustOptions(Color.PURPLE, 1.3f);
        Particle.DustOptions goldDust = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.3f);

        world.spawnParticle(Particle.DUST, center, 30, 0.4, 0.5, 0.4, 0, purpleDust);
        world.spawnParticle(Particle.DUST, center, 20, 0.3, 0.4, 0.3, 0, goldDust);
        world.spawnParticle(Particle.END_ROD, center, 20, 0.4, 0.4, 0.4, 0.03);
    }

    private static void spawnGoodCompletion(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Green and white
        Particle.DustOptions greenDust = new Particle.DustOptions(Color.LIME, 1.2f);
        world.spawnParticle(Particle.DUST, center, 25, 0.35, 0.4, 0.35, 0, greenDust);
        world.spawnParticle(Particle.HAPPY_VILLAGER, center, 15, 0.4, 0.4, 0.4, 0);
    }

    private static void spawnBasicCompletion(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Simple white puff
        world.spawnParticle(Particle.CLOUD, center, 15, 0.3, 0.3, 0.3, 0.02);
    }

    /**
     * Heat shimmer effect around hot metal.
     */
    public static void spawnHeatShimmer(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location center = location.clone().add(0.5, 1.3, 0.5);

        // Subtle heat distortion
        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 0.4;
            double offsetZ = (random.nextDouble() - 0.5) * 0.4;
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    center.clone().add(offsetX, 0, offsetZ),
                    0, 0, 0.03, 0, 0.01);
        }
    }
}