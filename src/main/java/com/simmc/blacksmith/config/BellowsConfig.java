package com.simmc.blacksmith.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration for bellows items used to heat furnaces.
 * Each bellows type provides different heat per blow and has durability.
 */
public class BellowsConfig {

    private final Map<String, BellowsType> bellowsById;
    private final Map<String, BellowsType> bellowsByItemId;

    public BellowsConfig() {
        this.bellowsById = new HashMap<>();
        this.bellowsByItemId = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        bellowsById.clear();
        bellowsByItemId.clear();


        // Check for nested 'bellows:' section first, then fall back to root level
        ConfigurationSection bellowsSection = config.getConfigurationSection("bellows");
        if (bellowsSection == null) {
            bellowsSection = config;
        }

        for (String id : bellowsSection.getKeys(false)) {
            ConfigurationSection section = bellowsSection.getConfigurationSection(id);
            if (section == null) continue;

            BellowsType type = parseType(id, section);
            if (type != null) {
                bellowsById.put(id.toLowerCase(), type);
                bellowsByItemId.put(type.itemId().toLowerCase(), type);
            }
        }
    }

    private BellowsType parseType(String id, ConfigurationSection section) {
        String itemId = section.getString("id", id);
        String itemType = section.getString("type", "smc").toLowerCase();
        // Support both heat_boost (current format) and heat_per_blow (legacy)
        int heatPerBlow = Math.max(1, section.getInt("heat_boost",
                section.getInt("heat_per_blow", 10)));
        int maxDurability = Math.max(1, section.getInt("max_durability", 100));
        int cooldownTicks = Math.max(0, section.getInt("cooldown_ticks", 10));
        String sound = section.getString("sound", "block.fire.ambient");
        String particle = section.getString("particle", "FLAME");

        return new BellowsType(id, itemId, itemType, heatPerBlow, maxDurability, cooldownTicks, sound, particle);
    }

    /**
     * Gets bellows type by config ID.
     */
    public Optional<BellowsType> getById(String id) {
        return Optional.ofNullable(bellowsById.get(id.toLowerCase()));
    }

    /**
     * Finds bellows type by item ID (for matching held items).
     */
    public Optional<BellowsType> findByItemId(String itemId) {
        if (itemId == null) return Optional.empty();
        return Optional.ofNullable(bellowsByItemId.get(itemId.toLowerCase()));
    }

    /**
     * Gets all bellows types.
     */
    public Collection<BellowsType> getAllTypes() {
        return bellowsById.values();
    }

    public int getBellowsTypeCount() {
        return bellowsById.size();
    }

    /**
     * Bellows type configuration record.
     */
    public record BellowsType(
            String id,
            String itemId,
            String itemType,
            int heatPerBlow,
            int maxDurability,
            int cooldownTicks,
            String sound,
            String particle
    ) {
        /**
         * Checks if this bellows uses SMCCore items.
         */
        public boolean isSMCCore() {
            return "smc".equalsIgnoreCase(itemType);
        }

        /**
         * Checks if this bellows uses vanilla items.
         */
        public boolean isVanilla() {
            return "minecraft".equalsIgnoreCase(itemType);
        }
    }
}