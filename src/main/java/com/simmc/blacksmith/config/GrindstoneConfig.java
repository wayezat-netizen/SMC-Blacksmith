package com.simmc.blacksmith.config;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.repair.RepairConfigData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GrindstoneConfig {

    private final Map<String, RepairConfigData> repairConfigs;
    private ItemProviderRegistry itemRegistry;

    public GrindstoneConfig() {
        this.repairConfigs = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        repairConfigs.clear();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            RepairConfigData data = parseRepairConfig(key, section);
            if (data != null) {
                repairConfigs.put(key, data);
            }
        }
    }

    private RepairConfigData parseRepairConfig(String id, ConfigurationSection section) {
        String permission = section.getString("permission", "");
        String itemId = section.getString("id", "");
        String itemType = section.getString("type", "minecraft");
        String repairChancePerm = section.getString("repair_chance_permission", "");

        ConfigurationSection inputSection = section.getConfigurationSection("input");
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;

        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft");
            inputAmount = inputSection.getInt("amount", 1);
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

    public RepairConfigData findByItem(ItemStack item) {
        if (item == null || itemRegistry == null) return null;

        for (RepairConfigData config : repairConfigs.values()) {
            if (itemRegistry.matches(item, config.itemType(), config.itemId())) {
                return config;
            }
        }
        return null;
    }

    public Map<String, RepairConfigData> getRepairConfigs() {
        return new HashMap<>(repairConfigs);
    }

    public int getRepairConfigCount() {
        return repairConfigs.size();
    }
}