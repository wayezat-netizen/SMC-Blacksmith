package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.UUID;

public class ForgeListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();

        if (!(clicked instanceof Interaction)) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!forgeManager.hasActiveSession(playerId)) return;

        event.setCancelled(true);
        forgeManager.processPointHit(player, clicked.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();

        if (!forgeManager.hasActiveSession(playerId)) return;

        event.setCancelled(true);
        forgeManager.processPointHit(player, event.getEntity().getUniqueId());
    }
}