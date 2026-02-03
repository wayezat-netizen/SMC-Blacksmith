package com.simmc.blacksmith.repair;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.GrindstoneConfig;
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

public class RepairManager {

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

    public boolean attemptRepair(Player player, ItemStack item) {
        MessageConfig messages = configManager.getMessageConfig();
        GrindstoneConfig grindstoneConfig = configManager.getGrindstoneConfig();

        RepairConfigData config = grindstoneConfig.findByItem(item);
        if (config == null) {
            player.sendMessage(messages.getInvalidItem());
            return false;
        }

        // Check PAPI condition
        if (!checkCondition(player, config)) {
            player.sendMessage(messages.getConditionNotMet());
            return false;
        }

        // Check materials
        if (!hasRepairMaterials(player, config)) {
            player.sendMessage(messages.getMissingMaterials(config.inputAmount(), config.inputId()));
            return false;
        }

        // Get success chance from PAPI
        int successChance = getSuccessChance(player, config);
        int roll = random.nextInt(100) + 1;

        if (roll > successChance) {
            player.sendMessage(messages.getRepairFailed());
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
            return false;
        }

        // Consume materials only on success
        consumeRepairMaterials(player, config);

        // Apply repair
        int repairPercent = getRepairAmount(player, config);
        applyRepair(item, repairPercent);

        player.sendMessage(messages.getRepairSuccess());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        return true;
    }

    private boolean checkCondition(Player player, RepairConfigData config) {
        if (!config.hasCondition()) return true;

        PlaceholderAPIHook papi = SMCBlacksmith.getInstance().getPapiHook();
        if (papi == null || !papi.isAvailable()) return true;

        return papi.checkCondition(player, config.condition());
    }

    private int getSuccessChance(Player player, RepairConfigData config) {
        if (!config.hasSuccessChancePlaceholder()) {
            return DEFAULT_SUCCESS_CHANCE;
        }

        PlaceholderAPIHook papi = SMCBlacksmith.getInstance().getPapiHook();
        if (papi == null || !papi.isAvailable()) {
            return DEFAULT_SUCCESS_CHANCE;
        }

        double value = papi.parseDouble(player, config.successChancePlaceholder());
        return Math.max(0, Math.min(100, (int) value));
    }

    private int getRepairAmount(Player player, RepairConfigData config) {
        if (!config.hasRepairAmountPlaceholder()) {
            return DEFAULT_REPAIR_PERCENT;
        }

        PlaceholderAPIHook papi = SMCBlacksmith.getInstance().getPapiHook();
        if (papi == null || !papi.isAvailable()) {
            return DEFAULT_REPAIR_PERCENT;
        }

        double value = papi.parseDouble(player, config.repairAmountPlaceholder());
        return Math.max(1, Math.min(100, (int) value));
    }

    private boolean hasRepairMaterials(Player player, RepairConfigData config) {
        if (!config.hasInput()) return true;

        int required = config.inputAmount();
        int found = 0;

        for (ItemStack slot : player.getInventory().getContents()) {
            if (slot == null) continue;
            if (itemRegistry.matches(slot, config.inputType(), config.inputId())) {
                found += slot.getAmount();
                if (found >= required) return true;
            }
        }
        return false;
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

                if (slot.getAmount() - take <= 0) {
                    player.getInventory().setItem(i, null);
                } else {
                    slot.setAmount(slot.getAmount() - take);
                }
            }
        }
    }

    private void applyRepair(ItemStack item, int percent) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        short maxDura = item.getType().getMaxDurability();
        if (maxDura <= 0) return;

        int repairAmount = (int) (maxDura * (percent / 100.0));
        int newDamage = Math.max(0, damageable.getDamage() - repairAmount);

        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
    }

    public boolean canRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!(item.getItemMeta() instanceof Damageable)) return false;
        return configManager.getGrindstoneConfig().findByItem(item) != null;
    }

    public boolean isDamaged(ItemStack item) {
        if (item == null) return false;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return false;
        return damageable.getDamage() > 0;
    }

    public void reload() {
        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
    }
}