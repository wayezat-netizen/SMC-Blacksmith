package com.simmc.blacksmith.commands;

import com.simmc.blacksmith.forge.ForgeHammer;
import com.simmc.blacksmith.forge.ForgeManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Forge-related commands for testing and usage.
 */
public class ForgeCommands implements CommandExecutor, TabCompleter {

    private final ForgeManager forgeManager;
    private final List<String> recipeIds;

    public ForgeCommands(ForgeManager forgeManager, List<String> recipeIds) {
        this.forgeManager = forgeManager;
        this.recipeIds = recipeIds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(player, args);
            case "cancel" -> handleCancel(player);
            case "hammer" -> handleHammer(player, args);
            case "list" -> handleList(player);
            case "debug" -> handleDebug(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleStart(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /forge start <recipe>");
            player.sendMessage("§7Available: " + String.join(", ", recipeIds));
            return;
        }

        String recipeId = args[1];

        // Find anvil near player or where they're looking
        Block targetBlock = player.getTargetBlockExact(5);
        Location anvilLocation;

        if (targetBlock != null && targetBlock.getType() == Material.ANVIL) {
            anvilLocation = targetBlock.getLocation();
        } else {
            // Look for anvil nearby
            anvilLocation = findNearbyAnvil(player.getLocation(), 3);
            if (anvilLocation == null) {
                player.sendMessage("§cNo anvil found nearby! Look at or stand near an anvil.");
                return;
            }
        }

        boolean started = forgeManager.startSession(player, recipeId, anvilLocation);
        if (!started) {
            // Error message already sent by forgeManager
        }
    }

    private void handleCancel(Player player) {
        if (!forgeManager.hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cYou don't have an active forging session.");
            return;
        }

        forgeManager.cancelSession(player.getUniqueId());
    }

    private void handleHammer(Player player, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "normal";

        switch (type) {
            case "master" -> {
                player.getInventory().addItem(ForgeHammer.createMasterHammer());
                player.sendMessage("§aReceived Master Forge Hammer!");
            }
            default -> {
                player.getInventory().addItem(ForgeHammer.createDefaultHammer());
                player.sendMessage("§aReceived Forge Hammer!");
            }
        }
    }

    private void handleList(Player player) {
        player.sendMessage("§6§l=== Forge Recipes ===");
        for (String id : recipeIds) {
            player.sendMessage("§7- §e" + id);
        }
    }

    private void handleDebug(Player player) {
        player.sendMessage("§6§l=== Forge Debug ===");
        player.sendMessage("§7Active Sessions: §f" + forgeManager.getActiveSessionCount());

        if (forgeManager.hasActiveSession(player.getUniqueId())) {
            var session = forgeManager.getSession(player.getUniqueId());
            player.sendMessage("§7Your Session:");
            player.sendMessage("§7  Recipe: §f" + session.getRecipe().getId());
            player.sendMessage("§7  Hits: §f" + session.getHitsCompleted() + "/" + session.getTotalHits());
            player.sendMessage("§7  Accuracy: §f" + String.format("%.1f%%", session.getAverageAccuracy() * 100));
            player.sendMessage("§7  Perfect Hits: §f" + session.getPerfectHits());
        } else {
            player.sendMessage("§7You have no active session.");
        }
    }

    private Location findNearbyAnvil(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location check = center.clone().add(x, y, z);
                    if (check.getBlock().getType() == Material.ANVIL ||
                            check.getBlock().getType() == Material.CHIPPED_ANVIL ||
                            check.getBlock().getType() == Material.DAMAGED_ANVIL) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== Forge Commands ===");
        player.sendMessage("§e/forge start <recipe> §7- Start forging");
        player.sendMessage("§e/forge cancel §7- Cancel current session");
        player.sendMessage("§e/forge hammer [type] §7- Get a forge hammer");
        player.sendMessage("§e/forge list §7- List available recipes");
        player.sendMessage("§e/forge debug §7- Debug information");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("start", "cancel", "hammer", "list", "debug"), args[0]);
        }
        if (args.length == 2) {
            if ("start".equalsIgnoreCase(args[0])) {
                return filterStartsWith(recipeIds, args[1]);
            }
            if ("hammer".equalsIgnoreCase(args[0])) {
                return filterStartsWith(Arrays.asList("normal", "master"), args[1]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}