package com.micaftic.morpher.peer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** Integrated-world bootstrap only. Invitations travel through the player's game connection. */
public final class PeerLanBootstrap {
    private record Offer(Object server, java.time.Instant expiry, Function<UUID, PeerInvitation> invitation) {}
    private static volatile Offer offer;
    private static final ConcurrentHashMap<UUID, Long> requests = new ConcurrentHashMap<>();
    private PeerLanBootstrap() {}
    public static void publish(Object server, java.time.Instant expiry, Function<UUID, PeerInvitation> invitation) {
        requests.clear(); offer = new Offer(server, expiry, invitation);
    }
    public static void clear(Object server) {
        Offer current = offer;
        if(current != null && current.server == server) { offer = null; requests.clear(); }
    }
    /** Called on the game server thread with the actual connection's UUID, never a client-supplied UUID. */
    public static String request(Object server, UUID player) {
        Offer current = offer;
        if(current == null || current.server != server || !java.time.Instant.now().isBefore(current.expiry)) return "";
        long now = System.nanoTime();
        Long previous = requests.put(player, now);
        if(previous != null && now - previous < 1_000_000_000L) return null;
        if(requests.size() > 64) requests.keySet().removeIf(id -> !id.equals(player));
        try { return current.invitation.apply(player).encode(); }
        catch(IllegalStateException unavailable) { return ""; }
    }
}
