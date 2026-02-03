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
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;

public class ForgeGUIListener implements Listener {

    private final ForgeManager forgeManager;

    public ForgeGUIListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        InventoryHolder holder = inv.getHolder();

        // Fast type dispatch
        if (holder instanceof ForgeCategoryGUI gui) {
            event.setCancelled(true);
            handleCategoryClick(player, gui, event.getRawSlot());
        } else if (holder instanceof ForgeRecipeGUI gui) {
            event.setCancelled(true);
            handleRecipeClick(player, gui, event.getRawSlot());
        }
    }

    private void handleCategoryClick(Player player, ForgeCategoryGUI gui, int slot) {
        if (gui.isCloseSlot(slot)) {
            player.closeInventory();
            return;
        }

        ForgeCategory category = gui.getCategoryAtSlot(slot);
        if (category != null) {
            playClickSound(player);
            openRecipeGUI(player, category, 0);
        }
    }

    private void handleRecipeClick(Player player, ForgeRecipeGUI gui, int slot) {
        if (gui.isBackSlot(slot)) {
            playClickSound(player);
            openCategoryGUI(player);
            return;
        }

        if (gui.isPrevPageSlot(slot)) {
            playClickSound(player);
            openRecipeGUI(player, gui.getCategory(), gui.getPage() - 1);
            return;
        }

        if (gui.isNextPageSlot(slot)) {
            playClickSound(player);
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

    private void playClickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    private void openCategoryGUI(Player player) {
        BlacksmithConfig config = SMCBlacksmith.getInstance().getConfigManager().getBlacksmithConfig();
        Map<String, ForgeCategory> categories = config.getCategories();
        new ForgeCategoryGUI(categories).open(player);
    }

    private void openRecipeGUI(Player player, ForgeCategory category, int page) {
        BlacksmithConfig config = SMCBlacksmith.getInstance().getConfigManager().getBlacksmithConfig();
        new ForgeRecipeGUI(category, config.getRecipes(), page).open(player);
    }
}