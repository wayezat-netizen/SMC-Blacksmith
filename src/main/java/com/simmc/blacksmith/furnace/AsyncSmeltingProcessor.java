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
 * Offloads heavy computation while keeping Bukkit API calls on main thread.
 */
public class AsyncSmeltingProcessor {

    private static final int MAX_RESULTS_PER_TICK = 50;
    private static final long BAD_OUTPUT_THRESHOLD_MS = 5000;
    private static final long TICK_INTERVAL_MS = 50;

    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final AtomicBoolean running;
    private final Map<UUID, SmeltingTask> pendingTasks;
    private final BlockingQueue<SmeltingResult> resultQueue;
    private BukkitRunnable resultProcessor;

    public AsyncSmeltingProcessor(JavaPlugin plugin, int threadPoolSize) {
        this.plugin = plugin;
        this.executor = createExecutor(threadPoolSize);
        this.running = new AtomicBoolean(true);
        this.pendingTasks = new ConcurrentHashMap<>();
        this.resultQueue = new LinkedBlockingQueue<>();

        startResultProcessor();
    }

    private ExecutorService createExecutor(int poolSize) {
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "SMCBlacksmith-Smelting-Worker");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    // ==================== PUBLIC API ====================

    /**
     * Process furnace tick calculations asynchronously.
     */
    public void processAsync(FurnaceInstance furnace, ItemProviderRegistry registry,
                             FuelConfig fuelConfig, Consumer<SmeltingResult> callback) {
        if (!running.get()) return;

        UUID furnaceId = furnace.getId();
        if (pendingTasks.containsKey(furnaceId)) return;

        SmeltingSnapshot snapshot = captureSnapshot(furnace);
        pendingTasks.put(furnaceId, new SmeltingTask(furnaceId, snapshot, callback));

        executor.submit(() -> processTask(furnaceId, snapshot, registry, fuelConfig, callback));
    }

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

    // ==================== TASK PROCESSING ====================

    private void processTask(UUID furnaceId, SmeltingSnapshot snapshot,
                             ItemProviderRegistry registry, FuelConfig fuelConfig,
                             Consumer<SmeltingResult> callback) {
        try {
            SmeltingResult result = processSmeltingLogic(snapshot, registry, fuelConfig);
            result.setCallback(callback);
            resultQueue.offer(result);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Async smelting error for furnace " + furnaceId, e);
        } finally {
            pendingTasks.remove(furnaceId);
        }
    }

    private SmeltingResult processSmeltingLogic(SmeltingSnapshot snapshot,
                                                ItemProviderRegistry registry,
                                                FuelConfig fuelConfig) {
        SmeltingResult result = new SmeltingResult(snapshot.furnaceId());
        result.setTimestamp(snapshot.timestamp());

        // Calculate burn state
        BurnCalculation burnCalc = calculateBurning(snapshot, fuelConfig);
        result.setBurnCalculation(burnCalc);

        // Calculate temperature
        int newTemp = calculateTemperature(snapshot, burnCalc.newTargetTemperature);
        result.setNewTemperature(newTemp);

        // Find/verify recipe
        FurnaceRecipe recipe = snapshot.currentRecipe();
        if (recipe == null) {
            recipe = findMatchingRecipe(snapshot, registry);
            if (recipe != null) {
                result.setNewRecipe(recipe);
            }
        }

        // Calculate smelting progress
        if (recipe != null) {
            SmeltingProgress progress = calculateSmeltingProgress(snapshot, recipe, newTemp, registry);
            result.setProgress(progress);
        }

        return result;
    }

    // ==================== CALCULATIONS ====================

    private int calculateTemperature(SmeltingSnapshot snapshot, int targetTemp) {
        int current = snapshot.currentTemperature();
        int change = snapshot.type().getTemperatureChange();
        int max = snapshot.type().getMaxTemperature();

        int newTemp;
        if (current < targetTemp) {
            newTemp = Math.min(current + change, targetTemp);
        } else if (current > targetTemp) {
            newTemp = Math.max(current - change, targetTemp);
        } else {
            newTemp = current;
        }

        return clamp(newTemp, 0, max);
    }

    private BurnCalculation calculateBurning(SmeltingSnapshot snapshot, FuelConfig fuelConfig) {
        BurnCalculation calc = new BurnCalculation();

        if (snapshot.burnTimeRemaining() > 0) {
            calc.newBurnTimeRemaining = snapshot.burnTimeRemaining() - 1;
            calc.isBurning = true;
            calc.newTargetTemperature = snapshot.targetTemperature();
            calc.consumeFuel = false;
        } else {
            calc.isBurning = false;
            calc.consumeFuel = false;

            ItemStack fuel = snapshot.fuelSlot();
            if (fuel != null && !fuel.getType().isAir()) {
                fuelConfig.getFuelData(fuel).ifPresent(fuelData -> {
                    calc.consumeFuel = true;
                    calc.newBurnTimeRemaining = fuelData.burnTimeTicks();
                    int maxFuelTemp = snapshot.type().getMaxFuelTemperature();
                    calc.newTargetTemperature = Math.min(fuelData.temperatureBoost(), maxFuelTemp);
                    calc.isBurning = true;
                });
            }

            if (!calc.isBurning) {
                calc.newTargetTemperature = 0;
                calc.newBurnTimeRemaining = 0;
            }
        }

        return calc;
    }

    private FurnaceRecipe findMatchingRecipe(SmeltingSnapshot snapshot, ItemProviderRegistry registry) {
        return snapshot.type().findMatchingRecipe(snapshot.inputSlots(), registry);
    }

    private SmeltingProgress calculateSmeltingProgress(SmeltingSnapshot snapshot,
                                                       FurnaceRecipe recipe,
                                                       int currentTemp,
                                                       ItemProviderRegistry registry) {
        SmeltingProgress progress = new SmeltingProgress();

        if (!recipe.matchesInputs(snapshot.inputSlots(), registry)) {
            progress.shouldReset = true;
            return progress;
        }

        boolean isIdeal = snapshot.type().isIdealTemperature(currentTemp);

        if (isIdeal) {
            progress.newSmeltProgress = snapshot.smeltProgress() + TICK_INTERVAL_MS;
            progress.newTimeOutsideIdealRange = 0;

            if (progress.newSmeltProgress >= recipe.getSmeltTimeMs()) {
                progress.isComplete = true;
                progress.isSuccess = true;
            }
        } else {
            progress.newSmeltProgress = snapshot.smeltProgress();
            progress.newTimeOutsideIdealRange = snapshot.timeOutsideIdealRange() + TICK_INTERVAL_MS;

            if (progress.newTimeOutsideIdealRange >= BAD_OUTPUT_THRESHOLD_MS) {
                progress.isComplete = true;
                progress.isSuccess = false;
            }
        }

        return progress;
    }

    // ==================== SNAPSHOT ====================

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
                cloneItem(furnace.getFuelSlot()),
                cloneItem(furnace.getOutputSlot()),
                System.currentTimeMillis()
        );
    }

    private ItemStack[] copyItemStacks(ItemStack[] source) {
        if (source == null) return new ItemStack[9];
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = cloneItem(source[i]);
        }
        return copy;
    }

    private ItemStack cloneItem(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== RESULT PROCESSOR ====================

    private void startResultProcessor() {
        resultProcessor = new BukkitRunnable() {
            @Override
            public void run() {
                if (!running.get()) {
                    cancel();
                    return;
                }
                processResults();
            }
        };
        resultProcessor.runTaskTimer(plugin, 1L, 1L);
    }

    private void processResults() {
        SmeltingResult result;
        int processed = 0;

        while (processed < MAX_RESULTS_PER_TICK && (result = resultQueue.poll()) != null) {
            Consumer<SmeltingResult> callback = result.getCallback();
            if (callback != null) {
                try {
                    callback.accept(result);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Error processing smelting result for furnace " + result.getFurnaceId(), e);
                }
            }
            processed++;
        }
    }

    // ==================== STATUS ====================

    public boolean hasPendingTask(UUID furnaceId) {
        return pendingTasks.containsKey(furnaceId);
    }

    public int getPendingTaskCount() {
        return pendingTasks.size();
    }

    public int getQueuedResultCount() {
        return resultQueue.size();
    }

    public boolean isRunning() {
        return running.get();
    }

    // ==================== INNER TYPES ====================

    /**
     * Immutable snapshot of furnace state.
     */
    public record SmeltingSnapshot(
            UUID furnaceId,
            FurnaceType type,
            int currentTemperature,
            int targetTemperature,
            boolean burning,
            long burnTimeRemaining,
            long smeltProgress,
            long smeltTimeTotal,
            FurnaceRecipe currentRecipe,
            long timeOutsideIdealRange,
            ItemStack[] inputSlots,
            ItemStack fuelSlot,
            ItemStack outputSlot,
            long timestamp
    ) {}

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

        // Getters and setters
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

    private record SmeltingTask(UUID furnaceId, SmeltingSnapshot snapshot, Consumer<SmeltingResult> callback) {}
}