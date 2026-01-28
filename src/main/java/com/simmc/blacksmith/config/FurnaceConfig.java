package com.simmc.blacksmith.config;

import com.simmc.blacksmith.furnace.FurnaceRecipe;
import com.simmc.blacksmith.furnace.FurnaceType;
import com.simmc.blacksmith.furnace.RecipeInput;
import com.simmc.blacksmith.furnace.RecipeOutput;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FurnaceConfig {

    private final Map<String, FurnaceType> furnaceTypes;

    public FurnaceConfig() {
        this.furnaceTypes = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        furnaceTypes.clear();

        for (String typeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(typeId);
            if (section == null) continue;

            FurnaceType type = parseFurnaceType(typeId, section);
            if (type != null) {
                furnaceTypes.put(typeId, type);
            }
        }
    }

    private FurnaceType parseFurnaceType(String id, ConfigurationSection section) {
        String itemId = section.getString("item_id", id);
        int maxTemp = section.getInt("max_temperature", 100);

        String materialStr = section.getString("display_material", "STICK");
        Material displayMaterial = Material.matchMaterial(materialStr);
        if (displayMaterial == null) displayMaterial = Material.STICK;

        int displayCmd = section.getInt("display_cmd", 0);
        int tempChange = section.getInt("temperature_change", 10);
        int minIdeal = section.getInt("min_ideal_temperature", 80);
        int maxIdeal = section.getInt("max_ideal_temperature", 100);
        double maxFuelPercent = section.getDouble("max_temperature_gain_from_fuel_percentage", 0.2);

        List<FurnaceRecipe> recipes = new ArrayList<>();
        ConfigurationSection recipesSection = section.getConfigurationSection("recipes");
        if (recipesSection != null) {
            for (String recipeId : recipesSection.getKeys(false)) {
                ConfigurationSection recipeSection = recipesSection.getConfigurationSection(recipeId);
                if (recipeSection != null) {
                    FurnaceRecipe recipe = parseRecipe(recipeId, recipeSection);
                    if (recipe != null) {
                        recipes.add(recipe);
                    }
                }
            }
        }

        return new FurnaceType(id, itemId, maxTemp, displayMaterial, displayCmd,
                tempChange, minIdeal, maxIdeal, maxFuelPercent, recipes);
    }

    private FurnaceRecipe parseRecipe(String id, ConfigurationSection section) {
        long smeltTime = section.getLong("smelt_time", 8000);

        List<RecipeInput> inputs = new ArrayList<>();
        ConfigurationSection inputsSection = section.getConfigurationSection("inputs");
        if (inputsSection != null) {
            for (String slot : inputsSection.getKeys(false)) {
                ConfigurationSection inputSection = inputsSection.getConfigurationSection(slot);
                if (inputSection != null) {
                    String inputId = inputSection.getString("id", "");
                    String inputType = inputSection.getString("type", "minecraft");
                    int amount = inputSection.getInt("amount", 1);
                    inputs.add(new RecipeInput(slot, inputId, inputType, amount));
                }
            }
        }

        List<RecipeOutput> outputs = new ArrayList<>();
        ConfigurationSection outputsSection = section.getConfigurationSection("outputs");
        if (outputsSection != null) {
            for (String slot : outputsSection.getKeys(false)) {
                ConfigurationSection outputSection = outputsSection.getConfigurationSection(slot);
                if (outputSection != null) {
                    String outputId = outputSection.getString("id", "");
                    String outputType = outputSection.getString("type", "minecraft");
                    int amount = outputSection.getInt("amount", 1);
                    outputs.add(new RecipeOutput(slot, outputId, outputType, amount));
                }
            }
        }

        List<RecipeOutput> badOutputs = new ArrayList<>();
        ConfigurationSection badSection = section.getConfigurationSection("bad_outputs");
        if (badSection != null) {
            for (String slot : badSection.getKeys(false)) {
                ConfigurationSection badOutputSection = badSection.getConfigurationSection(slot);
                if (badOutputSection != null) {
                    String badId = badOutputSection.getString("id", "");
                    String badType = badOutputSection.getString("type", "minecraft");
                    int amount = badOutputSection.getInt("amount", 1);
                    badOutputs.add(new RecipeOutput(slot, badId, badType, amount));
                }
            }
        }

        return new FurnaceRecipe(id, smeltTime, inputs, outputs, badOutputs);
    }

    public FurnaceType getFurnaceType(String id) {
        return furnaceTypes.get(id);
    }

    public Map<String, FurnaceType> getFurnaceTypes() {
        return new HashMap<>(furnaceTypes);
    }

    public int getFurnaceTypeCount() {
        return furnaceTypes.size();
    }
}