package com.simmc.blacksmith.furnace;

public record RecipeOutput(
        String slot,
        String id,
        String type,
        int amount
) {
    public RecipeOutput {
        if (amount < 1) amount = 1;
    }
}