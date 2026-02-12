package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/**
 * Handles forge session cancellation via keybind.
 * SNEAK + RIGHT-CLICK to cancel active forge session.
 */
public class ForgeCancelListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeCancelListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    /**
     * SNEAK + RIGHT-CLICK to cancel forge session.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Must have active session
        if (!forgeManager.hasActiveSession(playerId)) return;

        // Must be sneaking
        if (!player.isSneaking()) return;

        // Cancel the session
        event.setCancelled(true);
        forgeManager.cancelSession(playerId);
    }

    /**
     * Force end session when player disconnects (no refund).
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        if (forgeManager.hasActiveSession(playerId)) {
            forgeManager.forceEndSession(playerId);
        }
    }
}