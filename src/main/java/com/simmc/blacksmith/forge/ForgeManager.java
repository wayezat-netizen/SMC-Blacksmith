package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages forge/blacksmithing sessions for players.
 * Handles the minigame mechanics, GUI updates, and session lifecycle.
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Starting and managing forge sessions</li>
 *   <li>Processing player strikes and calculating accuracy</li>
 *   <li>Handling material consumption and output generation</li>
 *   <li>Managing GUI animations</li>
 * </ul>
 *
 * @author SMCBlacksmith Team
 * @since 1.0.0
 */

public class ForgeManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    private final Map<UUID, ForgeSession> activeSessions;
    private final Map<UUID, ForgeMinigameGUI> activeGUIs;
    private final Map<UUID, BukkitTask> animationTasks;

    public ForgeManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.activeSessions = new ConcurrentHashMap<>();
        this.activeGUIs = new ConcurrentHashMap<>();
        this.animationTasks = new ConcurrentHashMap<>();
    }

    public boolean startSession(Player player, String recipeId, Location anvilLocation) {
        if (hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active forging session!");
            return false;
        }

        ForgeRecipe recipe = configManager.getBlacksmithConfig().getRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage("§cUnknown recipe: " + recipeId);
            return false;
        }

        if (!recipe.getPermission().isEmpty() && !player.hasPermission(recipe.getPermission())) {
            player.sendMessage("§cYou don't have permission to forge this item.");
            return false;
        }

        if (!consumeInputMaterials(player, recipe)) {
            MessageConfig messages = configManager.getMessageConfig();
            String itemName = recipe.getInputId();
            player.sendMessage(messages.getMissingMaterials(recipe.getInputAmount(), itemName));
            return false;
        }

        ForgeSession session = new ForgeSession(player.getUniqueId(), recipe, anvilLocation);
        activeSessions.put(player.getUniqueId(), session);

        ForgeMinigameGUI gui = new ForgeMinigameGUI(session);
        activeGUIs.put(player.getUniqueId(), gui);

        startAnimationTask(player.getUniqueId());

        gui.open(player);

        player.sendMessage("§aForging session started! Click the hammer to strike.");
        return true;
    }

    private void startAnimationTask(UUID playerId) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ForgeMinigameGUI gui = activeGUIs.get(playerId);
            if (gui != null) {
                gui.tickIndicator();
            }
        }, 0L, 2L);

        animationTasks.put(playerId, task);
    }

    public void processStrike(Player player) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = activeSessions.get(playerId);
        ForgeMinigameGUI gui = activeGUIs.get(playerId);

        if (session == null || gui == null || !session.isActive()) {
            return;
        }

        double hitPosition = gui.getCurrentHitPosition();
        boolean complete = session.processHit(hitPosition);

        gui.updateFrame();
        gui.updateProgress();

        double accuracy = session.getHitRecords().get(session.getHitRecords().size() - 1).accuracy();
        if (accuracy >= 0.9) {
            player.sendMessage("§aPerfect hit!");
        } else if (accuracy >= 0.7) {
            player.sendMessage("§eGood hit!");
        } else if (accuracy >= 0.4) {
            player.sendMessage("§6Okay hit.");
        } else {
            player.sendMessage("§cMissed!");
        }

        if (complete) {
            completeSession(player);
        }
    }

    private void completeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = activeSessions.get(playerId);

        if (session == null) return;

        int stars = session.calculateStarRating();
        ForgeResult result = session.getResultItem();

        if (result != null) {
            ItemStack item = itemRegistry.getItem(result.type(), result.id(), result.amount());
            if (item != null) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }

                String starDisplay = ColorUtil.formatStars(stars, 5);
                player.sendMessage("§aForging complete! Quality: " + starDisplay);

                String command = session.getRecipe().getRunAfterCommand();
                if (command != null && !command.isEmpty()) {
                    String parsed = command.replace("%player%", player.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            }
        }

        cleanupSession(playerId);
        player.closeInventory();
    }

    public void cancelSession(UUID playerId) {
        ForgeSession session = activeSessions.get(playerId);
        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            refundInputMaterials(player, session.getRecipe());
            player.sendMessage("§eForging session cancelled. Materials refunded.");
            player.closeInventory();
        }

        cleanupSession(playerId);
    }

    private void cleanupSession(UUID playerId) {
        activeSessions.remove(playerId);
        activeGUIs.remove(playerId);

        BukkitTask task = animationTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    public ForgeSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    public ForgeMinigameGUI getGUI(UUID playerId) {
        return activeGUIs.get(playerId);
    }

    private boolean consumeInputMaterials(Player player, ForgeRecipe recipe) {
        if (recipe.getInputId().isEmpty()) return true;

        ItemStack required = itemRegistry.getItem(recipe.getInputType(), recipe.getInputId(), recipe.getInputAmount());
        if (required == null) return true;

        int remaining = recipe.getInputAmount();
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            if (itemRegistry.matches(item, recipe.getInputType(), recipe.getInputId())) {
                int take = Math.min(remaining, item.getAmount());
                remaining -= take;
            }
        }

        if (remaining > 0) return false;

        remaining = recipe.getInputAmount();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            if (itemRegistry.matches(item, recipe.getInputType(), recipe.getInputId())) {
                int take = Math.min(remaining, item.getAmount());
                remaining -= take;

                int newAmount = item.getAmount() - take;
                if (newAmount <= 0) {
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(newAmount);
                }
            }
        }

        return true;
    }

    private void refundInputMaterials(Player player, ForgeRecipe recipe) {
        if (recipe.getInputId().isEmpty()) return;

        ItemStack refund = itemRegistry.getItem(recipe.getInputType(), recipe.getInputId(), recipe.getInputAmount());
        if (refund == null) return;

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(refund);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    public void cancelAllSessions() {
        Set<UUID> playerIds = new HashSet<>(activeSessions.keySet());

        for (UUID playerId : playerIds) {
            try {
                cancelSession(playerId);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to cancel session for " + playerId + ": " + e.getMessage());
            }
        }
    }

    public void reload() {
        // Configuration reloaded through ConfigManager
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}