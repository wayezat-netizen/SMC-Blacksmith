package com.simmc.blacksmith.repair;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

/**
 * Manages item repair through grindstone interaction.
 * Supports PAPI placeholders for dynamic success chance and repair amount.
 */
public class RepairManager {

    private static final int DEFAULT_SUCCESS_CHANCE = 50;
    private static final int DEFAULT_REPAIR_PERCENT = 25;
    private static final int MIN_CHANCE = 0;
    private static final int MAX_CHANCE = 100;

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

    // ==================== PUBLIC API ====================

    /**
     * Attempts to repair an item for a player.
     * @return true if repair succeeded
     */
    public boolean attemptRepair(Player player, ItemStack item) {
        MessageConfig messages = configManager.getMessageConfig();

        // Find repair config
        RepairConfigData config = findRepairConfig(item);
        if (config == null) {
            player.sendMessage(messages.getInvalidItem());
            return false;
        }

        // Validate conditions
        ValidationResult validation = validateRepair(player, config);
        if (!validation.success()) {
            player.sendMessage(validation.message());
            return false;
        }

        // Roll for success
        int successChance = getSuccessChance(player, config);
        boolean success = rollSuccess(successChance);

        if (!success) {
            handleRepairFailure(player, messages);
            return false;
        }

        // Success - consume materials and apply repair
        consumeRepairMaterials(player, config);

        int repairPercent = getRepairAmount(player, config);
        applyRepair(item, repairPercent);

        handleRepairSuccess(player, messages);
        return true;
    }

    /**
     * Checks if an item can be repaired with this system.
     */
    public boolean canRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!isDamageableItem(item)) return false;
        return findRepairConfig(item) != null;
    }

    /**
     * Checks if an item is damaged and needs repair.
     */
    public boolean isDamaged(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        return damageable.getDamage() > 0;
    }

    public void reload() {
        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
    }

    // ==================== VALIDATION ====================

    private record ValidationResult(boolean success, String message) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }
        static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }

    private ValidationResult validateRepair(Player player, RepairConfigData config) {
        MessageConfig messages = configManager.getMessageConfig();

        // Check PAPI condition
        if (!checkCondition(player, config)) {
            return ValidationResult.fail(messages.getConditionNotMet());
        }

        // Check materials
        if (!hasRepairMaterials(player, config)) {
            return ValidationResult.fail(
                    messages.getMissingMaterials(config.inputAmount(), config.inputId())
            );
        }

        return ValidationResult.ok();
    }

    private boolean checkCondition(Player player, RepairConfigData config) {
        if (!config.hasCondition()) return true;

        PlaceholderAPIHook papi = getPapiHook();
        if (papi == null || !papi.isAvailable()) return true;

        return papi.checkCondition(player, config.condition());
    }

    // ==================== MATERIALS ====================

    private boolean hasRepairMaterials(Player player, RepairConfigData config) {
        if (!config.hasInput()) return true;

        int required = config.inputAmount();
        int found = countMatchingItems(player, config);
        return found >= required;
    }

    private int countMatchingItems(Player player, RepairConfigData config) {
        int count = 0;
        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot != null && itemRegistry.matches(slot, config.inputType(), config.inputId())) {
                count += slot.getAmount();
            }
        }
        return count;
    }

    private void consumeRepairMaterials(Player player, RepairConfigData config) {
        if (!config.hasInput()) return;

        int remaining = config.inputAmount();
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack slot = contents[i];
            if (slot == null) continue;

            if (itemRegistry.matches(slot, config.inputType(), config.inputId())) {
                int take = Math.min(remaining, slot.getAmount());
                remaining -= take;

                int newAmount = slot.getAmount() - take;
                if (newAmount <= 0) {
                    player.getInventory().setItem(i, null);
                } else {
                    slot.setAmount(newAmount);
                }
            }
        }
    }

    // ==================== REPAIR CALCULATION ====================

    private int getSuccessChance(Player player, RepairConfigData config) {
        if (!config.hasSuccessChancePlaceholder()) {
            return DEFAULT_SUCCESS_CHANCE;
        }

        PlaceholderAPIHook papi = getPapiHook();
        if (papi == null || !papi.isAvailable()) {
            return DEFAULT_SUCCESS_CHANCE;
        }

        double value = papi.parseDouble(player, config.successChancePlaceholder());
        return clamp((int) value, MIN_CHANCE, MAX_CHANCE);
    }

    private int getRepairAmount(Player player, RepairConfigData config) {
        if (!config.hasRepairAmountPlaceholder()) {
            return DEFAULT_REPAIR_PERCENT;
        }

        PlaceholderAPIHook papi = getPapiHook();
        if (papi == null || !papi.isAvailable()) {
            return DEFAULT_REPAIR_PERCENT;
        }

        double value = papi.parseDouble(player, config.repairAmountPlaceholder());
        return clamp((int) value, 1, 100);
    }

    private boolean rollSuccess(int chance) {
        int roll = random.nextInt(100) + 1;
        return roll <= chance;
    }

    private void applyRepair(ItemStack item, int percent) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;

        int repairAmount = (int) (maxDurability * (percent / 100.0));
        int newDamage = Math.max(0, damageable.getDamage() - repairAmount);

        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
    }

    // ==================== FEEDBACK ====================

    private void handleRepairSuccess(Player player, MessageConfig messages) {
        player.sendMessage(messages.getRepairSuccess());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    }

    private void handleRepairFailure(Player player, MessageConfig messages) {
        player.sendMessage(messages.getRepairFailed());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
    }

    // ==================== HELPERS ====================

    /**
     * Finds repair config for an item.
     * Handles Optional return from GrindstoneConfig.
     */
    private RepairConfigData findRepairConfig(ItemStack item) {
        // FIX: Handle Optional<RepairConfigData> return type
        return configManager.getGrindstoneConfig().findByItem(item).orElse(null);
    }

    private boolean isDamageableItem(ItemStack item) {
        return item.getItemMeta() instanceof Damageable;
    }

    private PlaceholderAPIHook getPapiHook() {
        return SMCBlacksmith.getInstance().getPapiHook();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}