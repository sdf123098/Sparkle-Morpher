package com.micaftic.morpher.fakeplayer;

import java.util.UUID;

/** Server-confirmed fake-player row used by the standalone manager GUI. */
public record FakePlayerListEntry(UUID uuid, String name, String displayName, String providerId,
                                  String modelId, String dimensionId, boolean online) {
    public FakePlayerListEntry(UUID uuid, String name, String providerId) {
        this(uuid, name, name, providerId, "", "", true);
    }
}
