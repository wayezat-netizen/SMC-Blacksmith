package com.simmc.blacksmith.commands;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.BlacksmithConfig;
import com.simmc.blacksmith.forge.ForgeCategory;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ForgeSession;
import com.simmc.blacksmith.forge.gui.ForgeCategoryGUI;
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
 */
public class BlacksmithCommand implements CommandExecutor, TabCompleter {

    private static final List<String> MAIN_COMMANDS = List.of("reload", "furnace", "forge", "list", "info");
    private static final List<String> ADMIN_COMMANDS = List.of("debug", "stats");
    private static final List<String> FURNACE_ACTIONS = List.of("create", "open", "remove");
    private static final List<String> LIST_TYPES = List.of("furnaces", "recipes", "active");
    private static final List<String> DEBUG_TYPES = List.of("furnace", "session", "hooks", "config");

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

        switch (args[0].toLowerCase()) {
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

    // ==================== HELP ====================

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§lSMCBlacksmith Commands:");
        sender.sendMessage("§e/bs reload §7- Reload configuration");
        sender.sendMessage("§e/bs furnace create <type> §7- Create furnace");
        sender.sendMessage("§e/bs furnace open §7- Open furnace GUI");
        sender.sendMessage("§e/bs forge [recipe] §7- Start forging");
        sender.sendMessage("§e/bs list <furnaces|recipes> §7- List items");
        sender.sendMessage("§e/bs info §7- Show plugin info");

        if (sender.hasPermission("blacksmith.admin")) {
            sender.sendMessage("§c§lAdmin:");
            sender.sendMessage("§e/bs debug <type> §7- Debug info");
            sender.sendMessage("§e/bs stats §7- Performance stats");
        }
    }

    // ==================== RELOAD ====================

    private void handleReload(CommandSender sender) {
        if (!checkPermission(sender, "blacksmith.admin")) return;

        plugin.reload();
        sender.sendMessage("§aConfiguration reloaded.");
    }

    // ==================== FURNACE ====================

    private void handleFurnace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bs furnace <create|open|remove> [type]");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "create" -> handleFurnaceCreate(player, args);
            case "open" -> handleFurnaceOpen(player);
            case "remove" -> handleFurnaceRemove(player);
            default -> sender.sendMessage("§cUnknown action: " + args[1]);
        }
    }

    private void handleFurnaceCreate(Player player, String[] args) {
        if (!checkPermission(player, "blacksmith.furnace.create")) return;

        if (args.length < 3) {
            player.sendMessage("§cUsage: /bs furnace create <type>");
            return;
        }

        String typeId = args[2];
        Optional<FurnaceType> typeOpt = plugin.getConfigManager().getFurnaceConfig().getFurnaceType(typeId);

        if (typeOpt.isEmpty()) {
            player.sendMessage("§cUnknown furnace type: " + typeId);
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cLook at a block to create furnace.");
            return;
        }

        FurnaceInstance instance = plugin.getFurnaceManager().createFurnace(typeId, targetBlock.getLocation());
        player.sendMessage(instance != null
                ? "§aCreated " + typeId + " furnace."
                : "§cFailed to create furnace.");
    }

    private void handleFurnaceOpen(Player player) {
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cLook at a block.");
            return;
        }

        Location loc = targetBlock.getLocation();
        if (!plugin.getFurnaceManager().isFurnace(loc)) {
            player.sendMessage("§cNo furnace at this location.");
            return;
        }

        plugin.getFurnaceManager().openFurnaceGUI(player, loc);
    }

    private void handleFurnaceRemove(Player player) {
        if (!checkPermission(player, "blacksmith.admin")) return;

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            player.sendMessage("§cLook at a block.");
            return;
        }

        Location loc = targetBlock.getLocation();
        if (!plugin.getFurnaceManager().isFurnace(loc)) {
            player.sendMessage("§cNo furnace at this location.");
            return;
        }

        plugin.getFurnaceManager().removeFurnace(loc);
        player.sendMessage("§aFurnace removed.");
    }

    // ==================== FORGE ====================

    private void handleForge(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }

        if (args.length < 2) {
            openCategoryGUI(player);
            return;
        }

        plugin.getForgeManager().startSession(player, args[1], player.getLocation());
    }

    private void openCategoryGUI(Player player) {
        Map<String, ForgeCategory> categories = plugin.getConfigManager().getBlacksmithConfig().getCategories();
        new ForgeCategoryGUI(categories).open(player);
    }

    // ==================== LIST ====================

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /bs list <furnaces|recipes|active>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "furnaces" -> listFurnaces(sender);
            case "recipes" -> listRecipes(sender);
            case "active" -> listActive(sender);
            default -> sender.sendMessage("§cUnknown list type: " + args[1]);
        }
    }

    private void listFurnaces(CommandSender sender) {
        Map<String, FurnaceType> types = plugin.getConfigManager().getFurnaceConfig().getFurnaceTypes();
        sender.sendMessage("§6§lFurnace Types (" + types.size() + "):");

        types.values().forEach(type -> sender.sendMessage(String.format(
                "§e- %s §7(Max: %d°C, Ideal: %d-%d°C, Recipes: %d)",
                type.getId(), type.getMaxTemperature(),
                type.getMinIdealTemperature(), type.getMaxIdealTemperature(),
                type.getRecipeCount()
        )));
    }

    private void listRecipes(CommandSender sender) {
        Map<String, ForgeRecipe> recipes = plugin.getConfigManager().getBlacksmithConfig().getRecipes();
        sender.sendMessage("§6§lForge Recipes (" + recipes.size() + "):");

        recipes.values().forEach(recipe -> sender.sendMessage(String.format(
                "§e- %s §7(Hits: %d, Perm: %s)",
                recipe.getId(), recipe.getHits(),
                recipe.hasPermission() ? recipe.getPermission() : "none"
        )));
    }

    private void listActive(CommandSender sender) {
        if (!checkPermission(sender, "blacksmith.admin")) return;

        sender.sendMessage("§6§lActive Sessions:");
        sender.sendMessage("§e- Furnaces: §f" + plugin.getFurnaceManager().getFurnaceCount());
        sender.sendMessage("§e- Open GUIs: §f" + plugin.getFurnaceManager().getOpenGUICount());
        sender.sendMessage("§e- Forge Sessions: §f" + plugin.getForgeManager().getActiveSessionCount());
    }

    // ==================== INFO ====================

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6§lSMCBlacksmith v" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Furnaces: §f" + plugin.getFurnaceManager().getFurnaceCount());
        sender.sendMessage("§7Forge Sessions: §f" + plugin.getForgeManager().getActiveSessionCount());
        sender.sendMessage("");
        sender.sendMessage("§7§lIntegrations:");
        sender.sendMessage("§7SMCCore: " + formatEnabled(plugin.hasSMCCore()));
        sender.sendMessage("§7CraftEngine: " + formatEnabled(plugin.hasCraftEngine()));
        sender.sendMessage("§7Nexo: " + formatEnabled(plugin.hasNexo()));
        sender.sendMessage("§7PlaceholderAPI: " + formatEnabled(plugin.hasPAPI()));
    }

    // ==================== DEBUG ====================

    private void handleDebug(CommandSender sender, String[] args) {
        if (!checkPermission(sender, "blacksmith.admin")) return;

        if (args.length < 2) {
            sender.sendMessage("§7Debug types: furnace, session, hooks, config");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "furnace" -> debugFurnace(sender);
            case "session" -> debugSession(sender);
            case "hooks" -> debugHooks(sender);
            case "config" -> debugConfig(sender);
            default -> sender.sendMessage("§cUnknown debug type.");
        }
    }

    private void debugFurnace(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            sender.sendMessage("§cLook at a block.");
            return;
        }

        FurnaceInstance furnace = plugin.getFurnaceManager().getFurnace(targetBlock.getLocation());
        if (furnace == null) {
            sender.sendMessage("§cNo furnace found.");
            return;
        }

        FurnaceType type = furnace.getType();
        sender.sendMessage("§6§lFurnace Debug:");
        sender.sendMessage("§7Type: §f" + type.getId());
        sender.sendMessage("§7Temp: §f" + furnace.getCurrentTemperature() + "/" + type.getMaxTemperature() + "°C");
        sender.sendMessage("§7Target: §f" + furnace.getTargetTemperature() + "°C");
        sender.sendMessage("§7Bellows Boost: §f" + furnace.getBellowsBoost() + "°C");
        sender.sendMessage("§7Ideal: §f" + type.getMinIdealTemperature() + "-" + type.getMaxIdealTemperature() + "°C");
        sender.sendMessage("§7Burning: §f" + furnace.isBurning());
        sender.sendMessage("§7Recipe: §f" + (furnace.getCurrentRecipe() != null ? furnace.getCurrentRecipe().getId() : "none"));
        sender.sendMessage("§7Progress: §f" + formatPercent(furnace.getSmeltProgress()));
    }

    private void debugSession(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
            return;
        }

        ForgeSession session = plugin.getForgeManager().getSession(player.getUniqueId());
        if (session == null) {
            sender.sendMessage("§cNo active forge session.");
            return;
        }

        sender.sendMessage("§6§lForge Session Debug:");
        sender.sendMessage("§7Recipe: §f" + session.getRecipe().getId());
        sender.sendMessage("§7Hits: §f" + session.getHitsCompleted() + "/" + session.getTotalHits());
        sender.sendMessage("§7Perfect: §f" + session.getPerfectHits());
        sender.sendMessage("§7Accuracy: §f" + formatPercent(session.getAverageAccuracy()));
        sender.sendMessage("§7Star Rating: §f" + session.calculateStarRating() + "★");
    }

    private void debugHooks(CommandSender sender) {
        sender.sendMessage("§6§lIntegration Hooks:");
        sender.sendMessage("§7PlaceholderAPI: " + formatEnabled(plugin.hasPAPI()));
        sender.sendMessage("§7SMCCore: " + formatEnabled(plugin.hasSMCCore()));
        sender.sendMessage("§7CraftEngine: " + formatEnabled(plugin.hasCraftEngine()));
        sender.sendMessage("§7Nexo: " + formatEnabled(plugin.hasNexo()));
        sender.sendMessage("§7Item Providers: §f" + plugin.getItemRegistry().getProviderCount());
    }

    private void debugConfig(CommandSender sender) {
        var cm = plugin.getConfigManager();
        sender.sendMessage("§6§lConfiguration:");
        sender.sendMessage("§7Furnace Types: §f" + cm.getFurnaceConfig().getFurnaceTypeCount());
        sender.sendMessage("§7Forge Recipes: §f" + cm.getBlacksmithConfig().getRecipeCount());
        sender.sendMessage("§7Categories: §f" + cm.getBlacksmithConfig().getCategoryCount());
        sender.sendMessage("§7Bellows: §f" + cm.getBellowsConfig().getBellowsTypeCount());
        sender.sendMessage("§7Hammers: §f" + cm.getHammerConfig().getHammerTypeCount());

        if (cm.hasConfigErrors()) {
            sender.sendMessage("§c§lConfiguration has errors!");
        }
    }

    // ==================== STATS ====================

    private void handleStats(CommandSender sender) {
        if (!checkPermission(sender, "blacksmith.admin")) return;

        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMB = rt.maxMemory() / 1024 / 1024;

        sender.sendMessage("§6§l=== SMCBlacksmith Stats ===");
        sender.sendMessage("§7Memory: §f" + usedMB + "/" + maxMB + "MB");
        sender.sendMessage("§7Furnaces: §f" + plugin.getFurnaceManager().getFurnaceCount());
        sender.sendMessage("§7Forge Sessions: §f" + plugin.getForgeManager().getActiveSessionCount());
        sender.sendMessage("§7Quench Sessions: §f" + plugin.getQuenchingManager().getActiveSessionCount());
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(MAIN_COMMANDS);
            if (sender.hasPermission("blacksmith.admin")) {
                completions.addAll(ADMIN_COMMANDS);
            }
        } else if (args.length == 2) {
            completions.addAll(getSecondArgCompletions(sender, args[0]));
        } else if (args.length == 3) {
            completions.addAll(getThirdArgCompletions(args));
        }

        return filterStartsWith(completions, args[args.length - 1]);
    }

    private List<String> getSecondArgCompletions(CommandSender sender, String firstArg) {
        return switch (firstArg.toLowerCase()) {
            case "furnace" -> new ArrayList<>(FURNACE_ACTIONS);
            case "forge" -> new ArrayList<>(plugin.getConfigManager().getBlacksmithConfig().getRecipeIds());
            case "list" -> new ArrayList<>(LIST_TYPES);
            case "debug" -> sender.hasPermission("blacksmith.admin") ? new ArrayList<>(DEBUG_TYPES) : List.of();
            default -> List.of();
        };
    }

    private List<String> getThirdArgCompletions(String[] args) {
        if (args[0].equalsIgnoreCase("furnace") && args[1].equalsIgnoreCase("create")) {
            return new ArrayList<>(plugin.getConfigManager().getFurnaceConfig().getTypeIds());
        }
        return List.of();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    // ==================== UTILITIES ====================

    private boolean checkPermission(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cNo permission.");
            return false;
        }
        return true;
    }

    private String formatEnabled(boolean enabled) {
        return enabled ? "§aEnabled" : "§cDisabled";
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }
}