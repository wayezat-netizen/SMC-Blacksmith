package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;

public class MainConfig {

    private String version;
    private String language;
    private int furnaceTicks;
    private int bellowsCooldown;
    private String smithingNameFormat;

    public void load(FileConfiguration config) {
        version = config.getString("version", "1.0.0");
        language = config.getString("language", "en_US.yml");
        furnaceTicks = config.getInt("furnaces.ticks", 20);
        bellowsCooldown = config.getInt("furnaces.bellows_cooldown", 20);
        smithingNameFormat = config.getString("blacksmithing.name_format", "Smithed by %s");
    }

    public String getVersion() {
        return version;
    }

    public String getLanguage() {
        return language;
    }

    public int getFurnaceTicks() {
        return furnaceTicks;
    }

    public int getBellowsCooldown() {
        return bellowsCooldown;
    }

    public String getSmithingNameFormat() {
        return smithingNameFormat;
    }
}