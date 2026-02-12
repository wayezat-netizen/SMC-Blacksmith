package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.furnace.FurnaceGUI;
import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles furnace GUI interactions with input restrictions.
 */
public class FurnaceListener implements Listener {

    private final FurnaceManager furnaceManager;

    public FurnaceListener(FurnaceManager furnaceManager) {
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        FurnaceGUI gui = furnaceManager.getOpenGUI(player.getUniqueId());
        if (gui == null) return;

        // Allow clicks in player's own inventory
        if (event.getClickedInventory() != null &&
                event.getClickedInventory().equals(player.getInventory())) {

            // But check shift-click into furnace
            if (event.isShiftClick()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked != null && !clicked.getType().isAir()) {
                    // Check if this would go into an input slot
                    if (gui.hasInputRestrictions() && !gui.isAllowedInput(clicked)) {
                        event.setCancelled(true);
                        sendInputRestrictionMessage(player, gui);
                        return;
                    }
                }
            }
            return;
        }

        int slot = event.getRawSlot();

        // Allow interaction with valid slots only
        if (!gui.isInteractableSlot(slot)) {
            event.setCancelled(true);
            return;
        }

        // If clicking fuel slot, validate fuel type
        if (gui.isFuelSlot(slot)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!gui.isValidFuel(cursor)) {
                    event.setCancelled(true);
                    player.sendMessage("§cThis item cannot be used as fuel!");
                    return;
                }
            }
        }

        // If clicking input slot, validate allowed inputs
        if (gui.isInputSlot(slot)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                if (!gui.isAllowedInput(cursor)) {
                    event.setCancelled(true);
                    sendInputRestrictionMessage(player, gui);
                    return;
                }
            }
        }

        // If clicking output slot, only allow taking items (not placing)
        if (gui.isOutputSlot(slot)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) {
                event.setCancelled(true);
                return;
            }
        }

        // Schedule save after click is processed
        Bukkit.getScheduler().runTaskLater(furnaceManager.getPlugin(), () -> {
            FurnaceGUI currentGui = furnaceManager.getOpenGUI(player.getUniqueId());
            if (currentGui != null) {
                currentGui.saveItemsToFurnace();
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        FurnaceGUI gui = furnaceManager.getOpenGUI(player.getUniqueId());
        if (gui == null) return;

        // Check all dragged slots
        for (int slot : event.getRawSlots()) {
            // Cancel if non-interactable
            if (!gui.isInteractableSlot(slot)) {
                event.setCancelled(true);
                return;
            }

            // Cancel if dragging into input slot with restricted item
            if (gui.isInputSlot(slot)) {
                ItemStack newItem = event.getNewItems().get(slot);
                if (newItem != null && !newItem.getType().isAir()) {
                    if (!gui.isAllowedInput(newItem)) {
                        event.setCancelled(true);
                        sendInputRestrictionMessage(player, gui);
                        return;
                    }
                }
            }

            // Cancel if dragging into fuel slot with non-fuel
            if (gui.isFuelSlot(slot)) {
                ItemStack newItem = event.getNewItems().get(slot);
                if (newItem != null && !newItem.getType().isAir()) {
                    if (!gui.isValidFuel(newItem)) {
                        event.setCancelled(true);
                        player.sendMessage("§cThis item cannot be used as fuel!");
                        return;
                    }
                }
            }

            // Cancel if dragging into output slot
            if (gui.isOutputSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }

        // Schedule save after drag is processed
        Bukkit.getScheduler().runTaskLater(furnaceManager.getPlugin(), () -> {
            FurnaceGUI currentGui = furnaceManager.getOpenGUI(player.getUniqueId());
            if (currentGui != null) {
                currentGui.saveItemsToFurnace();
            }
        }, 1L);
    }

    /**
     * Sends a message about input restrictions.
     */
    private void sendInputRestrictionMessage(Player player, FurnaceGUI gui) {
        player.sendMessage("§cThis item is not allowed in this furnace!");

        List<String> allowed = gui.getAllowedInputDescriptions();
        if (!allowed.isEmpty()) {
            player.sendMessage("§7Allowed items:");
            for (String desc : allowed) {
                player.sendMessage("§7 - §f" + desc);
            }
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