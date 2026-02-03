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

import java.util.Collection;

public class BlockInteractListener implements Listener {

    private final FurnaceManager furnaceManager;
    private final ConfigManager configManager;

    // Configurable constants
    private static final String HEAT_TOOL_ID = "heat_tool";
    private static final int HEAT_TOOL_BOOST = 50;
    private static final double FURNITURE_SEARCH_RADIUS = 1.0;

    // Cached references (lazy loaded)
    private SMCCoreHook smcHookCache;
    private CraftEngineHook ceHookCache;
    private boolean hooksInitialized;

    public BlockInteractListener(FurnaceManager furnaceManager, ConfigManager configManager) {
        this.furnaceManager = furnaceManager;
        this.configManager = configManager;
        this.hooksInitialized = false;
    }

    private void ensureHooksInitialized() {
        if (hooksInitialized) return;
        hooksInitialized = true;

        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        if (plugin != null) {
            smcHookCache = plugin.getSmcCoreHook();
            ceHookCache = plugin.getCraftEngineHook();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Fast rejection checks
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        Location location = block.getLocation();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check existing furnace first (most common case)
        if (furnaceManager.isFurnace(location)) {
            handleExistingFurnace(player, location, itemInHand, event);
            return;
        }

        // Check for CraftEngine furniture
        handlePotentialFurniture(player, block, location, itemInHand, event);
    }

    private void handleExistingFurnace(Player player, Location location,
                                       ItemStack itemInHand, PlayerInteractEvent event) {
        // Check for heat tool usage
        if (isHeatTool(itemInHand)) {
            event.setCancelled(true);
            applyHeatTool(player, location);
            return;
        }

        // Open furnace GUI
        event.setCancelled(true);
        furnaceManager.openFurnaceGUI(player, location);
    }

    private void handlePotentialFurniture(Player player, Block block, Location location,
                                          ItemStack itemInHand, PlayerInteractEvent event) {
        String furnitureId = getCraftEngineFurnitureId(block, location);
        if (furnitureId == null) return;

        FurnaceType type = getFurnaceTypeForFurniture(furnitureId);
        if (type == null) return;

        event.setCancelled(true);

        // Create furnace
        FurnaceInstance instance = furnaceManager.createFurnace(type.getId(), location);
        if (instance == null) return;

        // Check for heat tool on new furnace
        if (isHeatTool(itemInHand)) {
            applyHeatTool(player, location);
            return;
        }

        // Open GUI
        furnaceManager.openFurnaceGUI(player, location);
    }

    private boolean isHeatTool(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        ensureHooksInitialized();

        if (smcHookCache == null || !smcHookCache.isAvailable()) return false;

        String itemId = smcHookCache.getItemId(item);
        return HEAT_TOOL_ID.equalsIgnoreCase(itemId);
    }

    private void applyHeatTool(Player player, Location location) {
        FurnaceInstance furnace = furnaceManager.getFurnace(location);
        if (furnace == null) return;

        int maxTemp = furnace.getType().getMaxTemperature();
        int currentTarget = furnace.getTargetTemperature();
        int newTarget = Math.min(currentTarget + HEAT_TOOL_BOOST, maxTemp);

        furnace.setTargetTemperature(newTarget);

        // Feedback
        String message = configManager.getMessageConfig().getHeatToolUsed();
        if (message != null && !message.isEmpty()) {
            player.sendMessage(message);
        } else {
            player.sendMessage("§6Temperature boosted to " + newTarget + "°C!");
        }

        player.playSound(location, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.5f);
    }

    private String getCraftEngineFurnitureId(Block block, Location location) {
        ensureHooksInitialized();

        if (ceHookCache == null || !ceHookCache.isAvailable()) return null;

        // CraftEngine furniture uses barrier blocks
        if (block.getType() != Material.BARRIER) return null;

        // Search for item display entities
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

            // Try to get ID from entity's item
            if (entity instanceof ItemDisplay itemDisplay) {
                ItemStack displayItem = itemDisplay.getItemStack();
                if (displayItem != null) {
                    String ceId = ceHookCache.getItemId(displayItem);
                    if (ceId != null) {
                        return ceId;
                    }
                }
            }
        }

        return null;
    }

    private FurnaceType getFurnaceTypeForFurniture(String furnitureId) {
        if (furnitureId == null || furnitureId.isEmpty()) return null;

        FurnaceConfig furnaceConfig = configManager.getFurnaceConfig();

        for (FurnaceType type : furnaceConfig.getFurnaceTypes().values()) {
            String typeItemId = type.getItemId();
            if (typeItemId != null && furnitureId.equalsIgnoreCase(typeItemId)) {
                return type;
            }
        }

        return null;
    }
}