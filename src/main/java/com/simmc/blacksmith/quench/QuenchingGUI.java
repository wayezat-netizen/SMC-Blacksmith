package com.simmc.blacksmith.quench;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for naming a freshly forged item.
 * Shows item preview, star rating, and naming options.
 */
public class QuenchingGUI implements InventoryHolder {

    private static final int GUI_SIZE = 45;
    private static final String TITLE = "§6§l⚒ Name Your Creation";

    // Slot positions
    private static final int INFO_SLOT = 4;
    private static final int ITEM_PREVIEW_SLOT = 13;
    private static final int STAR_DISPLAY_SLOT = 22;
    private static final int RENAME_SLOT = 29;
    private static final int SKIP_SLOT = 33;
    private static final int CLOSE_SLOT = 40;

    private static final int[] GOLD_BORDER = {0, 1, 2, 3, 5, 6, 7, 8, 36, 37, 38, 39, 41, 42, 43, 44};

    private final QuenchingSession session;
    private final Inventory inventory;

    public QuenchingGUI(QuenchingSession session) {
        this.session = session;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, TITLE);
        setupGUI();
    }

    private void setupGUI() {
        fillBackground();

        ItemStack goldGlass = createGlassPane(Material.YELLOW_STAINED_GLASS_PANE);
        for (int slot : GOLD_BORDER) {
            inventory.setItem(slot, goldGlass);
        }

        inventory.setItem(INFO_SLOT, createInfoItem());
        inventory.setItem(ITEM_PREVIEW_SLOT, createPreviewItem());
        inventory.setItem(STAR_DISPLAY_SLOT, createStarDisplay());
        inventory.setItem(RENAME_SLOT, createRenameButton());
        inventory.setItem(SKIP_SLOT, createSkipButton());
        inventory.setItem(CLOSE_SLOT, createCloseButton());
    }

    private void fillBackground() {
        ItemStack glass = createGlassPane(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, glass);
        }
    }

    private ItemStack createInfoItem() {
        return buildItem(Material.BOOK, "§6§lForging Complete!",
                "",
                "§7Your item has been forged.",
                "§7You may now give it a custom name.",
                "",
                "§c§oThis is your only chance!",
                "",
                "§e• §fClick §aRename §fto open anvil",
                "§e• §fClick §7Skip/Close §fto finish"
        );
    }

    private ItemStack createPreviewItem() {
        ItemStack preview = session.getForgedItem().clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add("§7§m──────────────────");
            lore.add("§7Quality: " + formatStars(session.getStarRating()));
            lore.add("§7§m──────────────────");
            meta.setLore(lore);
            preview.setItemMeta(meta);
        }
        return preview;
    }

    private ItemStack createStarDisplay() {
        int stars = session.getStarRating();
        StarTier tier = StarTier.fromStars(stars);

        return buildItem(tier.material, tier.displayName,
                "",
                formatStars(stars),
                "",
                "§7Star Rating: §f" + stars + "/5"
        );
    }

    private ItemStack createRenameButton() {
        return buildItem(Material.ANVIL, "§a§lRename Item",
                "",
                "§7Open the anvil to name your item.",
                "",
                "§eClick to open anvil"
        );
    }

    private ItemStack createSkipButton() {
        return buildItem(Material.PAPER, "§7§lSkip Naming",
                "",
                "§7Keep the default item name.",
                "",
                "§eClick to finish without naming"
        );
    }

    private ItemStack createCloseButton() {
        return buildItem(Material.BARRIER, "§c§lClose & Finish",
                "",
                "§7Close this menu and receive your item",
                "§7without a custom name.",
                "",
                "§c§oYou cannot reopen this menu!"
        );
    }

    private ItemStack createGlassPane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildItem(Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(line);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatStars(int stars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            sb.append(i < stars ? "§6★" : "§8☆");
        }
        return sb.toString();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public boolean isRenameSlot(int slot) {
        return slot == RENAME_SLOT;
    }

    public boolean isSkipSlot(int slot) {
        return slot == SKIP_SLOT || slot == CLOSE_SLOT;
    }

    public boolean isCloseSlot(int slot) {
        return slot == CLOSE_SLOT;
    }

    public QuenchingSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private enum StarTier {
        MASTERWORK(5, Material.NETHER_STAR, "§6§lMASTERWORK"),
        EXCELLENT(4, Material.GOLD_INGOT, "§e§lExcellent"),
        GOOD(3, Material.IRON_INGOT, "§f§lGood"),
        COMMON(2, Material.COPPER_INGOT, "§7§lCommon"),
        POOR(0, Material.RAW_IRON, "§8§lPoor");

        final int minStars;
        final Material material;
        final String displayName;

        StarTier(int minStars, Material material, String displayName) {
            this.minStars = minStars;
            this.material = material;
            this.displayName = displayName;
        }

        static StarTier fromStars(int stars) {
            for (StarTier tier : values()) {
                if (stars >= tier.minStars) {
                    return tier;
                }
            }
            return POOR;
        }
    }
}