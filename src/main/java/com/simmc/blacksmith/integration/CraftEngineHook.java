package com.simmc.blacksmith.integration;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CraftEngineHook {

    private final JavaPlugin plugin;
    private final boolean available;
    private boolean initialized;

    // Cached reflection objects
    private Object itemManagerInstance;
    private Method buildItemMethod;
    private Method getCustomIdMethod;
    private boolean isStaticBuild;
    private boolean isStaticGetId;

    // Item ID cache to reduce reflection calls
    private final Map<Integer, String> itemIdCache;
    private static final int MAX_CACHE_SIZE = 500;

    public CraftEngineHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("CraftEngine") != null;
        this.itemIdCache = new ConcurrentHashMap<>();
        this.initialized = false;
    }

    private void ensureInitialized() {
        if (initialized || !available) return;
        initialized = true;
        initializeAPI();
    }

    private void initializeAPI() {
        // Try BukkitCraftEngine first
        if (tryBukkitCraftEngine()) return;

        // Try utility classes
        if (tryUtilityClasses()) return;

        // Try direct manager access
        tryManagerClasses();

        logInitResult();
    }

    private boolean tryBukkitCraftEngine() {
        try {
            Class<?> bukkitCEClass = Class.forName("net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine");
            Method getInstance = bukkitCEClass.getMethod("instance");
            Object ceInstance = getInstance.invoke(null);

            if (ceInstance != null) {
                Method itemManagerMethod = bukkitCEClass.getMethod("itemManager");
                itemManagerInstance = itemManagerMethod.invoke(ceInstance);

                if (itemManagerInstance != null) {
                    findMethods(itemManagerInstance.getClass());
                    return buildItemMethod != null || getCustomIdMethod != null;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private boolean tryUtilityClasses() {
        String[] utilityClasses = {
                "net.momirealms.craftengine.bukkit.util.CraftEngineItems",
                "net.momirealms.craftengine.bukkit.item.CraftEngineItems",
                "net.momirealms.craftengine.bukkit.api.CraftEngineItems"
        };

        for (String className : utilityClasses) {
            try {
                Class<?> itemsClass = Class.forName(className);
                findStaticMethods(itemsClass);
                if (buildItemMethod != null || getCustomIdMethod != null) {
                    return true;
                }
            } catch (ClassNotFoundException ignored) {}
        }
        return false;
    }

    private void tryManagerClasses() {
        String[] managerClasses = {
                "net.momirealms.craftengine.core.item.ItemManager",
                "net.momirealms.craftengine.bukkit.item.BukkitItemManager"
        };

        for (String className : managerClasses) {
            try {
                Class<?> managerClass = Class.forName(className);

                // Try getInstance method
                try {
                    Method getInstance = managerClass.getMethod("getInstance");
                    itemManagerInstance = getInstance.invoke(null);
                    if (itemManagerInstance != null) {
                        findMethods(itemManagerInstance.getClass());
                        if (buildItemMethod != null || getCustomIdMethod != null) {
                            return;
                        }
                    }
                } catch (NoSuchMethodException ignored) {}

            } catch (Exception ignored) {}
        }
    }

    private void findMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            String name = method.getName().toLowerCase();
            Class<?>[] params = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();

            // Find build method
            if (buildItemMethod == null && params.length >= 1 && params[0] == String.class) {
                if (name.contains("build") || name.contains("create") || name.contains("item")) {
                    buildItemMethod = method;
                    isStaticBuild = Modifier.isStatic(method.getModifiers());
                }
            }

            // Find getId method
            if (getCustomIdMethod == null && params.length == 1 &&
                    ItemStack.class.isAssignableFrom(params[0])) {
                if (returnType == String.class ||
                        returnType.getName().contains("Optional") ||
                        returnType.getName().contains("Key")) {
                    getCustomIdMethod = method;
                    isStaticGetId = Modifier.isStatic(method.getModifiers());
                }
            }

            if (buildItemMethod != null && getCustomIdMethod != null) break;
        }
    }

    private void findStaticMethods(Class<?> clazz) {
        for (Method method : clazz.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers())) continue;

            Class<?>[] params = method.getParameterTypes();
            Class<?> returnType = method.getReturnType();

            // Static getItem/buildItem
            if (buildItemMethod == null && params.length >= 1 && params[0] == String.class) {
                if (ItemStack.class.isAssignableFrom(returnType)) {
                    buildItemMethod = method;
                    isStaticBuild = true;
                }
            }

            // Static getId
            if (getCustomIdMethod == null && params.length == 1 &&
                    ItemStack.class.isAssignableFrom(params[0])) {
                if (returnType == String.class) {
                    getCustomIdMethod = method;
                    isStaticGetId = true;
                }
            }
        }
    }

    private void logInitResult() {
        plugin.getLogger().info("CraftEngine API initialized - buildItem: " +
                (buildItemMethod != null) + ", getCustomId: " + (getCustomIdMethod != null));
    }

    public boolean isAvailable() {
        ensureInitialized();
        return available && (buildItemMethod != null || getCustomIdMethod != null);
    }

    public ItemStack getItem(String id, int amount) {
        ensureInitialized();

        if (!available || id == null || id.isEmpty() || buildItemMethod == null) {
            return null;
        }

        try {
            Object result;
            if (isStaticBuild) {
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

    private ItemStack extractItemStack(Object result) {
        if (result == null) return null;
        if (result instanceof ItemStack) return (ItemStack) result;

        // Try common extraction methods
        String[] methods = {"load", "build", "getItemStack", "get", "toItemStack"};
        for (String methodName : methods) {
            try {
                Method method = result.getClass().getMethod(methodName);
                Object extracted = method.invoke(result);
                if (extracted instanceof ItemStack) {
                    return (ItemStack) extracted;
                }
            } catch (Exception ignored) {}
        }

        // Try any no-arg method returning ItemStack
        for (Method method : result.getClass().getMethods()) {
            if (method.getParameterCount() == 0 &&
                    ItemStack.class.isAssignableFrom(method.getReturnType())) {
                try {
                    Object extracted = method.invoke(result);
                    if (extracted instanceof ItemStack) {
                        return (ItemStack) extracted;
                    }
                } catch (Exception ignored) {}
            }
        }

        return null;
    }

    public String getItemId(ItemStack item) {
        ensureInitialized();

        if (!available || item == null || getCustomIdMethod == null) {
            return null;
        }

        // Check cache first
        int hashCode = System.identityHashCode(item);
        String cached = itemIdCache.get(hashCode);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        try {
            Object result;
            if (isStaticGetId) {
                result = getCustomIdMethod.invoke(null, item);
            } else if (itemManagerInstance != null) {
                result = getCustomIdMethod.invoke(itemManagerInstance, item);
            } else {
                return null;
            }

            String id = extractString(result);

            // Cache result (empty string for null)
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

        // Handle Key objects
        try {
            Method asString = result.getClass().getMethod("asString");
            return (String) asString.invoke(result);
        } catch (Exception ignored) {}

        return result.toString();
    }

    public boolean matches(ItemStack item, String id) {
        if (!available || item == null || id == null) {
            return false;
        }

        String itemId = getItemId(item);
        if (itemId == null) return false;

        // Direct match
        if (id.equalsIgnoreCase(itemId)) return true;

        // Match without namespace
        if (itemId.contains(":")) {
            String valueOnly = itemId.substring(itemId.indexOf(':') + 1);
            if (id.equalsIgnoreCase(valueOnly)) return true;
        }
        if (id.contains(":")) {
            String valueOnly = id.substring(id.indexOf(':') + 1);
            if (itemId.equalsIgnoreCase(valueOnly)) return true;
        }

        return false;
    }

    public boolean isCraftEngineItem(ItemStack item) {
        return getItemId(item) != null;
    }

    public void clearCache() {
        itemIdCache.clear();
    }
}