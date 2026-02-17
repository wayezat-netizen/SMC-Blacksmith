package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.config.FurnaceConfig;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.furnace.FurnaceType;
import com.simmc.blacksmith.integration.SMCCoreHook;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Handles furnace interactions - opening GUI and using bellows.
 */
public class FurnaceInteractionListener implements Listener {

    private static final Set<Material> FURNACE_BLOCKS = Set.of(
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER
    );

    private final FurnaceManager furnaceManager;
    private final ConfigManager configManager;

    // Bellows configuration: Maps "type:id" to heat boost
    private final Map<String, Integer> bellowsHeatBoost = new HashMap<>();

    public FurnaceInteractionListener(FurnaceManager furnaceManager, ConfigManager configManager) {
        this.furnaceManager = furnaceManager;
        this.configManager = configManager;

        initializeBellowsTypes();
    }

    /**
     * Initialize bellows types.
     */
    private void initializeBellowsTypes() {
        // SMCCore bellows
        bellowsHeatBoost.put("smc:simple_bellows", 10);
        bellowsHeatBoost.put("smc:advanced_bellows", 50);
        bellowsHeatBoost.put("smc:blacksmith_bellows", 25);
        bellowsHeatBoost.put("smc:bellow", 15);

        // Vanilla item fallbacks for testing
        bellowsHeatBoost.put("minecraft:leather", 5);
        bellowsHeatBoost.put("minecraft:paper", 3);
    }


    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Only handle furnace blocks
        if (!FURNACE_BLOCKS.contains(clickedBlock.getType())) {
            return;
        }

        Player player = event.getPlayer();
        Location blockLocation = clickedBlock.getLocation();
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // CRITICAL: Always cancel to prevent vanilla furnace GUI
        event.setCancelled(true);

        // Check if holding bellows
        Integer heatBoost = getBellowsHeatBoost(heldItem);
        if (heatBoost != null && heatBoost > 0) {
            // BELLOWS: Increase temperature
            handleBellowsUse(player, blockLocation, heldItem, heatBoost);
            return;
        }

        // NOT holding bellows - open furnace GUI
        handleFurnaceOpen(player, blockLocation);
    }

    private void handleBellowsUse(Player player, Location blockLocation, ItemStack bellowsItem, int heatBoost) {
        // Get or create furnace
        FurnaceInstance furnace = getOrCreateFurnace(blockLocation);

        if (furnace == null) {
            player.sendMessage("§cCould not access furnace!");
            return;
        }

        // Check if furnace has fuel or temperature
        if (!furnace.isBurning() && furnace.getCurrentTemperature() <= 0) {
            player.sendMessage(configManager.getMessageConfig().getBellowsNoFuel());
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // Apply heat boost
        boolean success = furnace.applyBellows(heatBoost);

        if (success) {
            // Consume bellows durability
            consumeBellowsDurability(player, bellowsItem);

            // Play effects
            player.playSound(blockLocation, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.8f);
            player.playSound(blockLocation, Sound.BLOCK_FIRE_AMBIENT, 0.7f, 1.2f);

            // Show feedback
            int newTemp = furnace.getCurrentTemperature();
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§6🔥 Temperature: §e" + newTemp + "°C §7(+" + heatBoost + ")"));
        }
    }

    /**
     * Handle furnace GUI opening.
     */
    private void handleFurnaceOpen(Player player, Location blockLocation) {
        // Get or create furnace automatically
        FurnaceInstance furnace = getOrCreateFurnace(blockLocation);

        if (furnace == null) {
            player.sendMessage("§cCould not create furnace!");
            return;
        }

        // Open custom furnace GUI
        furnaceManager.openFurnaceGUI(player, blockLocation);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    /**
     * Gets existing furnace or creates new one automatically.
     */
    private FurnaceInstance getOrCreateFurnace(Location blockLocation) {
        // Check if furnace already exists
        FurnaceInstance furnace = furnaceManager.getFurnace(blockLocation);
        if (furnace != null) {
            return furnace;
        }

        // Auto-create furnace based on block type
        Block block = blockLocation.getBlock();
        Material blockType = block.getType();

        String furnaceTypeId;
        if (blockType == Material.BLAST_FURNACE) {
            // Try advanced_furnace first, fallback to basic
            furnaceTypeId = furnaceTypeExists("advanced_furnace") ? "advanced_furnace" : "basic_furnace";
        } else {
            furnaceTypeId = "basic_furnace";
        }

        // Create the furnace automatically
        return furnaceManager.createFurnace(furnaceTypeId, blockLocation);
    }

    /**
     * Check if a furnace type exists in config.
     */
    private boolean furnaceTypeExists(String typeId) {
        FurnaceConfig furnaceConfig = configManager.getFurnaceConfig();
        if (furnaceConfig == null) return false;
        return furnaceConfig.getFurnaceType(typeId).isPresent();
    }

    /**
     * Gets bellows heat boost for an item.
     * Returns null if item is not a bellows.
     */
    private Integer getBellowsHeatBoost(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;

        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        SMCCoreHook smcHook = plugin.getSmcCoreHook();

        // Check SMCCore items first
        if (smcHook != null && smcHook.isAvailable()) {
            String smcId = smcHook.getItemId(item);
            if (smcId != null && !smcId.isEmpty()) {
                String key = "smc:" + smcId.toLowerCase();
                Integer boost = bellowsHeatBoost.get(key);
                if (boost != null) return boost;
            }
        }

        // Check vanilla items
        String materialKey = "minecraft:" + item.getType().name().toLowerCase();
        return bellowsHeatBoost.get(materialKey);
    }

    /**
     * Consumes bellows durability.
     */
    private void consumeBellowsDurability(Player player, ItemStack bellowsItem) {
        ItemMeta meta = bellowsItem.getItemMeta();

        if (meta instanceof Damageable damageable) {
            int maxDurability = bellowsItem.getType().getMaxDurability();
            if (maxDurability > 0) {
                int newDamage = damageable.getDamage() + 1;

                if (newDamage >= maxDurability) {
                    player.getInventory().setItemInMainHand(null);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                    player.sendMessage("§cYour bellows broke!");
                } else {
                    damageable.setDamage(newDamage);
                    bellowsItem.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Register additional bellows types.
     */
    public void registerBellows(String key, int heatBoost) {
        bellowsHeatBoost.put(key.toLowerCase(), heatBoost);
    }
}