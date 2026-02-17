package com.simmc.blacksmith.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Centralized configuration management for SMCBlacksmith.
 * Handles loading, validation, caching, and access to all config files.
 */
public class ConfigManager {

    private final JavaPlugin plugin;
    private final Map<ConfigType, FileConfiguration> configCache;

    // Configuration instances
    private ConfigValidator validator;
    private MainConfig mainConfig;
    private FurnaceConfig furnaceConfig;
    private BlacksmithConfig blacksmithConfig;
    private GrindstoneConfig grindstoneConfig;
    private FuelConfig fuelConfig;
    private MessageConfig messageConfig;
    private BellowsConfig bellowsConfig;
    private HammerConfig hammerConfig;

    /**
     * Enum for configuration types - ensures consistent file handling.
     */
    public enum ConfigType {
        MAIN("config.yml"),
        FURNACES("furnaces.yml"),
        BLACKSMITH("blacksmith.yml"),
        GRINDSTONE("grindstone.yml"),
        FUELS("fuels.yml"),
        BELLOWS("bellows.yml"),
        HAMMER("hammer.yml"),
        MESSAGES("lang/en_US.yml");

        private final String fileName;

        ConfigType(String fileName) {
            this.fileName = fileName;
        }

        public String getFileName() {
            return fileName;
        }
    }

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configCache = new EnumMap<>(ConfigType.class);
    }

    /**
     * Loads all configuration files with validation.
     */
    public void loadAll() {
        long startTime = System.currentTimeMillis();

        // Clear cache for reload
        configCache.clear();

        // Save defaults first
        saveDefaultConfigs();

        // Initialize fresh validator
        validator = new ConfigValidator(plugin);

        // Load configs in dependency order
        loadMainConfig();
        loadFurnaceConfig();
        loadBlacksmithConfig();
        loadGrindstoneConfig();
        loadFuelConfig();
        loadBellowsConfig();
        loadHammerConfig();
        loadMessages();

        // Log results
        validator.logResults();
        logValidationSummary();
        logLoadSummary(System.currentTimeMillis() - startTime);
    }

    // ==================== CONFIG LOADERS ====================

    private void loadMainConfig() {
        FileConfiguration config = loadConfig(ConfigType.MAIN);
        mainConfig = new MainConfig();
        mainConfig.load(config);
    }

    private void loadFurnaceConfig() {
        FileConfiguration config = loadConfig(ConfigType.FURNACES);
        validateSections(config, validator::validateFurnaceConfig);
        furnaceConfig = new FurnaceConfig();
        furnaceConfig.load(config);
    }

    private void loadBlacksmithConfig() {
        FileConfiguration config = loadConfig(ConfigType.BLACKSMITH);

        // Validate root-level recipes
        for (String key : config.getKeys(false)) {
            if (isInternalKey(key)) continue;

            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null && section.contains("hits")) {
                validator.validateForgeRecipe(section, key);
            }
        }

        // Validate nested recipes section
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection != null) {
            validateSections(recipesSection, validator::validateForgeRecipe);
        }

        blacksmithConfig = new BlacksmithConfig();
        blacksmithConfig.load(config);
        blacksmithConfig.logValidationResults(plugin);
    }

    private void loadGrindstoneConfig() {
        FileConfiguration config = loadConfig(ConfigType.GRINDSTONE);

        // Validate nested 'repairs:' section if it exists
        ConfigurationSection repairsSection = config.getConfigurationSection("repairs");
        if (repairsSection != null) {
            validateSections(repairsSection, validator::validateRepairConfig);
        }

        grindstoneConfig = new GrindstoneConfig();
        grindstoneConfig.load(config);
    }

    private void loadFuelConfig() {
        FileConfiguration config = loadConfig(ConfigType.FUELS);
        validateSections(config, validator::validateFuelConfig);
        fuelConfig = new FuelConfig();
        fuelConfig.load(config);
    }

    private void loadBellowsConfig() {
        FileConfiguration config = loadConfig(ConfigType.BELLOWS);

        // Validate nested 'bellows:' section if it exists, otherwise validate root
        ConfigurationSection bellowsSection = config.getConfigurationSection("bellows");
        if (bellowsSection != null) {
            validateSections(bellowsSection, validator::validateBellowsConfig);
        } else {
            validateSections(config, validator::validateBellowsConfig);
        }

        bellowsConfig = new BellowsConfig();
        bellowsConfig.load(config);
    }

    private void loadHammerConfig() {
        FileConfiguration config = loadConfig(ConfigType.HAMMER);
        validateSections(config, validator::validateHammerConfig);
        hammerConfig = new HammerConfig();
        hammerConfig.load(config);
    }

    private void loadMessages() {
        String langFile = "lang/" + mainConfig.getLanguage();
        FileConfiguration config = loadConfigByPath(langFile);
        messageConfig = new MessageConfig();
        messageConfig.load(config);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Validates all sections in a config using the provided validator.
     */
    private void validateSections(ConfigurationSection config,
                                  SectionValidator validatorFunc) {
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) {
                validatorFunc.validate(section, key);
            }
        }
    }

    @FunctionalInterface
    private interface SectionValidator {
        void validate(ConfigurationSection section, String id);
    }

    private boolean isInternalKey(String key) {
        return key.startsWith("_") ||
                key.equals("categories") ||
                key.equals("recipes");
    }

    /**
     * Loads a configuration file with defaults from JAR.
     */
    private FileConfiguration loadConfig(ConfigType type) {
        return loadConfigByPath(type.getFileName());
    }

    private FileConfiguration loadConfigByPath(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            saveResourceSafely(fileName);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        applyDefaults(config, fileName);

        return config;
    }

    private void applyDefaults(FileConfiguration config, String fileName) {
        try (InputStream defaultStream = plugin.getResource(fileName)) {
            if (defaultStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not load defaults for " + fileName, e);
        }
    }

    /**
     * Saves default configuration files if they don't exist.
     */
    private void saveDefaultConfigs() {
        for (ConfigType type : ConfigType.values()) {
            saveResourceSafely(type.getFileName());
        }
    }

    private void saveResourceSafely(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (file.exists()) return;

        // Ensure parent directories exist
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // Try to save from JAR, create empty file if not in JAR
        if (plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
        } else {
            try {
                file.createNewFile();
                plugin.getLogger().info("Created empty config: " + resourcePath);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create " + resourcePath + ": " + e.getMessage());
            }
        }
    }

    // ==================== LOGGING ====================

    private void logValidationSummary() {
        if (validator.hasErrors()) {
            plugin.getLogger().severe("========================================");
            plugin.getLogger().severe("CRITICAL: Configuration errors detected!");
            plugin.getLogger().severe("Some features may not work correctly.");
            plugin.getLogger().severe("Please fix the errors above and reload.");
            plugin.getLogger().severe("========================================");
        } else if (validator.hasWarnings()) {
            plugin.getLogger().warning("Configuration loaded with warnings.");
        }
        validator.logSummary();
    }

    private void logLoadSummary(long loadTimeMs) {
        plugin.getLogger().info("=== Configuration Load Summary ===");
        plugin.getLogger().info("Furnace types: " + furnaceConfig.getFurnaceTypeCount());
        plugin.getLogger().info("Forge recipes: " + blacksmithConfig.getRecipeCount());
        plugin.getLogger().info("Forge categories: " + blacksmithConfig.getCategoryCount());
        plugin.getLogger().info("Repair configs: " + grindstoneConfig.getRepairConfigCount());
        plugin.getLogger().info("Fuel types: " + fuelConfig.getFuelCount());
        plugin.getLogger().info("Bellows types: " + bellowsConfig.getBellowsTypeCount());
        plugin.getLogger().info("Hammer types: " + hammerConfig.getHammerTypeCount());
        plugin.getLogger().info("Load time: " + loadTimeMs + "ms");
        plugin.getLogger().info("==================================");
    }

    // ==================== PUBLIC API ====================

    public void reload() {
        loadAll();
    }

    // Getters
    public ConfigValidator getValidator() { return validator; }
    public MainConfig getMainConfig() { return mainConfig; }
    public FurnaceConfig getFurnaceConfig() { return furnaceConfig; }
    public BlacksmithConfig getBlacksmithConfig() { return blacksmithConfig; }
    public GrindstoneConfig getGrindstoneConfig() { return grindstoneConfig; }
    public FuelConfig getFuelConfig() { return fuelConfig; }
    public MessageConfig getMessageConfig() { return messageConfig; }
    public BellowsConfig getBellowsConfig() { return bellowsConfig; }
    public HammerConfig getHammerConfig() { return hammerConfig; }
    public JavaPlugin getPlugin() { return plugin; }

    // Convenience methods
    public int getFurnaceTickRate() { return mainConfig.getFurnaceTicks(); }
    public int getBellowsCooldown() { return mainConfig.getBellowsCooldown(); }
    public boolean hasConfigErrors() { return validator != null && validator.hasErrors(); }
    public boolean hasConfigWarnings() { return validator != null && validator.hasWarnings(); }
}