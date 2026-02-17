package com.simmc.blacksmith.forge.display;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles visual display of forge item on anvil during minigame.
 */
public class ForgeDisplay {

    private static final Logger LOGGER = Bukkit.getLogger();

    // Heat glow colors
    private static final Color COLOR_COLD = Color.fromRGB(255, 100, 50);
    private static final Color COLOR_WARM = Color.fromRGB(255, 150, 50);
    private static final Color COLOR_HOT = Color.fromRGB(255, 200, 80);
    private static final Color COLOR_WHITE_HOT = Color.fromRGB(255, 255, 200);

    private final UUID playerId;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;
    private final ForgeDisplaySettings settings;

    private ItemDisplay itemDisplay;
    private BossBar progressBar;
    private boolean spawned;
    private int tick;
    private int lastFrame = -1;

    private float currentScale;

    public ForgeDisplay(UUID playerId, Location anvilLocation, ForgeRecipe recipe) {
        this.playerId = playerId;
        this.anvilLocation = anvilLocation.clone();
        this.recipe = recipe;
        this.settings = recipe.getDisplaySettingsOrDefault();
        this.spawned = false;
        this.tick = 0;
        this.currentScale = settings.baseScale();
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

    /**
     * Spawn item display on top of anvil.
     * Uses configurable offsets from recipe display settings.
     */
    private void spawnItemDisplay(World world) {
        Location itemLoc = calculateDisplayLocation();

        itemDisplay = world.spawn(itemLoc, ItemDisplay.class, display -> {
            ItemStack item = getDisplayItem();

            display.setItemStack(item);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            display.setGlowing(true);
            display.setGlowColorOverride(COLOR_COLD);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setShadowRadius(0.5f);
            display.setShadowStrength(0.8f);

            // Proper transformation to sit on top of anvil
            display.setTransformation(createDisplayTransformation(settings.baseScale()));
            display.setInterpolationDuration(0);
        });
    }

    /**
     * Gets the display item - supports 3-stage items or legacy single item.
     */
    private ItemStack getDisplayItem() {
        return getDisplayItemForStage(0); // Start with stage 0
    }

    /**
     * Gets display item for a specific stage.
     */
    private ItemStack getDisplayItemForStage(int stage) {
        // Check for stage-specific item first (3-stage format)
        if (settings.hasStageItems()) {
            ForgeDisplaySettings.DisplayItem stageItem = settings.getStageItem(stage);
            if (stageItem != null && stageItem.isValid()) {
                ItemStack item = getItemFromProvider(stageItem.type(), stageItem.id());
                if (item != null) return item;
            }
        }

        // Check legacy single display item
        if (settings.hasCustomDisplayItem()) {
            ItemStack customItem = getCustomDisplayItem();
            if (customItem != null) {
                return customItem;
            }
        }

        // Fallback to recipe frame
        ForgeFrame frame = recipe.getFrame(stage);
        if (frame != null) {
            return frame.createDisplayItem();
        }

        return new ItemStack(Material.IRON_INGOT);
    }

    /**
     * Gets item from provider (CE/SMC/Minecraft).
     */
    private ItemStack getItemFromProvider(String type, String id) {
        if (id == null || id.isEmpty()) return null;

        try {
            SMCBlacksmith plugin = SMCBlacksmith.getInstance();
            ItemProviderRegistry registry = plugin.getItemRegistry();

            if (registry != null) {
                ItemStack item = registry.getItem(type, id, 1);
                if (item != null && !item.getType().isAir()) {
                    return item;
                }
            }
        } catch (Exception e) {
            LOGGER.warning("[ForgeDisplay] Failed to get item: " + type + ":" + id);
        }

        return null;
    }

    /**
     * Gets custom display item from CE/SMC/Minecraft (legacy format).
     */
    private ItemStack getCustomDisplayItem() {
        return getItemFromProvider(settings.displayItemType(), settings.displayItemId());
    }

    private void spawnProgressBar(Player player) {
        String title = "§6⚒ §e§lFORGING §7[§f0§7/§f" + recipe.getHits() + "§7]";
        progressBar = Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SEGMENTED_10);
        progressBar.setProgress(0);
        progressBar.addPlayer(player);
    }

    private void playSpawnEffects(World world) {
        Location loc = calculateDisplayLocation();
        world.spawnParticle(Particle.FLAME, loc, 10, 0.12, 0.08, 0.12, 0.02);
        world.spawnParticle(Particle.SMOKE, loc, 5, 0.08, 0.04, 0.08, 0.01);
        world.playSound(anvilLocation, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.2f);
        world.playSound(anvilLocation, Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
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

        // Update display item based on current stage (0, 1, 2)
        int currentFrame = session.getCurrentFrame();
        if (currentFrame != lastFrame) {
            // Get stage-specific item (supports 3-stage CE items)
            ItemStack stageItem = getDisplayItemForStage(currentFrame);
            itemDisplay.setItemStack(stageItem);
            playFrameChangeEffect();
            lastFrame = currentFrame;
        }

        double progress = session.getProgress();
        float heatIntensity = (float) progress;

        // Update glow color based on heat
        itemDisplay.setGlowColorOverride(getHeatColor(progress));

        // Calculate scale with animation
        float targetScale = settings.baseScale() + (settings.maxScale() - settings.baseScale()) * heatIntensity;
        currentScale = lerp(currentScale, targetScale, 0.1f);

        // Subtle breathing animation
        float time = tick * 0.05f;
        float breathe = (float) Math.sin(time * 2) * 0.02f * heatIntensity;

        float finalScale = currentScale + breathe;

        // CLIENT FIX: Update transformation to keep item on top of anvil
        itemDisplay.setTransformation(createDisplayTransformation(finalScale));
        itemDisplay.setInterpolationDuration(2);
        itemDisplay.setInterpolationDelay(0);
    }

    private void playFrameChangeEffect() {
        if (itemDisplay == null) return;

        World world = itemDisplay.getWorld();
        Location loc = itemDisplay.getLocation();

        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 8, 0.1, 0.05, 0.1, 0.02);
        world.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
    }

    private Color getHeatColor(double progress) {
        if (progress < 0.25) return lerpColor(COLOR_COLD, COLOR_WARM, (float) (progress / 0.25));
        if (progress < 0.5) return lerpColor(COLOR_WARM, COLOR_HOT, (float) ((progress - 0.25) / 0.25));
        if (progress < 0.75) return lerpColor(COLOR_HOT, COLOR_WHITE_HOT, (float) ((progress - 0.5) / 0.25));
        return COLOR_WHITE_HOT;
    }

    private Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int blue = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return Color.fromRGB(r, g, blue);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ==================== DISPLAY LOCATION & TRANSFORMATION ====================
    private Location calculateDisplayLocation() {
        // Get configured offsets (default: center of block, on top surface)
        double offsetX = settings.offsetX();  // 0.5 = center
        double offsetY = settings.offsetY();  // Height above anvil block (1.0 = on top surface)
        double offsetZ = settings.offsetZ();  // 0.5 = center

        return anvilLocation.clone().add(offsetX, offsetY, offsetZ);
    }

    private Transformation createDisplayTransformation(float scale) {
        Vector3f translation = new Vector3f(0, 0, 0);
        Vector3f scaleVec = new Vector3f(scale, scale, scale);
        Quaternionf rightRotation = new Quaternionf();

        Quaternionf leftRotation;
        if (settings.layFlat()) {
            // Lay flat on the anvil surface - rotate 90 degrees on X axis
            leftRotation = new Quaternionf().rotateX((float) Math.toRadians(-90));
        } else {
            // Stand upright
            leftRotation = new Quaternionf();
        }

        return new Transformation(translation, leftRotation, scaleVec, rightRotation);
    }

    // ==================== BOSS BAR ====================

    private void updateBossBar(ForgeSession session) {
        if (progressBar == null) return;

        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        double progress = session.getProgress();
        double accuracy = session.getAverageAccuracy() * 100;

        progressBar.setProgress(Math.min(1.0, progress));
        progressBar.setTitle(buildProgressTitle(hits, total, accuracy, session));
        progressBar.setColor(getProgressBarColor(progress, accuracy));
    }

    private String buildProgressTitle(int hits, int total, double accuracy, ForgeSession session) {
        StringBuilder title = new StringBuilder();

        double progress = session.getProgress();
        if (progress < 0.3) title.append("§6⚒ ");
        else if (progress < 0.7) title.append("§e⚒ ");
        else title.append("§f⚒ ");

        if (hits > 0) {
            title.append(getRatingText(accuracy)).append(" ");
        } else {
            title.append("§e§lFORGING ");
        }

        title.append("§7[§f").append(hits).append("§7/§f").append(total).append("§7]");

        if (hits > 0) {
            title.append(" §8| ").append(getAccuracyColor(accuracy)).append((int) accuracy).append("%");
        }

        // Timeout warning
        long timeUntilTimeout = session.getTimeUntilTimeout();
        if (timeUntilTimeout < 10000 && timeUntilTimeout > 0) {
            int seconds = (int) (timeUntilTimeout / 1000);
            title.append(" §c§l⚠ ").append(seconds).append("s");
        }

        return title.toString();
    }

    private String getRatingText(double accuracy) {
        if (accuracy >= 95) return "§a§l★ PERFECT";
        if (accuracy >= 85) return "§a§lGREAT";
        if (accuracy >= 70) return "§e§lGOOD";
        if (accuracy >= 50) return "§6OKAY";
        return "§cPOOR";
    }

    private String getAccuracyColor(double accuracy) {
        if (accuracy >= 90) return "§a";
        if (accuracy >= 70) return "§e";
        if (accuracy >= 50) return "§6";
        return "§c";
    }

    private BarColor getProgressBarColor(double progress, double accuracy) {
        if (accuracy >= 90) return BarColor.GREEN;
        if (accuracy >= 70) return BarColor.YELLOW;
        if (progress > 0.8) return BarColor.WHITE;
        return BarColor.YELLOW;
    }

    // ==================== PARTICLES ====================

    /**
     * Spawn ambient particles during forging.
     */
    private void spawnAmbientParticles(ForgeSession session) {
        World world = anvilLocation.getWorld();
        if (world == null) return;

        Location particleLoc = calculateDisplayLocation();
        double heat = session.getProgress();

        // Spawn particles less frequently for performance
        if (tick % 5 == 0 && heat > 0.1) {
            int count = 1 + (int) (heat * 1.5);
            world.spawnParticle(Particle.SMALL_FLAME, particleLoc, count, 0.06, 0.02, 0.06, 0.004);
        }

        // Spark particles
        if (heat > 0.4 && tick % 10 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, particleLoc.clone().add(0, 0.1, 0), 1, 0.08, 0.04, 0.08, 0.015);
        }

        // Smoke particles
        if (heat > 0.6 && tick % 15 == 0) {
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc.clone().add(0, 0.15, 0), 1, 0.03, 0, 0.03, 0.002);
        }

        // Ambient sound
        if (tick % 60 == 0 && heat > 0.3) {
            world.playSound(anvilLocation, Sound.BLOCK_FIRE_AMBIENT, 0.25f, 1.5f);
        }
    }

    // ==================== COMPLETION ====================

    public void showCompletion(int stars) {
        updateCompletionBar(stars);
        playCompletionEffects(stars);
    }

    private void updateCompletionBar(int stars) {
        if (progressBar == null) return;

        StringBuilder title = new StringBuilder();

        if (stars >= 5) title.append("§6§l✦ §e§lMASTERPIECE! §6§l✦ ");
        else if (stars >= 4) title.append("§a§l✓ EXCELLENT! ");
        else if (stars >= 3) title.append("§a✓ §fComplete! ");
        else if (stars >= 1) title.append("§e✓ §7Finished ");
        else title.append("§c✗ §7Failed ");

        for (int i = 0; i < 5; i++) {
            title.append(i < stars ? "§6★" : "§8☆");
        }

        progressBar.setTitle(title.toString());
        progressBar.setProgress(1.0);

        if (stars >= 5) progressBar.setColor(BarColor.PURPLE);
        else if (stars >= 4) progressBar.setColor(BarColor.GREEN);
        else if (stars >= 2) progressBar.setColor(BarColor.YELLOW);
        else progressBar.setColor(BarColor.RED);
    }

    private void playCompletionEffects(int stars) {
        World world = anvilLocation.getWorld();
        if (world == null) return;

        Location loc = calculateDisplayLocation().add(0, 0.2, 0);

        switch (stars) {
            case 5 -> {
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 50, 0.3, 0.5, 0.3, 0.3);
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 60, 0.4, 0.4, 0.4, 0.15);
                world.spawnParticle(Particle.FLAME, loc, 30, 0.3, 0.3, 0.3, 0.08);
                world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
            case 4 -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 40, 0.3, 0.35, 0.3, 0.1);
                world.spawnParticle(Particle.FLAME, loc, 20, 0.25, 0.25, 0.25, 0.06);
                world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
            }
            case 3 -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.2, 0.25, 0.2, 0.06);
                world.spawnParticle(Particle.CRIT, loc, 15, 0.2, 0.2, 0.2, 0.05);
                world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
            case 2 -> {
                world.spawnParticle(Particle.CRIT, loc, 10, 0.15, 0.15, 0.15, 0.03);
                world.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.7f, 0.9f);
            }
            case 1 -> {
                world.spawnParticle(Particle.SMOKE, loc, 12, 0.15, 0.15, 0.15, 0.02);
                world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
            }
            default -> {
                world.spawnParticle(Particle.LARGE_SMOKE, loc, 15, 0.2, 0.2, 0.2, 0.03);
                world.playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            }
        }
    }

    // ==================== HIT FEEDBACK ====================

    public void onHit(double accuracy) {
        if (itemDisplay == null || itemDisplay.isDead()) return;

        World world = itemDisplay.getWorld();
        Location loc = itemDisplay.getLocation();

        // Brief scale pulse on hit
        float hitScale = currentScale * 1.15f;
        itemDisplay.setTransformation(createDisplayTransformation(hitScale));
        itemDisplay.setInterpolationDuration(1);

        if (accuracy >= 0.9) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 12, 0.08, 0.05, 0.08, 0.03);
        } else if (accuracy >= 0.7) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 6, 0.06, 0.04, 0.06, 0.02);
        } else {
            world.spawnParticle(Particle.CRIT, loc, 4, 0.05, 0.03, 0.05, 0.01);
        }
    }

    // ==================== GETTERS ====================

    public boolean isValid() {
        return spawned && itemDisplay != null && !itemDisplay.isDead();
    }

    public UUID getPlayerId() { return playerId; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
    public boolean isSpawned() { return spawned; }
}