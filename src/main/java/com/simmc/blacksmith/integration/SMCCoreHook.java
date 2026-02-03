package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hook for SMCCore plugin integration.
 * Uses reflection to access the ItemManager API.
 */
public class SMCCoreHook {

    private final JavaPlugin plugin;
    private final boolean available;

    private Object itemManagerInstance;
    private Method getItemMethod;
    private Method getIdMethod;

    public SMCCoreHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("SMCCore") != null;

        if (available) {
            initializeAPI();
        }
    }

    private void initializeAPI() {
        try {
            Class<?> itemManagerClass = Class.forName("com.execsuroot.smccore.item.ItemManager");

            Field itemManagerField = itemManagerClass.getDeclaredField("itemManager");
            itemManagerField.setAccessible(true);
            itemManagerInstance = itemManagerField.get(null);

            if (itemManagerInstance == null) {
                plugin.getLogger().warning("SMCCore ItemManager instance is null");
                return;
            }

            Class<?> instanceClass = itemManagerInstance.getClass();

            // Find getItem method (String -> ItemStack)
            for (Method method : instanceClass.getMethods()) {
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();

                if (params.length == 1 && params[0] == String.class) {
                    if (name.toLowerCase().contains("item") || name.equals("get")) {
                        if (method.getReturnType() != void.class) {
                            getItemMethod = method;
                            break;
                        }
                    }
                }
            }

            // Find getId method (ItemStack -> String)
            for (Method method : instanceClass.getMethods()) {
                Class<?>[] params = method.getParameterTypes();

                if (params.length == 1 && ItemStack.class.isAssignableFrom(params[0])) {
                    if (method.getReturnType() == String.class) {
                        getIdMethod = method;
                        break;
                    }
                }
            }

            if (getItemMethod != null && getIdMethod != null) {
                plugin.getLogger().info("SMCCore API initialized successfully");
            } else {
                plugin.getLogger().warning("SMCCore API partially initialized - some methods not found");
            }

        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("SMCCore ItemManager class not found");
        } catch (NoSuchFieldException e) {
            plugin.getLogger().warning("SMCCore itemManager field not found");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to initialize SMCCore API: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available && itemManagerInstance != null;
    }

    public ItemStack getItem(String id, int amount) {
        if (!isAvailable() || id == null || id.isEmpty() || getItemMethod == null) {
            return null;
        }

        try {
            Object result = getItemMethod.invoke(itemManagerInstance, id);
            if (result instanceof ItemStack item) {
                ItemStack clone = item.clone();
                clone.setAmount(Math.max(1, amount));
                return clone;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get SMCCore item '" + id + "': " + e.getMessage());
        }

        return null;
    }

    public String getItemId(ItemStack item) {
        if (!isAvailable() || item == null || getIdMethod == null) {
            return null;
        }

        try {
            Object result = getIdMethod.invoke(itemManagerInstance, item);
            if (result instanceof String str) {
                return str;
            }
        } catch (Exception e) {
            // Silently fail - item might not be an SMC item
        }

        return null;
    }

    public boolean matches(ItemStack item, String id) {
        if (!isAvailable() || item == null || id == null || id.isEmpty()) {
            return false;
        }
        String itemId = getItemId(item);
        return itemId != null && id.equalsIgnoreCase(itemId);
    }
}