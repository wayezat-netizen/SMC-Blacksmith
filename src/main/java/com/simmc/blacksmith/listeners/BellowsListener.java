package com.simmc.blacksmith.listeners;

import com.simmc.blacksmith.SMCBlacksmith;
import com.simmc.blacksmith.config.BellowsConfig;
import com.simmc.blacksmith.config.ConfigManager;
import com.simmc.blacksmith.furnace.FurnaceInstance;
import com.simmc.blacksmith.furnace.FurnaceManager;
import com.simmc.blacksmith.integration.SMCCoreHook;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles bellows usage - right-click furnace with bellows to increase temperature.
 *
 * CRITICAL: Bellows REQUIRE fuel to be burning!
 * - Bellows fan flames, they don't create fire
 * - Without fuel, bellows do nothing
 * - With fuel, bellows increase temperature significantly
 *
 * FIXED: Only consume 1 bellows from stack when breaking
 */
public class BellowsListener implements Listener {

    private static final String DURABILITY_LORE_PREFIX = "§7Durability: §f";
    private static final long DEFAULT_COOLDOWN_MS = 400; // 8 ticks default

    private final SMCBlacksmith plugin;
    private final FurnaceManager furnaceManager;
    private final ConfigManager configManager;
    private final Map<UUID, Long> cooldowns;
    private final Map<UUID, Long> lastInteractTime;
    private final NamespacedKey durabilityKey;

    public BellowsListener(SMCBlacksmith plugin, FurnaceManager furnaceManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.furnaceManager = furnaceManager;
        this.configManager = configManager;
        this.cooldowns = new ConcurrentHashMap<>();
        this.lastInteractTime = new ConcurrentHashMap<>();
        this.durabilityKey = new NamespacedKey(plugin, "bellows_durability");
    }

    /**
     * HIGHEST priority + ignoreCancelled=false to intercept BEFORE other listeners.
     * This ensures bellows are detected before the furnace GUI opens.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isValidBellowsInteraction(event)) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Prevent double-fire from Bukkit calling event twice
        long now = System.currentTimeMillis();
        Long lastInteract = lastInteractTime.get(playerId);
        if (lastInteract != null && (now - lastInteract) < 50) {
            return;
        }
        lastInteractTime.put(playerId, now);

        Block clickedBlock = event.getClickedBlock();
        Location furnaceLocation = clickedBlock.getLocation();

        // Check if it's a custom furnace
        if (!furnaceManager.isFurnace(furnaceLocation)) return;

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        Optional<BellowsConfig.BellowsType> bellowsType = getBellowsType(heldItem);

        if (bellowsType.isEmpty()) {
            return;
        }

        // CRITICAL: Cancel event IMMEDIATELY to prevent GUI opening
        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        // Check cooldown BEFORE applying effect
        if (isOnCooldown(player)) {
            return;
        }

        // Set cooldown FIRST to prevent spam
        setCooldown(player, bellowsType.get());

        applyBellowsEffect(player, furnaceLocation, heldItem, bellowsType.get());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        cooldowns.remove(playerId);
        lastInteractTime.remove(playerId);
    }

    // ==================== VALIDATION ====================

    private boolean isValidBellowsInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        if (event.getHand() != EquipmentSlot.HAND) return false;
        if (event.getClickedBlock() == null) return false;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        return item != null && !item.getType().isAir();
    }

    private boolean isOnCooldown(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long cooldownEnd = cooldowns.get(playerId);
        if (cooldownEnd != null && now < cooldownEnd) {
            return true;
        }

        return false;
    }

    private void setCooldown(Player player, BellowsConfig.BellowsType bellowsType) {
        long now = System.currentTimeMillis();

        long cooldownTicks = bellowsType.cooldownTicks();
        if (cooldownTicks <= 0) {
            cooldownTicks = 8;
        }
        long cooldownMs = cooldownTicks * 50L;

        cooldowns.put(player.getUniqueId(), now + cooldownMs);
    }

    // ==================== BELLOWS DETECTION ====================

    private Optional<BellowsConfig.BellowsType> getBellowsType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return Optional.empty();
        }

        BellowsConfig bellowsConfig = configManager.getBellowsConfig();
        if (bellowsConfig == null || bellowsConfig.getBellowsTypeCount() == 0) {
            return Optional.empty();
        }

        // Check SMCCore items first
        SMCCoreHook smcHook = plugin.getSmcCoreHook();
        if (smcHook != null && smcHook.isAvailable()) {
            String smcId = smcHook.getItemId(item);
            if (smcId != null && !smcId.isEmpty()) {
                return findBellowsBySmcId(bellowsConfig, smcId);
            }
        }

        // Check vanilla items
        return findBellowsByMaterial(bellowsConfig, item.getType().name());
    }

    private Optional<BellowsConfig.BellowsType> findBellowsBySmcId(BellowsConfig config, String smcId) {
        return config.getAllTypes().stream()
                .filter(type -> type.isSMCCore() && type.itemId().equalsIgnoreCase(smcId))
                .findFirst();
    }

    private Optional<BellowsConfig.BellowsType> findBellowsByMaterial(BellowsConfig config, String materialName) {
        return config.getAllTypes().stream()
                .filter(type -> type.isVanilla() && type.itemId().equalsIgnoreCase(materialName))
                .findFirst();
    }

    // ==================== BELLOWS EFFECT ====================

    private void applyBellowsEffect(Player player, Location furnaceLocation,
                                    ItemStack bellows, BellowsConfig.BellowsType type) {

        FurnaceInstance furnace = furnaceManager.getFurnaceInstance(furnaceLocation);
        if (furnace == null) {
            player.sendMessage("§cNo furnace found!");
            return;
        }

        // STRICT CHECK: Must have fuel actively burning
        if (!furnace.isBurning()) {
            String noFuelMsg = configManager.getMessageConfig().getBellowsNoFuel();
            player.sendMessage(noFuelMsg != null && !noFuelMsg.isEmpty()
                    ? noFuelMsg
                    : "§cAdd fuel to the furnace first!");
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
            return;
        }

        // Apply bellows
        boolean success = furnace.applyBellows(type.heatPerBlow());

        if (!success) {
            player.sendMessage("§cThe furnace needs burning fuel!");
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.5f);
            return;
        }

        // Success
        boolean bellowsBroke = damageBellows(player, bellows, type);
        playEffects(player, furnaceLocation, type, furnace.getCurrentTemperature());

        if (bellowsBroke) {
            String brokeMsg = configManager.getMessageConfig().getBellowsBroke();
            player.sendMessage(brokeMsg != null && !brokeMsg.isEmpty() ? brokeMsg : "§cYour bellows broke!");
        }
    }

    private void playEffects(Player player, Location location, BellowsConfig.BellowsType type, int currentTemp) {
        // Sound
        try {
            Sound sound = Sound.valueOf(type.sound().toUpperCase());
            player.playSound(location, sound, 1.0f, 0.8f);
        } catch (IllegalArgumentException e) {
            player.playSound(location, Sound.ENTITY_HORSE_BREATHE, 1.0f, 0.8f);
        }

        // Particles - scale with temperature
        Location particleLoc = location.clone().add(0.5, 1.0, 0.5);

        int smokeCount = Math.min(15, 5 + (currentTemp / 100));
        player.getWorld().spawnParticle(Particle.SMOKE, particleLoc, smokeCount, 0.2, 0.3, 0.2, 0.02);

        Location flameLoc = location.clone().add(0.5, 0.8, 0.5);
        int flameCount = Math.min(10, 3 + (currentTemp / 150));
        player.getWorld().spawnParticle(Particle.FLAME, flameLoc, flameCount, 0.15, 0.1, 0.15, 0.02);

        if (currentTemp > 300) {
            player.getWorld().spawnParticle(Particle.LAVA, flameLoc, 2, 0.1, 0.1, 0.1, 0);
        }

        if (currentTemp > 600) {
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, flameLoc, 3, 0.1, 0.2, 0.1, 0.01);
        }
    }

    // ==================== DURABILITY ====================

    /**
     * Damages the bellows by 1 durability.
     * When durability reaches 0, only removes 1 item from the stack.
     *
     * @return true if the bellows broke
     */
    private boolean damageBellows(Player player, ItemStack bellows, BellowsConfig.BellowsType type) {
        int stackSize = bellows.getAmount();

        ItemMeta meta = bellows.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int maxDurability = type.maxDurability();
        int currentDurability = pdc.getOrDefault(durabilityKey, PersistentDataType.INTEGER, maxDurability);

        currentDurability--;

        if (currentDurability <= 0) {
            // Bellows broke - handle stack properly
            if (stackSize > 1) {
                // More than 1 in stack - reduce stack by 1 and reset durability on remaining
                bellows.setAmount(stackSize - 1);

                // Reset durability for the remaining items
                pdc.set(durabilityKey, PersistentDataType.INTEGER, maxDurability);
                updateDurabilityLore(meta, maxDurability, maxDurability);
                bellows.setItemMeta(meta);
            } else {
                // Only 1 item - remove it entirely
                player.getInventory().setItemInMainHand(null);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return true;
        }

        // Update durability
        pdc.set(durabilityKey, PersistentDataType.INTEGER, currentDurability);
        updateDurabilityLore(meta, currentDurability, maxDurability);
        bellows.setItemMeta(meta);

        return false;
    }

    private void updateDurabilityLore(ItemMeta meta, int current, int max) {
        List<String> lore = meta.getLore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        double percent = (double) current / max;
        String color;
        if (percent > 0.5) {
            color = "§a";
        } else if (percent > 0.25) {
            color = "§e";
        } else {
            color = "§c";
        }

        String durabilityLine = "§7Durability: " + color + current + "§7/" + max;

        boolean found = false;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("Durability:")) {
                lore.set(i, durabilityLine);
                found = true;
                break;
            }
        }

        if (!found) {
            lore.add(durabilityLine);
        }

        meta.setLore(lore);
    }
}