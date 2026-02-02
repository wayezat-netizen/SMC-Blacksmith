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
import com.simmc.blacksmith.listeners.WorldListener;
import com.simmc.blacksmith.repair.RepairManager;
import com.simmc.blacksmith.util.TaskManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Main plugin class for SMCBlacksmith.
 * A complete blacksmithing system with custom furnaces, forging minigame, and repair mechanics.
 *
 * @author SimMC
 * @version 1.0.0
 */
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
        long startTime = System.currentTimeMillis();
        instance = this;

        try {
            // Initialize task manager first
            taskManager = new TaskManager(this);

            // Initialize external plugin hooks
            initializeHooks();

            // Load configuration files
            configManager = new ConfigManager(this);
            configManager.loadAll();

            // Initialize item provider registry
            itemRegistry = new ItemProviderRegistry(this, smcCoreHook, craftEngineHook, nexoHook);

            // Initialize managers
            furnaceManager = new FurnaceManager(this, configManager, itemRegistry);
            forgeManager = new ForgeManager(this, configManager, itemRegistry);
            repairManager = new RepairManager(this, configManager, itemRegistry);

            // Register event listeners
            registerListeners();

            // Register commands
            registerCommands();

            // Start furnace system
            furnaceManager.startTickTask();
            furnaceManager.loadAll();

            // Log startup info
            logStartupInfo(System.currentTimeMillis() - startTime);

        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable SMCBlacksmith!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            // Cancel all forge sessions
            if (forgeManager != null) {
                forgeManager.cancelAllSessions();
            }

            // Stop furnace tick and save data
            if (furnaceManager != null) {
                furnaceManager.stopTickTask();
                furnaceManager.saveAll();
            }

            // Cancel all scheduled tasks
            if (taskManager != null) {
                taskManager.cancelAll();
            }

            getLogger().info("SMCBlacksmith disabled successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Error during plugin disable", e);
        }
    }

    /**
     * Initializes hooks to external plugins.
     */
    private void initializeHooks() {
        // PlaceholderAPI
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiHook = new PlaceholderAPIHook(this);
            getLogger().info("Hooked into PlaceholderAPI");
        }

        // SMCCore
        if (getServer().getPluginManager().getPlugin("SMCCore") != null) {
            smcCoreHook = new SMCCoreHook(this);
            getLogger().info("Hooked into SMCCore");
        }

        // CraftEngine
        if (getServer().getPluginManager().getPlugin("CraftEngine") != null) {
            craftEngineHook = new CraftEngineHook(this);
            getLogger().info("Hooked into CraftEngine");
        }

        // Nexo
        if (getServer().getPluginManager().getPlugin("Nexo") != null) {
            nexoHook = new NexoHook(this);
            getLogger().info("Hooked into Nexo");
        }
    }

    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new FurnaceListener(furnaceManager), this);
        getServer().getPluginManager().registerEvents(new ForgeListener(forgeManager), this);
        getServer().getPluginManager().registerEvents(new RepairListener(repairManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(furnaceManager, forgeManager), this);
        getServer().getPluginManager().registerEvents(new WorldListener(this, furnaceManager), this);
    }

    /**
     * Registers plugin commands.
     */
    private void registerCommands() {
        PluginCommand command = getCommand("blacksmith");
        if (command != null) {
            BlacksmithCommand executor = new BlacksmithCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        } else {
            getLogger().warning("Failed to register blacksmith command!");
        }
    }

    /**
     * Logs plugin startup information.
     */
    private void logStartupInfo(long loadTimeMs) {
        getLogger().info("========================================");
        getLogger().info("SMCBlacksmith v" + getDescription().getVersion() + " enabled!");
        getLogger().info("----------------------------------------");
        getLogger().info("Furnace types: " + configManager.getFurnaceConfig().getFurnaceTypeCount());
        getLogger().info("Forge recipes: " + configManager.getBlacksmithConfig().getRecipeCount());
        getLogger().info("Repair configs: " + configManager.getGrindstoneConfig().getRepairConfigCount());
        getLogger().info("Fuel types: " + configManager.getFuelConfig().getFuelCount());
        getLogger().info("Item providers: " + itemRegistry.getProviderCount());
        getLogger().info("Loaded furnaces: " + furnaceManager.getFurnaceCount());
        getLogger().info("----------------------------------------");
        getLogger().info("Load time: " + loadTimeMs + "ms");
        getLogger().info("========================================");
    }

    /**
     * Reloads the plugin configuration.
     */
    public void reload() {
        try {
            getLogger().info("Reloading SMCBlacksmith...");

            // Cancel active forge sessions
            forgeManager.cancelAllSessions();

            // Save furnace data
            furnaceManager.saveAll();

            // Reload configurations
            configManager.loadAll();

            // Refresh managers
            furnaceManager.reload();
            forgeManager.reload();
            repairManager.reload();

            getLogger().info("Configuration reloaded successfully.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to reload configuration!", e);
            throw e;
        }
    }

    // Getters

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

    public PlaceholderAPIHook getPapiHook() {
        return papiHook;
    }

    public boolean hasSMCCore() {
        return smcCoreHook != null && smcCoreHook.isAvailable();
    }

    /**
     * Checks if Nexo integration is available.
     */
    public boolean hasNexo() {
        return nexoHook != null && nexoHook.isAvailable();
    }

    /**
     * Checks if PlaceholderAPI integration is available.
     */
    public boolean hasPAPI() {
        return papiHook != null && papiHook.isAvailable();
    }

    /**
     * Checks if CraftEngine integration is available.
     */
    public boolean hasCraftEngine() {
        return craftEngineHook != null && craftEngineHook.isAvailable();
    }
}