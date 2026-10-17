package com.micaftic.morpher.peer;

import com.micaftic.morpher.client.motion.*;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerMotionNetworkTest {
    private static PeerMotion motion(long revision,long sequence){return new PeerMotion(revision,sequence,1,"wave",500,3000,Map.of("speed",.5f),Map.of("body",new PeerMotion.Controller(1,"idle",500,Map.of())),List.of(new PeerMotion.NumericEvent(1,100,List.of(.5f))));}
    @Test void threeFiveAndTenRealTlsMembersReceivePagedMotionWithoutCloudOrWallClock()throws Exception {
        for(int count:new int[]{3,5,10}) {
            char[] password="test".toCharArray();var identity=PeerTlsIdentity.create(password);var ids=new ArrayList<UUID>();for(int i=0;i<count;i++)ids.add(UUID.randomUUID());
            UUID room=UUID.randomUUID();var expires=Instant.now().plusSeconds(60);byte[] secret=new byte[32];
            try(var server=new PeerRoomServer(PeerInvitation.single(room,"localhost:12345",identity.pin(),expires,secret),count,identity,password,ids)) {
                var bound=server.listen(InetAddress.getByName("127.0.0.1"),0);var invite=PeerInvitation.single(room,"127.0.0.1:"+bound.getPort(),identity.pin(),expires,secret);var clients=new ArrayList<PeerRoomClient>();
                try {
                    for(var id:ids)clients.add(PeerRoomClient.join(invite,id,3000));
                    for(var client:clients){client.publish(new PeerAppearance(1,new PeerModelIdentity(PeerContentRoot.digest(new byte[]{1}),"ysm-v1"),"default"));assertTrue(client.publishMotion(motion(1,50)));assertFalse(client.publishMotion(motion(1,49)));}
                    for(var client:clients){client.poll();var receipts=client.motions();assertEquals(count-1,receipts.size());assertTrue(receipts.stream().allMatch(receipt->receipt.motion().action().equals("wave")&&receipt.transitAgeMillis()<3000));assertTrue(client.motions().isEmpty());}
                    // A new appearance restarts motion sequence. An old per-member cursor must not hide it.
                    clients.get(0).publish(new PeerAppearance(2,new PeerModelIdentity(PeerContentRoot.digest(new byte[]{2}),"ysm-v1"),"new"));
                    assertFalse(clients.get(0).publishMotion(motion(1,51)));assertTrue(clients.get(0).publishMotion(motion(2,1)));
                    clients.get(1).poll();assertEquals(2,clients.get(1).motions().getFirst().motion().appearanceRevision());
                    assertEquals(motion(2,1),PeerMotion.decode(motion(2,1).encode()));
                } finally {for(var client:clients)client.close();}
            }
        }
    }
    @Test void expirationRevocationAndGrantRenewalNeverReviveStaleState()throws Exception {
        byte[] secret=new byte[32];var now=Instant.now();var invite=PeerInvitation.single(UUID.randomUUID(),"localhost:12345",new PeerContentRoot("0".repeat(64)),now.plusSeconds(60),secret);
        try(var room=new PeerRoomCoordinator(invite,3)) {
            var a=room.join(secret,UUID.randomUUID(),true,now).session();var b=room.join(secret,UUID.randomUUID(),true,now).session();
            var model=new PeerModelIdentity(PeerContentRoot.digest(new byte[]{1}),"ysm-v1");room.publish(a,new PeerAppearance(1,model,"default"));assertTrue(room.publishMotion(a,motion(1,1),0));
            assertEquals(1,room.motionPage(b,0,Map.of(),2_999_000_000L).receipts().size());assertTrue(room.motionPage(b,0,Map.of(),3_000_000_000L).receipts().isEmpty());
            var grant=room.grant(b,a,0,10);assertTrue(room.renew(grant.id(),b,model,9));assertTrue(room.authorize(grant.id(),b,model,100));
            room.publish(a,new PeerAppearance(2,null,null));assertFalse(room.renew(grant.id(),b,model,101));assertTrue(room.motionPage(b,0,Map.of(),101).receipts().isEmpty());
            room.leave(a);assertThrows(SecurityException.class,()->room.publishMotion(a,motion(1,2),102));
        }
    }
    @Test void modelAllowListRejectsUnknownStateAndHeartbeatNeverRestartsAnEntry(){
        var raw=new RawYsmModel();raw.properties.extraAnimations.put("wave","wave");raw.functionFiles.put("declared",new RawYsmModel.RawDataFile("", "v.speed=0;".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var file=new RawYsmModel.RawAnimationControllerFile();var controller=new RawYsmModel.RawAnimationController();var state=new RawYsmModel.RawControllerState();state.name="idle";controller.states.add(state);file.controllers.put("body",controller);raw.mainEntity.animationControllerFiles.add(file);
        var policy=PeerModelPolicy.from(raw);assertTrue(policy.accepts(motion(1,1)));assertFalse(policy.controller("body","remote_code"));assertFalse(policy.variables().contains("undeclared"));
        var cursor=new ClientControllerMotionCursor();var first=new ClientMotion.Controller("session:1:8","idle",100,Map.of("speed",1f));cursor.accept(first);
        var heartbeat=new ClientMotion.Controller("session:1:8","idle",150,Map.of("speed",2f));assertFalse(cursor.needsTransition(heartbeat));assertTrue(cursor.needsVariables(heartbeat));
        assertTrue(cursor.needsTransition(new ClientMotion.Controller("session:1:9","idle",150,Map.of())));
        assertThrows(IllegalArgumentException.class,()->new PeerMotion(1,1,1,"wave",0,3000,Map.of("speed",Float.NaN),Map.of(),List.of()));
    }
    @Test void numericOptionsAreTypedDeclaredSnapshotsAndNeverTransportExecutableInput()throws Exception {
        assertEquals(Map.of("v.speed",2f),PeerNumericAssignments.capture("v.speed=2;",Set.of("v.speed")));
        assertTrue(PeerNumericAssignments.capture("v.speed=math.random(0,2);",Set.of("v.speed")).isEmpty());
        assertTrue(PeerNumericAssignments.capture("v.unknown=2;",Set.of("v.speed")).isEmpty());
        assertTrue(PeerNumericAssignments.capture("v.speed=2; query.command('remote');",Set.of("v.speed")).isEmpty());
        var state=new PeerMotion(1,1,1,"",0,3000,Map.of(),Map.of("v.speed",2f),Map.of(),List.of());assertEquals(state,PeerMotion.decode(state.encode()));
        assertThrows(IllegalArgumentException.class,()->new PeerMotion(1,1,1,"",0,3000,Map.of(),Map.of("query.command",1f),Map.of(),List.of()));
    }
}
