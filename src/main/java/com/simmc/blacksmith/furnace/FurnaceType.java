package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Represents a type of custom furnace with fully configurable properties.
 * All balance settings are configurable.
 */
public class FurnaceType {

    private final String id;
    private final String itemId;
    private final Material displayMaterial;
    private final int displayCmd;

    // Temperature settings
    private final int maxTemperature;
    private final int minIdealTemperature;
    private final int maxIdealTemperature;

    // Heating/Cooling rates - BALANCED for engaging gameplay
    private final int temperatureChange;
    private final int coolingRate;
    private final double heatingMultiplier;
    private final double coolingMultiplier;

    // Fuel settings
    private final double maxFuelTempPercentage;

    // Bellows settings - BALANCED for constant interaction
    private final double bellowsDecayRate;
    private final double bellowsInstantBoost;

    // Smelting quality settings
    private final long badOutputThresholdMs;
    private final double minIdealRatio;

    // GUI
    private final String guiTitle;
    private final int[] inputSlots;
    private final int fuelSlot;
    private final int outputSlot;

    // Recipes
    private final List<FurnaceRecipe> recipes;

    private FurnaceType(Builder builder) {
        this.id = builder.id;
        this.itemId = builder.itemId;
        this.displayMaterial = builder.displayMaterial;
        this.displayCmd = builder.displayCmd;
        this.maxTemperature = builder.maxTemperature;
        this.minIdealTemperature = builder.minIdealTemperature;
        this.maxIdealTemperature = builder.maxIdealTemperature;
        this.temperatureChange = builder.temperatureChange;
        this.coolingRate = builder.coolingRate;
        this.heatingMultiplier = builder.heatingMultiplier;
        this.coolingMultiplier = builder.coolingMultiplier;
        this.maxFuelTempPercentage = builder.maxFuelTempPercentage;
        this.bellowsDecayRate = builder.bellowsDecayRate;
        this.bellowsInstantBoost = builder.bellowsInstantBoost;
        this.badOutputThresholdMs = builder.badOutputThresholdMs;
        this.minIdealRatio = builder.minIdealRatio;
        this.guiTitle = builder.guiTitle;
        this.inputSlots = builder.inputSlots;
        this.fuelSlot = builder.fuelSlot;
        this.outputSlot = builder.outputSlot;
        this.recipes = List.copyOf(builder.recipes);
    }

    // ==================== TEMPERATURE ====================

    public boolean isIdealTemperature(int temp) {
        return temp >= minIdealTemperature && temp <= maxIdealTemperature;
    }

    public int getMaxFuelTemperature() {
        return (int) (maxTemperature * maxFuelTempPercentage);
    }

    // ==================== RECIPES ====================

    public FurnaceRecipe findMatchingRecipe(ItemStack[] inputs, ItemProviderRegistry registry) {
        for (FurnaceRecipe recipe : recipes) {
            if (recipe.matchesInputs(inputs, registry)) {
                return recipe;
            }
        }
        return null;
    }

    public Optional<FurnaceRecipe> getRecipe(String recipeId) {
        return recipes.stream()
                .filter(r -> r.getId().equals(recipeId))
                .findFirst();
    }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public String getItemId() { return itemId; }
    public Material getDisplayMaterial() { return displayMaterial; }
    public int getDisplayCmd() { return displayCmd; }
    public int getMaxTemperature() { return maxTemperature; }
    public int getMinIdealTemperature() { return minIdealTemperature; }
    public int getMaxIdealTemperature() { return maxIdealTemperature; }
    public int getTemperatureChange() { return temperatureChange; }
    public int getCoolingRate() { return coolingRate; }
    public double getHeatingMultiplier() { return heatingMultiplier; }
    public double getCoolingMultiplier() { return coolingMultiplier; }
    public double getMaxFuelTempPercentage() { return maxFuelTempPercentage; }
    public double getBellowsDecayRate() { return bellowsDecayRate; }
    public double getBellowsInstantBoost() { return bellowsInstantBoost; }
    public long getBadOutputThresholdMs() { return badOutputThresholdMs; }
    public double getMinIdealRatio() { return minIdealRatio; }
    public String getGuiTitle() { return guiTitle; }
    public int[] getInputSlots() { return inputSlots != null ? Arrays.copyOf(inputSlots, inputSlots.length) : new int[]{10, 11, 19, 20}; }
    public int getFuelSlot() { return fuelSlot; }
    public int getOutputSlot() { return outputSlot; }
    public List<FurnaceRecipe> getRecipes() { return recipes; }
    public int getRecipeCount() { return recipes.size(); }

    // ==================== BUILDER ====================

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String itemId;
        private Material displayMaterial = Material.FURNACE;
        private int displayCmd = 0;

        // Temperature - reasonable defaults
        private int maxTemperature = 1000;
        private int minIdealTemperature = 500;
        private int maxIdealTemperature = 800;

        // Heating/Cooling - BALANCED for engagement
        private int temperatureChange = 8;       // Base temp change per tick
        private int coolingRate = 6;             // How fast it cools (higher = faster cooling)
        private double heatingMultiplier = 0.4;  // Slower heating (40% of base)
        private double coolingMultiplier = 0.5;  // Moderate cooling (50% of base)

        // Fuel
        private double maxFuelTempPercentage = 0.6; // Fuel alone gets to 60% of max

        // Bellows - BALANCED for constant interaction
        private double bellowsDecayRate = 0.08;    // 8% decay per tick - need constant bellows use
        private double bellowsInstantBoost = 0.6;  // 60% applied instantly

        // Smelting quality
        private long badOutputThresholdMs = 4000;  // 4 seconds outside ideal = bad
        private double minIdealRatio = 0.5;        // Need 50% time in ideal

        // GUI
        private String guiTitle = "&8Furnace";
        private int[] inputSlots = new int[]{10, 11, 19, 20};
        private int fuelSlot = 40;
        private int outputSlot = 24;

        private List<FurnaceRecipe> recipes = new ArrayList<>();

        public Builder(String id) {
            this.id = id;
            this.itemId = id;
        }

        public Builder itemId(String itemId) { this.itemId = itemId; return this; }
        public Builder displayMaterial(Material material) { this.displayMaterial = material != null ? material : Material.FURNACE; return this; }
        public Builder displayCmd(int cmd) { this.displayCmd = cmd; return this; }
        public Builder maxTemperature(int temp) { this.maxTemperature = Math.max(100, temp); return this; }

        public Builder idealTemperatureRange(int min, int max) {
            this.minIdealTemperature = Math.min(min, max);
            this.maxIdealTemperature = Math.max(min, max);
            return this;
        }

        public Builder temperatureChange(int change) { this.temperatureChange = Math.max(1, change); return this; }
        public Builder coolingRate(int rate) { this.coolingRate = Math.max(1, rate); return this; }
        public Builder heatingMultiplier(double mult) { this.heatingMultiplier = clamp(mult, 0.1, 1.0); return this; }
        public Builder coolingMultiplier(double mult) { this.coolingMultiplier = clamp(mult, 0.1, 1.0); return this; }
        public Builder maxFuelTempPercentage(double pct) { this.maxFuelTempPercentage = clamp(pct, 0.1, 1.0); return this; }
        public Builder bellowsDecayRate(double rate) { this.bellowsDecayRate = clamp(rate, 0.01, 0.5); return this; }
        public Builder bellowsInstantBoost(double boost) { this.bellowsInstantBoost = clamp(boost, 0.0, 1.0); return this; }
        public Builder badOutputThresholdMs(long ms) { this.badOutputThresholdMs = Math.max(1000, ms); return this; }
        public Builder minIdealRatio(double ratio) { this.minIdealRatio = clamp(ratio, 0.1, 0.9); return this; }
        public Builder guiTitle(String title) { this.guiTitle = title != null ? title : "&8Furnace"; return this; }
        public Builder inputSlots(int[] slots) { this.inputSlots = slots; return this; }
        public Builder fuelSlot(int slot) { this.fuelSlot = slot; return this; }
        public Builder outputSlot(int slot) { this.outputSlot = slot; return this; }

        public Builder recipes(List<FurnaceRecipe> recipes) {
            this.recipes = recipes != null ? new ArrayList<>(recipes) : new ArrayList<>();
            return this;
        }

        public Builder addRecipe(FurnaceRecipe recipe) {
            if (recipe != null) this.recipes.add(recipe);
            return this;
        }

        private double clamp(double val, double min, double max) {
            return Math.max(min, Math.min(max, val));
        }

        public FurnaceType build() {
            if (maxIdealTemperature > maxTemperature) {
                maxIdealTemperature = maxTemperature;
            }
            if (minIdealTemperature > maxIdealTemperature) {
                minIdealTemperature = maxIdealTemperature;
            }
            return new FurnaceType(this);
        }
    }
}