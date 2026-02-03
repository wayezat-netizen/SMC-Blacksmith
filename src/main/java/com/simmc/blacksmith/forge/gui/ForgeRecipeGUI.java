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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForgeRecipeGUI implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final int[] RECIPE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final ForgeCategory category;
    private final Map<String, ForgeRecipe> allRecipes;
    private final Inventory inventory;
    private final Map<Integer, String> slotToRecipeId;
    private int page;

    public ForgeRecipeGUI(ForgeCategory category, Map<String, ForgeRecipe> allRecipes, int page) {
        this.category = category;
        this.allRecipes = allRecipes;
        this.page = page;
        this.slotToRecipeId = new HashMap<>();
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, "§8" + category.getDisplayName());
        setupGUI();
    }

    private void setupGUI() {
        // Fill background
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, glass);
        }

        // Get recipes for this category
        List<String> recipeIds = category.getRecipeIds();
        int startIndex = page * RECIPE_SLOTS.length;
        int endIndex = Math.min(startIndex + RECIPE_SLOTS.length, recipeIds.size());

        // Place recipe icons
        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < RECIPE_SLOTS.length; i++) {
            String recipeId = recipeIds.get(i);
            ForgeRecipe recipe = allRecipes.get(recipeId);

            if (recipe != null) {
                int slot = RECIPE_SLOTS[slotIndex];
                inventory.setItem(slot, createRecipeIcon(recipe));
                slotToRecipeId.put(slot, recipeId);
                slotIndex++;
            }
        }

        // Navigation
        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "§ePrevious Page", List.of("§7Page " + page)));
        }

        if (endIndex < recipeIds.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "§eNext Page", List.of("§7Page " + (page + 2))));
        }

        // Back button
        inventory.setItem(49, createItem(Material.OAK_DOOR, "§cBack to Categories", null));
    }

    private ItemStack createRecipeIcon(ForgeRecipe recipe) {
        Material material = Material.IRON_INGOT;
        int cmd = 0;

        // Use frame_0 as preview if available
        if (recipe.getFrame(0) != null) {
            material = recipe.getFrame(0).material();
            cmd = recipe.getFrame(0).customModelData();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6" + recipe.getId());

            List<String> lore = new ArrayList<>();
            lore.add("§7Required Hits: §f" + recipe.getHits());
            lore.add("§7Difficulty: §f" + formatDifficulty(recipe.getTargetSize()));

            if (recipe.hasInput()) {
                lore.add("");
                lore.add("§7Materials:");
                lore.add("§f  " + recipe.getInputAmount() + "x §e" + recipe.getInputId());
            }

            if (recipe.hasCondition()) {
                lore.add("");
                lore.add("§cRequires skill check");
            }

            lore.add("");
            lore.add("§eClick to start forging");

            meta.setLore(lore);

            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatDifficulty(double targetSize) {
        if (targetSize >= 0.5) return "§aEasy";
        if (targetSize >= 0.3) return "§eNormal";
        if (targetSize >= 0.2) return "§6Hard";
        return "§cExtreme";
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

    public String getRecipeIdAtSlot(int slot) {
        return slotToRecipeId.get(slot);
    }

    public boolean isBackSlot(int slot) {
        return slot == 49;
    }

    public boolean isPrevPageSlot(int slot) {
        return slot == 45 && page > 0;
    }

    public boolean isNextPageSlot(int slot) {
        return slot == 53;
    }

    public ForgeCategory getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}