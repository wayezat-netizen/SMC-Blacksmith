package com.simmc.blacksmith.integration;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceholderAPIHook {

    private final JavaPlugin plugin;
    private final boolean available;

    // Simple cache for parsed values (player UUID + placeholder -> result)
    private final Map<String, CachedValue> cache;
    private static final long CACHE_EXPIRATION_MS = 1000; // 1 second cache

    // Pre-compiled operator checks
    private static final String OP_GTE = ">=";
    private static final String OP_LTE = "<=";
    private static final String OP_GT = ">";
    private static final String OP_LT = "<";
    private static final String OP_EQ = "==";
    private static final String OP_NEQ = "!=";

    public PlaceholderAPIHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
        this.cache = new ConcurrentHashMap<>();

        if (available) {
            // Schedule cache cleanup every 30 seconds
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                    this::cleanupCache, 600L, 600L);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String parse(Player player, String text) {
        if (!available || player == null || text == null || text.isEmpty()) {
            return text;
        }

        // Skip if no placeholders present
        if (!text.contains("%")) {
            return text;
        }

        return PlaceholderAPI.setPlaceholders(player, text);
    }

    public double parseDouble(Player player, String placeholder) {
        if (!available || player == null || placeholder == null || placeholder.isEmpty()) {
            return 0.0;
        }

        // Check cache first
        String cacheKey = player.getUniqueId().toString() + ":" + placeholder;
        CachedValue cached = cache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return cached.value;
        }

        // Parse and cache
        String result = PlaceholderAPI.setPlaceholders(player, placeholder);
        double value = parseDoubleQuiet(result);

        cache.put(cacheKey, new CachedValue(value));
        return value;
    }

    private double parseDoubleQuiet(String str) {
        if (str == null || str.isEmpty()) return 0.0;

        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public boolean checkCondition(Player player, String condition) {
        if (!available || player == null || condition == null || condition.isEmpty()) {
            return true;
        }

        // Parse condition: "%placeholder% >= value"
        String trimmed = condition.trim();

        // Find operator (check longer operators first)
        String operator = null;
        int opIndex = -1;

        if ((opIndex = trimmed.indexOf(OP_GTE)) > 0) {
            operator = OP_GTE;
        } else if ((opIndex = trimmed.indexOf(OP_LTE)) > 0) {
            operator = OP_LTE;
        } else if ((opIndex = trimmed.indexOf(OP_NEQ)) > 0) {
            operator = OP_NEQ;
        } else if ((opIndex = trimmed.indexOf(OP_EQ)) > 0) {
            operator = OP_EQ;
        } else if ((opIndex = trimmed.indexOf(OP_GT)) > 0) {
            operator = OP_GT;
        } else if ((opIndex = trimmed.indexOf(OP_LT)) > 0) {
            operator = OP_LT;
        }

        if (operator == null || opIndex <= 0) {
            return true; // Invalid condition format, allow by default
        }

        String placeholder = trimmed.substring(0, opIndex).trim();
        String valueStr = trimmed.substring(opIndex + operator.length()).trim();

        double actualValue = parseDouble(player, placeholder);
        double compareValue = parseDoubleQuiet(valueStr);

        return evaluateCondition(actualValue, operator, compareValue);
    }

    private boolean evaluateCondition(double actual, String operator, double compare) {
        return switch (operator) {
            case OP_GT -> actual > compare;
            case OP_GTE -> actual >= compare;
            case OP_LT -> actual < compare;
            case OP_LTE -> actual <= compare;
            case OP_EQ -> actual == compare;
            case OP_NEQ -> actual != compare;
            default -> true;
        };
    }

    private void cleanupCache() {
        if (cache.isEmpty()) return;

        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now));
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearCacheForPlayer(UUID playerId) {
        String prefix = playerId.toString() + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private static class CachedValue {
        final double value;
        final long timestamp;

        CachedValue(double value) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRATION_MS;
        }

        boolean isExpiredAt(long now) {
            return now - timestamp > CACHE_EXPIRATION_MS;
        }
    }
}