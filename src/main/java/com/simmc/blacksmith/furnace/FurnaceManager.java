package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.listeners.FurnaceListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all furnace instances, GUIs, and temperature displays.
 * Boss bar shows recipe-specific ideal temperature ranges.
 */
public class FurnaceManager {

    private static final double LOOK_DISTANCE = 5.0;
    private static final String DATA_FILE = "data/furnaces.yml";

    // Batch size for processing furnaces
    private static final int FURNACE_BATCH_SIZE = 50;

    // Temperature status display data
    private static final String[] STATUS_DISPLAY = {
            "§8❄ Cold",
            "§9⬇ Too Cold",
            "§e↑ Warming Up",
            "§a✓ Ideal",
            "§6⬆ Too Hot",
            "§c⚠ Dangerous!"
    };

    private static final String[] STATUS_COLOR = {
            "§8", "§9", "§e", "§a", "§6", "§c"
    };

    private static final BarColor[] STATUS_BAR_COLOR = {
            BarColor.WHITE,
            BarColor.BLUE,
            BarColor.YELLOW,
            BarColor.GREEN,
            BarColor.YELLOW,
            BarColor.RED
    };

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    // ConcurrentHashMap for thread-safe access
    private final Map<String, FurnaceInstance> furnaces;
    private final Map<UUID, FurnaceGUI> openGUIs;
    private final Map<UUID, BossBar> playerBossBars;
    private final Map<UUID, String> playerLookingAt;

    // GUI refresh tasks
    private final Map<UUID, BukkitTask> guiRefreshTasks;

    // Cache fuel config to avoid repeated lookups
    private FuelConfig cachedFuelConfig;
    private long lastFuelConfigUpdate;

    private BukkitTask tickTask;
    private BukkitTask displayTask;

    // Reference to listener for refresh task management
    private FurnaceListener furnaceListener;

    public FurnaceManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;

        this.furnaces = new ConcurrentHashMap<>();
        this.openGUIs = new ConcurrentHashMap<>();
        this.playerBossBars = new ConcurrentHashMap<>();
        this.playerLookingAt = new ConcurrentHashMap<>();
        this.guiRefreshTasks = new ConcurrentHashMap<>();
    }

    // ==================== LIFECYCLE ====================

    public void startTickTask() {
        int tickRate = configManager.getFurnaceTickRate();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, tickRate, tickRate);
        displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateDisplays, 5L, 5L);
    }

    public void stopTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) tickTask.cancel();
        if (displayTask != null && !displayTask.isCancelled()) displayTask.cancel();
        tickTask = null;
        displayTask = null;

        // Stop all GUI refresh tasks
        guiRefreshTasks.values().forEach(task -> {
            if (task != null && !task.isCancelled()) task.cancel();
        });
        guiRefreshTasks.clear();

        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();
        playerLookingAt.clear();
    }

    /**
     * Sets the furnace listener reference for refresh task management.
     */
    public void setFurnaceListener(FurnaceListener listener) {
        this.furnaceListener = listener;
    }

    // ==================== TICK ====================

    /**
     * Main tick method - processes all furnaces.
     */
    private void tick() {
        // Cache fuel config (refresh every 5 seconds)
        long now = System.currentTimeMillis();
        if (cachedFuelConfig == null || (now - lastFuelConfigUpdate) > 5000) {
            cachedFuelConfig = configManager.getFuelConfig();
            if (cachedFuelConfig != null) {
                cachedFuelConfig.setItemRegistry(itemRegistry);
            }
            lastFuelConfigUpdate = now;
        }

        // Early exit if no furnaces
        if (furnaces.isEmpty()) return;

        // Process all furnaces
        for (FurnaceInstance furnace : furnaces.values()) {
            try {
                furnace.tick(itemRegistry, cachedFuelConfig);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error ticking furnace at " + furnace.getLocation(), e);
            }
        }
    }

    // ==================== DISPLAY ====================

    // Track display update cycle for batch processing
    private int displayUpdateIndex = 0;
    private static final int PLAYERS_PER_TICK = 50;

    /**
     * Updates boss bar displays for players in batches.
     * Processes players per tick to spread load across server ticks.
     */
    private void updateDisplays() {
        // Early exit if no furnaces exist
        if (furnaces.isEmpty()) {
            playerBossBars.values().forEach(bar -> bar.setVisible(false));
            return;
        }

        // Get snapshot of online players
        Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        int totalPlayers = players.length;

        if (totalPlayers == 0) return;

        // For small player counts, process all at once
        if (totalPlayers <= PLAYERS_PER_TICK) {
            for (Player player : players) {
                updatePlayerDisplay(player);
            }
            return;
        }

        // Batch processing for large player counts
        int startIndex = displayUpdateIndex % totalPlayers;
        int endIndex = Math.min(startIndex + PLAYERS_PER_TICK, totalPlayers);

        for (int i = startIndex; i < endIndex; i++) {
            updatePlayerDisplay(players[i]);
        }

        // Wrap around if needed
        if (endIndex >= totalPlayers) {
            displayUpdateIndex = 0;
        } else {
            displayUpdateIndex = endIndex;
        }
    }

    private void updatePlayerDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        FurnaceInstance lookingAt = getFurnacePlayerIsLookingAt(player);

        if (lookingAt != null) {
            playerLookingAt.put(playerId, locationToKey(lookingAt.getLocation()));

            BossBar bar = playerBossBars.computeIfAbsent(playerId, k -> {
                BossBar newBar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SEGMENTED_10);
                newBar.addPlayer(player);
                return newBar;
            });

            updateBossBar(bar, lookingAt);
            bar.setVisible(true);
        } else {
            playerLookingAt.remove(playerId);
            BossBar bar = playerBossBars.get(playerId);
            if (bar != null) {
                bar.setVisible(false);
            }
        }
    }

    /**
     * Updates the boss bar with furnace status.
     * Shows recipe-specific ideal temperature range when smelting.
     */
    private void updateBossBar(BossBar bar, FurnaceInstance furnace) {
        FurnaceType type = furnace.getType();
        int currentTemp = furnace.getCurrentTemperature();
        int maxTemp = type.getMaxTemperature();

        int statusCode = furnace.getTemperatureStatusCode();

        StringBuilder title = new StringBuilder();

        // Status display (shows if temp is LOW/IDEAL/HIGH relative to recipe)
        title.append(STATUS_DISPLAY[statusCode]);

        // Current temperature
        title.append(" §7| ");
        title.append(STATUS_COLOR[statusCode]).append(currentTemp).append("°C");

        // Fuel indicator with burn time
        if (furnace.isBurning()) {
            long burnRemaining = furnace.getBurnTimeRemaining();
            int seconds = (int) (burnRemaining / 20);
            title.append(" §6🔥 §7(").append(seconds).append("s)");
        } else {
            title.append(" §8🔥 §c(No Fuel!)");
        }

        // Recipe-specific display
        FurnaceRecipe recipe = furnace.getCurrentRecipe();
        double progress;

        if (recipe != null) {
            // Show recipe's ideal temperature range
            String idealRange = furnace.getIdealTemperatureRange();
            title.append(" §7| §fIdeal: §e").append(idealRange);

            // Show smelting progress with temperature status
            progress = furnace.getSmeltProgress();
            int percent = (int) (progress * 100);

            title.append(" §7| ");

            String tempStatus = furnace.getRecipeTemperatureStatus();
            switch (tempStatus) {
                case "LOW", "COLD" -> {
                    title.append("§9⬇ Too Cold! ");
                    title.append("§7").append(percent).append("%");
                }
                case "HIGH" -> {
                    title.append("§6⬆ Too Hot! ");
                    title.append("§7").append(percent).append("%");
                }
                case "DANGEROUS" -> {
                    title.append("§c⚠ Overheating! ");
                    title.append("§c").append(percent).append("%");
                }
                case "IDEAL" -> {
                    title.append("§a✓ Smelting: ");
                    title.append("§a").append(percent).append("%");
                }
                default -> {
                    title.append("§7Smelting: ").append(percent).append("%");
                }
            }

            // Warning indicator if outside ideal range for too long
            long timeOutside = furnace.getTimeOutsideIdealRange();
            long threshold = type.getBadOutputThresholdMs();

            if (timeOutside > threshold * 0.5) {
                title.append(" §4⚠");
            } else if (timeOutside > threshold * 0.25) {
                title.append(" §c⚠");
            }

            // Show quality indicator
            long timeInside = furnace.getTimeInsideIdealRange();
            long totalTime = timeInside + timeOutside;
            if (totalTime > 1000) {
                double ratio = (double) timeInside / totalTime;
                if (ratio >= 0.8) {
                    title.append(" §a★");
                } else if (ratio >= 0.6) {
                    title.append(" §e★");
                } else if (ratio >= type.getMinIdealRatio()) {
                    title.append(" §7★");
                } else {
                    title.append(" §c✗");
                }
            }

        } else {
            // No recipe - show temperature progress toward max
            progress = maxTemp > 0 ? (double) currentTemp / maxTemp : 0;

            if (currentTemp > 0) {
                title.append(" §7| §8No recipe loaded");
            } else {
                title.append(" §7| §8Add fuel and ingredients");
            }
        }

        bar.setTitle(title.toString());
        bar.setProgress(Math.max(0, Math.min(1, progress)));
        bar.setColor(STATUS_BAR_COLOR[statusCode]);
    }

    private FurnaceInstance getFurnacePlayerIsLookingAt(Player player) {
        RayTraceResult result = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                LOOK_DISTANCE
        );

        if (result == null || result.getHitBlock() == null) {
            return null;
        }

        return getFurnace(result.getHitBlock().getLocation());
    }

    // ==================== FURNACE CRUD ====================

    public FurnaceInstance createFurnace(String typeId, Location location) {
        Optional<FurnaceType> typeOpt = configManager.getFurnaceConfig().getFurnaceType(typeId);

        if (typeOpt.isEmpty()) {
            plugin.getLogger().warning("Unknown furnace type: " + typeId);
            return null;
        }

        String key = locationToKey(location);
        if (furnaces.containsKey(key)) {
            return furnaces.get(key);
        }

        FurnaceInstance instance = new FurnaceInstance(typeOpt.get(), normalizeLocation(location));
        furnaces.put(key, instance);
        return instance;
    }

    public FurnaceInstance getFurnace(Location location) {
        return furnaces.get(locationToKey(location));
    }

    /**
     * Gets a furnace instance directly.
     * Alias for getFurnace() for clearer API.
     */
    public FurnaceInstance getFurnaceInstance(Location location) {
        return getFurnace(location);
    }

    public void removeFurnace(Location location) {
        furnaces.remove(locationToKey(location));
    }

    public boolean isFurnace(Location location) {
        return furnaces.containsKey(locationToKey(location));
    }

    // ==================== GUI ====================

    /**
     * Opens the furnace GUI for a player.
     * Starts a refresh task to sync fuel consumption.
     */
    public void openFurnaceGUI(Player player, Location location) {
        FurnaceInstance furnace = getFurnace(location);
        if (furnace == null) {
            player.sendMessage("§cNo furnace found at this location.");
            return;
        }

        closeGUI(player);

        // Create GUI with fuel config for validation
        FuelConfig fuelConfig = configManager.getFuelConfig();
        FurnaceGUI gui = new FurnaceGUI(furnace, configManager.getMessageConfig(), itemRegistry, fuelConfig);
        gui.open(player);
        openGUIs.put(player.getUniqueId(), gui);

        // Start refresh task to sync fuel consumption
        startGUIRefreshTask(player.getUniqueId());
    }

    /**
     * Closes the furnace GUI for a player.
     * Saves items and stops refresh task.
     */
    public void closeGUI(Player player) {
        UUID playerId = player.getUniqueId();

        // Stop refresh task
        stopGUIRefreshTask(playerId);

        // Save and remove GUI
        FurnaceGUI gui = openGUIs.remove(playerId);
        if (gui != null) {
            gui.saveItemsToFurnace();
        }
    }

    /**
     * Starts a task to refresh GUI fuel display while open.
     */
    private void startGUIRefreshTask(UUID playerId) {
        stopGUIRefreshTask(playerId); // Cancel existing

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            FurnaceGUI gui = openGUIs.get(playerId);
            if (gui == null) {
                stopGUIRefreshTask(playerId);
                return;
            }

            // Refresh fuel slot display to show consumption
            gui.refreshFuelSlot();

        }, 20L, 20L); // Every 1 second

        guiRefreshTasks.put(playerId, task);
    }

    /**
     * Stops the GUI refresh task for a player.
     */
    private void stopGUIRefreshTask(UUID playerId) {
        BukkitTask task = guiRefreshTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public FurnaceGUI getOpenGUI(UUID playerId) {
        return openGUIs.get(playerId);
    }

    public boolean hasOpenGUI(UUID playerId) {
        return openGUIs.containsKey(playerId);
    }

    // ==================== BELLOWS ====================

    /**
     * @deprecated Use FurnaceInstance.applyBellows() directly for better control.
     * This method is kept for backward compatibility.
     */
    @Deprecated
    public boolean applyBellows(Player player, Location furnaceLocation, int heatBoost) {
        FurnaceInstance furnace = getFurnace(furnaceLocation);
        if (furnace == null) {
            return false;
        }

        boolean success = furnace.applyBellows(heatBoost);

        if (!success) {
            player.sendMessage(configManager.getMessageConfig().getBellowsNoFuel());
            return false;
        }

        return true;
    }

    // ==================== PLAYER EVENTS ====================

    public void handlePlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();

        // Stop refresh task
        stopGUIRefreshTask(playerId);

        closeGUI(player);

        BossBar bar = playerBossBars.remove(playerId);
        if (bar != null) {
            bar.removeAll();
        }
        playerLookingAt.remove(playerId);
    }

    // ==================== PERSISTENCE ====================

    public void saveAll() {
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        file.getParentFile().mkdirs();

        YamlConfiguration config = new YamlConfiguration();
        int count = 0;

        for (FurnaceInstance furnace : furnaces.values()) {
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
        File file = new File(plugin.getDataFolder(), DATA_FILE);
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("furnaces");
        if (section == null) return;

        int loaded = 0;
        for (String key : section.getKeys(false)) {
            ConfigurationSection fs = section.getConfigurationSection(key);
            if (fs == null) continue;

            String typeId = fs.getString("type");
            String worldName = fs.getString("world");
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) continue;

            Location loc = new Location(world, fs.getInt("x"), fs.getInt("y"), fs.getInt("z"));
            FurnaceInstance furnace = createFurnace(typeId, loc);

            if (furnace != null) {
                furnace.setCurrentTemperature(fs.getInt("temperature", 0));
                loaded++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " furnaces.");
    }

    public void reload() {
        openGUIs.values().forEach(FurnaceGUI::saveItemsToFurnace);
    }

    // ==================== UTILITIES ====================

    private String locationToKey(Location location) {
        if (location == null || location.getWorld() == null) return "null";
        return String.format("%s:%d:%d:%d",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    private Location normalizeLocation(Location location) {
        return new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    // ==================== GETTERS ====================

    public JavaPlugin getPlugin() { return plugin; }
    public int getFurnaceCount() { return furnaces.size(); }
    public int getOpenGUICount() { return openGUIs.size(); }
    public Map<String, FurnaceInstance> getAllFurnaces() { return new HashMap<>(furnaces); }
    public ConfigManager getConfigManager() { return configManager; }
    public ItemProviderRegistry getItemRegistry() { return itemRegistry; }
}