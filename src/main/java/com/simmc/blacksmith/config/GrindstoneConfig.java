package com.simmc.blacksmith.config;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.repair.RepairConfigData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration handler for grindstone repair system.
 * Parses grindstone.yml configuration file.
 */
public class GrindstoneConfig {

    private final Map<String, RepairConfigData> repairConfigs;
    private final List<String> loadWarnings;
    private ItemProviderRegistry itemRegistry;

    public GrindstoneConfig() {
        this.repairConfigs = new HashMap<>();
        this.loadWarnings = new ArrayList<>();
    }

    public void load(FileConfiguration config) {
        repairConfigs.clear();
        loadWarnings.clear();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            RepairConfigData data = parseRepairConfig(key, section);
            if (data != null) {
                repairConfigs.put(key, data);
            }
        }
    }

    /**
     * Logs validation results to the plugin logger.
     */
    public void logValidationResults(JavaPlugin plugin) {
        if (!loadWarnings.isEmpty()) {
            plugin.getLogger().warning("Grindstone config warnings:");
            for (String warning : loadWarnings) {
                plugin.getLogger().warning("  " + warning);
            }
        }
    }

    private RepairConfigData parseRepairConfig(String id, ConfigurationSection section) {
        String permission = section.getString("permission", "");
        String itemId = section.getString("id", "");
        String itemType = section.getString("type", "minecraft").toLowerCase();
        String repairChancePerm = section.getString("repair_chance_permission", "");

        if (itemId.isEmpty()) {
            loadWarnings.add("[" + id + "] Missing item id, skipping");
            return null;
        }

        // Parse input material requirements
        ConfigurationSection inputSection = section.getConfigurationSection("input");
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;

        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft").toLowerCase();
            inputAmount = inputSection.getInt("amount", 1);

            if (inputAmount <= 0) {
                loadWarnings.add("[" + id + ".input] Invalid amount, using 1");
                inputAmount = 1;
            }
        }

        return new RepairConfigData(id, permission, itemId, itemType,
                repairChancePerm, inputId, inputType, inputAmount);
    }

    public void setItemRegistry(ItemProviderRegistry registry) {
        this.itemRegistry = registry;
    }

    public RepairConfigData getRepairConfig(String id) {
        return repairConfigs.get(id);
    }

    /**
     * Finds a repair configuration that matches the given item.
     */
    public RepairConfigData findByItem(ItemStack item) {
        if (item == null || itemRegistry == null) return null;

        for (RepairConfigData config : repairConfigs.values()) {
            if (itemRegistry.matches(item, config.itemType(), config.itemId())) {
                return config;
            }
        }
        return null;
    }

    /**
     * Gets all repair configs for a specific permission base.
     * Useful for listing all items a player can repair.
     */
    public List<RepairConfigData> getConfigsByPermissionBase(String permBase) {
        List<RepairConfigData> result = new ArrayList<>();
        for (RepairConfigData config : repairConfigs.values()) {
            if (config.permission().startsWith(permBase)) {
                result.add(config);
            }
        }
        return result;
    }

    /**
     * Gets all repair configs for a specific item type.
     */
    public List<RepairConfigData> getConfigsByItemType(String type) {
        List<RepairConfigData> result = new ArrayList<>();
        for (RepairConfigData config : repairConfigs.values()) {
            if (config.itemType().equalsIgnoreCase(type)) {
                result.add(config);
            }
        }
        return result;
    }

    public Map<String, RepairConfigData> getRepairConfigs() {
        return new HashMap<>(repairConfigs);
    }

    public int getRepairConfigCount() {
        return repairConfigs.size();
    }

    public List<String> getWarnings() {
        return new ArrayList<>(loadWarnings);
    }
}