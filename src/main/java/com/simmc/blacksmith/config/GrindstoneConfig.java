package com.simmc.blacksmith.config;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.repair.RepairConfigData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Configuration for grindstone repair system.
 */
public class GrindstoneConfig {

    private final Map<String, RepairConfigData> repairConfigs;
    private final List<String> loadWarnings;
    private volatile ItemProviderRegistry itemRegistry;

    public GrindstoneConfig() {
        this.repairConfigs = new LinkedHashMap<>();
        this.loadWarnings = new ArrayList<>();
    }

    public void load(FileConfiguration config) {
        repairConfigs.clear();
        loadWarnings.clear();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            parseRepairConfig(key, section).ifPresent(data -> repairConfigs.put(key, data));
        }
    }

    private Optional<RepairConfigData> parseRepairConfig(String id, ConfigurationSection section) {
        String itemId = section.getString("id", "");
        if (itemId.isEmpty()) {
            loadWarnings.add("[" + id + "] Missing item id");
            return Optional.empty();
        }

        String itemType = section.getString("type", "minecraft");
        String condition = section.getString("condition", "");
        String successChance = section.getString("success_chance", "%svalues_repair_chance%");
        String repairAmount = section.getString("repair_amount", "%svalues_repair_amount%");

        // Input material
        String inputId = "";
        String inputType = "minecraft";
        int inputAmount = 1;

        ConfigurationSection inputSection = section.getConfigurationSection("input");
        if (inputSection != null) {
            inputId = inputSection.getString("id", "");
            inputType = inputSection.getString("type", "minecraft");
            inputAmount = Math.max(1, inputSection.getInt("amount", 1));
        }

        return Optional.of(new RepairConfigData(
                id, itemId, itemType,
                condition, successChance, repairAmount,
                inputId, inputType, inputAmount
        ));
    }

    public void setItemRegistry(ItemProviderRegistry registry) {
        this.itemRegistry = registry;
    }

    public Optional<RepairConfigData> findByItem(ItemStack item) {
        if (item == null || itemRegistry == null) return Optional.empty();

        return repairConfigs.values().stream()
                .filter(config -> itemRegistry.matches(item, config.itemType(), config.itemId()))
                .findFirst();
    }

    public Optional<RepairConfigData> getRepairConfig(String id) {
        return Optional.ofNullable(repairConfigs.get(id));
    }

    public Map<String, RepairConfigData> getRepairConfigs() {
        return new LinkedHashMap<>(repairConfigs);
    }

    public int getRepairConfigCount() {
        return repairConfigs.size();
    }

    public List<String> getWarnings() {
        return new ArrayList<>(loadWarnings);
    }
}