package com.simmc.blacksmith.items;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class MinecraftItemProvider implements ItemProvider {

    @Override
    public String getType() {
        return "minecraft";
    }

    @Override
    public ItemStack getItem(String id, int amount) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        String materialName = id.toUpperCase().replace("MINECRAFT:", "").replace(":", "_");
        Material material = Material.matchMaterial(materialName);

        if (material == null) {
            material = Material.matchMaterial("minecraft:" + id.toLowerCase());
        }

        if (material == null || material == Material.AIR) {
            return null;
        }

        return new ItemStack(material, Math.max(1, amount));
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        if (item == null || item.getType() == Material.AIR || id == null) {
            return false;
        }

        String materialName = id.toUpperCase().replace("MINECRAFT:", "").replace(":", "_");
        return item.getType().name().equalsIgnoreCase(materialName);
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}