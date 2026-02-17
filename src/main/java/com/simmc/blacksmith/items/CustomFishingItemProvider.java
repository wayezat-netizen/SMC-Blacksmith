package com.simmc.blacksmith.items;

import com.simmc.blacksmith.integration.CustomFishingHook;
import org.bukkit.inventory.ItemStack;

/**
 * Item provider for CustomFishing items.
 */
public class CustomFishingItemProvider implements ItemProvider {

    private final CustomFishingHook hook;

    public CustomFishingItemProvider(CustomFishingHook hook) {
        this.hook = hook;
    }

    @Override
    public String getType() {
        return "customfishing";
    }

    @Override
    public boolean isAvailable() {
        return hook != null && hook.isAvailable();
    }

    @Override
    public ItemStack getItem(String id, int amount) {
        if (!isAvailable() || id == null || id.isEmpty()) {
            return null;
        }

        ItemStack item = hook.getItem(id);
        if (item != null && amount > 1) {
            item.setAmount(amount);
        }
        return item;
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        if (!isAvailable() || item == null || id == null) {
            return false;
        }

        return hook.matches(item, id);
    }
}

