package com.simmc.blacksmith.forge;

import java.util.HashMap;
import java.util.Map;

public class StarModifier {

    private final int star;
    private final Map<String, Double> attributeModifiers;
    private final String displaySuffix;
    private final String loreLine;

    public StarModifier(int star, Map<String, Double> attributeModifiers, String displaySuffix, String loreLine) {
        this.star = star;
        this.attributeModifiers = attributeModifiers != null ? attributeModifiers : new HashMap<>();
        this.displaySuffix = displaySuffix;
        this.loreLine = loreLine;
    }

    public int getStar() { return star; }
    public Map<String, Double> getAttributeModifiers() { return new HashMap<>(attributeModifiers); }
    public String getDisplaySuffix() { return displaySuffix; }
    public String getLoreLine() { return loreLine; }

    public double getModifier(String attribute) {
        return attributeModifiers.getOrDefault(attribute.toLowerCase(), 0.0);
    }

    public boolean hasModifier(String attribute) {
        return attributeModifiers.containsKey(attribute.toLowerCase());
    }
}