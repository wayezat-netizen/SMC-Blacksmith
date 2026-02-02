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

        Map<String, Integer> available = new HashMap<>();
        for (ItemStack item : slots) {
            if (item == null || item.getType().isAir()) continue;

            String key = getItemKey(item, registry);
            if (key != null) {
                available.merge(key, item.getAmount(), Integer::sum);
            }
        }

        for (RecipeInput input : inputs) {
            String requiredKey = input.type().toLowerCase() + ":" + input.id().toLowerCase();
            int requiredAmount = input.amount();
            int availableAmount = available.getOrDefault(requiredKey, 0);

            if (availableAmount < requiredAmount) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets a unique key for an item based on its provider type.
     */
    private String getItemKey(ItemStack item, ItemProviderRegistry registry) {
        if (item == null) return null;

        // FIXED: Check each provider type properly
        // Priority: nexo > smc > craftengine > minecraft

        String[] providerTypes = {"nexo", "smc", "craftengine"};

        for (String type : providerTypes) {
            if (registry.hasProvider(type)) {
                String id = getItemIdForType(item, type, registry);
                if (id != null && !id.isEmpty()) {
                    return type + ":" + id.toLowerCase();
                }
            }
        }

        // Fallback to minecraft
        return "minecraft:" + item.getType().name().toLowerCase();
    }

    /**
     * Gets the item ID for a specific provider type.
     * FIXED: Now properly handles all provider types.
     */
    private String getItemIdForType(ItemStack item, String type, ItemProviderRegistry registry) {
        if (item == null || type == null || registry == null) {
            return null;
        }

        switch (type.toLowerCase()) {
            case "minecraft":
                return item.getType().name().toLowerCase();

            case "nexo":
                // Check if the item matches any Nexo item
                // We need to iterate through known IDs or use the provider's getId method
                return getCustomItemId(item, registry, "nexo");

            case "smc":
                return getCustomItemId(item, registry, "smc");

            case "craftengine":
                return getCustomItemId(item, registry, "craftengine");

            default:
                return null;
        }
    }

    /**
     * Attempts to get a custom item ID from the registry.
     * This checks if the item matches any known custom item.
     */
    private String getCustomItemId(ItemStack item, ItemProviderRegistry registry, String type) {
        // For custom items, we need to check against the inputs we're looking for
        // This is a limitation - we can only match items we know about

        for (RecipeInput input : inputs) {
            if (input.type().equalsIgnoreCase(type)) {
                if (registry.matches(item, type, input.id())) {
                    return input.id();
                }
            }
        }

        return null;
    }

    // Getters

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