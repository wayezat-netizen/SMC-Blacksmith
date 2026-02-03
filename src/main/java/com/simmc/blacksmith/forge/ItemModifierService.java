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

public class ItemModifierService {

    private static final String MODIFIER_PREFIX = "smcblacksmith_";

    public ItemStack applyStarModifiers(ItemStack item, int stars, Map<Integer, StarModifier> modifiers, String forgerName) {
        if (item == null || item.getType().isAir()) return item;

        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;

        StarModifier modifier = modifiers.get(stars);

        // Apply display name suffix
        if (modifier != null && modifier.getDisplaySuffix() != null) {
            String currentName = meta.hasDisplayName() ? meta.getDisplayName() : "";
            meta.setDisplayName(currentName + " " + modifier.getDisplaySuffix());
        }

        // Build lore
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Add star rating
        lore.add("");
        lore.add(formatStars(stars));

        // Add modifier description
        if (modifier != null && modifier.getLoreLine() != null) {
            lore.add(modifier.getLoreLine());
        }

        // Add forger name
        if (forgerName != null) {
            lore.add("");
            lore.add("§7Forged by §e" + forgerName);
        }

        meta.setLore(lore);

        // Apply attribute modifiers
        if (modifier != null) {
            applyAttributeModifiers(meta, modifier);
        }

        result.setItemMeta(meta);
        return result;
    }

    private void applyAttributeModifiers(ItemMeta meta, StarModifier modifier) {
        Map<String, Double> mods = modifier.getAttributeModifiers();

        for (Map.Entry<String, Double> entry : mods.entrySet()) {
            String attrName = entry.getKey().toUpperCase();
            double value = entry.getValue();

            try {
                Attribute attribute = Attribute.valueOf("GENERIC_" + attrName);
                UUID uuid = UUID.nameUUIDFromBytes((MODIFIER_PREFIX + attrName).getBytes());

                AttributeModifier attrMod = new AttributeModifier(
                        uuid,
                        MODIFIER_PREFIX + attrName.toLowerCase(),
                        value,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );

                meta.addAttributeModifier(attribute, attrMod);
            } catch (IllegalArgumentException e) {
                // Unknown attribute, skip
            }
        }
    }

    private String formatStars(int stars) {
        StringBuilder sb = new StringBuilder("§7Quality: ");
        for (int i = 0; i < 5; i++) {
            sb.append(i < stars ? "§6★" : "§7☆");
        }
        return sb.toString();
    }
}