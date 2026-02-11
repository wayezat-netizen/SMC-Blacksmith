package com.simmc.blacksmith.integration;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Hook for SMCCore plugin integration.
 * Supports SMCCore's ItemManager API.
 */
public class SMCCoreHook {

    private final JavaPlugin plugin;
    private final Logger logger;
    private boolean available;
    private boolean initialized;

    private Object itemManagerInstance;
    private Method getIdByItemStackOrNullMethod;  // getIdByItemStackOrNull(ItemStack) -> String
    private Method composeByIdMethod;              // composeById(String) -> Result
    private Method hasIdMethod;                    // hasId(ItemStack, String) -> boolean

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

            Class<?> instanceClass = itemManagerInstance.getClass();

            // Get the methods we need
            getIdByItemStackOrNullMethod = instanceClass.getMethod("getIdByItemStackOrNull", ItemStack.class);
            composeByIdMethod = instanceClass.getMethod("composeById", String.class);
            hasIdMethod = instanceClass.getMethod("hasId", ItemStack.class, String.class);

            available = true;
            logger.info("[SMCCoreHook] ✓ Successfully hooked into SMCCore ItemManager");
            logger.info("[SMCCoreHook]   - getIdByItemStackOrNull: " + getIdByItemStackOrNullMethod);
            logger.info("[SMCCoreHook]   - composeById: " + composeByIdMethod);
            logger.info("[SMCCoreHook]   - hasId: " + hasIdMethod);

        } catch (ClassNotFoundException e) {
            logger.info("[SMCCoreHook] ItemManager class not found");
        } catch (NoSuchFieldException e) {
            logger.info("[SMCCoreHook] itemManager field not found");
        } catch (NoSuchMethodException e) {
            logger.warning("[SMCCoreHook] Required method not found: " + e.getMessage());
        } catch (Exception e) {
            logger.log(Level.WARNING, "[SMCCoreHook] Failed to initialize", e);
        }
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
     * Uses: getIdByItemStackOrNull(ItemStack) -> String
     */
    public String getItemId(ItemStack item) {
        if (!isAvailable() || item == null || getIdByItemStackOrNullMethod == null) {
            return null;
        }

        try {
            Object result = getIdByItemStackOrNullMethod.invoke(itemManagerInstance, item);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            logger.fine("[SMCCoreHook] Error getting item ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates an ItemStack from an SMCCore item ID.
     * Uses: composeById(String) -> Result
     * The Result object needs to be unwrapped to get the ItemStack.
     */
    public ItemStack getItem(String id) {
        if (!isAvailable() || id == null || id.isEmpty() || composeByIdMethod == null) {
            return null;
        }

        try {
            Object result = composeByIdMethod.invoke(itemManagerInstance, id);

            if (result == null) {
                return null;
            }

            // Result is likely a custom Result type - need to extract the value
            // Try common patterns for Result/Optional types
            ItemStack itemStack = extractItemStack(result);

            if (itemStack != null) {
                return itemStack.clone();
            }

            return null;
        } catch (Exception e) {
            logger.warning("[SMCCoreHook] Error creating item '" + id + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts ItemStack from a Result object.
     * Tries various patterns: get(), getValue(), orElse(), getOrNull(), etc.
     */
    private ItemStack extractItemStack(Object result) {
        if (result == null) return null;

        // Direct ItemStack
        if (result instanceof ItemStack) {
            return (ItemStack) result;
        }

        Class<?> resultClass = result.getClass();

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
                Object value = method.invoke(result);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logger.fine("[SMCCoreHook] " + methodName + "() failed: " + e.getMessage());
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
     * Uses: hasId(ItemStack, String) -> boolean
     */
    public boolean matches(ItemStack item, String id) {
        if (!isAvailable() || item == null || id == null || hasIdMethod == null) {
            return false;
        }

        try {
            Object result = hasIdMethod.invoke(itemManagerInstance, item, id);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
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