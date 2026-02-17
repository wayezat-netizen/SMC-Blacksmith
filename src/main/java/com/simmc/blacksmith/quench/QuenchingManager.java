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
 */
public class QuenchingManager {

    private static final long DEFAULT_TIMEOUT_SECONDS = 120;
    private static final long TIMEOUT_CHECK_TICKS = 200L;

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

        // Auto-complete expired sessions without name
        expired.forEach(id -> autoCompleteSession(id));
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

        player.sendMessage("§e§l⚒ FORGING COMPLETE!");
        player.sendMessage("§7Click §aRename §7to name your item using the anvil.");
        player.sendMessage("§7Click §7Skip/Close §7to finish without naming.");
        player.sendMessage("§c§oYou only have one chance to name your item!");
    }

    // ==================== GUI HANDLERS ====================

    public void handleRenameClick(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);
        if (session == null) return;

        // Set state BEFORE closing inventory to prevent handleGUIClose from completing
        session.awaitNameInput();
        openGUIs.remove(playerId);

        player.closeInventory();

        // Open naming GUI
        QuenchingAnvilGUI namingGUI = new QuenchingAnvilGUI(plugin, session, this);
        namingGUI.open(player);
    }

    public void handleSkipClick(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);
        if (session == null) return;

        player.closeInventory();
        openGUIs.remove(playerId);

        completeQuenching(player, null);
    }


    public void handleGUIClose(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);
        if (session == null) return;

        if (!session.isGuiOpen()) return;

        openGUIs.remove(playerId);

        player.sendMessage("§7Naming skipped. Your item has been given to you.");
        completeQuenching(player, null);
    }

    /**
     * Called when anvil naming is complete.
     */
    public void handleAnvilComplete(Player player, String customName) {
        completeQuenching(player, customName);
    }

    /**
     * Called when anvil is closed without naming.
     */
    public void handleAnvilClose(Player player) {
        UUID playerId = player.getUniqueId();

        // Check if session exists before completing
        if (!sessions.containsKey(playerId)) {
            return;
        }

        // Complete without name
        completeQuenching(player, null);
    }


    // ==================== COMPLETION ====================

    /**
     * Completes the quenching session and gives the item to the player.
     */
    public void completeQuenching(Player player, String customName) {
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

    private void autoCompleteSession(UUID playerId) {
        QuenchingSession session = sessions.remove(playerId);
        openGUIs.remove(playerId);
        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof QuenchingGUI) {
                player.closeInventory();
            }

            ItemStack result = buildFinalItem(session, player.getName(), null);
            giveItemToPlayer(player, result);

            player.sendMessage("§cSession timed out.");
            player.sendMessage("§7Your item has been given to you without a custom name.");
        }
    }

    private ItemStack buildFinalItem(QuenchingSession session, String forgerName, String customName) {
        ItemStack result = session.getForgedItem();
        ForgeRecipe recipe = session.getRecipe();
        int stars = session.getStarRating();

        // Get formatted forger line from config
        String forgerFormat = configManager.getMainConfig().formatSmithedName(forgerName);

        if (recipe != null && recipe.hasStarModifiers()) {
            result = modifierService.applyStarModifiers(result, stars, recipe.getStarModifiers(), forgerName, forgerFormat);
        } else {
            result = addBasicLore(result, stars, forgerName);
        }

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

        // Use config format for forger name
        String forgerLine = configManager.getMainConfig().formatSmithedName(forgerName);
        lore.add(forgerLine);

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

    public void cancelSession(UUID playerId, String reason) {
        QuenchingSession session = sessions.remove(playerId);
        openGUIs.remove(playerId);
        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            // Close any quenching-related GUI
            var holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder instanceof QuenchingGUI || holder instanceof QuenchingAnvilGUI) {
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