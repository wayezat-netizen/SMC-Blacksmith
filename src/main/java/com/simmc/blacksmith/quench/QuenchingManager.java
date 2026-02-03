package com.simmc.blacksmith.quench;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.forge.ForgeRecipe;
import com.simmc.blacksmith.forge.ItemModifierService;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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

public class QuenchingManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ItemModifierService modifierService;

    private final Map<UUID, QuenchingSession> sessions;
    private final Map<UUID, Boolean> awaitingName;

    private BukkitTask timeoutTask;

    private static final String TONGS_ITEM_ID = "blacksmith_tongs";
    private static final long SESSION_TIMEOUT_MS = 120000;
    private static final long TIMEOUT_CHECK_INTERVAL = 200L;

    // Reusable list for timeout checking
    private final List<UUID> expiredSessions = new ArrayList<>();

    public QuenchingManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.modifierService = new ItemModifierService();
        this.sessions = new ConcurrentHashMap<>();
        this.awaitingName = new ConcurrentHashMap<>();
        startTimeoutChecker();
    }

    private void startTimeoutChecker() {
        timeoutTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkTimeouts,
                TIMEOUT_CHECK_INTERVAL, TIMEOUT_CHECK_INTERVAL);
    }

    private void checkTimeouts() {
        if (sessions.isEmpty()) return;

        long now = System.currentTimeMillis();
        expiredSessions.clear();

        // Find expired sessions
        for (Map.Entry<UUID, QuenchingSession> entry : sessions.entrySet()) {
            QuenchingSession session = entry.getValue();
            if (session != null && session.getElapsedTime() > SESSION_TIMEOUT_MS) {
                expiredSessions.add(entry.getKey());
            }
        }

        // Cancel expired sessions
        for (UUID playerId : expiredSessions) {
            cancelSession(playerId, "Session timed out.");
        }
    }

    public void startQuenching(Player player, ItemStack forgedItem, int starRating,
                               Location anvilLocation, ForgeRecipe recipe) {
        UUID playerId = player.getUniqueId();

        if (sessions.containsKey(playerId)) {
            player.sendMessage("§cYou already have an active quenching session.");
            return;
        }

        QuenchingSession session = new QuenchingSession(playerId, forgedItem, starRating, anvilLocation, recipe);
        sessions.put(playerId, session);

        player.sendMessage("§6§l⚒ QUENCHING");
        player.sendMessage("§7Use §etongs §7to pick up your item from the anvil.");
        player.playSound(player.getLocation(), Sound.BLOCK_LAVA_AMBIENT, 0.8f, 1.2f);
    }

    public boolean handleTongsUse(Player player, Location targetLocation) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);

        if (session == null) return false;

        if (!isHoldingTongs(player)) {
            player.sendMessage("§cYou need tongs to pick up the hot item!");
            return false;
        }

        if (session.isPickedUp()) {
            return handleContainerPlace(player, targetLocation);
        }

        Location anvilLoc = session.getAnvilLocation();
        if (anvilLoc.distanceSquared(targetLocation) > 4) {
            player.sendMessage("§cClick on the anvil to pick up your item.");
            return false;
        }

        session.pickup();
        player.sendMessage("§aItem picked up with tongs!");
        player.sendMessage("§7Now place it in a §equenching container§7.");
        player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.3f);

        return true;
    }

    private boolean handleContainerPlace(Player player, Location containerLocation) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.get(playerId);

        if (session == null || !session.isPickedUp()) return false;

        awaitingName.put(playerId, Boolean.TRUE);

        player.sendMessage("§6§l⚒ NAME YOUR CREATION");
        player.sendMessage("§7Type a name in chat, or type §eskip §7to use default.");
        player.playSound(containerLocation, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);

        return true;
    }

    public boolean handleChatInput(Player player, String message) {
        UUID playerId = player.getUniqueId();

        Boolean awaiting = awaitingName.get(playerId);
        if (awaiting == null || !awaiting) {
            return false;
        }

        awaitingName.remove(playerId);
        QuenchingSession session = sessions.get(playerId);

        if (session == null) return false;

        String customName = "skip".equalsIgnoreCase(message) ? null : message;
        session.setCustomName(customName);

        completeQuenching(player);
        return true;
    }

    private void completeQuenching(Player player) {
        UUID playerId = player.getUniqueId();
        QuenchingSession session = sessions.remove(playerId);
        awaitingName.remove(playerId);

        if (session == null) return;

        ItemStack result = session.getForgedItem();
        ForgeRecipe recipe = session.getRecipe();
        int stars = session.getStarRating();

        // Apply star modifiers if recipe has them
        if (recipe != null && recipe.hasStarModifiers()) {
            result = modifierService.applyStarModifiers(
                    result, stars, recipe.getStarModifiers(), player.getName()
            );
        } else {
            result = addBasicLore(result, stars, player.getName());
        }

        // Apply custom name if provided
        String customName = session.getCustomName();
        if (customName != null && !customName.isEmpty()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§f" + customName);
                result.setItemMeta(meta);
            }
        }

        // Give item
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(result);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage("§a§l✓ Quenching complete!");
        player.sendMessage("§7Your " + formatStars(stars) + " §7item is ready.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
    }

    private ItemStack addBasicLore(ItemStack item, int stars, String forgerName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>(3);
        lore.add("");
        lore.add(formatStars(stars));
        lore.add("§7Forged by §e" + forgerName);

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String formatStars(int stars) {
        StringBuilder sb = new StringBuilder("§7Quality: ");
        for (int i = 0; i < 5; i++) {
            sb.append(i < stars ? "§6★" : "§7☆");
        }
        return sb.toString();
    }

    public void cancelSession(UUID playerId, String reason) {
        QuenchingSession session = sessions.remove(playerId);
        awaitingName.remove(playerId);

        if (session == null) return;

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(session.getForgedItem());
            for (ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            player.sendMessage("§cQuenching cancelled: " + reason);
            player.sendMessage("§7Your item has been returned.");
        }
    }

    public boolean isHoldingTongs(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getType().isAir()) return false;

        SMCBlacksmith instance = SMCBlacksmith.getInstance();
        if (instance == null) return mainHand.getType() == Material.SHEARS;

        SMCCoreHook smcHook = instance.getSmcCoreHook();
        if (smcHook != null && smcHook.isAvailable()) {
            String itemId = smcHook.getItemId(mainHand);
            return TONGS_ITEM_ID.equalsIgnoreCase(itemId);
        }

        return mainHand.getType() == Material.SHEARS;
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isAwaitingName(UUID playerId) {
        Boolean awaiting = awaitingName.get(playerId);
        return awaiting != null && awaiting;
    }

    public void cancelAllSessions() {
        List<UUID> playerIds = new ArrayList<>(sessions.keySet());
        for (UUID playerId : playerIds) {
            cancelSession(playerId, "Plugin shutting down.");
        }
    }

    public void shutdown() {
        if (timeoutTask != null && !timeoutTask.isCancelled()) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        cancelAllSessions();
    }

    public void reload() {
        cancelAllSessions();
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}