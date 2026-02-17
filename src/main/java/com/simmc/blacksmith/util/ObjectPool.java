package com.simmc.blacksmith.util;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A thread-safe object pool for reusing frequently allocated objects.
 */
public class ObjectPool<T> {

    private final Queue<T> pool;
    private final Supplier<T> factory;
    private final Consumer<T> resetter;
    private final int maxSize;

    public ObjectPool(Supplier<T> factory, int maxSize) {
        this(factory, null, maxSize);
    }

    public ObjectPool(Supplier<T> factory, Consumer<T> resetter, int maxSize) {
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
        this.resetter = resetter;
        this.maxSize = maxSize;
    }

    public T acquire() {
        T obj = pool.poll();
        return obj != null ? obj : factory.get();
    }

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


    public void preFill(int count) {
        int toCreate = Math.min(count, maxSize - pool.size());
        for (int i = 0; i < toCreate; i++) {
            pool.offer(factory.get());
        }
    }

    public void clear() {
        pool.clear();
    }

    public int size() {
        return pool.size();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public boolean isEmpty() {
        return pool.isEmpty();
    }
}