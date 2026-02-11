package com.simmc.blacksmith.forge.gui;

import com.simmc.blacksmith.forge.ForgeCategory;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Category selection GUI for forge system.
 */
public class ForgeCategoryGUI implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final String TITLE = "§8Select Category";
    private static final int[] DEFAULT_SLOTS = {0, 9, 18, 27, 36, 45};
    private static final int CLOSE_SLOT = 49;

    private final Map<String, ForgeCategory> categories;
    private final Inventory inventory;
    private final Map<Integer, String> slotToCategoryId;

    public ForgeCategoryGUI(Map<String, ForgeCategory> categories) {
        this.categories = categories;
        this.slotToCategoryId = new HashMap<>();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, TITLE);
        setupGUI();
    }

    private void setupGUI() {
        // Fill background
        fillBackground();

        // Place categories
        int slotIndex = 0;
        for (Map.Entry<String, ForgeCategory> entry : categories.entrySet()) {
            ForgeCategory category = entry.getValue();
            int slot = determineSlot(category, slotIndex);

            if (slot >= 0 && slot < GUI_SIZE) {
                inventory.setItem(slot, createCategoryIcon(category));
                slotToCategoryId.put(slot, entry.getKey());
                slotIndex++;
            }
        }

        // Close button
        inventory.setItem(CLOSE_SLOT, createCloseButton());
    }

    private void fillBackground() {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, 0);
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    private int determineSlot(ForgeCategory category, int index) {
        int configuredSlot = category.getGuiSlot();
        if (configuredSlot >= 0 && configuredSlot < GUI_SIZE) {
            return configuredSlot;
        }
        return index < DEFAULT_SLOTS.length ? DEFAULT_SLOTS[index] : -1;
    }

    private ItemStack createCategoryIcon(ForgeCategory category) {
        ItemStack item = new ItemStack(category.getIconMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(category.getDisplayName());

            List<String> lore = new ArrayList<>(category.getDescription());
            lore.add("");
            lore.add("§7Recipes: §f" + category.getRecipeCount());
            lore.add("");
            lore.add("§eClick to view recipes");

            meta.setLore(lore);

            if (category.getIconCmd() > 0) {
                meta.setCustomModelData(category.getIconCmd());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCloseButton() {
        return createItem(Material.BARRIER, "§cClose", List.of("§7Click to close"), 0);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, int cmd) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            if (cmd > 0) meta.setCustomModelData(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== PUBLIC API ====================

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public Optional<ForgeCategory> getCategoryAtSlot(int slot) {
        String categoryId = slotToCategoryId.get(slot);
        return categoryId != null ? Optional.ofNullable(categories.get(categoryId)) : Optional.empty();
    }

    public boolean isCloseSlot(int slot) {
        return slot == CLOSE_SLOT;
    }

    public boolean isCategorySlot(int slot) {
        return slotToCategoryId.containsKey(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}