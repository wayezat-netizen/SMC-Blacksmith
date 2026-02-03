package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.repair.RepairManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class RepairListener implements Listener {

    private final RepairManager repairManager;

    public RepairListener(RepairManager repairManager) {
        this.repairManager = repairManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) return;

        Player player = event.getPlayer();

        // Sneak to use vanilla grindstone
        if (player.isSneaking()) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) return;

        if (!repairManager.canRepair(mainHand)) return;

        event.setCancelled(true);

        MessageConfig messages = SMCBlacksmith.getInstance().getConfigManager().getMessageConfig();

        if (!repairManager.isDamaged(mainHand)) {
            player.sendMessage(messages.getItemNotDamaged());
            return;
        }

        repairManager.attemptRepair(player, mainHand);
    }
}