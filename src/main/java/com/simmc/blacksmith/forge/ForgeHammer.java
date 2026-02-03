package com.simmc.blacksmith.forge;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles hammer item requirements and durability for forging.
 */
public class ForgeHammer {

    private final String hammerType;
    private final String hammerId;
    private final int durabilityPerStrike;
    private final double bonusAccuracy;

    /**
     * Default hammer (any item works).
     */
    public static final ForgeHammer DEFAULT = new ForgeHammer("minecraft", "IRON_AXE", 0, 0.0);

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

        // Check if it matches the required hammer
        if (registry != null && registry.matches(mainHand, hammerType, hammerId)) {
            return true;
        }

        // Fallback: check material for minecraft items
        if ("minecraft".equalsIgnoreCase(hammerType)) {
            Material required = Material.matchMaterial(hammerId);
            return required != null && mainHand.getType() == required;
        }

        return false;
    }

    /**
     * Consumes durability from the hammer.
     * Returns false if hammer breaks.
     */
    public boolean consumeDurability(Player player) {
        if (durabilityPerStrike <= 0) return true;

        ItemStack hammer = player.getInventory().getItemInMainHand();
        if (hammer == null || hammer.getType().isAir()) return false;

        // Check if item has durability
        if (!(hammer.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            return true; // No durability to consume
        }

        short maxDurability = hammer.getType().getMaxDurability();
        if (maxDurability <= 0) return true;

        int currentDamage = damageable.getDamage();
        int newDamage = currentDamage + durabilityPerStrike;

        if (newDamage >= maxDurability) {
            // Hammer breaks
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return false;
        }

        damageable.setDamage(newDamage);
        hammer.setItemMeta((ItemMeta) damageable);
        return true;
    }

    /**
     * Creates a default forge hammer item.
     */
    public static ItemStack createDefaultHammer() {
        ItemStack hammer = new ItemStack(Material.IRON_AXE);
        ItemMeta meta = hammer.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§lForge Hammer");
            List<String> lore = new ArrayList<>();
            lore.add("§7A sturdy hammer for forging.");
            lore.add("");
            lore.add("§eHold and left-click to strike!");
            meta.setLore(lore);
            hammer.setItemMeta(meta);
        }
        return hammer;
    }

    /**
     * Creates a master forge hammer with bonus accuracy.
     */
    public static ItemStack createMasterHammer() {
        ItemStack hammer = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = hammer.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l✦ Master Forge Hammer ✦");
            List<String> lore = new ArrayList<>();
            lore.add("§5§oA legendary tool of the masters.");
            lore.add("");
            lore.add("§a+5% §7Accuracy Bonus");
            lore.add("");
            lore.add("§eHold and left-click to strike!");
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