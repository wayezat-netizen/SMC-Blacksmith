package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Handles furnace removal when blocks are broken.
 */
public class FurnaceBlockListener implements Listener {

    private final FurnaceManager furnaceManager;

    private static final Set<Material> FURNACE_BLOCKS = EnumSet.of(
            Material.BARRIER,  // CraftEngine furniture often uses barriers
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER
    );

    public FurnaceBlockListener(FurnaceManager furnaceManager) {
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Location location = block.getLocation();

        // Check if this location has a registered furnace
        if (furnaceManager.isFurnace(location)) {
            furnaceManager.removeFurnace(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            Location location = block.getLocation();
            if (furnaceManager.isFurnace(location)) {
                furnaceManager.removeFurnace(location);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            Location location = block.getLocation();
            if (furnaceManager.isFurnace(location)) {
                furnaceManager.removeFurnace(location);
            }
        }
    }
}