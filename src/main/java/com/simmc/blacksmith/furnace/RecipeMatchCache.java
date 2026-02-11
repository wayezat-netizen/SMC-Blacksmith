package com.simmc.blacksmith.furnace;

import com.simmc.blacksmith.items.ItemProviderRegistry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for furnace recipe matching results.
 * Reduces CPU usage by avoiding repeated recipe matching for unchanged inputs.
 */
public class RecipeMatchCache {

    private static final long DEFAULT_EXPIRATION_MS = 5000;
    private static final long CLEANUP_INTERVAL_MS = 30000;

    private final Map<String, CachedMatch> cache;
    private final long cacheExpirationMs;
    private volatile long lastCleanupTime;

    public RecipeMatchCache() {
        this(DEFAULT_EXPIRATION_MS);
    }

    public RecipeMatchCache(long expirationMs) {
        this.cache = new ConcurrentHashMap<>();
        this.cacheExpirationMs = Math.max(1000, expirationMs);
        this.lastCleanupTime = System.currentTimeMillis();
    }

    // ==================== PUBLIC API ====================

    /**
     * Gets a cached recipe match, or performs matching if not cached/expired.
     */
    public Optional<FurnaceRecipe> getCachedMatch(String furnaceTypeId, ItemStack[] inputs,
                                                  FurnaceType type, ItemProviderRegistry registry) {
        maybeCleanup();

        String cacheKey = generateCacheKey(furnaceTypeId, inputs);
        CachedMatch cached = cache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return Optional.ofNullable(cached.recipe);
        }

        // Cache miss or expired
        FurnaceRecipe match = type.findMatchingRecipe(inputs, registry);
        cache.put(cacheKey, new CachedMatch(match, System.currentTimeMillis() + cacheExpirationMs));

        return Optional.ofNullable(match);
    }

    /**
     * Invalidates all cached entries for a specific furnace type.
     */
    public void invalidate(String furnaceTypeId) {
        String prefix = furnaceTypeId + ":";
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /**
     * Invalidates a specific cache entry.
     */
    public void invalidateSpecific(String furnaceTypeId, ItemStack[] inputs) {
        cache.remove(generateCacheKey(furnaceTypeId, inputs));
    }

    /**
     * Clears all cached entries.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Manually triggers cleanup of expired entries.
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        lastCleanupTime = now;
    }

    // ==================== CACHE KEY GENERATION ====================

    private String generateCacheKey(String furnaceTypeId, ItemStack[] inputs) {
        StringBuilder sb = new StringBuilder(64);
        sb.append(furnaceTypeId).append(':');

        if (inputs == null) return sb.toString();

        for (ItemStack item : inputs) {
            if (item != null && !item.getType().isAir()) {
                appendItemKey(sb, item);
            }
        }

        return sb.toString();
    }

    private void appendItemKey(StringBuilder sb, ItemStack item) {
        sb.append(item.getType().name())
                .append('-')
                .append(item.getAmount());

        // Include custom model data for custom items
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                sb.append("-cmd").append(meta.getCustomModelData());
            }
        }

        sb.append('|');
    }

    // ==================== MAINTENANCE ====================

    private void maybeCleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            cleanup();
        }
    }

    // ==================== STATISTICS ====================

    public int size() {
        return cache.size();
    }

    public CacheStats getStats() {
        long now = System.currentTimeMillis();
        int total = cache.size();
        long expired = cache.values().stream()
                .filter(c -> c.isExpired(now))
                .count();
        return new CacheStats(total, (int) expired, total - (int) expired);
    }

    // ==================== INNER TYPES ====================

    private static class CachedMatch {
        final FurnaceRecipe recipe;
        final long expirationTime;

        CachedMatch(FurnaceRecipe recipe, long expirationTime) {
            this.recipe = recipe;
            this.expirationTime = expirationTime;
        }

        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }

        boolean isExpired(long now) {
            return now > expirationTime;
        }
    }

    public record CacheStats(int total, int expired, int valid) {
        @Override
        public String toString() {
            return String.format("Cache[total=%d, valid=%d, expired=%d]", total, valid, expired);
        }
    }
}