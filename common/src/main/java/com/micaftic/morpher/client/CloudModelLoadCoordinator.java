package com.micaftic.morpher.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Coalesces exact runtime identities while keeping each consumer's cancellation independent. */
public final class CloudModelLoadCoordinator<E> {
    private record Waiter<E>(BooleanSupplier current, Consumer<E> completion) {}
    public static final class Ticket<E> {
        private final String id;
        private final List<Waiter<E>> waiters = new ArrayList<>();
        private Ticket(String id) { this.id = id; }
    }
    private final Map<String, Ticket<E>> active = new HashMap<>();

    /** Returns a ticket only to the leader; followers attach to the same parse/publication. */
    public synchronized Ticket<E> join(String id, BooleanSupplier current, Consumer<E> completion) {
        Ticket<E> ticket = active.get(id);
        boolean leader = ticket == null;
        if (leader) { ticket = new Ticket<>(id); active.put(id, ticket); }
        ticket.waiters.add(new Waiter<>(current, completion));
        return leader ? ticket : null;
    }

    public synchronized boolean isCurrent(Ticket<E> ticket) {
        return active.get(ticket.id) == ticket && ticket.waiters.stream().anyMatch(w -> w.current.getAsBoolean());
    }

    public synchronized boolean contains(String id) { return active.containsKey(id); }

    public void finish(Ticket<E> ticket, E error, E cancelled) {
        List<Waiter<E>> waiters;
        synchronized (this) {
            if (!active.remove(ticket.id, ticket)) return;
            waiters = List.copyOf(ticket.waiters);
        }
        waiters.forEach(w -> w.completion.accept(w.current.getAsBoolean() ? error : cancelled));
    }

    public void cancel(String id, E cancelled) {
        Ticket<E> ticket;
        synchronized (this) { ticket = active.get(id); }
        if (ticket != null) finish(ticket, cancelled, cancelled);
    }

    public void cancelAll(E cancelled) {
        List<Waiter<E>> waiters = new ArrayList<>();
        synchronized (this) { active.values().forEach(t -> waiters.addAll(t.waiters)); active.clear(); }
        waiters.forEach(w -> w.completion.accept(cancelled));
    }
}
