package com.simmc.blacksmith.furnace;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

/**
 * Displays temperature as a boss bar for nearby players.
 */
public class TemperatureBar {

    private final FurnaceInstance furnace;
    private BossBar bossBar;
    private boolean spawned;

    private static final double VIEW_DISTANCE = 10.0;
    private static final double VIEW_DISTANCE_SQUARED = VIEW_DISTANCE * VIEW_DISTANCE;

    public TemperatureBar(FurnaceInstance furnace) {
        this.furnace = furnace;
        this.spawned = false;
    }

    public void spawn() {
        if (spawned) return;

        bossBar = Bukkit.createBossBar(
                buildTitle(),
                BarColor.YELLOW,
                BarStyle.SEGMENTED_10
        );
        bossBar.setVisible(true);
        spawned = true;
    }

    public void update() {
        if (!spawned || bossBar == null) return;

        // Update title and progress
        bossBar.setTitle(buildTitle());
        bossBar.setProgress(getTemperatureProgress());
        bossBar.setColor(getBarColor());

        // Update visible players based on distance
        updateVisiblePlayers();
    }

    private String buildTitle() {
        int current = furnace.getCurrentTemperature();
        int max = furnace.getType().getMaxTemperature();
        boolean isIdeal = furnace.getType().isIdealTemperature(current);

        StringBuilder title = new StringBuilder();

        // Temperature icon
        if (current == 0) {
            title.append("§8❄ ");
        } else if (isIdeal) {
            title.append("§a✓ ");
        } else if (current < furnace.getType().getMinIdealTemperature()) {
            title.append("§e⚠ ");
        } else {
            title.append("§c⚠ ");
        }

        // Temperature value
        title.append("§e").append(current).append("°C");

        // Status
        if (furnace.isBurning()) {
            title.append(" §7| §6Burning");
        }

        if (furnace.getCurrentRecipe() != null) {
            int progress = (int) (furnace.getSmeltProgress() * 100);
            title.append(" §7| §fSmelting: §a").append(progress).append("%");
        }

        return title.toString();
    }

    private double getTemperatureProgress() {
        int current = furnace.getCurrentTemperature();
        int max = furnace.getType().getMaxTemperature();
        return max > 0 ? Math.min(1.0, (double) current / max) : 0.0;
    }

    private BarColor getBarColor() {
        int current = furnace.getCurrentTemperature();
        boolean isIdeal = furnace.getType().isIdealTemperature(current);

        if (current == 0) {
            return BarColor.WHITE;
        } else if (isIdeal) {
            return BarColor.GREEN;
        } else if (current < furnace.getType().getMinIdealTemperature()) {
            return BarColor.YELLOW;
        } else {
            return BarColor.RED;
        }
    }

    private void updateVisiblePlayers() {
        Location furnaceLoc = furnace.getLocation();
        if (furnaceLoc.getWorld() == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getWorld().equals(furnaceLoc.getWorld())) {
                bossBar.removePlayer(player);
                continue;
            }

            double distanceSquared = player.getLocation().distanceSquared(furnaceLoc);

            if (distanceSquared <= VIEW_DISTANCE_SQUARED) {
                if (!bossBar.getPlayers().contains(player)) {
                    bossBar.addPlayer(player);
                }
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    public void remove() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
        spawned = false;
    }

    public boolean isSpawned() {
        return spawned;
    }
}