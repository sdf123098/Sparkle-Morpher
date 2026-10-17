package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class PeerRelayQueueTest {
    @TempDir Path temporary;
    @Test void thirdPublisherWaitsForBusyRelayWhileControlRemainsResponsive() throws Exception {
        char[] password="test".toCharArray();var key=PeerTlsIdentity.create(password);
        var players=List.of(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
        var invite=PeerInvitation.single(UUID.randomUUID(),"localhost:1234",key.pin(),Instant.now().plusSeconds(60),new byte[32]);
        List<PeerRoomClient> clients=new ArrayList<>();List<PeerAssetStore.Verified> assets=new ArrayList<>();List<PeerAppearance> appearances=new ArrayList<>();
        var held=new CountDownLatch(2);var thirdStarted=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var server=new PeerRoomServer(invite,3,key,password,players,new PeerAssetStore(temporary.resolve("relay")));
            var pool=Executors.newFixedThreadPool(3)) {
            var address=server.listen(InetAddress.getByName("127.0.0.1"),0);
            var offered=new PeerInvitation(invite.roomId(),List.of(new PeerEndpoint("127.0.0.1",address.getPort())),invite.pin(),invite.expiresAt(),invite.secret());
            for(int i=0;i<3;i++) {
                var client=PeerRoomClient.join(offered,players.get(i),3000);clients.add(client);
                Path source=Files.createDirectory(temporary.resolve("source"+i));Files.write(source.resolve("m.ysm"),new byte[]{(byte)i,1,2});
                try(var frozen=new PeerAssetSnapshotBuilder(temporary.resolve("frozen")).freeze(source,"m.ysm",Map.of("m.ysm",PeerAssetManifest.Role.MODEL),()->true)) {
                    var asset=new PeerAssetStore(temporary.resolve("sender"+i)).copy(frozen,()->true);assets.add(asset);
                    var appearance=new PeerAppearance(1,new PeerModelIdentity(asset.manifest().contentRoot(),"ysm-v1"),"default");appearances.add(appearance);client.publish(appearance);
                }
            }
            BooleanSupplier hold=()->{held.countDown();try{return release.await(6,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}};
            var first=pool.submit(()->{clients.get(0).upload(appearances.get(0),assets.get(0),hold);return null;});
            var second=pool.submit(()->{clients.get(1).upload(appearances.get(1),assets.get(1),hold);return null;});
            assertTrue(held.await(3,TimeUnit.SECONDS));
            var third=pool.submit(()->{clients.get(2).upload(appearances.get(2),assets.get(2),()->{thirdStarted.countDown();return true;});return null;});
            assertTrue(thirdStarted.await(3,TimeUnit.SECONDS));
            assertThrows(TimeoutException.class,()->third.get(150,TimeUnit.MILLISECONDS));
            assertEquals(3,clients.get(2).poll().members().size());
            release.countDown();first.get(3,TimeUnit.SECONDS);second.get(3,TimeUnit.SECONDS);third.get(3,TimeUnit.SECONDS);
        } finally {release.countDown();clients.forEach(PeerRoomClient::close);}
    }
}
