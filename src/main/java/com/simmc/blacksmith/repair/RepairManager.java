package com.simmc.blacksmith.repair;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.GrindstoneConfig;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item repair through grindstone interaction.
 */
public class RepairManager {

    private static final int MIN_CHANCE = 0;
    private static final int MAX_CHANCE = 100;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;
    private final Random random;
    private final Map<UUID, RepairGUI> openGUIs;

    public RepairManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.random = new Random();
        this.openGUIs = new ConcurrentHashMap<>();
        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
    }

    // ==================== GUI MANAGEMENT ====================

    public void openRepairGUI(Player player) {
        GrindstoneConfig config = configManager.getGrindstoneConfig();
        int successChance = getSuccessChanceFromPermission(player);
        int repairAmount = getRepairAmountFromPermission(player);

        RepairGUI gui = new RepairGUI(player, config, successChance, repairAmount);
        openGUIs.put(player.getUniqueId(), gui);
        gui.open();
    }

    public void closeRepairGUI(Player player) {
        RepairGUI gui = openGUIs.remove(player.getUniqueId());
        if (gui != null) {
            ItemStack inputItem = gui.getInputItem();
            if (inputItem != null && !inputItem.getType().isAir()) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(inputItem);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
            }
        }
    }

    public RepairGUI getOpenGUI(UUID playerId) {
        return openGUIs.get(playerId);
    }

    public boolean hasOpenGUI(UUID playerId) {
        return openGUIs.containsKey(playerId);
    }

    // ==================== REPAIR EXECUTION ====================

    public boolean attemptRepairFromGUI(Player player) {
        RepairGUI gui = openGUIs.get(player.getUniqueId());
        if (gui == null) return false;

        ItemStack item = gui.getInputItem();
        if (item == null || item.getType().isAir()) {
            player.sendMessage("§cPlace an item first!");
            return false;
        }

        if (!isDamaged(item)) {
            player.sendMessage("§eThis item doesn't need repair.");
            return false;
        }

        RepairConfigData config = findRepairConfig(item);

        // Item MUST be in repair config to be repairable
        if (config == null) {
            player.sendMessage("§cThis item cannot be repaired here.");
            return false;
        }

        // Check per-item permission
        if (!hasRepairPermissionForItem(player, config)) {
            player.sendMessage(configManager.getMessageConfig().getNoPermission());
            return false;
        }

        // Check materials - ALWAYS required if config has input defined
        if (config.hasInput() && !hasRepairMaterials(player, config)) {
            player.sendMessage(configManager.getMessageConfig()
                    .getMissingMaterials(config.inputAmount(), config.inputId()));
            return false;
        }

        // Get per-item success chance
        int successChance = getSuccessChanceForItem(player, config);

        boolean success = rollSuccess(successChance);

        if (!success) {
            handleRepairFailure(player);
            // Consume materials even on failure
            if (config.hasInput()) {
                consumeRepairMaterials(player, config);
            }
            return false;
        }

        // Success - consume materials
        if (config.hasInput()) {
            consumeRepairMaterials(player, config);
        }

        int repairAmount = getRepairAmountForItem(player, config);

        applyRepair(item, repairAmount);
        gui.updateRepairButton();
        handleRepairSuccess(player);
        return true;
    }

    public boolean attemptRepair(Player player, ItemStack item) {
        MessageConfig messages = configManager.getMessageConfig();

        RepairConfigData config = findRepairConfig(item);

        // Item MUST be in repair config to be repairable
        if (config == null) {
            player.sendMessage("§cThis item cannot be repaired.");
            return false;
        }

        // Check per-item permission
        if (!hasRepairPermissionForItem(player, config)) {
            player.sendMessage(messages.getNoPermission());
            return false;
        }

        // Check materials - ALWAYS required if config has input defined
        if (config.hasInput() && !hasRepairMaterials(player, config)) {
            player.sendMessage(messages.getMissingMaterials(config.inputAmount(), config.inputId()));
            return false;
        }

        int successChance = getSuccessChanceForItem(player, config);
        boolean success = rollSuccess(successChance);

        if (!success) {
            handleRepairFailure(player);
            // Consume materials even on failure
            if (config.hasInput()) {
                consumeRepairMaterials(player, config);
            }
            return false;
        }

        // Success - consume materials
        if (config.hasInput()) {
            consumeRepairMaterials(player, config);
        }

        int repairPercent = getRepairAmountForItem(player, config);
        applyRepair(item, repairPercent);
        handleRepairSuccess(player);
        return true;
    }

    // ==================== PERMISSION CHECKS ====================

    public boolean hasRepairPermission(Player player) {
        return true; // We check per-item permission when attempting repair
    }

    public boolean hasRepairPermissionForItem(Player player, RepairConfigData config) {
        if (config == null) return false;
        String itemPerm = config.condition();
        if (itemPerm != null && !itemPerm.isEmpty()) {
            return player.hasPermission(itemPerm);
        }
        String basePerm = configManager.getGrindstoneConfig().getUsePermission();
        return player.hasPermission(basePerm);
    }

    public int getSuccessChanceFromPermission(Player player) {
        GrindstoneConfig config = configManager.getGrindstoneConfig();
        return getPermissionValue(player, config.getSuccessChancePermission(), config.getDefaultSuccessChance());
    }

    public int getSuccessChanceForItem(Player player, RepairConfigData config) {
        if (config == null) return getSuccessChanceFromPermission(player);
        String permPrefix = config.successChancePlaceholder();
        if (permPrefix == null || permPrefix.isEmpty()) {
            return getSuccessChanceFromPermission(player);
        }
        return getPermissionValue(player, permPrefix, configManager.getGrindstoneConfig().getDefaultSuccessChance());
    }

    public int getRepairAmountFromPermission(Player player) {
        GrindstoneConfig config = configManager.getGrindstoneConfig();
        return getPermissionValue(player, config.getRepairAmountPermission(), config.getDefaultRepairAmount());
    }

    public int getRepairAmountForItem(Player player, RepairConfigData config) {
        if (config == null) return getRepairAmountFromPermission(player);
        String permPrefix = config.repairAmountPlaceholder();
        if (permPrefix == null || permPrefix.isEmpty()) {
            return getRepairAmountFromPermission(player);
        }
        return getPermissionValue(player, permPrefix, configManager.getGrindstoneConfig().getDefaultRepairAmount());
    }

    private int getPermissionValue(Player player, String permPrefix, int defaultValue) {
        if (permPrefix == null || permPrefix.isEmpty()) return defaultValue;
        for (int value = 100; value >= 1; value--) {
            if (player.hasPermission(permPrefix + "." + value)) {
                return clamp(value, MIN_CHANCE, MAX_CHANCE);
            }
        }
        return defaultValue;
    }

    // ==================== PUBLIC API ====================

    public boolean canRepair(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!isDamageableItem(item)) return false;
        // Only items with a repair config can be repaired
        RepairConfigData config = findRepairConfig(item);
        return config != null;
    }

    public boolean isDamaged(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;
        return damageable.getDamage() > 0;
    }

    public boolean isRepairHammer(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        GrindstoneConfig config = configManager.getGrindstoneConfig();
        if (!config.isHammerRequired()) return true;

        String hammerType = config.getHammerType();
        String hammerId = config.getHammerId();

        if (itemRegistry.matches(item, hammerType, hammerId)) {
            return true;
        }

        String fallbackMaterial = config.getHammerFallbackMaterial();
        return item.getType().name().equalsIgnoreCase(fallbackMaterial);
    }

    public void reload() {
        configManager.getGrindstoneConfig().setItemRegistry(itemRegistry);
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

    // ==================== REPAIR APPLICATION ====================

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

    private void handleRepairSuccess(Player player) {
        player.sendMessage(configManager.getMessageConfig().getRepairSuccess());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
    }

    private void handleRepairFailure(Player player) {
        player.sendMessage(configManager.getMessageConfig().getRepairFailed());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.7f);
    }

    // ==================== HELPERS ====================

    private RepairConfigData findRepairConfig(ItemStack item) {
        return configManager.getGrindstoneConfig().findByItem(item).orElse(null);
    }

    private boolean isDamageableItem(ItemStack item) {
        return item.getItemMeta() instanceof Damageable;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

