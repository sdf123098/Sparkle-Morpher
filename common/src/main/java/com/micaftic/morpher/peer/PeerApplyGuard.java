package com.micaftic.morpher.peer;

import java.util.Objects;
import java.util.UUID;

/** Local candidate token; entity must be the exact live instance, not merely an equal UUID. */
public record PeerApplyGuard(long worldGeneration,PeerSession session,Object entityInstance,UUID ownerToken,
        PeerAppearance appearance) {
    public PeerApplyGuard {
        if(worldGeneration<0)throw new IllegalArgumentException("Invalid world generation");
        Objects.requireNonNull(session);Objects.requireNonNull(entityInstance);Objects.requireNonNull(ownerToken);Objects.requireNonNull(appearance);
    }
    public boolean matches(PeerApplyGuard current) {
        return current!=null && worldGeneration==current.worldGeneration && session.equals(current.session)
                && entityInstance==current.entityInstance && ownerToken.equals(current.ownerToken) && appearance.equals(current.appearance);
    }
}
