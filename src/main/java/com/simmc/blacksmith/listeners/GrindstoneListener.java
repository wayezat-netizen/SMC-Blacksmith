package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.repair.RepairManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles grindstone interactions for custom repair system.
 */
public class GrindstoneListener implements Listener {

    private static final int UPPER_SLOT = 0;
    private static final int LOWER_SLOT = 1;

    private final RepairManager repairManager;
    private final ConfigManager configManager;

    public GrindstoneListener(RepairManager repairManager, ConfigManager configManager) {
        this.repairManager = repairManager;
        this.configManager = configManager;
    }

    // ==================== INTERACTION ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) return;

        Player player = event.getPlayer();
        MessageConfig messages = configManager.getMessageConfig();

        // Sneaking = vanilla behavior
        if (player.isSneaking()) {
            player.sendMessage(messages.getVanillaAnvil());
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Empty hand = vanilla grindstone
        if (mainHand.getType().isAir()) return;

        // Not in custom repair system = vanilla
        if (!repairManager.canRepair(mainHand)) return;

        // Check if damaged
        if (!repairManager.isDamaged(mainHand)) {
            player.sendMessage("§eThis item doesn't need repair.");
            event.setCancelled(true);
            return;
        }

        // Use custom repair
        event.setCancelled(true);
        repairManager.attemptRepair(player, mainHand);
    }

    // ==================== GRINDSTONE PREPARE ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inv = event.getInventory();

        ItemStack upperItem = inv.getItem(UPPER_SLOT);
        ItemStack lowerItem = inv.getItem(LOWER_SLOT);

        boolean upperCustom = isCustomRepairable(upperItem);
        boolean lowerCustom = isCustomRepairable(lowerItem);

        // Prevent vanilla combining of custom items
        if (upperCustom && lowerCustom) {
            event.setResult(null);
        }
    }

    private boolean isCustomRepairable(ItemStack item) {
        return item != null && !item.getType().isAir() && repairManager.canRepair(item);
    }

    // ==================== PERMISSION CHECKS ====================

    public boolean canUseCustomRepair(Player player) {
        return player.hasPermission("blacksmith.repair.use");
    }

    public boolean canBypassCost(Player player) {
        return player.hasPermission("blacksmith.repair.free");
    }
}