package com.simmc.blacksmith.items;

import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.NexoHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class ItemProviderRegistry {

    private final JavaPlugin plugin;
    private final Map<String, ItemProvider> providers;

    public ItemProviderRegistry(JavaPlugin plugin, SMCCoreHook smcHook, CraftEngineHook craftEngineHook, NexoHook nexoHook) {
        this.plugin = plugin;
        this.providers = new HashMap<>();

        // Always register minecraft provider
        registerProvider(new MinecraftItemProvider());

        // Register SMCCore provider if available
        if (smcHook != null && smcHook.isAvailable()) {
            registerProvider(new SMCItemProvider(smcHook));
        }

        // Register CraftEngine provider if available
        if (craftEngineHook != null && craftEngineHook.isAvailable()) {
            registerProvider(new CraftEngineItemProvider(craftEngineHook));
        }

        // Register Nexo provider if available
        if (nexoHook != null && nexoHook.isAvailable()) {
            registerProvider(new NexoItemProvider(nexoHook));
        }
    }

    public void registerProvider(ItemProvider provider) {
        providers.put(provider.getType().toLowerCase(), provider);
        plugin.getLogger().info("Registered item provider: " + provider.getType());
    }

    public ItemStack getItem(String type, String id, int amount) {
        if (type == null || id == null) {
            return null;
        }

        ItemProvider provider = providers.get(type.toLowerCase());
        if (provider == null) {
            plugin.getLogger().warning("Unknown item provider: " + type);
            return null;
        }

        if (!provider.isAvailable()) {
            plugin.getLogger().warning("Item provider not available: " + type);
            return null;
        }

        return provider.getItem(id, amount);
    }

    public boolean matches(ItemStack item, String type, String id) {
        if (item == null || type == null || id == null) {
            return false;
        }

        ItemProvider provider = providers.get(type.toLowerCase());
        if (provider == null || !provider.isAvailable()) {
            return false;
        }

        return provider.matches(item, id);
    }

    public boolean hasProvider(String type) {
        ItemProvider provider = providers.get(type.toLowerCase());
        return provider != null && provider.isAvailable();
    }

    public int getProviderCount() {
        return providers.size();
    }
}