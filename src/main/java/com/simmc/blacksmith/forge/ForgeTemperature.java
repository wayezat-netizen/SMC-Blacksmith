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

    private int currentTemperature;
    private int targetTemperature;
    private final int heatingRate;
    private final int coolingRate;

    public ForgeTemperature(int heatingRate, int coolingRate) {
        this.currentTemperature = COLD;
        this.targetTemperature = COLD;
        this.heatingRate = heatingRate;
        this.coolingRate = coolingRate;
    }

    /**
     * Updates temperature towards target.
     */
    public void tick() {
        if (currentTemperature < targetTemperature) {
            currentTemperature = Math.min(currentTemperature + heatingRate, targetTemperature);
        } else if (currentTemperature > targetTemperature) {
            currentTemperature = Math.max(currentTemperature - coolingRate, targetTemperature);
        }
        currentTemperature = Math.max(COLD, Math.min(currentTemperature, MAX_TEMP));
    }

    /**
     * Sets the target temperature (from fuel).
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
     * Gets the heat level name.
     */
    public String getHeatLevelName() {
        if (currentTemperature >= WHITE_HOT) return "§f§lWHITE HOT";
        if (currentTemperature >= HOT) return "§6§lHOT";
        if (currentTemperature >= WARM) return "§e§lWARM";
        return "§7§lCOLD";
    }

    /**
     * Gets the heat color for particles.
     */
    public Color getHeatColor() {
        if (currentTemperature >= WHITE_HOT) {
            return Color.WHITE;
        } else if (currentTemperature >= HOT) {
            return Color.ORANGE;
        } else if (currentTemperature >= WARM) {
            return Color.YELLOW;
        }
        return Color.GRAY;
    }

    /**
     * Gets accuracy modifier based on temperature.
     * Forging at ideal temperature gives bonus.
     */
    public double getAccuracyModifier(int idealMin, int idealMax) {
        if (currentTemperature >= idealMin && currentTemperature <= idealMax) {
            return 0.05; // +5% bonus in ideal range
        } else if (currentTemperature < WARM) {
            return -0.15; // -15% penalty when cold
        } else if (currentTemperature > WHITE_HOT) {
            return -0.10; // -10% penalty when too hot
        }
        return 0.0;
    }

    /**
     * Checks if metal is workable.
     */
    public boolean isWorkable() {
        return currentTemperature >= WARM;
    }

    /**
     * Spawns temperature-based particles.
     */
    public void spawnHeatParticles(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        Location center = location.clone().add(0.5, 1.3, 0.5);

        if (currentTemperature >= WHITE_HOT) {
            // White hot - intense glow
            Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 1.5f);
            world.spawnParticle(Particle.DUST, center, 5, 0.15, 0.1, 0.15, 0, whiteDust);
            world.spawnParticle(Particle.FLAME, center, 3, 0.1, 0.05, 0.1, 0.01);
        } else if (currentTemperature >= HOT) {
            // Hot - orange glow
            Particle.DustOptions orangeDust = new Particle.DustOptions(Color.ORANGE, 1.3f);
            world.spawnParticle(Particle.DUST, center, 3, 0.12, 0.08, 0.12, 0, orangeDust);
            world.spawnParticle(Particle.FLAME, center, 1, 0.08, 0.03, 0.08, 0.005);
        } else if (currentTemperature >= WARM) {
            // Warm - red glow
            Particle.DustOptions redDust = new Particle.DustOptions(Color.RED, 1.0f);
            world.spawnParticle(Particle.DUST, center, 2, 0.1, 0.05, 0.1, 0, redDust);
        }
    }

    /**
     * Gets a visual temperature bar.
     */
    public String getTemperatureBar() {
        int barLength = 10;
        int filled = (int) ((double) currentTemperature / MAX_TEMP * barLength);

        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 0; i < barLength; i++) {
            if (i < filled) {
                if (currentTemperature >= WHITE_HOT) {
                    bar.append("§f█");
                } else if (currentTemperature >= HOT) {
                    bar.append("§6█");
                } else if (currentTemperature >= WARM) {
                    bar.append("§e█");
                } else {
                    bar.append("§c█");
                }
            } else {
                bar.append("§7░");
            }
        }
        bar.append("§8] ").append(getHeatLevelName());
        return bar.toString();
    }

    // Getters
    public int getCurrentTemperature() { return currentTemperature; }
    public int getTargetTemperature() { return targetTemperature; }
    public void setCurrentTemperature(int temp) { this.currentTemperature = temp; }
}