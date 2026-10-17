package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerAssetSnapshotTest {
    @TempDir Path temp;
    @Test void copiesOnlySelectedDependenciesAndFreezesBytesWithStableRoot()throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")),stage=temp.resolve("stage");
        Files.writeString(source.resolve("model.ysm"),"a");Files.writeString(source.resolve("credential.txt"),"must-not-share");
        var builder=new PeerAssetSnapshotBuilder(stage);var selection=Map.of("model.ysm",PeerAssetManifest.Role.MODEL);
        try(var a=builder.freeze(source,"model.ysm",selection,()->true);var b=builder.freeze(source,"model.ysm",selection,()->true)) {
            assertEquals(a.manifest().contentRoot(),b.manifest().contentRoot());assertEquals(1,a.manifest().resources().size());
            assertFalse(Files.exists(a.root().resolve("credential.txt")));Files.writeString(source.resolve("model.ysm"),"b");
            assertEquals("a",Files.readString(a.file("model.ysm")));
        }
        assertTrue(Files.exists(source.resolve("credential.txt")));try(var paths=Files.list(stage)){assertEquals(0,paths.count());}
    }
    @Test void cancellationAndMissingDependencyFailWholeRevisionAndCleanPrivateStage()throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")),stage=temp.resolve("stage");Files.writeString(source.resolve("model.gltf"),"{}");
        var builder=new PeerAssetSnapshotBuilder(stage);
        assertThrows(IOException.class,()->builder.freeze(source,"model.gltf",Map.of("model.gltf",PeerAssetManifest.Role.MODEL,"mesh.bin",PeerAssetManifest.Role.BUFFER),()->true));
        assertThrows(IOException.class,()->builder.freeze(source,"model.gltf",Map.of("model.gltf",PeerAssetManifest.Role.MODEL),()->false));
        try(var paths=Files.list(stage)){assertEquals(0,paths.count());}
        assertThrows(IOException.class,()->PeerSafeFiles.deletePrivateTree(stage,source));assertTrue(Files.exists(source));
    }
    @Test void sourceMutationWithSameLengthFailsAndDoesNotUseMtimeAsIntegrity()throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")),stage=temp.resolve("stage");Path model=source.resolve("model.ysm");Files.writeString(model,"a");
        var timestamp=Files.getLastModifiedTime(model);var calls=new java.util.concurrent.atomic.AtomicInteger();
        assertThrows(IOException.class,()->new PeerAssetSnapshotBuilder(stage).freeze(source,"model.ysm",Map.of("model.ysm",PeerAssetManifest.Role.MODEL),()->{
            if(calls.incrementAndGet()==3)try{Files.writeString(model,"b");Files.setLastModifiedTime(model,timestamp);}catch(IOException e){throw new RuntimeException(e);}return true;
        }));
    }
    @Test void manifestRejectsDirectoryAliasesAndFileDirectoryConflicts() {
        var root=PeerContentRoot.digest(new byte[0]);
        var a=new PeerAssetManifest.Resource("A/m.ysm",PeerAssetManifest.Role.MODEL,0,root);
        var b=new PeerAssetManifest.Resource("a/t.png",PeerAssetManifest.Role.TEXTURE,0,root);
        assertThrows(IllegalArgumentException.class,()->new PeerAssetManifest(1,"A/m.ysm",List.of(a,b)));
        var file=new PeerAssetManifest.Resource("A",PeerAssetManifest.Role.TEXTURE,0,root);
        assertThrows(IllegalArgumentException.class,()->new PeerAssetManifest(1,"A/m.ysm",List.of(a,file)));
    }
}
