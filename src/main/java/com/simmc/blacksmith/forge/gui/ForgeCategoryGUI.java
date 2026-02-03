package com.simmc.blacksmith.forge.gui;

import com.simmc.blacksmith.forge.ForgeCategory;
import com.simmc.blacksmith.forge.ForgeRecipe;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ForgeCategoryGUI implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final String TITLE = "§8Select Category";

    private final Map<String, ForgeCategory> categories;
    private final Inventory inventory;

    public ForgeCategoryGUI(Map<String, ForgeCategory> categories) {
        this.categories = categories;
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, TITLE);
        setupGUI();
    }

    private void setupGUI() {
        // Fill background
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, glass);
        }

        // Place category icons
        for (ForgeCategory category : categories.values()) {
            int slot = category.getGuiSlot();
            if (slot >= 0 && slot < GUI_SIZE) {
                inventory.setItem(slot, createCategoryIcon(category));
            }
        }

        // Close button
        inventory.setItem(49, createItem(Material.BARRIER, "§cClose", List.of("§7Click to close")));
    }

    private ItemStack createCategoryIcon(ForgeCategory category) {
        ItemStack item = new ItemStack(category.getIconMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(category.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.addAll(category.getDescription());
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

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public ForgeCategory getCategoryAtSlot(int slot) {
        for (ForgeCategory category : categories.values()) {
            if (category.getGuiSlot() == slot) {
                return category;
            }
        }
        return null;
    }

    public boolean isCloseSlot(int slot) {
        return slot == 49;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}