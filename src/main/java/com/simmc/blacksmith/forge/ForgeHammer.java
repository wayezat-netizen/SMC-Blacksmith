package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Handles hammer item requirements and durability for forging.
 */
public class ForgeHammer {

    public static final ForgeHammer DEFAULT = new ForgeHammer("minecraft", "IRON_AXE", 0, 0.0);

    private final String hammerType;
    private final String hammerId;
    private final int durabilityPerStrike;
    private final double bonusAccuracy;

    public ForgeHammer(String type, String id, int durabilityPerStrike, double bonusAccuracy) {
        this.hammerType = type;
        this.hammerId = id;
        this.durabilityPerStrike = durabilityPerStrike;
        this.bonusAccuracy = bonusAccuracy;
    }

    /**
     * Checks if player is holding a valid hammer.
     */
    public boolean isHoldingHammer(Player player, ItemProviderRegistry registry) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType().isAir()) {
            return false;
        }

        // Check via registry first
        if (registry != null && registry.matches(mainHand, hammerType, hammerId)) {
            return true;
        }

        // Fallback for vanilla items
        if ("minecraft".equalsIgnoreCase(hammerType)) {
            Material required = Material.matchMaterial(hammerId);
            return required != null && mainHand.getType() == required;
        }

        return false;
    }

    /**
     * Consumes durability from the hammer.
     * @return false if hammer breaks
     */
    public boolean consumeDurability(Player player) {
        if (durabilityPerStrike <= 0) return true;

        ItemStack hammer = player.getInventory().getItemInMainHand();
        if (hammer == null || hammer.getType().isAir()) return false;

        ItemMeta meta = hammer.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return true; // No durability to consume
        }

        short maxDurability = hammer.getType().getMaxDurability();
        if (maxDurability <= 0) return true;

        int newDamage = damageable.getDamage() + durabilityPerStrike;

        if (newDamage >= maxDurability) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return false;
        }

        damageable.setDamage(newDamage);
        hammer.setItemMeta(meta);
        return true;
    }

    /**
     * Creates a default forge hammer item.
     */
    public static ItemStack createDefaultHammer() {
        return createHammer(Material.IRON_AXE, "§6§lForge Hammer",
                List.of("§7A sturdy hammer for forging.", "", "§eHold and left-click to strike!"));
    }

    /**
     * Creates a master forge hammer with bonus accuracy.
     */
    public static ItemStack createMasterHammer() {
        return createHammer(Material.NETHERITE_AXE, "§6§l✦ Master Forge Hammer ✦",
                List.of("§5§oA legendary tool of the masters.", "", "§a+5% §7Accuracy Bonus", "", "§eHold and left-click to strike!"));
    }

    private static ItemStack createHammer(Material material, String name, List<String> lore) {
        ItemStack hammer = new ItemStack(material);
        ItemMeta meta = hammer.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            hammer.setItemMeta(meta);
        }
        return hammer;
    }

    // Getters
    public String getHammerType() { return hammerType; }
    public String getHammerId() { return hammerId; }
    public int getDurabilityPerStrike() { return durabilityPerStrike; }
    public double getBonusAccuracy() { return bonusAccuracy; }
}