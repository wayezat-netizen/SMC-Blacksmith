package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceGUI;
import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class FurnaceListener implements Listener {

    private final FurnaceManager furnaceManager;

    public FurnaceListener(FurnaceManager furnaceManager) {
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        FurnaceGUI gui = furnaceManager.getOpenGUI(playerId);

        if (gui == null) return;

        Inventory clickedInv = event.getClickedInventory();

        // Allow clicks in player inventory
        if (clickedInv != null && clickedInv.equals(player.getInventory())) {
            return;
        }

        int slot = event.getRawSlot();

        // Allow interaction with valid slots
        if (gui.isInteractableSlot(slot)) {
            return;
        }

        // Handle bellows click
        if (gui.isBellowsSlot(slot)) {
            event.setCancelled(true);
            handleBellowsClick(player, gui);
            return;
        }

        // Block all other slots
        event.setCancelled(true);
    }

    private void handleBellowsClick(Player player, FurnaceGUI gui) {
        gui.getFurnace().applyBellows(25);
        player.sendMessage("§6Bellows pumped! Temperature rising...");
        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.8f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        FurnaceGUI gui = furnaceManager.getOpenGUI(playerId);

        if (gui == null) return;

        for (int slot : event.getRawSlots()) {
            if (!gui.isInteractableSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();

        if (furnaceManager.hasOpenGUI(playerId)) {
            furnaceManager.closeGUI(player);
        }
    }
}