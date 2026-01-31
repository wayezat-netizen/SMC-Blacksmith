package com.simmc.blacksmith.furnace;

public record RecipeInput(
        String slot,
        String id,
        String type,
        int amount
) {
    public RecipeInput {
        if (amount < 1) amount = 1;
    }
}