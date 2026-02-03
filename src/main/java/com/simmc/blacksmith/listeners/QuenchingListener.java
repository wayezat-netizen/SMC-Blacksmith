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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Set;

public class QuenchingListener implements Listener {

    private final QuenchingManager quenchingManager;

    private static final Set<Material> CONTAINER_BLOCKS = Set.of(
            Material.CAULDRON,
            Material.WATER_CAULDRON,
            Material.BARREL,
            Material.CHEST
    );

    public QuenchingListener(QuenchingManager quenchingManager) {
        this.quenchingManager = quenchingManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;
        if (!quenchingManager.hasActiveSession(player.getUniqueId())) return;
        if (!quenchingManager.isHoldingTongs(player)) return;

        Location targetLoc = block.getLocation();

        if (isAnvilBlock(block.getType())) {
            event.setCancelled(true);
            quenchingManager.handleTongsUse(player, targetLoc);
            return;
        }

        if (CONTAINER_BLOCKS.contains(block.getType())) {
            event.setCancelled(true);
            quenchingManager.handleTongsUse(player, targetLoc);
        }
    }

    private boolean isAnvilBlock(Material material) {
        return material == Material.ANVIL
                || material == Material.CHIPPED_ANVIL
                || material == Material.DAMAGED_ANVIL
                || material == Material.BARRIER;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!quenchingManager.isAwaitingName(player.getUniqueId())) return;

        event.setCancelled(true);

        String message = event.getMessage();

        SMCBlacksmith.getInstance().getServer().getScheduler().runTask(
                SMCBlacksmith.getInstance(),
                () -> quenchingManager.handleChatInput(player, message)
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (quenchingManager.hasActiveSession(event.getPlayer().getUniqueId())) {
            quenchingManager.cancelSession(event.getPlayer().getUniqueId(), "Player disconnected.");
        }
    }
}