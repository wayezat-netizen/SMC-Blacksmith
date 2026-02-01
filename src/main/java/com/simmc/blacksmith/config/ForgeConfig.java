package com.simmc.blacksmith.config;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeResult;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration handler for forge/blacksmithing recipes.
 * Loads and manages forge recipes from the blacksmith.yml configuration file.
 */
public class ForgeConfig {

    private final Map<String, ForgeRecipe> recipes;
    private ItemProviderRegistry itemRegistry;

    public ForgeConfig() {
        this.recipes = new HashMap<>();
    }

    /**
     * Loads all forge recipes from the configuration file.
     *
     * @param config The configuration file to load from
     */
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

    /**
     * Parses a single forge recipe from a configuration section.
     *
     * @param id      The recipe ID
     * @param section The configuration section containing recipe data
     * @return The parsed ForgeRecipe, or null if parsing failed
     */
    private ForgeRecipe parseRecipe(String id, ConfigurationSection section) {
        // Parse frames (visual progression during forging)
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

        // Parse basic recipe properties
        String permission = section.getString("permission", "");
        int hits = section.getInt("hits", 8);
        double bias = section.getDouble("bias", 0.0);
        double targetSize = section.getDouble("target_size", 0.5);
        String runAfterCommand = section.getString("run_after_command", null);

        // Parse input materials
        ConfigurationSection inputSection = section.getConfigurationSection("input");
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;
        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft");
            inputAmount = inputSection.getInt("amount", 1);
        }

        // Parse result items (0-5 star ratings)
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

    /**
     * Sets the item registry for item matching operations.
     *
     * @param registry The item provider registry
     */
    public void setItemRegistry(ItemProviderRegistry registry) {
        this.itemRegistry = registry;
    }

    /**
     * Gets a recipe by its ID.
     *
     * @param id The recipe ID
     * @return The ForgeRecipe, or null if not found
     */
    public ForgeRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    /**
     * Finds a recipe that matches the given input item.
     *
     * @param item The input item to match
     * @return The matching ForgeRecipe, or null if none found
     */
    public ForgeRecipe findByInput(ItemStack item) {
        if (item == null || itemRegistry == null) return null;

        for (ForgeRecipe recipe : recipes.values()) {
            if (itemRegistry.matches(item, recipe.getInputType(), recipe.getInputId())) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Gets all loaded recipes.
     *
     * @return A copy of the recipes map
     */
    public Map<String, ForgeRecipe> getRecipes() {
        return new HashMap<>(recipes);
    }

    /**
     * Gets the number of loaded recipes.
     *
     * @return The recipe count
     */
    public int getRecipeCount() {
        return recipes.size();
    }

    /**
     * Checks if a recipe with the given ID exists.
     *
     * @param id The recipe ID to check
     * @return true if the recipe exists
     */
    public boolean hasRecipe(String id) {
        return recipes.containsKey(id);
    }

    /**
     * Gets all recipe IDs.
     *
     * @return A set of recipe IDs
     */
    public java.util.Set<String> getRecipeIds() {
        return new java.util.HashSet<>(recipes.keySet());
    }
}