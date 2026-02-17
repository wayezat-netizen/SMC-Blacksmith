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

    // Forge hit target settings
    private double forgeHitTargetOffsetY;
    private double forgeHitTargetSpreadX;
    private double forgeHitTargetSpreadZ;

    // Debug settings
    private boolean debugMode;

    public void load(FileConfiguration config) {
        version = config.getString("version", "1.0.0");
        language = config.getString("language", "en_US.yml");

        // Furnace settings - handle both string and int values
        furnaceTicks = parseIntValue(config, "furnaces.tick_rate",
                        parseIntValue(config, "furnaces.ticks", 20));
        bellowsCooldown = parseIntValue(config, "furnaces.bellows_cooldown", 20);
        temperatureBarEnabled = config.getBoolean("furnaces.temperature_bar_enabled", true);
        temperatureBarHeight = config.getDouble("furnaces.temperature_bar_height", 1.5);

        // Blacksmithing settings - check new path first, then legacy
        smithingNameFormat = config.getString("blacksmithing.name_format", null);
        if (smithingNameFormat == null || smithingNameFormat.isEmpty()) {
            // Fallback to legacy path
            smithingNameFormat = config.getString("display.forger_format", "<gray>锻造者 <player>");
        }

        // Debug settings
        debugMode = config.getBoolean("debug", false);

        // Forge hit target settings
        forgeHitTargetOffsetY = config.getDouble("forge.hit_target_offset_y", 1.0);
        forgeHitTargetSpreadX = config.getDouble("forge.hit_target_spread_x", 0.6);
        forgeHitTargetSpreadZ = config.getDouble("forge.hit_target_spread_z", 0.4);

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

        // Forge hit target Y offset (allow any value, but clamp to reasonable range)
        if (forgeHitTargetOffsetY < 0.0) {
            forgeHitTargetOffsetY = 0.0;
        } else if (forgeHitTargetOffsetY > 3.0) {
            forgeHitTargetOffsetY = 3.0;
        }

        // Forge hit target spread (positive only)
        if (forgeHitTargetSpreadX < 0.0) {
            forgeHitTargetSpreadX = 0.6;
        }
        if (forgeHitTargetSpreadZ < 0.0) {
            forgeHitTargetSpreadZ = 0.4;
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
     * Supports placeholders: <player>, %player%, {player}
     */
    public String getSmithingNameFormat() {
        return smithingNameFormat;
    }

    /**
     * Formats a player name using the smithing format.
     */
    public String formatSmithedName(String playerName) {
        if (smithingNameFormat == null || smithingNameFormat.isEmpty()) {
            return "§7锻造者 §e" + playerName;
        }

        String result = smithingNameFormat;

        // Replace all player placeholder styles
        result = result.replace("<player>", playerName);
        result = result.replace("%player%", playerName);
        result = result.replace("{player}", playerName);

        // Handle %s format
        if (result.contains("%s")) {
            result = String.format(result, playerName);
        }

        // Convert MiniMessage color tags to legacy format
        result = convertMiniMessageColors(result);

        // Convert & color codes to §
        result = result.replace("&", "§");

        return result;
    }

    /**
     * Converts MiniMessage color tags to legacy § format.
     */
    private String convertMiniMessageColors(String text) {
        return text
                .replace("<black>", "§0")
                .replace("<dark_blue>", "§1")
                .replace("<dark_green>", "§2")
                .replace("<dark_aqua>", "§3")
                .replace("<dark_red>", "§4")
                .replace("<dark_purple>", "§5")
                .replace("<gold>", "§6")
                .replace("<gray>", "§7")
                .replace("<grey>", "§7")
                .replace("<dark_gray>", "§8")
                .replace("<dark_grey>", "§8")
                .replace("<blue>", "§9")
                .replace("<green>", "§a")
                .replace("<aqua>", "§b")
                .replace("<red>", "§c")
                .replace("<light_purple>", "§d")
                .replace("<yellow>", "§e")
                .replace("<white>", "§f")
                .replace("<reset>", "§r")
                .replace("<bold>", "§l")
                .replace("<italic>", "§o")
                .replace("<underline>", "§n")
                .replace("<strikethrough>", "§m")
                .replace("<obfuscated>", "§k");
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

    public double getForgeHitTargetOffsetY() {
        return forgeHitTargetOffsetY;
    }

    public double getForgeHitTargetSpreadX() {
        return forgeHitTargetSpreadX;
    }

    public double getForgeHitTargetSpreadZ() {
        return forgeHitTargetSpreadZ;
    }
}