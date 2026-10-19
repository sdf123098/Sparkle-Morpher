package com.micaftic.morpher.client.gui;

import java.util.ArrayDeque;
import java.util.Optional;

/** Screen-local history; returning consumes an entry instead of creating another visit. */
final class NavigationHistory<T> {
    private final ArrayDeque<T> entries = new ArrayDeque<>();
    void push(T route) {
        if (!route.equals(entries.peekLast())) entries.addLast(route);
        if (entries.size() > 64) entries.removeFirst();
    }
    void clear(){entries.clear();}
    Optional<T> back() { return Optional.ofNullable(entries.pollLast()); }
}
