package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Represents a type of custom furnace with fully configurable properties.
 *
 * ADDED:
 * - CE furniture/block ID restrictions
 * - Allowed input items list
 */
public class FurnaceType {

    private final String id;
    private final String itemId;
    private final Material displayMaterial;
    private final int displayCmd;

    // CraftEngine integration
    private final String furnitureId;
    private final String blockId;
    private final Set<String> allowedFurnitureIds;

    // Temperature settings
    private final int maxTemperature;
    private final int minIdealTemperature;
    private final int maxIdealTemperature;

    // Heating/Cooling rates
    private final int temperatureChange;
    private final int coolingRate;
    private final double heatingMultiplier;
    private final double coolingMultiplier;

    // Fuel settings
    private final double maxFuelTempPercentage;

    // Bellows settings
    private final double bellowsDecayRate;
    private final double bellowsInstantBoost;

    // Smelting quality settings
    private final long badOutputThresholdMs;
    private final double minIdealRatio;

    // Input restrictions
    private final Set<AllowedInput> allowedInputs;
    private final boolean restrictInputs;

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

        this.furnitureId = builder.furnitureId;
        this.blockId = builder.blockId;
        this.allowedFurnitureIds = Set.copyOf(builder.allowedFurnitureIds);

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

        this.allowedInputs = Set.copyOf(builder.allowedInputs);
        this.restrictInputs = builder.restrictInputs;

        this.guiTitle = builder.guiTitle;
        this.inputSlots = builder.inputSlots;
        this.fuelSlot = builder.fuelSlot;
        this.outputSlot = builder.outputSlot;
        this.recipes = List.copyOf(builder.recipes);
    }

    // ==================== CE FURNITURE CHECKS ====================

    public boolean requiresCEFurniture() {
        return (furnitureId != null && !furnitureId.isEmpty())
                || (blockId != null && !blockId.isEmpty())
                || !allowedFurnitureIds.isEmpty();
    }

    public boolean matchesFurniture(String checkId) {
        if (checkId == null || checkId.isEmpty()) {
            return !requiresCEFurniture();
        }

        String normalizedCheck = normalizeId(checkId);

        if (furnitureId != null && !furnitureId.isEmpty()) {
            if (normalizeId(furnitureId).equals(normalizedCheck)) {
                return true;
            }
        }

        if (blockId != null && !blockId.isEmpty()) {
            if (normalizeId(blockId).equals(normalizedCheck)) {
                return true;
            }
        }

        for (String allowed : allowedFurnitureIds) {
            if (normalizeId(allowed).equals(normalizedCheck)) {
                return true;
            }
        }

        return false;
    }

    private String normalizeId(String id) {
        if (id == null) return "";
        return id.toLowerCase().trim();
    }

    // ==================== INPUT RESTRICTIONS ====================

    public boolean hasInputRestrictions() {
        return restrictInputs && !allowedInputs.isEmpty();
    }

    public boolean isAllowedInput(ItemStack item, ItemProviderRegistry registry) {
        if (!restrictInputs || allowedInputs.isEmpty()) {
            return true;
        }

        if (item == null || item.getType().isAir()) {
            return true;
        }

        for (AllowedInput allowed : allowedInputs) {
            if (allowed.matches(item, registry)) {
                return true;
            }
        }

        return false;
    }

    public List<String> getAllowedInputDescriptions() {
        List<String> descriptions = new ArrayList<>();
        for (AllowedInput input : allowedInputs) {
            descriptions.add(input.description());
        }
        return descriptions;
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

    public String getFurnitureId() { return furnitureId; }
    public String getBlockId() { return blockId; }
    public Set<String> getAllowedFurnitureIds() { return allowedFurnitureIds; }

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

    public Set<AllowedInput> getAllowedInputs() { return allowedInputs; }
    public boolean isRestrictInputs() { return restrictInputs; }

    public String getGuiTitle() { return guiTitle; }
    public int[] getInputSlots() { return inputSlots != null ? Arrays.copyOf(inputSlots, inputSlots.length) : new int[]{10, 11, 19, 20}; }
    public int getFuelSlot() { return fuelSlot; }
    public int getOutputSlot() { return outputSlot; }
    public List<FurnaceRecipe> getRecipes() { return recipes; }
    public int getRecipeCount() { return recipes.size(); }

    // ==================== ALLOWED INPUT RECORD ====================

    public record AllowedInput(String type, String id, String description) {

        public boolean matches(ItemStack item, ItemProviderRegistry registry) {
            if (item == null || item.getType().isAir()) return false;
            return registry.matches(item, type, id);
        }

        public static AllowedInput minecraft(String materialName) {
            return new AllowedInput("minecraft", materialName.toUpperCase(), materialName);
        }

        public static AllowedInput smc(String itemId) {
            return new AllowedInput("smc", itemId, itemId);
        }

        public static AllowedInput ce(String itemId) {
            return new AllowedInput("ce", itemId, itemId);
        }
    }

    // ==================== BUILDER ====================

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static class Builder {
        private final String id;
        private String itemId;
        private Material displayMaterial = Material.FURNACE;
        private int displayCmd = 0;

        private String furnitureId = null;
        private String blockId = null;
        private Set<String> allowedFurnitureIds = new HashSet<>();

        private int maxTemperature = 1000;
        private int minIdealTemperature = 500;
        private int maxIdealTemperature = 800;

        private int temperatureChange = 8;
        private int coolingRate = 6;
        private double heatingMultiplier = 0.4;
        private double coolingMultiplier = 0.5;

        private double maxFuelTempPercentage = 0.6;

        private double bellowsDecayRate = 0.08;
        private double bellowsInstantBoost = 0.6;

        private long badOutputThresholdMs = 4000;
        private double minIdealRatio = 0.5;

        private Set<AllowedInput> allowedInputs = new HashSet<>();
        private boolean restrictInputs = false;

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

        public Builder furnitureId(String furnitureId) { this.furnitureId = furnitureId; return this; }
        public Builder blockId(String blockId) { this.blockId = blockId; return this; }
        public Builder allowedFurnitureIds(Set<String> ids) {
            this.allowedFurnitureIds = ids != null ? new HashSet<>(ids) : new HashSet<>();
            return this;
        }
        public Builder addAllowedFurnitureId(String id) {
            if (id != null && !id.isEmpty()) this.allowedFurnitureIds.add(id);
            return this;
        }

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

        public Builder restrictInputs(boolean restrict) { this.restrictInputs = restrict; return this; }
        public Builder allowedInputs(Set<AllowedInput> inputs) {
            this.allowedInputs = inputs != null ? new HashSet<>(inputs) : new HashSet<>();
            return this;
        }
        public Builder addAllowedInput(AllowedInput input) {
            if (input != null) this.allowedInputs.add(input);
            return this;
        }
        public Builder addAllowedInput(String type, String id) {
            this.allowedInputs.add(new AllowedInput(type, id, id));
            return this;
        }

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