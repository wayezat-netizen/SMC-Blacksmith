package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.FurnaceConfig;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.furnace.FurnaceType;
import com.simmc.blacksmith.integration.CraftEngineHook;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Handles block/furniture interactions for the furnace system.
 * Detects CraftEngine furniture clicks and SMCCore heat tool usage.
 */
public class BlockInteractListener implements Listener {

    private final FurnaceManager furnaceManager;
    private final ConfigManager configManager;

    // SMCCore heat tool item ID (configurable)
    private static final String HEAT_TOOL_ID = "heat_tool";
    private static final int HEAT_TOOL_BOOST = 50;

    public BlockInteractListener(FurnaceManager furnaceManager, ConfigManager configManager) {
        this.furnaceManager = furnaceManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle right-click
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Only handle main hand to prevent double firing
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) {
            return;
        }

        Location location = block.getLocation();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if this location is already a registered furnace
        if (furnaceManager.isFurnace(location)) {
            // Check for SMCCore heat tool usage
            if (isHeatTool(itemInHand)) {
                handleHeatToolUse(player, location, event);
                return;
            }

            // Open existing furnace GUI
            event.setCancelled(true);
            furnaceManager.openFurnaceGUI(player, location);
            return;
        }

        // Check if this is a CraftEngine furniture that should become a furnace
        String furnitureId = getCraftEngineFurnitureId(block, location);
        if (furnitureId != null) {
            FurnaceType type = getFurnaceTypeForFurniture(furnitureId);
            if (type != null) {
                // Check for heat tool on new furnace
                if (isHeatTool(itemInHand)) {
                    // Create furnace first, then apply heat
                    FurnaceInstance instance = furnaceManager.createFurnace(type.getId(), location);
                    if (instance != null) {
                        handleHeatToolUse(player, location, event);
                    }
                    return;
                }

                // Create and open furnace
                event.setCancelled(true);
                FurnaceInstance instance = furnaceManager.createFurnace(type.getId(), location);
                if (instance != null) {
                    furnaceManager.openFurnaceGUI(player, location);
                }
            }
        }
    }

    /**
     * Checks if the item is an SMCCore heat tool.
     */
    private boolean isHeatTool(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        if (plugin == null) {
            return false;
        }

        SMCCoreHook smcHook = plugin.getSmcCoreHook();
        if (smcHook == null || !smcHook.isAvailable()) {
            return false;
        }

        String itemId = smcHook.getItemId(item);
        return HEAT_TOOL_ID.equalsIgnoreCase(itemId);
    }

    /**
     * Handles using the SMCCore heat tool on a furnace.
     */
    private void handleHeatToolUse(Player player, Location location, PlayerInteractEvent event) {
        event.setCancelled(true);

        FurnaceInstance furnace = furnaceManager.getFurnace(location);
        if (furnace == null) {
            return;
        }

        // Apply temperature boost beyond fuel limit
        int maxTemp = furnace.getType().getMaxTemperature();
        int currentTarget = furnace.getTargetTemperature();
        int newTarget = Math.min(currentTarget + HEAT_TOOL_BOOST, maxTemp);

        furnace.setTargetTemperature(newTarget);

        // Send feedback
        String message = configManager.getMessageConfig().getHeatToolUsed();
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        } else {
            player.sendMessage("§6Temperature boosted to " + newTarget + "°C!");
        }

        // Play sound effect
        player.playSound(location, org.bukkit.Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.5f);
    }

    /**
     * Gets the CraftEngine furniture ID at the given location.
     */
    private String getCraftEngineFurnitureId(Block block, Location location) {
        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        if (plugin == null) {
            return null;
        }

        CraftEngineHook ceHook = plugin.getCraftEngineHook();
        if (ceHook == null || !ceHook.isAvailable()) {
            return null;
        }

        // CraftEngine furniture detection
        // This needs to be implemented based on CraftEngine's actual API
        // For now, we use a placeholder that checks block metadata or nearby entities
        return getCraftEngineFurnitureIdFromBlock(block, ceHook);
    }

    /**
     * Gets furniture ID from block using CraftEngine API.
     * This method should be updated based on actual CraftEngine API.
     */
    private String getCraftEngineFurnitureIdFromBlock(Block block, CraftEngineHook ceHook) {
        // TODO: Implement based on CraftEngine's furniture API
        // CraftEngine typically uses entities (armor stands/item displays) for furniture
        // We need to check for nearby entities with custom tags

        // Placeholder implementation - check for barrier blocks (common for CE furniture)
        if (block.getType() == org.bukkit.Material.BARRIER) {
            // Look for nearby item display entities that might be CE furniture
            for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(
                    block.getLocation().add(0.5, 0.5, 0.5), 1.0, 1.0, 1.0)) {
                if (entity instanceof org.bukkit.entity.ItemDisplay) {
                    // Check entity custom name or persistent data for CE furniture ID
                    // This is a simplified check - actual implementation depends on CE version
                    String customName = entity.getCustomName();
                    if (customName != null && customName.startsWith("ce:")) {
                        return customName.substring(3);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Gets the furnace type that corresponds to a CraftEngine furniture ID.
     */
    private FurnaceType getFurnaceTypeForFurniture(String furnitureId) {
        if (furnitureId == null || furnitureId.isEmpty()) {
            return null;
        }

        FurnaceConfig furnaceConfig = configManager.getFurnaceConfig();

        // Check all furnace types for matching item_id
        for (FurnaceType type : furnaceConfig.getFurnaceTypes().values()) {
            if (furnitureId.equalsIgnoreCase(type.getItemId())) {
                return type;
            }
        }

        return null;
    }
}