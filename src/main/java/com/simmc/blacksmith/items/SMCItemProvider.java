package com.simmc.blacksmith.items;

import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.inventory.ItemStack;

public class SMCItemProvider implements ItemProvider {

    private final SMCCoreHook smcHook;

    public SMCItemProvider(SMCCoreHook smcHook) {
        this.smcHook = smcHook;
    }

    @Override
    public String getType() {
        return "smc";
    }

    @Override
    public ItemStack getItem(String id, int amount) {
        if (!isAvailable()) {
            return null;
        }
        return smcHook.getItem(id, amount);
    }

    @Override
    public boolean matches(ItemStack item, String id) {
        if (!isAvailable()) {
            return false;
        }
        return smcHook.matches(item, id);
    }

    @Override
    public boolean isAvailable() {
        return smcHook != null && smcHook.isAvailable();
    }
}