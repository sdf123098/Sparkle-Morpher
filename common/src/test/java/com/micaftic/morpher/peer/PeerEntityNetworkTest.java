package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.net.InetAddress;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerEntityNetworkTest {
    @TempDir Path directory;
    @Test void customMaidAndBuiltinDefaultReachLateObserverAndWithdrawRevokesEvenHotCache() throws Exception {
        char[] password="test".toCharArray();var tls=PeerTlsIdentity.create(password);byte[] secret=new byte[32];
        UUID owner=UUID.randomUUID(),observer=UUID.randomUUID(),room=UUID.randomUUID(),maid=UUID.randomUUID(),builtin=UUID.randomUUID();
        var expiry=Instant.now().plusSeconds(90);var base=PeerInvitation.single(room,"localhost:12345",tls.pin(),expiry,secret);
        Path source=directory.resolve("source");Files.createDirectories(source);Files.writeString(source.resolve("model.bbmodel"),"{\"name\":\"maid\"}");
        var selection=new LocalShareSourceResolver.Selection(source,"model.bbmodel","bbmodel-v1",Map.of("model.bbmodel",PeerAssetManifest.Role.MODEL));
        try(var hostStore=new PeerAssetStore(directory.resolve("host"));var ownerStore=new PeerAssetStore(directory.resolve("owner"));var readerStore=new PeerAssetStore(directory.resolve("reader"));
            var server=new PeerRoomServer(base,2,tls,password,List.of(owner,observer),hostStore)) {
            var address=server.listen(InetAddress.getByName("127.0.0.1"),0);
            var invite=PeerInvitation.single(room,"127.0.0.1:"+address.getPort(),tls.pin(),expiry,secret);
            try(var ca=PeerRoomClient.join(invite,owner,3000);var frozen=ownerStore.freeze(selection,()->true)) {
                var asset=ownerStore.copy(frozen,()->true);var identity=new PeerModelIdentity(asset.manifest().contentRoot(),"bbmodel-v1");
                var appearance=new PeerEntityAppearance(ca.session(),maid,1,identity,"blue",false);
                ca.publishEntity(appearance);ca.upload(appearance.assetAppearance(),asset,()->true);
                ca.publishEntity(new PeerEntityAppearance(ca.session(),builtin,2,null,"xiduo",false));
                try(var cb=PeerRoomClient.join(invite,observer,3000)) {
                    assertEquals(2,cb.entities().size());
                    assertEquals("xiduo",cb.entities().stream().filter(e->e.entityId().equals(builtin)).findFirst().orElseThrow().texture());
                    var received=cb.download(appearance.assetOwner(),readerStore,()->true);
                    assertEquals(Files.readString(source.resolve("model.bbmodel")),Files.readString(received.entrypoint()));
                    cb.authorizeDownload(appearance.assetOwner(),()->true);
                    ca.publishEntity(new PeerEntityAppearance(ca.session(),maid,3,null,null,true));
                    assertEquals(1,cb.entities().size());
                    assertThrows(java.io.IOException.class,()->cb.authorizeDownload(appearance.assetOwner(),()->true));
                    assertNotNull(readerStore.find(identity.contentRoot()),"Revocation must not require deleting valid cached bytes");
                }
            }
        }
    }

    @Test void runtimePublishesMultipleMaidsCoalescesTheirAssetsAndClearsSharingIndependently() throws Exception {
        int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        UUID owner=UUID.randomUUID(),observer=UUID.randomUUID(),one=UUID.randomUUID(),two=UUID.randomUUID();
        var invitation=new java.util.concurrent.CompletableFuture<PeerInvitation>();
        var catalog=new java.util.concurrent.atomic.AtomicReference<List<PeerEntityAppearance>>(List.of());
        Path source=directory.resolve("runtime-source");Files.createDirectories(source);Files.writeString(source.resolve("m.bbmodel"),"{\"name\":\"shared\"}");
        java.util.function.Supplier<LocalShareSourceResolver.Selection> resolver=()->new LocalShareSourceResolver.Selection(source,"m.bbmodel","bbmodel-v1",Map.of("m.bbmodel",PeerAssetManifest.Role.MODEL));
        try(var host=new PeerRoomRuntime(directory.resolve("runtime-host"),Runnable::run,s->{},s->{});
            var guest=new PeerRoomRuntime(directory.resolve("runtime-guest"),Runnable::run,s->{},s->{})) {
            guest.onEntities(catalog::set);host.host(new PeerEndpoint("127.0.0.1",port),owner,List.of(owner,observer),invitation::complete);
            guest.join(invitation.get(8,java.util.concurrent.TimeUnit.SECONDS),observer);
            var a=new java.util.concurrent.CompletableFuture<Boolean>();var b=new java.util.concurrent.CompletableFuture<Boolean>();
            host.shareEntity(one,resolver,"blue",a::complete);host.shareEntity(two,resolver,"red",b::complete);
            assertTrue(a.get(10,java.util.concurrent.TimeUnit.SECONDS));assertTrue(b.get(10,java.util.concurrent.TimeUnit.SECONDS));
            await(()->catalog.get().size()==2);
            var downloads=new ArrayList<java.util.concurrent.CompletableFuture<String>>();
            for(var entry:catalog.get()) {
                var result=new java.util.concurrent.CompletableFuture<String>();downloads.add(result);
                guest.download(entry.assetOwner(),()->true,(asset,lease)->{try{result.complete(Files.readString(asset.entrypoint()));}catch(Exception e){result.completeExceptionally(e);}finally{lease.close();}},failure->result.completeExceptionally(failure));
            }
            for(var result:downloads)assertTrue(result.get(8,java.util.concurrent.TimeUnit.SECONDS).contains("shared"));
            host.clearEntity(one);await(()->catalog.get().size()==1);assertEquals(two,catalog.get().getFirst().entityId());
            var builtin=new java.util.concurrent.CompletableFuture<Boolean>();host.shareEntity(two,null,"xiduo",builtin::complete);
            assertTrue(builtin.get(8,java.util.concurrent.TimeUnit.SECONDS));await(()->catalog.get().size()==1&&catalog.get().getFirst().model()==null);
            assertEquals("xiduo",catalog.get().getFirst().texture());
            host.clearEntities();await(()->catalog.get().isEmpty());
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while(!condition.getAsBoolean()&&System.nanoTime()<deadline)Thread.sleep(20);
        assertTrue(condition.getAsBoolean(),"P2P state did not converge");
    }
}
