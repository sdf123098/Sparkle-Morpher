package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerRoomCoordinatorTest {
    private static final byte[] SECRET=new byte[32];
    private static PeerInvitation invite(){return PeerInvitation.single(UUID.randomUUID(),"localhost:1234",new PeerContentRoot("a".repeat(64)),Instant.now().plusSeconds(3600),SECRET);}
    private static PeerAppearance appearance(long rev){return new PeerAppearance(rev,new PeerModelIdentity(new PeerContentRoot("a".repeat(64)),"ysm-v1"),"default");}
    @Test void fiveMembersPublishAndLateJoinSeesEveryCurrentSelection() {
        try(var room=new PeerRoomCoordinator(invite(),5)) {
            var sessions=new ArrayList<PeerSession>();
            for(int i=0;i<5;i++) {
                var welcome=room.join(SECRET,UUID.randomUUID(),true,Instant.now());sessions.add(welcome.session());
                assertTrue(room.authenticateAsset(welcome.session(),welcome.assetTicket()));
                assertFalse(room.authenticateAsset(welcome.session(),new byte[32]));room.publish(welcome.session(),appearance(1));
            }
            for(var observer:sessions) {
                var snapshot=room.snapshot(observer);assertEquals(5,snapshot.members().size());
                assertTrue(snapshot.members().stream().allMatch(m->m.appearance()!=null));
                assertEquals(10,snapshot.rosterRevision());assertTrue(room.drain(observer).isEmpty());
            }
            assertThrows(IllegalStateException.class,()->room.join(SECRET,UUID.randomUUID(),true,Instant.now()));
            assertFalse(room.publish(sessions.getFirst(),appearance(1)));
        }
    }
    @Test void explicitApprovalAndNoDuplicateUuidOrOldTicketsAfterReconnect() {
        try(var room=new PeerRoomCoordinator(invite(),5)) {
            UUID uuid=UUID.randomUUID();assertThrows(SecurityException.class,()->room.join(SECRET,uuid,false,Instant.now()));
            var first=room.join(SECRET,uuid,true,Instant.now());
            assertThrows(IllegalStateException.class,()->room.join(SECRET,uuid,true,Instant.now()));room.leave(first.session());
            var second=room.join(SECRET,uuid,true,Instant.now());assertNotEquals(first.session(),second.session());
            assertFalse(room.authenticateAsset(first.session(),first.assetTicket()));assertFalse(room.authenticateAsset(second.session(),first.assetTicket()));
            assertThrows(SecurityException.class,()->room.publish(first.session(),appearance(2)));
        }
    }
    @Test void warmBytesNeverReplaceCurrentRoomSessionGrantAndRevisionAuthorization() {
        try(var room=new PeerRoomCoordinator(invite(),5)) {
            var owner=room.join(SECRET,UUID.randomUUID(),true,Instant.now()).session();var reader=room.join(SECRET,UUID.randomUUID(),true,Instant.now()).session();
            room.publish(owner,appearance(1));var grant=room.grant(reader,owner,0,100);
            assertTrue(room.authorize(grant.id(),reader,appearance(1).model(),99));assertFalse(room.authorize(grant.id(),reader,appearance(1).model(),100));
            assertFalse(room.authorize(grant.id(),owner,appearance(1).model(),1));room.publish(owner,appearance(2));
            assertFalse(room.authorize(grant.id(),reader,appearance(1).model(),1));
            var current=room.grant(reader,owner,0,100);room.leave(owner);assertFalse(room.authorize(current.id(),reader,appearance(2).model(),1));
        }
    }
    @Test void slowConsumerRequiresResnapshotInsteadOfUnboundedHistory() {
        try(var room=new PeerRoomCoordinator(invite(),2)) {
            var owner=room.join(SECRET,UUID.randomUUID(),true,Instant.now()).session();room.snapshot(owner);
            for(int i=1;i<300;i++)room.publish(owner,appearance(i));
            assertThrows(IllegalStateException.class,()->room.drain(owner));assertEquals(299,room.snapshot(owner).members().getFirst().appearance().revision());
            assertTrue(room.drain(owner).isEmpty());
        }
    }
    @Test void everyApplyGuardFieldAndEntityInstanceIsRequired() {
        var s=new PeerSession(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        Object entity=new Object();UUID token=UUID.randomUUID();var guard=new PeerApplyGuard(1,s,entity,token,appearance(1));
        assertTrue(guard.matches(new PeerApplyGuard(1,s,entity,token,appearance(1))));
        assertFalse(guard.matches(new PeerApplyGuard(2,s,entity,token,appearance(1))));
        assertFalse(guard.matches(new PeerApplyGuard(1,s,new Object(),token,appearance(1))));
        assertFalse(guard.matches(new PeerApplyGuard(1,s,entity,UUID.randomUUID(),appearance(1))));
        assertFalse(guard.matches(new PeerApplyGuard(1,s,entity,token,appearance(2))));
        assertFalse(guard.matches(new PeerApplyGuard(1,new PeerSession(s.roomId(),s.epoch(),s.memberId(),UUID.randomUUID(),s.minecraftUuid()),entity,token,appearance(1))));
    }
}
