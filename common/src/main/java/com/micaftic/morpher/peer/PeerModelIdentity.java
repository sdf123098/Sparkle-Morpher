package com.micaftic.morpher.peer;

import com.micaftic.morpher.core.model.ModelRef;
import com.micaftic.morpher.core.model.ModelSourceType;
import java.util.Objects;

/** Runtime identity includes interpreter semantics; identical names never identify content. */
public record PeerModelIdentity(PeerContentRoot contentRoot, String importerProfile) {
    public PeerModelIdentity {
        Objects.requireNonNull(contentRoot);
        if (importerProfile == null || !importerProfile.matches("[a-z][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException("Invalid Peer importer profile");
        PeerProfiles.requireKnown(importerProfile);
    }
    public ModelRef modelRef() { return ModelRef.of(ModelSourceType.PEER, importerProfile, contentRoot.sha256()); }
    public String runtimeModelId() { return modelRef().toString(); }
}
