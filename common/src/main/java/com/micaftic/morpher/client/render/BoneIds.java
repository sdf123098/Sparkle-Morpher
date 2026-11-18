package com.micaftic.morpher.client.render;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe interned integer ID registry for bone names and controller/variable keys.
 */
public final class BoneIds {
    private static final ConcurrentMap<String, Integer> KEY_TO_ID = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, String> ID_TO_KEY = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    public static final int EMPTY = id("");

    private BoneIds() {
    }

    public static int id(String name) {
        if (name == null) {
            return EMPTY;
        }
        Integer existing = KEY_TO_ID.get(name);
        if (existing != null) {
            return existing;
        }
        int created = NEXT_ID.getAndIncrement();
        Integer race = KEY_TO_ID.putIfAbsent(name, created);
        if (race != null) {
            return race;
        }
        ID_TO_KEY.put(created, name);
        return created;
    }

    public static String name(int id) {
        return ID_TO_KEY.getOrDefault(id, "");
    }
}
