package com.simmc.blacksmith.config;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class FuelConfig {

    private final Map<String, FuelData> fuels;
    private ItemProviderRegistry itemRegistry;

    public FuelConfig() {
        this.fuels = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        fuels.clear();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            String id = section.getString("id", key);
            String type = section.getString("type", "minecraft");
            long burnTime = section.getLong("burn_time", 1600);
            int tempBoost = section.getInt("temperature_boost", 20);

            fuels.put(key, new FuelData(key, id, type, burnTime, tempBoost));
        }
    }

    public void setItemRegistry(ItemProviderRegistry registry) {
        this.itemRegistry = registry;
    }

    public FuelData getFuelData(ItemStack item) {
        if (item == null || itemRegistry == null) {
            return null;
        }

        for (FuelData data : fuels.values()) {
            if (itemRegistry.matches(item, data.type(), data.id())) {
                return data;
            }
        }
        return null;
    }

    public boolean isFuel(ItemStack item) {
        return getFuelData(item) != null;
    }

    public int getFuelCount() {
        return fuels.size();
    }

    public record FuelData(String configKey, String id, String type, long burnTimeTicks, int temperatureBoost) {
    }
}