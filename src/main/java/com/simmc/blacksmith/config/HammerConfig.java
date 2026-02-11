package com.simmc.blacksmith.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Configuration for blacksmith hammer items.
 */
public class HammerConfig {

    private final Map<String, HammerType> hammersById;
    private final Map<String, HammerType> hammersByItemId;

    public HammerConfig() {
        this.hammersById = new LinkedHashMap<>();
        this.hammersByItemId = new HashMap<>();
    }

    public void load(FileConfiguration config) {
        hammersById.clear();
        hammersByItemId.clear();

        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null) continue;

            HammerType type = parseType(id, section);
            hammersById.put(id.toLowerCase(), type);
            hammersByItemId.put(type.itemId().toLowerCase(), type);
        }
    }

    private HammerType parseType(String id, ConfigurationSection section) {
        String itemId = section.getString("id", id);
        String type = section.getString("type", "smc").toLowerCase();
        int maxDurability = Math.max(1, section.getInt("max_durability", 500));
        double speedBonus = clamp(section.getDouble("speed_bonus", 0.0), -1.0, 1.0);
        double accuracyBonus = clamp(section.getDouble("accuracy_bonus", 0.0), -1.0, 1.0);

        return new HammerType(id, itemId, type, maxDurability, speedBonus, accuracyBonus);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Optional<HammerType> getById(String id) {
        return Optional.ofNullable(hammersById.get(id.toLowerCase()));
    }

    public HammerType findByItemId(String itemId) {
        if (itemId == null) return null;
        return hammersByItemId.get(itemId.toLowerCase());
    }

    public Map<String, HammerType> getAllHammerTypes() {
        return new LinkedHashMap<>(hammersById);
    }

    public Collection<HammerType> getAllTypes() {
        return hammersById.values();
    }

    public boolean hasAnyHammer() {
        return !hammersById.isEmpty();
    }

    public int getHammerTypeCount() {
        return hammersById.size();
    }

    /**
     * Hammer type configuration.
     */
    public record HammerType(
            String id,
            String itemId,
            String type,
            int maxDurability,
            double speedBonus,
            double accuracyBonus
    ) {
        public boolean isSMCCore() {
            return "smc".equalsIgnoreCase(type);
        }

        public boolean isVanilla() {
            return "minecraft".equalsIgnoreCase(type);
        }
    }
}