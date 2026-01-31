package com.simmc.blacksmith.commands;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.BlacksmithConfig;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceType;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            sender.sendMessage("§cUsage: /blacksmith furnace <create|open> [type]");
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
            sender.sendMessage("§cUsage: /blacksmith list <furnaces|recipes>");
            return;
        }

        String listType = args[1].toLowerCase();

        switch (listType) {
            case "furnaces" -> {
                Map<String, FurnaceType> types = plugin.getConfigManager().getFurnaceConfig().getFurnaceTypes();
                sender.sendMessage("§6§lFurnace Types (" + types.size() + "):");
                for (FurnaceType type : types.values()) {
                    sender.sendMessage("§e- " + type.getId() + " §7(Max Temp: " + type.getMaxTemperature() + "°C, Recipes: " + type.getRecipeCount() + ")");
                }
            }

            case "recipes" -> {
                BlacksmithConfig config = plugin.getConfigManager().getBlacksmithConfig();
                Map<String, ForgeRecipe> recipes = config.getRecipes();
                sender.sendMessage("§6§lForge Recipes (" + recipes.size() + "):");
                for (ForgeRecipe recipe : recipes.values()) {
                    String perm = recipe.getPermission().isEmpty() ? "none" : recipe.getPermission();
                    sender.sendMessage("§e- " + recipe.getId() + " §7(Hits: " + recipe.getHits() + ", Perm: " + perm + ")");
                }
            }

            default -> sender.sendMessage("§cUnknown list type: " + listType);
        }
    }

    private void handleInfo(CommandSender sender) {
        sender.sendMessage("§6§lSMCBlacksmith Info:");
        sender.sendMessage("§7Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§7Furnaces: §f" + plugin.getFurnaceManager().getFurnaceCount());
        sender.sendMessage("§7Active Forge Sessions: §f" + plugin.getForgeManager().getActiveSessionCount());
        sender.sendMessage("§7SMCCore: §f" + (plugin.hasSMCCore() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7Nexo: §f" + (plugin.hasNexo() ? "§aEnabled" : "§cDisabled"));
        sender.sendMessage("§7PlaceholderAPI: §f" + (plugin.hasPAPI() ? "§aEnabled" : "§cDisabled"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "furnace", "forge", "list", "info"));
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "furnace" -> completions.addAll(Arrays.asList("create", "open"));
                case "forge" -> {
                    Map<String, ForgeRecipe> recipes = plugin.getConfigManager().getBlacksmithConfig().getRecipes();
                    completions.addAll(recipes.keySet());
                }
                case "list" -> completions.addAll(Arrays.asList("furnaces", "recipes"));
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