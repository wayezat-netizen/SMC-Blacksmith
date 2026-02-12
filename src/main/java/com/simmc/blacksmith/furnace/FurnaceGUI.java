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
import java.util.List;

/**
 * Furnace GUI with input restrictions and fuel validation.
 */
public class FurnaceGUI implements InventoryHolder {

    private static final int[] DEFAULT_INPUT_SLOTS = {10, 11, 19, 20};
    private static final int DEFAULT_FUEL_SLOT = 40;
    private static final int DEFAULT_OUTPUT_SLOT = 24;
    private static final int GUI_SIZE = 54;
    private static final boolean DEBUG = true;

    private final FurnaceInstance furnace;
    private final MessageConfig messages;
    private final ItemProviderRegistry itemRegistry;
    private final FuelConfig fuelConfig;

    private final Inventory inventory;
    private final int[] inputSlots;
    private final int fuelSlot;
    private final int outputSlot;

    // Track fuel state when GUI opened
    private ItemStack fuelSlotOnOpen;
    private int fuelAmountOnOpen;

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
        String title = furnace.getType().getGuiTitle();
        if (title == null || title.isEmpty()) {
            title = messages.getFurnaceTitle();
        }
        title = title.replace("&", "§");

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

    private void debug(String msg) {
        if (DEBUG) {
            Bukkit.getLogger().info("[FurnaceGUI] " + msg);
        }
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
        fuelAmountOnOpen = fuel != null ? fuel.getAmount() : 0;
        inventory.setItem(fuelSlot, fuel != null ? fuel.clone() : null);

        debug("Loaded fuel: " + (fuel != null ? fuel.getType() + " x" + fuel.getAmount() : "null"));

        // Load output slot
        ItemStack output = furnace.getOutputSlot();
        inventory.setItem(outputSlot, output != null ? output.clone() : null);
    }

    /**
     * Saves items from GUI back to furnace.
     */
    public void saveItemsToFurnace() {
        // Save input slots
        ItemStack[] inputs = new ItemStack[inputSlots.length];
        for (int i = 0; i < inputSlots.length; i++) {
            ItemStack item = inventory.getItem(inputSlots[i]);
            inputs[i] = isValidItem(item) ? item.clone() : null;
        }
        furnace.setInputSlots(inputs);

        // Handle fuel slot carefully
        saveFuelSlot();

        // Save output slot
        ItemStack output = inventory.getItem(outputSlot);
        furnace.setOutputSlot(isValidItem(output) ? output.clone() : null);
    }

    /**
     * Saves fuel slot while accounting for fuel consumed by furnace during GUI open.
     */
    private void saveFuelSlot() {
        ItemStack guiFuel = inventory.getItem(fuelSlot);
        ItemStack furnaceFuel = furnace.getFuelSlot();

        boolean guiHasFuel = isValidItem(guiFuel);
        boolean furnaceHasFuel = isValidItem(furnaceFuel);

        debug("Saving fuel - GUI: " + (guiHasFuel ? guiFuel.getType() + " x" + guiFuel.getAmount() : "null") +
                " | Furnace: " + (furnaceHasFuel ? furnaceFuel.getType() + " x" + furnaceFuel.getAmount() : "null") +
                " | OnOpen: " + (fuelSlotOnOpen != null ? fuelSlotOnOpen.getType() + " x" + fuelAmountOnOpen : "null"));

        // Case 1: GUI is empty
        if (!guiHasFuel) {
            if (fuelAmountOnOpen > 0) {
                if (furnaceHasFuel) {
                    debug("Case 1a: GUI empty but furnace has fuel - keeping furnace value");
                } else {
                    debug("Case 1b: Both empty - clearing");
                    furnace.setFuelSlot(null);
                }
            } else {
                debug("Case 1c: Was empty, still empty");
                furnace.setFuelSlot(null);
            }
            return;
        }

        // Case 2: GUI has fuel but furnace doesn't
        if (!furnaceHasFuel) {
            debug("Case 2: Player added new fuel");
            furnace.setFuelSlot(guiFuel.clone());
            return;
        }

        // Case 3: Both have fuel - compare
        boolean sameType = guiFuel.getType() == furnaceFuel.getType();

        if (!sameType) {
            debug("Case 3a: Different fuel type - using GUI");
            furnace.setFuelSlot(guiFuel.clone());
            return;
        }

        // Same type - check amounts
        int guiAmount = guiFuel.getAmount();
        int furnaceAmount = furnaceFuel.getAmount();

        if (guiAmount == fuelAmountOnOpen) {
            debug("Case 3b: GUI unchanged, furnace has " + furnaceAmount + " - keeping furnace");
        } else if (guiAmount > furnaceAmount) {
            debug("Case 3c: Player added fuel - using GUI (" + guiAmount + ")");
            furnace.setFuelSlot(guiFuel.clone());
        } else if (guiAmount < furnaceAmount) {
            int playerTook = fuelAmountOnOpen - guiAmount;
            int newAmount = Math.max(0, furnaceAmount - playerTook);

            debug("Case 3d: Player took " + playerTook + " fuel, new amount: " + newAmount);

            if (newAmount <= 0) {
                furnace.setFuelSlot(null);
            } else {
                ItemStack newFuel = furnaceFuel.clone();
                newFuel.setAmount(newAmount);
                furnace.setFuelSlot(newFuel);
            }
        } else {
            debug("Case 3e: Same amount - no change");
        }
    }

    /**
     * Refreshes the GUI with current furnace fuel state.
     */
    public void refreshFuelSlot() {
        ItemStack furnaceFuel = furnace.getFuelSlot();
        ItemStack guiFuel = inventory.getItem(fuelSlot);

        if (isValidItem(guiFuel) && isValidItem(furnaceFuel)) {
            if (guiFuel.getType() == furnaceFuel.getType()) {
                if (furnaceFuel.getAmount() < guiFuel.getAmount()) {
                    inventory.setItem(fuelSlot, furnaceFuel.clone());
                    fuelAmountOnOpen = furnaceFuel.getAmount();
                    debug("Refreshed fuel display: " + furnaceFuel.getAmount());
                }
            }
        } else if (!isValidItem(furnaceFuel) && isValidItem(guiFuel)) {
            inventory.setItem(fuelSlot, null);
            fuelAmountOnOpen = 0;
            debug("Refreshed fuel display: empty (all consumed)");
        }
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

    // ==================== ITEM VALIDATION ====================

    /**
     * Checks if an item is valid fuel.
     */
    public boolean isValidFuel(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;
        if (fuelConfig == null) return true;
        return fuelConfig.isFuel(item);
    }

    /**
     * Checks if an item is allowed in the input slots.
     * Uses furnace type's allowed inputs list.
     */
    public boolean isAllowedInput(ItemStack item) {
        if (item == null || item.getType().isAir()) return true;

        FurnaceType type = furnace.getType();
        if (!type.hasInputRestrictions()) {
            return true; // No restrictions
        }

        return type.isAllowedInput(item, itemRegistry);
    }

    /**
     * Gets the list of allowed input descriptions for display.
     */
    public List<String> getAllowedInputDescriptions() {
        return furnace.getType().getAllowedInputDescriptions();
    }

    /**
     * Checks if this furnace has input restrictions.
     */
    public boolean hasInputRestrictions() {
        return furnace.getType().hasInputRestrictions();
    }

    /**
     * Validates if an item can be placed in a specific slot.
     */
    public boolean canPlaceItem(int slot, ItemStack item) {
        if (!isInteractableSlot(slot)) return false;
        if (isOutputSlot(slot)) return false; // Output is take-only
        if (isFuelSlot(slot)) return isValidFuel(item);
        if (isInputSlot(slot)) return isAllowedInput(item);
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

    public ItemProviderRegistry getItemRegistry() {
        return itemRegistry;
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