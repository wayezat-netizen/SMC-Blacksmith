package com.simmc.blacksmith.config;

import com.simmc.blacksmith.forge.ForgeFrame;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeResult;
import com.simmc.blacksmith.forge.StarThreshold;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Configuration handler for blacksmith/forge recipes.
 * Parses and validates all forge recipe configurations including star thresholds.
 */
public class BlacksmithConfig {

    private final Map<String, ForgeRecipe> recipes;
    private final List<String> loadErrors;
    private final List<String> loadWarnings;

    public BlacksmithConfig() {
        this.recipes = new HashMap<>();
        this.loadErrors = new ArrayList<>();
        this.loadWarnings = new ArrayList<>();
    }

    /**
     * Loads all forge recipes from the configuration file.
     */
    public void load(FileConfiguration config) {
        recipes.clear();
        loadErrors.clear();
        loadWarnings.clear();

        for (String recipeId : config.getKeys(false)) {
            // Skip internal/meta sections
            if (recipeId.startsWith("_")) continue;

            ConfigurationSection section = config.getConfigurationSection(recipeId);
            if (section == null) continue;

            try {
                ForgeRecipe recipe = parseRecipe(recipeId, section);
                if (recipe != null) {
                    recipes.put(recipeId, recipe);
                }
            } catch (Exception e) {
                loadErrors.add("[" + recipeId + "] Failed to parse: " + e.getMessage());
            }
        }
    }

    /**
     * Parses a single forge recipe from configuration.
     */
    private ForgeRecipe parseRecipe(String id, ConfigurationSection section) {
        // Parse frames
        Map<Integer, ForgeFrame> frames = parseFrames(id, section);

        // Core settings
        String permission = section.getString("permission", "");
        String condition = section.getString("condition", "");
        int hits = section.getInt("hits", 8);
        double bias = section.getDouble("bias", 0.0);
        double targetSize = section.getDouble("target_size", 0.5);
        String runAfterCommand = section.getString("run_after_command", null);

        // Validate core settings
        if (hits <= 0) {
            loadWarnings.add("[" + id + "] hits must be positive, using default 8");
            hits = 8;
        }

        if (targetSize <= 0 || targetSize > 1.0) {
            loadWarnings.add("[" + id + "] target_size should be 0.0-1.0, clamping");
            targetSize = Math.max(0.1, Math.min(1.0, targetSize));
        }

        // Parse input
        ConfigurationSection inputSection = section.getConfigurationSection("input");
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;

        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft").toLowerCase();
            inputAmount = inputSection.getInt("amount", 1);

            if (inputAmount <= 0) {
                loadWarnings.add("[" + id + ".input] amount must be positive, using 1");
                inputAmount = 1;
            }
        }

        // Parse results
        Map<Integer, ForgeResult> results = parseResults(id, section);

        if (results.isEmpty()) {
            loadErrors.add("[" + id + "] Recipe must have at least one result");
            return null;
        }

        // Parse star thresholds (NEW)
        Map<Integer, StarThreshold> starThresholds = parseStarThresholds(id, section);

        return new ForgeRecipe(id, frames, permission, condition, hits, bias, targetSize,
                runAfterCommand, inputId, inputType, inputAmount, results, starThresholds);
    }

    /**
     * Parses frame configurations for visual progression.
     */
    private Map<Integer, ForgeFrame> parseFrames(String recipeId, ConfigurationSection section) {
        Map<Integer, ForgeFrame> frames = new HashMap<>();
        ConfigurationSection framesSection = section.getConfigurationSection("frames");

        if (framesSection == null) {
            frames.put(0, new ForgeFrame(Material.STICK, 0));
            frames.put(1, new ForgeFrame(Material.STICK, 0));
            frames.put(2, new ForgeFrame(Material.STICK, 0));
            return frames;
        }

        for (int i = 0; i <= 2; i++) {
            ConfigurationSection frameSection = framesSection.getConfigurationSection("frame_" + i);
            if (frameSection != null) {
                String matStr = frameSection.getString("material", "STICK");
                Material mat = Material.matchMaterial(matStr);

                if (mat == null) {
                    loadWarnings.add("[" + recipeId + ".frames.frame_" + i + "] Unknown material '" + matStr + "', using STICK");
                    mat = Material.STICK;
                }

                int cmd = frameSection.getInt("cmd", 0);
                frames.put(i, new ForgeFrame(mat, cmd));
            } else {
                frames.put(i, new ForgeFrame(Material.STICK, 0));
            }
        }

        return frames;
    }

    /**
     * Parses result configurations for each star rating.
     */
    private Map<Integer, ForgeResult> parseResults(String recipeId, ConfigurationSection section) {
        Map<Integer, ForgeResult> results = new HashMap<>();
        ConfigurationSection resultsSection = section.getConfigurationSection("results");

        if (resultsSection == null) {
            return results;
        }

        for (int i = 0; i <= 5; i++) {
            ConfigurationSection resultSection = resultsSection.getConfigurationSection("result_" + i);
            if (resultSection != null) {
                String resultId = resultSection.getString("id", "");
                String resultType = resultSection.getString("type", "minecraft").toLowerCase();
                int resultAmount = resultSection.getInt("amount", 1);

                if (resultId.isEmpty()) {
                    loadWarnings.add("[" + recipeId + ".results.result_" + i + "] Result id is empty, skipping");
                    continue;
                }

                if (resultAmount <= 0) {
                    resultAmount = 1;
                }

                results.put(i, new ForgeResult(resultId, resultType, resultAmount));
            }
        }

        // Fill missing star ratings with nearest defined result
        fillMissingResults(results);

        return results;
    }

    /**
     * Fills missing star rating results with the nearest defined result.
     */
    private void fillMissingResults(Map<Integer, ForgeResult> results) {
        if (results.isEmpty()) return;

        ForgeResult lastDefined = null;

        // Forward pass
        for (int i = 0; i <= 5; i++) {
            if (results.containsKey(i)) {
                lastDefined = results.get(i);
            } else if (lastDefined != null) {
                results.put(i, lastDefined);
            }
        }

        // Backward pass for any remaining
        lastDefined = null;
        for (int i = 5; i >= 0; i--) {
            if (results.containsKey(i)) {
                lastDefined = results.get(i);
            } else if (lastDefined != null) {
                results.put(i, lastDefined);
            }
        }
    }

    /**
     * Parses star threshold configurations for granular difficulty control.
     */
    private Map<Integer, StarThreshold> parseStarThresholds(String recipeId, ConfigurationSection section) {
        Map<Integer, StarThreshold> thresholds = new HashMap<>();
        ConfigurationSection thresholdSection = section.getConfigurationSection("star_thresholds");

        if (thresholdSection == null) {
            // No custom thresholds - recipe will use default score-based calculation
            return thresholds;
        }

        for (int star = 0; star <= 5; star++) {
            ConfigurationSection starSection = thresholdSection.getConfigurationSection("star_" + star);
            if (starSection != null) {
                int minPerfectHits = starSection.getInt("min_perfect_hits", 0);
                double minAccuracy = starSection.getDouble("min_accuracy", 0.0);

                // Validate
                if (minAccuracy < 0 || minAccuracy > 1.0) {
                    loadWarnings.add("[" + recipeId + ".star_thresholds.star_" + star +
                            "] min_accuracy should be 0.0-1.0, clamping");
                    minAccuracy = Math.max(0, Math.min(1.0, minAccuracy));
                }

                if (minPerfectHits < 0) {
                    loadWarnings.add("[" + recipeId + ".star_thresholds.star_" + star +
                            "] min_perfect_hits cannot be negative, using 0");
                    minPerfectHits = 0;
                }

                thresholds.put(star, new StarThreshold(minPerfectHits, minAccuracy));
            }
        }

        return thresholds;
    }

    /**
     * Logs validation results to the plugin logger.
     */
    public void logValidationResults(JavaPlugin plugin) {
        if (!loadWarnings.isEmpty()) {
            plugin.getLogger().warning("Blacksmith config warnings:");
            for (String warning : loadWarnings) {
                plugin.getLogger().warning("  " + warning);
            }
        }

        if (!loadErrors.isEmpty()) {
            plugin.getLogger().severe("Blacksmith config errors:");
            for (String error : loadErrors) {
                plugin.getLogger().severe("  " + error);
            }
        }

        plugin.getLogger().info("Loaded " + recipes.size() + " forge recipes.");
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

    public ForgeRecipe getRecipe(String id) {
        return recipes.get(id);
    }

    public Map<String, ForgeRecipe> getRecipes() {
        return new HashMap<>(recipes);
    }

    public int getRecipeCount() {
        return recipes.size();
    }

    public Set<String> getRecipeIds() {
        return new HashSet<>(recipes.keySet());
    }

    public boolean hasRecipe(String id) {
        return recipes.containsKey(id);
    }

}