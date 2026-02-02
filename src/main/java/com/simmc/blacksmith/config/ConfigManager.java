package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * Manages all configuration files for the plugin.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    private MainConfig mainConfig;
    private FurnaceConfig furnaceConfig;
    private BlacksmithConfig blacksmithConfig;
    private GrindstoneConfig grindstoneConfig;
    private FuelConfig fuelConfig;
    private MessageConfig messageConfig;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all configuration files.
     */
    public void loadAll() {
        try {
            saveDefaultConfigs();

            FileConfiguration mainFile = loadConfig("config.yml");
            mainConfig = new MainConfig();
            mainConfig.load(mainFile);

            FileConfiguration furnaceFile = loadConfig("furnaces.yml");
            furnaceConfig = new FurnaceConfig();
            furnaceConfig.load(furnaceFile);

            FileConfiguration blacksmithFile = loadConfig("blacksmith.yml");
            blacksmithConfig = new BlacksmithConfig();
            blacksmithConfig.load(blacksmithFile);

            FileConfiguration grindstoneFile = loadConfig("grindstone.yml");
            grindstoneConfig = new GrindstoneConfig();
            grindstoneConfig.load(grindstoneFile);

            FileConfiguration fuelFile = loadConfig("fuels.yml");
            fuelConfig = new FuelConfig();
            fuelConfig.load(fuelFile);

            String langFile = "lang/" + mainConfig.getLanguage();
            FileConfiguration langConfig = loadConfig(langFile);
            messageConfig = new MessageConfig();
            messageConfig.load(langConfig);

            plugin.getLogger().info("Loaded " + furnaceConfig.getFurnaceTypeCount() + " furnace types");
            plugin.getLogger().info("Loaded " + blacksmithConfig.getRecipeCount() + " forge recipes");
            plugin.getLogger().info("Loaded " + grindstoneConfig.getRepairConfigCount() + " repair configs");
            plugin.getLogger().info("Loaded " + fuelConfig.getFuelCount() + " fuel types");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error loading configurations", e);
            throw new RuntimeException("Failed to load configurations", e);
        }
    }

    /**
     * Saves default configuration files if they don't exist.
     */
    private void saveDefaultConfigs() {
        saveResourceIfNotExists("config.yml");
        saveResourceIfNotExists("furnaces.yml");
        saveResourceIfNotExists("blacksmith.yml");
        saveResourceIfNotExists("grindstone.yml");
        saveResourceIfNotExists("fuels.yml");
        saveResourceIfNotExists("lang/en_US.yml");
    }

    /**
     * Saves a resource file if it doesn't exist.
     */
    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    /**
     * Loads a configuration file.
     */
    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            saveResourceIfNotExists(fileName);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        return config;
    }

    /**
     * Gets the furnace tick rate from config.
     */
    public int getFurnaceTickRate() {
        return mainConfig != null ? mainConfig.getFurnaceTickRate() : 20;
    }

    // Getters

    public MainConfig getMainConfig() {
        return mainConfig;
    }

    public FurnaceConfig getFurnaceConfig() {
        return furnaceConfig;
    }

    public BlacksmithConfig getBlacksmithConfig() {
        return blacksmithConfig;
    }

    public GrindstoneConfig getGrindstoneConfig() {
        return grindstoneConfig;
    }

    public FuelConfig getFuelConfig() {
        return fuelConfig;
    }

    public MessageConfig getMessageConfig() {
        return messageConfig;
    }
}