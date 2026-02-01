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

public class RepairManager {

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

        if (!config.permission().isEmpty() && !player.hasPermission(config.permission())) {
            player.sendMessage(messages.getNoPermission());
            return false;
        }

        if (!hasRepairMaterials(player, config)) {
            String itemName = config.inputId();
            player.sendMessage(messages.getMissingMaterials(config.inputAmount(), itemName));
            return false;
        }

        consumeRepairMaterials(player, config);

        int successChance = getSuccessChance(player, config.repairChancePermission());
        int roll = random.nextInt(100) + 1;

        if (roll > successChance) {
            player.sendMessage(messages.getRepairFailed());
            return false;
        }

        int repairPercent = getRepairPercent(player, config.permission());
        applyRepair(item, repairPercent);

        player.sendMessage(messages.getRepairSuccess());
        return true;
    }

    private boolean hasRepairMaterials(Player player, RepairConfigData config) {
        if (config.inputId().isEmpty()) return true;

        int required = config.inputAmount();
        int found = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;

            if (itemRegistry.matches(item, config.inputType(), config.inputId())) {
                found += item.getAmount();
                if (found >= required) return true;
            }
        }

        return false;
    }

    private void consumeRepairMaterials(Player player, RepairConfigData config) {
        if (config.inputId().isEmpty()) return;

        int remaining = config.inputAmount();
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

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

    private void applyRepair(ItemStack item, int percent) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return;

        int currentDamage = damageable.getDamage();
        int maxDamage = maxDurability;

        int repairAmount = (int) (maxDamage * (percent / 100.0));
        int newDamage = Math.max(0, currentDamage - repairAmount);

        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
    }

    private int getSuccessChance(Player player, String permissionBase) {
        if (permissionBase == null || permissionBase.isEmpty()) {
            return 100;
        }

        for (int i = 100; i >= 1; i--) {
            String perm = permissionBase + "." + i;
            if (player.hasPermission(perm)) {
                return i;
            }
        }

        return 50;
    }

    private int getRepairPercent(Player player, String permissionBase) {
        if (permissionBase == null || permissionBase.isEmpty()) {
            return 25;
        }

        for (int i = 100; i >= 1; i--) {
            String perm = permissionBase + "." + i;
            if (player.hasPermission(perm)) {
                return i;
            }
        }

        return 25;
    }

    public boolean canRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) return false;

        GrindstoneConfig grindstoneConfig = configManager.getGrindstoneConfig();
        return grindstoneConfig.findByItem(item) != null;
    }

    public boolean isDamaged(ItemStack item) {
        if (item == null) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;

        return damageable.getDamage() > 0;
    }

    public double getDurabilityPercent(ItemStack item) {
        if (item == null) return 1.0;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return 1.0;

        short maxDurability = item.getType().getMaxDurability();
        if (maxDurability <= 0) return 1.0;

        int currentDamage = damageable.getDamage();
        return 1.0 - ((double) currentDamage / maxDurability);
    }

    public void reload() {
        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
    }
}