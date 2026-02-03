package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.ForgePoint;
import com.simmc.blacksmith.forge.ForgeSession;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ForgeListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clicked = event.getRightClicked();

        if (!(clicked instanceof Interaction)) return;
        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        event.setCancelled(true);
        forgeManager.processPointHit(player, clicked.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Interaction)) return;
        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        event.setCancelled(true);
        forgeManager.processPointHit(player, event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (forgeManager.hasActiveSession(event.getPlayer().getUniqueId())) {
            forgeManager.cancelSession(event.getPlayer().getUniqueId());
        }
    }
}