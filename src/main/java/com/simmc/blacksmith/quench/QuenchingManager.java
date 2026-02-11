package com.simmc.blacksmith.quench;

import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ItemModifierService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages quenching (naming) sessions after forging completes.
 * Flow: Forge complete → GUI opens → Player names item (or skips) → Item given
 */
public class QuenchingManager {

    private static final long DEFAULT_TIMEOUT_SECONDS = 120;
    private static final long TIMEOUT_CHECK_TICKS = 200L; // 10 seconds
    private static final int MAX_NAME_LENGTH = 50;

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemModifierService modifierService;

    private final Map<UUID, QuenchingSession> sessions;
    private final Map<UUID, QuenchingGUI> openGUIs;

    private BukkitTask timeoutTask;

    public QuenchingManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.modifierService = new ItemModifierService();
        this.sessions = new ConcurrentHashMap<>();
        this.openGUIs = new ConcurrentHashMap<>();
        startTimeoutChecker();
    }

    // ==================== LIFECYCLE ====================

    private void startTimeoutChecker() {
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin,
                this::checkTimeouts, TIMEOUT_CHECK_TICKS, TIMEOUT_CHECK_TICKS);
    }

    private void checkTimeouts() {
        if (sessions.isEmpty()) return;

        long timeoutMs = getSessionTimeoutMs();
        List<UUID> expired = new ArrayList<>();

        for (Map.Entry<UUID, QuenchingSession> entry : sessions.entrySet()) {
            if (entry.getValue().getElapsedTime() > timeoutMs) {
                expired.add(entry.getKey());
            }
        }

        expired.forEach(id -> cancelSession(id, "Session timed out."));
    }

    private long getSessionTimeoutMs() {
        return plugin.getConfig().getLong("quenching.session_timeout", DEFAULT_TIMEOUT_SECONDS) * 1000L;
    }

    // ==================== SESSION START ====================

    /**
     * Starts a quenching session after forging completes.
     */
    public void startQuenching(Player player, ItemStack forgedItem, int starRating,
                               Location anvilLocation, ForgeRecipe recipe) {
        UUID playerId = player.getUniqueId();

        if (sessions.containsKey(playerId)) {
            player.sendMessage("§cYou already have an active quenching session.");
            return;
        }

        QuenchingSession session = new QuenchingSession(playerId, forgedItem, starRating, anvilLocation, recipe);
        sessions.put(playerId, session);

        QuenchingGUI gui = new QuenchingGUI(session);
        openGUIs.put(playerId, gui);
        gui.open(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.0f);
    }

    // ==================== GUI HANDLERS ====================

    /**
     * Handles rename button click - transitions to chat input mode.
     */
    public void handleRenameClick(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);
        if (session == null) return;

        player.closeInventory();
        openGUIs.remove(playerId);
        session.awaitNameInput();

        sendNamingPrompt(player);
    }

    /**
     * Handles skip button click - completes with default name.
     */
    public void handleSkipClick(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);
        if (session == null) return;

        player.closeInventory();
        openGUIs.remove(playerId);
        completeQuenching(player, null);
    }

    /**
     * Handles GUI close event.
     */
    public void handleGUIClose(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);
        if (session == null || !session.isGuiOpen()) return;

        openGUIs.remove(playerId);
        sendReminderMessage(player);
    }

    private void sendNamingPrompt(Player player) {
        player.sendMessage("");
        player.sendMessage("§6§l⚒ NAME YOUR CREATION");
        player.sendMessage("§7Type a name in chat, or type §c'cancel' §7to go back.");
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
    }

    private void sendReminderMessage(Player player) {
        player.sendMessage("");
        player.sendMessage("§e§l⚠ NAMING SESSION ACTIVE");
        player.sendMessage("§7Type §a/forge name §7to reopen the naming GUI");
        player.sendMessage("§7Or type §c/forge cancel §7to cancel and get your item back");
        player.sendMessage("");
    }

    // ==================== CHAT INPUT ====================

    /**
     * Handles chat input for naming.
     */
    public void handleChatInput(Player player, String message) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);

        if (session == null || !session.isAwaitingName()) return;

        String trimmed = message.trim();

        if (trimmed.equalsIgnoreCase("cancel")) {
            reopenGUI(player);
            player.sendMessage("§7Naming cancelled. Choose an option.");
            return;
        }

        if (trimmed.isEmpty()) {
            player.sendMessage("§cPlease enter a valid name or type §c'cancel' §7to go back.");
            return;
        }

        if (trimmed.length() > MAX_NAME_LENGTH) {
            player.sendMessage("§cName too long! Maximum " + MAX_NAME_LENGTH + " characters.");
            return;
        }

        completeQuenching(player, trimmed);
    }

    // ==================== COMPLETION ====================

    /**
     * Completes the quenching session and gives the item to the player.
     */
    private void completeQuenching(Player player, String customName) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.remove(playerId);
        openGUIs.remove(playerId);

        if (session == null) return;
        session.complete();

        ItemStack result = buildFinalItem(session, player.getName(), customName);
        giveItemToPlayer(player, result);
        sendCompletionMessage(player, session.getStarRating(), customName);
        playCompletionSounds(player);
    }

    private ItemStack buildFinalItem(QuenchingSession session, String forgerName, String customName) {
        ItemStack result = session.getForgedItem();
        ForgeRecipe recipe = session.getRecipe();
        int stars = session.getStarRating();

        // Apply star modifiers
        if (recipe != null && recipe.hasStarModifiers()) {
            result = modifierService.applyStarModifiers(result, stars, recipe.getStarModifiers(), forgerName);
        } else {
            result = addBasicLore(result, stars, forgerName);
        }

        // Apply custom name
        if (customName != null && !customName.isEmpty()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                String formatted = customName.replace("&", "§");
                meta.setDisplayName("§f" + formatted);
                result.setItemMeta(meta);
            }
        }

        return result;
    }

    private ItemStack addBasicLore(ItemStack item, int stars, String forgerName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§7§m─────────────");
        lore.add(formatStars(stars));
        lore.add("§7Forged by §e" + forgerName);
        lore.add("§7§m─────────────");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void sendCompletionMessage(Player player, int stars, String customName) {
        player.sendMessage("");
        player.sendMessage("§a§l✓ FORGING COMPLETE!");
        player.sendMessage("§7Your " + formatStars(stars) + " §7item is ready.");
        if (customName != null && !customName.isEmpty()) {
            player.sendMessage("§7Named: §f" + customName.replace("&", "§"));
        }
        player.sendMessage("");
    }

    private void playCompletionSounds(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
    }

    // ==================== SESSION MANAGEMENT ====================

    /**
     * Reopens the naming GUI for a player.
     */
    public void reopenGUI(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);

        if (session == null) {
            player.sendMessage("§cYou don't have an active naming session.");
            return;
        }

        // Reset to GUI state
        session.returnToGui();

        QuenchingGUI gui = new QuenchingGUI(session);
        openGUIs.put(playerId, gui);
        gui.open(player);
    }

    /**
     * Cancels a session and returns the item to the player.
     */
    public void cancelSession(UUID playerId, String reason) {
        QuenchingSession session = sessions.remove(playerId);
        openGUIs.remove(playerId);
        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof QuenchingGUI) {
                player.closeInventory();
            }

            giveItemToPlayer(player, session.getForgedItem());
            player.sendMessage("§cNaming cancelled: " + reason);
            player.sendMessage("§7Your item has been returned.");
        }
    }

    public void cancelAllSessions() {
        new ArrayList<>(sessions.keySet()).forEach(id -> cancelSession(id, "Plugin shutting down."));
    }

    // ==================== UTILITY ====================

    private String formatStars(int stars) {
        StringBuilder sb = new StringBuilder("§7Quality: ");
        for (int i = 0; i < 5; i++) {
            sb.append(i < stars ? "§6★" : "§8☆");
        }
        return sb.toString();
    }

    // ==================== PUBLIC API ====================

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isAwaitingName(UUID playerId) {
        QuenchingSession session = sessions.get(playerId);
        return session != null && session.isAwaitingName();
    }

    public QuenchingGUI getOpenGUI(UUID playerId) {
        return openGUIs.get(playerId);
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    public void reload() {
        cancelAllSessions();
    }

    public void shutdown() {
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
        }
        cancelAllSessions();
    }
}