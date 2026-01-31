package com.simmc.blacksmith.forge;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public record ForgeFrame(
        Material material,
        int customModelData
) {
    public ItemStack createDisplayItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(customModelData);
            item.setItemMeta(meta);
        }
        return item;
    }
}