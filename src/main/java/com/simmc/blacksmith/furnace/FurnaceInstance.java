package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Furnace with TIME-BASED burning (not tick-based).
 *
 * FIXES:
 * - Improved fuel consumption tracking
 * - Added fuel consumed counter for debugging
 * - Better sync between GUI and instance
 */
public class FurnaceInstance {

    private static final int DEFAULT_INPUT_SLOTS = 9;
    private static final boolean DEBUG = true;
    private static final double FUEL_BASE_HEAT_PERCENTAGE = 0.25;

    private final UUID id;
    private final FurnaceType type;
    private final Location location;

    // Temperature
    private int currentTemperature;
    private int bellowsBoost;
    private long lastBellowsTime;

    // Burn state - NOW TIME-BASED
    private boolean burning;
    private int fuelMaxTemperature;
    private int fuelBaseTemperature;
    private long burnEndTime;      // System time when burn ends
    private long burnStartTime;    // System time when burn started
    private long burnDurationMs;   // Total burn duration in milliseconds

    // Fuel tracking
    private int fuelConsumedCount; // Track how many fuel items consumed
    private String currentFuelType; // Track current fuel type for debugging

    // Smelting
    private long smeltProgress;
    private long smeltTimeTotal;
    private FurnaceRecipe currentRecipe;
    private long timeOutsideIdealRange;
    private long timeInsideIdealRange;
    private boolean reachedIdealDuringSmelting;

    // Inventory
    private ItemStack[] inputSlots;
    private ItemStack fuelSlot;
    private ItemStack outputSlot;

    // Timing
    private long lastTickTime;
    private boolean dirty;
    private long lastDebugLog;

    public FurnaceInstance(FurnaceType type, Location location) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.location = location.clone();
        this.inputSlots = new ItemStack[DEFAULT_INPUT_SLOTS];
        this.lastTickTime = System.currentTimeMillis();
        this.lastBellowsTime = 0;
        this.lastDebugLog = 0;
        this.dirty = false;
        this.fuelConsumedCount = 0;
        reset();
    }

    public void reset() {
        this.currentTemperature = 0;
        this.bellowsBoost = 0;
        this.burning = false;
        this.fuelMaxTemperature = 0;
        this.fuelBaseTemperature = 0;
        this.burnEndTime = 0;
        this.burnStartTime = 0;
        this.burnDurationMs = 0;
        this.smeltProgress = 0;
        this.smeltTimeTotal = 0;
        this.currentRecipe = null;
        this.timeOutsideIdealRange = 0;
        this.timeInsideIdealRange = 0;
        this.reachedIdealDuringSmelting = false;
        this.lastBellowsTime = 0;
        this.currentFuelType = null;
    }

    private void debug(String msg) {
        if (DEBUG) {
            Bukkit.getLogger().info("[Furnace " + id.toString().substring(0, 8) + "] " + msg);
        }
    }

    public void tick(ItemProviderRegistry registry, FuelConfig fuelConfig) {
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickTime;
        lastTickTime = now;

        // Skip if elapsed time is too small (prevent double-ticking)
        if (elapsed < 40) return;

        // Check fuel removed
        checkFuelRemoved();

        // Update burning (time-based)
        updateBurningState(now, fuelConfig, registry);

        // Decay bellows
        updateBellowsDecay(now);

        // Update temp
        updateTemperature();

        // Recipe
        if (currentRecipe == null && currentTemperature > 0) {
            findMatchingRecipe(registry);
        }

        if (currentRecipe != null && currentTemperature > 0) {
            processSmelting(elapsed, registry);
        }
    }

    // ==================== FUEL CHECK ====================

    private void checkFuelRemoved() {
        if (burning && !hasFuelInSlot()) {
            debug("!!! FUEL REMOVED WHILE BURNING - STOPPING !!!");
            stopBurning();
        }
    }

    private void stopBurning() {
        burning = false;
        fuelMaxTemperature = 0;
        fuelBaseTemperature = 0;
        burnEndTime = 0;
        burnStartTime = 0;
        burnDurationMs = 0;
        currentFuelType = null;
        markDirty();
    }

    // ==================== BURNING (TIME-BASED) ====================

    private void updateBurningState(long now, FuelConfig fuelConfig, ItemProviderRegistry registry) {
        // Debug log every 5 seconds when burning
        if (burning && (now - lastDebugLog) >= 5000) {
            lastDebugLog = now;
            long remaining = burnEndTime - now;
            debug("BURNING: " + (remaining / 1000) + "s left | temp=" + currentTemperature +
                    " | fuel=" + (fuelSlot != null ? fuelSlot.getType() + " x" + fuelSlot.getAmount() : "NONE") +
                    " | consumed=" + fuelConsumedCount);
        }

        // Check if burn time expired
        if (burning && now >= burnEndTime) {
            debug("=== BURN TIME EXPIRED for " + currentFuelType + " ===");
            consumeOneFuel();

            // Try to start burning next fuel
            if (hasFuelInSlot() && fuelConfig != null) {
                debug("Attempting to start next fuel...");
                tryStartBurning(now, fuelConfig);
            } else {
                debug("No more fuel - stopping burn");
                stopBurning();
            }
            return;
        }

        // If not burning but has fuel, try to start
        if (!burning && hasFuelInSlot() && fuelConfig != null) {
            tryStartBurning(now, fuelConfig);
        }
    }

    /**
     * Consumes exactly ONE fuel item from the fuel slot.
     * This is called when burn time expires.
     */
    private void consumeOneFuel() {
        if (fuelSlot == null || fuelSlot.getType().isAir()) {
            debug("consumeOneFuel: No fuel to consume!");
            return;
        }

        int prevAmount = fuelSlot.getAmount();
        String fuelName = fuelSlot.getType().name();

        if (prevAmount <= 1) {
            // Last item - clear the slot
            fuelSlot = null;
            debug("CONSUMED FUEL: " + fuelName + " | 1 -> 0 (slot now empty)");
        } else {
            // Reduce by 1
            fuelSlot.setAmount(prevAmount - 1);
            debug("CONSUMED FUEL: " + fuelName + " | " + prevAmount + " -> " + (prevAmount - 1));
        }

        fuelConsumedCount++;
        markDirty();
    }

    private void tryStartBurning(long now, FuelConfig fuelConfig) {
        if (!hasFuelInSlot()) {
            debug("tryStartBurning: No fuel in slot");
            return;
        }

        if (fuelConfig == null) {
            debug("tryStartBurning: FuelConfig is null");
            return;
        }

        var fuelDataOpt = fuelConfig.getFuelData(fuelSlot);
        if (fuelDataOpt.isEmpty()) {
            debug("tryStartBurning: " + fuelSlot.getType() + " is not valid fuel");
            return;
        }

        var fuelData = fuelDataOpt.get();

        // Calculate burn duration in MILLISECONDS
        // burnTimeTicks is in game ticks (20 ticks = 1 second)
        long burnTicks = fuelData.burnTimeTicks();
        burnDurationMs = burnTicks * 50; // 1 tick = 50ms

        burnStartTime = now;
        burnEndTime = now + burnDurationMs;

        fuelMaxTemperature = fuelData.temperatureBoost();
        fuelBaseTemperature = (int) (fuelMaxTemperature * FUEL_BASE_HEAT_PERCENTAGE);
        burning = true;
        currentFuelType = fuelSlot.getType().name();

        debug("=== STARTED BURNING ===");
        debug("  Fuel: " + currentFuelType + " x" + fuelSlot.getAmount());
        debug("  Duration: " + (burnDurationMs / 1000) + " seconds (" + burnTicks + " ticks)");
        debug("  Will expire at: " + burnEndTime);
        debug("  Base temp: " + fuelBaseTemperature + "°C, Max: " + fuelMaxTemperature + "°C");

        lastDebugLog = now;
        markDirty();
    }

    private boolean hasFuelInSlot() {
        return fuelSlot != null && !fuelSlot.getType().isAir() && fuelSlot.getAmount() > 0;
    }

    // ==================== BELLOWS ====================

    private void updateBellowsDecay(long currentTime) {
        if (bellowsBoost <= 0) {
            bellowsBoost = 0;
            return;
        }

        double decayRate = type.getBellowsDecayRate();
        long timeSinceBellows = currentTime - lastBellowsTime;

        if (timeSinceBellows > 1500) decayRate *= 1.5;
        if (timeSinceBellows > 3000) decayRate *= 2.0;
        if (timeSinceBellows > 5000) decayRate *= 2.5;

        if (!burning) {
            decayRate *= 5.0;
        }

        int decay = Math.max(2, (int) (bellowsBoost * decayRate));
        bellowsBoost = Math.max(0, bellowsBoost - decay);
    }

    public boolean applyBellows(int temperatureBoost) {
        if (!burning) {
            debug("Bellows REJECTED: not burning");
            return false;
        }

        if (!hasFuelInSlot()) {
            debug("Bellows REJECTED: no fuel in slot");
            stopBurning();
            return false;
        }

        long now = System.currentTimeMillis();
        if (now >= burnEndTime) {
            debug("Bellows REJECTED: burn time expired");
            return false;
        }

        int maxBellowsBoost = fuelMaxTemperature - fuelBaseTemperature;
        maxBellowsBoost = Math.min(maxBellowsBoost, (int) (type.getMaxTemperature() * 0.5));

        int effectiveBoost = temperatureBoost;
        double currentRatio = maxBellowsBoost > 0 ? (double) bellowsBoost / maxBellowsBoost : 0;

        if (currentRatio > 0.8) {
            effectiveBoost = (int) (temperatureBoost * 0.3);
        } else if (currentRatio > 0.5) {
            effectiveBoost = (int) (temperatureBoost * 0.6);
        }

        bellowsBoost = Math.min(bellowsBoost + effectiveBoost, maxBellowsBoost);
        lastBellowsTime = now;

        double instantPercent = type.getBellowsInstantBoost();
        int instantBoost = (int) (effectiveBoost * instantPercent);
        currentTemperature = Math.min(currentTemperature + instantBoost, type.getMaxTemperature());

        debug("Bellows applied: +" + effectiveBoost + " boost, total=" + bellowsBoost + ", temp=" + currentTemperature);

        markDirty();
        return true;
    }

    public boolean canUseBellows() {
        return burning && hasFuelInSlot() && System.currentTimeMillis() < burnEndTime;
    }

    // ==================== TEMPERATURE ====================

    private void updateTemperature() {
        if (!burning && bellowsBoost <= 0) {
            if (currentTemperature > 0) {
                int coolRate = Math.max(5, (int) (type.getCoolingRate() * 2.0));
                currentTemperature = Math.max(0, currentTemperature - coolRate);
            }
            return;
        }

        if (!burning && bellowsBoost > 0) {
            int coolRate = Math.max(3, (int) (type.getCoolingRate() * 1.5));
            currentTemperature = Math.max(0, currentTemperature - coolRate);
            return;
        }

        int targetTemp = calculateTargetTemperature();

        if (currentTemperature < targetTemp) {
            double heatingMult = type.getHeatingMultiplier();
            int heatRate = Math.max(1, (int) (type.getTemperatureChange() * heatingMult));
            int diff = targetTemp - currentTemperature;
            if (diff > 150) heatRate = (int) (heatRate * 1.3);
            currentTemperature = Math.min(currentTemperature + heatRate, targetTemp);
        } else if (currentTemperature > targetTemp) {
            double coolingMult = type.getCoolingMultiplier();
            int coolRate = Math.max(2, (int) (type.getCoolingRate() * coolingMult));
            currentTemperature = Math.max(targetTemp, currentTemperature - coolRate);
        }

        currentTemperature = clampTemperature(currentTemperature);
    }

    private int calculateTargetTemperature() {
        if (!burning) return 0;
        int target = fuelBaseTemperature + bellowsBoost;
        return Math.min(target, type.getMaxTemperature());
    }

    private int clampTemperature(int temp) {
        return Math.max(0, Math.min(temp, type.getMaxTemperature()));
    }

    // ==================== SMELTING ====================

    private void findMatchingRecipe(ItemProviderRegistry registry) {
        currentRecipe = type.findMatchingRecipe(inputSlots, registry);
        if (currentRecipe != null) {
            smeltTimeTotal = currentRecipe.getSmeltTimeMs();
            smeltProgress = 0;
            timeOutsideIdealRange = 0;
            timeInsideIdealRange = 0;
            reachedIdealDuringSmelting = false;
            debug("Found matching recipe: " + currentRecipe.getId());
            markDirty();
        }
    }

    private void processSmelting(long elapsedMs, ItemProviderRegistry registry) {
        if (!currentRecipe.matchesInputs(inputSlots, registry)) {
            resetSmelting();
            return;
        }

        int minTemp = currentRecipe.getMinTemperature();
        if (currentTemperature < minTemp) return;

        boolean isIdeal = currentRecipe.isIdealTemperature(currentTemperature);

        if (isIdeal) {
            timeInsideIdealRange += elapsedMs;
            timeOutsideIdealRange = Math.max(0, timeOutsideIdealRange - (elapsedMs / 2));
            reachedIdealDuringSmelting = true;
            smeltProgress += elapsedMs;
        } else {
            timeOutsideIdealRange += elapsedMs;
            double efficiency = calculateSmeltingEfficiency();
            smeltProgress += (long) (elapsedMs * efficiency);
        }

        if (smeltProgress >= smeltTimeTotal) {
            boolean goodOutput = shouldProduceGoodOutput();
            completeSmelting(goodOutput, registry);
        }
    }

    private double calculateSmeltingEfficiency() {
        if (currentRecipe == null) return 0.5;
        int minIdeal = currentRecipe.getMinIdealTemperature();
        int maxIdeal = currentRecipe.getMaxIdealTemperature();

        if (currentTemperature < minIdeal) {
            double ratio = (double) currentTemperature / minIdeal;
            return Math.max(0.3, ratio * 0.7);
        } else if (currentTemperature > maxIdeal) {
            return 0.75;
        }
        return 1.0;
    }

    private boolean shouldProduceGoodOutput() {
        if (!reachedIdealDuringSmelting) return false;
        if (timeOutsideIdealRange >= type.getBadOutputThresholdMs()) return false;

        long totalTime = timeInsideIdealRange + timeOutsideIdealRange;
        if (totalTime > 0) {
            double idealRatio = (double) timeInsideIdealRange / totalTime;
            if (idealRatio < type.getMinIdealRatio()) return false;
        }
        return true;
    }

    public void completeSmelting(boolean success, ItemProviderRegistry registry) {
        if (currentRecipe == null) return;

        debug("Completing smelting: success=" + success);
        consumeInputs(registry);

        List<RecipeOutput> outputs = success ? currentRecipe.getOutputs() : currentRecipe.getBadOutputs();
        if (outputs == null || outputs.isEmpty()) outputs = currentRecipe.getOutputs();

        giveOutputs(outputs, registry);
        resetSmelting();
    }

    private void consumeInputs(ItemProviderRegistry registry) {
        if (currentRecipe == null) return;

        for (RecipeInput input : currentRecipe.getInputs()) {
            int remaining = input.amount();
            String inputType = input.type().toLowerCase();
            String inputId = input.id();

            for (int i = 0; i < inputSlots.length && remaining > 0; i++) {
                ItemStack slot = inputSlots[i];
                if (slot == null || slot.getType().isAir()) continue;

                if (matchesInput(slot, inputType, inputId, registry)) {
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
        markDirty();
    }

    private boolean matchesInput(ItemStack item, String inputType, String inputId, ItemProviderRegistry registry) {
        if (item == null || item.getType().isAir()) return false;

        if (inputType.equals("minecraft")) {
            String slotMaterial = item.getType().name().toUpperCase();
            String targetMaterial = inputId.toUpperCase().replace("-", "_").replace(" ", "_");
            if (targetMaterial.startsWith("MINECRAFT:")) targetMaterial = targetMaterial.substring(10);
            return slotMaterial.equals(targetMaterial);
        } else {
            return registry.matches(item, inputType, inputId);
        }
    }

    private void giveOutputs(List<RecipeOutput> outputs, ItemProviderRegistry registry) {
        if (outputs == null || outputs.isEmpty()) return;

        for (RecipeOutput output : outputs) {
            if (output.amount() <= 0) continue;
            ItemStack item = registry.getItem(output.type(), output.id(), output.amount());
            if (item == null) continue;

            if (outputSlot == null || outputSlot.getType().isAir()) {
                outputSlot = item.clone();
            } else if (outputSlot.isSimilar(item)) {
                int newAmount = Math.min(outputSlot.getAmount() + item.getAmount(), outputSlot.getMaxStackSize());
                outputSlot.setAmount(newAmount);
            }
        }
        markDirty();
    }

    public void resetSmelting() {
        currentRecipe = null;
        smeltProgress = 0;
        smeltTimeTotal = 0;
        timeOutsideIdealRange = 0;
        timeInsideIdealRange = 0;
        reachedIdealDuringSmelting = false;
        markDirty();
    }

    // ==================== STATE ====================

    public void markDirty() { this.dirty = true; }
    public boolean isDirty() { return dirty; }
    public void clearDirty() { this.dirty = false; }

    // ==================== GETTERS ====================

    public UUID getId() { return id; }
    public FurnaceType getType() { return type; }
    public Location getLocation() { return location.clone(); }
    public int getCurrentTemperature() { return currentTemperature; }
    public int getBellowsBoost() { return bellowsBoost; }
    public int getFuelConsumedCount() { return fuelConsumedCount; }

    public boolean isBurning() {
        return burning && hasFuelInSlot() && System.currentTimeMillis() < burnEndTime;
    }

    public long getBurnTimeRemaining() {
        if (!burning) return 0;
        long remaining = burnEndTime - System.currentTimeMillis();
        return Math.max(0, remaining / 50); // Convert ms to ticks
    }

    public long getMaxBurnTime() {
        return burnDurationMs / 50; // Convert ms to ticks
    }

    public int getFuelBaseTemperature() { return fuelBaseTemperature; }
    public int getFuelMaxTemperature() { return fuelMaxTemperature; }
    public FurnaceRecipe getCurrentRecipe() { return currentRecipe; }
    public long getTimeOutsideIdealRange() { return timeOutsideIdealRange; }
    public long getTimeInsideIdealRange() { return timeInsideIdealRange; }
    public ItemStack[] getInputSlots() { return inputSlots; }
    public ItemStack getFuelSlot() { return fuelSlot; }
    public ItemStack getOutputSlot() { return outputSlot; }
    public long getSmeltProgressMs() { return smeltProgress; }
    public long getSmeltTimeTotal() { return smeltTimeTotal; }

    public int getTargetTemperature() { return calculateTargetTemperature(); }

    public double getSmeltProgress() {
        if (smeltTimeTotal <= 0) return 0.0;
        return Math.min(1.0, (double) smeltProgress / smeltTimeTotal);
    }

    public double getBurnProgress() {
        if (burnDurationMs <= 0 || !burning) return 0.0;
        long elapsed = System.currentTimeMillis() - burnStartTime;
        return Math.min(1.0, (double) elapsed / burnDurationMs);
    }

    public boolean isInIdealTemperature() {
        return currentRecipe != null && currentRecipe.isIdealTemperature(currentTemperature);
    }

    public String getIdealTemperatureRange() {
        if (currentRecipe == null) return null;
        return currentRecipe.getMinIdealTemperature() + "-" + currentRecipe.getMaxIdealTemperature() + "°C";
    }

    public int getRecipeMinIdealTemp() {
        return currentRecipe != null ? currentRecipe.getMinIdealTemperature() : 0;
    }

    public int getRecipeMaxIdealTemp() {
        return currentRecipe != null ? currentRecipe.getMaxIdealTemperature() : type.getMaxTemperature();
    }

    public String getRecipeTemperatureStatus() {
        if (currentTemperature <= 0) return "COLD";
        if (currentRecipe == null) {
            if (currentTemperature < type.getMaxTemperature() / 3) return "LOW";
            else if (currentTemperature < type.getMaxTemperature() * 2 / 3) return "WARMING";
            else return "HOT";
        }

        int minIdeal = currentRecipe.getMinIdealTemperature();
        int maxIdeal = currentRecipe.getMaxIdealTemperature();

        if (currentTemperature < minIdeal) {
            return (double) currentTemperature / minIdeal >= 0.7 ? "WARMING" : "LOW";
        } else if (currentTemperature > maxIdeal) {
            int overTemp = currentTemperature - maxIdeal;
            int maxOver = type.getMaxTemperature() - maxIdeal;
            return (maxOver > 0 && (double) overTemp / maxOver >= 0.6) ? "DANGEROUS" : "HIGH";
        }
        return "IDEAL";
    }

    public int getTemperatureStatusCode() {
        if (currentTemperature <= 0) return 0;
        int minIdeal = currentRecipe != null ? currentRecipe.getMinIdealTemperature() : type.getMaxTemperature() / 3;
        int maxIdeal = currentRecipe != null ? currentRecipe.getMaxIdealTemperature() : type.getMaxTemperature() * 2 / 3;

        if (currentTemperature < minIdeal) {
            return (double) currentTemperature / minIdeal >= 0.7 ? 2 : 1;
        } else if (currentTemperature > maxIdeal) {
            int overTemp = currentTemperature - maxIdeal;
            int maxOver = type.getMaxTemperature() - maxIdeal;
            return (maxOver > 0 && (double) overTemp / maxOver >= 0.6) ? 5 : 4;
        }
        return 3;
    }

    // ==================== SETTERS ====================

    public void setCurrentTemperature(int temp) {
        this.currentTemperature = clampTemperature(temp);
    }

    public void setTargetTemperature(int temp) {
        this.currentTemperature = clampTemperature(temp);
        markDirty();
    }

    public void addExternalHeat(int amount) {
        this.currentTemperature = clampTemperature(this.currentTemperature + amount);
        markDirty();
    }

    public void setInputSlots(ItemStack[] slots) {
        this.inputSlots = new ItemStack[DEFAULT_INPUT_SLOTS];
        if (slots != null) {
            for (int i = 0; i < Math.min(slots.length, DEFAULT_INPUT_SLOTS); i++) {
                this.inputSlots[i] = slots[i] != null ? slots[i].clone() : null;
            }
        }
        markDirty();
    }

    public void setFuelSlot(ItemStack fuel) {
        ItemStack oldFuel = this.fuelSlot;
        this.fuelSlot = fuel != null ? fuel.clone() : null;

        // Debug fuel slot changes
        if (DEBUG) {
            String oldStr = oldFuel != null ? oldFuel.getType() + " x" + oldFuel.getAmount() : "null";
            String newStr = this.fuelSlot != null ? this.fuelSlot.getType() + " x" + this.fuelSlot.getAmount() : "null";
            if (!oldStr.equals(newStr)) {
                debug("Fuel slot changed: " + oldStr + " -> " + newStr);
            }
        }

        // Stop burning if fuel removed
        if (burning && !hasFuelInSlot()) {
            debug("setFuelSlot: Fuel removed while burning - STOPPING");
            stopBurning();
        }

        markDirty();
    }

    public void setOutputSlot(ItemStack output) {
        this.outputSlot = output != null ? output.clone() : null;
        markDirty();
    }
}