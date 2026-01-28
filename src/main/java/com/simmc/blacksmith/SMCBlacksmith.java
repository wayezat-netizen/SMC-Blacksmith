package com.simmc.blacksmith;

import com.simmc.blacksmith.commands.BlacksmithCommand;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.NexoHook;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.listeners.ForgeListener;
import com.simmc.blacksmith.listeners.FurnaceListener;
import com.simmc.blacksmith.listeners.PlayerListener;
import com.simmc.blacksmith.listeners.RepairListener;
import com.simmc.blacksmith.repair.RepairManager;
import com.simmc.blacksmith.util.TaskManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SMCBlacksmith extends JavaPlugin {

    private static SMCBlacksmith instance;

    private TaskManager taskManager;
    private ConfigManager configManager;
    private ItemProviderRegistry itemRegistry;
    private FurnaceManager furnaceManager;
    private ForgeManager forgeManager;
    private RepairManager repairManager;

    private PlaceholderAPIHook papiHook;
    private SMCCoreHook smcCoreHook;
    private CraftEngineHook craftEngineHook;
    private NexoHook nexoHook;

    @Override
    public void onEnable() {
        instance = this;

        taskManager = new TaskManager(this);

        initializeHooks();

        configManager = new ConfigManager(this);
        configManager.loadAll();

        itemRegistry = new ItemProviderRegistry(this, smcCoreHook, craftEngineHook, nexoHook);

        furnaceManager = new FurnaceManager(this, configManager, itemRegistry);
        forgeManager = new ForgeManager(this, configManager, itemRegistry);
        repairManager = new RepairManager(this, configManager, itemRegistry);

        registerListeners();
        registerCommands();

        furnaceManager.startTickTask();
        furnaceManager.loadAll();

        logStartupInfo();
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
    }

    private void initializeHooks() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiHook = new PlaceholderAPIHook(this);
            getLogger().info("Hooked into PlaceholderAPI");
        }

        if (getServer().getPluginManager().getPlugin("SMCCore") != null) {
            smcCoreHook = new SMCCoreHook(this);
            getLogger().info("Hooked into SMCCore");
        }

        if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            craftEngineHook = new CraftEngineHook(this);
            getLogger().info("Hooked into CraftEngine");
        }

        if (getServer().getPluginManager().getPlugin("Nexo") != null) {
            nexoHook = new NexoHook(this);
            getLogger().info("Hooked into Nexo");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FurnaceListener(furnaceManager), this);
        getServer().getPluginManager().registerEvents(new ForgeListener(forgeManager), this);
        getServer().getPluginManager().registerEvents(new RepairListener(repairManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(furnaceManager, forgeManager), this);
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

    public void reload() {
        configManager.loadAll();
        furnaceManager.reload();
        forgeManager.reload();
        repairManager.reload();
        getLogger().info("Configuration reloaded.");
    }

    public static SMCBlacksmith getInstance() { return instance; }
    public TaskManager getTaskManager() { return taskManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public ItemProviderRegistry getItemRegistry() { return itemRegistry; }
    public FurnaceManager getFurnaceManager() { return furnaceManager; }
    public ForgeManager getForgeManager() { return forgeManager; }
    public RepairManager getRepairManager() { return repairManager; }
    public PlaceholderAPIHook getPapiHook() { return papiHook; }
    public SMCCoreHook getSmcCoreHook() { return smcCoreHook; }
    public CraftEngineHook getCraftEngineHook() { return craftEngineHook; }
    public NexoHook getNexoHook() { return nexoHook; }

    public boolean hasPAPI() { return papiHook != null && papiHook.isAvailable(); }
    public boolean hasSMCCore() { return smcCoreHook != null && smcCoreHook.isAvailable(); }
    public boolean hasCraftEngine() { return craftEngineHook != null && craftEngineHook.isAvailable(); }
    public boolean hasNexo() { return nexoHook != null && nexoHook.isAvailable(); }
}