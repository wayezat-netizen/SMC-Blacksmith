package com.simmc.blacksmith.commands;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.BlacksmithConfig;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.furnace.FurnaceType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Main command handler for SMCBlacksmith.
 * Provides commands for furnace management, forging, configuration, and debugging.
 */
public class BlacksmithCommand implements CommandExecutor, TabCompleter {

    private final SMCBlacksmith plugin;

    public BlacksmithCommand(SMCBlacksmith plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "furnace" -> handleFurnace(sender, args);
            case "forge" -> handleForge(sender, args);
            case "list" -> handleList(sender, args);
            case "info" -> handleInfo(sender);
            case "debug" -> handleDebug(sender, args);
            case "stats" -> handleStats(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lSMCBlacksmith Commands:");
        sender.sendMessage("§e/blacksmith reload §7- Reload configuration");
        sender.sendMessage("§e/blacksmith furnace create <type> §7- Create furnace at location");
        sender.sendMessage("§e/blacksmith furnace open §7- Open furnace GUI");
        sender.sendMessage("§e/blacksmith forge <recipe> §7- Start forging session");
        sender.sendMessage("§e/blacksmith list furnaces §7- List furnace types");
        sender.sendMessage("§e/blacksmith list recipes §7- List forge recipes");
        sender.sendMessage("§e/blacksmith info §7- Show plugin info");

        if (sender.hasPermission("blacksmith.admin")) {
            sender.sendMessage("§c§lAdmin Commands:");
            sender.sendMessage("§e/blacksmith debug §7- Toggle debug mode");
            sender.sendMessage("§e/blacksmith debug furnace §7- Debug furnace at location");
            sender.sendMessage("§e/blacksmith debug session §7- Debug active forge session");
            sender.sendMessage("§e/blacksmith stats §7- Show performance statistics");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("blacksmith.admin")) {
            sender.sendMessage("§cYou don't have permission to do this.");
            return;
        }

        plugin.reload();
        sender.sendMessage("§aConfiguration reloaded successfully.");
    }

    private void handleFurnace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /blacksmith furnace <create|open|remove> [type]");
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /blacksmith furnace create <type>");
                    return;
                }

                if (!player.hasPermission("blacksmith.furnace.create")) {
                    sender.sendMessage("§cYou don't have permission to do this.");
                    return;
                }

                String typeId = args[2];
                FurnaceType type = plugin.getConfigManager().getFurnaceConfig().getFurnaceType(typeId);

                if (type == null) {
                    sender.sendMessage("§cUnknown furnace type: " + typeId);
                    return;
                }

                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock == null) {
                    sender.sendMessage("§cLook at a block to create furnace.");
                    return;
                }

                Location loc = targetBlock.getLocation();
                FurnaceInstance instance = plugin.getFurnaceManager().createFurnace(typeId, loc);

                if (instance != null) {
                    sender.sendMessage("§aCreated " + typeId + " furnace at target block.");
                } else {
                    sender.sendMessage("§cFailed to create furnace.");
                }
            }

            case "open" -> {
                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock == null) {
                    sender.sendMessage("§cLook at a block.");
                    return;
                }

                Location loc = targetBlock.getLocation();

                if (!plugin.getFurnaceManager().isFurnace(loc)) {
                    sender.sendMessage("§cNo furnace found at this location.");
                    return;
                }

                plugin.getFurnaceManager().openFurnaceGUI(player, loc);
            }

            case "remove" -> {
                if (!player.hasPermission("blacksmith.admin")) {
                    sender.sendMessage("§cYou don't have permission to do this.");
                    return;
                }

                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock == null) {
                    sender.sendMessage("§cLook at a block.");
                    return;
                }

                Location loc = targetBlock.getLocation();

                if (!plugin.getFurnaceManager().isFurnace(loc)) {
                    sender.sendMessage("§cNo furnace found at this location.");
                    return;
                }

                plugin.getFurnaceManager().removeFurnace(loc);
                sender.sendMessage("§aFurnace removed.");
            }

            default -> sender.sendMessage("§cUnknown action: " + action);
        }
    }

    private void handleForge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /blacksmith forge <recipe>");
            return;
        }

        String recipeId = args[1];
        Location anvilLoc = player.getLocation();

        boolean started = plugin.getForgeManager().startSession(player, recipeId, anvilLoc);

        if (!started) {
            sender.sendMessage("§cFailed to start forging session.");
        }
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /blacksmith list <furnaces|recipes|active>");
            return;
        }

        String listType = args[1].toLowerCase();

        switch (listType) {
            case "furnaces" -> {
                Map<String, FurnaceType> types = plugin.getConfigManager().getFurnaceConfig().getFurnaceTypes();
                sender.sendMessage("§6§lFurnace Types (" + types.size() + "):");
                for (FurnaceType type : types.values()) {
                    sender.sendMessage("§e- " + type.getId() + " §7(Max Temp: " + type.getMaxTemperature() +
                            "°C, Ideal: " + type.getMinIdealTemperature() + "-" + type.getMaxIdealTemperature() +
                            "°C, Recipes: " + type.getRecipeCount() + ")");
                }
            }

            case "recipes" -> {
                BlacksmithConfig config = plugin.getConfigManager().getBlacksmithConfig();
                Map<String, ForgeRecipe> recipes = config.getRecipes();
                sender.sendMessage("§6§lForge Recipes (" + recipes.size() + "):");
                for (ForgeRecipe recipe : recipes.values()) {
                    String perm = recipe.hasPermission() ? recipe.getPermission() : "none";
                    String cond = recipe.hasCondition() ? "yes" : "no";
                    String thresholds = recipe.hasStarThresholds() ? "custom" : "default";
                    sender.sendMessage("§e- " + recipe.getId() +
                            " §7(Hits: " + recipe.getHits() +
                            ", Perm: " + perm +
                            ", Condition: " + cond +
                            ", Thresholds: " + thresholds + ")");
                }
            }

            case "active" -> {
                if (!sender.hasPermission("blacksmith.admin")) {
                    sender.sendMessage("§cYou don't have permission to do this.");
                    return;
                }

                int furnaceCount = plugin.getFurnaceManager().getFurnaceCount();
                int forgeCount = plugin.getForgeManager().getActiveSessionCount();
                int guiCount = plugin.getFurnaceManager().getOpenGUICount();

                sender.sendMessage("§6§lActive Sessions:");
                sender.sendMessage("§e- Active Furnaces: §f" + furnaceCount);
                sender.sendMessage("§e- Open Furnace GUIs: §f" + guiCount);
                sender.sendMessage("§e- Active Forge Sessions: §f" + forgeCount);
            }

            default -> sender.sendMessage("§cUnknown list type: " + listType);
        }
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6§lSMCBlacksmith Info:");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Furnaces: §f" + plugin.getFurnaceManager().getFurnaceCount());
        sender.sendMessage("§7Active Forge Sessions: §f" + plugin.getForgeManager().getActiveSessionCount());
        sender.sendMessage("§7");
        sender.sendMessage("§7§lIntegrations:");
        sender.sendMessage("§7SMCCore: " + (plugin.hasSMCCore() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7CraftEngine: " + (plugin.hasCraftEngine() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Nexo: " + (plugin.hasNexo() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7PlaceholderAPI: " + (plugin.hasPAPI() ? "§aEnabled" : "§cDisabled"));
    }

    /**
     * Handles debug commands for administrators.
     */
    private void handleDebug(CommandSender sender, String[] args) {
        if (!sender.hasPermission("blacksmith.admin")) {
            sender.sendMessage("§cYou don't have permission to do this.");
            return;
        }

        if (args.length < 2) {
            // Toggle debug mode
            boolean debug = plugin.getConfigManager().getMainConfig().isDebugMode();
            sender.sendMessage("§7Debug mode is currently: " + (debug ? "§aENABLED" : "§cDISABLED"));
            sender.sendMessage("§7Debug subcommands: furnace, session, hooks, config");
            return;
        }

        String debugType = args[1].toLowerCase();

        switch (debugType) {
            case "furnace" -> debugFurnace(sender);
            case "session" -> debugSession(sender);
            case "hooks" -> debugHooks(sender);
            case "config" -> debugConfig(sender);
            case "cache" -> debugCache(sender);
            default -> sender.sendMessage("§cUnknown debug type: " + debugType);
        }
    }

    private void debugFurnace(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            sender.sendMessage("§cLook at a block.");
            return;
        }

        Location loc = targetBlock.getLocation();
        FurnaceInstance furnace = plugin.getFurnaceManager().getFurnace(loc);

        if (furnace == null) {
            sender.sendMessage("§cNo furnace found at this location.");
            return;
        }

        sender.sendMessage("§6§lFurnace Debug Info:");
        sender.sendMessage("§7ID: §f" + furnace.getId());
        sender.sendMessage("§7Type: §f" + furnace.getType().getId());
        sender.sendMessage("§7Location: §f" + formatLocation(loc));
        sender.sendMessage("§7Temperature: §f" + furnace.getCurrentTemperature() + "°C / " +
                furnace.getType().getMaxTemperature() + "°C");
        sender.sendMessage("§7Target Temp: §f" + furnace.getTargetTemperature() + "°C");
        sender.sendMessage("§7Ideal Range: §f" + furnace.getType().getMinIdealTemperature() + "-" +
                furnace.getType().getMaxIdealTemperature() + "°C");
        sender.sendMessage("§7Burning: §f" + furnace.isBurning());
        sender.sendMessage("§7Burn Time Left: §f" + furnace.getBurnTimeRemaining() + " ticks");
        sender.sendMessage("§7Current Recipe: §f" + (furnace.getCurrentRecipe() != null ?
                furnace.getCurrentRecipe().getId() : "none"));
        sender.sendMessage("§7Smelt Progress: §f" + String.format("%.1f%%", furnace.getSmeltProgress() * 100));
    }

    private void debugSession(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        ForgeSession session = plugin.getForgeManager().getSession(player.getUniqueId());

        if (session == null) {
            sender.sendMessage("§cYou don't have an active forge session.");
            return;
        }

        sender.sendMessage("§6§lForge Session Debug Info:");
        sender.sendMessage("§7Session ID: §f" + session.getSessionId());
        sender.sendMessage("§7Recipe: §f" + session.getRecipe().getId());
        sender.sendMessage("§7Hits: §f" + session.getHitsCompleted() + "/" + session.getTotalHits());
        sender.sendMessage("§7Perfect Hits: §f" + session.getPerfectHits());
        sender.sendMessage("§7Average Accuracy: §f" + String.format("%.1f%%", session.getAverageAccuracy() * 100));
        sender.sendMessage("§7Final Score: §f" + String.format("%.2f", session.calculateFinalScore()));
        sender.sendMessage("§7Current Star Rating: §f" + session.calculateStarRating() + " stars");
        sender.sendMessage("§7Progress: §f" + String.format("%.1f%%", session.getProgress() * 100));
        sender.sendMessage("§7Elapsed Time: §f" + (session.getElapsedTime() / 1000) + "s");
        sender.sendMessage("§7Active: §f" + session.isActive());

        // Show star thresholds if custom
        if (session.getRecipe().hasStarThresholds()) {
            sender.sendMessage("§7§lCustom Star Thresholds:");
            for (int i = 5; i >= 0; i--) {
                var threshold = session.getRecipe().getStarThreshold(i);
                if (threshold != null) {
                    sender.sendMessage("§7  " + i + "★: §fHits≥" + threshold.getMinPerfectHits() +
                            ", Acc≥" + String.format("%.0f%%", threshold.getMinAccuracy() * 100));
                }
            }
        }
    }

    private void debugHooks(CommandSender sender) {
        sender.sendMessage("§6§lIntegration Hooks Debug:");
        sender.sendMessage("§7PlaceholderAPI: " + (plugin.hasPAPI() ? "§aAvailable" : "§cNot Available"));
        sender.sendMessage("§7SMCCore: " + (plugin.hasSMCCore() ? "§aAvailable" : "§cNot Available"));
        sender.sendMessage("§7CraftEngine: " + (plugin.hasCraftEngine() ? "§aAvailable" : "§cNot Available"));
        sender.sendMessage("§7Nexo: " + (plugin.hasNexo() ? "§aAvailable" : "§cNot Available"));
        sender.sendMessage("§7Item Providers: §f" + plugin.getItemRegistry().getProviderCount());
    }

    private void debugConfig(CommandSender sender) {
        sender.sendMessage("§6§lConfiguration Debug:");
        sender.sendMessage("§7Furnace Types: §f" + plugin.getConfigManager().getFurnaceConfig().getFurnaceTypeCount());
        sender.sendMessage("§7Forge Recipes: §f" + plugin.getConfigManager().getBlacksmithConfig().getRecipeCount());
        sender.sendMessage("§7Repair Configs: §f" + plugin.getConfigManager().getGrindstoneConfig().getRepairConfigCount());
        sender.sendMessage("§7Fuel Types: §f" + plugin.getConfigManager().getFuelConfig().getFuelCount());
        sender.sendMessage("§7Language: §f" + plugin.getConfigManager().getMainConfig().getLanguage());
        sender.sendMessage("§7Tick Rate: §f" + plugin.getConfigManager().getFurnaceTickRate() + " ticks");
        sender.sendMessage("§7Bellows Cooldown: §f" + plugin.getConfigManager().getBellowsCooldown() + " ticks");

        if (plugin.getConfigManager().hasConfigErrors()) {
            sender.sendMessage("§c§lConfiguration has errors! Check console.");
        }
        if (plugin.getConfigManager().hasConfigWarnings()) {
            sender.sendMessage("§e§lConfiguration has warnings. Check console.");
        }
    }

    private void debugCache(CommandSender sender) {
        FurnaceManager fm = plugin.getFurnaceManager();
        sender.sendMessage("§6§lCache Debug:");
        sender.sendMessage("§7Recipe Cache: §f" + fm.getRecipeCache().getStats());
    }

    /**
     * Handles stats command showing performance metrics.
     */
    private void handleStats(CommandSender sender) {
        if (!sender.hasPermission("blacksmith.admin")) {
            sender.sendMessage("§cYou don't have permission to do this.");
            return;
        }

        sender.sendMessage("§6§l=== SMCBlacksmith Performance Stats ===");

        // Memory info
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMB = rt.maxMemory() / 1024 / 1024;
        sender.sendMessage("§7Memory: §f" + usedMB + "MB / " + maxMB + "MB");

        // Furnace stats
        FurnaceManager fm = plugin.getFurnaceManager();
        sender.sendMessage("§7");
        sender.sendMessage("§e§lFurnace Manager:");
        sender.sendMessage("§7  Active Furnaces: §f" + fm.getFurnaceCount());
        sender.sendMessage("§7  Temperature Bars: §f" + fm.getTemperatureBarCount());
        sender.sendMessage("§7  Open GUIs: §f" + fm.getOpenGUICount());
        sender.sendMessage("§7  Async Enabled: §f" + fm.isAsyncEnabled());

        // Recipe cache stats
        sender.sendMessage("§7  Recipe Cache: §f" + fm.getRecipeCache().getStats());

        // Forge stats
        sender.sendMessage("§7");
        sender.sendMessage("§e§lForge Manager:");
        sender.sendMessage("§7  Active Sessions: §f" + plugin.getForgeManager().getActiveSessionCount());

        // Integration status
        sender.sendMessage("§7");
        sender.sendMessage("§e§lIntegrations:");
        sender.sendMessage("§7  " + plugin.getHookSummary());

        sender.sendMessage("§6§l==========================================");
    }

    private String formatLocation(Location loc) {
        return String.format("%s, %d, %d, %d",
                loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "furnace", "forge", "list", "info"));
            if (sender.hasPermission("blacksmith.admin")) {
                completions.addAll(Arrays.asList("debug", "stats"));
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "furnace" -> completions.addAll(Arrays.asList("create", "open", "remove"));
                case "forge" -> {
                    Map<String, ForgeRecipe> recipes = plugin.getConfigManager().getBlacksmithConfig().getRecipes();
                    completions.addAll(recipes.keySet());
                }
                case "list" -> completions.addAll(Arrays.asList("furnaces", "recipes", "active"));
                case "debug" -> {
                    if (sender.hasPermission("blacksmith.admin")) {
                        completions.addAll(Arrays.asList("furnace", "session", "hooks", "config", "cache"));
                    }
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("furnace") && args[1].equalsIgnoreCase("create")) {
                Map<String, FurnaceType> types = plugin.getConfigManager().getFurnaceConfig().getFurnaceTypes();
                completions.addAll(types.keySet());
            }
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}