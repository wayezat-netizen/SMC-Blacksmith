package com.simmc.blacksmith.furnace;

/**
 * Represents an output item for a furnace smelting recipe.
 */
public record RecipeOutput(
        String slot,
        String id,
        String type,
        int amount
) {
    public RecipeOutput {
        if (slot == null) slot = "a";
        if (id == null) id = "";
        if (type == null) type = "minecraft";
        if (amount < 1) amount = 1;
        type = type.toLowerCase();
    }
}