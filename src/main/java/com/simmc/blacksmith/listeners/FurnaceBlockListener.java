package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Handles furnace removal when blocks are broken or exploded.
 */
public class FurnaceBlockListener implements Listener {

    private final FurnaceManager furnaceManager;

    public FurnaceBlockListener(FurnaceManager furnaceManager) {
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        handleBlockRemoval(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleBlocksRemoval(event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleBlocksRemoval(event.blockList());
    }

    private void handleBlockRemoval(Location location) {
        if (furnaceManager.isFurnace(location)) {
            furnaceManager.removeFurnace(location);
        }
    }

    private void handleBlocksRemoval(List<Block> blocks) {
        for (Block block : blocks) {
            handleBlockRemoval(block.getLocation());
        }
    }
}