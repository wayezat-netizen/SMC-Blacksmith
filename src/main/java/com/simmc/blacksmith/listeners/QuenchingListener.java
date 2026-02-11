package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.quench.QuenchingGUI;
import com.simmc.blacksmith.quench.QuenchingManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Handles quenching GUI interactions and chat input for naming.
 */
public class QuenchingListener implements Listener {

    private final QuenchingManager quenchingManager;

    public QuenchingListener(QuenchingManager quenchingManager) {
        this.quenchingManager = quenchingManager;
    }

    // ==================== GUI INTERACTION ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof QuenchingGUI gui)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (gui.isRenameSlot(slot)) {
            quenchingManager.handleRenameClick(player);
        } else if (gui.isSkipSlot(slot)) {
            quenchingManager.handleSkipClick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof QuenchingGUI)) return;

        quenchingManager.handleGUIClose(player);
    }

    // ==================== CHAT INPUT ====================

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!quenchingManager.isAwaitingName(playerId)) return;

        event.setCancelled(true);
        String message = event.getMessage();

        // Handle on main thread
        SMCBlacksmith.getInstance().getServer().getScheduler().runTask(
                SMCBlacksmith.getInstance(),
                () -> quenchingManager.handleChatInput(player, message)
        );
    }

    // ==================== CLEANUP ====================

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (quenchingManager.hasActiveSession(playerId)) {
            quenchingManager.cancelSession(playerId, "Player disconnected.");
        }
    }
}