package com.micaftic.morpher.fakeplayer;

import java.util.List;

/** Client-side snapshot populated by the server after the manager GUI opens. */
public final class FakePlayerListCache {

    private static volatile List<FakePlayerListEntry> entries = List.of();

    private FakePlayerListCache() {
    }

    public static List<FakePlayerListEntry> entries() {
        return entries;
    }

    public static void replace(List<FakePlayerListEntry> newEntries) {
        entries = List.copyOf(newEntries);
    }
}
