package com.micaftic.morpher.client.peer;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class PeerMaidOwnershipTest {
    public record Legacy(UUID id) { public UUID getOwnerUUID(){return id;} }
    public record Reference(UUID id) { public UUID getUUID(){return id;} }
    public record Current(Reference owner) { public Reference getOwnerReference(){return owner;} }
    @Test void resolvesBothOfficial1211AndOrihime26OwnerApisAndRejectsUntamedEntities() {
        UUID owner=UUID.randomUUID();
        assertEquals(owner,PeerMaidModelSync.ownerUuid(new Legacy(owner)));
        assertEquals(owner,PeerMaidModelSync.ownerUuid(new Current(new Reference(owner))));
        assertNull(PeerMaidModelSync.ownerUuid(new Current(null)));
        assertNull(PeerMaidModelSync.ownerUuid(new Object()));
    }
}
