package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a smelting recipe for custom furnaces.
 * Each recipe has its own ideal temperature range.
 */
public class FurnaceRecipe {

    private final String id;
    private final long smeltTimeMs;
    private final int minTemperature;
    private final int minIdealTemperature;
    private final int maxIdealTemperature;
    private final List<RecipeInput> inputs;
    private final List<RecipeOutput> outputs;
    private final List<RecipeOutput> badOutputs;

    // Constructor with ideal temperature range
    public FurnaceRecipe(String id, long smeltTimeMs, int minTemperature,
                         int minIdealTemperature, int maxIdealTemperature,
                         List<RecipeInput> inputs, List<RecipeOutput> outputs,
                         List<RecipeOutput> badOutputs) {
        this.id = id;
        this.smeltTimeMs = smeltTimeMs;
        this.minTemperature = Math.max(0, minTemperature);
        this.minIdealTemperature = Math.max(minTemperature, minIdealTemperature);
        this.maxIdealTemperature = Math.max(minIdealTemperature, maxIdealTemperature);
        this.inputs = List.copyOf(inputs);
        this.outputs = List.copyOf(outputs);
        this.badOutputs = badOutputs != null ? List.copyOf(badOutputs) : List.of();
    }

    // Legacy constructor (backward compatibility)
    public FurnaceRecipe(String id, long smeltTimeMs, int minTemperature,
                         List<RecipeInput> inputs, List<RecipeOutput> outputs,
                         List<RecipeOutput> badOutputs) {
        this(id, smeltTimeMs, minTemperature,
                minTemperature + 100, minTemperature + 300,  // Default ideal range
                inputs, outputs, badOutputs);
    }

    // Original constructor
    public FurnaceRecipe(String id, long smeltTimeMs, List<RecipeInput> inputs,
                         List<RecipeOutput> outputs, List<RecipeOutput> badOutputs) {
        this(id, smeltTimeMs, 0, inputs, outputs, badOutputs);
    }

    /**
     * Checks if temperature is in this recipe's ideal range.
     */
    public boolean isIdealTemperature(int temp) {
        return temp >= minIdealTemperature && temp <= maxIdealTemperature;
    }

    /**
     * Gets temperature status relative to this recipe's ideal range.
     * Returns: "LOW", "IDEAL", or "HIGH"
     */
    public String getTemperatureStatus(int temp) {
        if (temp < minIdealTemperature) {
            return "LOW";
        } else if (temp > maxIdealTemperature) {
            return "HIGH";
        }
        return "IDEAL";
    }

    /**
     * Gets how far temperature deviates from ideal range.
     * Negative = too cold, Positive = too hot, 0 = ideal
     */
    public int getTemperatureDeviation(int temp) {
        if (temp < minIdealTemperature) {
            return temp - minIdealTemperature;
        } else if (temp > maxIdealTemperature) {
            return temp - maxIdealTemperature;
        }
        return 0;
    }

    /**
     * Checks if the provided input slots match this recipe's requirements.
     * FIXED: Proper matching for both minecraft and custom items.
     */
    public boolean matchesInputs(ItemStack[] slots, ItemProviderRegistry registry) {
        if (slots == null || registry == null) {
            return false;
        }

        // Build map of what's required
        Map<String, Integer> required = new HashMap<>();
        for (RecipeInput input : inputs) {
            String key = buildKey(input.type(), input.id());
            required.merge(key, input.amount(), Integer::sum);
        }

        // Build map of what's available
        Map<String, Integer> available = new HashMap<>();
        for (ItemStack item : slots) {
            if (item == null || item.getType().isAir()) continue;

            // Check each input to see if this item matches
            for (RecipeInput input : inputs) {
                if (matchesInput(item, input, registry)) {
                    String key = buildKey(input.type(), input.id());
                    available.merge(key, item.getAmount(), Integer::sum);
                    break; // Don't double count
                }
            }
        }

        // Check all requirements are met
        for (Map.Entry<String, Integer> req : required.entrySet()) {
            int found = available.getOrDefault(req.getKey(), 0);
            if (found < req.getValue()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if an ItemStack matches a specific input requirement.
     */
    private boolean matchesInput(ItemStack item, RecipeInput input, ItemProviderRegistry registry) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        String inputType = input.type().toLowerCase();
        String inputId = input.id();

        if (inputType.equals("minecraft")) {
            // For minecraft items, compare material names directly
            String itemMaterial = item.getType().name();
            String targetMaterial = inputId.toUpperCase().replace("-", "_").replace(" ", "_");

            // Exact match
            if (itemMaterial.equalsIgnoreCase(targetMaterial)) {
                return true;
            }

            // Without underscores match
            String itemNoUnderscore = itemMaterial.replace("_", "");
            String targetNoUnderscore = targetMaterial.replace("_", "");
            return itemNoUnderscore.equalsIgnoreCase(targetNoUnderscore);
        } else {
            // For custom items (smc, craftengine, etc.), use registry
            return registry.matches(item, inputType, inputId);
        }
    }

    private String buildKey(String type, String id) {
        return type.toLowerCase() + ":" + id.toLowerCase();
    }

    public int getTotalInputAmount() {
        return inputs.stream().mapToInt(RecipeInput::amount).sum();
    }

    public boolean hasBadOutputs() {
        return !badOutputs.isEmpty();
    }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public long getSmeltTimeMs() { return smeltTimeMs; }
    public int getMinTemperature() { return minTemperature; }
    public int getMinIdealTemperature() { return minIdealTemperature; }
    public int getMaxIdealTemperature() { return maxIdealTemperature; }
    public List<RecipeInput> getInputs() { return inputs; }
    public List<RecipeOutput> getOutputs() { return outputs; }
    public List<RecipeOutput> getBadOutputs() { return badOutputs; }
    public int getInputCount() { return inputs.size(); }
}