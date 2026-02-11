package com.simmc.blacksmith.config;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Configuration for fuel items used in furnaces.
 */
public class FuelConfig {

    private final Map<String, FuelData> fuelsById;
    private volatile ItemProviderRegistry itemRegistry;

    public FuelConfig() {
        this.fuelsById = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        fuelsById.clear();

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) continue;

            FuelData data = parseData(key, section);
            if (data != null) {
                fuelsById.put(key.toLowerCase(), data);
            }
        }
    }

    private FuelData parseData(String key, ConfigurationSection section) {
        String id = section.getString("id", key);
        String type = section.getString("type", "minecraft").toLowerCase();
        long burnTime = Math.max(1, section.getLong("burn_time", 1600));
        int tempBoost = Math.max(1, section.getInt("temperature_boost", 20));

        return new FuelData(key, id, type, burnTime, tempBoost);
    }

    public void setItemRegistry(ItemProviderRegistry registry) {
        this.itemRegistry = registry;
    }

    /**
     * Gets fuel data for an item stack.
     */
    public Optional<FuelData> getFuelData(ItemStack item) {
        if (item == null || item.getType().isAir() || itemRegistry == null) {
            return Optional.empty();
        }

        for (FuelData data : fuelsById.values()) {
            if (itemRegistry.matches(item, data.type(), data.id())) {
                return Optional.of(data);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if an item is valid fuel.
     */
    public boolean isFuel(ItemStack item) {
        return getFuelData(item).isPresent();
    }

    /**
     * Gets fuel by config key.
     */
    public Optional<FuelData> getById(String key) {
        return Optional.ofNullable(fuelsById.get(key.toLowerCase()));
    }

    /**
     * Gets all fuel types.
     */
    public Collection<FuelData> getAllFuels() {
        return fuelsById.values();
    }

    public int getFuelCount() {
        return fuelsById.size();
    }

    /**
     * Fuel data record.
     */
    public record FuelData(
            String configKey,
            String id,
            String type,
            long burnTimeTicks,
            int temperatureBoost
    ) {
        /**
         * Checks if this fuel uses vanilla items.
         */
        public boolean isVanilla() {
            return "minecraft".equalsIgnoreCase(type);
        }
    }
}