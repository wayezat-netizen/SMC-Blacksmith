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
    private static final double FURNITURE_SEARCH_RADIUS = 1.0;

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

    /**
     * Use NORMAL priority so BellowsListener (HIGHEST) runs first.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isValidInteraction(event)) return;

        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        Location location = block.getLocation();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Hammer + Anvil → Open Forge GUI
        if (isAnvilBlock(block) && isHammer(itemInHand)) {
            event.setCancelled(true);
            openForgeGUI(player, location, itemInHand);
            return;
        }

        // Check if holding bellows - skip furnace GUI if so
        // (BellowsListener handles this at HIGHEST priority, but double-check here)
        if (isBellows(itemInHand)) {
            // Don't open furnace GUI - bellows listener handles it
            return;
        }

        // Existing furnace interaction
        if (furnaceManager.isFurnace(location)) {
            handleFurnaceInteraction(player, location, itemInHand, event);
            return;
        }

        // Check for CraftEngine furniture
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

    /**
     * Check if item is a bellows to prevent opening furnace GUI.
     */
    private boolean isBellows(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        BellowsConfig bellowsConfig = configManager.getBellowsConfig();
        if (bellowsConfig == null || bellowsConfig.getBellowsTypeCount() == 0) {
            return false;
        }

        ensureHooksInitialized();

        // Check SMCCore items first
        if (smcHook != null && smcHook.isAvailable()) {
            String smcId = smcHook.getItemId(item);
            if (smcId != null && !smcId.isEmpty()) {
                return bellowsConfig.getAllTypes().stream()
                        .anyMatch(t -> t.isSMCCore() && t.itemId().equalsIgnoreCase(smcId));
            }
        }

        // Check vanilla items
        String materialName = item.getType().name();
        return bellowsConfig.getAllTypes().stream()
                .anyMatch(t -> t.isVanilla() && t.itemId().equalsIgnoreCase(materialName));
    }

    // ==================== HAMMER DETECTION ====================

    private boolean isHammer(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        HammerConfig hammerConfig = configManager.getHammerConfig();
        if (hammerConfig == null || hammerConfig.getHammerTypeCount() == 0) {
            return false;
        }

        return getHammerType(item).isPresent();
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
                return hammerConfig.getAllHammerTypes().values().stream()
                        .filter(t -> t.type().equalsIgnoreCase("smc") && t.itemId().equalsIgnoreCase(smcId))
                        .findFirst();
            }
        }

        // Check vanilla items
        String materialName = item.getType().name().toLowerCase();
        return hammerConfig.getAllHammerTypes().values().stream()
                .filter(t -> t.type().equalsIgnoreCase("minecraft") && t.itemId().equalsIgnoreCase(materialName))
                .findFirst();
    }

    // ==================== FORGE GUI ====================

    private void openForgeGUI(Player player, Location anvilLocation, ItemStack hammerItem) {
        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        ForgeManager forgeManager = plugin.getForgeManager();

        // Validation checks
        if (forgeManager.hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cYou already have an active forge session!");
            return;
        }

        if (plugin.getQuenchingManager().hasActiveSession(player.getUniqueId())) {
            player.sendMessage("§cComplete your current session first!");
            return;
        }

        // Store context for recipe selection
        forgeManager.setPlayerAnvilLocation(player.getUniqueId(), anvilLocation);

        getHammerType(hammerItem).ifPresent(hammerType ->
                forgeManager.setPlayerHammerType(player.getUniqueId(), hammerType));

        // Open category GUI
        BlacksmithConfig config = configManager.getBlacksmithConfig();
        Map<String, ForgeCategory> categories = config.getCategories();

        if (categories.isEmpty()) {
            player.sendMessage("§cNo forge recipes configured!");
            return;
        }

        new ForgeCategoryGUI(categories).open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
    }

    // ==================== FURNACE INTERACTION ====================

    private void handleFurnaceInteraction(Player player, Location location,
                                          ItemStack itemInHand, PlayerInteractEvent event) {
        // Heat tool check
        if (isHeatTool(itemInHand)) {
            event.setCancelled(true);
            applyHeatTool(player, location);
            return;
        }

        // Open furnace GUI
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

    // ==================== FURNITURE HANDLING ====================

    private void handleFurnitureInteraction(Player player, Block block, Location location,
                                            ItemStack itemInHand, PlayerInteractEvent event) {
        String furnitureId = getCraftEngineFurnitureId(block, location);
        if (furnitureId == null) return;

        Optional<FurnaceType> furnaceType = getFurnaceTypeForFurniture(furnitureId);
        if (furnaceType.isEmpty()) return;

        // Check if holding bellows - don't open GUI
        if (isBellows(itemInHand)) {
            return;
        }

        event.setCancelled(true);

        FurnaceInstance instance = furnaceManager.createFurnace(furnaceType.get().getId(), location);
        if (instance == null) return;

        if (isHeatTool(itemInHand)) {
            applyHeatTool(player, location);
            return;
        }

        furnaceManager.openFurnaceGUI(player, location);
    }

    private String getCraftEngineFurnitureId(Block block, Location location) {
        ensureHooksInitialized();
        if (ceHook == null || !ceHook.isAvailable()) return null;
        if (block.getType() != Material.BARRIER) return null;

        Location center = location.clone().add(0.5, 0.5, 0.5);
        Collection<Entity> nearby = location.getWorld().getNearbyEntities(
                center, FURNITURE_SEARCH_RADIUS, FURNITURE_SEARCH_RADIUS, FURNITURE_SEARCH_RADIUS,
                entity -> entity instanceof ItemDisplay
        );

        for (Entity entity : nearby) {
            String customName = entity.getCustomName();
            if (customName != null && customName.startsWith("ce:")) {
                return customName.substring(3);
            }

            if (entity instanceof ItemDisplay itemDisplay) {
                ItemStack displayItem = itemDisplay.getItemStack();
                if (displayItem != null) {
                    String ceId = ceHook.getItemId(displayItem);
                    if (ceId != null) return ceId;
                }
            }
        }

        return null;
    }

    private Optional<FurnaceType> getFurnaceTypeForFurniture(String furnitureId) {
        if (furnitureId == null || furnitureId.isEmpty()) return Optional.empty();

        return configManager.getFurnaceConfig().getAllTypes().stream()
                .filter(type -> furnitureId.equalsIgnoreCase(type.getItemId()))
                .findFirst();
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