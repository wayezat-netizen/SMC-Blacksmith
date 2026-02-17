package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.BellowsConfig;
import com.simmc.blacksmith.config.BlacksmithConfig;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.HammerConfig;
import com.simmc.blacksmith.forge.ForgeCategory;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.forge.gui.ForgeCategoryGUI;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.furnace.FurnaceType;
import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles block interactions for furnaces and anvils.
 */
public class BlockInteractListener implements Listener {

    private static final Set<Material> ANVIL_MATERIALS = EnumSet.of(
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL
    );

    private static final String HEAT_TOOL_ID = "heat_tool";
    private static final int HEAT_TOOL_BOOST = 50;
    private static final double FURNITURE_SEARCH_RADIUS = 1.5;

    private final FurnaceManager furnaceManager;
    private final ConfigManager configManager;

    // Lazy-loaded hooks
    private SMCCoreHook smcHook;
    private CraftEngineHook ceHook;
    private boolean hooksInitialized;

    public BlockInteractListener(FurnaceManager furnaceManager, ConfigManager configManager) {
        this.furnaceManager = furnaceManager;
        this.configManager = configManager;
        this.hooksInitialized = false;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isValidInteraction(event)) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Location location = block.getLocation();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Hammer + Anvil → Open Forge GUI (always check, even if event was cancelled)
        if (isAnvilBlock(block) && isHammer(itemInHand)) {
            event.setCancelled(true);
            openForgeGUI(player, location, itemInHand);
            return;
        }

        // Skip the rest if event is already cancelled (e.g., by BellowsListener)
        if (event.isCancelled()) {
            return;
        }

        // Check if holding bellows - skip furnace GUI
        if (isBellows(itemInHand)) {
            return;
        }

        // Existing furnace interaction
        if (furnaceManager.isFurnace(location)) {
            handleExistingFurnaceInteraction(player, location, itemInHand, event);
            return;
        }

        // Check for CraftEngine furniture and validate against config
        handleFurnitureInteraction(player, block, location, itemInHand, event);
    }

    // ==================== VALIDATION ====================

    private boolean isValidInteraction(PlayerInteractEvent event) {
        return event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getHand() == EquipmentSlot.HAND
                && event.getClickedBlock() != null;
    }

    private boolean isAnvilBlock(Block block) {
        return block != null && ANVIL_MATERIALS.contains(block.getType());
    }

    // ==================== BELLOWS CHECK ====================

    private boolean isBellows(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        BellowsConfig bellowsConfig = configManager.getBellowsConfig();
        if (bellowsConfig == null || bellowsConfig.getBellowsTypeCount() == 0) {
            return false;
        }

        ensureHooksInitialized();

        if (smcHook != null && smcHook.isAvailable()) {
            String smcId = smcHook.getItemId(item);
            if (smcId != null && !smcId.isEmpty()) {
                return bellowsConfig.getAllTypes().stream()
                        .anyMatch(t -> t.isSMCCore() && t.itemId().equalsIgnoreCase(smcId));
            }
        }

        String materialName = item.getType().name();
        return bellowsConfig.getAllTypes().stream()
                .anyMatch(t -> t.isVanilla() && t.itemId().equalsIgnoreCase(materialName));
    }

    // ==================== HAMMER DETECTION ====================

    private boolean isHammer(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        HammerConfig hammerConfig = configManager.getHammerConfig();
        if (hammerConfig == null || hammerConfig.getHammerTypeCount() == 0) {
            // Debug: config not loaded
            return false;
        }

        Optional<HammerConfig.HammerType> hammerType = getHammerType(item);
        return hammerType.isPresent();
    }

    private Optional<HammerConfig.HammerType> getHammerType(ItemStack item) {
        if (item == null || item.getType().isAir()) return Optional.empty();

        ensureHooksInitialized();

        HammerConfig hammerConfig = configManager.getHammerConfig();
        if (hammerConfig == null) return Optional.empty();

        // Check SMCCore items first
        if (smcHook != null && smcHook.isAvailable()) {
            String smcId = smcHook.getItemId(item);
            if (smcId != null && !smcId.isEmpty()) {
                Optional<HammerConfig.HammerType> result = hammerConfig.getAllHammerTypes().values().stream()
                        .filter(t -> t.type().equalsIgnoreCase("smc") && t.itemId().equalsIgnoreCase(smcId))
                        .findFirst();
                if (result.isPresent()) {
                    return result;
                }
            }
        }

        // Check vanilla items - compare item material name with configured IDs
        String materialName = item.getType().name();
        return hammerConfig.getAllHammerTypes().values().stream()
                .filter(t -> t.type().equalsIgnoreCase("minecraft") && t.itemId().equalsIgnoreCase(materialName))
                .findFirst();
    }

    // ==================== FORGE GUI ====================

    private void openForgeGUI(Player player, Location anvilLocation, ItemStack hammerItem) {
        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        ForgeManager forgeManager = plugin.getForgeManager();

        if (forgeManager.hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active forge session!");
            return;
        }

        if (plugin.getQuenchingManager().hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cComplete your current session first!");
            return;
        }

        forgeManager.setPlayerAnvilLocation(player.getUniqueId(), anvilLocation);

        getHammerType(hammerItem).ifPresent(hammerType ->
                forgeManager.setPlayerHammerType(player.getUniqueId(), hammerType));

        BlacksmithConfig config = configManager.getBlacksmithConfig();
        Map<String, ForgeCategory> categories = config.getCategories();

        if (categories.isEmpty()) {
            player.sendMessage("§cNo forge recipes configured!");
            return;
        }

        new ForgeCategoryGUI(categories).open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
    }

    // ==================== EXISTING FURNACE INTERACTION ====================

    private void handleExistingFurnaceInteraction(Player player, Location location,
                                                  ItemStack itemInHand, PlayerInteractEvent event) {
        // Don't open GUI if holding bellows - let BellowsListener handle it
        if (isBellows(itemInHand)) {
            return;
        }

        if (isHeatTool(itemInHand)) {
            event.setCancelled(true);
            applyHeatTool(player, location);
            return;
        }

        // Only open GUI if hand is empty
        if (itemInHand != null && !itemInHand.getType().isAir()) {
            // Hand has item but it's not bellows or heat tool - don't open GUI
            return;
        }

        event.setCancelled(true);
        furnaceManager.openFurnaceGUI(player, location);
    }

    private boolean isHeatTool(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ensureHooksInitialized();
        if (smcHook == null || !smcHook.isAvailable()) return false;

        String itemId = smcHook.getItemId(item);
        return HEAT_TOOL_ID.equalsIgnoreCase(itemId);
    }

    private void applyHeatTool(Player player, Location location) {
        FurnaceInstance furnace = furnaceManager.getFurnace(location);
        if (furnace == null) return;

        int maxTemp = furnace.getType().getMaxTemperature();
        int currentTarget = furnace.getTargetTemperature();
        int newTarget = Math.min(currentTarget + HEAT_TOOL_BOOST, maxTemp);

        furnace.setTargetTemperature(newTarget);

        String message = configManager.getMessageConfig().getHeatToolUsed();
        player.sendMessage(message != null && !message.isEmpty()
                ? message
                : "§6Temperature boosted to " + newTarget + "°C!");

        player.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.5f);
    }

    // ==================== CE FURNITURE HANDLING ====================

    private void handleFurnitureInteraction(Player player, Block block, Location location,
                                            ItemStack itemInHand, PlayerInteractEvent event) {
        // Get the CE furniture ID at this location
        String furnitureId = getCraftEngineFurnitureId(block, location);

        if (furnitureId == null || furnitureId.isEmpty()) {
            // Not a CE furniture - ignore
            return;
        }

        // Check if this furniture ID is registered to a furnace type
        Optional<FurnaceType> furnaceTypeOpt = configManager.getFurnaceConfig()
                .getFurnaceTypeForFurniture(furnitureId);

        if (furnaceTypeOpt.isEmpty()) {
            // Not a registered furnace
            return;
        }

        FurnaceType furnaceType = furnaceTypeOpt.get();

        // Validate furniture match
        if (!furnaceType.matchesFurniture(furnitureId)) {
            return;
        }

        // Don't process if holding bellows - let BellowsListener handle it
        if (isBellows(itemInHand)) {
            return;
        }

        // Create or get existing furnace instance (needed for bellows to work)
        FurnaceInstance instance = furnaceManager.createFurnace(furnaceType.getId(), location);
        if (instance == null) {
            player.sendMessage("§cFailed to create furnace!");
            return;
        }

        // Heat tool handling
        if (isHeatTool(itemInHand)) {
            event.setCancelled(true);
            applyHeatTool(player, location);
            return;
        }

        // Open furnace GUI (right-click with any item except bellows/heat tool)
        event.setCancelled(true);
        furnaceManager.openFurnaceGUI(player, location);
    }

    /**
     * Gets the CraftEngine furniture/block ID at a location.
     * Checks both block and nearby ItemDisplay entities.
     */
    private String getCraftEngineFurnitureId(Block block, Location location) {
        ensureHooksInitialized();

        if (ceHook == null || !ceHook.isAvailable()) {
            return null;
        }

        // Method 1: Check if block itself is a CE block (barrier block with ItemDisplay)
        if (block.getType() == Material.BARRIER) {
            String id = findFurnitureIdFromItemDisplays(location);
            if (id != null) return id;
        }

        // Method 2: Check if the block is a CE custom block
        String blockId = getCEBlockId(block);
        if (blockId != null) return blockId;

        // Method 3: Check nearby ItemDisplays for furniture
        String nearbyId = findFurnitureIdFromItemDisplays(location);
        if (nearbyId != null) return nearbyId;

        return null;
    }

    /**
     * Tries to get CE block ID from the block itself.
     */
    private String getCEBlockId(Block block) {

        ensureHooksInitialized();
        if (ceHook == null) return null;

        // Create a temporary ItemStack to check
        ItemStack blockItem = new ItemStack(block.getType());
        String ceId = ceHook.getItemId(blockItem);

        // Only return if it's actually a CE item, not just a vanilla block
        if (ceId != null && ceId.contains(":")) {
            return ceId;
        }

        return null;
    }

    /**
     * Finds furniture ID from nearby ItemDisplay entities.
     */
    private String findFurnitureIdFromItemDisplays(Location location) {
        Location center = location.clone().add(0.5, 0.5, 0.5);

        Collection<Entity> nearby = location.getWorld().getNearbyEntities(
                center,
                FURNITURE_SEARCH_RADIUS,
                FURNITURE_SEARCH_RADIUS,
                FURNITURE_SEARCH_RADIUS,
                entity -> entity instanceof ItemDisplay
        );

        for (Entity entity : nearby) {
            // Method 1: Check custom name (some CE versions store ID here)
            String customName = entity.getCustomName();
            if (customName != null) {
                if (customName.startsWith("ce:")) {
                    return customName.substring(3);
                }
                if (customName.contains(":")) {
                    return customName;
                }
            }

            // Method 2: Check the item in the ItemDisplay
            if (entity instanceof ItemDisplay itemDisplay) {
                ItemStack displayItem = itemDisplay.getItemStack();
                if (displayItem != null && !displayItem.getType().isAir()) {
                    String ceId = ceHook.getItemId(displayItem);
                    if (ceId != null && !ceId.isEmpty()) {
                        return ceId;
                    }
                }
            }
        }

        return null;
    }

    // ==================== CE ENTITY INTERACTION ====================

    /**
     * Handles right-click on CE furniture entities (ItemDisplay).
     * Opens furnace GUI if the entity is a registered furnace furniture.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof ItemDisplay itemDisplay)) return;

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Don't process if holding bellows
        if (isBellows(itemInHand)) {
            return;
        }

        // Get furniture ID from the ItemDisplay
        String furnitureId = getFurnitureIdFromEntity(itemDisplay);
        if (furnitureId == null || furnitureId.isEmpty()) {
            return;
        }

        // Check if this furniture ID is registered to a furnace type
        Optional<FurnaceType> furnaceTypeOpt = configManager.getFurnaceConfig()
                .getFurnaceTypeForFurniture(furnitureId);

        if (furnaceTypeOpt.isEmpty()) {
            return;
        }

        FurnaceType furnaceType = furnaceTypeOpt.get();
        Location location = itemDisplay.getLocation().getBlock().getLocation();

        // Create or get existing furnace instance
        FurnaceInstance instance = furnaceManager.createFurnace(furnaceType.getId(), location);
        if (instance == null) {
            return;
        }

        // Heat tool handling
        if (isHeatTool(itemInHand)) {
            event.setCancelled(true);
            applyHeatTool(player, location);
            return;
        }

        // Open furnace GUI
        event.setCancelled(true);
        furnaceManager.openFurnaceGUI(player, location);
    }

    /**
     * Gets furniture ID from an ItemDisplay entity.
     */
    private String getFurnitureIdFromEntity(ItemDisplay itemDisplay) {
        ensureHooksInitialized();

        // Check custom name
        String customName = itemDisplay.getCustomName();
        if (customName != null) {
            if (customName.startsWith("ce:")) {
                return customName.substring(3);
            }
            if (customName.contains(":")) {
                return customName;
            }
        }

        // Check the item in the ItemDisplay
        ItemStack displayItem = itemDisplay.getItemStack();
        if (displayItem != null && !displayItem.getType().isAir() && ceHook != null) {
            String ceId = ceHook.getItemId(displayItem);
            if (ceId != null && !ceId.isEmpty()) {
                return ceId;
            }
        }

        return null;
    }

    // ==================== HOOKS ====================

    private void ensureHooksInitialized() {
        if (hooksInitialized) return;
        hooksInitialized = true;

        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        if (plugin != null) {
            smcHook = plugin.getSmcCoreHook();
            ceHook = plugin.getCraftEngineHook();
        }
    }
}