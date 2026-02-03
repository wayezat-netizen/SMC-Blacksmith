package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Manages all furnace instances, GUIs, temperature bars, and async processing.
 * Includes performance optimizations: batch GUI updates and recipe caching.
 */
public class FurnaceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    // CHANGED: Use String keys instead of Location to avoid equality issues
    private final Map<String, FurnaceInstance> furnaces;
    private final Map<String, TemperatureBar> temperatureBars;
    private final Map<UUID, FurnaceGUI> openGUIs;

    private BukkitTask tickTask;
    private AsyncSmeltingProcessor asyncProcessor;
    private RecipeMatchCache recipeCache;

    // Configuration
    private boolean asyncEnabled = true;
    private static final int DEFAULT_THREAD_COUNT = 2;

    // Batch GUI update settings
    private static final int GUI_UPDATE_BATCH_SIZE = 10;
    private int guiUpdateOffset = 0;

    // Cache settings
    private static final long RECIPE_CACHE_EXPIRATION_MS = 5000; // 5 seconds

    public FurnaceManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.furnaces = new HashMap<>();
        this.temperatureBars = new HashMap<>();
        this.openGUIs = new HashMap<>();

        // Initialize async processor
        int threadCount = Math.max(1, Math.min(DEFAULT_THREAD_COUNT,
                Runtime.getRuntime().availableProcessors() / 4));
        this.asyncProcessor = new AsyncSmeltingProcessor(plugin, threadCount);

        // Initialize recipe cache
        this.recipeCache = new RecipeMatchCache(RECIPE_CACHE_EXPIRATION_MS);

        plugin.getLogger().info("Initialized async smelting processor with " + threadCount + " worker thread(s)");
    }

    public void startTickTask() {
        int tickRate = configManager.getFurnaceTickRate();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, tickRate, tickRate);
    }

    public void stopTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }

        // Shutdown async processor
        if (asyncProcessor != null) {
            asyncProcessor.shutdown();
            asyncProcessor = null;
        }

        // Clear recipe cache
        if (recipeCache != null) {
            recipeCache.clear();
        }

        // Remove all temperature bars
        removeAllTemperatureBars();
    }

    /**
     * Main tick method - processes all furnaces.
     */
    private void tick() {
        FuelConfig fuelConfig = configManager.getFuelConfig();
        fuelConfig.setItemRegistry(itemRegistry);

        if (asyncEnabled && asyncProcessor != null && asyncProcessor.isRunning()) {
            // Async processing mode
            tickAsync(fuelConfig);
        } else {
            // Synchronous fallback
            tickSync(fuelConfig);
        }

        // Batch GUI updates for performance
        tickGUIUpdatesBatched();

        // Temperature bar updates (always synchronous)
        tickTemperatureBars();
    }

    /**
     * Asynchronous tick - offloads calculations to worker threads.
     */
    private void tickAsync(FuelConfig fuelConfig) {
        for (FurnaceInstance furnace : furnaces.values()) {
            // Skip if async task still pending
            if (asyncProcessor.hasPendingTask(furnace.getId())) {
                continue;
            }

            // Process async with callback
            asyncProcessor.processAsync(furnace, itemRegistry, fuelConfig, result -> {
                applySmeltingResult(furnace, result);
            });
        }
    }

    /**
     * Synchronous tick - fallback when async is disabled.
     */
    private void tickSync(FuelConfig fuelConfig) {
        for (FurnaceInstance furnace : furnaces.values()) {
            furnace.tick(itemRegistry, fuelConfig);
        }
    }

    /**
     * Applies the result of async smelting calculations to the furnace.
     * This runs on the main thread.
     */
    private void applySmeltingResult(FurnaceInstance furnace,
                                     AsyncSmeltingProcessor.SmeltingResult result) {
        // Apply temperature
        furnace.setCurrentTemperature(result.getNewTemperature());

        // Apply burn state
        AsyncSmeltingProcessor.BurnCalculation burn = result.getBurnCalculation();
        if (burn != null) {
            furnace.setBurning(burn.isBurning);
            furnace.setBurnTimeRemaining(burn.newBurnTimeRemaining);
            furnace.setTargetTemperature(burn.newTargetTemperature);

            if (burn.consumeFuel) {
                // Consume fuel on main thread (modifies inventory)
                furnace.consumeFuelItem();
            }
        }

        // Apply recipe changes
        if (result.getNewRecipe() != null) {
            furnace.setCurrentRecipe(result.getNewRecipe());
            furnace.setSmeltProgress(0);
            furnace.setTimeOutsideIdealRange(0);

            // Invalidate cache for this furnace's inputs
            recipeCache.invalidateSpecific(furnace.getType().getId(), furnace.getInputSlots());
        }

        // Apply smelting progress
        AsyncSmeltingProcessor.SmeltingProgress progress = result.getProgress();
        if (progress != null) {
            if (progress.shouldReset) {
                furnace.resetSmelting();
            } else if (progress.isComplete) {
                // Complete smelting on main thread (modifies inventory)
                furnace.completeSmelting(progress.isSuccess, itemRegistry);

                // Invalidate cache after smelting completes
                recipeCache.invalidateSpecific(furnace.getType().getId(), furnace.getInputSlots());
            } else {
                furnace.setSmeltProgress(progress.newSmeltProgress);
                furnace.setTimeOutsideIdealRange(progress.newTimeOutsideIdealRange);
            }
        }
    }

    /**
     * Updates GUIs in batches to reduce per-tick overhead.
     * Updates GUI_UPDATE_BATCH_SIZE GUIs per tick in round-robin fashion.
     */
    private void tickGUIUpdatesBatched() {
        if (openGUIs.isEmpty()) return;

        List<FurnaceGUI> guiList = new ArrayList<>(openGUIs.values());
        int total = guiList.size();

        // If we have fewer GUIs than batch size, update all
        if (total <= GUI_UPDATE_BATCH_SIZE) {
            for (FurnaceGUI gui : guiList) {
                gui.updateDisplay();
            }
            return;
        }

        // Update a subset of GUIs each tick
        int start = guiUpdateOffset % total;
        int end = Math.min(start + GUI_UPDATE_BATCH_SIZE, total);

        for (int i = start; i < end; i++) {
            guiList.get(i).updateDisplay();
        }

        // Wrap around for next tick
        guiUpdateOffset = (end >= total) ? 0 : end;
    }

    /**
     * Updates all temperature bar displays.
     */
    private void tickTemperatureBars() {
        for (TemperatureBar bar : temperatureBars.values()) {
            bar.update();
        }
    }

    /**
     * Removes all temperature bars.
     */
    private void removeAllTemperatureBars() {
        for (TemperatureBar bar : temperatureBars.values()) {
            bar.remove();
        }
        temperatureBars.clear();
    }

    /**
     * Gets a cached recipe match for the given furnace and inputs.
     * Uses the recipe cache to avoid repeated matching.
     */
    public FurnaceRecipe getCachedRecipeMatch(FurnaceInstance furnace) {
        return recipeCache.getCachedMatch(
                furnace.getType().getId(),
                furnace.getInputSlots(),
                furnace.getType(),
                itemRegistry
        );
    }

    /**
     * Creates a furnace with a temperature bar display.
     */
    public FurnaceInstance createFurnace(String typeId, Location location) {
        FurnaceType type = configManager.getFurnaceConfig().getFurnaceType(typeId);
        if (type == null) {
            plugin.getLogger().warning("Unknown furnace type: " + typeId);
            return null;
        }

        String key = locationToKey(location);

        if (furnaces.containsKey(key)) {
            return furnaces.get(key);
        }

        Location normalizedLoc = normalizeLocation(location);
        FurnaceInstance instance = new FurnaceInstance(type, normalizedLoc);
        furnaces.put(key, instance);

        if (configManager.getMainConfig().isTemperatureBarEnabled()) {
            createTemperatureBar(instance, key);
        }

        return instance;
    }

    /**
     * Creates a temperature bar for the furnace.
     */
    private void createTemperatureBar(FurnaceInstance furnace, String key) {
        // Remove existing bar if any
        TemperatureBar existingBar = temperatureBars.remove(key);
        if (existingBar != null) {
            existingBar.remove();
        }

        // Create new bar
        TemperatureBar bar = new TemperatureBar(furnace);
        bar.spawn();
        temperatureBars.put(key, bar);
    }

    public FurnaceInstance getFurnace(Location location) {
        String key = locationToKey(location);
        return furnaces.get(key);
    }


    public void removeFurnace(Location location) {
        String key = locationToKey(location);

        FurnaceInstance removed = furnaces.remove(key);

        TemperatureBar bar = temperatureBars.remove(key);
        if (bar != null) {
            bar.remove();
        }

        if (removed != null) {
            plugin.getLogger().info("Removed furnace at " + key);
        }
    }


    public boolean isFurnace(Location location) {
        String key = locationToKey(location);
        return furnaces.containsKey(key);
    }

    public void openFurnaceGUI(Player player, Location location) {
        FurnaceInstance furnace = getFurnace(location);
        if (furnace == null) {
            player.sendMessage("§cNo furnace found at this location.");
            return;
        }

        closeGUI(player);

        FurnaceGUI gui = new FurnaceGUI(furnace, configManager.getMessageConfig(), itemRegistry);
        gui.open(player);
        openGUIs.put(player.getUniqueId(), gui);
    }

    public void closeGUI(Player player) {
        FurnaceGUI gui = openGUIs.remove(player.getUniqueId());
        if (gui != null) {
            gui.saveItemsToFurnace();
        }
    }

    public FurnaceGUI getOpenGUI(UUID playerId) {
        return openGUIs.get(playerId);
    }

    public boolean hasOpenGUI(UUID playerId) {
        return openGUIs.containsKey(playerId);
    }

    public void saveAll() {
        File file = new File(plugin.getDataFolder(), "data/furnaces.yml");
        file.getParentFile().mkdirs();

        YamlConfiguration config = new YamlConfiguration();

        int count = 0;
        for (Map.Entry<String, FurnaceInstance> entry : furnaces.entrySet()) {
            FurnaceInstance furnace = entry.getValue();
            Location loc = furnace.getLocation();

            String path = "furnaces." + count;
            config.set(path + ".type", furnace.getType().getId());
            config.set(path + ".world", loc.getWorld().getName());
            config.set(path + ".x", loc.getBlockX());
            config.set(path + ".y", loc.getBlockY());
            config.set(path + ".z", loc.getBlockZ());
            config.set(path + ".temperature", furnace.getCurrentTemperature());

            count++;
        }

        config.set("count", count);

        try {
            config.save(file);
            plugin.getLogger().info("Saved " + count + " furnaces.");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save furnaces", e);
        }
    }

    public void loadAll() {
        File file = new File(plugin.getDataFolder(), "data/furnaces.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;

        ConfigurationSection furnacesSection = config.getConfigurationSection("furnaces");
        if (furnacesSection == null) return;

        for (String key : furnacesSection.getKeys(false)) {
            ConfigurationSection section = furnacesSection.getConfigurationSection(key);
            if (section == null) continue;

            String typeId = section.getString("type");
            String worldName = section.getString("world");
            int x = section.getInt("x");
            int y = section.getInt("y");
            int z = section.getInt("z");
            int temperature = section.getInt("temperature", 0);

            World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;

            Location loc = new Location(world, x, y, z);
            FurnaceInstance furnace = createFurnace(typeId, loc);
            if (furnace != null) {
                furnace.setCurrentTemperature(temperature);
                loaded++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " furnaces.");
    }

    public void reload() {
        for (FurnaceGUI gui : openGUIs.values()) {
            gui.saveItemsToFurnace();
        }

        // Clear caches on reload
        if (recipeCache != null) {
            recipeCache.clear();
        }
    }

    /**
     * Converts a Location to a unique String key.
     * This avoids issues with Location's equals/hashCode methods.
     */
    private String locationToKey(Location location) {
        if (location == null || location.getWorld() == null) {
            return "null";
        }
        return location.getWorld().getName() + ":" +
                location.getBlockX() + ":" +
                location.getBlockY() + ":" +
                location.getBlockZ();
    }

    /**
     * Normalizes a location to block coordinates.
     */
    private Location normalizeLocation(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    // ==================== GETTERS ====================

    public int getFurnaceCount() {
        return furnaces.size();
    }

    public int getTemperatureBarCount() {
        return temperatureBars.size();
    }

    public int getOpenGUICount() {
        return openGUIs.size();
    }

    public Map<String, FurnaceInstance> getAllFurnaces() {
        return new HashMap<>(furnaces);
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean enabled) {
        this.asyncEnabled = enabled;
    }

    public RecipeMatchCache getRecipeCache() {
        return recipeCache;
    }

    /**
     * Gets performance statistics for debugging.
     */
    public String getPerformanceStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== FurnaceManager Stats ===\n");
        sb.append("Furnaces: ").append(furnaces.size()).append("\n");
        sb.append("Temperature Bars: ").append(temperatureBars.size()).append("\n");
        sb.append("Open GUIs: ").append(openGUIs.size()).append("\n");
        sb.append("Async Enabled: ").append(asyncEnabled).append("\n");

        if (asyncProcessor != null) {
            sb.append("Async Pending: ").append(asyncProcessor.getPendingTaskCount()).append("\n");
            sb.append("Async Queued: ").append(asyncProcessor.getQueuedResultCount()).append("\n");
        }

        if (recipeCache != null) {
            sb.append("Recipe Cache: ").append(recipeCache.getStats()).append("\n");
        }

        return sb.toString();
    }
}