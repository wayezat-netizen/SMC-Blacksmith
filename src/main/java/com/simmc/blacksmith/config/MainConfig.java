package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Main configuration settings for the plugin.
 */
public class MainConfig {

    private String version;
    private String language;
    private int furnaceTickRate;
    private int bellowsCooldown;
    private String smithedNameFormat;

    public void load(FileConfiguration config) {
        version = config.getString("version", "1.0.0");
        language = config.getString("language", "en_US.yml");
        furnaceTickRate = config.getInt("furnaces.ticks", 20);
        bellowsCooldown = config.getInt("furnaces.bellows_cooldown", 20);
        smithedNameFormat = config.getString("blacksmithing.name_format", "Smithed by %s");
    }

    public String getVersion() {
        return version;
    }

    public String getLanguage() {
        return language;
    }

    public int getFurnaceTickRate() {
        return furnaceTickRate;
    }

    public int getBellowsCooldown() {
        return bellowsCooldown;
    }

    public String getSmithedNameFormat() {
        return smithedNameFormat;
    }

    public String formatSmithedName(String playerName) {
        return String.format(smithedNameFormat, playerName);
    }
}