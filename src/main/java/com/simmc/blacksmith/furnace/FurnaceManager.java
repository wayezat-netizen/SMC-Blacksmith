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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages all custom furnace instances in the plugin.
 * Handles furnace creation, ticking, persistence, and GUI management.
 */
public class FurnaceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    // FIXED: Use ConcurrentHashMap for thread safety
    private final Map<Location, FurnaceInstance> furnaces;
    private final Map<UUID, FurnaceGUI> openGUIs;

    private BukkitTask tickTask;

    // FIXED: Cache FuelConfig to avoid setting registry every tick
    private FuelConfig cachedFuelConfig;

    public FurnaceManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.furnaces = new ConcurrentHashMap<>();
        this.openGUIs = new ConcurrentHashMap<>();
    }

    /**
     * Starts the furnace tick task.
     */
    public void startTickTask() {
        // Cache fuel config with registry
        cachedFuelConfig = configManager.getFuelConfig();
        cachedFuelConfig.setItemRegistry(itemRegistry);

        int tickRate = configManager.getFurnaceTickRate();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, tickRate, tickRate);
        plugin.getLogger().info("Furnace tick task started (rate: " + tickRate + " ticks)");
    }

    /**
     * Stops the furnace tick task.
     */
    public void stopTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
            plugin.getLogger().info("Furnace tick task stopped");
        }
    }

    /**
     * Main tick method - updates all furnaces and GUIs.
     */
    private void tick() {
        // FIXED: No longer setting itemRegistry every tick

        // Tick all furnaces with exception handling
        for (Map.Entry<Location, FurnaceInstance> entry : furnaces.entrySet()) {
            try {
                entry.getValue().tick(itemRegistry, cachedFuelConfig);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error ticking furnace at " + formatLocation(entry.getKey()), e);
            }
        }

        // Update all open GUIs
        for (FurnaceGUI gui : openGUIs.values()) {
            try {
                gui.updateDisplay();
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error updating furnace GUI", e);
            }
        }
    }

    /**
     * Creates a new furnace at the specified location.
     */
    public FurnaceInstance createFurnace(String typeId, Location location) {
        if (typeId == null || location == null) {
            plugin.getLogger().warning("createFurnace called with null typeId or location");
            return null;
        }

        FurnaceType type = configManager.getFurnaceConfig().getFurnaceType(typeId);
        if (type == null) {
            plugin.getLogger().warning("Unknown furnace type: " + typeId);
            return null;
        }

        Location key = normalizeLocation(location);
        if (key == null) {
            plugin.getLogger().warning("Cannot create furnace - location has null world");
            return null;
        }

        if (furnaces.containsKey(key)) {
            return furnaces.get(key);
        }

        FurnaceInstance instance = new FurnaceInstance(type, key);
        furnaces.put(key, instance);
        plugin.getLogger().fine("Created furnace '" + typeId + "' at " + formatLocation(key));
        return instance;
    }

    /**
     * Gets a furnace at the specified location.
     */
    public FurnaceInstance getFurnace(Location location) {
        if (location == null) return null;
        return furnaces.get(normalizeLocation(location));
    }

    /**
     * Removes a furnace from the specified location.
     */
    public void removeFurnace(Location location) {
        if (location == null) return;

        Location key = normalizeLocation(location);
        FurnaceInstance removed = furnaces.remove(key);
        if (removed != null) {
            plugin.getLogger().info("Removed furnace at " + formatLocation(key));
        }
    }

    /**
     * Checks if a location has a custom furnace.
     */
    public boolean isFurnace(Location location) {
        if (location == null) return false;
        return furnaces.containsKey(normalizeLocation(location));
    }

    /**
     * Opens the furnace GUI for a player.
     */
    public void openFurnaceGUI(Player player, Location location) {
        if (player == null || location == null) return;

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

    /**
     * Closes the furnace GUI for a player.
     */
    public void closeGUI(Player player) {
        if (player == null) return;

        FurnaceGUI gui = openGUIs.remove(player.getUniqueId());
        if (gui != null) {
            gui.saveItemsToFurnace();
        }
    }

    /**
     * Gets the open GUI for a player.
     */
    public FurnaceGUI getOpenGUI(UUID playerId) {
        return openGUIs.get(playerId);
    }

    /**
     * Checks if a player has an open furnace GUI.
     */
    public boolean hasOpenGUI(UUID playerId) {
        return openGUIs.containsKey(playerId);
    }

    /**
     * Saves all furnaces to disk.
     */
    public void saveAll() {
        File file = new File(plugin.getDataFolder(), "data/furnaces.yml");
        file.getParentFile().mkdirs();

        YamlConfiguration config = new YamlConfiguration();

        int count = 0;
        int skipped = 0;

        for (Map.Entry<Location, FurnaceInstance> entry : furnaces.entrySet()) {
            Location loc = entry.getKey();
            FurnaceInstance furnace = entry.getValue();

            // FIXED: Null world check
            if (loc.getWorld() == null) {
                plugin.getLogger().warning("Skipping furnace with null world during save");
                skipped++;
                continue;
            }

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
            plugin.getLogger().info("Saved " + count + " furnaces" +
                    (skipped > 0 ? " (skipped " + skipped + " with null worlds)" : ""));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save furnaces", e);
        }
    }

    /**
     * Loads all furnaces from disk.
     */
    public void loadAll() {
        File file = new File(plugin.getDataFolder(), "data/furnaces.yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        int loaded = 0;
        int failed = 0;

        ConfigurationSection furnacesSection = config.getConfigurationSection("furnaces");
        if (furnacesSection == null) return;

        for (String key : furnacesSection.getKeys(false)) {
            ConfigurationSection section = furnacesSection.getConfigurationSection(key);
            if (section == null) continue;

            try {
                String typeId = section.getString("type");
                String worldName = section.getString("world");
                int x = section.getInt("x");
                int y = section.getInt("y");
                int z = section.getInt("z");
                int temperature = section.getInt("temperature", 0);

                World world = plugin.getServer().getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("World '" + worldName + "' not found, skipping furnace");
                    failed++;
                    continue;
                }

                Location loc = new Location(world, x, y, z);
                FurnaceInstance furnace = createFurnace(typeId, loc);
                if (furnace != null) {
                    furnace.setCurrentTemperature(temperature);
                    loaded++;
                } else {
                    failed++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error loading furnace entry: " + key, e);
                failed++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " furnaces" +
                (failed > 0 ? " (" + failed + " failed)" : ""));
    }

    /**
     * Reloads the furnace manager configuration.
     */
    public void reload() {
        // Save all GUI items before reload
        for (FurnaceGUI gui : openGUIs.values()) {
            gui.saveItemsToFurnace();
        }

        // Update cached fuel config
        cachedFuelConfig = configManager.getFuelConfig();
        cachedFuelConfig.setItemRegistry(itemRegistry);
    }

    /**
     * Normalizes a location to block coordinates.
     */
    private Location normalizeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    /**
     * Formats a location for logging.
     */
    private String formatLocation(Location loc) {
        if (loc == null) return "null";
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "null";
        return worldName + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public int getFurnaceCount() {
        return furnaces.size();
    }

    public Map<Location, FurnaceInstance> getAllFurnaces() {
        return new ConcurrentHashMap<>(furnaces);
    }
}