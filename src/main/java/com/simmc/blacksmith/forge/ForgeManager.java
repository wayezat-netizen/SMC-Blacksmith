package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.forge.display.ForgeDisplay;
import com.simmc.blacksmith.forge.display.ForgeParticles;
import com.simmc.blacksmith.forge.display.ForgeSounds;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages forge/blacksmithing sessions with 3D world display.
 */
public class ForgeManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    private final Map<UUID, ForgeSession> activeSessions;
    private final Map<UUID, ForgeDisplay> activeDisplays;
    private BukkitTask tickTask;

    // Particle tick counter (don't spawn every tick)
    private int particleTick = 0;

    public ForgeManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.activeSessions = new ConcurrentHashMap<>();
        this.activeDisplays = new ConcurrentHashMap<>();

        startTickTask();
    }

    /**
     * Starts the main tick task for all forge displays.
     */
    private void startTickTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    /**
     * Main tick - updates all active forge displays.
     */
    private void tick() {
        particleTick++;

        for (Map.Entry<UUID, ForgeSession> entry : activeSessions.entrySet()) {
            UUID playerId = entry.getKey();
            ForgeSession session = entry.getValue();
            ForgeDisplay display = activeDisplays.get(playerId);

            if (display == null || !display.isValid()) continue;
            if (!session.isActive()) continue;

            // Update display
            display.tick(session, session.getCurrentTargetPosition());

            // Spawn ambient particles (every 5 ticks)
            if (particleTick % 5 == 0) {
                double intensity = 0.5 + session.getProgress() * 0.5;
                ForgeParticles.spawnAmbientHeat(display.getAnvilLocation(), intensity);
            }

            // Heat shimmer (every 10 ticks)
            if (particleTick % 10 == 0) {
                ForgeParticles.spawnHeatShimmer(display.getAnvilLocation());
            }

            // Ambient sound (every 40 ticks / 2 seconds)
            if (particleTick % 40 == 0) {
                ForgeSounds.playAmbient(display.getAnvilLocation());
            }
        }
    }

    /**
     * Starts a new 3D forging session.
     */
    public boolean startSession(Player player, String recipeId, Location anvilLocation) {
        if (player == null || recipeId == null) {
            return false;
        }

        MessageConfig messages = configManager.getMessageConfig();

        // Check for existing session
        if (hasActiveSession(player.getUniqueId())) {
            player.sendMessage(messages.getForgeSessionActive());
            return false;
        }

        // Get recipe
        ForgeRecipe recipe = configManager.getBlacksmithConfig().getRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(messages.getForgeUnknownRecipe(recipeId));
            return false;
        }

        // Check permission
        if (recipe.hasPermission() && !player.hasPermission(recipe.getPermission())) {
            player.sendMessage(messages.getNoPermission());
            return false;
        }

        // Check PAPI condition
        if (!checkPAPICondition(player, recipe)) {
            player.sendMessage(messages.getConditionNotMet());
            return false;
        }

        // Check and consume input materials
        if (!consumeInputMaterials(player, recipe)) {
            String itemName = recipe.getInputId();
            player.sendMessage(messages.getMissingMaterials(recipe.getInputAmount(), itemName));
            return false;
        }

        // Create session
        ForgeSession session = new ForgeSession(player.getUniqueId(), recipe, anvilLocation);
        activeSessions.put(player.getUniqueId(), session);

        // Create 3D display
        ForgeDisplay display = new ForgeDisplay(player.getUniqueId(), anvilLocation, recipe);
        display.spawn();
        activeDisplays.put(player.getUniqueId(), display);

        // Play start sound
        ForgeSounds.playSessionStart(player, anvilLocation);

        player.sendMessage(messages.getForgeStarted());
        player.sendMessage("§e§lLEFT CLICK §7near the anvil to strike!");

        return true;
    }

    /**
     * Processes a hammer strike from the player.
     */
    public void processStrike(Player player) {
        if (player == null) return;

        UUID playerId = player.getUniqueId();
        ForgeSession session = activeSessions.get(playerId);
        ForgeDisplay display = activeDisplays.get(playerId);

        if (session == null || display == null || !session.isActive()) {
            return;
        }

        // Check player is near the anvil
        if (player.getLocation().distanceSquared(display.getAnvilLocation()) > 16) { // 4 blocks
            player.sendMessage("§cYou're too far from the anvil!");
            return;
        }

        MessageConfig messages = configManager.getMessageConfig();

        // Get hit position from display indicator
        double hitPosition = display.getIndicatorPosition();
        double accuracy = session.calculateAccuracy(hitPosition);
        boolean complete = session.processHit(hitPosition);

        // Visual feedback
        display.onStrike(accuracy);
        ForgeParticles.spawnStrikeEffect(display.getAnvilLocation(), accuracy);
        ForgeSounds.playStrike(player, display.getAnvilLocation(), accuracy);

        // Send hit feedback
        if (accuracy >= 0.9) {
            player.sendMessage(messages.getForgePerfectHit());
        } else if (accuracy >= 0.7) {
            player.sendMessage(messages.getForgeGoodHit());
        } else if (accuracy >= 0.4) {
            player.sendMessage(messages.getForgeOkayHit());
        } else {
            player.sendMessage(messages.getForgeMiss());
        }

        // Reset instruction text after short delay
        Bukkit.getScheduler().runTaskLater(plugin, display::resetInstructionText, 20L);

        if (complete) {
            completeSession(player);
        }
    }

    /**
     * Completes a forging session.
     */
    private void completeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = activeSessions.get(playerId);
        ForgeDisplay display = activeDisplays.get(playerId);

        if (session == null) return;

        MessageConfig messages = configManager.getMessageConfig();
        int stars = session.calculateStarRating();
        ForgeResult result = session.getResultItem();

        // Show completion on display
        if (display != null) {
            display.showCompletion(stars);
            ForgeParticles.spawnCompletionEffect(display.getAnvilLocation(), stars);
            ForgeSounds.playCompletion(player, display.getAnvilLocation(), stars);
        }

        // Give result item
        if (result != null) {
            ItemStack item = itemRegistry.getItem(result.type(), result.id(), result.amount());
            if (item != null) {
                HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                for (ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }

                String starDisplay = ColorUtil.formatStars(stars, 5);
                player.sendMessage(messages.getForgeComplete(stars, starDisplay));

                // Execute after command
                String command = session.getRecipe().getRunAfterCommand();
                if (command != null && !command.isEmpty()) {
                    String parsed = command
                            .replace("%player%", player.getName())
                            .replace("<player>", player.getName())
                            .replace("%stars%", String.valueOf(stars))
                            .replace("<stars>", String.valueOf(stars));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            }
        }

        // Cleanup after delay (let player see the completion)
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanupSession(playerId), 60L);
    }

    /**
     * Cancels a forging session.
     */
    public void cancelSession(UUID playerId) {
        ForgeSession session = activeSessions.get(playerId);
        ForgeDisplay display = activeDisplays.get(playerId);

        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            refundInputMaterials(player, session.getRecipe());
            player.sendMessage(configManager.getMessageConfig().getForgeRefunded());

            if (display != null) {
                ForgeSounds.playSessionCancel(player, display.getAnvilLocation());
            }
        }

        cleanupSession(playerId);
    }

    /**
     * Cleans up session data and entities.
     */
    private void cleanupSession(UUID playerId) {
        activeSessions.remove(playerId);

        ForgeDisplay display = activeDisplays.remove(playerId);
        if (display != null) {
            display.remove();
        }
    }

    /**
     * Checks PAPI condition.
     */
    private boolean checkPAPICondition(Player player, ForgeRecipe recipe) {
        if (!recipe.hasCondition()) {
            return true;
        }

        SMCBlacksmith instance = SMCBlacksmith.getInstance();
        if (instance == null) return true;

        PlaceholderAPIHook papiHook = instance.getPapiHook();
        if (papiHook == null || !papiHook.isAvailable()) {
            return true;
        }

        return papiHook.checkCondition(player, recipe.getCondition());
    }

    /**
     * Checks if a player has an active session.
     */
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Gets a player's active session.
     */
    public ForgeSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }

    /**
     * Gets a player's forge display.
     */
    public ForgeDisplay getDisplay(UUID playerId) {
        return activeDisplays.get(playerId);
    }

    /**
     * Consumes input materials.
     */
    private boolean consumeInputMaterials(Player player, ForgeRecipe recipe) {
        if (!recipe.hasInput()) return true;

        String inputType = recipe.getInputType();
        String inputId = recipe.getInputId();
        int required = recipe.getInputAmount();

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
     * Refunds input materials.
     */
    private void refundInputMaterials(Player player, ForgeRecipe recipe) {
        if (!recipe.hasInput()) return;

        ItemStack refund = itemRegistry.getItem(recipe.getInputType(), recipe.getInputId(), recipe.getInputAmount());
        if (refund == null) return;

        HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(refund);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * Cancels all active sessions.
     */
    public void cancelAllSessions() {
        for (UUID playerId : new HashMap<>(activeSessions).keySet()) {
            try {
                cancelSession(playerId);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Error cancelling session for " + playerId, e);
            }
        }
    }

    /**
     * Shuts down the manager.
     */
    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        cancelAllSessions();
    }

    /**
     * Reloads the manager.
     */
    public void reload() {
        cancelAllSessions();
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }
}