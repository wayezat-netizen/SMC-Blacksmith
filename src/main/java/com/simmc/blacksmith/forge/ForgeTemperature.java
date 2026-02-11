package com.simmc.blacksmith.forge;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

/**
 * Temperature/heat system for the forge.
 * Affects forging difficulty and visual effects.
 */
public class ForgeTemperature {

    // Temperature thresholds
    public static final int COLD = 0;
    public static final int WARM = 300;
    public static final int HOT = 600;
    public static final int WHITE_HOT = 900;
    public static final int MAX_TEMP = 1200;

    // Temperature bar display
    private static final int BAR_LENGTH = 10;

    private int currentTemperature;
    private int targetTemperature;
    private final int heatingRate;
    private final int coolingRate;

    public ForgeTemperature(int heatingRate, int coolingRate) {
        this.currentTemperature = COLD;
        this.targetTemperature = COLD;
        this.heatingRate = Math.max(1, heatingRate);
        this.coolingRate = Math.max(1, coolingRate);
    }

    // ==================== TEMPERATURE UPDATES ====================

    /**
     * Updates temperature towards target.
     */
    public void tick() {
        if (currentTemperature < targetTemperature) {
            currentTemperature = Math.min(currentTemperature + heatingRate, targetTemperature);
        } else if (currentTemperature > targetTemperature) {
            currentTemperature = Math.max(currentTemperature - coolingRate, targetTemperature);
        }
        currentTemperature = clamp(currentTemperature, COLD, MAX_TEMP);
    }

    /**
     * Sets the target temperature (from fuel/bellows).
     */
    public void heat(int temperature) {
        this.targetTemperature = Math.min(temperature, MAX_TEMP);
    }

    /**
     * Lets the forge cool down.
     */
    public void cool() {
        this.targetTemperature = COLD;
    }

    /**
     * Directly sets current temperature.
     */
    public void setCurrentTemperature(int temp) {
        this.currentTemperature = clamp(temp, COLD, MAX_TEMP);
    }

    // ==================== HEAT LEVEL ====================

    /**
     * Gets the current heat level.
     */
    public HeatLevel getHeatLevel() {
        if (currentTemperature >= WHITE_HOT) return HeatLevel.WHITE_HOT;
        if (currentTemperature >= HOT) return HeatLevel.HOT;
        if (currentTemperature >= WARM) return HeatLevel.WARM;
        return HeatLevel.COLD;
    }

    /**
     * Gets the heat level name with color codes.
     */
    public String getHeatLevelName() {
        return getHeatLevel().getDisplayName();
    }

    /**
     * Gets the heat color for particles/displays.
     */
    public Color getHeatColor() {
        return getHeatLevel().getColor();
    }

    /**
     * Checks if metal is workable.
     */
    public boolean isWorkable() {
        return currentTemperature >= WARM;
    }

    /**
     * Checks if temperature is in ideal range.
     */
    public boolean isInIdealRange(int idealMin, int idealMax) {
        return currentTemperature >= idealMin && currentTemperature <= idealMax;
    }

    // ==================== MODIFIERS ====================

    /**
     * Gets accuracy modifier based on temperature.
     */
    public double getAccuracyModifier(int idealMin, int idealMax) {
        if (isInIdealRange(idealMin, idealMax)) {
            return 0.05; // +5% bonus in ideal range
        } else if (currentTemperature < WARM) {
            return -0.15; // -15% penalty when cold
        } else if (currentTemperature > WHITE_HOT) {
            return -0.10; // -10% penalty when too hot
        }
        return 0.0;
    }

    /**
     * Gets the temperature as a percentage of max.
     */
    public double getTemperaturePercentage() {
        return (double) currentTemperature / MAX_TEMP;
    }

    // ==================== VISUAL EFFECTS ====================

    /**
     * Spawns temperature-based particles at location.
     */
    public void spawnHeatParticles(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location center = location.clone().add(0.5, 1.3, 0.5);
        HeatLevel level = getHeatLevel();

        switch (level) {
            case WHITE_HOT -> {
                spawnDust(world, center, Color.WHITE, 1.5f, 5);
                world.spawnParticle(Particle.FLAME, center, 3, 0.1, 0.05, 0.1, 0.01);
            }
            case HOT -> {
                spawnDust(world, center, Color.ORANGE, 1.3f, 3);
                world.spawnParticle(Particle.FLAME, center, 1, 0.08, 0.03, 0.08, 0.005);
            }
            case WARM -> {
                spawnDust(world, center, Color.RED, 1.0f, 2);
            }
            // COLD - no particles
        }
    }

    private void spawnDust(World world, Location loc, Color color, float size, int count) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        world.spawnParticle(Particle.DUST, loc, count, 0.12, 0.08, 0.12, 0, dust);
    }

    /**
     * Gets a visual temperature bar string.
     */
    public String getTemperatureBar() {
        int filled = (int) (getTemperaturePercentage() * BAR_LENGTH);
        String barColor = getHeatLevel().getBarColor();

        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < BAR_LENGTH; i++) {
            bar.append(i < filled ? barColor + "█" : "§7░");
        }
        bar.append("§8] ").append(getHeatLevelName());

        return bar.toString();
    }

    // ==================== GETTERS ====================

    public int getCurrentTemperature() { return currentTemperature; }
    public int getTargetTemperature() { return targetTemperature; }
    public int getHeatingRate() { return heatingRate; }
    public int getCoolingRate() { return coolingRate; }

    // ==================== UTILITIES ====================

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== HEAT LEVEL ENUM ====================

    public enum HeatLevel {
        COLD("§7§lCOLD", Color.GRAY, "§7"),
        WARM("§e§lWARM", Color.YELLOW, "§e"),
        HOT("§6§lHOT", Color.ORANGE, "§6"),
        WHITE_HOT("§f§lWHITE HOT", Color.WHITE, "§f");

        private final String displayName;
        private final Color color;
        private final String barColor;

        HeatLevel(String displayName, Color color, String barColor) {
            this.displayName = displayName;
            this.color = color;
            this.barColor = barColor;
        }

        public String getDisplayName() { return displayName; }
        public Color getColor() { return color; }
        public String getBarColor() { return barColor; }
    }
}