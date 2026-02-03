package com.simmc.blacksmith.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manages all configuration files for the plugin.
 * Handles loading, validation, and access to configurations.
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    private ConfigValidator validator;
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
     * Loads all configuration files with validation.
     */
    public void loadAll() {
        saveDefaultConfigs();

        // Initialize validator
        validator = new ConfigValidator(plugin);

        // Load main config (no validation needed)
        loadMainConfig();

        // Load and validate furnace config
        loadFurnaceConfig();

        // Load and validate blacksmith config
        loadBlacksmithConfig();

        // Load and validate grindstone config
        loadGrindstoneConfig();

        // Load and validate fuel config
        loadFuelConfig();

        // Load messages
        loadMessages();

        // Log validation results
        validator.logResults();
        validator.logSummary();

        // Warn if critical errors found
        if (validator.hasErrors()) {
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("CRITICAL: Configuration errors detected!");
            plugin.getLogger().severe("Some features may not work correctly.");
            plugin.getLogger().severe("Please fix the errors above and reload.");
            plugin.getLogger().severe("========================================");
        }

        // Log load summary
        logLoadSummary();
    }

    /**
     * Loads and validates the main config.
     */
    private void loadMainConfig() {
        FileConfiguration mainFile = loadConfig("config.yml");
        mainConfig = new MainConfig();
        mainConfig.load(mainFile);
    }

    /**
     * Loads and validates the furnace config.
     */
    private void loadFurnaceConfig() {
        FileConfiguration config = loadConfig("furnaces.yml");

        // Validate before loading
        for (String furnaceId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(furnaceId);
            if (section != null) {
                validator.validateFurnaceConfig(section, furnaceId);
            }
        }

        furnaceConfig = new FurnaceConfig();
        furnaceConfig.load(config);
    }

    /**
     * Loads and validates the blacksmith/forge config.
     */
    private void loadBlacksmithConfig() {
        FileConfiguration config = loadConfig("blacksmith.yml");

        // Validate before loading
        for (String recipeId : config.getKeys(false)) {
            // Skip internal sections
            if (recipeId.startsWith("_")) continue;

            ConfigurationSection section = config.getConfigurationSection(recipeId);
            if (section != null) {
                validator.validateForgeRecipe(section, recipeId);
            }
        }

        blacksmithConfig = new BlacksmithConfig();
        blacksmithConfig.load(config);

        // Log any additional validation from BlacksmithConfig itself
        blacksmithConfig.logValidationResults(plugin);
    }

    /**
     * Loads and validates the grindstone/repair config.
     */
    private void loadGrindstoneConfig() {
        FileConfiguration config = loadConfig("grindstone.yml");

        // Validate before loading
        for (String configId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(configId);
            if (section != null) {
                validator.validateRepairConfig(section, configId);
            }
        }

        grindstoneConfig = new GrindstoneConfig();
        grindstoneConfig.load(config);
    }

    /**
     * Loads and validates the fuel config.
     */
    private void loadFuelConfig() {
        FileConfiguration config = loadConfig("fuels.yml");

        // Validate before loading
        for (String fuelId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(fuelId);
            if (section != null) {
                validator.validateFuelConfig(section, fuelId);
            }
        }

        fuelConfig = new FuelConfig();
        fuelConfig.load(config);
    }

    /**
     * Loads the message config.
     */
    private void loadMessages() {
        String langFile = "lang/" + mainConfig.getLanguage();
        FileConfiguration langConfig = loadConfig(langFile);
        messageConfig = new MessageConfig();
        messageConfig.load(langConfig);
    }

    /**
     * Logs a summary of loaded configurations.
     */
    private void logLoadSummary() {
        plugin.getLogger().info("=== Configuration Load Summary ===");
        plugin.getLogger().info("Furnace types: " + furnaceConfig.getFurnaceTypeCount());
        plugin.getLogger().info("Forge recipes: " + blacksmithConfig.getRecipeCount());
        plugin.getLogger().info("Repair configs: " + grindstoneConfig.getRepairConfigCount());
        plugin.getLogger().info("Fuel types: " + fuelConfig.getFuelCount());
        plugin.getLogger().info("==================================");
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
     * Saves a resource file if it doesn't already exist.
     */
    private void saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource(resourcePath, false);
        }
    }

    /**
     * Loads a configuration file with defaults.
     */
    private FileConfiguration loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            saveResourceIfNotExists(fileName);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Load defaults from JAR
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            config.setDefaults(defaultConfig);
        }

        return config;
    }

    /**
     * Reloads all configuration files.
     */
    public void reload() {
        loadAll();
    }

    // ==================== GETTERS ====================

    public ConfigValidator getValidator() {
        return validator;
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

    /**
     * Checks if configuration has critical errors.
     */
    public boolean hasConfigErrors() {
        return validator != null && validator.hasErrors();
    }

    /**
     * Checks if configuration has warnings.
     */
    public boolean hasConfigWarnings() {
        return validator != null && validator.hasWarnings();
    }
}