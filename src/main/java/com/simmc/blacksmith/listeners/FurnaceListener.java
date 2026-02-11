package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceGUI;
import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles furnace GUI interactions.
 */
public class FurnaceListener implements Listener {

    private final FurnaceManager furnaceManager;

    public FurnaceListener(FurnaceManager furnaceManager) {
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // FIX: FurnaceManager.getOpenGUI() returns FurnaceGUI directly (not Optional)
        FurnaceGUI gui = furnaceManager.getOpenGUI(player.getUniqueId());
        if (gui == null) return;

        // Allow clicks in player's own inventory
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().equals(player.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        // Allow interaction with valid slots only
        if (!gui.isInteractableSlot(slot)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // FIX: FurnaceManager.getOpenGUI() returns FurnaceGUI directly (not Optional)
        FurnaceGUI gui = furnaceManager.getOpenGUI(player.getUniqueId());
        if (gui == null) return;

        // Cancel if any slot is non-interactable
        boolean hasInvalidSlot = event.getRawSlots().stream()
                .anyMatch(slot -> !gui.isInteractableSlot(slot));

        if (hasInvalidSlot) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        if (furnaceManager.hasOpenGUI(player.getUniqueId())) {
            furnaceManager.closeGUI(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        furnaceManager.handlePlayerQuit(event.getPlayer());
    }
}