package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Handles asynchronous smelting calculations for furnaces.
 * Moves heavy computation (recipe matching, temperature calculations) off the main thread
 * while keeping Bukkit API calls (inventory modifications) on the main thread.
 */
public class AsyncSmeltingProcessor {

    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final Map<UUID, SmeltingTask> pendingTasks;
    private final BlockingQueue<SmeltingResult> resultQueue;
    private BukkitRunnable resultProcessor;

    private static final int MAX_RESULTS_PER_TICK = 50;
    private static final long BAD_OUTPUT_THRESHOLD_MS = 5000;

    public AsyncSmeltingProcessor(JavaPlugin plugin, int threadPoolSize) {
        this.plugin = plugin;
        this.executor = Executors.newFixedThreadPool(threadPoolSize, r -> {
            Thread t = new Thread(r, "SMCBlacksmith-Smelting-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1); // Slightly lower priority
            return t;
        });
        this.running = new AtomicBoolean(true);
        this.pendingTasks = new ConcurrentHashMap<>();
        this.resultQueue = new LinkedBlockingQueue<>();

        startResultProcessor();
    }

    /**
     * Process furnace tick calculations asynchronously.
     * The callback will be invoked on the main thread with results.
     */
    public void processAsync(FurnaceInstance furnace, ItemProviderRegistry registry,
                             FuelConfig fuelConfig, Consumer<SmeltingResult> callback) {

        if (!running.get()) return;

        UUID furnaceId = furnace.getId();

        // Skip if already processing
        if (pendingTasks.containsKey(furnaceId)) {
            return;
        }

        // Capture current state snapshot (thread-safe read on main thread)
        SmeltingSnapshot snapshot = captureSnapshot(furnace);

        SmeltingTask task = new SmeltingTask(furnaceId, snapshot, callback);
        pendingTasks.put(furnaceId, task);

        // Submit async task
        executor.submit(() -> {
            try {
                SmeltingResult result = processSmeltingLogic(snapshot, registry, fuelConfig);
                result.setCallback(callback);
                resultQueue.offer(result);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Async smelting error for furnace " + furnaceId, e);
                // Still remove from pending to allow retry
            } finally {
                pendingTasks.remove(furnaceId);
            }
        });
    }

    /**
     * Captures an immutable snapshot of furnace state for async processing.
     * This method runs on the main thread and is thread-safe.
     */
    private SmeltingSnapshot captureSnapshot(FurnaceInstance furnace) {
        return new SmeltingSnapshot(
                furnace.getId(),
                furnace.getType(),
                furnace.getCurrentTemperature(),
                furnace.getTargetTemperature(),
                furnace.isBurning(),
                furnace.getBurnTimeRemaining(),
                furnace.getSmeltProgressMs(),
                furnace.getSmeltTimeTotal(),
                furnace.getCurrentRecipe(),
                furnace.getTimeOutsideIdealRange(),
                copyItemStacks(furnace.getInputSlots()),
                furnace.getFuelSlot() != null ? furnace.getFuelSlot().clone() : null,
                furnace.getOutputSlot() != null ? furnace.getOutputSlot().clone() : null,
                System.currentTimeMillis()
        );
    }

    /**
     * Creates a deep copy of item stacks for thread-safe processing.
     */
    private ItemStack[] copyItemStacks(ItemStack[] source) {
        if (source == null) return new ItemStack[9];
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] != null ? source[i].clone() : null;
        }
        return copy;
    }

    /**
     * Core smelting logic - runs on worker thread.
     * No Bukkit API calls that require main thread are allowed here.
     */
    private SmeltingResult processSmeltingLogic(SmeltingSnapshot snapshot,
                                                ItemProviderRegistry registry,
                                                FuelConfig fuelConfig) {
        SmeltingResult result = new SmeltingResult(snapshot.furnaceId);
        result.setTimestamp(snapshot.timestamp);

        // Calculate burn state first (affects temperature)
        BurnCalculation burnCalc = calculateBurning(snapshot, fuelConfig, registry);
        result.setBurnCalculation(burnCalc);

        // Calculate new temperature based on burn state
        int effectiveTarget = burnCalc.newTargetTemperature;
        int newTemp = calculateTemperature(snapshot, effectiveTarget);
        result.setNewTemperature(newTemp);

        // Find matching recipe if none active
        FurnaceRecipe matchedRecipe = snapshot.currentRecipe;
        if (matchedRecipe == null) {
            matchedRecipe = findMatchingRecipe(snapshot, registry);
            if (matchedRecipe != null) {
                result.setNewRecipe(matchedRecipe);
            }
        }

        // Process smelting progress if we have a recipe
        if (matchedRecipe != null) {
            SmeltingProgress progress = calculateSmeltingProgress(
                    snapshot, matchedRecipe, newTemp, registry);
            result.setProgress(progress);
        }

        return result;
    }

    /**
     * Calculates temperature changes.
     */
    private int calculateTemperature(SmeltingSnapshot snapshot, int effectiveTarget) {
        int current = snapshot.currentTemperature;
        int change = snapshot.type.getTemperatureChange();
        int max = snapshot.type.getMaxTemperature();

        int newTemp;
        if (current < effectiveTarget) {
            newTemp = Math.min(current + change, effectiveTarget);
        } else if (current > effectiveTarget) {
            newTemp = Math.max(current - change, effectiveTarget);
        } else {
            newTemp = current;
        }

        return Math.max(0, Math.min(newTemp, max));
    }

    /**
     * Calculates burn state and fuel consumption.
     */
    private BurnCalculation calculateBurning(SmeltingSnapshot snapshot,
                                             FuelConfig fuelConfig,
                                             ItemProviderRegistry registry) {
        BurnCalculation calc = new BurnCalculation();

        if (snapshot.burnTimeRemaining > 0) {
            // Still burning from previous fuel
            calc.newBurnTimeRemaining = snapshot.burnTimeRemaining - 1;
            calc.isBurning = true;
            calc.newTargetTemperature = snapshot.targetTemperature;
            calc.consumeFuel = false;
        } else {
            // Need to check for new fuel
            calc.isBurning = false;
            calc.consumeFuel = false;

            if (snapshot.fuelSlot != null && !snapshot.fuelSlot.getType().isAir()) {
                FuelConfig.FuelData fuelData = fuelConfig.getFuelData(snapshot.fuelSlot);
                if (fuelData != null) {
                    // Found valid fuel
                    calc.consumeFuel = true;
                    calc.newBurnTimeRemaining = fuelData.burnTimeTicks();
                    int maxFuelTemp = snapshot.type.getMaxFuelTemperature();
                    calc.newTargetTemperature = Math.min(fuelData.temperatureBoost(), maxFuelTemp);
                    calc.isBurning = true;
                } else {
                    // No valid fuel data
                    calc.newTargetTemperature = 0;
                    calc.newBurnTimeRemaining = 0;
                }
            } else {
                // No fuel in slot
                calc.newTargetTemperature = 0;
                calc.newBurnTimeRemaining = 0;
            }
        }

        return calc;
    }

    /**
     * Finds a matching recipe for the current inputs.
     */
    private FurnaceRecipe findMatchingRecipe(SmeltingSnapshot snapshot, ItemProviderRegistry registry) {
        return snapshot.type.findMatchingRecipe(snapshot.inputSlots, registry);
    }

    /**
     * Calculates smelting progress and completion state.
     */
    private SmeltingProgress calculateSmeltingProgress(SmeltingSnapshot snapshot,
                                                       FurnaceRecipe recipe,
                                                       int currentTemp,
                                                       ItemProviderRegistry registry) {
        SmeltingProgress progress = new SmeltingProgress();

        // Verify inputs still match
        if (!recipe.matchesInputs(snapshot.inputSlots, registry)) {
            progress.shouldReset = true;
            return progress;
        }

        // Calculate elapsed time since last tick
        // Note: We use a fixed tick interval estimation for consistency
        long tickIntervalMs = 50; // Approximate 1 tick = 50ms at 20 TPS

        boolean isIdeal = snapshot.type.isIdealTemperature(currentTemp);

        if (isIdeal) {
            // Temperature is in ideal range - progress smelting
            progress.newSmeltProgress = snapshot.smeltProgress + tickIntervalMs;
            progress.newTimeOutsideIdealRange = 0;

            if (progress.newSmeltProgress >= recipe.getSmeltTimeMs()) {
                progress.isComplete = true;
                progress.isSuccess = true;
            }
        } else {
            // Temperature outside ideal range
            progress.newSmeltProgress = snapshot.smeltProgress; // No progress
            progress.newTimeOutsideIdealRange = snapshot.timeOutsideIdealRange + tickIntervalMs;

            if (progress.newTimeOutsideIdealRange >= BAD_OUTPUT_THRESHOLD_MS) {
                progress.isComplete = true;
                progress.isSuccess = false; // Bad output
            }
        }

        return progress;
    }

    /**
     * Starts the result processor that runs on the main thread.
     */
    private void startResultProcessor() {
        resultProcessor = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running.get()) {
                    cancel();
                    return;
                }

                SmeltingResult result;
                int processed = 0;

                // Process up to MAX_RESULTS_PER_TICK results per tick to avoid lag
                while (processed < MAX_RESULTS_PER_TICK && (result = resultQueue.poll()) != null) {
                    if (result.getCallback() != null) {
                        try {
                            result.getCallback().accept(result);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING,
                                    "Error processing smelting result for furnace " + result.getFurnaceId(), e);
                        }
                    }
                    processed++;
                }
            }
        };
        resultProcessor.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Shuts down the async processor.
     */
    public void shutdown() {
        running.set(false);

        if (resultProcessor != null) {
            resultProcessor.cancel();
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        pendingTasks.clear();
        resultQueue.clear();
    }

    /**
     * Checks if there's a pending async task for the given furnace.
     */
    public boolean hasPendingTask(UUID furnaceId) {
        return pendingTasks.containsKey(furnaceId);
    }

    /**
     * Gets the number of pending tasks.
     */
    public int getPendingTaskCount() {
        return pendingTasks.size();
    }

    /**
     * Gets the number of results waiting to be processed.
     */
    public int getQueuedResultCount() {
        return resultQueue.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    // ==================== INNER CLASSES ====================

    /**
     * Immutable snapshot of furnace state for async processing.
     */
    public static class SmeltingSnapshot {
        public final UUID furnaceId;
        public final FurnaceType type;
        public final int currentTemperature;
        public final int targetTemperature;
        public final boolean burning;
        public final long burnTimeRemaining;
        public final long smeltProgress;
        public final long smeltTimeTotal;
        public final FurnaceRecipe currentRecipe;
        public final long timeOutsideIdealRange;
        public final ItemStack[] inputSlots;
        public final ItemStack fuelSlot;
        public final ItemStack outputSlot;
        public final long timestamp;

        public SmeltingSnapshot(UUID furnaceId, FurnaceType type, int currentTemperature,
                                int targetTemperature, boolean burning, long burnTimeRemaining,
                                long smeltProgress, long smeltTimeTotal, FurnaceRecipe currentRecipe,
                                long timeOutsideIdealRange, ItemStack[] inputSlots,
                                ItemStack fuelSlot, ItemStack outputSlot, long timestamp) {
            this.furnaceId = furnaceId;
            this.type = type;
            this.currentTemperature = currentTemperature;
            this.targetTemperature = targetTemperature;
            this.burning = burning;
            this.burnTimeRemaining = burnTimeRemaining;
            this.smeltProgress = smeltProgress;
            this.smeltTimeTotal = smeltTimeTotal;
            this.currentRecipe = currentRecipe;
            this.timeOutsideIdealRange = timeOutsideIdealRange;
            this.inputSlots = inputSlots;
            this.fuelSlot = fuelSlot;
            this.outputSlot = outputSlot;
            this.timestamp = timestamp;
        }
    }

    /**
     * Result of async smelting calculation.
     */
    public static class SmeltingResult {
        private final UUID furnaceId;
        private long timestamp;
        private int newTemperature;
        private BurnCalculation burnCalculation;
        private FurnaceRecipe newRecipe;
        private SmeltingProgress progress;
        private Consumer<SmeltingResult> callback;

        public SmeltingResult(UUID furnaceId) {
            this.furnaceId = furnaceId;
        }

        public UUID getFurnaceId() { return furnaceId; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public int getNewTemperature() { return newTemperature; }
        public void setNewTemperature(int temp) { this.newTemperature = temp; }
        public BurnCalculation getBurnCalculation() { return burnCalculation; }
        public void setBurnCalculation(BurnCalculation calc) { this.burnCalculation = calc; }
        public FurnaceRecipe getNewRecipe() { return newRecipe; }
        public void setNewRecipe(FurnaceRecipe recipe) { this.newRecipe = recipe; }
        public SmeltingProgress getProgress() { return progress; }
        public void setProgress(SmeltingProgress progress) { this.progress = progress; }
        public Consumer<SmeltingResult> getCallback() { return callback; }
        public void setCallback(Consumer<SmeltingResult> callback) { this.callback = callback; }
    }

    /**
     * Burn state calculation results.
     */
    public static class BurnCalculation {
        public boolean isBurning;
        public long newBurnTimeRemaining;
        public int newTargetTemperature;
        public boolean consumeFuel;
    }

    /**
     * Smelting progress calculation results.
     */
    public static class SmeltingProgress {
        public boolean shouldReset;
        public long newSmeltProgress;
        public long newTimeOutsideIdealRange;
        public boolean isComplete;
        public boolean isSuccess;
    }

    /**
     * Internal task tracking class.
     */
    private static class SmeltingTask {
        final UUID furnaceId;
        final SmeltingSnapshot snapshot;
        final Consumer<SmeltingResult> callback;

        SmeltingTask(UUID furnaceId, SmeltingSnapshot snapshot, Consumer<SmeltingResult> callback) {
            this.furnaceId = furnaceId;
            this.snapshot = snapshot;
            this.callback = callback;
        }
    }
}