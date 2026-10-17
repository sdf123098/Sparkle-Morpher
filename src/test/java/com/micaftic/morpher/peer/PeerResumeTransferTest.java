package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PeerResumeTransferTest {
    @TempDir Path temporary;
    @Test void activeOutgoingReservationsDoNotHideUnrelatedPrivateFilesFromDiskAccounting()throws Exception {
        try(var cache=new PeerAssetStore(temporary.resolve("disk"),4L*1024*1024)) {
            Path unknown=temporary.resolve("disk/outgoing/unrelated.bin");Files.write(unknown,new byte[3*1024*1024]);
            Path source=Files.createDirectory(temporary.resolve("source-budget"));Files.write(source.resolve("m.ysm"),new byte[1024*1024]);
            var selection=new LocalShareSourceResolver.Selection(source,"m.ysm","ysm-v1",Map.of("m.ysm",PeerAssetManifest.Role.MODEL));
            assertThrows(IOException.class,()->cache.freeze(selection,()->true));assertEquals(3L*1024*1024,Files.size(unknown));
        }
    }
    @Test void transportInterruptionResumesVerifiedPrefixAfterReopeningTheCache()throws Exception {
        byte[] bytes=new byte[700000];new Random(5).nextBytes(bytes);Path source=Files.createDirectory(temporary.resolve("source"));Files.write(source.resolve("m.ysm"),bytes);
        var cacheRoot=temporary.resolve("cache");var cache=new PeerAssetStore(cacheRoot);
        try(var snapshot=new PeerAssetSnapshotBuilder(temporary.resolve("snapshots")).freeze(source,"m.ysm",Map.of("m.ysm",PeerAssetManifest.Role.MODEL),()->true)) {
            var asset=new PeerAssetStore(temporary.resolve("sender")).copy(snapshot,()->true);
            Path checkpoint=cache.partialDirectory(asset.manifest(),"ysm-v1",PeerLimits.CHUNK_BYTES).resolve("checkpoint.bin");
            try(var listener=new ServerSocket(0,4,InetAddress.getLoopbackAddress());var pool=Executors.newSingleThreadExecutor()) {
                var sending=pool.submit(()->{try(var socket=listener.accept()){PeerAssetWire.send(asset,new DataInputStream(socket.getInputStream()),new DataOutputStream(socket.getOutputStream()),()->{
                    if(Files.exists(checkpoint))try{socket.close();}catch(IOException ignored){}return true;
                });}return null;});
                try(var receiver=new Socket(listener.getInetAddress(),listener.getLocalPort())) {
                    receiver.setSoTimeout(3000);
                    assertThrows(IOException.class,()->PeerAssetWire.receive(asset.manifest().contentRoot(),cache,new DataInputStream(receiver.getInputStream()),new DataOutputStream(receiver.getOutputStream()),()->true));
                }
                assertThrows(ExecutionException.class,()->sending.get(3,TimeUnit.SECONDS));
            }
            var reopened=new PeerAssetStore(cacheRoot);
            try(var partial=new PeerPartialReceive(reopened,asset.manifest(),"ysm-v1",PeerLimits.CHUNK_BYTES,()->true)) {
                assertArrayEquals(new long[]{PeerLimits.CHUNK_BYTES},partial.offsets());partial.pause();
            }
            var transferred=new AtomicLong();
            try(var listener=new ServerSocket(0,4,InetAddress.getLoopbackAddress());var pool=Executors.newSingleThreadExecutor()) {
                var sending=pool.submit(()->{try(var socket=listener.accept()){PeerAssetWire.send(asset,new DataInputStream(socket.getInputStream()),new DataOutputStream(socket.getOutputStream()),()->true);}return null;});
                try(var receiver=new Socket(listener.getInetAddress(),listener.getLocalPort())) {
                    receiver.setSoTimeout(3000);
                    var measured=new FilterInputStream(receiver.getInputStream()) {
                        @Override public int read()throws IOException{int n=in.read();if(n>=0)transferred.incrementAndGet();return n;}
                        @Override public int read(byte[] b,int offset,int count)throws IOException{int n=in.read(b,offset,count);if(n>0)transferred.addAndGet(n);return n;}
                    };
                    var result=PeerAssetWire.receive(asset.manifest().contentRoot(),reopened,new DataInputStream(measured),new DataOutputStream(receiver.getOutputStream()),()->true);
                    assertArrayEquals(bytes,Files.readAllBytes(result.entrypoint()));
                }
                sending.get(3,TimeUnit.SECONDS);
            }
            assertTrue(transferred.get()<bytes.length-PeerLimits.CHUNK_BYTES+4096);
            assertFalse(Files.exists(checkpoint));assertEquals(0,PeerResourceBudget.STREAM.used());
        }
    }
    @Test void corruptedPartialIsDiscardedAndChangedChunkOrProfileCannotReuseIt()throws Exception {
        var bytes=new byte[40000];Arrays.fill(bytes,(byte)5);var manifest=manifest(bytes);var store=new PeerAssetStore(temporary.resolve("cache"));
        try(var partial=new PeerPartialReceive(store,manifest,"ysm-v1",16384,()->true)) {partial.append(0,0,Arrays.copyOf(bytes,16384));partial.pause();}
        try(var changed=new PeerPartialReceive(store,manifest,"bbmodel-v1",16384,()->true)){assertEquals(0,changed.offsets()[0]);}
        try(var changed=new PeerPartialReceive(store,manifest,"ysm-v1",32768,()->true)){assertEquals(0,changed.offsets()[0]);}
        Files.write(store.partialDirectory(manifest,"ysm-v1",16384).resolve("files/m.ysm"),new byte[16384]);
        try(var reopened=new PeerPartialReceive(store,manifest,"ysm-v1",16384,()->true)){assertEquals(0,reopened.offsets()[0]);}
        assertThrows(IOException.class,()->PeerResumeState.decode(PeerResumeState.encode(manifest,"ysm-v1",16384,new long[]{16384}),manifest,"ysm-v1",32768));
    }
    @Test void startupCleansOrphansPreservesActiveJobsAndChargesPrivateFiles()throws Exception {
        Path root=temporary.resolve("cache");var store=new PeerAssetStore(root,1000);var manifest=manifest(new byte[]{1,2,3});
        try(var active=store.begin(manifest,()->true)) {
            active.append(0,0,new byte[]{1,2,3});new PeerAssetStore(root,1000);
            active.finishFile(0);assertNotNull(active.commit());
        }
        Path orphan=Files.createDirectory(root.resolve("staging/receive-orphan"));Files.write(orphan.resolve("old"),new byte[10]);
        Path outgoing=Files.createDirectory(root.resolve("outgoing/snapshot-orphan"));Files.write(outgoing.resolve("old"),new byte[10]);
        new PeerAssetStore(root,1000);assertFalse(Files.exists(orphan));assertFalse(Files.exists(outgoing));
        Files.write(root.resolve("outgoing/keep-owned.bin"),new byte[950]);
        assertThrows(IOException.class,()->store.begin(manifest,()->true));assertTrue(Files.exists(root.resolve("outgoing/keep-owned.bin")));
    }
    private static PeerAssetManifest manifest(byte[] bytes){return new PeerAssetManifest(1,"m.ysm",List.of(new PeerAssetManifest.Resource("m.ysm",PeerAssetManifest.Role.MODEL,bytes.length,PeerContentRoot.digest(bytes))));}
}
