package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class FurnaceManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;
    private FuelConfig cachedFuelConfig;

    private final Map<Location, FurnaceInstance> furnaces;
    private final Map<UUID, FurnaceGUI> openGUIs;

    private BukkitTask tickTask;

    public FurnaceManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.furnaces = new ConcurrentHashMap<>();
        this.openGUIs = new ConcurrentHashMap<>();
    }

    public void startTickTask() {
        cachedFuelConfig = configManager.getFuelConfig();
        cachedFuelConfig.setItemRegistry(itemRegistry);

        int tickRate = configManager.getFurnaceTickRate();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, tickRate, tickRate);
    }

    public void stopTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        for (FurnaceInstance furnace : furnaces.values()) {
            furnace.tick(itemRegistry, cachedFuelConfig);
        }

        for (FurnaceGUI gui : openGUIs.values()) {
            gui.updateDisplay();
        }
    }

    public FurnaceInstance createFurnace(String typeId, Location location) {
        FurnaceType type = configManager.getFurnaceConfig().getFurnaceType(typeId);
        if (type == null) {
            plugin.getLogger().warning("Unknown furnace type: " + typeId);
            return null;
        }

        Location key = normalizeLocation(location);
        if (furnaces.containsKey(key)) {
            return furnaces.get(key);
        }

        FurnaceInstance instance = new FurnaceInstance(type, key);
        furnaces.put(key, instance);
        return instance;
    }

    public FurnaceInstance getFurnace(Location location) {
        return furnaces.get(normalizeLocation(location));
    }

    public void removeFurnace(Location location) {
        Location key = normalizeLocation(location);
        FurnaceInstance removed = furnaces.remove(key);
        if (removed != null) {
            plugin.getLogger().info("Removed furnace at " + formatLocation(key));
        }
    }

    public boolean isFurnace(Location location) {
        return furnaces.containsKey(normalizeLocation(location));
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
        for (Map.Entry<Location, FurnaceInstance> entry : furnaces.entrySet()) {
            Location loc = entry.getKey();
            FurnaceInstance furnace = entry.getValue();

            if (loc.getWorld() == null) {
                plugin.getLogger().warning("Skipping furnace with null world at location");
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
        int count = config.getInt("count", 0);
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

            org.bukkit.World world = plugin.getServer().getWorld(worldName);
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
        cachedFuelConfig = configManager.getFuelConfig();
        cachedFuelConfig.setItemRegistry(itemRegistry);

        for (FurnaceGUI gui : openGUIs.values()) {
            gui.saveItemsToFurnace();
        }
    }

    private Location normalizeLocation(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        );
    }

    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public int getFurnaceCount() {
        return furnaces.size();
    }

    public Map<Location, FurnaceInstance> getAllFurnaces() {
        return new HashMap<>(furnaces);
    }
}