package com.micaftic.morpher.peer;

import java.util.Objects;
import java.util.UUID;

/** All room/session fields must match; local world generations are not room authentication. */
public record PeerSession(UUID roomId, UUID epoch, UUID memberId, UUID memberSession, UUID minecraftUuid) {
    public PeerSession {
        Objects.requireNonNull(roomId); Objects.requireNonNull(epoch); Objects.requireNonNull(memberId);
        Objects.requireNonNull(memberSession); Objects.requireNonNull(minecraftUuid);
    }
}
