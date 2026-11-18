package com.micaftic.morpher.client.core;

/** Identity-scoped Host generation tickets cannot be committed by another lane or replacement. */
public final class CoreSceneEpoch {
    public record Ticket(Object owner, long generation) { }
    private final Object identity = new Object();
    private long generation;
    public synchronized Ticket ticket() { return new Ticket(identity, generation); }
    public synchronized boolean current(Ticket ticket) {
        return ticket != null && ticket.owner() == identity && ticket.generation() == generation;
    }
    public synchronized void retire() { generation = Math.incrementExact(generation); }
}
