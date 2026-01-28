package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public boolean matchesInputs(ItemStack[] slots, ItemProviderRegistry registry) {
        if (slots == null || registry == null) return false;

        Map<String, Integer> available = new HashMap<>();
        for (int i = 0; i < slots.length; i++) {
            ItemStack item = slots[i];
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

    private String getItemKey(ItemStack item, ItemProviderRegistry registry) {
        for (String type : new String[]{"nexo", "smc", "minecraft"}) {
            if (registry.hasProvider(type)) {
                String id = getItemIdForType(item, type, registry);
                if (id != null) {
                    return type + ":" + id.toLowerCase();
                }
            }
        }
        return "minecraft:" + item.getType().name().toLowerCase();
    }

    private String getItemIdForType(ItemStack item, String type, ItemProviderRegistry registry) {
        if ("minecraft".equals(type)) {
            return item.getType().name().toLowerCase();
        }
        return null;
    }

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