package com.simmc.blacksmith.furnace;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;

/**
 * Displays furnace temperature as a boss bar for nearby players.
 * Shows temperature, status, and smelting progress.
 */
public class TemperatureBar {

    private static final double VIEW_DISTANCE = 10.0;
    private static final double VIEW_DISTANCE_SQUARED = VIEW_DISTANCE * VIEW_DISTANCE;

    private final FurnaceInstance furnace;
    private BossBar bossBar;
    private boolean spawned;

    public TemperatureBar(FurnaceInstance furnace) {
        this.furnace = furnace;
        this.spawned = false;
    }

    // ==================== LIFECYCLE ====================

    public void spawn() {
        if (spawned) return;

        bossBar = Bukkit.createBossBar(
                buildTitle(),
                getBarColor(),
                BarStyle.SEGMENTED_10
        );
        bossBar.setVisible(true);
        spawned = true;
    }

    public void remove() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
        spawned = false;
    }

    public void update() {
        if (!spawned || bossBar == null) return;

        bossBar.setTitle(buildTitle());
        bossBar.setProgress(getTemperatureProgress());
        bossBar.setColor(getBarColor());
        updateVisiblePlayers();
    }

    // ==================== TITLE BUILDING ====================

    private String buildTitle() {
        FurnaceType type = furnace.getType();
        int current = furnace.getCurrentTemperature();
        boolean isIdeal = type.isIdealTemperature(current);

        StringBuilder title = new StringBuilder();

        // Status icon
        title.append(getStatusIcon(current, isIdeal));

        // Temperature
        title.append("§e").append(current).append("°C");

        // Burning indicator
        if (furnace.isBurning()) {
            title.append(" §7| §6🔥 Burning");
        }

        // Smelting progress
        FurnaceRecipe recipe = furnace.getCurrentRecipe();
        if (recipe != null) {
            appendSmeltingProgress(title, isIdeal);
        }

        return title.toString();
    }

    private String getStatusIcon(int temp, boolean isIdeal) {
        if (temp == 0) {
            return "§8❄ "; // Cold
        } else if (isIdeal) {
            return "§a✓ "; // Ideal
        } else if (temp < furnace.getType().getMinIdealTemperature()) {
            return "§e⚠ "; // Too low
        } else {
            return "§c⚠ "; // Too high
        }
    }

    private void appendSmeltingProgress(StringBuilder title, boolean isIdeal) {
        int progress = (int) (furnace.getSmeltProgress() * 100);
        title.append(" §7| §fSmelting: ");

        if (isIdeal) {
            title.append("§a");
        } else {
            title.append("§c");
        }

        title.append(progress).append("%");

        // Warning if outside ideal range too long
        if (!isIdeal && furnace.getTimeOutsideIdealRange() > 2000) {
            title.append(" §4⚠");
        }
    }

    // ==================== BAR PROPERTIES ====================

    private double getTemperatureProgress() {
        int current = furnace.getCurrentTemperature();
        int max = furnace.getType().getMaxTemperature();

        if (max <= 0) return 0.0;
        return Math.min(1.0, Math.max(0.0, (double) current / max));
    }

    private BarColor getBarColor() {
        int current = furnace.getCurrentTemperature();
        FurnaceType type = furnace.getType();

        if (current == 0) {
            return BarColor.WHITE;
        } else if (type.isIdealTemperature(current)) {
            return BarColor.GREEN;
        } else if (current < type.getMinIdealTemperature()) {
            return BarColor.YELLOW;
        } else {
            return BarColor.RED;
        }
    }

    // ==================== PLAYER VISIBILITY ====================

    private void updateVisiblePlayers() {
        Location furnaceLoc = furnace.getLocation();
        if (furnaceLoc.getWorld() == null) return;

        Set<Player> currentPlayers = new HashSet<>(bossBar.getPlayers());

        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean shouldSee = shouldPlayerSeeBar(player, furnaceLoc);
            boolean currentlySees = currentPlayers.contains(player);

            if (shouldSee && !currentlySees) {
                bossBar.addPlayer(player);
            } else if (!shouldSee && currentlySees) {
                bossBar.removePlayer(player);
            }
        }
    }

    private boolean shouldPlayerSeeBar(Player player, Location furnaceLoc) {
        if (!player.getWorld().equals(furnaceLoc.getWorld())) {
            return false;
        }
        return player.getLocation().distanceSquared(furnaceLoc) <= VIEW_DISTANCE_SQUARED;
    }

    // ==================== GETTERS ====================

    public boolean isSpawned() {
        return spawned;
    }

    public FurnaceInstance getFurnace() {
        return furnace;
    }

    public int getPlayerCount() {
        return bossBar != null ? bossBar.getPlayers().size() : 0;
    }
}