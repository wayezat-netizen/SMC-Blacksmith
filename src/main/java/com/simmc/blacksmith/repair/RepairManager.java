package com.simmc.blacksmith.repair;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.GrindstoneConfig;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.logging.Level;

/**
 * Manages the repair system for items using the grindstone mechanic.
 * Handles material consumption, success chance calculation, and durability restoration.
 */
public class RepairManager {

    private static final int[] COMMON_PERMISSION_TIERS = {100, 95, 90, 85, 80, 75, 70, 65, 60, 55, 50, 45, 40, 35, 30, 25, 20, 15, 10, 5};
    private static final int DEFAULT_SUCCESS_CHANCE = 50;
    private static final int DEFAULT_REPAIR_PERCENT = 25;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;
    private final Random random;

    public RepairManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.random = new Random();

        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
    }

    /**
     * Attempts to repair an item for the specified player.
     *
     * @param player The player attempting the repair
     * @param item The item to repair
     * @return true if repair was successful, false otherwise
     */
    public boolean attemptRepair(Player player, ItemStack item) {
        // Null checks
        if (player == null || item == null) {
            plugin.getLogger().warning("attemptRepair called with null player or item");
            return false;
        }

        MessageConfig messages = configManager.getMessageConfig();
        GrindstoneConfig grindstoneConfig = configManager.getGrindstoneConfig();

        // Find repair configuration for this item
        RepairConfigData config = grindstoneConfig.findByItem(item);
        if (config == null) {
            player.sendMessage(messages.getInvalidItem());
            return false;
        }

        // Check permission
        if (!config.permission().isEmpty() && !player.hasPermission(config.permission())) {
            player.sendMessage(messages.getNoPermission());
            return false;
        }

        // Check if player has required materials
        if (!hasRepairMaterials(player, config)) {
            String itemName = config.inputId();
            player.sendMessage(messages.getMissingMaterials(config.inputAmount(), itemName));
            return false;
        }

        // FIXED: Calculate success BEFORE consuming materials
        int successChance = getSuccessChance(player, config.repairChancePermission());
        int roll = random.nextInt(100) + 1;

        plugin.getLogger().fine("Repair attempt - Player: " + player.getName() +
                ", Success chance: " + successChance + ", Roll: " + roll);

        if (roll > successChance) {
            // FIXED: Materials are NOT consumed on failure
            player.sendMessage(messages.getRepairFailed());
            return false;
        }

        // Only consume materials on successful repair
        consumeRepairMaterials(player, config);

        // Apply the repair
        int repairPercent = getRepairPercent(player, config.permission());
        applyRepair(item, repairPercent);

        player.sendMessage(messages.getRepairSuccess());
        return true;
    }

    /**
     * Checks if the player has the required materials for repair.
     */
    private boolean hasRepairMaterials(Player player, RepairConfigData config) {
        if (config.inputId() == null || config.inputId().isEmpty()) {
            return true;
        }

        int required = config.inputAmount();
        int found = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;

            if (itemRegistry.matches(item, config.inputType(), config.inputId())) {
                found += item.getAmount();
                if (found >= required) return true;
            }
        }

        return false;
    }

    /**
     * Consumes the required repair materials from the player's inventory.
     */
    private void consumeRepairMaterials(Player player, RepairConfigData config) {
        if (config.inputId() == null || config.inputId().isEmpty()) {
            return;
        }

        int remaining = config.inputAmount();
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;

            if (itemRegistry.matches(item, config.inputType(), config.inputId())) {
                int take = Math.min(remaining, item.getAmount());
                remaining -= take;

                int newAmount = item.getAmount() - take;
                if (newAmount <= 0) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(newAmount);
                }
            }
        }
    }

    /**
     * Applies durability repair to the item.
     */
    private void applyRepair(ItemStack item, int percent) {
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;

        int currentDamage = damageable.getDamage();
        int repairAmount = (int) (maxDurability * (percent / 100.0));
        int newDamage = Math.max(0, currentDamage - repairAmount);

        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
    }

    /**
     * Gets the success chance for the player based on permissions.
     * OPTIMIZED: Checks common tiers first before full loop.
     */
    private int getSuccessChance(Player player, String permissionBase) {
        if (permissionBase == null || permissionBase.isEmpty()) {
            return 100; // 100% success if no permission configured
        }

        // OPTIMIZED: Check common tiers first (much faster)
        for (int tier : COMMON_PERMISSION_TIERS) {
            if (player.hasPermission(permissionBase + "." + tier)) {
                return tier;
            }
        }

        // Fallback: check all other values
        for (int i = 99; i >= 1; i--) {
            // Skip values already checked in common tiers
            if (isCommonTier(i)) continue;

            if (player.hasPermission(permissionBase + "." + i)) {
                return i;
            }
        }

        return DEFAULT_SUCCESS_CHANCE;
    }

    /**
     * Gets the repair percentage for the player based on permissions.
     * OPTIMIZED: Checks common tiers first before full loop.
     */
    private int getRepairPercent(Player player, String permissionBase) {
        if (permissionBase == null || permissionBase.isEmpty()) {
            return DEFAULT_REPAIR_PERCENT;
        }

        // OPTIMIZED: Check common tiers first
        for (int tier : COMMON_PERMISSION_TIERS) {
            if (player.hasPermission(permissionBase + "." + tier)) {
                return tier;
            }
        }

        // Fallback: check all other values
        for (int i = 99; i >= 1; i--) {
            if (isCommonTier(i)) continue;

            if (player.hasPermission(permissionBase + "." + i)) {
                return i;
            }
        }

        return DEFAULT_REPAIR_PERCENT;
    }

    private boolean isCommonTier(int value) {
        for (int tier : COMMON_PERMISSION_TIERS) {
            if (tier == value) return true;
        }
        return false;
    }

    /**
     * Checks if an item can be repaired.
     */
    public boolean canRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) return false;

        GrindstoneConfig grindstoneConfig = configManager.getGrindstoneConfig();
        return grindstoneConfig.findByItem(item) != null;
    }

    /**
     * Checks if an item is damaged.
     */
    public boolean isDamaged(ItemStack item) {
        if (item == null) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;

        return damageable.getDamage() > 0;
    }

    /**
     * Gets the current durability percentage of an item.
     */
    public double getDurabilityPercent(ItemStack item) {
        if (item == null) return 1.0;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return 1.0;

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return 1.0;

        int currentDamage = damageable.getDamage();
        return 1.0 - ((double) currentDamage / maxDurability);
    }

    /**
     * Reloads the repair manager configuration.
     */
    public void reload() {
        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
    }
}