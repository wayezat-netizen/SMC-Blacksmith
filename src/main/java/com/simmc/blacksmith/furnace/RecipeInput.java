package com.simmc.blacksmith.furnace;

/**
 * Represents an input item requirement for a furnace recipe.
 */
public record RecipeInput(
        String slot,
        String id,
        String type,
        int amount
) {
    // Compact constructor for validation
    public RecipeInput {
        slot = slot != null ? slot : "a";
        id = id != null ? id : "";
        type = (type != null ? type : "minecraft").toLowerCase();
        amount = Math.max(1, amount);
    }

    /**
     * Creates an input with just ID and amount (defaults to minecraft type, slot a).
     */
    public static RecipeInput of(String id, int amount) {
        return new RecipeInput("a", id, "minecraft", amount);
    }

    /**
     * Creates an input with ID, type, and amount (defaults to slot a).
     */
    public static RecipeInput of(String id, String type, int amount) {
        return new RecipeInput("a", id, type, amount);
    }

    public boolean isVanilla() {
        return "minecraft".equalsIgnoreCase(type);
    }

    public boolean isSMCCore() {
        return "smc".equalsIgnoreCase(type);
    }

    public boolean isEmpty() {
        return id == null || id.isEmpty();
    }

    /**
     * Gets a unique key for this input type.
     */
    public String getKey() {
        return type.toLowerCase() + ":" + id.toLowerCase();
    }
}