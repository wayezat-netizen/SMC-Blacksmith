package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class FurnaceInstance {

    private static final long BAD_OUTPUT_THRESHOLD_MS = 5000;

    private final UUID id;
    private final FurnaceType type;
    private final Location location;

    private int currentTemperature;
    private int targetTemperature;
    private boolean burning;
    private long burnTimeRemaining;
    private long smeltProgress;
    private long smeltTimeTotal;
    private FurnaceRecipe currentRecipe;
    private long timeOutsideIdealRange;

    private ItemStack[] inputSlots;
    private ItemStack fuelSlot;
    private ItemStack outputSlot;

    private long lastTickTime;

    public FurnaceInstance(FurnaceType type, Location location) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.location = location.clone();

        this.currentTemperature = 0;
        this.targetTemperature = 0;
        this.burning = false;
        this.burnTimeRemaining = 0;
        this.smeltProgress = 0;
        this.smeltTimeTotal = 0;
        this.currentRecipe = null;
        this.timeOutsideIdealRange = 0;

        this.inputSlots = new ItemStack[9];
        this.fuelSlot = null;
        this.outputSlot = null;

        this.lastTickTime = System.currentTimeMillis();
    }

    public void tick(ItemProviderRegistry registry, FuelConfig fuelConfig) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickTime;
        lastTickTime = now;

        updateBurning(fuelConfig, registry);
        updateTemperature();

        if (currentRecipe == null) {
            findMatchingRecipe(registry);
        }

        if (currentRecipe != null) {
            processSmelting(elapsed, registry);
        }
    }

    private void updateBurning(FuelConfig fuelConfig, ItemProviderRegistry registry) {
        if (burnTimeRemaining > 0) {
            burnTimeRemaining--;
            burning = true;
        } else {
            burning = false;
            consumeFuel(fuelConfig, registry);
        }
    }

    private void consumeFuel(FuelConfig fuelConfig, ItemProviderRegistry registry) {
        if (fuelSlot == null || fuelSlot.getType().isAir()) {
            targetTemperature = 0;
            return;
        }

        FuelConfig.FuelData fuelData = fuelConfig.getFuelData(fuelSlot);
        if (fuelData == null) {
            targetTemperature = 0;
            return;
        }

        burnTimeRemaining = fuelData.burnTimeTicks();
        int maxFuelTemp = type.getMaxFuelTemperature();
        targetTemperature = Math.min(fuelData.temperatureBoost(), maxFuelTemp);
        burning = true;

        int newAmount = fuelSlot.getAmount() - 1;
        if (newAmount <= 0) {
            fuelSlot = null;
        } else {
            fuelSlot.setAmount(newAmount);
        }
    }

    private void updateTemperature() {
        if (currentTemperature < targetTemperature) {
            currentTemperature = Math.min(currentTemperature + type.getTemperatureChange(), targetTemperature);
        } else if (currentTemperature > targetTemperature) {
            currentTemperature = Math.max(currentTemperature - type.getTemperatureChange(), targetTemperature);
        }

        currentTemperature = Math.max(0, Math.min(currentTemperature, type.getMaxTemperature()));
    }

    private void findMatchingRecipe(ItemProviderRegistry registry) {
        currentRecipe = type.findMatchingRecipe(inputSlots, registry);
        if (currentRecipe != null) {
            smeltTimeTotal = currentRecipe.getSmeltTimeMs();
            smeltProgress = 0;
            timeOutsideIdealRange = 0;
        }
    }

    private void processSmelting(long elapsedMs, ItemProviderRegistry registry) {
        if (!currentRecipe.matchesInputs(inputSlots, registry)) {
            resetSmelting();
            return;
        }

        if (type.isIdealTemperature(currentTemperature)) {
            timeOutsideIdealRange = 0;
            smeltProgress += elapsedMs;

            if (smeltProgress >= smeltTimeTotal) {
                completeSmelting(true, registry);
            }
        } else {
            timeOutsideIdealRange += elapsedMs;

            if (timeOutsideIdealRange >= BAD_OUTPUT_THRESHOLD_MS) {
                completeSmelting(false, registry);
            }
        }
    }

    private void completeSmelting(boolean success, ItemProviderRegistry registry) {
        consumeInputs(registry);

        if (success) {
            giveOutputs(currentRecipe.getOutputs(), registry);
        } else {
            giveOutputs(currentRecipe.getBadOutputs(), registry);
        }

        resetSmelting();
    }

    private void consumeInputs(ItemProviderRegistry registry) {
        for (RecipeInput input : currentRecipe.getInputs()) {
            int remaining = input.amount();

            for (int i = 0; i < inputSlots.length && remaining > 0; i++) {
                ItemStack slot = inputSlots[i];
                if (slot == null || slot.getType().isAir()) continue;

                if (registry.matches(slot, input.type(), input.id())) {
                    int take = Math.min(remaining, slot.getAmount());
                    remaining -= take;

                    int newAmount = slot.getAmount() - take;
                    if (newAmount <= 0) {
                        inputSlots[i] = null;
                    } else {
                        slot.setAmount(newAmount);
                    }
                }
            }
        }
    }

    private void giveOutputs(java.util.List<RecipeOutput> outputs, ItemProviderRegistry registry) {
        for (RecipeOutput output : outputs) {
            ItemStack item = registry.getItem(output.type(), output.id(), output.amount());
            if (item == null) continue;

            if (outputSlot == null || outputSlot.getType().isAir()) {
                outputSlot = item.clone();
            } else if (outputSlot.isSimilar(item)) {
                int newAmount = outputSlot.getAmount() + item.getAmount();
                int maxStack = outputSlot.getMaxStackSize();
                outputSlot.setAmount(Math.min(newAmount, maxStack));
            }
        }
    }

    private void resetSmelting() {
        currentRecipe = null;
        smeltProgress = 0;
        smeltTimeTotal = 0;
        timeOutsideIdealRange = 0;
    }

    public void applyBellows(int temperatureBoost) {
        targetTemperature = Math.min(targetTemperature + temperatureBoost, type.getMaxTemperature());
    }

    public UUID getId() {
        return id;
    }

    public FurnaceType getType() {
        return type;
    }

    public Location getLocation() {
        return location.clone();
    }

    public int getCurrentTemperature() {
        return currentTemperature;
    }

    public int getTargetTemperature() {
        return targetTemperature;
    }

    public boolean isBurning() {
        return burning;
    }

    public long getBurnTimeRemaining() {
        return burnTimeRemaining;
    }

    public double getSmeltProgress() {
        if (smeltTimeTotal <= 0) return 0.0;
        return Math.min(1.0, (double) smeltProgress / smeltTimeTotal);
    }

    public long getSmeltProgressMs() {
        return smeltProgress;
    }

    public long getSmeltTimeTotal() {
        return smeltTimeTotal;
    }

    public FurnaceRecipe getCurrentRecipe() {
        return currentRecipe;
    }

    public ItemStack[] getInputSlots() {
        return inputSlots;
    }

    public void setInputSlots(ItemStack[] slots) {
        if (slots == null) {
            this.inputSlots = new ItemStack[9];
        } else {
            this.inputSlots = new ItemStack[9];
            for (int i = 0; i < Math.min(slots.length, 9); i++) {
                this.inputSlots[i] = slots[i] != null ? slots[i].clone() : null;
            }
        }
    }

    public ItemStack getFuelSlot() {
        return fuelSlot;
    }

    public void setFuelSlot(ItemStack fuel) {
        this.fuelSlot = fuel != null ? fuel.clone() : null;
    }

    public ItemStack getOutputSlot() {
        return outputSlot;
    }

    public void setOutputSlot(ItemStack output) {
        this.outputSlot = output != null ? output.clone() : null;
    }

    public void setCurrentTemperature(int temperature) {
        this.currentTemperature = Math.max(0, Math.min(temperature, type.getMaxTemperature()));
    }

    public void setTargetTemperature(int temperature) {
        this.targetTemperature = Math.max(0, Math.min(temperature, type.getMaxTemperature()));
    }
}