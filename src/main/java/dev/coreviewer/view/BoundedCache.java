package dev.coreviewer.view;

import java.util.LinkedHashMap;
import java.util.Map;

/** Access-ordered cache; world/resource changes must additionally invalidate owners. */
public final class BoundedCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;

    public BoundedCache(int capacity) {
        super(16, .75f, true);
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
