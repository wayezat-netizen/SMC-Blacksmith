package com.simmc.blacksmith;

import com.simmc.blacksmith.commands.BlacksmithCommand;
import com.simmc.blacksmith.commands.ForgeCommands;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.CustomFishingHook;
import com.simmc.blacksmith.integration.NexoHook;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.listeners.*;
import com.simmc.blacksmith.quench.QuenchingManager;
import com.simmc.blacksmith.repair.RepairManager;
import com.simmc.blacksmith.util.TaskManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;

public final class SMCBlacksmith extends JavaPlugin {

    private static SMCBlacksmith instance;

    private TaskManager taskManager;
    private ConfigManager configManager;
    private ItemProviderRegistry itemRegistry;
    private FurnaceManager furnaceManager;
    private ForgeManager forgeManager;
    private QuenchingManager quenchingManager;
    private RepairManager repairManager;

    private final LazyHook<PlaceholderAPIHook> papiHook = new LazyHook<>();
    private final LazyHook<SMCCoreHook> smcCoreHook = new LazyHook<>();
    private final LazyHook<CraftEngineHook> craftEngineHook = new LazyHook<>();
    private final LazyHook<NexoHook> nexoHook = new LazyHook<>();
    private final LazyHook<CustomFishingHook> customFishingHook = new LazyHook<>();

    private volatile boolean fullyEnabled = false;

    @Override
    public void onEnable() {
        instance = this;

        try {
            initializeCore();
            initializeManagers();
            registerListeners();
            registerCommands();
            startSystems();

            fullyEnabled = true;
            logStartupInfo();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable SMCBlacksmith!", e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        fullyEnabled = false;

        shutdownSafely("QuenchingManager", () -> {
            if (quenchingManager != null) quenchingManager.cancelAllSessions();
        });

        shutdownSafely("ForgeManager", () -> {
            if (forgeManager != null) forgeManager.cancelAllSessions();
        });

        shutdownSafely("FurnaceManager", () -> {
            if (furnaceManager != null) {
                furnaceManager.stopTickTask();
                furnaceManager.saveAll();
            }
        });

        shutdownSafely("TaskManager", () -> {
            if (taskManager != null) taskManager.cancelAll();
        });

        getLogger().info("SMCBlacksmith disabled.");
        instance = null;
    }

    private void initializeCore() {
        taskManager = new TaskManager(this);
        configManager = new ConfigManager(this);
        configManager.loadAll();

        detectIntegrations();
    }

    private void initializeManagers() {
        SMCCoreHook smcHook = getSmcCoreHook();
        CraftEngineHook ceHook = getCraftEngineHook();
        NexoHook nHook = getNexoHook();
        CustomFishingHook cfHook = getCustomFishingHook();

        getLogger().info("SMCCore hook available: " + (smcHook != null && smcHook.isAvailable()));
        getLogger().info("CraftEngine hook available: " + (ceHook != null && ceHook.isAvailable()));
        getLogger().info("Nexo hook available: " + (nHook != null && nHook.isAvailable()));
        getLogger().info("CustomFishing hook available: " + (cfHook != null && cfHook.isAvailable()));

        itemRegistry = new ItemProviderRegistry(this, smcHook, ceHook, nHook, cfHook);

        furnaceManager = new FurnaceManager(this, configManager, itemRegistry);
        forgeManager = new ForgeManager(this, configManager, itemRegistry);
        quenchingManager = new QuenchingManager(this, configManager);
        repairManager = new RepairManager(this, configManager, itemRegistry);
    }

    private void registerListeners() {
        PluginManager pm = getServer().getPluginManager();

        // Furnace listeners
        pm.registerEvents(new FurnaceListener(furnaceManager), this);
        pm.registerEvents(new FurnaceBlockListener(furnaceManager), this);
        pm.registerEvents(new BellowsListener(this, furnaceManager, configManager), this);
        pm.registerEvents(new FurnaceInteractionListener(furnaceManager, configManager), this);

        // Forge listeners
        pm.registerEvents(new ForgeListener(forgeManager), this);
        pm.registerEvents(new ForgeGUIListener(forgeManager), this);
        pm.registerEvents(new ForgeCancelListener(forgeManager), this);

        // Repair/Grindstone listener
        pm.registerEvents(new GrindstoneListener(repairManager, configManager), this);

        // Other listeners
        pm.registerEvents(new QuenchingListener(quenchingManager), this);
        pm.registerEvents(new PlayerListener(furnaceManager, forgeManager, quenchingManager), this);
        pm.registerEvents(new BlockInteractListener(furnaceManager, configManager), this);
    }

    private void registerCommands() {
        BlacksmithCommand commandExecutor = new BlacksmithCommand(this);
        registerCommand("blacksmith", commandExecutor, commandExecutor);

        List<String> recipeIds = new ArrayList<>(configManager.getBlacksmithConfig().getRecipeIds());
        ForgeCommands forgeCommands = new ForgeCommands(forgeManager, recipeIds);
        registerCommand("forge", forgeCommands, forgeCommands);
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor,
                                 org.bukkit.command.TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(completer);
        } else {
            getLogger().warning("Command '" + name + "' not found in plugin.yml!");
        }
    }

    private void startSystems() {
        furnaceManager.startTickTask();
        furnaceManager.loadAll();
    }

    private void detectIntegrations() {
        PluginManager pm = getServer().getPluginManager();

        logIntegration("PlaceholderAPI", pm.getPlugin("PlaceholderAPI") != null);
        logIntegration("SmcCore", pm.getPlugin("SmcCore") != null);
        logIntegration("CraftEngine", pm.getPlugin("CraftEngine") != null);
        logIntegration("Nexo", pm.getPlugin("Nexo") != null);
        logIntegration("CustomFishing", pm.getPlugin("CustomFishing") != null);
    }

    private void logIntegration(String name, boolean found) {
        if (found) {
            getLogger().info("Integration detected: " + name);
        }
    }

    private void shutdownSafely(String component, Runnable shutdown) {
        try {
            shutdown.run();
        } catch (Exception e) {
            getLogger().log(Level.WARNING, "Error shutting down " + component, e);
        }
    }

    private void logStartupInfo() {
        getLogger().info("========================================");
        getLogger().info("SMCBlacksmith v" + getDescription().getVersion());
        getLogger().info("Furnace Types: " + configManager.getFurnaceConfig().getFurnaceTypeCount());
        getLogger().info("Forge Recipes: " + configManager.getBlacksmithConfig().getRecipeCount());
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

    // ==================== HOOK ACCESSORS ====================

    public PlaceholderAPIHook getPapiHook() {
        return papiHook.get(() -> isPluginPresent("PlaceholderAPI")
                ? new PlaceholderAPIHook(this) : null);
    }

    public SMCCoreHook getSmcCoreHook() {
        return smcCoreHook.get(() -> isPluginPresent("SmcCore")
                ? new SMCCoreHook(this) : null);
    }

    public CraftEngineHook getCraftEngineHook() {
        return craftEngineHook.get(() -> isPluginPresent("CraftEngine")
                ? new CraftEngineHook(this) : null);
    }

    public NexoHook getNexoHook() {
        return nexoHook.get(() -> isPluginPresent("Nexo")
                ? new NexoHook(this) : null);
    }

    public CustomFishingHook getCustomFishingHook() {
        return customFishingHook.get(() -> isPluginPresent("CustomFishing")
                ? new CustomFishingHook(this) : null);
    }

    private boolean isPluginPresent(String name) {
        return getServer().getPluginManager().getPlugin(name) != null;
    }

    public boolean hasPAPI() {
        return Optional.ofNullable(getPapiHook()).map(PlaceholderAPIHook::isAvailable).orElse(false);
    }

    public boolean hasSMCCore() {
        return Optional.ofNullable(getSmcCoreHook()).map(SMCCoreHook::isAvailable).orElse(false);
    }

    public boolean hasCraftEngine() {
        return Optional.ofNullable(getCraftEngineHook()).map(CraftEngineHook::isAvailable).orElse(false);
    }

    public boolean hasNexo() {
        return Optional.ofNullable(getNexoHook()).map(NexoHook::isAvailable).orElse(false);
    }

    public boolean hasCustomFishing() {
        return Optional.ofNullable(getCustomFishingHook()).map(CustomFishingHook::isAvailable).orElse(false);
    }

    // ==================== GETTERS ====================

    public static SMCBlacksmith getInstance() { return instance; }
    public TaskManager getTaskManager() { return taskManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public ItemProviderRegistry getItemRegistry() { return itemRegistry; }
    public FurnaceManager getFurnaceManager() { return furnaceManager; }
    public ForgeManager getForgeManager() { return forgeManager; }
    public QuenchingManager getQuenchingManager() { return quenchingManager; }
    public RepairManager getRepairManager() { return repairManager; }
    public boolean isFullyEnabled() { return fullyEnabled; }

    private static class LazyHook<T> {
        private final AtomicReference<T> ref = new AtomicReference<>();
        private volatile boolean initialized = false;

        public T get(Supplier<T> supplier) {
            if (!initialized) {
                synchronized (this) {
                    if (!initialized) {
                        ref.set(supplier.get());
                        initialized = true;
                    }
                }
            }
            return ref.get();
        }
    }
}