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

import java.util.*;

/**
 * Recipe selection GUI for a specific category.
 */
public class ForgeRecipeGUI implements InventoryHolder {

    private static final int GUI_SIZE = 54;
    private static final String TITLE = "§8Anvil";

    private static final int[] CATEGORY_SLOTS = {0, 9, 18, 27, 36, 45};
    private static final int[] RECIPE_SLOTS = {
            2, 3, 4, 5, 6, 7, 8,
            11, 12, 13, 14, 15, 16, 17,
            20, 21, 22, 23, 24, 25, 26,
            29, 30, 31, 32, 33, 34, 35,
            38, 39, 40, 41, 42, 43, 44
    };

    private static final int CLOSE_SLOT = 49;
    private static final int PREV_PAGE_SLOT = 47;
    private static final int NEXT_PAGE_SLOT = 51;

    private final ForgeCategory category;
    private final Map<String, ForgeCategory> allCategories;
    private final Map<String, ForgeRecipe> allRecipes;
    private final List<String> visibleRecipeIds;
    private final Map<Integer, String> slotToRecipeId;
    private final Inventory inventory;

    private final int page;
    private final int totalPages;

    public ForgeRecipeGUI(ForgeCategory category, Map<String, ForgeCategory> allCategories,
                          Map<String, ForgeRecipe> allRecipes, int page, Player player) {
        this.category = category;
        this.allCategories = allCategories != null ? allCategories : Map.of();
        this.allRecipes = allRecipes;
        this.slotToRecipeId = new HashMap<>();

        this.visibleRecipeIds = filterRecipesByPermission(category.getRecipeIds(), player);
        this.totalPages = Math.max(1, (int) Math.ceil((double) visibleRecipeIds.size() / RECIPE_SLOTS.length));
        this.page = clamp(page, 0, totalPages - 1);

        this.inventory = Bukkit.createInventory(this, GUI_SIZE, TITLE);
        setupGUI(player);
    }

    // Backwards compatibility constructors
    public ForgeRecipeGUI(ForgeCategory category, Map<String, ForgeRecipe> allRecipes, int page, Player player) {
        this(category, Map.of(), allRecipes, page, player);
    }

    public ForgeRecipeGUI(ForgeCategory category, Map<String, ForgeRecipe> allRecipes, int page) {
        this.category = category;
        this.allCategories = Map.of();
        this.allRecipes = allRecipes;
        this.slotToRecipeId = new HashMap<>();
        this.visibleRecipeIds = new ArrayList<>(category.getRecipeIds());
        this.totalPages = Math.max(1, (int) Math.ceil((double) visibleRecipeIds.size() / RECIPE_SLOTS.length));
        this.page = clamp(page, 0, totalPages - 1);
        this.inventory = Bukkit.createInventory(this, GUI_SIZE, TITLE);
        setupGUIBasic();
    }

    // ==================== SETUP ====================

    private void setupGUI(Player player) {
        fillBackground();
        placeCategories();
        placeRecipes();
        placeNavigation();
    }

    private void setupGUIBasic() {
        fillBackground();
        placeRecipes();
        placeNavigation();
    }

    private void fillBackground() {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, 0);
        for (int i = 0; i < GUI_SIZE; i++) {
            inventory.setItem(i, filler);
        }
    }

    private void placeCategories() {
        int catIndex = 0;
        for (ForgeCategory cat : allCategories.values()) {
            if (catIndex >= CATEGORY_SLOTS.length) break;

            boolean selected = cat.getId().equals(category.getId());
            inventory.setItem(CATEGORY_SLOTS[catIndex], createCategoryIcon(cat, selected));
            catIndex++;
        }
    }

    private void placeRecipes() {
        int startIndex = page * RECIPE_SLOTS.length;
        int endIndex = Math.min(startIndex + RECIPE_SLOTS.length, visibleRecipeIds.size());

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < RECIPE_SLOTS.length; i++) {
            String recipeId = visibleRecipeIds.get(i);
            ForgeRecipe recipe = allRecipes.get(recipeId);

            if (recipe != null) {
                int slot = RECIPE_SLOTS[slotIndex];
                inventory.setItem(slot, createRecipeIcon(recipe));
                slotToRecipeId.put(slot, recipeId);
                slotIndex++;
            }
        }
    }

    private void placeNavigation() {
        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, createItem(Material.ARROW, "§e« Previous",
                    List.of("§7Page " + page), 0));
        }

        if (page < totalPages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, createItem(Material.ARROW, "§eNext »",
                    List.of("§7Page " + (page + 2)), 0));
        }

        inventory.setItem(CLOSE_SLOT, createItem(Material.BARRIER, "§cClose", null, 0));
    }

    // ==================== ITEM CREATION ====================

    private ItemStack createCategoryIcon(ForgeCategory cat, boolean selected) {
        ItemStack item = new ItemStack(cat.getIconMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String prefix = selected ? "§a§l" : "§7";
            meta.setDisplayName(prefix + stripColor(cat.getDisplayName()));

            List<String> lore = List.of(selected ? "§a▶ Selected" : "§eClick to view");
            meta.setLore(lore);

            if (cat.getIconCmd() > 0) {
                meta.setCustomModelData(cat.getIconCmd());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createRecipeIcon(ForgeRecipe recipe) {
        Material material = Material.IRON_INGOT;
        int cmd = 0;

        if (recipe.getFrame(0) != null) {
            material = recipe.getFrame(0).material();
            cmd = recipe.getFrame(0).customModelData();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6" + formatName(recipe.getId()));
            meta.setLore(buildRecipeLore(recipe));

            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private List<String> buildRecipeLore(ForgeRecipe recipe) {
        List<String> lore = new ArrayList<>();
        lore.add("§7§m─────────────────");
        lore.add("");

        if (recipe.hasInput()) {
            lore.add("§f§lMaterials:");
            lore.add("§e  " + recipe.getInputAmount() + "x §7" + formatName(recipe.getInputId()));
        }

        lore.add("");
        lore.add("§7Hits: §f" + recipe.getHits());
        lore.add("§7Difficulty: " + formatDifficulty(recipe.getTargetSize()));

        if (recipe.hasCondition()) {
            lore.add("");
            lore.add("§c⚠ Requires skill check");
        }

        lore.add("");
        lore.add("§7§m─────────────────");
        lore.add("§e▶ Click to forge");

        return lore;
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

    // ==================== UTILITIES ====================

    private List<String> filterRecipesByPermission(List<String> recipeIds, Player player) {
        List<String> filtered = new ArrayList<>();
        for (String recipeId : recipeIds) {
            ForgeRecipe recipe = allRecipes.get(recipeId);
            if (recipe == null) continue;

            if (!recipe.hasPermission() || player.hasPermission(recipe.getPermission())) {
                filtered.add(recipeId);
            }
        }
        return filtered;
    }

    private String formatName(String id) {
        if (id == null || id.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        for (String part : id.split("_")) {
            if (!part.isEmpty()) {
                result.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    private String formatDifficulty(double targetSize) {
        if (targetSize >= 0.5) return "§aEasy";
        if (targetSize >= 0.35) return "§eNormal";
        if (targetSize >= 0.25) return "§6Hard";
        return "§cExtreme";
    }

    private String stripColor(String text) {
        return text == null ? "" : text.replaceAll("§[0-9a-fk-or]", "");
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== PUBLIC API ====================

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public Optional<String> getRecipeIdAtSlot(int slot) {
        return Optional.ofNullable(slotToRecipeId.get(slot));
    }

    public Optional<ForgeCategory> getCategoryAtSlot(int slot) {
        int index = 0;
        for (ForgeCategory cat : allCategories.values()) {
            if (index < CATEGORY_SLOTS.length && slot == CATEGORY_SLOTS[index]) {
                return Optional.of(cat);
            }
            index++;
        }
        return Optional.empty();
    }

    public boolean isCloseSlot(int slot) {
        return slot == CLOSE_SLOT;
    }

    public boolean isPrevPageSlot(int slot) {
        return slot == PREV_PAGE_SLOT && page > 0;
    }

    public boolean isNextPageSlot(int slot) {
        return slot == NEXT_PAGE_SLOT && page < totalPages - 1;
    }

    public ForgeCategory getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean isBackSlot(int slot) {
        return slot == CLOSE_SLOT;  // Same as close slot - goes back to categories
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}