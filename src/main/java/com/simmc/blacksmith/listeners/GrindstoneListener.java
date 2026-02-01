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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Handles all grindstone-related events including:
 * - Custom repair system when right-clicking grindstone with item in hand
 * - Vanilla grindstone GUI interactions
 * - Preventing vanilla grindstone behavior when custom repair is used
 */
public class GrindstoneListener implements Listener {

    private final RepairManager repairManager;
    private final ConfigManager configManager;

    // Grindstone inventory slot indices
    private static final int UPPER_SLOT = 0;
    private static final int LOWER_SLOT = 1;
    private static final int RESULT_SLOT = 2;

    public GrindstoneListener(RepairManager repairManager, ConfigManager configManager) {
        this.repairManager = repairManager;
        this.configManager = configManager;
    }

    /**
     * Handles player right-clicking on a grindstone block.
     * If the player has a repairable item in hand and is not sneaking,
     * attempt custom repair. Otherwise, allow vanilla grindstone GUI.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.GRINDSTONE) return;

        Player player = event.getPlayer();
        MessageConfig messages = configManager.getMessageConfig();

        // If sneaking, allow vanilla grindstone behavior
        if (player.isSneaking()) {
            player.sendMessage(messages.getVanillaAnvil());
            return;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // If hand is empty, open vanilla grindstone
        if (mainHand.getType().isAir()) {
            return;
        }

        // Check if item can be repaired with our custom system
        if (!repairManager.canRepair(mainHand)) {
            // Item not in our repair config, let vanilla handle it
            return;
        }

        // Check if item needs repair
        if (!repairManager.isDamaged(mainHand)) {
            player.sendMessage("§eThis item doesn't need repair.");
            event.setCancelled(true);
            return;
        }

        // Cancel vanilla grindstone and use custom repair
        event.setCancelled(true);

        // Attempt the repair
        repairManager.attemptRepair(player, mainHand);
    }

    /**
     * Handles the grindstone inventory opening event.
     * Can be used to track players using the grindstone GUI.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrindstoneOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() != InventoryType.GRINDSTONE) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Optional: Log or track grindstone usage
        // plugin.getLogger().fine(player.getName() + " opened grindstone GUI");
    }

    /**
     * Handles clicks within the grindstone inventory.
     * Can be used to intercept and modify grindstone behavior.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstoneClick(InventoryClickEvent event) {
        if (event.getInventory().getType() != InventoryType.GRINDSTONE) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        int slot = event.getRawSlot();

        // Slot 0 and 1 are input slots, slot 2 is output
        if (slot == RESULT_SLOT) {
            ItemStack result = inv.getItem(RESULT_SLOT);
            if (result != null && !result.getType().isAir()) {
                // Player is taking the result from grindstone
                // This is vanilla grindstone behavior (removing enchants/combining)
                // We allow this by default
            }
        }
    }

    /**
     * Handles the grindstone prepare event.
     * This fires when items are placed in the grindstone slots.
     * Can be used to modify or prevent vanilla grindstone results.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inv = event.getInventory();

        // Use getItem() with slot indices to get items
        ItemStack upperItem = inv.getItem(UPPER_SLOT);
        ItemStack lowerItem = inv.getItem(LOWER_SLOT);

        // Check if either item is in our custom repair system
        boolean upperCustom = upperItem != null && !upperItem.getType().isAir() && repairManager.canRepair(upperItem);
        boolean lowerCustom = lowerItem != null && !lowerItem.getType().isAir() && repairManager.canRepair(lowerItem);

        // If both slots have custom repairable items, prevent vanilla combining
        if (upperCustom && lowerCustom) {
            // Prevent vanilla grindstone from combining these items
            // Players should use our custom repair system instead
            event.setResult(null);
            return;
        }

        // Allow vanilla grindstone behavior for:
        // - Removing enchantments
        // - Combining non-custom items
        // - Single item operations
    }

    /**
     * Handles the grindstone inventory closing event.
     * Returns items to player if they left items in the grindstone.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onGrindstoneClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() != InventoryType.GRINDSTONE) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Bukkit handles returning items automatically, but we can add custom behavior here
        // For example, logging or special handling for custom items
    }

    /**
     * Checks if a player has permission to use the grindstone for custom repairs.
     *
     * @param player The player to check
     * @return true if the player can use custom grindstone repairs
     */
    public boolean canUseCustomRepair(Player player) {
        return player.hasPermission("blacksmith.repair.use");
    }

    /**
     * Checks if a player can bypass the repair cost.
     *
     * @param player The player to check
     * @return true if the player can repair for free
     */
    public boolean canBypassCost(Player player) {
        return player.hasPermission("blacksmith.repair.free");
    }
}