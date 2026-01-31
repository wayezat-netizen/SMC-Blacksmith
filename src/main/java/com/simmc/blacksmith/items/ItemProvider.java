package com.simmc.blacksmith.items;

import org.bukkit.inventory.ItemStack;

public interface ItemProvider {

    String getType();

    ItemStack getItem(String id, int amount);

    boolean matches(ItemStack item, String id);

    boolean isAvailable();
}