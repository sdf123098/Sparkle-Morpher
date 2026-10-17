package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerNegotiationAndBudgetTest {
    @Test void handshakeEnforcesClientProfileAndLowerTransferLimits()throws Exception {
        char[] password="test".toCharArray();var identity=PeerTlsIdentity.create(password);var player=UUID.randomUUID();
        var invite=PeerInvitation.single(UUID.randomUUID(),"localhost:1234",identity.pin(),Instant.now().plusSeconds(60),new byte[32]);
        try(var server=new PeerRoomServer(invite,2,identity,password,List.of(player))) {
            var address=server.listen(InetAddress.getLoopbackAddress(),0);
            var actual=new PeerInvitation(invite.roomId(),List.of(new PeerEndpoint(address.getAddress().getHostAddress(),address.getPort())),invite.pin(),invite.expiresAt(),invite.secret());
            var offered=new PeerCapabilities(Set.of("ysm-v1"),16384,1024,2,false,false);
            try(var client=PeerRoomClient.join(actual,player,3000,offered)) {
                assertEquals(offered,client.capabilities());
                var failure=assertThrows(PeerFailure.class,()->client.publish(new PeerAppearance(1,new PeerModelIdentity(PeerContentRoot.digest(new byte[]{1}),"gltf-v1"),"default")));
                assertEquals(PeerFrame.ErrorCode.UNSUPPORTED_PROFILE,failure.code());
            }
        }
    }
    @Test void boundedFairRelayIsolatesOneOwnerAndRechecksQueuedAuthorization()throws Exception {
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();
        try(var scheduler=new PeerRelayScheduler(2,2,1);var pool=Executors.newSingleThreadExecutor();
            var held=scheduler.acquire(first,UUID.randomUUID(),1_000_000_000L,()->true)) {
            var allowed=new java.util.concurrent.atomic.AtomicBoolean(true);
            var queued=pool.submit(()->scheduler.acquire(first,UUID.randomUUID(),2_000_000_000L,allowed::get));
            long deadline=System.nanoTime()+1_000_000_000L;while(scheduler.queued()==0 && System.nanoTime()<deadline)Thread.onSpinWait();
            assertEquals(1,scheduler.queued());
            assertThrows(PeerFailure.class,()->scheduler.acquire(first,UUID.randomUUID(),1_000_000L,()->true));
            try(var other=scheduler.acquire(second,UUID.randomUUID(),1_000_000_000L,()->true)) {assertEquals(1,scheduler.queued());}
            allowed.set(false);
            var error=assertThrows(ExecutionException.class,()->queued.get(1,TimeUnit.SECONDS));assertInstanceOf(PeerFailure.class,error.getCause());
            assertEquals(0,scheduler.queued());
        }
    }
    @Test void globalReservationsRejectBeforeAllocationAndReleaseExactlyOnce()throws Exception {
        var budget=new PeerResourceBudget(10);var lease=budget.reserve(8);
        assertThrows(PeerFailure.class,()->budget.reserve(3));assertEquals(8,budget.used());
        lease.close();lease.close();assertEquals(0,budget.used());
        try(var full=budget.reserve(10)){assertEquals(10,budget.used());}
        assertEquals(0,budget.used());
    }
}
