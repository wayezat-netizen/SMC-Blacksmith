package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.logging.Level;

public class WorldListener implements Listener {

    private final JavaPlugin plugin;
    private final FurnaceManager furnaceManager;

    public WorldListener(JavaPlugin plugin, FurnaceManager furnaceManager) {
        this.plugin = plugin;
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();

        try {
            // FIXED: Use the actual return type from your FurnaceManager
            Map<String, FurnaceInstance> allFurnaces = furnaceManager.getAllFurnaces();
            int count = 0;

            for (FurnaceInstance furnace : allFurnaces.values()) {
                Location loc = furnace.getLocation();
                if (loc.getWorld() != null && loc.getWorld().equals(world)) {
                    count++;
                }
            }

            if (count > 0) {
                plugin.getLogger().info("World '" + world.getName() + "' unloading with " + count + " furnaces. Saving...");
                furnaceManager.saveAll();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error handling world unload for " + world.getName(), e);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        try {
            furnaceManager.saveAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error saving furnaces during world save", e);
        }
    }
}