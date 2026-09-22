package com.micaftic.morpher.cloud.identity;

import com.micaftic.morpher.cloud.scope.CloudScopeRef;
import com.micaftic.morpher.cloud.scope.CloudTargetRef;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudIdentityRefTest {

    private static final UUID UUID_VALUE = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    @Test
    void roundTripsAllIdentityNamespaces() {
        assertEquals("official:123e4567-e89b-12d3-a456-426614174000",
                CloudIdentityRef.official(UUID_VALUE).toWireString());
        assertEquals("yggdrasil:community:123e4567-e89b-12d3-a456-426614174000",
                CloudIdentityRef.parse("yggdrasil:Community:" + UUID_VALUE).toWireString());
        assertEquals("offline:scope-a:123e4567-e89b-12d3-a456-426614174000",
                CloudIdentityRef.parse("offline:scope-a:" + UUID_VALUE).toWireString());
    }

    @Test
    void namespacesRequireTheirOwnScopeAndProviderShape() {
        assertThrows(IllegalArgumentException.class,
                () -> new CloudIdentityRef(CloudIdentityRef.Kind.OFFICIAL, "provider", null, UUID_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> CloudIdentityRef.parse("offline:" + UUID_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> CloudIdentityRef.parse("yggdrasil:bad provider:" + UUID_VALUE));
        assertThrows(IllegalArgumentException.class,
                () -> CloudIdentityRef.parse("official:!" + UUID_VALUE));
    }

    @Test
    void targetAndScopeReferencesKeepInstanceAndTenantBoundaries() {
        assertEquals("instance:tenant:target-1",
                new CloudTargetRef("instance", "tenant", "target-1").toWireString());
        assertEquals("instance:tenant:scope-1:epoch-2",
                new CloudScopeRef("instance", "tenant", "scope-1", "epoch-2").toWireString());
        assertThrows(IllegalArgumentException.class,
                () -> new CloudScopeRef("instance", "tenant", "scope/other", "epoch-2"));
        assertThrows(NullPointerException.class,
                () -> new CloudTargetRef("instance", "tenant", null));
    }
}
