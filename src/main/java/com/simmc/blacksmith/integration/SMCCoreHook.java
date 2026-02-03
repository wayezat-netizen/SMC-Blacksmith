package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SMCCoreHook {

    private final JavaPlugin plugin;
    private final boolean available;
    private boolean initialized;

    // Cached reflection objects
    private Object itemManagerInstance;
    private Method getItemMethod;
    private Method getItemIdMethod;
    private Method matchesMethod;
    private boolean isStaticGetItem;
    private boolean isStaticGetId;

    // Item ID cache
    private final Map<Integer, String> itemIdCache;
    private static final int MAX_CACHE_SIZE = 500;

    public SMCCoreHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("SMCCore") != null;
        this.itemIdCache = new ConcurrentHashMap<>();
        this.initialized = false;
    }

    private void ensureInitialized() {
        if (initialized || !available) return;
        initialized = true;
        initializeAPI();
    }

    private void initializeAPI() {
        String[] apiClasses = {
                "com.simmc.core.api.SMCCoreAPI",
                "com.simmc.core.SMCCore",
                "com.simmc.core.item.ItemManager"
        };

        for (String className : apiClasses) {
            try {
                Class<?> apiClass = Class.forName(className);

                // Try to get instance
                try {
                    Method getInstance = apiClass.getMethod("getInstance");
                    itemManagerInstance = getInstance.invoke(null);
                } catch (NoSuchMethodException e) {
                    // Try static methods
                }

                findMethods(apiClass);

                if (getItemMethod != null || getItemIdMethod != null) {
                    plugin.getLogger().info("SMCCore hooked via " + className);
                    return;
                }
            } catch (ClassNotFoundException ignored) {}
            catch (Exception e) {
                plugin.getLogger().warning("Failed to hook SMCCore via " + className + ": " + e.getMessage());
            }
        }

        plugin.getLogger().info("SMCCore API initialized - getItem: " +
                (getItemMethod != null) + ", getItemId: " + (getItemIdMethod != null));
    }

    private void findMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String name = method.getName().toLowerCase();
            Class<?>[] params = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();

            // Find getItem(String id) or getItem(String id, int amount)
            if (getItemMethod == null) {
                if ((name.equals("getitem") || name.equals("builditem") || name.equals("createitem"))) {
                    if (params.length >= 1 && params[0] == String.class &&
                            ItemStack.class.isAssignableFrom(returnType)) {
                        getItemMethod = method;
                        isStaticGetItem = Modifier.isStatic(method.getModifiers());
                    }
                }
            }

            // Find getItemId(ItemStack) or getId(ItemStack)
            if (getItemIdMethod == null) {
                if ((name.equals("getitemid") || name.equals("getid") || name.equals("getcustomid"))) {
                    if (params.length == 1 && ItemStack.class.isAssignableFrom(params[0]) &&
                            returnType == String.class) {
                        getItemIdMethod = method;
                        isStaticGetId = Modifier.isStatic(method.getModifiers());
                    }
                }
            }

            // Find matches method
            if (matchesMethod == null && name.equals("matches")) {
                if (params.length == 2 &&
                        ItemStack.class.isAssignableFrom(params[0]) &&
                        params[1] == String.class &&
                        returnType == boolean.class) {
                    matchesMethod = method;
                }
            }
        }
    }

    public boolean isAvailable() {
        ensureInitialized();
        return available && (getItemMethod != null || getItemIdMethod != null);
    }

    public ItemStack getItem(String id, int amount) {
        ensureInitialized();

        if (!available || id == null || id.isEmpty() || getItemMethod == null) {
            return null;
        }

        try {
            Object result;
            Class<?>[] params = getItemMethod.getParameterTypes();

            if (isStaticGetItem) {
                if (params.length >= 2 && params[1] == int.class) {
                    result = getItemMethod.invoke(null, id, amount);
                } else {
                    result = getItemMethod.invoke(null, id);
                }
            } else if (itemManagerInstance != null) {
                if (params.length >= 2 && params[1] == int.class) {
                    result = getItemMethod.invoke(itemManagerInstance, id, amount);
                } else {
                    result = getItemMethod.invoke(itemManagerInstance, id);
                }
            } else {
                return null;
            }

            if (result instanceof ItemStack item) {
                ItemStack clone = item.clone();
                if (params.length < 2) {
                    clone.setAmount(Math.max(1, amount));
                }
                return clone;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get SMCCore item '" + id + "': " + e.getMessage());
        }

        return null;
    }

    public String getItemId(ItemStack item) {
        ensureInitialized();

        if (!available || item == null || getItemIdMethod == null) {
            return null;
        }

        // Check cache
        int hashCode = System.identityHashCode(item);
        String cached = itemIdCache.get(hashCode);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        try {
            Object result;
            if (isStaticGetId) {
                result = getItemIdMethod.invoke(null, item);
            } else if (itemManagerInstance != null) {
                result = getItemIdMethod.invoke(itemManagerInstance, item);
            } else {
                return null;
            }

            String id = result instanceof String ? (String) result : null;

            // Cache result
            if (itemIdCache.size() < MAX_CACHE_SIZE) {
                itemIdCache.put(hashCode, id != null ? id : "");
            }

            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || item == null || id == null) {
            return false;
        }

        // Try native matches method first
        if (matchesMethod != null) {
            try {
                Object result;
                if (Modifier.isStatic(matchesMethod.getModifiers())) {
                    result = matchesMethod.invoke(null, item, id);
                } else if (itemManagerInstance != null) {
                    result = matchesMethod.invoke(itemManagerInstance, item, id);
                } else {
                    result = null;
                }

                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            } catch (Exception ignored) {}
        }

        // Fallback to ID comparison
        String itemId = getItemId(item);
        return itemId != null && itemId.equalsIgnoreCase(id);
    }

    public boolean isSMCCoreItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public void clearCache() {
        itemIdCache.clear();
    }
}