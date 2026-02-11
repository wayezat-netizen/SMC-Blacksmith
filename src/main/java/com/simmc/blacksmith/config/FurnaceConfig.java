package com.simmc.blacksmith.config;

import com.simmc.blacksmith.furnace.FurnaceRecipe;
import com.simmc.blacksmith.furnace.FurnaceType;
import com.simmc.blacksmith.furnace.RecipeInput;
import com.simmc.blacksmith.furnace.RecipeOutput;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Configuration for furnace types and their recipes.
 */
public class FurnaceConfig {

    private final Map<String, FurnaceType> furnaceTypes;
    private final List<String> loadErrors;

    public FurnaceConfig() {
        this.furnaceTypes = new LinkedHashMap<>();
        this.loadErrors = new ArrayList<>();
    }

    public void load(FileConfiguration config) {
        furnaceTypes.clear();
        loadErrors.clear();

        for (String typeId : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(typeId);
            if (section == null) continue;

            parseFurnaceType(typeId, section).ifPresent(type -> {
                furnaceTypes.put(typeId, type);
                Bukkit.getLogger().info("[FurnaceConfig] Loaded furnace type: " + typeId +
                        " with " + type.getRecipeCount() + " recipes");
            });
        }
    }

    private Optional<FurnaceType> parseFurnaceType(String id, ConfigurationSection section) {
        try {
            FurnaceType.Builder builder = FurnaceType.builder(id);

            // Basic settings
            builder.itemId(section.getString("item_id", id));
            builder.displayMaterial(parseMaterial(section.getString("display_material", "FURNACE")));
            builder.displayCmd(section.getInt("display_cmd", 0));

            // Temperature settings
            int maxTemp = Math.max(100, section.getInt("max_temperature", 1000));
            builder.maxTemperature(maxTemp);

            int minIdeal = section.getInt("min_ideal_temperature", 600);
            int maxIdeal = section.getInt("max_ideal_temperature", 900);
            builder.idealTemperatureRange(minIdeal, maxIdeal);

            // Heating/Cooling rates
            builder.temperatureChange(Math.max(1, section.getInt("temperature_change", 10)));
            builder.coolingRate(Math.max(1, section.getInt("cooling_rate", 5)));
            builder.heatingMultiplier(clamp(section.getDouble("heating_multiplier", 0.5), 0.01, 1.0));
            builder.coolingMultiplier(clamp(section.getDouble("cooling_multiplier", 0.2), 0.01, 1.0));

            // Fuel settings
            builder.maxFuelTempPercentage(clamp(section.getDouble("max_temperature_gain_from_fuel_percentage", 0.6), 0.1, 1.0));

            // Bellows settings
            builder.bellowsDecayRate(clamp(section.getDouble("bellows_decay_rate", 0.02), 0.001, 0.5));
            builder.bellowsInstantBoost(clamp(section.getDouble("bellows_instant_boost", 0.8), 0.0, 1.0));

            // Smelting quality settings
            builder.badOutputThresholdMs(Math.max(1000, section.getLong("bad_output_threshold_ms", 3000)));
            builder.minIdealRatio(clamp(section.getDouble("min_ideal_ratio", 0.4), 0.1, 0.9));

            // GUI configuration
            ConfigurationSection guiSection = section.getConfigurationSection("gui");
            if (guiSection != null) {
                builder.guiTitle(guiSection.getString("title", "&8Furnace"));

                List<Integer> inputSlotList = guiSection.getIntegerList("input_slots");
                if (!inputSlotList.isEmpty()) {
                    builder.inputSlots(inputSlotList.stream().mapToInt(Integer::intValue).toArray());
                }

                builder.fuelSlot(guiSection.getInt("fuel_slot", 40));
                builder.outputSlot(guiSection.getInt("output_slot", 24));
            }

            // Parse recipes
            ConfigurationSection recipesSection = section.getConfigurationSection("recipes");
            if (recipesSection != null) {
                for (String recipeId : recipesSection.getKeys(false)) {
                    ConfigurationSection rs = recipesSection.getConfigurationSection(recipeId);
                    if (rs != null) {
                        parseRecipe(recipeId, rs).ifPresent(builder::addRecipe);
                    }
                }
            }

            return Optional.of(builder.build());
        } catch (Exception e) {
            loadErrors.add("Failed to parse furnace type '" + id + "': " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }

    private Optional<FurnaceRecipe> parseRecipe(String recipeId, ConfigurationSection section) {
        try {
            long smeltTime = Math.max(1000, section.getLong("smelt_time", 8000));
            int minTemperature = Math.max(0, section.getInt("min_temperature", 0));

            // Parse per-recipe ideal temperature range
            int minIdealTemp = section.getInt("min_ideal_temperature", minTemperature + 100);
            int maxIdealTemp = section.getInt("max_ideal_temperature", minTemperature + 300);

            // Validate ranges
            if (minIdealTemp > maxIdealTemp) {
                int temp = minIdealTemp;
                minIdealTemp = maxIdealTemp;
                maxIdealTemp = temp;
            }
            if (minIdealTemp < minTemperature) {
                minIdealTemp = minTemperature;
            }

            List<RecipeInput> inputs = parseInputs(section.getConfigurationSection("inputs"));
            List<RecipeOutput> outputs = parseOutputs(section.getConfigurationSection("outputs"));
            List<RecipeOutput> badOutputs = parseOutputs(section.getConfigurationSection("bad_outputs"));

            if (inputs.isEmpty()) {
                loadErrors.add("Recipe '" + recipeId + "' has no inputs");
                return Optional.empty();
            }

            if (outputs.isEmpty()) {
                loadErrors.add("Recipe '" + recipeId + "' has no outputs");
                return Optional.empty();
            }

            Bukkit.getLogger().info("[FurnaceConfig] Loaded recipe: " + recipeId +
                    " | smeltTime=" + smeltTime +
                    " | minTemp=" + minTemperature +
                    " | idealRange=" + minIdealTemp + "-" + maxIdealTemp + "°C");

            return Optional.of(new FurnaceRecipe(
                    recipeId, smeltTime, minTemperature,
                    minIdealTemp, maxIdealTemp,
                    inputs, outputs, badOutputs
            ));
        } catch (Exception e) {
            loadErrors.add("Failed to parse recipe '" + recipeId + "': " + e.getMessage());
            return Optional.empty();
        }
    }

    private List<RecipeInput> parseInputs(ConfigurationSection section) {
        List<RecipeInput> inputs = new ArrayList<>();
        if (section == null) return inputs;

        for (String slot : section.getKeys(false)) {
            ConfigurationSection is = section.getConfigurationSection(slot);
            if (is == null) continue;

            String id = is.getString("id", "");
            if (id.isEmpty()) continue;

            String type = is.getString("type", "minecraft").toLowerCase();
            int amount = Math.max(1, is.getInt("amount", 1));

            inputs.add(new RecipeInput(slot, id, type, amount));

            Bukkit.getLogger().info("[FurnaceConfig]   Input: " + type + ":" + id + " x" + amount);
        }

        return inputs;
    }

    private List<RecipeOutput> parseOutputs(ConfigurationSection section) {
        List<RecipeOutput> outputs = new ArrayList<>();
        if (section == null) return outputs;

        for (String slot : section.getKeys(false)) {
            ConfigurationSection os = section.getConfigurationSection(slot);
            if (os == null) continue;

            String id = os.getString("id", "");
            if (id.isEmpty()) continue;

            String type = os.getString("type", "minecraft").toLowerCase();
            int amount = Math.max(1, os.getInt("amount", 1));

            outputs.add(new RecipeOutput(slot, id, type, amount));
        }

        return outputs;
    }

    // ==================== UTILITIES ====================

    private Material parseMaterial(String str) {
        if (str == null || str.isEmpty()) return Material.FURNACE;
        Material mat = Material.matchMaterial(str.toUpperCase());
        return mat != null ? mat : Material.FURNACE;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== GETTERS ====================

    public Optional<FurnaceType> getFurnaceType(String id) {
        return Optional.ofNullable(furnaceTypes.get(id));
    }

    public FurnaceType requireFurnaceType(String id) {
        return getFurnaceType(id).orElseThrow(() ->
                new IllegalArgumentException("Unknown furnace type: " + id));
    }

    public Map<String, FurnaceType> getFurnaceTypes() {
        return new LinkedHashMap<>(furnaceTypes);
    }

    public Collection<FurnaceType> getAllTypes() {
        return furnaceTypes.values();
    }

    public int getFurnaceTypeCount() {
        return furnaceTypes.size();
    }

    public Set<String> getTypeIds() {
        return furnaceTypes.keySet();
    }

    public boolean hasType(String id) {
        return furnaceTypes.containsKey(id);
    }

    public List<String> getLoadErrors() {
        return new ArrayList<>(loadErrors);
    }

    public boolean hasErrors() {
        return !loadErrors.isEmpty();
    }
}