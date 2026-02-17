package com.simmc.blacksmith.items;

import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.CustomFishingHook;
import com.simmc.blacksmith.integration.NexoHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class ItemProviderRegistry {

    private final JavaPlugin plugin;
    private final Map<String, ItemProvider> providers;

    // Type aliases for convenience (e.g., "ce" -> "craftengine")
    private static final Map<String, String> TYPE_ALIASES = Map.of(
            "ce", "craftengine",
            "craft_engine", "craftengine",
            "smccore", "smc",
            "vanilla", "minecraft",
            "mc", "minecraft"
    );

    public ItemProviderRegistry(JavaPlugin plugin, SMCCoreHook smcHook, CraftEngineHook craftEngineHook,
                                NexoHook nexoHook, CustomFishingHook customFishingHook) {
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

        // Register CustomFishing provider if available
        if (customFishingHook != null && customFishingHook.isAvailable()) {
            registerProvider(new CustomFishingItemProvider(customFishingHook));
        }
    }

    public void registerProvider(ItemProvider provider) {
        providers.put(provider.getType().toLowerCase(), provider);
        plugin.getLogger().info("Registered item provider: " + provider.getType());
    }

    /**
     * Resolves type aliases to canonical type names.
     */
    private String resolveType(String type) {
        if (type == null) return null;
        String lower = type.toLowerCase();
        return TYPE_ALIASES.getOrDefault(lower, lower);
    }

    public ItemStack getItem(String type, String id, int amount) {
        if (type == null || type.isEmpty()) {
            plugin.getLogger().warning("getItem called with null/empty type");
            return null;
        }
        if (id == null || id.isEmpty()) {
            plugin.getLogger().warning("getItem called with null/empty id");
            return null;
        }
        if (amount < 1) {
            amount = 1;
        }

        String resolvedType = resolveType(type);
        ItemProvider provider = providers.get(resolvedType);
        if (provider == null) {
            plugin.getLogger().warning("Unknown item provider: " + type + " (resolved: " + resolvedType + ")");
            return null;
        }

        if (!provider.isAvailable()) {
            plugin.getLogger().warning("Item provider not available: " + type);
            return null;
        }

        try {
            return provider.getItem(id, amount);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get item " + type + ":" + id + " - " + e.getMessage());
            return null;
        }
    }

    public boolean matches(ItemStack item, String type, String id) {
        if (item == null || type == null || id == null) {
            return false;
        }

        String resolvedType = resolveType(type);
        ItemProvider provider = providers.get(resolvedType);
        if (provider == null || !provider.isAvailable()) {
            return false;
        }

        return provider.matches(item, id);
    }

    public boolean hasProvider(String type) {
        String resolvedType = resolveType(type);
        ItemProvider provider = providers.get(resolvedType);
        return provider != null && provider.isAvailable();
    }

    public int getProviderCount() {
        return providers.size();
    }
}