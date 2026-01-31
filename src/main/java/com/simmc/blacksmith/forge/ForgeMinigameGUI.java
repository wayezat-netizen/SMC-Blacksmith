package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ForgeMinigameGUI implements InventoryHolder {

    private static final int INFO_SLOT = 0;
    private static final int FRAME_SLOT = 4;
    private static final int EXIT_SLOT = 8;
    private static final int BAR_START = 9;
    private static final int BAR_END = 17;
    private static final int PROGRESS_SLOT = 18;
    private static final int HAMMER_SLOT = 26;

    private static final int GUI_SIZE = 27;
    private static final int BAR_LENGTH = BAR_END - BAR_START + 1;

    private final ForgeSession session;
    private final Inventory inventory;

    private int indicatorPosition;
    private int indicatorDirection;

    public ForgeMinigameGUI(ForgeSession session) {
        this.session = session;
        this.indicatorPosition = 0;
        this.indicatorDirection = 1;

        String title = "§8Forging: §6" + session.getRecipe().getId();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, title);

        setupGUI();
    }

    private void setupGUI() {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i < BAR_START || i > BAR_END) {
                if (i != INFO_SLOT && i != FRAME_SLOT && i != EXIT_SLOT &&
                        i != PROGRESS_SLOT && i != HAMMER_SLOT) {
                    inventory.setItem(i, glass);
                }
            }
        }

        updateInfo();
        updateFrame();
        updateBar();
        updateProgress();

        ItemStack exitItem = createItem(Material.BARRIER, "§cCancel");
        ItemMeta exitMeta = exitItem.getItemMeta();
        if (exitMeta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to cancel forging");
            lore.add("§7Materials will be refunded");
            exitMeta.setLore(lore);
            exitItem.setItemMeta(exitMeta);
        }
        inventory.setItem(EXIT_SLOT, exitItem);

        ItemStack hammerItem = createItem(Material.IRON_AXE, "§6Strike!");
        ItemMeta hammerMeta = hammerItem.getItemMeta();
        if (hammerMeta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to strike the anvil");
            lore.add("§7Time your hits with the target!");
            hammerMeta.setLore(lore);
            hammerItem.setItemMeta(hammerMeta);
        }
        inventory.setItem(HAMMER_SLOT, hammerItem);
    }

    public void tickIndicator() {
        indicatorPosition += indicatorDirection;

        if (indicatorPosition >= BAR_LENGTH - 1) {
            indicatorPosition = BAR_LENGTH - 1;
            indicatorDirection = -1;
        } else if (indicatorPosition <= 0) {
            indicatorPosition = 0;
            indicatorDirection = 1;
        }

        updateBar();
    }

    public void updateBar() {
        double targetPos = session.getCurrentTargetPosition();
        double targetSize = session.getRecipe().getTargetSize();

        int targetStart = (int) ((targetPos - targetSize / 2.0) * BAR_LENGTH);
        int targetEnd = (int) ((targetPos + targetSize / 2.0) * BAR_LENGTH);

        for (int i = 0; i < BAR_LENGTH; i++) {
            Material material;
            String name;

            if (i == indicatorPosition) {
                material = Material.WHITE_STAINED_GLASS_PANE;
                name = "§f▼ Indicator";
            } else if (i >= targetStart && i <= targetEnd) {
                material = Material.LIME_STAINED_GLASS_PANE;
                name = "§aTarget Zone";
            } else {
                material = Material.GRAY_STAINED_GLASS_PANE;
                name = " ";
            }

            inventory.setItem(BAR_START + i, createItem(material, name));
        }
    }

    public double getCurrentHitPosition() {
        return (double) indicatorPosition / (BAR_LENGTH - 1);
    }

    public void updateFrame() {
        ForgeFrame frame = session.getRecipe().getFrame(session.getCurrentFrame());
        if (frame != null) {
            ItemStack frameItem = frame.createDisplayItem();
            ItemMeta meta = frameItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6Current Progress");
                List<String> lore = new ArrayList<>();
                lore.add("§7Stage: §f" + (session.getCurrentFrame() + 1) + "/3");
                meta.setLore(lore);
                frameItem.setItemMeta(meta);
            }
            inventory.setItem(FRAME_SLOT, frameItem);
        }
    }

    public void updateProgress() {
        int hits = session.getHitsCompleted();
        int total = session.getTotalHits();
        double progress = session.getProgress();

        ItemStack progressItem = createItem(Material.PAPER, "§eProgress");
        ItemMeta meta = progressItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Hits: §f" + hits + "/" + total);
            lore.add("");
            lore.add(ColorUtil.createProgressBar(progress, 15));

            if (!session.getHitRecords().isEmpty()) {
                double avgAcc = session.getHitRecords().stream()
                        .mapToDouble(ForgeSession.HitRecord::accuracy)
                        .average()
                        .orElse(0.0);
                lore.add("");
                lore.add("§7Average Accuracy: §f" + (int) (avgAcc * 100) + "%");
            }

            meta.setLore(lore);
            progressItem.setItemMeta(meta);
        }
        inventory.setItem(PROGRESS_SLOT, progressItem);
    }

    public void updateInfo() {
        ForgeRecipe recipe = session.getRecipe();

        ItemStack infoItem = createItem(Material.BOOK, "§6" + recipe.getId());
        ItemMeta meta = infoItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Required Hits: §f" + recipe.getHits());
            lore.add("§7Target Size: §f" + (int) (recipe.getTargetSize() * 100) + "%");
            lore.add("");
            lore.add("§7Time your clicks to hit the");
            lore.add("§agreen target zone§7!");
            meta.setLore(lore);
            infoItem.setItemMeta(meta);
        }
        inventory.setItem(INFO_SLOT, infoItem);
    }

    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public boolean isHammerSlot(int slot) {
        return slot == HAMMER_SLOT;
    }

    public boolean isExitSlot(int slot) {
        return slot == EXIT_SLOT;
    }

    public ForgeSession getSession() {
        return session;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}