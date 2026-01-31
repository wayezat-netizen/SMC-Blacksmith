package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.furnace.FurnaceManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final FurnaceManager furnaceManager;
    private final ForgeManager forgeManager;

    public PlayerListener(FurnaceManager furnaceManager, ForgeManager forgeManager) {
        this.furnaceManager = furnaceManager;
        this.forgeManager = forgeManager;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (furnaceManager.hasOpenGUI(player.getUniqueId())) {
            furnaceManager.closeGUI(player);
        }

        if (forgeManager.hasActiveSession(player.getUniqueId())) {
            forgeManager.cancelSession(player.getUniqueId());
        }
    }
}