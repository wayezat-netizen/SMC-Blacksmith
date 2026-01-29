package com.simmc.blacksmith.config;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeResult;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;

public class BlacksmithConfig {

    private final Map<String, ForgeRecipe> recipes;

    public BlacksmithConfig() {
        this.recipes = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        recipes.clear();

        for (String recipeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(recipeId);
            if (section == null) continue;

            ForgeRecipe recipe = parseRecipe(recipeId, section);
            if (recipe != null) {
                recipes.put(recipeId, recipe);
            }
        }
    }

    private ForgeRecipe parseRecipe(String id, ConfigurationSection section) {
        Map<Integer, ForgeFrame> frames = new HashMap<>();
        ConfigurationSection framesSection = section.getConfigurationSection("frames");
        if (framesSection != null) {
            for (int i = 0; i <= 2; i++) {
                ConfigurationSection frameSection = framesSection.getConfigurationSection("frame_" + i);
                if (frameSection != null) {
                    Material mat = Material.matchMaterial(frameSection.getString("material", "STICK"));
                    if (mat == null) mat = Material.STICK;
                    int cmd = frameSection.getInt("cmd", 0);
                    frames.put(i, new ForgeFrame(mat, cmd));
                }
            }
        }

        String permission = section.getString("permission", "");
        int hits = section.getInt("hits", 8);
        double bias = section.getDouble("bias", 0.0);
        double targetSize = section.getDouble("target_size", 0.5);
        String runAfterCommand = section.getString("run_after_command", null);

        ConfigurationSection inputSection = section.getConfigurationSection("input");
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;
        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft");
            inputAmount = inputSection.getInt("amount", 1);
        }

        Map<Integer, ForgeResult> results = new HashMap<>();
        ConfigurationSection resultsSection = section.getConfigurationSection("results");
        if (resultsSection != null) {
            for (int i = 0; i <= 5; i++) {
                ConfigurationSection resultSection = resultsSection.getConfigurationSection("result_" + i);
                if (resultSection != null) {
                    String resultId = resultSection.getString("id", "");
                    String resultType = resultSection.getString("type", "minecraft");
                    int resultAmount = resultSection.getInt("amount", 1);
                    results.put(i, new ForgeResult(resultId, resultType, resultAmount));
                }
            }
        }

        return new ForgeRecipe(id, frames, permission, hits, bias, targetSize,
                runAfterCommand, inputId, inputType, inputAmount, results);
    }

    public ForgeRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    public Map<String, ForgeRecipe> getRecipes() {
        return new HashMap<>(recipes);
    }

    public int getRecipeCount() {
        return recipes.size();
    }
}