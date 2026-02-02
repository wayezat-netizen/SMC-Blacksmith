package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages forge/blacksmithing sessions for players.
 * Handles the minigame mechanics, GUI updates, and session lifecycle.
 */
public class ForgeManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    // FIXED: Use ConcurrentHashMap for thread safety
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

    /**
     * Starts a new forging session for a player.
     */
    public boolean startSession(Player player, String recipeId, Location anvilLocation) {
        if (player == null || recipeId == null) {
            plugin.getLogger().warning("startSession called with null player or recipeId");
            return false;
        }

        MessageConfig messages = configManager.getMessageConfig();

        if (hasActiveSession(player.getUniqueId())) {
            // FIXED: Use configurable message
            player.sendMessage(messages.getForgeSessionActive());
            return false;
        }

        ForgeRecipe recipe = configManager.getBlacksmithConfig().getRecipe(recipeId);
        if (recipe == null) {
            // FIXED: Use configurable message
            player.sendMessage(messages.getForgeUnknownRecipe(recipeId));
            return false;
        }

        if (!recipe.getPermission().isEmpty() && !player.hasPermission(recipe.getPermission())) {
            player.sendMessage(messages.getNoPermission());
            return false;
        }

        if (!consumeInputMaterials(player, recipe)) {
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

        // FIXED: Use configurable message
        player.sendMessage(messages.getForgeStarted());

        plugin.getLogger().fine("Started forge session for " + player.getName() + " with recipe: " + recipeId);
        return true;
    }

    /**
     * Starts the animation task for the forge GUI.
     */
    private void startAnimationTask(UUID playerId) {
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ForgeMinigameGUI gui = activeGUIs.get(playerId);
            if (gui != null) {
                try {
                    gui.tickIndicator();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error in forge animation task", e);
                }
            }
        }, 0L, 2L);

        animationTasks.put(playerId, task);
    }

    /**
     * Processes a hammer strike from the player.
     */
    public void processStrike(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        ForgeSession session = activeSessions.get(playerId);
        ForgeMinigameGUI gui = activeGUIs.get(playerId);

        if (session == null || gui == null || !session.isActive()) {
            return;
        }

        MessageConfig messages = configManager.getMessageConfig();

        double hitPosition = gui.getCurrentHitPosition();
        boolean complete = session.processHit(hitPosition);

        gui.updateFrame();
        gui.updateProgress();

        // Send hit feedback
        if (!session.getHitRecords().isEmpty()) {
            double accuracy = session.getHitRecords().get(session.getHitRecords().size() - 1).accuracy();
            if (accuracy >= 0.9) {
                player.sendMessage(messages.getForgePerfectHit());
            } else if (accuracy >= 0.7) {
                player.sendMessage(messages.getForgeGoodHit());
            } else if (accuracy < 0.3) {
                player.sendMessage(messages.getForgeMiss());
            }
        }

        if (complete) {
            completeSession(player);
        }
    }

    /**
     * Completes a forging session and gives the result.
     */
    private void completeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = activeSessions.get(playerId);

        if (session == null) return;

        ForgeResult result = session.getResultItem();
        if (result != null) {
            ItemStack item = itemRegistry.getItem(result.type(), result.id(), result.amount());
            if (item != null) {
                player.getInventory().addItem(item);

                MessageConfig messages = configManager.getMessageConfig();
                player.sendMessage(messages.getForgeComplete(session.calculateStarRating()));
            }
        }

        // Run after command if configured
        String afterCommand = session.getRecipe().getRunAfterCommand();
        if (afterCommand != null && !afterCommand.isEmpty()) {
            String command = afterCommand.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        cancelSession(playerId);
    }

    /**
     * Cancels a forging session.
     */
    public void cancelSession(UUID playerId) {
        if (playerId == null) return;

        ForgeSession session = activeSessions.remove(playerId);
        if (session != null) {
            session.cancel();
        }

        ForgeMinigameGUI gui = activeGUIs.remove(playerId);
        if (gui != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.closeInventory();
            }
        }

        BukkitTask task = animationTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Cancels all active forging sessions.
     * FIXED: Proper exception handling to prevent cascade failures.
     */
    public void cancelAllSessions() {
        Set<UUID> playerIds = new HashSet<>(activeSessions.keySet());

        for (UUID playerId : playerIds) {
            try {
                cancelSession(playerId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error cancelling session for " + playerId, e);
            }
        }

        // Clear any remaining entries
        activeSessions.clear();
        activeGUIs.clear();
        animationTasks.clear();
    }

    /**
     * Checks if a player has an active forging session.
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Gets the active session for a player.
     */
    public ForgeSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    /**
     * Consumes input materials for a recipe.
     */
    private boolean consumeInputMaterials(Player player, ForgeRecipe recipe) {
        String inputType = recipe.getInputType();
        String inputId = recipe.getInputId();
        int required = recipe.getInputAmount();

        if (inputId == null || inputId.isEmpty()) {
            return true; // No input required
        }

        // Check if player has enough
        int found = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (itemRegistry.matches(item, inputType, inputId)) {
                found += item.getAmount();
            }
        }

        if (found < required) {
            return false;
        }

        // Consume items
        int remaining = required;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;

            if (itemRegistry.matches(item, inputType, inputId)) {
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

    /**
     * Reloads the forge manager.
     */
    public void reload() {
        // Cancel all active sessions on reload
        cancelAllSessions();
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}