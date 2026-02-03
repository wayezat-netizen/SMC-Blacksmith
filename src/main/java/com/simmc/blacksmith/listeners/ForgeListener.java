package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.ForgeSession;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class ForgeListener implements Listener {

    private final ForgeManager forgeManager;

    private static final Set<Material> ANVIL_MATERIALS = EnumSet.of(
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL
    );

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

    /**
     * Prevent anvil from being damaged/broken during forging.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!forgeManager.hasActiveSession(playerId)) return;

        Block block = event.getBlock();
        if (ANVIL_MATERIALS.contains(block.getType())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevent left-click on anvil during forging (which would normally damage it).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!forgeManager.hasActiveSession(playerId)) return;

        Block block = event.getClickedBlock();
        if (block != null && ANVIL_MATERIALS.contains(block.getType())) {
            event.setCancelled(true);
        }
    }
}