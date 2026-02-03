package com.simmc.blacksmith.forge.display;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
import com.simmc.blacksmith.forge.ForgeTemperature;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * Premium 3D forge display system.
 * Creates and manages all visual entities for the forging minigame.
 */
public class ForgeDisplay {

    // Entity references
    private ItemDisplay itemDisplay;        // The item being forged
    private ItemDisplay anvilOverlay;       // Optional anvil enhancement
    private TextDisplay timingBar;          // The timing indicator
    private TextDisplay progressText;       // Progress/stats display
    private TextDisplay instructionText;    // Instructions for player

    // State
    private final UUID playerId;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;
    private boolean spawned;
    private int animationTick;

    // Timing bar state
    private double indicatorPosition;       // 0.0 to 1.0
    private int indicatorDirection;         // 1 or -1

    // Visual settings
    private static final float ITEM_HEIGHT = 1.3f;
    private static final float TIMING_BAR_HEIGHT = 2.0f;
    private static final float PROGRESS_HEIGHT = 2.4f;
    private static final float INSTRUCTION_HEIGHT = 0.8f;

    // Timing bar visual settings
    private static final int BAR_LENGTH = 30;
    private static final String BAR_TARGET_CHAR = "█";
    private static final String BAR_INDICATOR_CHAR = "▼";
    private static final String BAR_EMPTY_CHAR = "░";

    public ForgeDisplay(UUID playerId, Location anvilLocation, ForgeRecipe recipe) {
        this.playerId = playerId;
        this.anvilLocation = anvilLocation.clone();
        this.recipe = recipe;
        this.spawned = false;
        this.animationTick = 0;
        this.indicatorPosition = 0.0;
        this.indicatorDirection = 1;
    }

    /**
     * Spawns all display entities in the world.
     */
    public void spawn() {
        if (spawned) return;

        World world = anvilLocation.getWorld();
        if (world == null) return;

        // Calculate center position (top of anvil block)
        Location center = anvilLocation.clone().add(0.5, 1.0, 0.5);

        // Spawn the forging item display
        spawnItemDisplay(center);

        // Spawn timing bar above
        spawnTimingBar(center);

        // Spawn progress text
        spawnProgressText(center);

        // Spawn instruction text below
        spawnInstructionText(center);

        spawned = true;
    }

    /**
     * Spawns the item being forged.
     */
    private void spawnItemDisplay(Location center) {
        Location itemLoc = center.clone().add(0, ITEM_HEIGHT, 0);
        World world = itemLoc.getWorld();
        if (world == null) return;

        itemDisplay = world.spawn(itemLoc, ItemDisplay.class, display -> {
            // Get the initial frame item
            ForgeFrame frame = recipe.getFrame(0);
            ItemStack displayItem;
            if (frame != null) {
                displayItem = frame.createDisplayItem();
            } else {
                displayItem = new ItemStack(Material.IRON_INGOT);
            }

            display.setItemStack(displayItem);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);

            // Initial transformation - flat on anvil, facing up
            Transformation transform = new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0), // Lay flat
                    new Vector3f(1.5f, 1.5f, 1.5f), // Scale up
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setTransformation(transform);
            display.setInterpolationDuration(2);
            display.setInterpolationDelay(0);
        });
    }

    /**
     * Spawns the timing bar display.
     */
    private void spawnTimingBar(Location center) {
        Location barLoc = center.clone().add(0, TIMING_BAR_HEIGHT, 0);
        World world = barLoc.getWorld();
        if (world == null) return;

        timingBar = world.spawn(barLoc, TextDisplay.class, display -> {
            display.setText(generateTimingBarText(0.5, recipe.getTargetSize()));
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(180, 0, 0, 0));
            display.setShadowed(true);

            Transformation transform = new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.2f, 1.2f, 1.2f),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setTransformation(transform);
        });
    }

    /**
     * Spawns the progress text display.
     */
    private void spawnProgressText(Location center) {
        Location textLoc = center.clone().add(0, PROGRESS_HEIGHT, 0);
        World world = textLoc.getWorld();
        if (world == null) return;

        progressText = world.spawn(textLoc, TextDisplay.class, display -> {
            display.setText("§6§l⚒ FORGING ⚒\n§7Hits: §f0§7/§f" + recipe.getHits());
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(200, 30, 30, 30));
            display.setShadowed(true);

            Transformation transform = new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setTransformation(transform);
        });
    }

    /**
     * Spawns instruction text near the anvil.
     */
    private void spawnInstructionText(Location center) {
        Location textLoc = center.clone().add(0, INSTRUCTION_HEIGHT, 0);
        World world = textLoc.getWorld();
        if (world == null) return;

        instructionText = world.spawn(textLoc, TextDisplay.class, display -> {
            display.setText("§e§lLEFT CLICK §7to strike!");
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setSeeThrough(false);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(150, 0, 0, 0));

            Transformation transform = new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(0.8f, 0.8f, 0.8f),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setTransformation(transform);
        });
    }

    /**
     * Updates the display every tick.
     * Called from the main tick loop.
     */
    public void tick(ForgeSession session, double targetPosition) {
        if (!spawned) return;

        animationTick++;

        // Update timing bar indicator
        updateIndicator();
        updateTimingBar(targetPosition, session.getRecipe().getTargetSize());

        // Animate the item (subtle bounce/glow effect)
        animateItem(session);

        // Update progress text
        updateProgressText(session);
    }

    /**
     * Updates the indicator position (bouncing back and forth).
     */
    private void updateIndicator() {
        double speed = 0.04; // Adjust for difficulty
        indicatorPosition += speed * indicatorDirection;

        if (indicatorPosition >= 1.0) {
            indicatorPosition = 1.0;
            indicatorDirection = -1;
        } else if (indicatorPosition <= 0.0) {
            indicatorPosition = 0.0;
            indicatorDirection = 1;
        }
    }

    /**
     * Gets the current indicator position for hit detection.
     */
    public double getIndicatorPosition() {
        return indicatorPosition;
    }

    /**
     * Updates the timing bar visual.
     */
    private void updateTimingBar(double targetPosition, double targetSize) {
        if (timingBar == null || timingBar.isDead()) return;

        timingBar.setText(generateTimingBarText(targetPosition, targetSize));
    }

    /**
     * Generates the timing bar text with colors.
     */
    private String generateTimingBarText(double targetPosition, double targetSize) {
        StringBuilder bar = new StringBuilder();

        double targetStart = targetPosition - targetSize / 2.0;
        double targetEnd = targetPosition + targetSize / 2.0;

        int indicatorIdx = (int) (indicatorPosition * (BAR_LENGTH - 1));

        // Top line: indicator arrow
        bar.append("§7");
        for (int i = 0; i < BAR_LENGTH; i++) {
            if (i == indicatorIdx) {
                bar.append("§e§l").append(BAR_INDICATOR_CHAR).append("§r");
            } else {
                bar.append(" ");
            }
        }
        bar.append("\n");

        // Main bar line
        bar.append("§8[");
        for (int i = 0; i < BAR_LENGTH; i++) {
            double pos = (double) i / (BAR_LENGTH - 1);

            if (i == indicatorIdx) {
                // Indicator position
                bar.append("§e§l|§r");
            } else if (pos >= targetStart && pos <= targetEnd) {
                // Target zone - gradient from edges to center
                double distFromCenter = Math.abs(pos - targetPosition) / (targetSize / 2.0);
                if (distFromCenter < 0.3) {
                    bar.append("§a§l").append(BAR_TARGET_CHAR); // Perfect center
                } else if (distFromCenter < 0.6) {
                    bar.append("§a").append(BAR_TARGET_CHAR);   // Good
                } else {
                    bar.append("§2").append(BAR_TARGET_CHAR);   // Edge of target
                }
            } else if (pos < 0.1 || pos > 0.9) {
                // Danger zones at edges
                bar.append("§c").append(BAR_EMPTY_CHAR);
            } else {
                // Neutral zone
                bar.append("§7").append(BAR_EMPTY_CHAR);
            }
        }
        bar.append("§8]");

        return bar.toString();
    }

    /**
     * Animates the forging item with subtle effects.
     */
    private void animateItem(ForgeSession session) {
        if (itemDisplay == null || itemDisplay.isDead()) return;

        // Calculate heat color based on progress
        float progress = (float) session.getProgress();

        // Subtle bounce animation
        float bounce = (float) Math.sin(animationTick * 0.15) * 0.02f;

        // Subtle rotation
        float rotation = (animationTick * 0.5f) % 360;

        // Update transformation with animation
        Transformation transform = new Transformation(
                new Vector3f(0, bounce, 0),
                new AxisAngle4f((float) Math.toRadians(-90 + Math.sin(animationTick * 0.1) * 3), 1, 0, 0),
                new Vector3f(1.5f + progress * 0.2f, 1.5f + progress * 0.2f, 1.5f + progress * 0.2f),
                new AxisAngle4f((float) Math.toRadians(rotation * 0.1f), 0, 1, 0)
        );

        itemDisplay.setTransformation(transform);
        itemDisplay.setInterpolationDuration(2);

        // Update item frame based on progress
        int frameIndex = session.getCurrentFrame();
        ForgeFrame frame = recipe.getFrame(frameIndex);
        if (frame != null) {
            ItemStack currentItem = itemDisplay.getItemStack();
            ItemStack frameItem = frame.createDisplayItem();

            // Only update if different to reduce packet spam
            if (currentItem == null || currentItem.getType() != frameItem.getType()) {
                itemDisplay.setItemStack(frameItem);
            }
        }
    }

    /**
     * Updates the progress text display.
     */
    private void updateProgressText(ForgeSession session) {
        if (progressText == null || progressText.isDead()) return;

        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        double accuracy = session.getAverageAccuracy();
        int perfectHits = session.getPerfectHits();

        StringBuilder text = new StringBuilder();
        text.append("§6§l⚒ FORGING ⚒\n");
        text.append("§7Hits: §f").append(hits).append("§7/§f").append(total).append("\n");

        if (hits > 0) {
            String accColor = accuracy >= 0.8 ? "§a" : accuracy >= 0.5 ? "§e" : "§c";
            text.append("§7Accuracy: ").append(accColor).append((int)(accuracy * 100)).append("%\n");
            text.append("§7Perfect: §a").append(perfectHits);
        }

        progressText.setText(text.toString());
    }

    /**
     * Called when player strikes - visual feedback.
     */
    public void onStrike(double accuracy) {
        if (!spawned) return;

        // Flash the item based on accuracy
        if (itemDisplay != null && !itemDisplay.isDead()) {
            // Quick scale pulse
            float scale = accuracy >= 0.8 ? 2.0f : 1.7f;
            Transformation transform = new Transformation(
                    new Vector3f(0, 0.1f, 0), // Pop up
                    new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            itemDisplay.setTransformation(transform);
            itemDisplay.setInterpolationDuration(1);
        }

        // Update instruction text with feedback
        if (instructionText != null && !instructionText.isDead()) {
            String feedback;
            if (accuracy >= 0.9) {
                feedback = "§a§l★ PERFECT! ★";
            } else if (accuracy >= 0.7) {
                feedback = "§a§lGood!";
            } else if (accuracy >= 0.4) {
                feedback = "§e§lOkay";
            } else {
                feedback = "§c§lMiss!";
            }
            instructionText.setText(feedback);
        }
    }

    /**
     * Resets instruction text after strike feedback.
     */
    public void resetInstructionText() {
        if (instructionText != null && !instructionText.isDead()) {
            instructionText.setText("§e§lLEFT CLICK §7to strike!");
        }
    }

    /**
     * Shows completion animation.
     */
    public void showCompletion(int starRating) {
        if (progressText != null && !progressText.isDead()) {
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                if (i < starRating) {
                    stars.append("§6★");
                } else {
                    stars.append("§7☆");
                }
            }

            progressText.setText("§a§l✓ COMPLETE! ✓\n" + stars.toString());

            // Scale up for emphasis
            Transformation transform = new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1.5f, 1.5f, 1.5f),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            progressText.setTransformation(transform);
        }

        // Hide timing bar
        if (timingBar != null && !timingBar.isDead()) {
            timingBar.setText("");
        }

        // Hide instructions
        if (instructionText != null && !instructionText.isDead()) {
            instructionText.setText("");
        }
    }

    /**
     * Removes all display entities.
     */
    public void remove() {
        if (itemDisplay != null && !itemDisplay.isDead()) {
            itemDisplay.remove();
        }
        if (anvilOverlay != null && !anvilOverlay.isDead()) {
            anvilOverlay.remove();
        }
        if (timingBar != null && !timingBar.isDead()) {
            timingBar.remove();
        }
        if (progressText != null && !progressText.isDead()) {
            progressText.remove();
        }
        if (instructionText != null && !instructionText.isDead()) {
            instructionText.remove();
        }

        spawned = false;
    }

    /**
     * Checks if the display is still valid.
     */
    public boolean isValid() {
        return spawned && itemDisplay != null && !itemDisplay.isDead();
    }

    // ==================== GETTERS ====================

    public UUID getPlayerId() {
        return playerId;
    }

    public Location getAnvilLocation() {
        return anvilLocation.clone();
    }

    public boolean isSpawned() {
        return spawned;
    }

    public void updateTemperature(ForgeTemperature temperature) {
        if (progressText == null || progressText.isDead()) return;
    }
}