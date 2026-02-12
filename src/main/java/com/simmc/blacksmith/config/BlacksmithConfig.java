package com.simmc.blacksmith.config;

import com.simmc.blacksmith.forge.*;
import com.simmc.blacksmith.forge.display.ForgeDisplaySettings;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Configuration for forge recipes and categories.
 *
 * ADDED:
 * - Display settings parsing for CE items on anvil
 */
public class BlacksmithConfig {

    private final Map<String, ForgeRecipe> recipes;
    private final Map<String, ForgeCategory> categories;
    private final List<String> loadErrors;
    private final List<String> loadWarnings;

    public BlacksmithConfig() {
        this.recipes = new HashMap<>();
        this.categories = new HashMap<>();
        this.loadErrors = new ArrayList<>();
        this.loadWarnings = new ArrayList<>();
    }

    public void load(FileConfiguration config) {
        recipes.clear();
        categories.clear();
        loadErrors.clear();
        loadWarnings.clear();

        // Load categories
        ConfigurationSection catSection = config.getConfigurationSection("categories");
        if (catSection != null) {
            for (String catId : catSection.getKeys(false)) {
                ConfigurationSection cs = catSection.getConfigurationSection(catId);
                if (cs != null) {
                    ForgeCategory category = parseCategory(catId, cs);
                    if (category != null) {
                        categories.put(catId, category);
                    }
                }
            }
        }

        // Load recipes
        ConfigurationSection recipesSection = config.getConfigurationSection("recipes");
        if (recipesSection != null) {
            for (String recipeId : recipesSection.getKeys(false)) {
                ConfigurationSection rs = recipesSection.getConfigurationSection(recipeId);
                if (rs != null) {
                    ForgeRecipe recipe = parseRecipe(recipeId, rs);
                    if (recipe != null) {
                        recipes.put(recipeId, recipe);
                    }
                }
            }
        }

        // Fallback: old format
        if (recipes.isEmpty()) {
            for (String key : config.getKeys(false)) {
                if (key.equals("categories")) continue;
                ConfigurationSection rs = config.getConfigurationSection(key);
                if (rs != null && rs.contains("hits")) {
                    ForgeRecipe recipe = parseRecipe(key, rs);
                    if (recipe != null) {
                        recipes.put(key, recipe);
                    }
                }
            }
        }

        // Auto-create default category
        if (categories.isEmpty() && !recipes.isEmpty()) {
            List<String> allRecipeIds = new ArrayList<>(recipes.keySet());
            ForgeCategory defaultCat = new ForgeCategory(
                    "all", "§6All Recipes", Material.ANVIL, 0,
                    List.of("§7All available recipes"), allRecipeIds, 22
            );
            categories.put("all", defaultCat);
        }
    }

    private ForgeCategory parseCategory(String id, ConfigurationSection section) {
        String displayName = section.getString("display_name", "§f" + id).replace("&", "§");

        String materialStr = section.getString("icon.material", "IRON_INGOT");
        Material material = Material.matchMaterial(materialStr);
        if (material == null) material = Material.IRON_INGOT;

        int cmd = section.getInt("icon.cmd", 0);
        int slot = section.getInt("slot", 22);

        List<String> description = section.getStringList("description");
        description = description.stream().map(s -> s.replace("&", "§")).toList();

        List<String> recipeIds = section.getStringList("recipes");

        return new ForgeCategory(id, displayName, material, cmd, description, recipeIds, slot);
    }

    private ForgeRecipe parseRecipe(String id, ConfigurationSection section) {
        // Parse frames
        Map<Integer, ForgeFrame> frames = parseFrames(id, section.getConfigurationSection("frames"));

        String permission = section.getString("permission", "");
        String condition = section.getString("condition", "");
        int hits = section.getInt("hits", 5);
        double bias = section.getDouble("bias", 0.0);
        double targetSize = section.getDouble("target_size", 0.4);
        String runAfterCommand = section.getString("run_after_command", "");

        // Parse input
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;

        ConfigurationSection inputSection = section.getConfigurationSection("input");
        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft");
            inputAmount = inputSection.getInt("amount", 1);
        }

        // Parse results
        Map<Integer, ForgeResult> results = parseResults(id, section.getConfigurationSection("results"));

        // Parse star thresholds
        Map<Integer, StarThreshold> thresholds = parseStarThresholds(id, section.getConfigurationSection("star_thresholds"));

        // Parse star modifiers
        Map<Integer, StarModifier> starModifiers = parseStarModifiers(id, section.getConfigurationSection("star_modifiers"));

        // Base item
        String baseItemId = section.getString("base_item", "");
        String baseItemType = section.getString("base_item_type", "smc");

        // Parse display settings
        ForgeDisplaySettings displaySettings = parseDisplaySettings(id, section.getConfigurationSection("display"));

        return new ForgeRecipe(id, frames, permission, condition, hits, bias, targetSize,
                runAfterCommand, inputId, inputType, inputAmount, results, thresholds,
                starModifiers, baseItemId, baseItemType, displaySettings);
    }

    /**
     * Parses display settings for anvil model.
     */
    private ForgeDisplaySettings parseDisplaySettings(String recipeId, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String itemType = section.getString("item_type", null);
        String itemId = section.getString("item_id", null);

        double offsetX = section.getDouble("offset_x", 0.5);
        double offsetY = section.getDouble("offset_y", 0.9);
        double offsetZ = section.getDouble("offset_z", 0.5);

        float baseScale = (float) section.getDouble("base_scale", 0.5);
        float maxScale = (float) section.getDouble("max_scale", 0.7);

        boolean layFlat = section.getBoolean("lay_flat", true);

        return new ForgeDisplaySettings(
                itemType, itemId,
                offsetX, offsetY, offsetZ,
                baseScale, maxScale, layFlat
        );
    }

    private Map<Integer, ForgeFrame> parseFrames(String recipeId, ConfigurationSection section) {
        Map<Integer, ForgeFrame> frames = new HashMap<>();
        if (section == null) return frames;

        for (String key : section.getKeys(false)) {
            ConfigurationSection fs = section.getConfigurationSection(key);
            if (fs == null) continue;

            int index;
            try {
                index = Integer.parseInt(key.replace("frame_", ""));
            } catch (NumberFormatException e) {
                continue;
            }

            String materialStr = fs.getString("material", "STICK");
            Material material = Material.matchMaterial(materialStr);
            if (material == null) material = Material.STICK;

            int cmd = fs.getInt("cmd", 0);
            frames.put(index, new ForgeFrame(material, cmd));
        }

        return frames;
    }

    private Map<Integer, ForgeResult> parseResults(String recipeId, ConfigurationSection section) {
        Map<Integer, ForgeResult> results = new HashMap<>();
        if (section == null) return results;

        for (String key : section.getKeys(false)) {
            ConfigurationSection rs = section.getConfigurationSection(key);
            if (rs == null) continue;

            int star;
            try {
                star = Integer.parseInt(key.replace("result_", ""));
            } catch (NumberFormatException e) {
                continue;
            }

            String itemId = rs.getString("id", "");
            String itemType = rs.getString("type", "minecraft");
            int amount = rs.getInt("amount", 1);

            results.put(star, new ForgeResult(itemId, itemType, amount));
        }

        fillMissingResults(results);
        return results;
    }

    private void fillMissingResults(Map<Integer, ForgeResult> results) {
        ForgeResult fallback = results.get(0);
        if (fallback == null && !results.isEmpty()) {
            fallback = results.values().iterator().next();
        }
        if (fallback == null) return;

        for (int i = 0; i <= 5; i++) {
            results.putIfAbsent(i, fallback);
        }
    }

    private Map<Integer, StarThreshold> parseStarThresholds(String recipeId, ConfigurationSection section) {
        Map<Integer, StarThreshold> thresholds = new HashMap<>();
        if (section == null) return thresholds;

        for (String key : section.getKeys(false)) {
            ConfigurationSection ts = section.getConfigurationSection(key);
            if (ts == null) continue;

            int star;
            try {
                star = Integer.parseInt(key.replace("star_", ""));
            } catch (NumberFormatException e) {
                continue;
            }

            int minPerfect = ts.getInt("min_perfect_hits", 0);
            double minAccuracy = ts.getDouble("min_accuracy", 0.0);

            thresholds.put(star, new StarThreshold(minPerfect, minAccuracy));
        }

        return thresholds;
    }

    private Map<Integer, StarModifier> parseStarModifiers(String recipeId, ConfigurationSection section) {
        Map<Integer, StarModifier> modifiers = new HashMap<>();
        if (section == null) return modifiers;

        for (String key : section.getKeys(false)) {
            ConfigurationSection ms = section.getConfigurationSection(key);
            if (ms == null) continue;

            int star;
            try {
                star = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }

            String suffix = ms.getString("suffix", "").replace("&", "§");
            String loreLine = ms.getString("lore", "").replace("&", "§");

            Map<String, Double> attrMods = new HashMap<>();
            ConfigurationSection attrSection = ms.getConfigurationSection("attributes");
            if (attrSection != null) {
                for (String attrKey : attrSection.getKeys(false)) {
                    attrMods.put(attrKey.toLowerCase(), attrSection.getDouble(attrKey, 0.0));
                }
            }

            modifiers.put(star, new StarModifier(star, attrMods,
                    suffix.isEmpty() ? null : suffix,
                    loreLine.isEmpty() ? null : loreLine));
        }

        return modifiers;
    }

    public void logValidationResults(JavaPlugin plugin) {
        for (String warning : loadWarnings) {
            plugin.getLogger().warning(warning);
        }
        for (String error : loadErrors) {
            plugin.getLogger().severe(error);
        }
    }

    // Getters
    public ForgeRecipe getRecipe(String id) { return recipes.get(id); }
    public Map<String, ForgeRecipe> getRecipes() { return new HashMap<>(recipes); }
    public int getRecipeCount() { return recipes.size(); }
    public Set<String> getRecipeIds() { return new HashSet<>(recipes.keySet()); }
    public boolean hasRecipe(String id) { return recipes.containsKey(id); }

    public ForgeCategory getCategory(String id) { return categories.get(id); }
    public Map<String, ForgeCategory> getCategories() { return new HashMap<>(categories); }
    public int getCategoryCount() { return categories.size(); }

    public boolean hasErrors() { return !loadErrors.isEmpty(); }
    public List<String> getErrors() { return new ArrayList<>(loadErrors); }
    public List<String> getWarnings() { return new ArrayList<>(loadWarnings); }
}