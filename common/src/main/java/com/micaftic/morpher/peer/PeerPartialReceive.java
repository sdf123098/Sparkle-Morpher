package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Authenticated transfer job with verified chunk checkpoints. Partials are bytes, never authorization. */
public final class PeerPartialReceive implements AutoCloseable {
    private record Chunk(int file,long offset,int bytes,PeerContentRoot hash){}
    private final PeerAssetStore store;private final PeerAssetManifest manifest;private final String profile;private final int chunk;
    private final Path directory,files;private final PeerAssetStore.Transient reservation;private final BooleanSupplier current;
    private final long[] offsets;private final List<Chunk> chunks=new ArrayList<>();private boolean keep,closed;
    public PeerPartialReceive(PeerAssetStore store,PeerAssetManifest manifest,String profile,int chunk,BooleanSupplier current)throws IOException {
        this.store=store;this.manifest=manifest;this.profile=profile;this.chunk=chunk;this.current=current;
        offsets=new long[manifest.resources().size()];directory=store.partialDirectory(manifest,profile,chunk);
        reservation=store.reserveTransient(directory,manifest.totalBytes()+2L*PeerLimits.MANIFEST_BYTES,true);
        Path built=null;
        try {
            PeerSafeFiles.privateDirectory(directory.getParent(),directory.getFileName().toString());
            built=PeerSafeFiles.privateDirectory(directory,"files");
            if(Files.exists(directory.resolve("checkpoint.bin"),LinkOption.NOFOLLOW_LINKS)) {
                try {restore(built);}catch(IOException|RuntimeException corrupt){PeerSafeFiles.deletePrivateTree(directory.getParent(),directory);Arrays.fill(offsets,0);chunks.clear();PeerSafeFiles.privateDirectory(directory.getParent(),directory.getFileName().toString());built=PeerSafeFiles.privateDirectory(directory,"files");}
            }
            files=built;
        } catch(IOException|RuntimeException error){reservation.close();throw error;}
    }
    public long[] offsets(){return offsets.clone();}
    private void check()throws IOException {if(closed || !current.getAsBoolean())throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.REVOKED);}
    private void restore(Path files)throws IOException {
        Path checkpoint=PeerSafeFiles.existing(directory,"checkpoint.bin");
        if(Files.size(checkpoint)>PeerLimits.MANIFEST_BYTES)throw new IOException("Partial metadata exceeds budget");
        try(var in=new DataInputStream(Files.newInputStream(checkpoint,LinkOption.NOFOLLOW_LINKS))) {
            if(in.readInt()!=0x53505232)throw new IOException("Invalid partial schema");
            int count=in.readInt();if(count<0 || count>PeerLimits.CONTROL_BYTES)throw new IOException("Invalid partial specification size");
            var claimed=PeerResumeState.decode(in.readNBytes(count),manifest,profile,chunk);
            int n=in.readInt();long max=(manifest.totalBytes()+chunk-1)/chunk+manifest.resources().size();
            if(n<0 || n>max || n>16384)throw new IOException("Invalid partial chunk count");
            byte[] buffer=new byte[chunk];
            for(int i=0;i<n;i++) {
                check();int file=in.readInt();long at=in.readLong();int bytes=in.readInt();byte[] hash=new byte[32];in.readFully(hash);
                if(file<0 || file>=offsets.length || at!=offsets[file] || bytes<1 || bytes!=Math.min(chunk,manifest.resources().get(file).byteSize()-at))throw new IOException("Invalid partial chunk range");
                Path path=PeerSafeFiles.existing(files,manifest.resources().get(file).path());
                try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){input.skipNBytes(at);if(input.readNBytes(buffer,0,bytes)!=bytes
                        || !java.security.MessageDigest.isEqual(hash,PeerContentRoot.digest(Arrays.copyOf(buffer,bytes)).bytes()))throw new IOException("Corrupt partial chunk");}
                offsets[file]+=bytes;chunks.add(new Chunk(file,at,bytes,new PeerContentRoot(HexFormat.of().formatHex(hash))));
            }
            if(in.read()!=-1 || !Arrays.equals(claimed,offsets))throw new IOException("Partial checkpoint range mismatch");
            for(int i=0;i<offsets.length;i++)if(offsets[i]>0 && Files.size(PeerSafeFiles.existing(files,manifest.resources().get(i).path()))!=offsets[i])throw new IOException("Partial file size mismatch");
        }
    }
    public void append(int file,long offset,byte[] bytes)throws IOException {
        check();if(file<0 || file>=offsets.length || offset!=offsets[file] || bytes.length!=Math.min(chunk,manifest.resources().get(file).byteSize()-offset) || bytes.length<1)throw new IOException("Invalid partial block");
        Path path=PeerSafeFiles.destination(files,manifest.resources().get(file).path());
        if(Files.exists(path,LinkOption.NOFOLLOW_LINKS))PeerSafeFiles.existing(files,manifest.resources().get(file).path());
        try(var out=Files.newOutputStream(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,StandardOpenOption.APPEND,LinkOption.NOFOLLOW_LINKS)){out.write(bytes);}
        chunks.add(new Chunk(file,offset,bytes.length,PeerContentRoot.digest(bytes)));offsets[file]+=bytes.length;checkpoint();
    }
    private void checkpoint()throws IOException {
        byte[] state=PeerResumeState.encode(manifest,profile,chunk,offsets);
        Path temporary=Files.createTempFile(directory,"checkpoint-",".tmp");
        try {
            try(var channel=java.nio.channels.FileChannel.open(temporary,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
                var out=new DataOutputStream(java.nio.channels.Channels.newOutputStream(channel))) {
                out.writeInt(0x53505232);out.writeInt(state.length);out.write(state);out.writeInt(chunks.size());
                for(var entry:chunks){out.writeInt(entry.file);out.writeLong(entry.offset);out.writeInt(entry.bytes);out.write(entry.hash.bytes());}out.flush();channel.force(true);
            }
            Files.move(temporary,directory.resolve("checkpoint.bin"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    public PeerAssetStore.Verified commit()throws IOException {
        check();for(int i=0;i<offsets.length;i++) {
            if(offsets[i]!=manifest.resources().get(i).byteSize())throw new IOException("Incomplete partial receive");
            if(offsets[i]==0)Files.createFile(PeerSafeFiles.destination(files,manifest.resources().get(i).path()));
        }
        return store.commitPartial(manifest,files,current);
    }
    /** Only transport interruptions preserve verified chunks; cancellation/corruption purge them. */
    public void pause()throws IOException {check();keep=!chunks.isEmpty();}
    @Override public void close()throws IOException {if(closed)return;closed=true;try{if(!keep)PeerSafeFiles.deletePrivateTree(directory.getParent(),directory);}finally{reservation.close();}}
}
