package com.simmc.blacksmith.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Item provider for vanilla Minecraft items.
 */
public class MinecraftItemProvider implements ItemProvider {

    @Override
    public String getType() {
        return "minecraft";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ItemStack getItem(String id, int amount) {
        if (id == null || id.isEmpty()) return null;

        // Try exact match first (uppercase)
        Material material = Material.getMaterial(id.toUpperCase());

        // Try with underscores replaced
        if (material == null) {
            material = Material.getMaterial(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        }

        // Try matching by name contains (for partial matches)
        if (material == null) {
            String upperId = id.toUpperCase();
            for (Material m : Material.values()) {
                if (m.name().equalsIgnoreCase(upperId)) {
                    material = m;
                    break;
                }
            }
        }

        if (material == null || material.isAir()) {
            return null;
        }

        return new ItemStack(material, Math.max(1, amount));
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        if (item == null || id == null || item.getType().isAir()) {
            return false;
        }

        // Normalize both to uppercase for comparison
        String itemMaterialName = item.getType().name().toUpperCase();
        String targetId = id.toUpperCase().replace("-", "_").replace(" ", "_");

        // Exact match
        if (itemMaterialName.equals(targetId)) {
            return true;
        }

        // Also check without underscores (raw_iron vs RAWIRON)
        String itemNoUnderscore = itemMaterialName.replace("_", "");
        String targetNoUnderscore = targetId.replace("_", "");

        return itemNoUnderscore.equals(targetNoUnderscore);
    }
}