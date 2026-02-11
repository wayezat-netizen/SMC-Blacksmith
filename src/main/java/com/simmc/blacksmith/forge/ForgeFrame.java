package com.simmc.blacksmith.forge;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Represents a visual frame (model) shown during forging progress.
 */
public record ForgeFrame(Material material, int customModelData) {

    public ForgeFrame {
        if (material == null) {
            material = Material.IRON_INGOT;
        }
    }

    /**
     * Creates a display item for this frame.
     */
    public ItemStack createDisplayItem() {
        ItemStack item = new ItemStack(material);

        if (customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }

        return item;
    }
}