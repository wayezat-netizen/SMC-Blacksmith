package com.simmc.blacksmith.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hook for SMCCore plugin integration.
 * Supports SMCCore's ItemManager API using MethodHandles for better module access.
 */
public class SMCCoreHook {

    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean available;
    private boolean initialized;

    private Object itemManagerInstance;

    // MethodHandles for better module access
    private MethodHandle composeByIdHandle;
    private MethodHandle getIdByItemStackOrNullHandle;
    private MethodHandle hasIdHandle;

    // Fallback to reflection if MethodHandles fail
    private Method composeByIdMethod;
    private Method getIdByItemStackOrNullMethod;
    private Method hasIdMethod;

    public SMCCoreHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.available = false;
        this.initialized = false;

        initialize();

        if (!available) {
            Bukkit.getScheduler().runTaskLater(plugin, this::initializeDelayed, 1L);
        }
    }

    private void initializeDelayed() {
        if (initialized && available) return;
        initialize();
        if (available) {
            logger.info("[SMCCoreHook] Initialized (delayed)");
        }
    }

    private void initialize() {
        Plugin smcCore = findSmcCorePlugin();
        if (smcCore == null || !smcCore.isEnabled()) {
            return;
        }

        initialized = true;

        try {
            // Get ItemManager class and static field
            Class<?> itemManagerClass = Class.forName("com.execsuroot.smccore.item.ItemManager");
            Field itemManagerField = itemManagerClass.getField("itemManager");
            itemManagerInstance = itemManagerField.get(null);

            if (itemManagerInstance == null) {
                logger.warning("[SMCCoreHook] ItemManager.itemManager is null");
                return;
            }

            Class<?> implClass = itemManagerInstance.getClass();

            // Try MethodHandles first (better module access)
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                // Try to find composeById method
                Method composeMethod = findMethodInHierarchy(implClass, "composeById", String.class);
                if (composeMethod != null) {
                    composeMethod.setAccessible(true);
                    composeByIdHandle = lookup.unreflect(composeMethod);
                    logger.info("[SMCCoreHook] Got MethodHandle for composeById");
                }

                // Try to find getIdByItemStackOrNull method
                Method getIdMethod = findMethodInHierarchy(implClass, "getIdByItemStackOrNull", ItemStack.class);
                if (getIdMethod != null) {
                    getIdMethod.setAccessible(true);
                    getIdByItemStackOrNullHandle = lookup.unreflect(getIdMethod);
                }

                // Try to find hasId method
                Method hasMethod = findMethodInHierarchy(implClass, "hasId", ItemStack.class, String.class);
                if (hasMethod != null) {
                    hasMethod.setAccessible(true);
                    hasIdHandle = lookup.unreflect(hasMethod);
                }

            } catch (Exception e) {
                logger.info("[SMCCoreHook] MethodHandles failed, using reflection fallback: " + e.getMessage());
            }

            // Fallback to reflection if MethodHandles failed
            if (composeByIdHandle == null) {
                composeByIdMethod = findMethodInHierarchy(implClass, "composeById", String.class);
                if (composeByIdMethod != null) {
                    composeByIdMethod.setAccessible(true);
                }
            }

            if (getIdByItemStackOrNullHandle == null) {
                getIdByItemStackOrNullMethod = findMethodInHierarchy(implClass, "getIdByItemStackOrNull", ItemStack.class);
                if (getIdByItemStackOrNullMethod != null) {
                    getIdByItemStackOrNullMethod.setAccessible(true);
                }
            }

            if (hasIdHandle == null) {
                hasIdMethod = findMethodInHierarchy(implClass, "hasId", ItemStack.class, String.class);
                if (hasIdMethod != null) {
                    hasIdMethod.setAccessible(true);
                }
            }

            available = (composeByIdHandle != null || composeByIdMethod != null);

            if (available) {
                logger.info("[SMCCoreHook] Successfully hooked into SMCCore ItemManager" +
                    (composeByIdHandle != null ? " (MethodHandles)" : " (Reflection)"));
            } else {
                logger.warning("[SMCCoreHook] Could not find required methods in SMCCore");
            }

        } catch (ClassNotFoundException e) {
            logger.info("[SMCCoreHook] ItemManager class not found");
        } catch (NoSuchFieldException e) {
            logger.info("[SMCCoreHook] itemManager field not found");
        } catch (Exception e) {
            logger.log(Level.WARNING, "[SMCCoreHook] Failed to initialize", e);
        }
    }

    /**
     * Find a method in the class hierarchy including interfaces.
     */
    private Method findMethodInHierarchy(Class<?> clazz, String name, Class<?>... paramTypes) {
        // Check interfaces first
        for (Class<?> iface : clazz.getInterfaces()) {
            try {
                return iface.getMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}
        }

        // Check class hierarchy
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, paramTypes);
            } catch (NoSuchMethodException ignored) {}
            current = current.getSuperclass();
        }

        // Try public method
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {}

        return null;
    }


    private Plugin findSmcCorePlugin() {
        String[] names = {"SmcCore", "SMCCore", "smccore"};
        for (String name : names) {
            Plugin p = Bukkit.getPluginManager().getPlugin(name);
            if (p != null) return p;
        }
        return null;
    }

    // ==================== PUBLIC API ====================

    public boolean isAvailable() {
        if (!available && !initialized) {
            initialize();
        }
        return available;
    }

    /**
     * Gets the SMCCore item ID from an ItemStack.
     */
    public String getItemId(ItemStack item) {
        if (!isAvailable() || item == null) {
            return null;
        }

        try {
            Object result = null;

            if (getIdByItemStackOrNullHandle != null) {
                result = getIdByItemStackOrNullHandle.invoke(itemManagerInstance, item);
            } else if (getIdByItemStackOrNullMethod != null) {
                result = getIdByItemStackOrNullMethod.invoke(itemManagerInstance, item);
            }

            return result != null ? result.toString() : null;
        } catch (Throwable e) {
            logger.fine("[SMCCoreHook] Error getting item ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates an ItemStack from an SMCCore item ID.
     */
    public ItemStack getItem(String id) {
        if (!isAvailable() || id == null || id.isEmpty()) {
            return null;
        }

        try {
            Object result = null;

            if (composeByIdHandle != null) {
                result = composeByIdHandle.invoke(itemManagerInstance, id);
            } else if (composeByIdMethod != null) {
                result = composeByIdMethod.invoke(itemManagerInstance, id);
            }

            if (result == null) {
                logger.warning("[SMCCoreHook] composeById returned null for: " + id);
                return null;
            }

            // Result is likely a custom Result type - need to extract the value
            ItemStack itemStack = extractItemStack(result);

            if (itemStack != null) {
                return itemStack.clone();
            }

            logger.warning("[SMCCoreHook] Could not extract ItemStack from result for: " + id +
                " (result type: " + result.getClass().getName() + ")");
            return null;
        } catch (Throwable e) {
            logger.warning("[SMCCoreHook] Error creating item '" + id + "': " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts ItemStack from a Result object.
     */

    private ItemStack extractItemStack(Object result) {
        if (result == null) return null;

        // Direct ItemStack
        if (result instanceof ItemStack) {
            return (ItemStack) result;
        }

        Class<?> resultClass = result.getClass();

        // Check for failure states first
        try {
            Method isFailureMethod = resultClass.getMethod("isFailure");
            Boolean isFailure = (Boolean) isFailureMethod.invoke(result);
            if (Boolean.TRUE.equals(isFailure)) {
                // Try to get error message
                try {
                    Method getErrorMethod = resultClass.getMethod("getError");
                    Object error = getErrorMethod.invoke(result);
                    logger.warning("[SMCCoreHook] Result is failure: " + error);
                } catch (Exception ignored) {
                    logger.warning("[SMCCoreHook] Result is failure (no error message available)");
                }
                return null;
            }
        } catch (NoSuchMethodException ignored) {}
        catch (Exception e) {
            logger.fine("[SMCCoreHook] isFailure check failed: " + e.getMessage());
        }

        // Check isPresent() for Optional-like types
        try {
            Method isPresentMethod = resultClass.getMethod("isPresent");
            Boolean isPresent = (Boolean) isPresentMethod.invoke(result);
            if (!Boolean.TRUE.equals(isPresent)) {
                logger.fine("[SMCCoreHook] Result is not present (empty Optional)");
                return null;
            }
        } catch (NoSuchMethodException ignored) {}
        catch (Exception e) {
            logger.fine("[SMCCoreHook] isPresent check failed: " + e.getMessage());
        }

        // Try get() method
        ItemStack item = tryExtractMethod(result, resultClass, "get");
        if (item != null) return item;

        // Try getValue() method
        item = tryExtractMethod(result, resultClass, "getValue");
        if (item != null) return item;

        // Try getOrNull() method
        item = tryExtractMethod(result, resultClass, "getOrNull");
        if (item != null) return item;

        // Try value() method (for records)
        item = tryExtractMethod(result, resultClass, "value");
        if (item != null) return item;

        // Try orElse(null) for Optional-like types
        try {
            Method orElseMethod = resultClass.getMethod("orElse", Object.class);
            Object value = orElseMethod.invoke(result, (Object) null);
            if (value instanceof ItemStack) {
                return (ItemStack) value;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.fine("[SMCCoreHook] orElse failed: " + e.getMessage());
        }

        // Try isSuccess() + get() pattern
        try {
            Method isSuccessMethod = resultClass.getMethod("isSuccess");
            Boolean isSuccess = (Boolean) isSuccessMethod.invoke(result);
            if (Boolean.TRUE.equals(isSuccess)) {
                item = tryExtractMethod(result, resultClass, "get", "getValue", "getOrNull");
                if (item != null) return item;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.fine("[SMCCoreHook] isSuccess pattern failed: " + e.getMessage());
        }

        // Try isOk() + get() pattern (Rust-style Result)
        try {
            Method isOkMethod = resultClass.getMethod("isOk");
            Boolean isOk = (Boolean) isOkMethod.invoke(result);
            if (Boolean.TRUE.equals(isOk)) {
                item = tryExtractMethod(result, resultClass, "get", "getValue", "unwrap");
                if (item != null) return item;
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.fine("[SMCCoreHook] isOk pattern failed: " + e.getMessage());
        }

        logger.fine("[SMCCoreHook] Could not extract ItemStack from Result type: " + resultClass.getName());
        return null;
    }

    private ItemStack tryExtractMethod(Object result, Class<?> resultClass, String... methodNames) {
        for (String methodName : methodNames) {
            try {
                Method method = resultClass.getMethod(methodName);
                method.setAccessible(true);
                Object value = method.invoke(result);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                // Try getDeclaredMethod as fallback
                try {
                    Method method = resultClass.getDeclaredMethod(methodName);
                    method.setAccessible(true);
                    Object value = method.invoke(result);
                    if (value instanceof ItemStack) {
                        return (ItemStack) value;
                    }
                } catch (Exception ignored2) {}
            }
        }
        return null;
    }

    /**
     * Creates an ItemStack from an SMCCore item ID with specified amount.
     */
    public ItemStack getItem(String id, int amount) {
        ItemStack item = getItem(id);
        if (item != null) {
            item.setAmount(Math.max(1, amount));
        }
        return item;
    }

    /**
     * Checks if an ItemStack matches an SMCCore item ID.
     */
    public boolean matches(ItemStack item, String id) {
        if (!isAvailable() || item == null || id == null) {
            return false;
        }

        try {
            Object result = null;

            if (hasIdHandle != null) {
                result = hasIdHandle.invoke(itemManagerInstance, item, id);
            } else if (hasIdMethod != null) {
                result = hasIdMethod.invoke(itemManagerInstance, item, id);
            }

            return Boolean.TRUE.equals(result);
        } catch (Throwable e) {
            logger.fine("[SMCCoreHook] Error checking hasId: " + e.getMessage());
            // Fallback to ID comparison
            String itemId = getItemId(item);
            return id.equalsIgnoreCase(itemId);
        }
    }

    /**
     * Checks if an SMCCore item with the given ID exists.
     */
    public boolean hasItem(String id) {
        return getItem(id) != null;
    }
}