package com.simmc.blacksmith.repair;

import com.simmc.blacksmith.config.GrindstoneConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * GUI for the repair system.
 */
public class RepairGUI implements InventoryHolder {

    private static final ItemStack FILLER = createFiller();
    private static final ItemStack REPAIR_BUTTON = createRepairButton();
    private static final ItemStack REPAIR_BUTTON_DISABLED = createRepairButtonDisabled();

    private final Player player;
    private final GrindstoneConfig config;
    private final Inventory inventory;
    private final int inputSlot;
    private final int repairButtonSlot;
    private final int infoSlot;

    // Cached values for display
    private int successChance;
    private int repairAmount;

    public RepairGUI(Player player, GrindstoneConfig config, int successChance, int repairAmount) {
        this.player = player;
        this.config = config;
        this.successChance = successChance;
        this.repairAmount = repairAmount;

        this.inputSlot = config.getInputSlot();
        this.repairButtonSlot = config.getRepairButtonSlot();
        this.infoSlot = config.getInfoSlot();

        this.inventory = createInventory();
        fillBackground();
        updateInfoDisplay();
        updateRepairButton();
    }

    private Inventory createInventory() {
        return Bukkit.createInventory(this, config.getGuiSize(), config.getGuiTitle());
    }

    private void fillBackground() {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (i != inputSlot && i != repairButtonSlot && i != infoSlot) {
                inventory.setItem(i, FILLER);
            }
        }
    }

    // ==================== DISPLAY UPDATES ====================

    public void updateInfoDisplay() {
        ItemStack info = createInfoItem();
        inventory.setItem(infoSlot, info);
    }

    public void updateRepairButton() {
        ItemStack inputItem = inventory.getItem(inputSlot);
        boolean hasItem = inputItem != null && !inputItem.getType().isAir();
        boolean isDamaged = hasItem && isDamagedItem(inputItem);

        if (hasItem && isDamaged) {
            inventory.setItem(repairButtonSlot, REPAIR_BUTTON);
        } else {
            inventory.setItem(repairButtonSlot, REPAIR_BUTTON_DISABLED);
        }
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lRepair Information");
            meta.setLore(Arrays.asList(
                    "",
                    "§7Place a damaged item in the slot below",
                    "§7then click the repair button.",
                    "",
                    "§6Your Stats:",
                    "§7• Success Chance: §a" + successChance + "%",
                    "§7• Repair Amount: §a" + repairAmount + "%",
                    "",
                    "§8Requires repair materials"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== SLOT CHECKS ====================

    public boolean isInputSlot(int slot) {
        return slot == inputSlot;
    }

    public boolean isRepairButtonSlot(int slot) {
        return slot == repairButtonSlot;
    }

    public boolean isInteractableSlot(int slot) {
        return slot == inputSlot;
    }

    public int getInputSlot() {
        return inputSlot;
    }

    public ItemStack getInputItem() {
        return inventory.getItem(inputSlot);
    }

    public void clearInputSlot() {
        inventory.setItem(inputSlot, null);
    }

    // ==================== HELPERS ====================

    private boolean isDamagedItem(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) return false;
        return damageable.getDamage() > 0;
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public int getSuccessChance() {
        return successChance;
    }

    public int getRepairAmount() {
        return repairAmount;
    }

    // ==================== STATIC ITEM CREATORS ====================

    private static ItemStack createFiller() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createRepairButton() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lREPAIR");
            meta.setLore(Arrays.asList(
                    "",
                    "§7Click to attempt repair",
                    "",
                    "§e§lCLICK §7to repair"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createRepairButtonDisabled() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lREPAIR");
            meta.setLore(Arrays.asList(
                    "",
                    "§cPlace a damaged item first"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}

