package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.forge.display.ForgeDisplay;
import com.simmc.blacksmith.integration.PlaceholderAPIHook;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.quench.QuenchingManager;
import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ForgeManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemProviderRegistry itemRegistry;

    private final Map<UUID, ForgeSession> sessions;
    private final Map<UUID, ForgeDisplay> displays;
    private BukkitTask tickTask;

    // Cached list for iteration to avoid ConcurrentModificationException
    private final List<UUID> tickIterationList = new ArrayList<>();

    public ForgeManager(JavaPlugin plugin, ConfigManager configManager, ItemProviderRegistry itemRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.sessions = new ConcurrentHashMap<>();
        this.displays = new ConcurrentHashMap<>();
        startTickTask();
    }

    private void startTickTask() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
    }

    private void tick() {
        if (sessions.isEmpty()) return;

        // Copy keys to avoid CME
        tickIterationList.clear();
        tickIterationList.addAll(sessions.keySet());

        for (UUID playerId : tickIterationList) {
            ForgeSession session = sessions.get(playerId);
            if (session == null) continue;

            if (!session.isActive()) {
                cleanup(playerId);
                continue;
            }

            session.tick();

            ForgeDisplay display = displays.get(playerId);
            if (display != null && display.isValid()) {
                display.tick(session);
            }

            if (session.isComplete()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.isOnline()) {
                    completeSession(player);
                } else {
                    cleanup(playerId);
                }
            }
        }
    }

    public boolean startSession(Player player, String recipeId, Location anvilLocation) {
        MessageConfig messages = configManager.getMessageConfig();
        UUID playerId = player.getUniqueId();

        if (sessions.containsKey(playerId)) {
            player.sendMessage(messages.getForgeSessionActive());
            return false;
        }

        ForgeRecipe recipe = configManager.getBlacksmithConfig().getRecipe(recipeId);
        if (recipe == null) {
            player.sendMessage(messages.getForgeUnknownRecipe(recipeId));
            return false;
        }

        if (recipe.hasPermission() && !player.hasPermission(recipe.getPermission())) {
            player.sendMessage(messages.getNoPermission());
            return false;
        }

        if (!checkCondition(player, recipe)) {
            player.sendMessage(messages.getConditionNotMet());
            return false;
        }

        if (!consumeMaterials(player, recipe)) {
            player.sendMessage(messages.getMissingMaterials(recipe.getInputAmount(), recipe.getInputId()));
            return false;
        }

        ForgeSession session = new ForgeSession(playerId, recipe, anvilLocation);
        sessions.put(playerId, session);

        ForgeDisplay display = new ForgeDisplay(playerId, anvilLocation, recipe);
        display.spawn();
        displays.put(playerId, display);

        player.playSound(anvilLocation, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.9f);
        player.sendMessage(messages.getForgeStarted());
        player.sendMessage("§e§lRIGHT CLICK §7the glowing points to strike!");

        return true;
    }

    public void processPointHit(Player player, UUID hitboxId) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = sessions.get(playerId);

        if (session == null || !session.isActive()) return;

        double accuracy = session.processHit(hitboxId);
        if (accuracy < 0) return;

        MessageConfig messages = configManager.getMessageConfig();

        if (accuracy >= 0.9) {
            player.sendMessage(messages.getForgePerfectHit());
        } else if (accuracy >= 0.7) {
            player.sendMessage(messages.getForgeGoodHit());
        } else if (accuracy >= 0.4) {
            player.sendMessage(messages.getForgeOkayHit());
        } else {
            player.sendMessage(messages.getForgeMiss());
        }
    }

    private void completeSession(Player player) {
        UUID playerId = player.getUniqueId();
        ForgeSession session = sessions.get(playerId);
        ForgeDisplay display = displays.get(playerId);

        if (session == null) return;

        int stars = session.calculateStarRating();
        ForgeRecipe recipe = session.getRecipe();

        if (display != null) {
            display.showCompletion(stars);
        }

        playCompletionEffects(player, session.getAnvilLocation(), stars);

        // Get result item
        ItemStack resultItem = null;

        if (recipe.usesBaseItem()) {
            resultItem = itemRegistry.getItem(recipe.getBaseItemType(), recipe.getBaseItemId(), 1);
        } else {
            ForgeResult result = recipe.getResult(stars);
            if (result != null) {
                resultItem = itemRegistry.getItem(result.type(), result.id(), result.amount());
            }
        }

        String starDisplay = ColorUtil.formatStars(stars, 5);
        player.sendMessage(configManager.getMessageConfig().getForgeComplete(stars, starDisplay));

        executeCommand(player, recipe, stars);

        // Start quenching
        if (resultItem != null) {
            SMCBlacksmith instance = SMCBlacksmith.getInstance();
            QuenchingManager quenchManager = instance.getQuenchingManager();

            if (quenchManager != null) {
                quenchManager.startQuenching(player, resultItem, stars, session.getAnvilLocation(), recipe);
            } else {
                giveItem(player, resultItem);
            }
        }

        // Delayed cleanup
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(playerId), 40L);
    }

    private void giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void playCompletionEffects(Player player, Location loc, int stars) {
        if (stars >= 5) {
            player.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else if (stars >= 3) {
            player.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
        } else {
            player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private void executeCommand(Player player, ForgeRecipe recipe, int stars) {
        String command = recipe.getRunAfterCommand();
        if (command == null || command.isEmpty()) return;

        String parsed = command
                .replace("%player%", player.getName())
                .replace("<player>", player.getName())
                .replace("%stars%", String.valueOf(stars))
                .replace("<stars>", String.valueOf(stars));

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
    }

    public void cancelSession(UUID playerId) {
        ForgeSession session = sessions.get(playerId);
        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            refundMaterials(player, session.getRecipe());
            player.sendMessage(configManager.getMessageConfig().getForgeRefunded());
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
        }

        session.cancel();
        cleanup(playerId);
    }

    private void cleanup(UUID playerId) {
        ForgeSession session = sessions.remove(playerId);
        if (session != null) {
            session.cleanup();
        }

        ForgeDisplay display = displays.remove(playerId);
        if (display != null) {
            display.remove();
        }
    }

    private boolean checkCondition(Player player, ForgeRecipe recipe) {
        if (!recipe.hasCondition()) return true;

        SMCBlacksmith instance = SMCBlacksmith.getInstance();
        PlaceholderAPIHook papi = instance.getPapiHook();

        if (papi == null || !papi.isAvailable()) return true;

        return papi.checkCondition(player, recipe.getCondition());
    }

    private boolean consumeMaterials(Player player, ForgeRecipe recipe) {
        if (!recipe.hasInput()) return true;

        String type = recipe.getInputType();
        String id = recipe.getInputId();
        int required = recipe.getInputAmount();

        // First pass: count available
        int found = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack item : contents) {
            if (item != null && itemRegistry.matches(item, type, id)) {
                found += item.getAmount();
                if (found >= required) break;
            }
        }

        if (found < required) return false;

        // Second pass: consume
        int remaining = required;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;

            if (itemRegistry.matches(item, type, id)) {
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

    private void refundMaterials(Player player, ForgeRecipe recipe) {
        if (!recipe.hasInput()) return;

        ItemStack refund = itemRegistry.getItem(recipe.getInputType(), recipe.getInputId(), recipe.getInputAmount());
        if (refund == null) return;

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(refund);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    public void cancelAllSessions() {
        // Create copy to avoid CME
        List<UUID> playerIds = new ArrayList<>(sessions.keySet());
        for (UUID playerId : playerIds) {
            cancelSession(playerId);
        }
    }

    public void shutdown() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
        cancelAllSessions();
    }

    public void reload() {
        cancelAllSessions();
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public ForgeSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}