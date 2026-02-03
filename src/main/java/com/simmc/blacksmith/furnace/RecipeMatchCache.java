package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for furnace recipe matching results.
 * Reduces CPU usage by avoiding repeated recipe matching for unchanged inputs.
 */
public class RecipeMatchCache {

    private final Map<String, CachedMatch> cache;
    private final long cacheExpirationMs;
    private long lastCleanupTime;

    private static final long CLEANUP_INTERVAL_MS = 30000; // Clean up every 30 seconds

    /**
     * Creates a new recipe match cache.
     *
     * @param expirationMs how long cached results remain valid (in milliseconds)
     */
    public RecipeMatchCache(long expirationMs) {
        this.cache = new ConcurrentHashMap<>();
        this.cacheExpirationMs = expirationMs;
        this.lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * Gets a cached recipe match, or performs matching if not cached/expired.
     *
     * @param furnaceTypeId the furnace type ID
     * @param inputs        the input items
     * @param type          the furnace type for matching
     * @param registry      the item provider registry
     * @return the matched recipe, or null if no match
     */
    public FurnaceRecipe getCachedMatch(String furnaceTypeId, ItemStack[] inputs,
                                        FurnaceType type, ItemProviderRegistry registry) {
        // Periodic cleanup
        maybeCleanup();

        String cacheKey = generateCacheKey(furnaceTypeId, inputs);
        CachedMatch cached = cache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return cached.recipe;
        }

        // Cache miss or expired - perform actual matching
        FurnaceRecipe match = type.findMatchingRecipe(inputs, registry);
        cache.put(cacheKey, new CachedMatch(match, System.currentTimeMillis() + cacheExpirationMs));

        return match;
    }

    /**
     * Generates a cache key based on furnace type and input items.
     */
    private String generateCacheKey(String furnaceTypeId, ItemStack[] inputs) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(furnaceTypeId).append(":");

        if (inputs == null) {
            return sb.toString();
        }

        for (ItemStack item : inputs) {
            if (item != null && !item.getType().isAir()) {
                sb.append(item.getType().name())
                        .append("-")
                        .append(item.getAmount());

                // Include custom model data if present (for custom items)
                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasCustomModelData()) {
                        sb.append("-cmd").append(meta.getCustomModelData());
                    }
                }

                sb.append("|");
            }
        }

        return sb.toString();
    }

    /**
     * Invalidates all cached entries for a specific furnace type.
     *
     * @param furnaceTypeId the furnace type ID to invalidate
     */
    public void invalidate(String furnaceTypeId) {
        String prefix = furnaceTypeId + ":";
        cache.entrySet().removeIf(e -> e.getKey().startsWith(prefix));
    }

    /**
     * Invalidates a specific cache entry.
     *
     * @param furnaceTypeId the furnace type ID
     * @param inputs        the input items
     */
    public void invalidateSpecific(String furnaceTypeId, ItemStack[] inputs) {
        String cacheKey = generateCacheKey(furnaceTypeId, inputs);
        cache.remove(cacheKey);
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Performs cleanup if enough time has passed since the last cleanup.
     */
    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            cleanup();
            lastCleanupTime = now;
        }
    }

    /**
     * Removes all expired entries from the cache.
     */
    public void cleanup() {
        Iterator<Map.Entry<String, CachedMatch>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isExpired()) {
                iterator.remove();
            }
        }
    }

    /**
     * Gets the current cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Gets cache statistics as a string.
     */
    public String getStats() {
        int total = cache.size();
        long expired = cache.values().stream().filter(CachedMatch::isExpired).count();
        return String.format("Cache size: %d, Expired: %d", total, expired);
    }

    /**
     * Internal class to hold cached match results.
     */
    private static class CachedMatch {
        final FurnaceRecipe recipe;
        final long expirationTime;

        CachedMatch(FurnaceRecipe recipe, long expirationTime) {
            this.recipe = recipe;
            this.expirationTime = expirationTime;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}