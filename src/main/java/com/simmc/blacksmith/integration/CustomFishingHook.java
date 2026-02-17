package com.simmc.blacksmith.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/**
 * Integration hook for CustomFishing plugin.
 * Uses reflection to avoid hard dependency.
 */
public class CustomFishingHook {

    private final JavaPlugin plugin;
    private boolean available;
    private Object itemManager;
    private Method buildMethod;
    private Method matchMethod;
    private Method getIdMethod;

    public CustomFishingHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = false;
        initialize();
    }

    private void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("CustomFishing")) {
            plugin.getLogger().info("[CustomFishingHook] CustomFishing not found");
            return;
        }

        try {
            // Try to get the CustomFishingPlugin API
            Class<?> apiClass = Class.forName("net.momirealms.customfishing.api.CustomFishingPlugin");
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            Object apiInstance = getInstanceMethod.invoke(null);

            if (apiInstance == null) {
                plugin.getLogger().warning("[CustomFishingHook] CustomFishing API instance is null");
                return;
            }

            // Get ItemManager
            Method getItemManagerMethod = apiClass.getMethod("getItemManager");
            itemManager = getItemManagerMethod.invoke(apiInstance);

            if (itemManager == null) {
                plugin.getLogger().warning("[CustomFishingHook] CustomFishing ItemManager is null");
                return;
            }

            // Get methods
            Class<?> itemManagerClass = itemManager.getClass();
            buildMethod = findMethod(itemManagerClass, "build", String.class);
            matchMethod = findMethod(itemManagerClass, "getItemID", ItemStack.class);
            getIdMethod = matchMethod;

            if (buildMethod == null) {
                // Try alternative method names
                buildMethod = findMethod(itemManagerClass, "buildItem", String.class);
            }

            if (buildMethod != null) {
                available = true;
                plugin.getLogger().info("[CustomFishingHook] Successfully hooked into CustomFishing!");
            } else {
                plugin.getLogger().warning("[CustomFishingHook] Could not find build method in CustomFishing");
            }

        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("[CustomFishingHook] CustomFishing API class not found");
        } catch (Exception e) {
            plugin.getLogger().warning("[CustomFishingHook] Failed to hook: " + e.getMessage());
        }
    }

    private Method findMethod(Class<?> clazz, String name, Class<?>... params) {
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            // Try to find in declared methods
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
            return null;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public ItemStack getItem(String id) {
        if (!available || buildMethod == null || itemManager == null) {
            return null;
        }

        try {
            Object result = buildMethod.invoke(itemManager, id);
            if (result instanceof ItemStack) {
                return (ItemStack) result;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[CustomFishingHook] Failed to get item: " + id + " - " + e.getMessage());
        }

        return null;
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || getIdMethod == null || itemManager == null || item == null) {
            return false;
        }

        try {
            Object result = getIdMethod.invoke(itemManager, item);
            if (result instanceof String) {
                return id.equalsIgnoreCase((String) result);
            }
        } catch (Exception e) {
            // Ignore
        }

        return false;
    }

    public String getItemId(ItemStack item) {
        if (!available || getIdMethod == null || itemManager == null || item == null) {
            return null;
        }

        try {
            Object result = getIdMethod.invoke(itemManager, item);
            if (result instanceof String) {
                return (String) result;
            }
        } catch (Exception e) {
            // Ignore
        }

        return null;
    }
}

