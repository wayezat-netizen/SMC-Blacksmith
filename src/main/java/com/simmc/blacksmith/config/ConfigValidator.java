package com.simmc.blacksmith.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates configuration files and reports errors/warnings.
 * Provides detailed validation for furnaces, forge recipes, fuels, bellows, and repair configs.
 */
public class ConfigValidator {

    private final JavaPlugin plugin;
    private final List<String> errors;
    private final List<String> warnings;

    public ConfigValidator(JavaPlugin plugin) {
        this.plugin = plugin;
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    /**
     * Resets all errors and warnings.
     */
    public void reset() {
        errors.clear();
        warnings.clear();
    }

    // ==================== FURNACE VALIDATION ====================

    /**
     * Validates a furnace configuration section.
     */
    public boolean validateFurnaceConfig(ConfigurationSection section, String furnaceId) {
        boolean valid = true;

        // Required field: max_temperature
        if (!section.contains("max_temperature")) {
            addError(furnaceId, "Missing required field 'max_temperature'");
            valid = false;
        } else {
            int maxTemp = section.getInt("max_temperature");
            if (maxTemp <= 0) {
                addError(furnaceId, "max_temperature must be positive, got: " + maxTemp);
                valid = false;
            }
        }

        // Temperature range validation
        int minIdeal = section.getInt("min_ideal_temperature", 0);
        int maxIdeal = section.getInt("max_ideal_temperature", 100);
        int maxTemp = section.getInt("max_temperature", 100);

        if (minIdeal > maxIdeal) {
            addError(furnaceId, "min_ideal_temperature (" + minIdeal +
                    ") cannot be greater than max_ideal_temperature (" + maxIdeal + ")");
            valid = false;
        }

        if (maxIdeal > maxTemp) {
            addWarning(furnaceId, "max_ideal_temperature (" + maxIdeal +
                    ") exceeds max_temperature (" + maxTemp + "), will be clamped");
        }

        // Fuel percentage validation
        double fuelPercent = section.getDouble("max_temperature_gain_from_fuel_percentage", 0.2);
        if (fuelPercent < 0 || fuelPercent > 1.0) {
            addError(furnaceId, "max_temperature_gain_from_fuel_percentage must be 0.0-1.0, got: " + fuelPercent);
            valid = false;
        }

        // Temperature change validation
        int tempChange = section.getInt("temperature_change", 10);
        if (tempChange <= 0) {
            addWarning(furnaceId, "temperature_change should be positive, got: " + tempChange);
        }

        // Cooling rate validation
        int coolingRate = section.getInt("cooling_rate", 1);
        if (coolingRate < 0) {
            addWarning(furnaceId, "cooling_rate should be non-negative, got: " + coolingRate);
        }

        // Display material validation
        String materialStr = section.getString("display_material", "STICK");
        if (Material.matchMaterial(materialStr) == null) {
            addWarning(furnaceId, "Unknown material '" + materialStr + "', using STICK");
        }

        // Recipe validation
        ConfigurationSection recipes = section.getConfigurationSection("recipes");
        if (recipes != null) {
            for (String recipeId : recipes.getKeys(false)) {
                ConfigurationSection recipe = recipes.getConfigurationSection(recipeId);
                if (recipe != null) {
                    valid &= validateFurnaceRecipe(recipe, furnaceId + ".recipes." + recipeId);
                }
            }
        }

        return valid;
    }

    /**
     * Validates a furnace recipe configuration section.
     */
    public boolean validateFurnaceRecipe(ConfigurationSection section, String path) {
        boolean valid = true;

        // Smelt time validation
        long smeltTime = section.getLong("smelt_time", 0);
        if (smeltTime <= 0) {
            addError(path, "smelt_time must be positive, got: " + smeltTime);
            valid = false;
        }

        // Inputs validation
        ConfigurationSection inputs = section.getConfigurationSection("inputs");
        if (inputs == null || inputs.getKeys(false).isEmpty()) {
            addError(path, "Recipe must have at least one input");
            valid = false;
        } else {
            for (String inputKey : inputs.getKeys(false)) {
                ConfigurationSection input = inputs.getConfigurationSection(inputKey);
                if (input != null) {
                    valid &= validateItemReference(input, path + ".inputs." + inputKey);
                }
            }
        }

        // Outputs validation
        ConfigurationSection outputs = section.getConfigurationSection("outputs");
        if (outputs == null || outputs.getKeys(false).isEmpty()) {
            addError(path, "Recipe must have at least one output");
            valid = false;
        } else {
            for (String outputKey : outputs.getKeys(false)) {
                ConfigurationSection output = outputs.getConfigurationSection(outputKey);
                if (output != null) {
                    valid &= validateItemReference(output, path + ".outputs." + outputKey);
                }
            }
        }

        // Bad outputs validation (optional but validate if present)
        ConfigurationSection badOutputs = section.getConfigurationSection("bad_outputs");
        if (badOutputs != null) {
            for (String outputKey : badOutputs.getKeys(false)) {
                ConfigurationSection output = badOutputs.getConfigurationSection(outputKey);
                if (output != null) {
                    valid &= validateItemReference(output, path + ".bad_outputs." + outputKey);
                }
            }
        }

        return valid;
    }

    // ==================== FORGE/BLACKSMITH VALIDATION ====================

    /**
     * Validates a forge recipe configuration section.
     */
    public boolean validateForgeRecipe(ConfigurationSection section, String recipeId) {
        boolean valid = true;

        // Hits validation
        int hits = section.getInt("hits", 0);
        if (hits <= 0) {
            addError(recipeId, "hits must be positive, got: " + hits);
            valid = false;
        }

        // Bias validation
        double bias = section.getDouble("bias", 0.0);
        if (bias < 0 || bias > 1.0) {
            addWarning(recipeId, "bias should typically be 0.0-1.0, got: " + bias);
        }

        // Target size validation
        double targetSize = section.getDouble("target_size", 0.5);
        if (targetSize <= 0 || targetSize > 1.0) {
            addError(recipeId, "target_size must be between 0.0-1.0, got: " + targetSize);
            valid = false;
        }

        // Input validation (optional)
        ConfigurationSection input = section.getConfigurationSection("input");
        if (input != null) {
            valid &= validateItemReference(input, recipeId + ".input");
        }

        // Results validation
        ConfigurationSection results = section.getConfigurationSection("results");
        if (results == null) {
            addError(recipeId, "Recipe must have a 'results' section");
            valid = false;
        } else {
            boolean hasAnyResult = false;
            for (int i = 0; i <= 5; i++) {
                ConfigurationSection result = results.getConfigurationSection("result_" + i);
                if (result != null) {
                    hasAnyResult = true;
                    valid &= validateItemReference(result, recipeId + ".results.result_" + i);
                }
            }
            if (!hasAnyResult) {
                addError(recipeId, "Recipe must have at least one result (result_0 through result_5)");
                valid = false;
            }
        }

        // Frames validation (optional but validate if present)
        ConfigurationSection frames = section.getConfigurationSection("frames");
        if (frames != null) {
            for (int i = 0; i <= 2; i++) {
                ConfigurationSection frame = frames.getConfigurationSection("frame_" + i);
                if (frame != null) {
                    String mat = frame.getString("material", "STICK");
                    if (Material.matchMaterial(mat) == null) {
                        addWarning(recipeId + ".frames.frame_" + i,
                                "Unknown material '" + mat + "', using STICK");
                    }
                }
            }
        }

        return valid;
    }

    // ==================== GRINDSTONE/REPAIR VALIDATION ====================

    /**
     * Validates a grindstone repair configuration section.
     */
    public boolean validateRepairConfig(ConfigurationSection section, String configId) {
        boolean valid = true;

         // Item ID validation - check both 'id' and 'item_id'
        String itemId = section.getString("id", section.getString("item_id", ""));
        if (itemId.isEmpty()) {
            addError(configId, "Item id cannot be empty");
            valid = false;
        }

        // Item type validation - check both 'type' and 'item_type'
        String itemType = section.getString("type", section.getString("item_type", "minecraft")).toLowerCase();
        if (!isValidItemType(itemType)) {
            addWarning(configId, "Unknown item type '" + itemType + "', expected minecraft/craftengine/smc/nexo");
        }

        // Validate minecraft items
        if (itemType.equals("minecraft") && !itemId.isEmpty() && !itemId.contains(":")) {
            Material mat = Material.matchMaterial(itemId);
            if (mat == null) {
                addWarning(configId, "Unknown minecraft item '" + itemId + "'");
            }
        }

        // Input validation (optional) - check both 'input' and 'repair_material'
        ConfigurationSection input = section.getConfigurationSection("input");
        if (input == null) {
            input = section.getConfigurationSection("repair_material");
        }
        if (input != null) {
            valid &= validateItemReference(input, configId + ".input");
        }

        return valid;
    }

    // ==================== FUEL VALIDATION ====================

    /**
     * Validates a fuel configuration section.
     */
    public boolean validateFuelConfig(ConfigurationSection section, String fuelId) {
        boolean valid = true;

        // Burn time validation
        long burnTime = section.getLong("burn_time", 0);
        if (burnTime <= 0) {
            addError(fuelId, "burn_time must be positive, got: " + burnTime);
            valid = false;
        }

        // Temperature boost validation
        int tempBoost = section.getInt("temperature_boost", 0);
        if (tempBoost <= 0) {
            addError(fuelId, "temperature_boost must be positive, got: " + tempBoost);
            valid = false;
        }

        // Item reference validation
        String itemId = section.getString("id", "");
        if (itemId.isEmpty()) {
            addError(fuelId, "Fuel id cannot be empty");
            valid = false;
        }

        String itemType = section.getString("type", "minecraft").toLowerCase();
        if (!isValidItemType(itemType)) {
            addWarning(fuelId, "Unknown item type '" + itemType + "'");
        }

        // Validate minecraft items
        if (itemType.equals("minecraft") && !itemId.isEmpty() && !itemId.contains(":")) {
            Material mat = Material.matchMaterial(itemId);
            if (mat == null) {
                addWarning(fuelId, "Unknown minecraft fuel item '" + itemId + "'");
            }
        }

        return valid;
    }

    // ==================== BELLOWS VALIDATION (NEW) ====================

    /**
     * Validates a bellows configuration section.
     */
    public boolean validateBellowsConfig(ConfigurationSection section, String bellowsId) {
        boolean valid = true;

        // Item ID validation
        String itemId = section.getString("id", "");
        if (itemId.isEmpty()) {
            addWarning(bellowsId, "Missing 'id' field, using config key as ID");
        }

        // Item type validation
        String itemType = section.getString("type", "smc").toLowerCase();
        if (!isValidItemType(itemType)) {
            addWarning(bellowsId, "Unknown item type '" + itemType + "', expected minecraft/craftengine/smc/nexo");
        }

        // Heat per blow validation - check both heat_boost and heat_per_blow
        int heatPerBlow = section.getInt("heat_boost", section.getInt("heat_per_blow", 0));
        if (heatPerBlow <= 0) {
            addWarning(bellowsId, "heat_boost/heat_per_blow should be positive, got: " + heatPerBlow + ", defaulting to 10");
        }

        // Max durability validation
        int maxDurability = section.getInt("max_durability", 0);
        if (maxDurability <= 0) {
            addWarning(bellowsId, "max_durability should be positive, got: " + maxDurability + ", defaulting to 100");
        }

        // Validate minecraft items
        if (itemType.equals("minecraft") && !itemId.isEmpty() && !itemId.contains(":")) {
            Material mat = Material.matchMaterial(itemId);
            if (mat == null) {
                addWarning(bellowsId, "Unknown minecraft item '" + itemId + "'");
            }
        }

        return valid;
    }

    // ==================== ITEM REFERENCE VALIDATION ====================

    /**
     * Validates an item reference configuration section.
     */
    public boolean validateItemReference(ConfigurationSection section, String path) {
        boolean valid = true;

        String id = section.getString("id", "");
        if (id.isEmpty()) {
            addError(path, "Item id cannot be empty");
            valid = false;
        }

        String type = section.getString("type", "minecraft").toLowerCase();
        if (!isValidItemType(type)) {
            addWarning(path, "Unknown item type '" + type + "', expected minecraft/craftengine/smc/nexo");
        }

        int amount = section.getInt("amount", 1);
        if (amount <= 0) {
            addError(path, "amount must be positive, got: " + amount);
            valid = false;
        }

        // Validate minecraft items at load time
        if (type.equals("minecraft") && !id.isEmpty() && !id.contains(":")) {
            Material mat = Material.matchMaterial(id);
            if (mat == null) {
                addWarning(path, "Unknown minecraft item '" + id + "'");
            }
        }

        return valid;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Checks if the item type is valid.
     */
    private boolean isValidItemType(String type) {
        return type.equals("minecraft") || type.equals("craftengine") ||
                type.equals("smc") || type.equals("nexo");
    }

    // ==================== ERROR/WARNING TRACKING ====================

    private void addError(String path, String message) {
        String full = "[ERROR] " + path + ": " + message;
        errors.add(full);
    }

    private void addWarning(String path, String message) {
        String full = "[WARNING] " + path + ": " + message;
        warnings.add(full);
    }

    /**
     * Validates a hammer configuration section.
     */
    public boolean validateHammerConfig(ConfigurationSection section, String hammerId) {
        boolean valid = true;

        // Item ID validation
        String itemId = section.getString("id", "");
        if (itemId.isEmpty()) {
            addWarning(hammerId, "Missing 'id' field, using config key as ID");
        }

        // Item type validation
        String itemType = section.getString("type", "smc").toLowerCase();
        if (!isValidItemType(itemType)) {
            addWarning(hammerId, "Unknown item type '" + itemType + "', expected minecraft/craftengine/smc/nexo");
        }

        // Max durability validation
        int maxDurability = section.getInt("max_durability", 0);
        if (maxDurability <= 0) {
            addWarning(hammerId, "max_durability should be positive, got: " + maxDurability + ", defaulting to 500");
        }

        // Speed bonus validation
        double speedBonus = section.getDouble("speed_bonus", 0.0);
        if (speedBonus < 0 || speedBonus > 1.0) {
            addWarning(hammerId, "speed_bonus should be 0.0-1.0, got: " + speedBonus);
        }

        // Accuracy bonus validation
        double accuracyBonus = section.getDouble("accuracy_bonus", 0.0);
        if (accuracyBonus < 0 || accuracyBonus > 1.0) {
            addWarning(hammerId, "accuracy_bonus should be 0.0-1.0, got: " + accuracyBonus);
        }

        // Validate minecraft items
        if (itemType.equals("minecraft") && !itemId.isEmpty() && !itemId.contains(":")) {
            Material mat = Material.matchMaterial(itemId);
            if (mat == null) {
                addWarning(hammerId, "Unknown minecraft item '" + itemId + "'");
            }
        }

        return valid;
    }

    /**
     * Logs all errors and warnings to the plugin logger.
     */
    public void logResults() {
        if (!warnings.isEmpty()) {
            plugin.getLogger().warning("Configuration warnings found:");
            for (String warning : warnings) {
                plugin.getLogger().warning("  " + warning);
            }
        }

        if (!errors.isEmpty()) {
            plugin.getLogger().severe("Configuration errors found:");
            for (String error : errors) {
                plugin.getLogger().severe("  " + error);
            }
        }

        if (errors.isEmpty() && warnings.isEmpty()) {
            plugin.getLogger().info("All configurations validated successfully.");
        }
    }

    /**
     * Logs a summary of validation results.
     */
    public void logSummary() {
        if (errors.isEmpty() && warnings.isEmpty()) {
            return;
        }

        plugin.getLogger().info("Validation Summary: " + errors.size() + " error(s), " + warnings.size() + " warning(s)");
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public int getErrorCount() {
        return errors.size();
    }

    public int getWarningCount() {
        return warnings.size();
    }
}