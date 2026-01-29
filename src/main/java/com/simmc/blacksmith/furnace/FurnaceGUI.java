package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import com.simmc.blacksmith.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FurnaceGUI implements InventoryHolder {

    private static final int[] INPUT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int FUEL_SLOT = 49;
    private static final int OUTPUT_SLOT = 16;
    private static final int PROGRESS_SLOT = 14;
    private static final int TEMPERATURE_SLOT = 23;
    private static final int BELLOWS_SLOT = 41;

    private static final int GUI_SIZE = 54;

    private final FurnaceInstance furnace;
    private final MessageConfig messages;
    private final ItemProviderRegistry itemRegistry;
    private Inventory inventory;

    public FurnaceGUI(FurnaceInstance furnace, MessageConfig messages, ItemProviderRegistry itemRegistry) {
        this.furnace = furnace;
        this.messages = messages;
        this.itemRegistry = itemRegistry;
        setupGUI();
    }

    private void setupGUI() {
        String title = messages.getFurnaceTitle();
        inventory = Bukkit.createInventory(this, GUI_SIZE, title);

        fillBackground();
        loadItemsFromFurnace();
        updateDisplay();
    }

    private void fillBackground() {
        ItemStack glass = createGlassPane(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < GUI_SIZE; i++) {
            if (!isInteractableSlot(i) && i != PROGRESS_SLOT && i != TEMPERATURE_SLOT && i != BELLOWS_SLOT) {
                inventory.setItem(i, glass);
            }
        }

        inventory.setItem(BELLOWS_SLOT, createBellowsItem());
    }

    public void loadItemsFromFurnace() {
        ItemStack[] furnaceInputs = furnace.getInputSlots();
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            if (i < furnaceInputs.length && furnaceInputs[i] != null) {
                inventory.setItem(INPUT_SLOTS[i], furnaceInputs[i].clone());
            } else {
                inventory.setItem(INPUT_SLOTS[i], null);
            }
        }

        ItemStack fuel = furnace.getFuelSlot();
        inventory.setItem(FUEL_SLOT, fuel != null ? fuel.clone() : null);

        ItemStack output = furnace.getOutputSlot();
        inventory.setItem(OUTPUT_SLOT, output != null ? output.clone() : null);
    }

    public void saveItemsToFurnace() {
        ItemStack[] inputs = new ItemStack[9];
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack item = inventory.getItem(INPUT_SLOTS[i]);
            inputs[i] = item != null ? item.clone() : null;
        }
        furnace.setInputSlots(inputs);

        ItemStack fuel = inventory.getItem(FUEL_SLOT);
        furnace.setFuelSlot(fuel != null ? fuel.clone() : null);

        ItemStack output = inventory.getItem(OUTPUT_SLOT);
        furnace.setOutputSlot(output != null ? output.clone() : null);
    }

    public void updateDisplay() {
        updateProgressIndicator();
        updateTemperatureDisplay();
    }

    private void updateProgressIndicator() {
        double progress = furnace.getSmeltProgress();
        FurnaceRecipe recipe = furnace.getCurrentRecipe();

        ItemStack progressItem;
        if (recipe != null) {
            progressItem = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = progressItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aSmelting Progress");
                List<String> lore = new ArrayList<>();
                lore.add("§7Recipe: §f" + recipe.getId());
                lore.add("§7Progress: §f" + (int) (progress * 100) + "%");
                lore.add("");
                lore.add(ColorUtil.createProgressBar(progress, 20));
                meta.setLore(lore);
                progressItem.setItemMeta(meta);
            }
        } else {
            progressItem = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = progressItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cNo Active Recipe");
                List<String> lore = new ArrayList<>();
                lore.add("§7Add ingredients to start smelting");
                meta.setLore(lore);
                progressItem.setItemMeta(meta);
            }
        }

        inventory.setItem(PROGRESS_SLOT, progressItem);
    }

    private void updateTemperatureDisplay() {
        int current = furnace.getCurrentTemperature();
        int max = furnace.getType().getMaxTemperature();
        int minIdeal = furnace.getType().getMinIdealTemperature();
        int maxIdeal = furnace.getType().getMaxIdealTemperature();
        boolean isIdeal = furnace.getType().isIdealTemperature(current);

        Material material;
        String status;
        if (current == 0) {
            material = Material.COAL;
            status = "§7Cold";
        } else if (isIdeal) {
            material = Material.BLAZE_POWDER;
            status = "§aIdeal";
        } else if (current < minIdeal) {
            material = Material.ORANGE_DYE;
            status = "§eToo Cold";
        } else {
            material = Material.RED_DYE;
            status = "§cToo Hot";
        }

        ItemStack tempItem = new ItemStack(material);
        ItemMeta meta = tempItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Temperature");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current: §f" + current + "°C");
            lore.add("§7Maximum: §f" + max + "°C");
            lore.add("§7Ideal Range: §f" + minIdeal + "°C - " + maxIdeal + "°C");
            lore.add("");
            lore.add("§7Status: " + status);
            lore.add("");
            lore.add(createTemperatureBar(current, max, minIdeal, maxIdeal));

            if (furnace.isBurning()) {
                lore.add("");
                lore.add("§7Burning: §aYes");
            }

            meta.setLore(lore);
            tempItem.setItemMeta(meta);
        }

        inventory.setItem(TEMPERATURE_SLOT, tempItem);
    }

    private String createTemperatureBar(int current, int max, int minIdeal, int maxIdeal) {
        int barLength = 20;
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < barLength; i++) {
            int temp = (int) ((double) i / barLength * max);
            int nextTemp = (int) ((double) (i + 1) / barLength * max);

            if (temp <= current && current < nextTemp) {
                bar.append("§f█");
            } else if (temp >= minIdeal && temp <= maxIdeal) {
                bar.append("§a░");
            } else if (temp < current) {
                bar.append("§6█");
            } else {
                bar.append("§8░");
            }
        }

        return bar.toString();
    }

    private ItemStack createBellowsItem() {
        ItemStack item = new ItemStack(Material.LEATHER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6Bellows");
            List<String> lore = new ArrayList<>();
            lore.add("§7Click to increase temperature");
            lore.add("§7beyond fuel limits");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlassPane(Material material, String name) {
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

    public boolean isInputSlot(int slot) {
        for (int inputSlot : INPUT_SLOTS) {
            if (slot == inputSlot) return true;
        }
        return false;
    }

    public boolean isFuelSlot(int slot) {
        return slot == FUEL_SLOT;
    }

    public boolean isOutputSlot(int slot) {
        return slot == OUTPUT_SLOT;
    }

    public boolean isBellowsSlot(int slot) {
        return slot == BELLOWS_SLOT;
    }

    public boolean isInteractableSlot(int slot) {
        return isInputSlot(slot) || isFuelSlot(slot) || isOutputSlot(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public FurnaceInstance getFurnace() {
        return furnace;
    }

    public static int[] getInputSlots() {
        return Arrays.copyOf(INPUT_SLOTS, INPUT_SLOTS.length);
    }

    public static int getFuelSlot() {
        return FUEL_SLOT;
    }

    public static int getOutputSlot() {
        return OUTPUT_SLOT;
    }
}