package com.simmc.blacksmith.quench;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class QuenchingManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private final Map<UUID, QuenchingSession> sessions;
    private final Map<UUID, Boolean> awaitingName;

    private static final String TONGS_ITEM_ID = "blacksmith_tongs";
    private static final long SESSION_TIMEOUT_MS = 120000; // 2 minutes

    public QuenchingManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.sessions = new ConcurrentHashMap<>();
        this.awaitingName = new ConcurrentHashMap<>();

        startTimeoutChecker();
    }

    private void startTimeoutChecker() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID playerId : new ArrayList<>(sessions.keySet())) {
                QuenchingSession session = sessions.get(playerId);
                if (session != null && session.getElapsedTime() > SESSION_TIMEOUT_MS) {
                    cancelSession(playerId, "Session timed out.");
                }
            }
        }, 200L, 200L);
    }

    public void startQuenching(Player player, ItemStack forgedItem, int starRating, Location anvilLocation) {
        UUID playerId = player.getUniqueId();

        if (sessions.containsKey(playerId)) {
            player.sendMessage("§cYou already have an active quenching session.");
            return;
        }

        QuenchingSession session = new QuenchingSession(playerId, forgedItem, starRating, anvilLocation);
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

        // Check if clicking near anvil
        if (session.getAnvilLocation().distanceSquared(targetLocation) > 4) {
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

        // Valid container interaction
        awaitingName.put(playerId, true);

        player.sendMessage("§6§l⚒ NAME YOUR CREATION");
        player.sendMessage("§7Type a name in chat, or type §eskip §7to use default.");
        player.playSound(containerLocation, Sound.BLOCK_LAVA_EXTINGUISH, 1.0f, 1.0f);

        return true;
    }

    public boolean handleChatInput(Player player, String message) {
        UUID playerId = player.getUniqueId();

        if (!awaitingName.getOrDefault(playerId, false)) {
            return false;
        }

        awaitingName.remove(playerId);
        QuenchingSession session = sessions.get(playerId);

        if (session == null) return false;

        String customName = message.equalsIgnoreCase("skip") ? null : message;
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
        ItemMeta meta = result.getItemMeta();

        if (meta != null) {
            // Apply custom name
            if (session.getCustomName() != null && !session.getCustomName().isEmpty()) {
                meta.setDisplayName("§f" + session.getCustomName());
            }

            // Add forger lore
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7Forged by §e" + player.getName());
            lore.add("§7Quality: " + formatStars(session.getStarRating()));

            String format = configManager.getMainConfig().getSmithingNameFormat();
            if (format != null && !format.isEmpty()) {
                lore.add("§8" + String.format(format, player.getName()));
            }

            meta.setLore(lore);
            result.setItemMeta(meta);
        }

        // Give item
        var overflow = player.getInventory().addItem(result);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.sendMessage("§a§l✓ Quenching complete!");
        player.sendMessage("§7Your " + formatStars(session.getStarRating()) + " §7item is ready.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 0.8f);
    }

    private String formatStars(int stars) {
        StringBuilder sb = new StringBuilder();
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
        if (player != null) {
            // Refund the item
            var overflow = player.getInventory().addItem(session.getForgedItem());
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
        SMCCoreHook smcHook = instance.getSmcCoreHook();

        if (smcHook != null && smcHook.isAvailable()) {
            String itemId = smcHook.getItemId(mainHand);
            return TONGS_ITEM_ID.equalsIgnoreCase(itemId);
        }

        // Fallback: check for shears (vanilla tongs substitute)
        return mainHand.getType() == org.bukkit.Material.SHEARS;
    }

    public boolean hasActiveSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isAwaitingName(UUID playerId) {
        return awaitingName.getOrDefault(playerId, false);
    }

    public void cancelAllSessions() {
        for (UUID playerId : new ArrayList<>(sessions.keySet())) {
            cancelSession(playerId, "Plugin shutting down.");
        }
    }

    public void reload() {
        cancelAllSessions();
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }
}