package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Handles world-related events to ensure furnace data is saved
 * when worlds are unloaded.
 */
public class WorldListener implements Listener {

    private final JavaPlugin plugin;
    private final FurnaceManager furnaceManager;

    public WorldListener(JavaPlugin plugin, FurnaceManager furnaceManager) {
        this.plugin = plugin;
        this.furnaceManager = furnaceManager;
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();

        // Count furnaces in this world
        Map<Location, ?> allFurnaces = furnaceManager.getAllFurnaces();
        int count = 0;
        for (Location loc : allFurnaces.keySet()) {
            if (loc.getWorld() != null && loc.getWorld().equals(world)) {
                count++;
            }
        }

        if (count > 0) {
            plugin.getLogger().info("World " + world.getName() + " unloading with " + count + " furnaces. Saving...");
            furnaceManager.saveAll();
        }
    }
}