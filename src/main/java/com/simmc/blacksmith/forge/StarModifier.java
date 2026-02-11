package com.simmc.blacksmith.forge;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Modifiers applied to forged items based on star rating.
 */
public class StarModifier {

    private final int star;
    private final Map<String, Double> attributeModifiers;
    private final String displaySuffix;
    private final String loreLine;

    public StarModifier(int star, Map<String, Double> attributeModifiers,
                        String displaySuffix, String loreLine) {
        this.star = star;
        this.attributeModifiers = attributeModifiers != null
                ? new HashMap<>(attributeModifiers)
                : new HashMap<>();
        this.displaySuffix = displaySuffix;
        this.loreLine = loreLine;
    }

    public int getStar() { return star; }
    public String getDisplaySuffix() { return displaySuffix; }
    public String getLoreLine() { return loreLine; }

    public Map<String, Double> getAttributeModifiers() {
        return Collections.unmodifiableMap(attributeModifiers);
    }

    public double getModifier(String attribute) {
        return attributeModifiers.getOrDefault(attribute.toLowerCase(), 0.0);
    }

    public boolean hasModifier(String attribute) {
        return attributeModifiers.containsKey(attribute.toLowerCase());
    }

    public boolean hasDisplaySuffix() {
        return displaySuffix != null && !displaySuffix.isEmpty();
    }

    public boolean hasLoreLine() {
        return loreLine != null && !loreLine.isEmpty();
    }

    public boolean hasAttributeModifiers() {
        return !attributeModifiers.isEmpty();
    }
}