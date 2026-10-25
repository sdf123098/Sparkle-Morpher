package com.micaftic.morpher.peer;

import java.util.Objects;
import java.util.UUID;

/** An owner-session-scoped maid selection. A null model means the built-in player model. */
public record PeerEntityAppearance(PeerSession owner, UUID entityId, long revision,
        PeerModelIdentity model, String texture, boolean removed) {
    public PeerEntityAppearance {
        Objects.requireNonNull(owner); Objects.requireNonNull(entityId);
        if (revision < 1) throw new IllegalArgumentException("Invalid entity revision");
        if (removed) { model = null; texture = null; }
        else if (texture == null || texture.isBlank() || texture.length() > 256 || texture.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid entity texture");
    }
    public PeerAppearance assetAppearance() {
        if (removed || model == null) throw new IllegalStateException("Entity has no transferable model");
        return new PeerAppearance(revision, model, texture);
    }
    public PeerRoomCoordinator.Member assetOwner() { return new PeerRoomCoordinator.Member(owner, assetAppearance(), entityId); }
}
