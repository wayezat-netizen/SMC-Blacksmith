package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CraftEngineHook {

    private final JavaPlugin plugin;
    private final boolean available;

    // CraftEngine uses a different structure
    private Object itemManagerInstance;
    private Method buildItemMethod;
    private Method getCustomIdMethod;

    public CraftEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null;

        if (available) {
            initializeAPI();
        }
    }

    private void initializeAPI() {
        try {
            // Try to get the BukkitCraftEngine instance first
            Class<?> bukkitCraftEngineClass = Class.forName("net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine");
            Method getInstanceMethod = bukkitCraftEngineClass.getMethod("instance");
            Object craftEngineInstance = getInstanceMethod.invoke(null);

            if (craftEngineInstance != null) {
                plugin.getLogger().info("CraftEngine: Got BukkitCraftEngine instance");

                // Get ItemManager from the instance
                Method itemManagerMethod = bukkitCraftEngineClass.getMethod("itemManager");
                itemManagerInstance = itemManagerMethod.invoke(craftEngineInstance);

                if (itemManagerInstance != null) {
                    plugin.getLogger().info("CraftEngine: Got ItemManager instance");
                    findMethods(itemManagerInstance.getClass());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().info("CraftEngine: BukkitCraftEngine not found, trying alternative...");
            tryAlternativeAPI();
        }

        plugin.getLogger().info("CraftEngine API initialized - buildItem: " + (buildItemMethod != null) +
                ", getCustomId: " + (getCustomIdMethod != null));
    }

    private void tryAlternativeAPI() {
        // Try CraftEngineItems utility class
        String[] utilityClasses = {
                "net.momirealms.craftengine.bukkit.util.CraftEngineItems",
                "net.momirealms.craftengine.bukkit.item.CraftEngineItems",
                "net.momirealms.craftengine.bukkit.api.CraftEngineItems"
        };

        for (String className : utilityClasses) {
            try {
                Class<?> itemsClass = Class.forName(className);
                plugin.getLogger().info("CraftEngine: Found utility class - " + className);
                findStaticMethods(itemsClass);
                if (buildItemMethod != null || getCustomIdMethod != null) {
                    return;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }

        // Try direct ItemManager access
        String[] managerClasses = {
                "net.momirealms.craftengine.core.item.ItemManager",
                "net.momirealms.craftengine.bukkit.item.BukkitItemManager"
        };

        for (String className : managerClasses) {
            try {
                Class<?> managerClass = Class.forName(className);
                plugin.getLogger().info("CraftEngine: Found manager class - " + className);

                // Try to get singleton instance
                for (Field field : managerClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                            managerClass.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        itemManagerInstance = field.get(null);
                        if (itemManagerInstance != null) {
                            plugin.getLogger().info("CraftEngine: Got manager instance from field");
                            findMethods(itemManagerInstance.getClass());
                            return;
                        }
                    }
                }

                // Try getInstance method
                try {
                    Method getInstance = managerClass.getMethod("getInstance");
                    itemManagerInstance = getInstance.invoke(null);
                    if (itemManagerInstance != null) {
                        findMethods(itemManagerInstance.getClass());
                        return;
                    }
                } catch (NoSuchMethodException ignored) {
                }

            } catch (Exception ignored) {
            }
        }
    }

    private void findMethods(Class<?> clazz) {
        // Log all available methods for debugging
        plugin.getLogger().info("CraftEngine: Searching methods in " + clazz.getName());

        for (Method method : clazz.getMethods()) {
            String name = method.getName().toLowerCase();
            Class<?>[] params = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();

            // Look for build/create item method (takes String id, returns ItemStack or wrapper)
            if (buildItemMethod == null) {
                if ((name.contains("build") || name.contains("create") || name.contains("get")) &&
                        name.contains("item")) {
                    if (params.length >= 1 && params[0] == String.class) {
                        buildItemMethod = method;
                        plugin.getLogger().info("CraftEngine: Found build method - " + method.getName() +
                                " returns " + returnType.getSimpleName());
                    }
                }
            }

            // Look for getId method (takes ItemStack, returns String)
            if (getCustomIdMethod == null) {
                if ((name.contains("id") || name.contains("key") || name.contains("custom"))) {
                    if (params.length == 1 && ItemStack.class.isAssignableFrom(params[0])) {
                        if (returnType == String.class || returnType.getName().contains("Optional") ||
                                returnType.getName().contains("Key")) {
                            getCustomIdMethod = method;
                            plugin.getLogger().info("CraftEngine: Found getId method - " + method.getName() +
                                    " returns " + returnType.getSimpleName());
                        }
                    }
                }
            }
        }

        // If still no methods, try broader search
        if (buildItemMethod == null || getCustomIdMethod == null) {
            for (Method method : clazz.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                Class<?> returnType = method.getReturnType();

                // Any method that takes String and returns something item-like
                if (buildItemMethod == null && params.length == 1 && params[0] == String.class) {
                    String returnName = returnType.getName().toLowerCase();
                    if (returnName.contains("item") || returnName.contains("stack")) {
                        buildItemMethod = method;
                        plugin.getLogger().info("CraftEngine: Found potential build method - " + method.getName());
                    }
                }

                // Any method that takes ItemStack and returns String/Optional<String>
                if (getCustomIdMethod == null && params.length == 1 &&
                        ItemStack.class.isAssignableFrom(params[0])) {
                    if (returnType == String.class) {
                        getCustomIdMethod = method;
                        plugin.getLogger().info("CraftEngine: Found potential getId method - " + method.getName());
                    }
                }
            }
        }
    }

    private void findStaticMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) continue;

            String name = method.getName().toLowerCase();
            Class<?>[] params = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();

            // Static getItem/buildItem method
            if (buildItemMethod == null && params.length >= 1 && params[0] == String.class) {
                if (ItemStack.class.isAssignableFrom(returnType)) {
                    buildItemMethod = method;
                    plugin.getLogger().info("CraftEngine: Found static build method - " + method.getName());
                }
            }

            // Static getId method
            if (getCustomIdMethod == null && params.length == 1 &&
                    ItemStack.class.isAssignableFrom(params[0])) {
                if (returnType == String.class) {
                    getCustomIdMethod = method;
                    plugin.getLogger().info("CraftEngine: Found static getId method - " + method.getName());
                }
            }
        }
    }

    public boolean isAvailable() {
        return available && (buildItemMethod != null || getCustomIdMethod != null);
    }

    public ItemStack getItem(String id, int amount) {
        if (!available || id == null || id.isEmpty() || buildItemMethod == null) {
            return null;
        }

        try {
            Object result;
            if (java.lang.reflect.Modifier.isStatic(buildItemMethod.getModifiers())) {
                result = buildItemMethod.invoke(null, id);
            } else if (itemManagerInstance != null) {
                result = buildItemMethod.invoke(itemManagerInstance, id);
            } else {
                return null;
            }

            ItemStack item = extractItemStack(result);
            if (item != null) {
                ItemStack clone = item.clone();
                clone.setAmount(Math.max(1, amount));
                return clone;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get CraftEngine item '" + id + "': " + e.getMessage());
        }

        return null;
    }

    /**
     * Extracts ItemStack from various wrapper types CraftEngine might use.
     */
    private ItemStack extractItemStack(Object result) {
        if (result == null) return null;

        if (result instanceof ItemStack) {
            return (ItemStack) result;
        }

        // Try to call load(), build(), or getItemStack() on wrapper objects
        String[] extractMethods = {"load", "build", "getItemStack", "get", "toItemStack"};
        for (String methodName : extractMethods) {
            try {
                Method method = result.getClass().getMethod(methodName);
                Object extracted = method.invoke(result);
                if (extracted instanceof ItemStack) {
                    return (ItemStack) extracted;
                }
            } catch (Exception ignored) {
            }
        }

        // Try no-arg methods that return ItemStack
        for (Method method : result.getClass().getMethods()) {
            if (method.getParameterCount() == 0 &&
                    ItemStack.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Object extracted = method.invoke(result);
                    if (extracted instanceof ItemStack) {
                        return (ItemStack) extracted;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        return null;
    }

    public String getItemId(ItemStack item) {
        if (!available || item == null || getCustomIdMethod == null) {
            return null;
        }

        try {
            Object result;
            if (java.lang.reflect.Modifier.isStatic(getCustomIdMethod.getModifiers())) {
                result = getCustomIdMethod.invoke(null, item);
            } else if (itemManagerInstance != null) {
                result = getCustomIdMethod.invoke(itemManagerInstance, item);
            } else {
                return null;
            }

            return extractString(result);
        } catch (Exception e) {
            // Silent fail - getId is optional
        }

        return null;
    }

    /**
     * Extracts String from various types (Optional, Key objects, etc.)
     */
    private String extractString(Object result) {
        if (result == null) return null;

        if (result instanceof String) {
            return (String) result;
        }

        // Handle Optional<String>
        if (result.getClass().getName().contains("Optional")) {
            try {
                Method isPresent = result.getClass().getMethod("isPresent");
                Method get = result.getClass().getMethod("get");
                if ((Boolean) isPresent.invoke(result)) {
                    Object value = get.invoke(result);
                    if (value instanceof String) {
                        return (String) value;
                    }
                    return value != null ? value.toString() : null;
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        // Handle Key objects (namespace:value format)
        try {
            Method asString = result.getClass().getMethod("asString");
            return (String) asString.invoke(result);
        } catch (Exception ignored) {
        }

        try {
            Method toString = result.getClass().getMethod("toString");
            return (String) toString.invoke(result);
        } catch (Exception ignored) {
        }

        return result.toString();
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || item == null || id == null) {
            return false;
        }

        String itemId = getItemId(item);
        if (itemId != null) {
            // Handle both "namespace:id" and just "id" formats
            if (id.equalsIgnoreCase(itemId)) {
                return true;
            }
            // Check if id matches the value part of "namespace:id"
            if (itemId.contains(":")) {
                String valueOnly = itemId.substring(itemId.indexOf(':') + 1);
                if (id.equalsIgnoreCase(valueOnly)) {
                    return true;
                }
            }
            // Check if itemId matches the value part of "namespace:id" in input
            if (id.contains(":")) {
                String valueOnly = id.substring(id.indexOf(':') + 1);
                if (itemId.equalsIgnoreCase(valueOnly)) {
                    return true;
                }
            }
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