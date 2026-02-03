package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.quench.QuenchingManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class QuenchingListener implements Listener {

    private final QuenchingManager quenchingManager;

    private static final Set<Material> CONTAINER_BLOCKS = EnumSet.of(
            Material.CAULDRON,
            Material.WATER_CAULDRON,
            Material.BARREL,
            Material.CHEST
    );

    private static final Set<Material> ANVIL_BLOCKS = EnumSet.of(
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL,
            Material.BARRIER
    );

    public QuenchingListener(QuenchingManager quenchingManager) {
        this.quenchingManager = quenchingManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!quenchingManager.hasActiveSession(playerId)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (!quenchingManager.isHoldingTongs(player)) return;

        Material blockType = block.getType();
        Location targetLoc = block.getLocation();

        if (ANVIL_BLOCKS.contains(blockType) || CONTAINER_BLOCKS.contains(blockType)) {
            event.setCancelled(true);
            quenchingManager.handleTongsUse(player, targetLoc);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (!quenchingManager.isAwaitingName(playerId)) return;

        event.setCancelled(true);

        String message = event.getMessage();

        SMCBlacksmith.getInstance().getServer().getScheduler().runTask(
                SMCBlacksmith.getInstance(),
                () -> quenchingManager.handleChatInput(player, message)
        );
    }
}