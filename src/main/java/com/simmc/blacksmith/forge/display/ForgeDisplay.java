package com.simmc.blacksmith.forge.display;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
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

public class ForgeDisplay {

    private final UUID playerId;
    private final Location anvilLocation;
    private final ForgeRecipe recipe;

    private ItemDisplay itemDisplay;
    private TextDisplay progressText;
    private boolean spawned;
    private int tick;
    private int lastFrame = -1;
    private int lastHits = -1;

    // Cached transformation components
    private final Vector3f translationVector = new Vector3f();
    private final Vector3f scaleVector = new Vector3f();
    private final AxisAngle4f rotationX = new AxisAngle4f();
    private final AxisAngle4f rotationY = new AxisAngle4f();

    // Cached text builder
    private final StringBuilder textBuilder = new StringBuilder(128);

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

        Location center = anvilLocation.clone().add(0.5, 1.0, 0.5);

        try {
            spawnItemDisplay(center);
            spawnProgressText(center);
            spawned = true;
        } catch (Exception e) {
            remove();
        }
    }

    private void spawnItemDisplay(Location center) {
        Location loc = center.clone().add(0, 0.5, 0);
        World world = loc.getWorld();

        itemDisplay = world.spawn(loc, ItemDisplay.class, display -> {
            ForgeFrame frame = recipe.getFrame(0);
            ItemStack item = frame != null ? frame.createDisplayItem() : new ItemStack(Material.IRON_INGOT);
            display.setItemStack(item);
            display.setBillboard(Display.Billboard.FIXED);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);

            Transformation t = new Transformation(
                    new Vector3f(0, 0, 0),
                    new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0),
                    new Vector3f(1.5f, 1.5f, 1.5f),
                    new AxisAngle4f(0, 0, 0, 1)
            );
            display.setTransformation(t);
        });
    }

    private void spawnProgressText(Location center) {
        Location loc = center.clone().add(0, 1.8, 0);
        World world = loc.getWorld();

        progressText = world.spawn(loc, TextDisplay.class, display -> {
            display.setText("§6§l⚒ FORGING ⚒\n§7Hits: §f0§7/§f" + recipe.getHits());
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setDefaultBackground(false);
            display.setBackgroundColor(Color.fromARGB(180, 0, 0, 0));
        });
    }

    public void tick(ForgeSession session) {
        if (!spawned) return;
        tick++;

        updateItemDisplay(session);

        // Only update text every 5 ticks or when hits change
        int currentHits = session.getHitsCompleted();
        if (tick % 5 == 0 || currentHits != lastHits) {
            updateProgressText(session);
            lastHits = currentHits;
        }
    }

    private void updateItemDisplay(ForgeSession session) {
        if (itemDisplay == null || itemDisplay.isDead()) return;

        // Update frame only if changed
        int currentFrame = session.getCurrentFrame();
        if (currentFrame != lastFrame) {
            ForgeFrame frame = recipe.getFrame(currentFrame);
            if (frame != null) {
                itemDisplay.setItemStack(frame.createDisplayItem());
            }
            lastFrame = currentFrame;
        }

        // Animation
        float bounce = (float) Math.sin(tick * 0.1) * 0.03f;
        float rotation = (tick * 0.5f) % 360;
        float progress = (float) session.getProgress();
        float scale = 1.5f + progress * 0.3f;

        translationVector.set(0, bounce, 0);
        rotationX.set((float) Math.toRadians(-90), 1, 0, 0);
        scaleVector.set(scale, scale, scale);
        rotationY.set((float) Math.toRadians(rotation * 0.2f), 0, 1, 0);

        Transformation t = new Transformation(translationVector, rotationX, scaleVector, rotationY);
        itemDisplay.setTransformation(t);
        itemDisplay.setInterpolationDuration(3);
    }

    private void updateProgressText(ForgeSession session) {
        if (progressText == null || progressText.isDead()) return;

        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        int perfect = session.getPerfectHits();

        textBuilder.setLength(0);
        textBuilder.append("§6§l⚒ FORGING ⚒\n");
        textBuilder.append("§7Hits: §f").append(hits).append("§7/§f").append(total);

        if (hits > 0) {
            double acc = session.getAverageAccuracy() * 100;
            String accColor = acc >= 80 ? "§a" : acc >= 50 ? "§e" : "§c";
            textBuilder.append("\n§7Accuracy: ").append(accColor);
            appendFormattedPercent(textBuilder, acc);
            textBuilder.append("\n§7Perfect: §a").append(perfect);
        }

        progressText.setText(textBuilder.toString());
    }

    private void appendFormattedPercent(StringBuilder sb, double value) {
        int intPart = (int) value;
        sb.append(intPart).append("%");
    }

    public void showCompletion(int stars) {
        if (progressText == null || progressText.isDead()) return;

        textBuilder.setLength(0);
        textBuilder.append("§a§l✓ COMPLETE! ✓\n");
        for (int i = 0; i < 5; i++) {
            textBuilder.append(i < stars ? "§6★" : "§7☆");
        }

        progressText.setText(textBuilder.toString());
    }

    public void remove() {
        if (itemDisplay != null) {
            try {
                if (!itemDisplay.isDead()) itemDisplay.remove();
            } catch (Exception ignored) {}
            itemDisplay = null;
        }
        if (progressText != null) {
            try {
                if (!progressText.isDead()) progressText.remove();
            } catch (Exception ignored) {}
            progressText = null;
        }
        spawned = false;
    }

    public boolean isValid() {
        return spawned && itemDisplay != null && !itemDisplay.isDead();
    }

    public UUID getPlayerId() { return playerId; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
}