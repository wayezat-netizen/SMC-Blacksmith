package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.quench.QuenchingManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Central listener for player cleanup across all systems.
 */
public class PlayerListener implements Listener {

    private final FurnaceManager furnaceManager;
    private final ForgeManager forgeManager;
    private final QuenchingManager quenchingManager;

    public PlayerListener(FurnaceManager furnaceManager, ForgeManager forgeManager,
                          QuenchingManager quenchingManager) {
        this.furnaceManager = furnaceManager;
        this.forgeManager = forgeManager;
        this.quenchingManager = quenchingManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Cleanup all systems
        cleanupFurnace(player, playerId);
        cleanupForge(playerId);
        cleanupQuenching(playerId);
    }

    private void cleanupFurnace(Player player, UUID playerId) {
        if (furnaceManager.hasOpenGUI(playerId)) {
            furnaceManager.closeGUI(player);
        }
    }

    private void cleanupForge(UUID playerId) {
        if (forgeManager.hasActiveSession(playerId)) {
            forgeManager.cancelSession(playerId);
        }
    }

    private void cleanupQuenching(UUID playerId) {
        if (quenchingManager.hasActiveSession(playerId)) {
            quenchingManager.cancelSession(playerId, "Player disconnected.");
        }
    }
}