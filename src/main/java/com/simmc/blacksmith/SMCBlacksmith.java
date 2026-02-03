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
import com.simmc.blacksmith.quench.QuenchingManager;
import com.simmc.blacksmith.repair.RepairManager;
import com.simmc.blacksmith.util.TaskManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class SMCBlacksmith extends JavaPlugin {

    private static SMCBlacksmith instance;

    private TaskManager taskManager;
    private ConfigManager configManager;
    private ItemProviderRegistry itemRegistry;
    private FurnaceManager furnaceManager;
    private ForgeManager forgeManager;
    private QuenchingManager quenchingManager;
    private RepairManager repairManager;

    private volatile PlaceholderAPIHook papiHook;
    private volatile SMCCoreHook smcCoreHook;
    private volatile CraftEngineHook craftEngineHook;
    private volatile NexoHook nexoHook;

    private final Object papiLock = new Object();
    private final Object smcCoreLock = new Object();
    private final Object craftEngineLock = new Object();
    private final Object nexoLock = new Object();

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
        forgeManager = new ForgeManager(this, configManager, itemRegistry);
        quenchingManager = new QuenchingManager(this, configManager);
        repairManager = new RepairManager(this, configManager, itemRegistry);

        registerListeners();
        registerCommands();

        furnaceManager.startTickTask();
        furnaceManager.loadAll();

        logStartupInfo();
    }

    @Override
    public void onDisable() {
        if (quenchingManager != null) {
            quenchingManager.cancelAllSessions();
        }

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

    private void initializeEarlyHooks() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            getLogger().info("PlaceholderAPI detected");
        }
        if (getServer().getPluginManager().getPlugin("SMCCore") != null) {
            getLogger().info("SMCCore detected");
        }
        if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            getLogger().info("CraftEngine detected");
        }
        if (getServer().getPluginManager().getPlugin("Nexo") != null) {
            getLogger().info("Nexo detected");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FurnaceListener(furnaceManager), this);
        getServer().getPluginManager().registerEvents(new ForgeListener(forgeManager), this);
        getServer().getPluginManager().registerEvents(new QuenchingListener(quenchingManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(furnaceManager, forgeManager), this);
        getServer().getPluginManager().registerEvents(new ForgeGUIListener(forgeManager), this);
        getServer().getPluginManager().registerEvents(new BlockInteractListener(furnaceManager, configManager), this);
    }

    private void registerCommands() {
        BlacksmithCommand commandExecutor = new BlacksmithCommand(this);
        PluginCommand bsCommand = getCommand("blacksmith");
        if (bsCommand != null) {
            bsCommand.setExecutor(commandExecutor);
            bsCommand.setTabCompleter(commandExecutor);
        }

        List<String> recipeIds = new ArrayList<>(configManager.getBlacksmithConfig().getRecipeIds());
        ForgeCommands forgeCommands = new ForgeCommands(forgeManager, recipeIds);
        PluginCommand forgeCommand = getCommand("forge");
        if (forgeCommand != null) {
            forgeCommand.setExecutor(forgeCommands);
            forgeCommand.setTabCompleter(forgeCommands);
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

    public void reload() {
        configManager.loadAll();
        furnaceManager.reload();
        forgeManager.reload();
        quenchingManager.reload();
        repairManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    // Lazy hook getters (unchanged)
    public PlaceholderAPIHook getPapiHook() {
        if (!papiChecked) {
            synchronized (papiLock) {
                if (!papiChecked) {
                    if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                        papiHook = new PlaceholderAPIHook(this);
                    }
                    papiChecked = true;
                }
            }
        }
        return papiHook;
    }

    public SMCCoreHook getSmcCoreHook() {
        if (!smcCoreChecked) {
            synchronized (smcCoreLock) {
                if (!smcCoreChecked) {
                    if (getServer().getPluginManager().getPlugin("SMCCore") != null) {
                        smcCoreHook = new SMCCoreHook(this);
                    }
                    smcCoreChecked = true;
                }
            }
        }
        return smcCoreHook;
    }

    public CraftEngineHook getCraftEngineHook() {
        if (!craftEngineChecked) {
            synchronized (craftEngineLock) {
                if (!craftEngineChecked) {
                    if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
                        craftEngineHook = new CraftEngineHook(this);
                    }
                    craftEngineChecked = true;
                }
            }
        }
        return craftEngineHook;
    }

    public NexoHook getNexoHook() {
        if (!nexoChecked) {
            synchronized (nexoLock) {
                if (!nexoChecked) {
                    if (getServer().getPluginManager().getPlugin("Nexo") != null) {
                        nexoHook = new NexoHook(this);
                    }
                    nexoChecked = true;
                }
            }
        }
        return nexoHook;
    }

    public static SMCBlacksmith getInstance() { return instance; }
    public TaskManager getTaskManager() { return taskManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public ItemProviderRegistry getItemRegistry() { return itemRegistry; }
    public FurnaceManager getFurnaceManager() { return furnaceManager; }
    public ForgeManager getForgeManager() { return forgeManager; }
    public QuenchingManager getQuenchingManager() { return quenchingManager; }
    public RepairManager getRepairManager() { return repairManager; }

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
}