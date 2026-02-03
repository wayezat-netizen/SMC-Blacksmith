package com.simmc.blacksmith.furnace;

/**
 * Represents an input item for a furnace smelting recipe.
 * Uses letter-based slots (a, b, c, d, e) as defined in config.
 */
public record RecipeInput(
        String slot,
        String id,
        String type,
        int amount
) {
    public RecipeInput {
        if (slot == null) slot = "a";
        if (id == null) id = "";
        if (type == null) type = "minecraft";
        if (amount < 1) amount = 1;
        type = type.toLowerCase();
    }
}