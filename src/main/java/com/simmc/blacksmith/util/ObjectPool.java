package com.simmc.blacksmith.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A thread-safe object pool for reusing frequently allocated objects.
 * Reduces GC pressure by recycling objects instead of creating new ones.
 *
 * @param <T> the type of objects to pool
 */
public class ObjectPool<T> {

    private final Queue<T> pool;
    private final Supplier<T> factory;
    private final Consumer<T> resetter;
    private final int maxSize;

    /**
     * Creates a new object pool.
     *
     * @param factory  supplier to create new objects when pool is empty
     * @param maxSize  maximum number of objects to keep in the pool
     */
    public ObjectPool(Supplier<T> factory, int maxSize) {
        this(factory, null, maxSize);
    }

    /**
     * Creates a new object pool with a reset function.
     *
     * @param factory  supplier to create new objects when pool is empty
     * @param resetter consumer to reset objects before returning to pool (can be null)
     * @param maxSize  maximum number of objects to keep in the pool
     */
    public ObjectPool(Supplier<T> factory, Consumer<T> resetter, int maxSize) {
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
        this.resetter = resetter;
        this.maxSize = maxSize;
    }

    /**
     * Acquires an object from the pool, or creates a new one if pool is empty.
     *
     * @return an object ready for use
     */
    public T acquire() {
        T obj = pool.poll();
        return obj != null ? obj : factory.get();
    }

    /**
     * Releases an object back to the pool for reuse.
     * If the pool is full, the object is discarded.
     *
     * @param obj the object to release
     */
    public void release(T obj) {
        if (obj == null) return;

        if (pool.size() < maxSize) {
            // Reset the object if a resetter is provided
            if (resetter != null) {
                try {
                    resetter.accept(obj);
                } catch (Exception e) {
                    // If reset fails, don't return to pool
                    return;
                }
            }
            pool.offer(obj);
        }
    }

    /**
     * Pre-fills the pool with objects up to the specified count.
     *
     * @param count number of objects to pre-create
     */
    public void preFill(int count) {
        int toCreate = Math.min(count, maxSize - pool.size());
        for (int i = 0; i < toCreate; i++) {
            pool.offer(factory.get());
        }
    }

    /**
     * Clears all objects from the pool.
     */
    public void clear() {
        pool.clear();
    }

    /**
     * Gets the current number of objects in the pool.
     */
    public int size() {
        return pool.size();
    }

    /**
     * Gets the maximum size of the pool.
     */
    public int getMaxSize() {
        return maxSize;
    }

    /**
     * Checks if the pool is empty.
     */
    public boolean isEmpty() {
        return pool.isEmpty();
    }
}