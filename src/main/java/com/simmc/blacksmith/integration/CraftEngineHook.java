package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class CraftEngineHook {

    private final JavaPlugin plugin;
    private final boolean available;
    private Object itemManagerInstance;
    private Method getItemMethod;
    private Method getIdMethod;

    public CraftEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null;

        if (available) {
            initializeAPI();
        }
    }

    private void initializeAPI() {
        String[] possibleClasses = {
                "net.momirealms.craftengine.core.item.ItemManager",
                "net.momirealms.craftengine.bukkit.api.CraftEngineAPI",
                "net.momirealms.craftengine.api.CraftEngineAPI",
                "net.momirealms.craftengine.bukkit.CraftEngineAPI"
        };

        Class<?> apiClass = null;
        for (String className : possibleClasses) {
            try {
                apiClass = Class.forName(className);
                plugin.getLogger().info("CraftEngine: Found API class - " + className);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }

        if (apiClass == null) {
            plugin.getLogger().warning("CraftEngine: No API class found");
            return;
        }

        // Try to get instance (for non-static methods)
        try {
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            itemManagerInstance = getInstanceMethod.invoke(null);
            plugin.getLogger().info("CraftEngine: Got ItemManager instance");
        } catch (Exception ignored) {
            // Static methods only
        }

        // Search for methods
        for (Method method : apiClass.getMethods()) {
            String name = method.getName();
            Class<?>[] params = method.getParameterTypes();

            // Look for getItem method
            if (getItemMethod == null && params.length >= 1 && params[0] == String.class) {
                if (name.toLowerCase().contains("item") || name.toLowerCase().contains("get")) {
                    Class<?> returnType = method.getReturnType();
                    if (ItemStack.class.isAssignableFrom(returnType) || returnType.getName().contains("ItemStack")) {
                        getItemMethod = method;
                        plugin.getLogger().info("CraftEngine: Found getItem method - " + method.getName());
                    }
                }
            }

            // Look for getId method
            if (getIdMethod == null && params.length == 1 && ItemStack.class.isAssignableFrom(params[0])) {
                if (method.getReturnType() == String.class) {
                    getIdMethod = method;
                    plugin.getLogger().info("CraftEngine: Found getId method - " + method.getName());
                }
            }
        }

        // If still no getId, try alternative names
        if (getIdMethod == null) {
            String[] methodNames = {"getItemId", "getId", "getCustomItemId", "getItemKey", "getKey"};
            for (String methodName : methodNames) {
                try {
                    Method method = apiClass.getMethod(methodName, ItemStack.class);
                    if (method.getReturnType() == String.class) {
                        getIdMethod = method;
                        plugin.getLogger().info("CraftEngine: Found getId method - " + methodName);
                        break;
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
        }

        plugin.getLogger().info("CraftEngine API initialized - getItem: " + (getItemMethod != null) + ", getId: " + (getIdMethod != null));
    }

    public boolean isAvailable() {
        return available && getItemMethod != null;
    }

    public ItemStack getItem(String id, int amount) {
        if (!available || id == null || id.isEmpty() || getItemMethod == null) {
            return null;
        }

        try {
            Object result;
            if (java.lang.reflect.Modifier.isStatic(getItemMethod.getModifiers())) {
                result = getItemMethod.invoke(null, id);
            } else if (itemManagerInstance != null) {
                result = getItemMethod.invoke(itemManagerInstance, id);
            } else {
                return null;
            }

            if (result instanceof ItemStack item) {
                ItemStack clone = item.clone();
                clone.setAmount(Math.max(1, amount));
                return clone;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get CraftEngine item '" + id + "': " + e.getMessage());
        }

        return null;
    }

    public String getItemId(ItemStack item) {
        if (!available || item == null || getIdMethod == null) {
            return null;
        }

        try {
            Object result;
            if (java.lang.reflect.Modifier.isStatic(getIdMethod.getModifiers())) {
                result = getIdMethod.invoke(null, item);
            } else if (itemManagerInstance != null) {
                result = getIdMethod.invoke(itemManagerInstance, item);
            } else {
                return null;
            }

            if (result instanceof String str) {
                return str;
            }
        } catch (Exception e) {
            // Silent fail - getId is optional
        }

        return null;
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || item == null || id == null) {
            return false;
        }

        String itemId = getItemId(item);
        if (itemId != null) {
            return id.equalsIgnoreCase(itemId);
        }

        // Fallback: compare by getting the item and checking equality
        ItemStack compareItem = getItem(id, 1);
        if (compareItem != null) {
            return compareItem.isSimilar(item);
        }

        return false;
    }

    public boolean isCraftEngineItem(ItemStack item) {
        return getItemId(item) != null;
    }
}