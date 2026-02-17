package com.simmc.blacksmith.forge;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for applying star-based modifications to forged items.
 */
public class ItemModifierService {

    private static final String MODIFIER_PREFIX = "smcblacksmith_";

    /**
     * Applies star modifiers to a forged item.
     */
    public ItemStack applyStarModifiers(ItemStack item, int stars,
                                        Map<Integer, StarModifier> modifiers,
                                        String forgerName, String forgerFormat) {
        if (item == null || item.getType().isAir()) return item;

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        StarModifier modifier = modifiers != null ? modifiers.get(stars) : null;

        applyDisplayName(meta, modifier);
        applyLore(meta, stars, modifier, forgerName, forgerFormat);
        applyAttributes(meta, modifier);

        result.setItemMeta(meta);
        return result;
    }

    private void applyDisplayName(ItemMeta meta, StarModifier modifier) {
        if (modifier == null || !modifier.hasDisplaySuffix()) return;

        String currentName = meta.hasDisplayName() ? meta.getDisplayName() : "";
        meta.setDisplayName(currentName + " " + modifier.getDisplaySuffix());
    }

    private void applyLore(ItemMeta meta, int stars, StarModifier modifier, String forgerName, String forgerFormat) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Star rating
        lore.add("");
        lore.add(formatStars(stars));

        // Modifier description
        if (modifier != null && modifier.hasLoreLine()) {
            lore.add(modifier.getLoreLine());
        }

        // Forger signature - use the pre-formatted line
        if (forgerFormat != null && !forgerFormat.isEmpty()) {
            lore.add("");
            lore.add(forgerFormat);
        } else if (forgerName != null && !forgerName.isEmpty()) {
            lore.add("");
            lore.add("§7Forged by §e" + forgerName);
        }

        meta.setLore(lore);
    }

    private void applyAttributes(ItemMeta meta, StarModifier modifier) {
        if (modifier == null || !modifier.hasAttributeModifiers()) return;

        for (Map.Entry<String, Double> entry : modifier.getAttributeModifiers().entrySet()) {
            String attrName = entry.getKey().toUpperCase();
            double value = entry.getValue();

            try {
                Attribute attribute = parseAttribute(attrName);
                if (attribute == null) continue;

                UUID uuid = UUID.nameUUIDFromBytes((MODIFIER_PREFIX + attrName).getBytes());

                AttributeModifier attrMod = new AttributeModifier(
                        uuid,
                        MODIFIER_PREFIX + attrName.toLowerCase(),
                        value,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );

                meta.addAttributeModifier(attribute, attrMod);
            } catch (Exception ignored) {
                // Skip invalid attributes
            }
        }
    }

    private Attribute parseAttribute(String name) {
        // Try with GENERIC_ prefix
        try {
            return Attribute.valueOf("GENERIC_" + name);
        } catch (IllegalArgumentException ignored) {}

        // Try direct match
        try {
            return Attribute.valueOf(name);
        } catch (IllegalArgumentException ignored) {}

        return null;
    }

    private String formatStars(int stars) {
        StringBuilder sb = new StringBuilder("§7Quality: ");
        for (int i = 0; i < 5; i++) {
            sb.append(i < stars ? "§6★" : "§7☆");
        }
        return sb.toString();
    }
}