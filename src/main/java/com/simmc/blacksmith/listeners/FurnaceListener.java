package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.furnace.FurnaceGUI;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class FurnaceListener implements Listener {

    private final FurnaceManager furnaceManager;

    public FurnaceListener(FurnaceManager furnaceManager) {
        this.furnaceManager = furnaceManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof FurnaceGUI gui)) return;

        int slot = event.getRawSlot();

        if (slot < 0 || slot >= inv.getSize()) {
            return;
        }

        if (gui.isBellowsSlot(slot)) {
            event.setCancelled(true);
            FurnaceInstance furnace = gui.getFurnace();
            furnace.applyBellows(furnace.getType().getTemperatureChange() * 2);

            // Use configurable message
            MessageConfig messages = SMCBlacksmith.getInstance().getConfigManager().getMessageConfig();
            player.sendMessage(messages.getBellowsUsed());
            return;
        }

        if (!gui.isInteractableSlot(slot)) {
            event.setCancelled(true);
            return;
        }

        if (gui.isOutputSlot(slot)) {
            // Allow taking from output
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof FurnaceGUI gui)) return;

        for (int slot : event.getRawSlots()) {
            if (slot < inv.getSize() && !gui.isInteractableSlot(slot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof FurnaceGUI)) return;

        furnaceManager.closeGUI(player);
    }
}