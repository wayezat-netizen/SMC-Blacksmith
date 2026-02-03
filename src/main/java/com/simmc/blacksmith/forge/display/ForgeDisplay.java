package com.simmc.blacksmith.forge.display;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Professional forge display with item on anvil and boss bar progress.
 */
public class ForgeDisplay {

    private final UUID playerId;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;

    private ItemDisplay itemDisplay;
    private BossBar progressBar;
    private boolean spawned;
    private int tick;
    private int lastFrame = -1;
    private double lastProgress = 0;

    // Animation constants
    private static final float BASE_SCALE = 0.8f;
    private static final float MAX_SCALE = 1.1f;

    public ForgeDisplay(UUID playerId, Location anvilLocation, ForgeRecipe recipe) {
        this.playerId = playerId;
        this.anvilLocation = anvilLocation.clone();
        this.recipe = recipe;
        this.spawned = false;
        this.tick = 0;
    }

    public void spawn() {
        if (spawned) return;

        World world = anvilLocation.getWorld();
        if (world == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;

        try {
            // Create item display on anvil
            Location itemLoc = anvilLocation.clone().add(0.5, 1.05, 0.5);

            itemDisplay = world.spawn(itemLoc, ItemDisplay.class, display -> {
                ForgeFrame frame = recipe.getFrame(0);
                ItemStack item = frame != null ? frame.createDisplayItem() : new ItemStack(Material.IRON_INGOT);
                display.setItemStack(item);
                display.setBillboard(Display.Billboard.FIXED);
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
                display.setGlowing(true);
                display.setGlowColorOverride(Color.ORANGE);

                // Lay flat on anvil
                Transformation t = new Transformation(
                        new Vector3f(0, 0, 0),
                        new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0),
                        new Vector3f(BASE_SCALE, BASE_SCALE, BASE_SCALE),
                        new AxisAngle4f(0, 0, 0, 1)
                );
                display.setTransformation(t);
            });

            // Create boss bar for progress
            progressBar = Bukkit.createBossBar(
                    "§6⚒ §fForging... §7[0/" + recipe.getHits() + "]",
                    BarColor.YELLOW,
                    BarStyle.SEGMENTED_10
            );
            progressBar.setProgress(0);
            progressBar.addPlayer(player);

            // Initial spawn particles
            world.spawnParticle(Particle.FLAME, itemLoc, 10, 0.2, 0.1, 0.2, 0.02);
            world.playSound(anvilLocation, Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.5f);

            spawned = true;

        } catch (Exception e) {
            remove();
        }
    }

    public void tick(ForgeSession session) {
        if (!spawned) return;
        tick++;

        updateItemDisplay(session);
        updateBossBar(session);
        spawnAmbientEffects(session);
    }

    private void updateItemDisplay(ForgeSession session) {
        if (itemDisplay == null || itemDisplay.isDead()) return;

        // Update frame based on progress
        int currentFrame = session.getCurrentFrame();
        if (currentFrame != lastFrame) {
            ForgeFrame frame = recipe.getFrame(currentFrame);
            if (frame != null) {
                itemDisplay.setItemStack(frame.createDisplayItem());
            }
            lastFrame = currentFrame;
        }

        double progress = session.getProgress();

        // Heat glow color progression
        if (progress < 0.3) {
            itemDisplay.setGlowColorOverride(Color.ORANGE);
        } else if (progress < 0.6) {
            itemDisplay.setGlowColorOverride(Color.fromRGB(255, 200, 50));  // Bright orange-yellow
        } else if (progress < 0.9) {
            itemDisplay.setGlowColorOverride(Color.YELLOW);
        } else {
            itemDisplay.setGlowColorOverride(Color.WHITE);  // White hot
        }

        // Smooth scaling animation with heat wobble
        float heatIntensity = (float) progress;
        float wobble = (float) Math.sin(tick * 0.2) * 0.02f * heatIntensity;
        float scale = BASE_SCALE + (MAX_SCALE - BASE_SCALE) * heatIntensity;
        float yOffset = (float) Math.sin(tick * 0.15) * 0.01f * heatIntensity;

        Transformation t = new Transformation(
                new Vector3f(0, yOffset, 0),
                new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0),
                new Vector3f(scale + wobble, scale + wobble, scale + wobble),
                new AxisAngle4f(0, 0, 0, 1)
        );
        itemDisplay.setTransformation(t);
        itemDisplay.setInterpolationDuration(2);

        lastProgress = progress;
    }

    private void updateBossBar(ForgeSession session) {
        if (progressBar == null) return;

        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        double progress = session.getProgress();
        double accuracy = session.getAverageAccuracy() * 100;

        progressBar.setProgress(Math.min(1.0, progress));

        // Build title with stats
        StringBuilder title = new StringBuilder();
        title.append("§6⚒ §f");

        // Add accuracy rating
        if (hits > 0) {
            String accColor;
            String rating;
            if (accuracy >= 90) {
                accColor = "§a";
                rating = "PERFECT";
            } else if (accuracy >= 70) {
                accColor = "§e";
                rating = "GREAT";
            } else if (accuracy >= 50) {
                accColor = "§6";
                rating = "GOOD";
            } else {
                accColor = "§c";
                rating = "POOR";
            }
            title.append(accColor).append(rating).append(" ");
        }

        title.append("§7[§f").append(hits).append("§7/§f").append(total).append("§7]");

        if (hits > 0) {
            title.append(" §8| §7Accuracy: ");
            if (accuracy >= 80) title.append("§a");
            else if (accuracy >= 50) title.append("§e");
            else title.append("§c");
            title.append((int) accuracy).append("%");
        }

        progressBar.setTitle(title.toString());

        // Update bar color based on progress
        if (progress < 0.3) {
            progressBar.setColor(BarColor.YELLOW);
        } else if (progress < 0.7) {
            progressBar.setColor(BarColor.GREEN);
        } else {
            progressBar.setColor(BarColor.WHITE);
        }
    }

    private void spawnAmbientEffects(ForgeSession session) {
        World world = anvilLocation.getWorld();
        if (world == null) return;

        Location particleLoc = anvilLocation.clone().add(0.5, 1.15, 0.5);
        double heat = session.getProgress();

        // Heat shimmer
        if (tick % 4 == 0) {
            world.spawnParticle(Particle.SMALL_FLAME, particleLoc, 1, 0.1, 0.02, 0.1, 0.002);
        }

        // Sparks as heat increases
        if (heat > 0.3 && tick % 8 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, particleLoc, 1, 0.08, 0.05, 0.08, 0.01);
        }

        // Smoke wisps
        if (heat > 0.5 && tick % 12 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 1, 0.05, 0, 0.05, 0.005);
        }
    }

    public void showCompletion(int stars) {
        Player player = Bukkit.getPlayer(playerId);

        // Update boss bar
        if (progressBar != null) {
            StringBuilder starDisplay = new StringBuilder("§a§l✓ COMPLETE! ");
            for (int i = 0; i < 5; i++) {
                starDisplay.append(i < stars ? "§6★" : "§8☆");
            }
            progressBar.setTitle(starDisplay.toString());
            progressBar.setProgress(1.0);
            progressBar.setColor(stars >= 4 ? BarColor.PURPLE : BarColor.GREEN);
        }

        // Completion effects
        World world = anvilLocation.getWorld();
        if (world != null) {
            Location loc = anvilLocation.clone().add(0.5, 1.3, 0.5);

            if (stars >= 5) {
                // Legendary completion
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 0.3, 0.3, 0.3, 0.1);
                world.spawnParticle(Particle.FLAME, loc, 20, 0.2, 0.2, 0.2, 0.05);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 15, 0.3, 0.3, 0.3, 0.2);
                world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            } else if (stars >= 4) {
                // Excellent
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 0.25, 0.25, 0.25, 0.08);
                world.spawnParticle(Particle.FLAME, loc, 12, 0.15, 0.15, 0.15, 0.04);
                world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
            } else if (stars >= 3) {
                // Good
                world.spawnParticle(Particle.CRIT, loc, 15, 0.2, 0.2, 0.2, 0.05);
                world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            } else {
                // Basic
                world.spawnParticle(Particle.SMOKE, loc, 8, 0.15, 0.15, 0.15, 0.02);
                world.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.8f, 0.9f);
            }
        }

        // Make item glow brighter on completion
        if (itemDisplay != null && !itemDisplay.isDead()) {
            itemDisplay.setGlowColorOverride(stars >= 4 ? Color.PURPLE : Color.GREEN);
        }
    }

    public void remove() {
        if (itemDisplay != null) {
            try { if (!itemDisplay.isDead()) itemDisplay.remove(); } catch (Exception ignored) {}
            itemDisplay = null;
        }

        if (progressBar != null) {
            progressBar.removeAll();
            progressBar = null;
        }

        spawned = false;
    }

    public boolean isValid() {
        return spawned && itemDisplay != null && !itemDisplay.isDead();
    }

    public UUID getPlayerId() { return playerId; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
}