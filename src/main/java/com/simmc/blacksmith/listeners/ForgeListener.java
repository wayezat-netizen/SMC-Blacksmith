package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.display.ForgeDisplay;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.ForgeSession;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listener for 3D world-based forge interactions.
 */
public class ForgeListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    /**
     * Handles left-click (strike) during forging.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Only handle left clicks
        if (event.getAction() != Action.LEFT_CLICK_AIR &&
                event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        // Only main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Check if player has active forge session
        if (!forgeManager.hasActiveSession(player.getUniqueId())) {
            return;
        }

        ForgeSession session = forgeManager.getSession(player.getUniqueId());
        ForgeDisplay display = forgeManager.getDisplay(player.getUniqueId());

        if (session == null || display == null || !session.isActive()) {
            return;
        }

        // Check distance to anvil
        if (player.getLocation().distanceSquared(display.getAnvilLocation()) > 25) { // 5 blocks
            return;
        }

        // Cancel the event to prevent breaking blocks
        event.setCancelled(true);

        // Process the strike
        forgeManager.processStrike(player);
    }

    /**
     * Cancels session when player leaves.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (forgeManager.hasActiveSession(player.getUniqueId())) {
            forgeManager.cancelSession(player.getUniqueId());
        }
    }
}