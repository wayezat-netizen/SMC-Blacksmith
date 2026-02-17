package com.simmc.blacksmith.quench;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Custom GUI for naming forged items.
 */
public class QuenchingAnvilGUI implements Listener, InventoryHolder {

    private static final int GUI_SIZE = 27;
    private static final int ITEM_SLOT = 13;
    private static final int CONFIRM_SLOT = 11;
    private static final int SKIP_SLOT = 15;

    private final JavaPlugin plugin;
    private final QuenchingSession session;
    private final QuenchingManager manager;
    private final UUID playerId;
    private final Inventory inventory;

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean awaitingChatInput = new AtomicBoolean(false);
    private volatile String customName = null;

    public QuenchingAnvilGUI(JavaPlugin plugin, QuenchingSession session, QuenchingManager manager) {
        this.plugin = plugin;
        this.session = session;
        this.manager = manager;
        this.playerId = session.getPlayerId();
        this.inventory = createInventory();
    }

    private Inventory createInventory() {
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, "§6§lName Your Item");

        // Fill background
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        // Item preview in center
        inv.setItem(ITEM_SLOT, session.getForgedItem().clone());

        // Confirm/Rename button
        inv.setItem(CONFIRM_SLOT, createItem(Material.NAME_TAG, "§a§lRename Item",
                "",
                "§7Click to type a custom name",
                "§7in chat.",
                "",
                "§eClick to rename"));

        // Skip button
        inv.setItem(SKIP_SLOT, createItem(Material.BARRIER, "§c§lSkip Naming",
                "",
                "§7Keep the default item name",
                "§7and receive your item.",
                "",
                "§eClick to skip"));

        return inv;
    }

    private ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(playerId)) return;
        if (event.getInventory().getHolder() != this) return;

        event.setCancelled(true);

        if (completed.get() || awaitingChatInput.get()) return;

        int slot = event.getRawSlot();

        if (slot == CONFIRM_SLOT) {
            // Start chat input mode
            awaitingChatInput.set(true);
            player.closeInventory();
            player.sendMessage("");
            player.sendMessage("§6§l⚒ NAMING MODE");
            player.sendMessage("§7Type your item's name in chat.");
            player.sendMessage("§7Type §ccancel §7to skip naming.");
            player.sendMessage("");
        } else if (slot == SKIP_SLOT) {
            // Skip naming - complete without name
            completeNaming(player, null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.getUniqueId().equals(playerId)) return;
        if (event.getInventory().getHolder() != this) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!player.getUniqueId().equals(playerId)) return;
        if (event.getInventory().getHolder() != this) return;

        // If awaiting chat input, don't complete yet
        if (awaitingChatInput.get()) {
            return;
        }

        // If already completed, don't handle again
        if (completed.get()) {
            return;
        }

        // Reopen GUI if closed without completing
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !completed.get() && !awaitingChatInput.get()) {
                player.openInventory(inventory);
                player.sendMessage("§cYou must name your item or click Skip!");
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.getPlayer().getUniqueId().equals(playerId)) return;
        if (!awaitingChatInput.get()) return;

        event.setCancelled(true);
        String message = event.getMessage().trim();
        Player player = event.getPlayer();

        // Run on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (completed.get()) return;

            if (message.equalsIgnoreCase("cancel")) {
                // Cancelled - complete without name
                player.sendMessage("§7Naming cancelled.");
                completeNaming(player, null);
            } else if (!message.isEmpty()) {
                // Valid name entered
                customName = message;
                player.sendMessage("§aItem named: §f" + message);
                completeNaming(player, message);
            } else {
                // Empty message - ask again
                player.sendMessage("§cPlease enter a valid name or type 'cancel'.");
            }
        });
    }

    private void completeNaming(Player player, String name) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }

        awaitingChatInput.set(false);
        HandlerList.unregisterAll(this);

        // Close inventory if open
        if (player.getOpenInventory().getTopInventory().getHolder() == this) {
            player.closeInventory();
        }

        // Complete the quenching session
        manager.handleAnvilComplete(player, name);
    }
}