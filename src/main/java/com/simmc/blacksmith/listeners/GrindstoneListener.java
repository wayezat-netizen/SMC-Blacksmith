package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.GrindstoneConfig;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.repair.RepairGUI;
import com.simmc.blacksmith.repair.RepairManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
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

    // ==================== GRINDSTONE INTERACTION ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.GRINDSTONE) return;

        Player player = event.getPlayer();
        GrindstoneConfig config = configManager.getGrindstoneConfig();
        MessageConfig messages = configManager.getMessageConfig();

        // Sneaking = vanilla behavior
        if (player.isSneaking() && config.isSneakForVanilla()) {
            player.sendMessage(messages.getVanillaAnvil());
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // Check if holding repair hammer
        if (config.isHammerRequired()) {
            if (!repairManager.isRepairHammer(mainHand)) {
                // Not holding hammer - use vanilla grindstone
                return;
            }
        }

        // Cancel vanilla grindstone
        event.setCancelled(true);

        // Check permission
        if (!repairManager.hasRepairPermission(player)) {
            player.sendMessage(messages.getNoPermission());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // Open repair GUI
        repairManager.openRepairGUI(player);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.6f, 1.2f);
    }

    // ==================== REPAIR GUI INTERACTION ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof RepairGUI gui)) return;

        int slot = event.getRawSlot();

        // Click in GUI area
        if (slot >= 0 && slot < inventory.getSize()) {
            // Allow interaction with input slot
            if (gui.isInputSlot(slot)) {
                // Update repair button after a tick to allow item placement
                player.getServer().getScheduler().runTaskLater(
                        configManager.getPlugin(),
                        gui::updateRepairButton,
                        1L
                );
                return; // Allow normal item placement
            }

            // Cancel all other GUI clicks
            event.setCancelled(true);

            // Check repair button click
            if (gui.isRepairButtonSlot(slot)) {
                repairManager.attemptRepairFromGUI(player);
            }
        } else {
            // Click in player inventory - allow shift-click to input slot
            if (event.isShiftClick()) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && !clickedItem.getType().isAir()) {
                    // Only allow damageable items
                    if (repairManager.isDamaged(clickedItem)) {
                        // Allow shift-click, update button after
                        player.getServer().getScheduler().runTaskLater(
                                configManager.getPlugin(),
                                gui::updateRepairButton,
                                1L
                        );
                        return;
                    }
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof RepairGUI)) return;

        repairManager.closeRepairGUI(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        repairManager.closeRepairGUI(event.getPlayer());
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
}