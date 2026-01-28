package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class FurnaceType {

    private final String id;
    private final String itemId;
    private final int maxTemperature;
    private final Material displayMaterial;
    private final int displayCmd;
    private final int temperatureChange;
    private final int minIdealTemperature;
    private final int maxIdealTemperature;
    private final double maxFuelTempPercentage;
    private final List<FurnaceRecipe> recipes;

    public FurnaceType(String id, String itemId, int maxTemperature, Material displayMaterial,
                       int displayCmd, int temperatureChange, int minIdealTemperature,
                       int maxIdealTemperature, double maxFuelTempPercentage,
                       List<FurnaceRecipe> recipes) {
        this.id = id;
        this.itemId = itemId;
        this.maxTemperature = maxTemperature;
        this.displayMaterial = displayMaterial;
        this.displayCmd = displayCmd;
        this.temperatureChange = temperatureChange;
        this.minIdealTemperature = minIdealTemperature;
        this.maxIdealTemperature = maxIdealTemperature;
        this.maxFuelTempPercentage = maxFuelTempPercentage;
        this.recipes = recipes;
    }

    public boolean isIdealTemperature(int temp) {
        return temp >= minIdealTemperature && temp <= maxIdealTemperature;
    }

    public int getMaxFuelTemperature() {
        return (int) (maxTemperature * maxFuelTempPercentage);
    }

    public FurnaceRecipe findMatchingRecipe(ItemStack[] inputs, ItemProviderRegistry registry) {
        for (FurnaceRecipe recipe : recipes) {
            if (recipe.matchesInputs(inputs, registry)) {
                return recipe;
            }
        }
        return null;
    }

    public String getId() {
        return id;
    }

    public String getItemId() {
        return itemId;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public Material getDisplayMaterial() {
        return displayMaterial;
    }

    public int getDisplayCmd() {
        return displayCmd;
    }

    public int getTemperatureChange() {
        return temperatureChange;
    }

    public int getMinIdealTemperature() {
        return minIdealTemperature;
    }

    public int getMaxIdealTemperature() {
        return maxIdealTemperature;
    }

    public double getMaxFuelTempPercentage() {
        return maxFuelTempPercentage;
    }

    public List<FurnaceRecipe> getRecipes() {
        return recipes;
    }

    public int getRecipeCount() {
        return recipes.size();
    }
}