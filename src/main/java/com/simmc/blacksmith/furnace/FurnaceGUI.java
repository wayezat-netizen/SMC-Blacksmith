package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.config.FuelConfig;
import com.simmc.blacksmith.config.MessageConfig;
import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;

/**
 * Furnace GUI - does NOT interfere with fuel consumption.
 */
public class FurnaceGUI implements InventoryHolder {

    private static final int[] DEFAULT_INPUT_SLOTS = {10, 11, 19, 20};
    private static final int DEFAULT_FUEL_SLOT = 40;
    private static final int DEFAULT_OUTPUT_SLOT = 24;
    private static final int GUI_SIZE = 54;

    private final FurnaceInstance furnace;
    private final MessageConfig messages;
    private final ItemProviderRegistry itemRegistry;
    private final FuelConfig fuelConfig;

    private final Inventory inventory;
    private final int[] inputSlots;
    private final int fuelSlot;
    private final int outputSlot;

    // Track what was in fuel slot when GUI opened
    private ItemStack fuelSlotOnOpen;

    public FurnaceGUI(FurnaceInstance furnace, MessageConfig messages,
                      ItemProviderRegistry itemRegistry, FuelConfig fuelConfig) {
        this.furnace = furnace;
        this.messages = messages;
        this.itemRegistry = itemRegistry;
        this.fuelConfig = fuelConfig;

        FurnaceType type = furnace.getType();
        this.inputSlots = type.getInputSlots() != null ? type.getInputSlots() : DEFAULT_INPUT_SLOTS;
        this.fuelSlot = type.getFuelSlot() >= 0 ? type.getFuelSlot() : DEFAULT_FUEL_SLOT;
        this.outputSlot = type.getOutputSlot() >= 0 ? type.getOutputSlot() : DEFAULT_OUTPUT_SLOT;

        this.inventory = createInventory();
        loadItemsFromFurnace();
    }

    public FurnaceGUI(FurnaceInstance furnace, MessageConfig messages, ItemProviderRegistry itemRegistry) {
        this(furnace, messages, itemRegistry, null);
    }

    private Inventory createInventory() {
        String title = messages.getFurnaceTitle();
        Inventory inv = Bukkit.createInventory(this, GUI_SIZE, title);
        fillBackground(inv);
        return inv;
    }

    private void fillBackground(Inventory inv) {
        ItemStack filler = createFillerItem();
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!isInteractableSlot(i)) {
                inv.setItem(i, filler);
            }
        }
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    // ==================== ITEM MANAGEMENT ====================

    public void loadItemsFromFurnace() {
        // Load input slots
        ItemStack[] furnaceInputs = furnace.getInputSlots();
        for (int i = 0; i < inputSlots.length; i++) {
            ItemStack item = (i < furnaceInputs.length && furnaceInputs[i] != null)
                    ? furnaceInputs[i].clone() : null;
            inventory.setItem(inputSlots[i], item);
        }

        // Load fuel slot and remember what it was
        ItemStack fuel = furnace.getFuelSlot();
        fuelSlotOnOpen = fuel != null ? fuel.clone() : null;
        inventory.setItem(fuelSlot, fuel != null ? fuel.clone() : null);

        // Load output slot
        ItemStack output = furnace.getOutputSlot();
        inventory.setItem(outputSlot, output != null ? output.clone() : null);
    }

    /**
     * Saves items from GUI back to furnace.
     * CRITICAL: Handle fuel slot carefully to not interfere with burning.
     */
    public void saveItemsToFurnace() {
        // Save input slots
        ItemStack[] inputs = new ItemStack[inputSlots.length];
        for (int i = 0; i < inputSlots.length; i++) {
            ItemStack item = inventory.getItem(inputSlots[i]);
            inputs[i] = isValidItem(item) ? item.clone() : null;
        }
        furnace.setInputSlots(inputs);

        // Save fuel slot - ALWAYS sync what's in GUI
        ItemStack guiFuel = inventory.getItem(fuelSlot);
        if (isValidItem(guiFuel)) {
            furnace.setFuelSlot(guiFuel.clone());
        } else {
            // GUI is empty - player took fuel or it was consumed
            furnace.setFuelSlot(null);
        }

        // Save output slot
        ItemStack output = inventory.getItem(outputSlot);
        furnace.setOutputSlot(isValidItem(output) ? output.clone() : null);
    }

    private boolean isValidItem(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    // ==================== SLOT CHECKS ====================

    public boolean isInputSlot(int slot) {
        for (int inputSlot : inputSlots) {
            if (slot == inputSlot) return true;
        }
        return false;
    }

    public boolean isFuelSlot(int slot) {
        return slot == fuelSlot;
    }

    public boolean isOutputSlot(int slot) {
        return slot == outputSlot;
    }

    public boolean isInteractableSlot(int slot) {
        return isInputSlot(slot) || isFuelSlot(slot) || isOutputSlot(slot);
    }

    public boolean isValidFuel(ItemStack item) {
        if (fuelConfig == null) return true;
        return fuelConfig.isFuel(item);
    }

    public boolean canPlaceItem(int slot, ItemStack item) {
        if (!isInteractableSlot(slot)) return false;
        if (isOutputSlot(slot)) return false;
        if (isFuelSlot(slot)) return isValidFuel(item);
        return true;
    }

    // ==================== ACTIONS ====================

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void refresh() {
        loadItemsFromFurnace();
    }

    // ==================== GETTERS ====================

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public FurnaceInstance getFurnace() {
        return furnace;
    }

    public int[] getInputSlots() {
        return Arrays.copyOf(inputSlots, inputSlots.length);
    }

    public int getFuelSlotIndex() {
        return fuelSlot;
    }

    public int getOutputSlotIndex() {
        return outputSlot;
    }

    public static int[] getDefaultInputSlots() {
        return Arrays.copyOf(DEFAULT_INPUT_SLOTS, DEFAULT_INPUT_SLOTS.length);
    }

    public static int getDefaultFuelSlot() {
        return DEFAULT_FUEL_SLOT;
    }

    public static int getDefaultOutputSlot() {
        return DEFAULT_OUTPUT_SLOT;
    }
}