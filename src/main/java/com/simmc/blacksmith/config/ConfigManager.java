package com.simmc.blacksmith.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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

    public void loadAll() {
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
    }

    private void saveDefaultConfigs() {
        saveResourceIfNotExists("config.yml");
        saveResourceIfNotExists("furnaces.yml");
        saveResourceIfNotExists("blacksmith.yml");
        saveResourceIfNotExists("grindstone.yml");
        saveResourceIfNotExists("fuels.yml");
        saveResourceIfNotExists("lang/en_US.yml");
    }

    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

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

    public int getFurnaceTickRate() {
        return mainConfig.getFurnaceTicks();
    }

    public int getBellowsCooldown() {
        return mainConfig.getBellowsCooldown();
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }
}