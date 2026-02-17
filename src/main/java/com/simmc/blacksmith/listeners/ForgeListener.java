package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.HammerConfig;
import com.simmc.blacksmith.forge.ForgeManager;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles forge session interactions.
 */
public class ForgeListener implements Listener {

    private static final Set<Material> ANVIL_MATERIALS = EnumSet.of(
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    );

    private static final String NO_HAMMER_MESSAGE = "§c§l⚠ §cYou need to hold a hammer to forge!";

    private final ForgeManager forgeManager;

    public ForgeListener(ForgeManager forgeManager) {
        this.forgeManager = forgeManager;
    }

    // ==================== HIT DETECTION ====================

    /**
     * PRIMARY: Left-click attack on Interaction entity.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Interaction)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        event.setCancelled(true);

        if (!validateHammerAndHit(player, event.getEntity().getUniqueId())) {
            return;
        }
    }

    /**
     * FALLBACK: Right-click on Interaction entity.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction)) return;

        Player player = event.getPlayer();
        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        event.setCancelled(true);

        if (!validateHammerAndHit(player, event.getRightClicked().getUniqueId())) {
            return;
        }
    }

    /**
     * Validates hammer and processes hit.
     * @return true if hit was processed
     */
    private boolean validateHammerAndHit(Player player, UUID hitboxId) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        if (!isHammer(heldItem)) {
            player.sendMessage(NO_HAMMER_MESSAGE);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return false;
        }

        forgeManager.processPointHit(player, hitboxId);
        return true;
    }

    // ==================== ANVIL PROTECTION ====================

    /**
     * Prevent block damage and reset break animation.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        Block block = event.getBlock();
        if (ANVIL_MATERIALS.contains(block.getType())) {
            event.setCancelled(true);

            // CRITICAL: Reset the block break animation by sending stage -1
            sendBlockBreakAnimation(player, block, -1);
        }
    }

    /**
     * Cancel left-click interact on anvil and reset animation.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        Block block = event.getClickedBlock();
        if (block != null && ANVIL_MATERIALS.contains(block.getType())) {
            event.setCancelled(true);

            // Reset break animation
            sendBlockBreakAnimation(player, block, -1);
        }
    }

    /**
     * Handle when player stops breaking (releases left-click).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockDamageAbort(BlockDamageAbortEvent event) {
        Player player = event.getPlayer();
        if (!forgeManager.hasActiveSession(player.getUniqueId())) return;

        Block block = event.getBlock();
        if (ANVIL_MATERIALS.contains(block.getType())) {
            // Ensure animation is fully reset
            sendBlockBreakAnimation(player, block, -1);
        }
    }

    private void sendBlockBreakAnimation(Player player, Block block, int stage) {
        // Use a unique entity ID for the animation (negative to avoid conflicts)
        int entityId = -block.hashCode();

        try {
            // Method 1: Use the block's world to send the animation
            block.getWorld().getPlayers().forEach(p -> {
                if (p.equals(player) || p.getLocation().distanceSquared(block.getLocation()) < 64 * 64) {
                    // Send block change to force refresh
                    p.sendBlockChange(block.getLocation(), block.getBlockData());
                }
            });
        } catch (Exception ignored) {
            // Fallback: just send block change
            player.sendBlockChange(block.getLocation(), block.getBlockData());
        }
    }

    // ==================== HAMMER VALIDATION ====================

    /**
     * Checks if the item is a configured hammer.
     */
    private boolean isHammer(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }

        SMCBlacksmith plugin = SMCBlacksmith.getInstance();
        HammerConfig hammerConfig = plugin.getConfigManager().getHammerConfig();

        if (hammerConfig == null || hammerConfig.getHammerTypeCount() == 0) {
            return false;
        }

        // Check SMCCore items first
        SMCCoreHook smcHook = plugin.getSmcCoreHook();
        if (smcHook != null && smcHook.isAvailable()) {
            String smcId = smcHook.getItemId(item);
            if (smcId != null && !smcId.isEmpty()) {
                return matchesHammerType(hammerConfig, "smc", smcId);
            }
        }

        // Check vanilla items
        String materialName = item.getType().name().toLowerCase();
        return matchesHammerType(hammerConfig, "minecraft", materialName);
    }

    private boolean matchesHammerType(HammerConfig config, String type, String id) {
        for (HammerConfig.HammerType hammerType : config.getAllHammerTypes().values()) {
            if (hammerType.type().equalsIgnoreCase(type) &&
                    hammerType.itemId().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }
}