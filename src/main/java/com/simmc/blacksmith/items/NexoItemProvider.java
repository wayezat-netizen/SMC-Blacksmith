package com.simmc.blacksmith.items;

import com.simmc.blacksmith.integration.NexoHook;
import org.bukkit.inventory.ItemStack;

public class NexoItemProvider implements ItemProvider {

    private final NexoHook nexoHook;

    public NexoItemProvider(NexoHook nexoHook) {
        this.nexoHook = nexoHook;
    }

    @Override
    public String getType() {
        return "nexo";
    }

    @Override
    public ItemStack getItem(String id, int amount) {
        if (!isAvailable()) {
            return null;
        }
        return nexoHook.getItem(id, amount);
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        if (!isAvailable()) {
            return false;
        }
        return nexoHook.matches(item, id);
    }

    @Override
    public boolean isAvailable() {
        return nexoHook != null && nexoHook.isAvailable();
    }
}