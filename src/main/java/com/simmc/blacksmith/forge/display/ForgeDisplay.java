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
        spawnItemDisplay(center);
        spawnProgressText(center);
        spawned = true;
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
        updateProgressText(session);
    }

    private void updateItemDisplay(ForgeSession session) {
        if (itemDisplay == null || itemDisplay.isDead()) return;

        // Update frame based on progress
        ForgeFrame frame = recipe.getFrame(session.getCurrentFrame());
        if (frame != null) {
            ItemStack current = itemDisplay.getItemStack();
            ItemStack frameItem = frame.createDisplayItem();
            if (current == null || current.getType() != frameItem.getType()) {
                itemDisplay.setItemStack(frameItem);
            }
        }

        // Subtle animation
        float bounce = (float) Math.sin(tick * 0.1) * 0.03f;
        float rotation = (tick * 0.5f) % 360;
        float progress = (float) session.getProgress();

        Transformation t = new Transformation(
                new Vector3f(0, bounce, 0),
                new AxisAngle4f((float) Math.toRadians(-90), 1, 0, 0),
                new Vector3f(1.5f + progress * 0.3f, 1.5f + progress * 0.3f, 1.5f + progress * 0.3f),
                new AxisAngle4f((float) Math.toRadians(rotation * 0.2f), 0, 1, 0)
        );
        itemDisplay.setTransformation(t);
        itemDisplay.setInterpolationDuration(3);
    }

    private void updateProgressText(ForgeSession session) {
        if (progressText == null || progressText.isDead()) return;

        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        int perfect = session.getPerfectHits();

        StringBuilder text = new StringBuilder();
        text.append("§6§l⚒ FORGING ⚒\n");
        text.append("§7Hits: §f").append(hits).append("§7/§f").append(total);

        if (hits > 0) {
            double acc = session.getAverageAccuracy() * 100;
            String accColor = acc >= 80 ? "§a" : acc >= 50 ? "§e" : "§c";
            text.append("\n§7Accuracy: ").append(accColor).append(String.format("%.0f%%", acc));
            text.append("\n§7Perfect: §a").append(perfect);
        }

        progressText.setText(text.toString());
    }

    public void showCompletion(int stars) {
        if (progressText == null || progressText.isDead()) return;

        StringBuilder starDisplay = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            starDisplay.append(i < stars ? "§6★" : "§7☆");
        }

        progressText.setText("§a§l✓ COMPLETE! ✓\n" + starDisplay);
    }

    public void remove() {
        if (itemDisplay != null && !itemDisplay.isDead()) {
            itemDisplay.remove();
        }
        if (progressText != null && !progressText.isDead()) {
            progressText.remove();
        }
        spawned = false;
    }

    public boolean isValid() {
        return spawned && itemDisplay != null && !itemDisplay.isDead();
    }

    public UUID getPlayerId() { return playerId; }
    public Location getAnvilLocation() { return anvilLocation.clone(); }
}