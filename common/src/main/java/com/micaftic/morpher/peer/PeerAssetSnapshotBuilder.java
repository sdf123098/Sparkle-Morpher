package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Copies only a resolver-selected dependency closure, never walks or uploads the user's entire library. */
public final class PeerAssetSnapshotBuilder {
    private final Path stagingParent;
    public PeerAssetSnapshotBuilder(Path stagingParent){this.stagingParent=stagingParent.toAbsolutePath().normalize();}
    public PeerAssetSnapshot freeze(Path selectedRoot,String entrypoint,Map<String,PeerAssetManifest.Role> dependencies,
            BooleanSupplier current)throws IOException {
        if(dependencies.isEmpty() || dependencies.size()>PeerLimits.RESOURCE_COUNT)throw new IOException("Invalid Peer dependency count");
        if(dependencies.get(entrypoint)!=PeerAssetManifest.Role.MODEL)throw new IOException("Missing Peer entrypoint");
        selectedRoot=selectedRoot.toRealPath();
        dependencies.keySet().forEach(PeerPaths::validate);Files.createDirectories(stagingParent);
        Path root=Files.createTempDirectory(stagingParent,"snapshot-");List<PeerAssetManifest.Resource> resources=new ArrayList<>();long total=0;
        try {
            for(var dependency:new TreeMap<>(dependencies).entrySet()) {
                requireCurrent(current);Path source=PeerSafeFiles.existing(selectedRoot,dependency.getKey());
                long size=Files.size(source);if(size>PeerLimits.RESOURCE_BYTES || size<0 || size>PeerLimits.EXPANDED_BYTES-total)throw new IOException("Peer snapshot budget exceeded");
                Path destination=PeerSafeFiles.destination(root,dependency.getKey());var hash=PeerContentRoot.newDigest();long copied=0;
                try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS);var output=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer=new byte[PeerLimits.CHUNK_BYTES];int count;
                    while((count=input.read(buffer))!=-1){requireCurrent(current);copied=Math.addExact(copied,count);if(copied>size)throw new IOException("Peer source changed during snapshot");output.write(buffer,0,count);hash.update(buffer,0,count);}
                }
                if(copied!=size)throw new IOException("Peer source changed during snapshot");
                resources.add(new PeerAssetManifest.Resource(dependency.getKey(),dependency.getValue(),size,new PeerContentRoot(HexFormat.of().formatHex(hash.digest()))));total+=size;
            }
            // Full bytes, not mtime/size fingerprints: catches rewrites preserving file metadata.
            for(var resource:resources) {
                requireCurrent(current);Path source=PeerSafeFiles.existing(selectedRoot,resource.path());var hash=PeerContentRoot.newDigest();long read=0;
                try(var input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)) {
                    byte[] buffer=new byte[PeerLimits.CHUNK_BYTES];int count;
                    while((count=input.read(buffer))!=-1){requireCurrent(current);read=Math.addExact(read,count);if(read>resource.byteSize())throw new IOException("Peer source changed during snapshot");hash.update(buffer,0,count);}
                }
                if(read!=resource.byteSize() || !MessageDigest.isEqual(hash.digest(),resource.digest().bytes()))throw new IOException("Peer source changed during snapshot");
            }
            var manifest=new PeerAssetManifest(1,entrypoint,resources);requireCurrent(current);return new PeerAssetSnapshot(stagingParent,root,manifest);
        } catch(IOException|RuntimeException e){PeerSafeFiles.deletePrivateTree(stagingParent,root);throw e;}
    }
    private static void requireCurrent(BooleanSupplier current)throws IOException{if(!current.getAsBoolean())throw new IOException("Peer snapshot cancelled");}
}
