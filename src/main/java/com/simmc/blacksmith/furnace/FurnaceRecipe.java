package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a smelting recipe for custom furnaces.
 * Handles input matching and output generation.
 */
public class FurnaceRecipe {

    private final String id;
    private final long smeltTimeMs;
    private final List<RecipeInput> inputs;
    private final List<RecipeOutput> outputs;
    private final List<RecipeOutput> badOutputs;

    public FurnaceRecipe(String id, long smeltTimeMs, List<RecipeInput> inputs,
                         List<RecipeOutput> outputs, List<RecipeOutput> badOutputs) {
        this.id = id;
        this.smeltTimeMs = smeltTimeMs;
        this.inputs = inputs;
        this.outputs = outputs;
        this.badOutputs = badOutputs;
    }

    /**
     * Checks if the provided input slots match this recipe's requirements.
     */
    public boolean matchesInputs(ItemStack[] slots, ItemProviderRegistry registry) {
        if (slots == null || registry == null) return false;

        // Build a map of available items by type:id
        Map<String, Integer> available = new HashMap<>();

        for (ItemStack item : slots) {
            if (item == null || item.getType().isAir()) continue;

            // Check each input to find matching type
            for (RecipeInput input : inputs) {
                if (registry.matches(item, input.type(), input.id())) {
                    String key = input.type().toLowerCase() + ":" + input.id().toLowerCase();
                    available.merge(key, item.getAmount(), Integer::sum);
                    break; // Item matched, don't check other inputs
                }
            }

            // Also check for vanilla minecraft items
            String vanillaKey = "minecraft:" + item.getType().name().toLowerCase();
            if (!available.containsKey(vanillaKey)) {
                // Check if this vanilla item is needed
                for (RecipeInput input : inputs) {
                    if (input.type().equalsIgnoreCase("minecraft") &&
                            input.id().equalsIgnoreCase(item.getType().name())) {
                        available.merge(vanillaKey, item.getAmount(), Integer::sum);
                        break;
                    }
                }
            }
        }

        // Check if all required inputs are available
        for (RecipeInput input : inputs) {
            String key = input.type().toLowerCase() + ":" + input.id().toLowerCase();
            int required = input.amount();
            int found = available.getOrDefault(key, 0);

            if (found < required) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the total number of input item types required.
     */
    public int getInputCount() {
        return inputs.size();
    }

    /**
     * Gets the total number of items required across all inputs.
     */
    public int getTotalInputAmount() {
        int total = 0;
        for (RecipeInput input : inputs) {
            total += input.amount();
        }
        return total;
    }

    // ==================== GETTERS ====================

    public String getId() {
        return id;
    }

    public long getSmeltTimeMs() {
        return smeltTimeMs;
    }

    public List<RecipeInput> getInputs() {
        return inputs;
    }

    public List<RecipeOutput> getOutputs() {
        return outputs;
    }

    public List<RecipeOutput> getBadOutputs() {
        return badOutputs;
    }
}