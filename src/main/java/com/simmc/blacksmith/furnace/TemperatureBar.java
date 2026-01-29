package com.simmc.blacksmith.furnace;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class TemperatureBar {

    private final FurnaceInstance furnace;
    private TextDisplay displayEntity;
    private boolean spawned;

    public TemperatureBar(FurnaceInstance furnace) {
        this.furnace = furnace;
        this.spawned = false;
    }

    public void spawn() {
        if (spawned) return;

        Location loc = furnace.getLocation().clone().add(0.5, 1.5, 0.5);
        if (loc.getWorld() == null) return;

        displayEntity = (TextDisplay) loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        displayEntity.setBillboard(Display.Billboard.CENTER);
        displayEntity.setAlignment(TextDisplay.TextAlignment.CENTER);
        displayEntity.setSeeThrough(false);
        displayEntity.setDefaultBackground(false);

        Transformation transform = new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 0, 1),
                new Vector3f(1, 1, 1),
                new AxisAngle4f(0, 0, 0, 1)
        );
        displayEntity.setTransformation(transform);

        update();
        spawned = true;
    }

    public void update() {
        if (displayEntity == null || displayEntity.isDead()) return;

        displayEntity.setText(formatTemperature());
    }

    public void remove() {
        if (displayEntity != null && !displayEntity.isDead()) {
            displayEntity.remove();
        }
        displayEntity = null;
        spawned = false;
    }

    public String formatTemperature() {
        int temp = furnace.getCurrentTemperature();
        int max = furnace.getType().getMaxTemperature();
        boolean isIdeal = furnace.getType().isIdealTemperature(temp);

        String color;
        if (temp == 0) {
            color = "§7";
        } else if (isIdeal) {
            color = "§a";
        } else if (temp < furnace.getType().getMinIdealTemperature()) {
            color = "§e";
        } else {
            color = "§c";
        }

        String indicator = isIdeal ? "§a⬤" : "§7○";
        return indicator + " " + color + temp + "°C §7/ §f" + max + "°C";
    }

    public boolean isSpawned() {
        return spawned;
    }

    public FurnaceInstance getFurnace() {
        return furnace;
    }
}