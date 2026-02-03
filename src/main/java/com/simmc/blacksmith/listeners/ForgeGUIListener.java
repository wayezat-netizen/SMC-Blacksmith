package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.BlacksmithConfig;
import com.simmc.blacksmith.forge.ForgeCategory;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.gui.ForgeCategoryGUI;
import com.simmc.blacksmith.forge.gui.ForgeRecipeGUI;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public class ForgeGUIListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeGUIListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();

        if (inv.getHolder() instanceof ForgeCategoryGUI gui) {
            handleCategoryClick(event, player, gui);
        } else if (inv.getHolder() instanceof ForgeRecipeGUI gui) {
            handleRecipeClick(event, player, gui);
        }
    }

    private void handleCategoryClick(InventoryClickEvent event, Player player, ForgeCategoryGUI gui) {
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        ForgeCategory category = gui.getCategoryAtSlot(slot);
        if (category != null) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            openRecipeGUI(player, category, 0);
        }
    }

    private void handleRecipeClick(InventoryClickEvent event, Player player, ForgeRecipeGUI gui) {
        event.setCancelled(true);

        int slot = event.getRawSlot();

        if (gui.isBackSlot(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            openCategoryGUI(player);
            return;
        }

        if (gui.isPrevPageSlot(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            openRecipeGUI(player, gui.getCategory(), gui.getPage() - 1);
            return;
        }

        if (gui.isNextPageSlot(slot)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            openRecipeGUI(player, gui.getCategory(), gui.getPage() + 1);
            return;
        }

        String recipeId = gui.getRecipeIdAtSlot(slot);
        if (recipeId != null) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.7f, 1.0f);
            forgeManager.startSession(player, recipeId, player.getLocation());
        }
    }

    private void openCategoryGUI(Player player) {
        BlacksmithConfig config = SMCBlacksmith.getInstance().getConfigManager().getBlacksmithConfig();
        Map<String, ForgeCategory> categories = config.getCategories();
        ForgeCategoryGUI gui = new ForgeCategoryGUI(categories);
        gui.open(player);
    }

    private void openRecipeGUI(Player player, ForgeCategory category, int page) {
        BlacksmithConfig config = SMCBlacksmith.getInstance().getConfigManager().getBlacksmithConfig();
        ForgeRecipeGUI gui = new ForgeRecipeGUI(category, config.getRecipes(), page);
        gui.open(player);
    }
}