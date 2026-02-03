package com.simmc.blacksmith;

import com.simmc.blacksmith.commands.BlacksmithCommand;
import com.simmc.blacksmith.commands.ForgeCommands;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.NexoHook;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.listeners.*;
import com.simmc.blacksmith.repair.RepairManager;
import com.simmc.blacksmith.util.TaskManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Main plugin class for SMCBlacksmith.
 * Manages initialization, hooks, and provides access to all managers.
 */
public final class SMCBlacksmith extends JavaPlugin {

    private static SMCBlacksmith instance;

    private TaskManager taskManager;
    private ConfigManager configManager;
    private ItemProviderRegistry itemRegistry;
    private FurnaceManager furnaceManager;
    private ForgeManager forgeManager;
    private RepairManager repairManager;

    // Lazy-initialized hooks with double-checked locking
    private volatile PlaceholderAPIHook papiHook;
    private volatile SMCCoreHook smcCoreHook;
    private volatile CraftEngineHook craftEngineHook;
    private volatile NexoHook nexoHook;

    // Lock objects for lazy initialization
    private final Object papiLock = new Object();
    private final Object smcCoreLock = new Object();
    private final Object craftEngineLock = new Object();
    private final Object nexoLock = new Object();

    // Track if hooks have been checked (to avoid repeated null checks)
    private volatile boolean papiChecked = false;
    private volatile boolean smcCoreChecked = false;
    private volatile boolean craftEngineChecked = false;
    private volatile boolean nexoChecked = false;

    @Override
    public void onEnable() {
        instance = this;

        taskManager = new TaskManager(this);
        initializeEarlyHooks();

        configManager = new ConfigManager(this);
        configManager.loadAll();

        itemRegistry = new ItemProviderRegistry(this,
                getSmcCoreHook(),
                getCraftEngineHook(),
                getNexoHook());

        furnaceManager = new FurnaceManager(this, configManager, itemRegistry);
        forgeManager = new ForgeManager(this, configManager, itemRegistry);  // Only create once
        repairManager = new RepairManager(this, configManager, itemRegistry);

        registerListeners();
        registerCommands();

        furnaceManager.startTickTask();
        furnaceManager.loadAll();

        logStartupInfo();

        List<String> recipeIds = new ArrayList<>(configManager.getBlacksmithConfig().getRecipeIds());
        PluginCommand forgeCommand = getCommand("forge");
        if (forgeCommand != null) {
            ForgeCommands forgeCommands = new ForgeCommands(forgeManager, recipeIds);
            forgeCommand.setExecutor(forgeCommands);
            forgeCommand.setTabCompleter(forgeCommands);
        }
    }

    @Override
    public void onDisable() {
        if (forgeManager != null) {
            forgeManager.cancelAllSessions();
        }

        if (furnaceManager != null) {
            furnaceManager.stopTickTask();
            furnaceManager.saveAll();
        }

        if (taskManager != null) {
            taskManager.cancelAll();
        }

        getLogger().info("SMCBlacksmith disabled.");
        instance = null;
    }

    /**
     * Initialize hooks that are needed during startup.
     * These are checked eagerly but still use lazy initialization pattern.
     */
    private void initializeEarlyHooks() {
        // Just mark what's available - actual initialization is lazy
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI detected - will hook when needed");
        }
        if (getServer().getPluginManager().getPlugin("SMCCore") != null) {
            getLogger().info("SMCCore detected - will hook when needed");
        }
        if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            getLogger().info("CraftEngine detected - will hook when needed");
        }
        if (getServer().getPluginManager().getPlugin("Nexo") != null) {
            getLogger().info("Nexo detected - will hook when needed");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FurnaceListener(furnaceManager), this);
        getServer().getPluginManager().registerEvents(new ForgeListener(forgeManager), this);
        getServer().getPluginManager().registerEvents(new RepairListener(repairManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(furnaceManager, forgeManager), this);
        getServer().getPluginManager().registerEvents(new BlockInteractListener(furnaceManager, configManager), this);
    }

    private void registerCommands() {
        BlacksmithCommand commandExecutor = new BlacksmithCommand(this);
        PluginCommand command = getCommand("blacksmith");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }
    }

    private void logStartupInfo() {
        getLogger().info("========================================");
        getLogger().info("SMCBlacksmith v" + getDescription().getVersion());
        getLogger().info("Furnace Types: " + configManager.getFurnaceConfig().getFurnaceTypeCount());
        getLogger().info("Forge Recipes: " + configManager.getBlacksmithConfig().getRecipeCount());
        getLogger().info("Repair Configs: " + configManager.getGrindstoneConfig().getRepairConfigCount());
        getLogger().info("Item Providers: " + itemRegistry.getProviderCount());
        getLogger().info("========================================");
    }

    /**
     * Reloads all configurations and managers.
     */
    public void reload() {
        configManager.loadAll();
        furnaceManager.reload();
        forgeManager.reload();
        repairManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    // ==================== LAZY INITIALIZATION GETTERS ====================

    /**
     * Gets the PlaceholderAPI hook, initializing it lazily if available.
     */
    public PlaceholderAPIHook getPapiHook() {
        if (!papiChecked) {
            synchronized (papiLock) {
                if (!papiChecked) {
                    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        papiHook = new PlaceholderAPIHook(this);
                        getLogger().info("Lazily hooked into PlaceholderAPI");
                    }
                    papiChecked = true;
                }
            }
        }
        return papiHook;
    }

    /**
     * Gets the SMCCore hook, initializing it lazily if available.
     */
    public SMCCoreHook getSmcCoreHook() {
        if (!smcCoreChecked) {
            synchronized (smcCoreLock) {
                if (!smcCoreChecked) {
                    if (getServer().getPluginManager().getPlugin("SMCCore") != null) {
                        smcCoreHook = new SMCCoreHook(this);
                        getLogger().info("Lazily hooked into SMCCore");
                    }
                    smcCoreChecked = true;
                }
            }
        }
        return smcCoreHook;
    }

    /**
     * Gets the CraftEngine hook, initializing it lazily if available.
     */
    public CraftEngineHook getCraftEngineHook() {
        if (!craftEngineChecked) {
            synchronized (craftEngineLock) {
                if (!craftEngineChecked) {
                    if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
                        craftEngineHook = new CraftEngineHook(this);
                        getLogger().info("Lazily hooked into CraftEngine");
                    }
                    craftEngineChecked = true;
                }
            }
        }
        return craftEngineHook;
    }

    /**
     * Gets the Nexo hook, initializing it lazily if available.
     */
    public NexoHook getNexoHook() {
        if (!nexoChecked) {
            synchronized (nexoLock) {
                if (!nexoChecked) {
                    if (getServer().getPluginManager().getPlugin("Nexo") != null) {
                        nexoHook = new NexoHook(this);
                        getLogger().info("Lazily hooked into Nexo");
                    }
                    nexoChecked = true;
                }
            }
        }
        return nexoHook;
    }

    // ==================== STANDARD GETTERS ====================

    public static SMCBlacksmith getInstance() {
        return instance;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemProviderRegistry getItemRegistry() {
        return itemRegistry;
    }

    public FurnaceManager getFurnaceManager() {
        return furnaceManager;
    }

    public ForgeManager getForgeManager() {
        return forgeManager;
    }

    public RepairManager getRepairManager() {
        return repairManager;
    }

    // ==================== AVAILABILITY CHECKS ====================

    public boolean hasPAPI() {
        PlaceholderAPIHook hook = getPapiHook();
        return hook != null && hook.isAvailable();
    }

    public boolean hasSMCCore() {
        SMCCoreHook hook = getSmcCoreHook();
        return hook != null && hook.isAvailable();
    }

    public boolean hasCraftEngine() {
        CraftEngineHook hook = getCraftEngineHook();
        return hook != null && hook.isAvailable();
    }

    public boolean hasNexo() {
        NexoHook hook = getNexoHook();
        return hook != null && hook.isAvailable();
    }

    /**
     * Gets a summary of hook availability for debugging.
     */
    public String getHookSummary() {
        return String.format("Hooks: PAPI=%s, SMCCore=%s, CraftEngine=%s, Nexo=%s",
                hasPAPI(), hasSMCCore(), hasCraftEngine(), hasNexo());
    }
}