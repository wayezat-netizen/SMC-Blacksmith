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

    // Default layout - fuel directly below inputs
    private static final int[] DEFAULT_INPUT_SLOTS = {10, 11, 19, 20};
    private static final int DEFAULT_FUEL_SLOT = 29;
    private static final int DEFAULT_OUTPUT_SLOT = 24;
    private static final int GUI_SIZE = 54;

    // Disable debug logging for production
    private static final boolean DEBUG = false;

    // Cache filler item to reduce object creation
    private static final ItemStack FILLER_ITEM = createStaticFillerItem();

    private final FurnaceInstance furnace;
    private final MessageConfig messages;
    private final ItemProviderRegistry itemRegistry;
    private final FuelConfig fuelConfig;

    private final Inventory inventory;
    private final int[] inputSlots;
    private final int fuelSlot;
    private final int outputSlot;

    // Pre-computed interactable slots for fast lookup
    private final boolean[] interactableSlotMask;

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

        // Pre-compute interactable slots for O(1) lookup
        this.interactableSlotMask = new boolean[GUI_SIZE];
        for (int slot : inputSlots) {
            if (slot >= 0 && slot < GUI_SIZE) {
                interactableSlotMask[slot] = true;
            }
        }
        if (fuelSlot >= 0 && fuelSlot < GUI_SIZE) {
            interactableSlotMask[fuelSlot] = true;
        }
        if (outputSlot >= 0 && outputSlot < GUI_SIZE) {
            interactableSlotMask[outputSlot] = true;
        }

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
        // Use cached filler item
        for (int i = 0; i < GUI_SIZE; i++) {
            if (!isInteractableSlotFast(i)) {
                inv.setItem(i, FILLER_ITEM);
            }
        }
    }

    /**
     * Creates a static filler item once at class load time.
     */
    private static ItemStack createStaticFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Fast O(1) interactable slot check using pre-computed mask.
     */
    private boolean isInteractableSlotFast(int slot) {
        if (slot < 0 || slot >= GUI_SIZE) return false;
        return interactableSlotMask[slot];
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

        // Case 1: GUI is empty - player took all fuel or never had any
        if (!guiHasFuel) {
            // Player has emptied the fuel slot - clear the furnace's fuel
            debug("Case 1: GUI empty - clearing furnace fuel slot");
            furnace.setFuelSlot(null);
            return;
        }

        // Case 2: No fuel existed when GUI opened - player adding new fuel
        if (fuelAmountOnOpen == 0) {
            debug("Case 2: Fresh fuel added (was empty on open) - using GUI value: " + guiFuel.getAmount());
            furnace.setFuelSlot(guiFuel.clone());
            return;
        }

        // Case 3: GUI has fuel and furnace doesn't (furnace consumed all)
        if (!furnaceHasFuel) {
            debug("Case 3: Furnace consumed all fuel, player has " + guiFuel.getAmount() + " - using GUI");
            furnace.setFuelSlot(guiFuel.clone());
            return;
        }

        // Case 4: Both have fuel - compare types
        boolean sameType = guiFuel.getType() == furnaceFuel.getType();

        if (!sameType) {
            // Different fuel type - player replaced fuel, use GUI value
            debug("Case 4a: Different fuel type - using GUI");
            furnace.setFuelSlot(guiFuel.clone());
            return;
        }

        // Same type - figure out what player did
        int guiAmount = guiFuel.getAmount();
        int furnaceAmount = furnaceFuel.getAmount();

        // Calculate what the player changed from when they opened the GUI
        int playerChange = guiAmount - fuelAmountOnOpen;

        if (playerChange > 0) {
            // Player added more fuel to existing stack
            // Add the difference to furnace's current amount
            int newAmount = furnaceAmount + playerChange;
            debug("Case 4b: Player added " + playerChange + " fuel, new amount: " + newAmount);
            ItemStack newFuel = guiFuel.clone();
            newFuel.setAmount(Math.min(newAmount, guiFuel.getMaxStackSize()));
            furnace.setFuelSlot(newFuel);
        } else if (playerChange < 0) {
            // Player took fuel - apply their take to furnace's current amount
            int playerTook = -playerChange;
            int newAmount = Math.max(0, furnaceAmount - playerTook);
            debug("Case 4c: Player took " + playerTook + " fuel, new amount: " + newAmount);

            if (newAmount <= 0) {
                furnace.setFuelSlot(null);
            } else {
                ItemStack newFuel = furnaceFuel.clone();
                newFuel.setAmount(newAmount);
                furnace.setFuelSlot(newFuel);
            }
        } else {
            // Player didn't change amount - keep furnace's consumed state
            debug("Case 4d: Player unchanged, keeping furnace amount: " + furnaceAmount);
            // Don't update - furnace has the correct consumed amount
        }
    }

    /**
     * Refreshes the GUI with current furnace fuel state.
     * Called periodically to show fuel being consumed by the furnace.
     */
    public void refreshFuelSlot() {
        ItemStack furnaceFuel = furnace.getFuelSlot();
        ItemStack guiFuel = inventory.getItem(fuelSlot);

        boolean furnaceHasFuel = isValidItem(furnaceFuel);
        boolean guiHasFuel = isValidItem(guiFuel);

        // Case 1: Furnace has no fuel
        if (!furnaceHasFuel) {
            if (guiHasFuel && fuelAmountOnOpen > 0) {
                // Furnace consumed all fuel - clear GUI display
                inventory.setItem(fuelSlot, null);
                fuelAmountOnOpen = 0;
                fuelSlotOnOpen = null;
                debug("Refreshed fuel display: empty (all consumed)");
            }
            return;
        }

        // Case 2: Furnace has fuel but GUI is empty - restore display
        if (!guiHasFuel) {
            inventory.setItem(fuelSlot, furnaceFuel.clone());
            fuelAmountOnOpen = furnaceFuel.getAmount();
            fuelSlotOnOpen = furnaceFuel.clone();
            debug("Refreshed fuel display: restored " + furnaceFuel.getAmount());
            return;
        }

        // Case 3: Both have fuel - check if we need to update display
        if (guiFuel.getType() == furnaceFuel.getType()) {
            // Furnace consumed some fuel - update GUI to show remaining
            if (furnaceFuel.getAmount() < guiFuel.getAmount()) {
                inventory.setItem(fuelSlot, furnaceFuel.clone());
                fuelAmountOnOpen = furnaceFuel.getAmount();
                debug("Refreshed fuel display: " + furnaceFuel.getAmount() + " (consumed some)");
            }
        }
        // If types differ, don't change - player may have added different fuel
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

    /**
     * Checks if slot is interactable using pre-computed boolean mask.
     */
    public boolean isInteractableSlot(int slot) {
        if (slot < 0 || slot >= GUI_SIZE) return false;
        return interactableSlotMask[slot];
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