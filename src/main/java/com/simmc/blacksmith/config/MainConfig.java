package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Main configuration settings for the plugin.
 * Handles global settings like tick rates, cooldowns, and formatting.
 */
public class MainConfig {

    private String version;
    private String language;
    private int furnaceTicks;
    private int bellowsCooldown;
    private String smithingNameFormat;

    // Temperature bar settings
    private boolean temperatureBarEnabled;
    private double temperatureBarHeight;

    // Debug settings
    private boolean debugMode;

    public void load(FileConfiguration config) {
        version = config.getString("version", "1.0.0");
        language = config.getString("language", "en_US.yml");

        // Furnace settings - handle both string and int values
        furnaceTicks = parseIntValue(config, "furnaces.ticks", 20);
        bellowsCooldown = parseIntValue(config, "furnaces.bellows_cooldown", 20);
        temperatureBarEnabled = config.getBoolean("furnaces.temperature_bar_enabled", true);
        temperatureBarHeight = config.getDouble("furnaces.temperature_bar_height", 1.5);

        // Blacksmithing settings
        smithingNameFormat = config.getString("blacksmithing.name_format", "Smithed by %s");

        // Debug settings
        debugMode = config.getBoolean("debug", false);

        // Validate settings
        validateSettings();
    }

    /**
     * Parses an integer value from config, handling both string and integer formats.
     * This handles cases like: ticks: '20' or ticks: 20
     */
    private int parseIntValue(FileConfiguration config, String path, int defaultValue) {
        if (config.isInt(path)) {
            return config.getInt(path, defaultValue);
        }

        String stringValue = config.getString(path);
        if (stringValue != null && !stringValue.isEmpty()) {
            try {
                return Integer.parseInt(stringValue.trim().replace("'", ""));
            } catch (NumberFormatException ignored) {
                // Fall through to default
            }
        }
        return defaultValue;
    }

    /**
     * Validates and clamps settings to valid ranges.
     */
    private void validateSettings() {
        // Furnace ticks must be positive
        if (furnaceTicks <= 0) {
            furnaceTicks = 20;
        }

        // Bellows cooldown must be non-negative
        if (bellowsCooldown < 0) {
            bellowsCooldown = 20;
        }

        // Temperature bar height must be positive
        if (temperatureBarHeight <= 0) {
            temperatureBarHeight = 1.5;
        }
    }

    // ==================== GETTERS ====================

    public String getVersion() {
        return version;
    }

    public String getLanguage() {
        return language;
    }

    /**
     * Gets the furnace tick rate in server ticks.
     * This is used by ConfigManager.getFurnaceTickRate()
     */
    public int getFurnaceTicks() {
        return furnaceTicks;
    }

    public int getBellowsCooldown() {
        return bellowsCooldown;
    }

    /**
     * Gets the smithing name format string.
     * This is used by ForgeManager to add lore to items.
     */
    public String getSmithingNameFormat() {
        return smithingNameFormat;
    }

    /**
     * Formats a player name using the smithing format.
     */
    public String formatSmithedName(String playerName) {
        if (smithingNameFormat == null || smithingNameFormat.isEmpty()) {
            return "Smithed by " + playerName;
        }
        return String.format(smithingNameFormat, playerName);
    }

    public boolean isTemperatureBarEnabled() {
        return temperatureBarEnabled;
    }

    public double getTemperatureBarHeight() {
        return temperatureBarHeight;
    }

    public boolean isDebugMode() {
        return debugMode;
    }
}