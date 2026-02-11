package com.simmc.blacksmith.forge;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a category of forge recipes in the GUI.
 */
public class ForgeCategory {

    private final String id;
    private final String displayName;
    private final Material iconMaterial;
    private final int iconCmd;
    private final List<String> description;
    private final List<String> recipeIds;
    private final int guiSlot;

    public ForgeCategory(String id, String displayName, Material iconMaterial, int iconCmd,
                         List<String> description, List<String> recipeIds, int guiSlot) {
        this.id = id;
        this.displayName = displayName;
        this.iconMaterial = iconMaterial != null ? iconMaterial : Material.IRON_INGOT;
        this.iconCmd = iconCmd;
        this.description = description != null ? new ArrayList<>(description) : new ArrayList<>();
        this.recipeIds = recipeIds != null ? new ArrayList<>(recipeIds) : new ArrayList<>();
        this.guiSlot = guiSlot;
    }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getIconMaterial() { return iconMaterial; }
    public int getIconCmd() { return iconCmd; }
    public List<String> getDescription() { return new ArrayList<>(description); }
    public List<String> getRecipeIds() { return new ArrayList<>(recipeIds); }
    public int getGuiSlot() { return guiSlot; }

    public boolean containsRecipe(String recipeId) {
        return recipeIds.contains(recipeId);
    }

    public int getRecipeCount() {
        return recipeIds.size();
    }

    public boolean isEmpty() {
        return recipeIds.isEmpty();
    }
}