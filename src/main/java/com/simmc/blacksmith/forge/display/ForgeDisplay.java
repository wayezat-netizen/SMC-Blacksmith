package com.simmc.blacksmith.forge.display;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
import org.bukkit.*;
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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles visual display of forge item on anvil during minigame.
 * Shows progress, animations, and completion effects.
 */
public class ForgeDisplay {

    private static final Logger LOGGER = Bukkit.getLogger();

    // Display constants
    private static final float BASE_SCALE = 0.8f;
    private static final float MAX_SCALE = 1.1f;
    private static final double OFFSET_X = 0.5;
    private static final double OFFSET_Y = 1.05;
    private static final double OFFSET_Z = 0.5;

    // Color progression for heat glow
    private static final Color COLOR_COLD = Color.ORANGE;
    private static final Color COLOR_WARM = Color.fromRGB(255, 200, 50);
    private static final Color COLOR_HOT = Color.YELLOW;
    private static final Color COLOR_WHITE_HOT = Color.WHITE;

    private final UUID playerId;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;

    private ItemDisplay itemDisplay;
    private BossBar progressBar;
    private boolean spawned;
    private int tick;
    private int lastFrame = -1;

    public ForgeDisplay(UUID playerId, Location anvilLocation, ForgeRecipe recipe) {
        this.playerId = playerId;
        this.anvilLocation = anvilLocation.clone();
        this.recipe = recipe;
        this.spawned = false;
        this.tick = 0;
    }

    // ==================== LIFECYCLE ====================

    public void spawn() {
        if (spawned) return;

        World world = anvilLocation.getWorld();
        Player player = Bukkit.getPlayer(playerId);
        if (world == null || player == null) return;

        try {
            spawnItemDisplay(world);
            spawnProgressBar(player);
            playSpawnEffects(world);
            spawned = true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[SMCBlacksmith] Failed to spawn forge display", e);
            remove();
        }
    }

    private void spawnItemDisplay(World world) {
        Location itemLoc = getDisplayLocation();

        itemDisplay = world.spawn(itemLoc, ItemDisplay.class, display -> {
            ForgeFrame frame = recipe.getFrame(0);
            ItemStack item = frame != null ? frame.createDisplayItem() : new ItemStack(Material.IRON_INGOT);

            display.setItemStack(item);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            display.setGlowing(true);
            display.setGlowColorOverride(COLOR_COLD);
            display.setTransformation(createTransformation(BASE_SCALE, 0, 0));
        });
    }

    private void spawnProgressBar(Player player) {
        String title = "§6⚒ §fForging... §7[0/" + recipe.getHits() + "]";
        progressBar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SEGMENTED_10);
        progressBar.setProgress(0);
        progressBar.addPlayer(player);
    }

    private void playSpawnEffects(World world) {
        Location loc = getDisplayLocation();
        world.spawnParticle(Particle.FLAME, loc, 10, 0.2, 0.1, 0.2, 0.02);
        world.playSound(anvilLocation, Sound.ITEM_FIRECHARGE_USE, 0.6f, 1.5f);
    }

    public void remove() {
        if (progressBar != null) {
            progressBar.removeAll();
            progressBar = null;
        }

        if (itemDisplay != null && !itemDisplay.isDead()) {
            try {
                itemDisplay.remove();
            } catch (Exception ignored) {}
            itemDisplay = null;
        }

        spawned = false;
    }

    // ==================== TICK UPDATE ====================

    public void tick(ForgeSession session) {
        if (!spawned) return;
        tick++;

        updateItemDisplay(session);
        updateBossBar(session);
        spawnAmbientParticles(session);
    }

    private void updateItemDisplay(ForgeSession session) {
        if (itemDisplay == null || itemDisplay.isDead()) return;

        // Update frame if changed
        int currentFrame = session.getCurrentFrame();
        if (currentFrame != lastFrame) {
            ForgeFrame frame = recipe.getFrame(currentFrame);
            if (frame != null) {
                itemDisplay.setItemStack(frame.createDisplayItem());
            }
            lastFrame = currentFrame;
        }

        double progress = session.getProgress();

        // Update glow color based on heat
        itemDisplay.setGlowColorOverride(getHeatColor(progress));

        // Calculate animation values
        float heatIntensity = (float) progress;
        float wobble = (float) Math.sin(tick * 0.2) * 0.02f * heatIntensity;
        float scale = BASE_SCALE + (MAX_SCALE - BASE_SCALE) * heatIntensity;
        float yOffset = (float) Math.sin(tick * 0.15) * 0.01f * heatIntensity;

        itemDisplay.setTransformation(createTransformation(scale + wobble, yOffset, 0));
        itemDisplay.setInterpolationDuration(2);
    }

    private Color getHeatColor(double progress) {
        if (progress < 0.3) return COLOR_COLD;
        if (progress < 0.6) return COLOR_WARM;
        if (progress < 0.9) return COLOR_HOT;
        return COLOR_WHITE_HOT;
    }

    private void updateBossBar(ForgeSession session) {
        if (progressBar == null) return;

        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        double progress = session.getProgress();
        double accuracy = session.getAverageAccuracy() * 100;

        progressBar.setProgress(Math.min(1.0, progress));
        progressBar.setTitle(buildProgressTitle(hits, total, accuracy));
        progressBar.setColor(getProgressBarColor(progress));
    }

    private String buildProgressTitle(int hits, int total, double accuracy) {
        StringBuilder title = new StringBuilder("§6⚒ §f");

        if (hits > 0) {
            title.append(getRatingText(accuracy)).append(" ");
        }

        title.append("§7[§f").append(hits).append("§7/§f").append(total).append("§7]");

        if (hits > 0) {
            title.append(" §8| §7Acc: ")
                    .append(getAccuracyColor(accuracy))
                    .append((int) accuracy).append("%");
        }

        return title.toString();
    }

    private String getRatingText(double accuracy) {
        if (accuracy >= 90) return "§aPERFECT";
        if (accuracy >= 70) return "§eGREAT";
        if (accuracy >= 50) return "§6GOOD";
        return "§cPOOR";
    }

    private String getAccuracyColor(double accuracy) {
        if (accuracy >= 80) return "§a";
        if (accuracy >= 50) return "§e";
        return "§c";
    }

    private BarColor getProgressBarColor(double progress) {
        if (progress < 0.3) return BarColor.YELLOW;
        if (progress < 0.7) return BarColor.GREEN;
        return BarColor.WHITE;
    }

    private void spawnAmbientParticles(ForgeSession session) {
        World world = anvilLocation.getWorld();
        if (world == null) return;

        Location particleLoc = getDisplayLocation().add(0, 0.1, 0);
        double heat = session.getProgress();

        if (tick % 4 == 0) {
            world.spawnParticle(Particle.SMALL_FLAME, particleLoc, 1, 0.1, 0.02, 0.1, 0.002);
        }

        if (heat > 0.3 && tick % 8 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, particleLoc, 1, 0.08, 0.05, 0.08, 0.01);
        }

        if (heat > 0.5 && tick % 12 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 1, 0.05, 0, 0.05, 0.005);
        }
    }

    // ==================== COMPLETION ====================

    public void showCompletion(int stars) {
        updateCompletionBar(stars);
        playCompletionEffects(stars);
    }

    private void updateCompletionBar(int stars) {
        if (progressBar == null) return;

        StringBuilder starDisplay = new StringBuilder("§a§l✓ COMPLETE! ");
        for (int i = 0; i < 5; i++) {
            starDisplay.append(i < stars ? "§6★" : "§8☆");
        }

        progressBar.setTitle(starDisplay.toString());
        progressBar.setProgress(1.0);
        progressBar.setColor(stars >= 4 ? BarColor.PURPLE : BarColor.GREEN);
    }

    private void playCompletionEffects(int stars) {
        World world = anvilLocation.getWorld();
        if (world == null) return;

        Location loc = getDisplayLocation().add(0, 0.25, 0);

        if (stars >= 5) {
            // Perfect - spectacular
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 0.3, 0.3, 0.3, 0.1);
            world.spawnParticle(Particle.FLAME, loc, 20, 0.2, 0.2, 0.2, 0.05);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 15, 0.3, 0.3, 0.3, 0.2);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
        } else if (stars >= 4) {
            // Great
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 25, 0.25, 0.25, 0.25, 0.08);
            world.spawnParticle(Particle.FLAME, loc, 12, 0.15, 0.15, 0.15, 0.04);
            world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
        } else if (stars >= 3) {
            // Good
            world.spawnParticle(Particle.CRIT, loc, 15, 0.2, 0.2, 0.2, 0.05);
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            // Poor
            world.spawnParticle(Particle.SMOKE, loc, 8, 0.15, 0.15, 0.15, 0.02);
            world.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.8f, 0.9f);
        }
    }

    // ==================== UTILITIES ====================

    private Location getDisplayLocation() {
        return anvilLocation.clone().add(OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    private Transformation createTransformation(float scale, float yOffset, float rotation) {
        return new Transformation(
                new Vector3f(0, yOffset, 0),
                new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(rotation, 0, 1, 0)
        );
    }

    // ==================== GETTERS ====================

    public boolean isValid() {
        return spawned && itemDisplay != null && !itemDisplay.isDead();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getAnvilLocation() {
        return anvilLocation.clone();
    }

    public boolean isSpawned() {
        return spawned;
    }
}