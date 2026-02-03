package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NexoHook {

    private final JavaPlugin plugin;
    private final boolean available;
    private boolean initialized;

    // Cached reflection objects
    private Method getItemMethod;
    private Method getItemIdMethod;
    private Object apiInstance;
    private boolean isStaticGetItem;
    private boolean isStaticGetId;

    // Item ID cache
    private final Map<Integer, String> itemIdCache;
    private static final int MAX_CACHE_SIZE = 500;

    public NexoHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("Nexo") != null;
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
                "com.nexomc.nexo.api.NexoItems",
                "com.nexomc.nexo.items.NexoItems",
                "io.th0rgal.nexo.api.NexoItems"
        };

        for (String className : apiClasses) {
            try {
                Class<?> apiClass = Class.forName(className);
                findMethods(apiClass);

                if (getItemMethod != null || getItemIdMethod != null) {
                    plugin.getLogger().info("Nexo hooked via " + className);
                    return;
                }
            } catch (ClassNotFoundException ignored) {}
        }

        plugin.getLogger().info("Nexo API initialized - getItem: " +
                (getItemMethod != null) + ", getItemId: " + (getItemIdMethod != null));
    }

    private void findMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String name = method.getName().toLowerCase();
            Class<?>[] params = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();
            boolean isStatic = Modifier.isStatic(method.getModifiers());

            // Find itemFromId or similar
            if (getItemMethod == null) {
                if (name.contains("item") && name.contains("from") ||
                        name.equals("getitem") || name.equals("builditem")) {
                    if (params.length >= 1 && params[0] == String.class) {
                        getItemMethod = method;
                        isStaticGetItem = isStatic;
                    }
                }
            }

            // Find idFromItem or similar
            if (getItemIdMethod == null) {
                if (name.contains("id") && name.contains("from") ||
                        name.equals("getid") || name.equals("getitemid")) {
                    if (params.length == 1 && ItemStack.class.isAssignableFrom(params[0])) {
                        getItemIdMethod = method;
                        isStaticGetId = isStatic;
                    }
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
            if (isStaticGetItem) {
                result = getItemMethod.invoke(null, id);
            } else if (apiInstance != null) {
                result = getItemMethod.invoke(apiInstance, id);
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
            plugin.getLogger().warning("Failed to get Nexo item '" + id + "': " + e.getMessage());
        }

        return null;
    }

    private ItemStack extractItemStack(Object result) {
        if (result == null) return null;
        if (result instanceof ItemStack) return (ItemStack) result;

        // Try build method (NexoItems often returns a builder)
        try {
            Method build = result.getClass().getMethod("build");
            Object built = build.invoke(result);
            if (built instanceof ItemStack) {
                return (ItemStack) built;
            }
        } catch (Exception ignored) {}

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
            } else if (apiInstance != null) {
                result = getItemIdMethod.invoke(apiInstance, item);
            } else {
                return null;
            }

            String id = extractString(result);

            // Cache result
            if (itemIdCache.size() < MAX_CACHE_SIZE) {
                itemIdCache.put(hashCode, id != null ? id : "");
            }

            return id;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractString(Object result) {
        if (result == null) return null;
        if (result instanceof String) return (String) result;

        // Handle Optional
        if (result.getClass().getName().contains("Optional")) {
            try {
                Method isPresent = result.getClass().getMethod("isPresent");
                if ((Boolean) isPresent.invoke(result)) {
                    Method get = result.getClass().getMethod("get");
                    Object value = get.invoke(result);
                    return value instanceof String ? (String) value : value.toString();
                }
            } catch (Exception ignored) {}
            return null;
        }

        return result.toString();
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || item == null || id == null) {
            return false;
        }

        String itemId = getItemId(item);
        return itemId != null && itemId.equalsIgnoreCase(id);
    }

    public boolean isNexoItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public void clearCache() {
        itemIdCache.clear();
    }
}