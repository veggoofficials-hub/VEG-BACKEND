package com.veggofresh.platform.common;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A tiny in-memory cache whose entries expire after a fixed time. Used for guest (public) browsing so a
 * burst of identical requests does not repeat the same expensive catalog work.
 *
 * <p>In-memory, so each application instance has its own cache. If the loader throws, nothing is cached.
 * When the cache is full, expired entries are dropped first, and if it is still full it is cleared (simple
 * and safe: the next requests just recompute).
 */
public class TtlCache {

    private record Entry(Object value, long expiresAtMillis) {
    }

    private final long ttlMillis;
    private final int maxEntries;
    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    public TtlCache(long ttlSeconds, int maxEntries) {
        this.ttlMillis = Math.max(0, ttlSeconds) * 1000L;
        this.maxEntries = Math.max(1, maxEntries);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> loader) {
        if (ttlMillis == 0) {
            return loader.get();
        }
        long now = System.currentTimeMillis();
        Entry cached = map.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return (T) cached.value();
        }
        T value = loader.get();
        if (map.size() >= maxEntries) {
            map.entrySet().removeIf(e -> e.getValue().expiresAtMillis() <= now);
            if (map.size() >= maxEntries) {
                map.clear();
            }
        }
        map.put(key, new Entry(value, now + ttlMillis));
        return value;
    }
}
