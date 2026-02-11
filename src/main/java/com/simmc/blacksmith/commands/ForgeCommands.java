package com.simmc.blacksmith.commands;

import com.simmc.blacksmith.forge.ForgeHammer;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.ForgeSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Forge-related commands for testing and usage.
 */
public class ForgeCommands implements CommandExecutor, TabCompleter {

    private static final Set<Material> ANVIL_MATERIALS = EnumSet.of(
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    );

    private static final List<String> SUBCOMMANDS = List.of("start", "cancel", "hammer", "list", "debug");
    private static final List<String> HAMMER_TYPES = List.of("normal", "master");

    private final ForgeManager forgeManager;
    private final List<String> recipeIds;

    public ForgeCommands(ForgeManager forgeManager, List<String> recipeIds) {
        this.forgeManager = forgeManager;
        this.recipeIds = new ArrayList<>(recipeIds);
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

    // ==================== SUBCOMMAND HANDLERS ====================

    private void handleStart(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /forge start <recipe>");
            player.sendMessage("§7Available: " + String.join(", ", recipeIds));
            return;
        }

        String recipeId = args[1];
        Location anvilLocation = findAnvilLocation(player);

        if (anvilLocation == null) {
            player.sendMessage("§cNo anvil found nearby! Look at or stand near an anvil.");
            return;
        }

        forgeManager.startSession(player, recipeId, anvilLocation);
    }

    private void handleCancel(Player player) {
        if (!forgeManager.hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cYou don't have an active forging session.");
            return;
        }

        forgeManager.cancelSession(player.getUniqueId());
        player.sendMessage("§eForging session cancelled.");
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
        if (recipeIds.isEmpty()) {
            player.sendMessage("§7No recipes configured.");
        } else {
            recipeIds.forEach(id -> player.sendMessage("§7- §e" + id));
        }
    }

    private void handleDebug(Player player) {
        player.sendMessage("§6§l=== Forge Debug ===");
        player.sendMessage("§7Active Sessions: §f" + forgeManager.getActiveSessionCount());

        ForgeSession session = forgeManager.getSession(player.getUniqueId());
        if (session != null) {
            displaySessionInfo(player, session);
        } else {
            player.sendMessage("§7You have no active session.");
        }
    }

    private void displaySessionInfo(Player player, ForgeSession session) {
        player.sendMessage("§7Your Session:");
        player.sendMessage("§7  Recipe: §f" + session.getRecipe().getId());
        player.sendMessage("§7  Hits: §f" + session.getHitsCompleted() + "/" + session.getTotalHits());
        player.sendMessage("§7  Accuracy: §f" + formatPercent(session.getAverageAccuracy()));
        player.sendMessage("§7  Perfect Hits: §f" + session.getPerfectHits());
    }

    // ==================== UTILITIES ====================

    private Location findAnvilLocation(Player player) {
        // First check what player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null && ANVIL_MATERIALS.contains(targetBlock.getType())) {
            return targetBlock.getLocation();
        }

        // Then search nearby
        return findNearbyAnvil(player.getLocation(), 3);
    }

    private Location findNearbyAnvil(Location center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location check = center.clone().add(x, y, z);
                    if (ANVIL_MATERIALS.contains(check.getBlock().getType())) {
                        return check;
                    }
                }
            }
        }
        return null;
    }

    private String formatPercent(double value) {
        return String.format("%.1f%%", value * 100);
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6§l=== Forge Commands ===");
        player.sendMessage("§e/forge start <recipe> §7- Start forging");
        player.sendMessage("§e/forge cancel §7- Cancel current session");
        player.sendMessage("§e/forge hammer [type] §7- Get a forge hammer");
        player.sendMessage("§e/forge list §7- List available recipes");
        player.sendMessage("§e/forge debug §7- Debug information");
    }

    // ==================== TAB COMPLETION ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase()) {
                case "start" -> filterStartsWith(recipeIds, args[1]);
                case "hammer" -> filterStartsWith(HAMMER_TYPES, args[1]);
                default -> List.of();
            };
        }

        return List.of();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }
}