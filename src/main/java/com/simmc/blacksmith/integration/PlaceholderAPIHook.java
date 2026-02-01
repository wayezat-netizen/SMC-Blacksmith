package com.simmc.blacksmith.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class PlaceholderAPIHook {

    private final JavaPlugin plugin;
    private final boolean available;

    public PlaceholderAPIHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean isAvailable() {
        return available;
    }

    public String parse(Player player, String text) {
        if (!available || player == null || text == null) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public double parseDouble(Player player, String placeholder) {
        if (!available || player == null || placeholder == null) {
            return 0.0;
        }

        String result = PlaceholderAPI.setPlaceholders(player, placeholder);
        try {
            return Double.parseDouble(result);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public boolean checkCondition(Player player, String condition) {
        if (!available || player == null || condition == null || condition.isEmpty()) {
            return true;
        }

        String[] parts = condition.split(" ");
        if (parts.length < 3) {
            return true;
        }

        String placeholder = parts[0];
        String operator = parts[1];
        String valueStr = parts[2];

        double actualValue = parseDouble(player, placeholder);
        double compareValue;

        try {
            compareValue = Double.parseDouble(valueStr);
        } catch (NumberFormatException e) {
            return true;
        }

        return switch (operator) {
            case ">" -> actualValue > compareValue;
            case ">=" -> actualValue >= compareValue;
            case "<" -> actualValue < compareValue;
            case "<=" -> actualValue <= compareValue;
            case "==" -> actualValue == compareValue;
            case "!=" -> actualValue != compareValue;
            default -> true;
        };
    }
}