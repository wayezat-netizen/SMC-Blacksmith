package com.simmc.blacksmith.config;

import com.simmc.blacksmith.furnace.FurnaceRecipe;
import com.simmc.blacksmith.furnace.FurnaceType;
import com.simmc.blacksmith.furnace.RecipeInput;
import com.simmc.blacksmith.furnace.RecipeOutput;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Configuration handler for furnace types and smelting recipes.
 * Parses furnaces.yml configuration file.
 */
public class FurnaceConfig {

    private final Map<String, FurnaceType> furnaceTypes;
    private final List<String> loadErrors;
    private final List<String> loadWarnings;

    public FurnaceConfig() {
        this.furnaceTypes = new HashMap<>();
        this.loadErrors = new ArrayList<>();
        this.loadWarnings = new ArrayList<>();
    }

    public void load(FileConfiguration config) {
        furnaceTypes.clear();
        loadErrors.clear();
        loadWarnings.clear();

        for (String typeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(typeId);
            if (section == null) continue;

            try {
                FurnaceType type = parseFurnaceType(typeId, section);
                if (type != null) {
                    furnaceTypes.put(typeId, type);
                }
            } catch (Exception e) {
                loadErrors.add("[" + typeId + "] Failed to parse: " + e.getMessage());
            }
        }
    }

    /**
     * Logs validation results to the plugin logger.
     */
    public void logValidationResults(JavaPlugin plugin) {
        if (!loadWarnings.isEmpty()) {
            plugin.getLogger().warning("Furnace config warnings:");
            for (String warning : loadWarnings) {
                plugin.getLogger().warning("  " + warning);
            }
        }

        if (!loadErrors.isEmpty()) {
            plugin.getLogger().log(Level.SEVERE, "Furnace config errors:");
            for (String error : loadErrors) {
                plugin.getLogger().severe("  " + error);
            }
        }
    }

    private FurnaceType parseFurnaceType(String id, ConfigurationSection section) {
        String itemId = section.getString("item_id", id);
        int maxTemp = section.getInt("max_temperature", 100);

        String materialStr = section.getString("display_material", "STICK");
        Material displayMaterial = Material.matchMaterial(materialStr);
        if (displayMaterial == null) {
            loadWarnings.add("[" + id + "] Unknown material '" + materialStr + "', using STICK");
            displayMaterial = Material.STICK;
        }

        int displayCmd = section.getInt("display_cmd", 0);
        int tempChange = section.getInt("temperature_change", 10);
        int minIdeal = section.getInt("min_ideal_temperature", 80);
        int maxIdeal = section.getInt("max_ideal_temperature", 100);
        double maxFuelPercent = section.getDouble("max_temperature_gain_from_fuel_percentage", 0.2);

        // Validate temperature ranges
        if (minIdeal > maxIdeal) {
            loadWarnings.add("[" + id + "] min_ideal_temperature > max_ideal_temperature, swapping");
            int temp = minIdeal;
            minIdeal = maxIdeal;
            maxIdeal = temp;
        }

        if (maxIdeal > maxTemp) {
            loadWarnings.add("[" + id + "] max_ideal_temperature > max_temperature, clamping");
            maxIdeal = maxTemp;
        }

        if (maxFuelPercent <= 0 || maxFuelPercent > 1.0) {
            loadWarnings.add("[" + id + "] max_temperature_gain_from_fuel_percentage should be 0.0-1.0, clamping");
            maxFuelPercent = Math.max(0.1, Math.min(1.0, maxFuelPercent));
        }

        // Parse recipes
        List<FurnaceRecipe> recipes = new ArrayList<>();
        ConfigurationSection recipesSection = section.getConfigurationSection("recipes");
        if (recipesSection != null) {
            for (String recipeId : recipesSection.getKeys(false)) {
                ConfigurationSection recipeSection = recipesSection.getConfigurationSection(recipeId);
                if (recipeSection != null) {
                    FurnaceRecipe recipe = parseRecipe(id, recipeId, recipeSection);
                    if (recipe != null) {
                        recipes.add(recipe);
                    }
                }
            }
        }

        return new FurnaceType(id, itemId, maxTemp, displayMaterial, displayCmd,
                tempChange, minIdeal, maxIdeal, maxFuelPercent, recipes);
    }

    private FurnaceRecipe parseRecipe(String furnaceId, String recipeId, ConfigurationSection section) {
        long smeltTime = section.getLong("smelt_time", 8000);

        if (smeltTime <= 0) {
            loadWarnings.add("[" + furnaceId + "." + recipeId + "] smelt_time must be positive, using 8000");
            smeltTime = 8000;
        }

        // Parse inputs (letter-based slots: a, b, c, d, e, etc.)
        List<RecipeInput> inputs = new ArrayList<>();
        ConfigurationSection inputsSection = section.getConfigurationSection("inputs");
        if (inputsSection != null) {
            for (String slot : inputsSection.getKeys(false)) {
                ConfigurationSection inputSection = inputsSection.getConfigurationSection(slot);
                if (inputSection != null) {
                    String inputId = inputSection.getString("id", "");
                    String inputType = inputSection.getString("type", "minecraft").toLowerCase();
                    int amount = inputSection.getInt("amount", 1);

                    if (inputId.isEmpty()) {
                        loadWarnings.add("[" + furnaceId + "." + recipeId + ".inputs." + slot + "] Empty id, skipping");
                        continue;
                    }

                    if (amount <= 0) {
                        loadWarnings.add("[" + furnaceId + "." + recipeId + ".inputs." + slot + "] Invalid amount, using 1");
                        amount = 1;
                    }

                    inputs.add(new RecipeInput(slot, inputId, inputType, amount));
                }
            }
        }

        // Parse outputs
        List<RecipeOutput> outputs = new ArrayList<>();
        ConfigurationSection outputsSection = section.getConfigurationSection("outputs");
        if (outputsSection != null) {
            for (String slot : outputsSection.getKeys(false)) {
                ConfigurationSection outputSection = outputsSection.getConfigurationSection(slot);
                if (outputSection != null) {
                    String outputId = outputSection.getString("id", "");
                    String outputType = outputSection.getString("type", "minecraft").toLowerCase();
                    int amount = outputSection.getInt("amount", 1);

                    if (outputId.isEmpty()) {
                        loadWarnings.add("[" + furnaceId + "." + recipeId + ".outputs." + slot + "] Empty id, skipping");
                        continue;
                    }

                    outputs.add(new RecipeOutput(slot, outputId, outputType, amount));
                }
            }
        }

        // Parse bad outputs (failure outputs)
        List<RecipeOutput> badOutputs = new ArrayList<>();
        ConfigurationSection badSection = section.getConfigurationSection("bad_outputs");
        if (badSection != null) {
            for (String slot : badSection.getKeys(false)) {
                ConfigurationSection badOutputSection = badSection.getConfigurationSection(slot);
                if (badOutputSection != null) {
                    String badId = badOutputSection.getString("id", "");
                    String badType = badOutputSection.getString("type", "minecraft").toLowerCase();
                    int amount = badOutputSection.getInt("amount", 1);

                    if (!badId.isEmpty()) {
                        badOutputs.add(new RecipeOutput(slot, badId, badType, amount));
                    }
                }
            }
        }

        // Validate recipe has required components
        if (inputs.isEmpty()) {
            loadErrors.add("[" + furnaceId + "." + recipeId + "] Recipe has no inputs");
            return null;
        }

        if (outputs.isEmpty()) {
            loadErrors.add("[" + furnaceId + "." + recipeId + "] Recipe has no outputs");
            return null;
        }

        return new FurnaceRecipe(recipeId, smeltTime, inputs, outputs, badOutputs);
    }

    // ==================== GETTERS ====================

    public FurnaceType getFurnaceType(String id) {
        return furnaceTypes.get(id);
    }

    public Map<String, FurnaceType> getFurnaceTypes() {
        return new HashMap<>(furnaceTypes);
    }

    public int getFurnaceTypeCount() {
        return furnaceTypes.size();
    }

    public boolean hasErrors() {
        return !loadErrors.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(loadErrors);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(loadWarnings);
    }

    /**
     * Gets total recipe count across all furnace types.
     */
    public int getTotalRecipeCount() {
        int count = 0;
        for (FurnaceType type : furnaceTypes.values()) {
            count += type.getRecipeCount();
        }
        return count;
    }
}