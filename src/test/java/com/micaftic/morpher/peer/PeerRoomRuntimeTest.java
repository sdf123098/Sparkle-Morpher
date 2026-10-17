package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerRoomRuntimeTest {
    @TempDir Path temporary;
    @Test void stalledUiHasOnlyOnePendingRoomSnapshotAndSourceHashDetectsSameMtimeRewrite()throws Exception {
        var queue=new LinkedBlockingQueue<Runnable>();var invitation=new CompletableFuture<PeerInvitation>();UUID a=UUID.randomUUID(),b=UUID.randomUUID();int port;try(var socket=new ServerSocket(0)){port=socket.getLocalPort();}
        try(var host=new PeerRoomRuntime(temporary.resolve("watcher"),queue::add,ignored->{},ignored->{})) {
            host.host(new PeerEndpoint("127.0.0.1",port),a,List.of(a,b),invitation::complete);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);while(!invitation.isDone()&&System.nanoTime()<deadline){var event=queue.poll(100,TimeUnit.MILLISECONDS);if(event!=null)event.run();}
            var invite=invitation.get(1,TimeUnit.SECONDS);Thread.sleep(1100);assertTrue(queue.size()<=2,"room poll must coalesce UI events");
            try(var observer=PeerRoomClient.join(invite,b,3000)) {
                Path source=Files.createDirectory(temporary.resolve("watch-source")),file=source.resolve("m.bbmodel");Files.writeString(file,"{\"name\":\"first\"}");
                host.share(()->new LocalShareSourceResolver.Selection(source,"m.bbmodel","bbmodel-v1",Map.of("m.bbmodel",PeerAssetManifest.Role.MODEL)),"default");
                PeerAppearance first=null;while(System.nanoTime()<deadline){first=host.publishedAppearance();if(first!=null)break;Thread.sleep(20);}assertNotNull(first);
                var timestamp=Files.getLastModifiedTime(file);Files.writeString(file,"{\"name\":\"other\"}");Files.setLastModifiedTime(file,timestamp);
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);PeerAppearance changed=null;while(System.nanoTime()<deadline){changed=host.publishedAppearance();if(changed!=null&&changed.revision()>first.revision())break;Thread.sleep(50);}
                assertNotNull(changed);assertTrue(changed.revision()>first.revision());assertNotEquals(first.model(),changed.model());assertEquals(timestamp,Files.getLastModifiedTime(file));
            }
        }
    }
    @Test void queuedPublicationSuccessCannotOverwriteAReplacementSelection() throws Exception {
        var queue = new java.util.concurrent.LinkedBlockingQueue<Runnable>();
        var states = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var invitation = new CompletableFuture<PeerInvitation>(); UUID a=UUID.randomUUID(), b=UUID.randomUUID();
        int port; try(var free=new ServerSocket(0)){port=free.getLocalPort();}
        try(var host=new PeerRoomRuntime(temporary.resolve("host"), queue::add, ignored->{}, states::add)) {
            host.host(new PeerEndpoint("127.0.0.1",port),a,List.of(a,b),invitation::complete);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
            while(!invitation.isDone() && System.nanoTime()<deadline) { var task=queue.poll(100,TimeUnit.MILLISECONDS);if(task!=null)task.run(); }
            var invite=invitation.get(1,TimeUnit.SECONDS);
            try(var observer=PeerRoomClient.join(invite,b,3000)) {
                Path source=Files.createDirectory(temporary.resolve("local"));Files.writeString(source.resolve("m.bbmodel"),"{\"name\":\"first\"}");
                host.share(()->new LocalShareSourceResolver.Selection(source,"m.bbmodel","bbmodel-v1",Map.of("m.bbmodel",PeerAssetManifest.Role.MODEL)),"default");
                boolean transferred=false;
                while(!transferred && System.nanoTime()<deadline) {
                    var member=observer.poll().members().stream().filter(m->m.session().minecraftUuid().equals(a)&&m.appearance()!=null&&!m.appearance().cleared()).findFirst();
                    if(member.isPresent()) try { observer.download(member.get(),new PeerAssetStore(temporary.resolve("receiver")),()->true); transferred=true; }
                    catch(IOException notUploadedYet) { Thread.sleep(10); }
                }
                assertTrue(transferred);
                host.clear(); // UI has not processed any old queued publication result.
                while(!states.contains("P2P 已停止分享") && System.nanoTime()<deadline) { var task=queue.poll(100,TimeUnit.MILLISECONDS);if(task!=null)task.run(); }
                assertTrue(states.contains("P2P 已停止分享"));
                assertFalse(states.contains("P2P 已分享当前本地模型与贴图"),states.toString());
            }
        }
    }
    @Test void automaticRoomSignalsRenewalBeforeItsBoundCredentialsExpire() throws Exception {
        var invitation=new CompletableFuture<PeerInvitation>(); UUID player=UUID.randomUUID();
        try(var host=new PeerRoomRuntime(temporary, Runnable::run, ignored->{}, ignored->{})) {
            host.hostAutomatic(player,List.of(player),invitation::complete);
            var invite=invitation.get(8,TimeUnit.SECONDS);
            assertFalse(host.automaticInvitationNeedsRenewal(invite.expiresAt().minusSeconds(61)));
            assertTrue(host.automaticInvitationNeedsRenewal(invite.expiresAt().minusSeconds(60)));
            assertTrue(host.automaticInvitationNeedsRenewal(invite.expiresAt()));
        }
    }
    @Test void optInRuntimeHostsJoinsSharesAndRevokesOverSeparateAssetChannel() throws Exception {
        int port; try(var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); var invite = new CompletableFuture<PeerInvitation>();
        var appeared = new CompletableFuture<PeerRoomCoordinator.Member>(); var cleared = new CompletableFuture<Void>();
        try(var host = new PeerRoomRuntime(temporary.resolve("host"), Runnable::run, ignored -> {}, ignored -> {});
            var guest = new PeerRoomRuntime(temporary.resolve("guest"), Runnable::run, snapshot -> {
                snapshot.members().stream().filter(m -> m.session().minecraftUuid().equals(a) && m.appearance() != null).forEach(member -> {
                    if(member.appearance().cleared()) cleared.complete(null); else appeared.complete(member);
                });
            }, ignored -> {})) {
            assertFalse(host.connected()); host.host(PeerEndpoint.parse("127.0.0.1:" + port), a, List.of(a, b), invite::complete);
            guest.join(invite.get(8, TimeUnit.SECONDS), b);
            Path source = Files.createDirectory(temporary.resolve("local")); Files.writeString(source.resolve("model.bbmodel"), "{\"name\":\"LAN fixture\"}");
            host.share(() -> new LocalShareSourceResolver.Selection(source, "model.bbmodel", "bbmodel-v1", Map.of("model.bbmodel", PeerAssetManifest.Role.MODEL)), "default");
            var member = appeared.get(8, TimeUnit.SECONDS); var result = new CompletableFuture<String>();
            guest.download(member, () -> true, (asset, lease) -> {
                try { result.complete(Files.readString(asset.entrypoint())); } catch(Exception e) { result.completeExceptionally(e); } finally { lease.close(); }
            }, () -> result.completeExceptionally(new IllegalStateException("Download failed")));
            assertTrue(result.get(8, TimeUnit.SECONDS).contains("LAN fixture"));
            host.clear(); cleared.get(8, TimeUnit.SECONDS);
        }
        // Recreate through the real listener API; raw ServerSocket(port) chooses a different wildcard/dual-stack bind on Windows.
        var recreated = new CompletableFuture<PeerInvitation>();
        try(var replacement = new PeerRoomRuntime(temporary.resolve("replacement"), Runnable::run, ignored -> {}, ignored -> {})) {
            replacement.host(PeerEndpoint.parse("127.0.0.1:" + port), a, List.of(a), recreated::complete);
            assertEquals(port, recreated.get(8, TimeUnit.SECONDS).endpoints().get(0).port());
        }
    }
}
