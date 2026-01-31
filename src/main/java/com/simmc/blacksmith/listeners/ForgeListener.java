package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.ForgeMinigameGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class ForgeListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof ForgeMinigameGUI gui)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (gui.isHammerSlot(slot)) {
            forgeManager.processStrike(player);
            return;
        }

        if (gui.isExitSlot(slot)) {
            forgeManager.cancelSession(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Inventory inv = event.getInventory();
        if (inv.getHolder() instanceof ForgeMinigameGUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof ForgeMinigameGUI)) return;

        if (forgeManager.hasActiveSession(player.getUniqueId())) {
            forgeManager.cancelSession(player.getUniqueId());
        }
    }
}