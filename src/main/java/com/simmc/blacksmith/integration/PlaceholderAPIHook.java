package com.simmc.blacksmith.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hook for PlaceholderAPI integration.
 */
public class PlaceholderAPIHook {

    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "([^<>=!]+)\\s*(>=|<=|>|<|==|!=)\\s*(.+)"
    );

    private final Logger logger;
    private final boolean available;

    public PlaceholderAPIHook(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
        this.available = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        if (available) {
            logger.info("PlaceholderAPI hook initialized");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Parses placeholders in a string.
     */
    public String parse(Player player, String text) {
        if (!available || text == null || player == null) {
            return text;
        }
        return PlaceholderAPI.setPlaceholders(player, text);
    }

    /**
     * Parses a placeholder and returns it as a double.
     *
     * @param player The player
     * @param placeholder The placeholder string (e.g., "%svalues_smithing%")
     * @return The parsed double value, or 0.0 if parsing fails
     */
    public double parseDouble(Player player, String placeholder) {
        if (!available || placeholder == null || placeholder.isEmpty() || player == null) {
            return 0.0;
        }

        try {
            String parsed = PlaceholderAPI.setPlaceholders(player, placeholder);

            // Clean the string - remove non-numeric characters except . and -
            String cleaned = parsed.replaceAll("[^0-9.\\-]", "").trim();

            if (cleaned.isEmpty()) {
                return 0.0;
            }

            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            logger.warning("Failed to parse placeholder '" + placeholder + "' as double");
            return 0.0;
        } catch (Exception e) {
            logger.warning("Error parsing placeholder '" + placeholder + "': " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Parses a placeholder and returns it as an integer.
     *
     * @param player The player
     * @param placeholder The placeholder string
     * @return The parsed int value, or 0 if parsing fails
     */
    public int parseInt(Player player, String placeholder) {
        return (int) parseDouble(player, placeholder);
    }

    /**
     * Parses a placeholder and returns it as a long.
     *
     * @param player The player
     * @param placeholder The placeholder string
     * @return The parsed long value, or 0 if parsing fails
     */
    public long parseLong(Player player, String placeholder) {
        return (long) parseDouble(player, placeholder);
    }

    /**
     * Parses a placeholder and returns it as a boolean.
     *
     * @param player The player
     * @param placeholder The placeholder string
     * @return true if the result is "true", "yes", "1", or a positive number
     */
    public boolean parseBoolean(Player player, String placeholder) {
        if (!available || placeholder == null || placeholder.isEmpty() || player == null) {
            return false;
        }

        try {
            String parsed = PlaceholderAPI.setPlaceholders(player, placeholder).trim().toLowerCase();

            if (parsed.equals("true") || parsed.equals("yes") || parsed.equals("1")) {
                return true;
            }
            if (parsed.equals("false") || parsed.equals("no") || parsed.equals("0")) {
                return false;
            }

            // Try as number - positive = true
            try {
                return Double.parseDouble(parsed) > 0;
            } catch (NumberFormatException ignored) {
            }

            // Non-empty string = true
            return !parsed.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks a condition like "%placeholder% >= 5"
     *
     * @param player The player to check
     * @param condition The condition string
     * @return true if condition is met, false otherwise
     */
    public boolean checkCondition(Player player, String condition) {
        if (!available || condition == null || condition.trim().isEmpty()) {
            return true;
        }

        try {
            // Parse placeholders first
            String parsed = PlaceholderAPI.setPlaceholders(player, condition);

            // Try to evaluate the condition
            Matcher matcher = CONDITION_PATTERN.matcher(parsed.trim());

            if (!matcher.matches()) {
                return evaluateSimple(parsed.trim());
            }

            String leftStr = matcher.group(1).trim();
            String operator = matcher.group(2).trim();
            String rightStr = matcher.group(3).trim();

            // Try numeric comparison
            try {
                double left = parseNumber(leftStr);
                double right = parseNumber(rightStr);
                return evaluateNumeric(left, operator, right);
            } catch (NumberFormatException e) {
                // Fall back to string comparison
                return evaluateString(leftStr, operator, rightStr);
            }

        } catch (Exception e) {
            logger.warning("Error evaluating condition '" + condition + "': " + e.getMessage());
            return true;
        }
    }

    private boolean evaluateSimple(String value) {
        if (value.isEmpty()) return false;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        if (value.equals("0")) return false;
        return true;
    }

    private double parseNumber(String str) throws NumberFormatException {
        String cleaned = str.replaceAll("[^0-9.\\-]", "").trim();
        if (cleaned.isEmpty()) {
            throw new NumberFormatException("No number found in: " + str);
        }
        return Double.parseDouble(cleaned);
    }

    private boolean evaluateNumeric(double left, String operator, double right) {
        return switch (operator) {
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            case ">" -> left > right;
            case "<" -> left < right;
            case "==" -> Math.abs(left - right) < 0.0001;
            case "!=" -> Math.abs(left - right) >= 0.0001;
            default -> false;
        };
    }

    private boolean evaluateString(String left, String operator, String right) {
        return switch (operator) {
            case "==" -> left.equalsIgnoreCase(right);
            case "!=" -> !left.equalsIgnoreCase(right);
            default -> false;
        };
    }
}