package com.simmc.blacksmith.items;

import com.simmc.blacksmith.integration.CraftEngineHook;
import org.bukkit.inventory.ItemStack;

public class CraftEngineItemProvider implements ItemProvider {

    private final CraftEngineHook craftEngineHook;

    public CraftEngineItemProvider(CraftEngineHook craftEngineHook) {
        this.craftEngineHook = craftEngineHook;
    }

    @Override
    public String getType() {
        return "craftengine";
    }

    @Override
    public ItemStack getItem(String id, int amount) {
        if (!isAvailable()) {
            return null;
        }
        return craftEngineHook.getItem(id, amount);
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        if (!isAvailable()) {
            return false;
        }
        return craftEngineHook.matches(item, id);
    }

    @Override
    public boolean isAvailable() {
        return craftEngineHook != null && craftEngineHook.isAvailable();
    }
}