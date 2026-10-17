package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Content-addressed verified Peer bytes; never writes custom/auth/built or Cloud caches. */
public final class PeerAssetStore implements AutoCloseable {
    private static final java.lang.ref.Cleaner CLEANER=java.lang.ref.Cleaner.create();
    private final java.lang.ref.Cleaner.Cleanable ownership;
    private final AtomicBoolean closed=new AtomicBoolean();
    public record Verified(PeerAssetManifest manifest, Path root) {
        public Path entrypoint() throws IOException { return PeerSafeFiles.existing(root, manifest.entrypoint()); }
        public Path file(int index) throws IOException { return PeerSafeFiles.existing(root, manifest.resources().get(index).path()); }
    }
    private final Path assets, staging, outgoing;
    private final long budget;
    private static final Map<Path,java.lang.ref.WeakReference<Ledger>> LEDGERS=new HashMap<>();
    private static final class Ledger {
        final Map<PeerContentRoot,Integer> pins=new HashMap<>();
        final Map<Path,Integer> active=new HashMap<>();
        long reserved;int stores;
        java.nio.channels.FileChannel channel;java.nio.channels.FileLock lock;
        void acquire(Path root)throws IOException {
            if(lock!=null)return;
            var path=root.resolve(".store.lock");if(Files.exists(path,LinkOption.NOFOLLOW_LINKS)&&!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid Peer cache ownership file");
            channel=java.nio.channels.FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
            try{lock=channel.tryLock();if(lock==null)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);}
            catch(IOException|RuntimeException failure){channel.close();channel=null;throw failure;}
        }
        void releaseIfIdle(){
            if(stores!=0||!active.isEmpty()||!pins.isEmpty()||lock==null)return;
            try{lock.release();channel.close();}catch(IOException ignored){}finally{lock=null;channel=null;}
        }
    }
    private final Ledger ledger;
    public PeerAssetStore(Path root) throws IOException { this(root, 2L * 1024 * 1024 * 1024); }
    public PeerAssetStore(Path root, long budget) throws IOException {
        if (budget < 1) throw new IllegalArgumentException("Invalid Peer disk budget");
        Path base = root.toAbsolutePath().normalize();Files.createDirectories(base);base=base.toRealPath();
        assets=PeerSafeFiles.privateDirectory(base,"assets");staging=PeerSafeFiles.privateDirectory(base,"staging");outgoing=PeerSafeFiles.privateDirectory(base,"outgoing");this.budget = budget;
        synchronized(LEDGERS) {
            var reference=LEDGERS.get(base);var shared=reference==null?null:reference.get();
            ledger=shared==null?new Ledger():shared; synchronized(ledger){ledger.acquire(base);ledger.stores++;} LEDGERS.put(base,new java.lang.ref.WeakReference<>(ledger));
            LEDGERS.entrySet().removeIf(entry->entry.getValue().get()==null);
        }
        var retained=ledger;ownership=CLEANER.register(this,()->{synchronized(retained){retained.stores--;retained.releaseIfIdle();}});
        try {synchronized(ledger) { recover(staging,"receive-",false);recover(staging,"partial-",true);recover(outgoing,"snapshot-",false); }}catch(IOException|RuntimeException failure){ownership.clean();throw failure;}
    }
    @Override public void close(){if(closed.compareAndSet(false,true))ownership.clean();}
    public Lease retain(PeerContentRoot root) { synchronized(ledger){if(closed.get())throw new IllegalStateException("Peer cache closed");ledger.pins.merge(root, 1, Integer::sum); return new Lease(root);} }
    public final class Lease implements AutoCloseable {
        private final PeerContentRoot root; private final AtomicBoolean closed = new AtomicBoolean();
        private Lease(PeerContentRoot root) { this.root = root; }
        @Override public void close() { if (closed.compareAndSet(false, true)) synchronized(ledger) { ledger.pins.computeIfPresent(root, (k, v) -> v == 1 ? null : v - 1);ledger.releaseIfIdle(); } }
    }
    public Verified find(PeerContentRoot contentRoot) throws IOException {
        Path directory = assets.resolve(contentRoot.sha256()); if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!directory.toRealPath().startsWith(assets.toRealPath())) throw new IOException("Peer cache path escape");
        Path metadata = PeerSafeFiles.existing(directory, "manifest.bin");
        if (Files.size(metadata) > PeerLimits.MANIFEST_BYTES) throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        var manifest = PeerAssetManifest.decode(Files.readAllBytes(metadata));
        if (!manifest.contentRoot().equals(contentRoot)) throw new IOException("Peer cache root mismatch");
        var result = new Verified(manifest, directory.resolve("files")); verify(result, () -> true); return result;
    }
    public Receive begin(PeerAssetManifest manifest, BooleanSupplier current) throws IOException {
        if(closed.get())throw new IOException("Peer cache closed");
        if (manifest.totalBytes() > PeerLimits.SOURCE_BYTES) throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        long size = Math.addExact(manifest.totalBytes(), manifest.canonicalSize());
        Path directory = null;
        boolean admitted=false;
        try { synchronized(ledger){if(closed.get())throw new IOException("Peer cache closed");trimFor(size); directory = Files.createTempDirectory(staging, "receive-");ledger.active.put(directory,1);ledger.reserved=Math.addExact(ledger.reserved,size);admitted=true;}
            return new Receive(manifest, directory, size, current); }
        catch(IOException | RuntimeException e) {
            if(directory != null) try { PeerSafeFiles.deletePrivateTree(staging, directory); } catch(IOException cleanup) { e.addSuppressed(cleanup); }
            synchronized(ledger) { if(admitted){ledger.reserved -= size;ledger.active.remove(directory);ledger.releaseIfIdle();} } throw e;
        }
    }
    public Verified copy(PeerAssetSnapshot snapshot, BooleanSupplier current) throws IOException {
        try(var job = begin(snapshot.manifest(), current)) {
            byte[] buffer = new byte[PeerLimits.CHUNK_BYTES];
            for(int i = 0; i < snapshot.manifest().resources().size(); i++) {
                try(var input = Files.newInputStream(snapshot.file(snapshot.manifest().resources().get(i).path()))) {
                    int count; long offset = 0;
                    while((count = input.read(buffer)) != -1) { job.append(i, offset, Arrays.copyOf(buffer, count)); offset += count; }
                }
                job.finishFile(i);
            }
            return job.commit();
        }
    }
    public final class Receive implements AutoCloseable {
        private final PeerAssetManifest manifest; private final Path directory, files; private final long reservation;
        private final BooleanSupplier current; private int index; private long offset; private OutputStream output; private MessageDigest digest;
        private boolean closed, settled;
        private Receive(PeerAssetManifest manifest, Path directory, long reservation, BooleanSupplier current) throws IOException {
            this.manifest = manifest; this.directory = directory; this.reservation = reservation; this.current = current;
            files = Files.createDirectory(directory.resolve("files"));
        }
        private void check() throws IOException { if(closed || !current.getAsBoolean()) throw new IOException("Peer transfer cancelled"); }
        private void open(int file) throws IOException {
            check(); if(file != index || file >= manifest.resources().size()) throw new IOException("Peer file sequence mismatch");
            if(output == null) { output = Files.newOutputStream(PeerSafeFiles.destination(files, manifest.resources().get(file).path()), StandardOpenOption.CREATE_NEW); digest = PeerContentRoot.newDigest(); }
        }
        public void append(int file, long expectedOffset, byte[] data) throws IOException {
            open(file); if(data.length < 1 || data.length > PeerLimits.CHUNK_BYTES || expectedOffset != offset
                    || data.length > manifest.resources().get(file).byteSize() - offset) throw new IOException("Invalid Peer block range");
            output.write(data); digest.update(data); offset += data.length;
        }
        public void finishFile(int file) throws IOException {
            open(file); output.close(); output = null;
            var resource = manifest.resources().get(index);
            if(offset != resource.byteSize() || !MessageDigest.isEqual(digest.digest(), resource.digest().bytes())) throw new IOException("Peer file digest mismatch");
            index++; offset = 0; digest = null;
        }
        public Verified commit() throws IOException {
            check(); if(index != manifest.resources().size()) throw new IOException("Incomplete Peer asset");
            Files.write(directory.resolve("manifest.bin"), manifest.canonicalBytes(), StandardOpenOption.CREATE_NEW);
            synchronized(ledger) {
                check(); Path destination = assets.resolve(manifest.contentRoot().sha256());
                if(Files.exists(destination)) {
                    Verified existing = null;
                    try { existing = find(manifest.contentRoot()); } catch(IOException corrupt) { /* Replace only with these freshly verified bytes. */ }
                    if(existing != null) { close(); return existing; }
                    PeerSafeFiles.deletePrivateTree(assets, destination);
                }
                // Same filesystem; atomic visibility is mandatory, no partially visible fallback.
                Files.move(directory, destination, StandardCopyOption.ATOMIC_MOVE);
                ledger.reserved -= reservation;ledger.active.remove(directory);ledger.releaseIfIdle(); settled = true; closed = true;
                return new Verified(manifest, destination.resolve("files"));
            }
        }
        @Override public void close() throws IOException {
            if(closed) return; closed = true;
            try { if(output != null) output.close(); }
            finally { try { PeerSafeFiles.deletePrivateTree(staging, directory); } finally { synchronized(ledger) { if(!settled) { ledger.reserved -= reservation;ledger.active.remove(directory);ledger.releaseIfIdle(); settled = true; } } } }
        }
    }
    public static void verify(Verified asset, BooleanSupplier current) throws IOException {
        try(var buffers=PeerResourceBudget.STREAM.reserve(PeerLimits.CHUNK_BYTES)){verifyBytes(asset,current);}
    }
    private static void verifyBytes(Verified asset,BooleanSupplier current)throws IOException {
        byte[] buffer = new byte[PeerLimits.CHUNK_BYTES];
        for(int i = 0; i < asset.manifest.resources().size(); i++) {
            if(!current.getAsBoolean()) throw new IOException("Peer validation cancelled");
            var resource = asset.manifest.resources().get(i); var digest = PeerContentRoot.newDigest(); long size = 0;
            try(var input = Files.newInputStream(asset.file(i), LinkOption.NOFOLLOW_LINKS)) {
                int n; while((n = input.read(buffer)) != -1) { if(!current.getAsBoolean()) throw new IOException("Peer validation cancelled"); size += n;
                    if(size > resource.byteSize()) throw new IOException("Peer cache size mismatch"); digest.update(buffer, 0, n); }
            }
            if(size != resource.byteSize() || !MessageDigest.isEqual(digest.digest(), resource.digest().bytes())) throw new IOException("Peer cache integrity mismatch");
        }
    }
    private void trimFor(long incoming) throws IOException {
        if(incoming > budget) throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        List<Path> entries; try(var stream = Files.list(assets)) { entries = stream.filter(p -> p.getFileName().toString().matches("[0-9a-f]{64}")).limit(4097).toList(); if(entries.size()>4096)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED); }
        long bytes = privateBytes(assets); Map<Path, Long> sizes = new LinkedHashMap<>();
        for(Path path : entries) { long size; try(var files = Files.walk(path)) { size = files.limit(65537).filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).mapToLong(p -> { try { return Files.size(p); } catch(IOException e) { throw new UncheckedIOException(e); } }).sum(); }
            sizes.put(path, size); }
        bytes=Math.addExact(bytes,privateBytes(staging));bytes=Math.addExact(bytes,privateBytes(outgoing));
        for(var entry : sizes.entrySet()) { if(bytes + ledger.reserved + incoming <= budget) break;
            if(ledger.pins.containsKey(new PeerContentRoot(entry.getKey().getFileName().toString()))) continue;
            PeerSafeFiles.deletePrivateTree(assets, entry.getKey()); bytes -= entry.getValue(); }
        if(bytes + ledger.reserved + incoming > budget) throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
    }
    private long privateBytes(Path root)throws IOException {
        long bytes=0;
        try(var paths=Files.walk(root)) {
            var iterator=paths.iterator();int count=0;
            while(iterator.hasNext()){var path=iterator.next();if(++count>65536)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)
                    && ledger.active.keySet().stream().noneMatch(path::startsWith))bytes=Math.addExact(bytes,Files.size(path));}
        }
        return bytes;
    }
    private void recover(Path parent,String prefix,boolean resumable)throws IOException {
        try(var paths=Files.list(parent)) {
            var entries=paths.limit(4097).toList();if(entries.size()>4096)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
            for(Path path:entries) if(path.getFileName().toString().startsWith(prefix)
                    && ledger.active.keySet().stream().noneMatch(path::startsWith)
                    && (!resumable || Files.getLastModifiedTime(path,LinkOption.NOFOLLOW_LINKS).toMillis()<System.currentTimeMillis()-86_400_000L))
                PeerSafeFiles.deletePrivateTree(parent,path);
        }
    }
    Path partialDirectory(PeerAssetManifest manifest,String profile,int chunk)throws IOException {
        String spec=manifest.schema()+"/"+profile+"/"+chunk;
        return staging.resolve("partial-"+manifest.contentRoot().sha256()+"-"+PeerContentRoot.digest(spec.getBytes(java.nio.charset.StandardCharsets.UTF_8)).sha256());
    }
    Transient reserveTransient(Path path,long bytes,boolean exclusive)throws IOException {
        if(closed.get())throw new IOException("Peer cache closed");
        path=path.toAbsolutePath().normalize();if(!path.startsWith(staging) && !path.startsWith(outgoing))throw new IOException("Invalid Peer reservation root");
        synchronized(ledger){
            if(closed.get())throw new IOException("Peer cache closed");
            if(exclusive && ledger.active.containsKey(path))throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);
            ledger.active.merge(path,1,Integer::sum);
            try {trimFor(bytes);ledger.reserved=Math.addExact(ledger.reserved,bytes);return new Transient(path,bytes);}
            catch(IOException|RuntimeException error){ledger.active.computeIfPresent(path,(k,n)->n==1?null:n-1);throw error;}
        }
    }
    final class Transient implements AutoCloseable {
        final Path path;final long bytes;boolean closed;
        Transient(Path path,long bytes){this.path=path;this.bytes=bytes;}
        public void close(){synchronized(ledger){if(closed)return;closed=true;ledger.reserved-=bytes;ledger.active.computeIfPresent(path,(k,n)->n==1?null:n-1);ledger.releaseIfIdle();}}
    }
    public PeerAssetSnapshot freeze(LocalShareSourceResolver.Selection selection,BooleanSupplier current)throws IOException {
        long bytes=PeerLimits.MANIFEST_BYTES;
        for(String path:selection.resources().keySet())bytes=Math.addExact(bytes,Files.size(PeerSafeFiles.existing(selection.root(),path)));
        Path directory=outgoing.resolve("snapshot-reserved-"+UUID.randomUUID());var reservation=reserveTransient(directory,bytes,true);
        try {Files.createDirectory(directory);var snapshot=LocalShareSourceResolver.freeze(selection,directory,current);
            snapshot.onClose(()->{try{PeerSafeFiles.deletePrivateTree(outgoing,directory);}catch(IOException failure){throw new UncheckedIOException(failure);}finally{reservation.close();}});return snapshot;}
        catch(IOException|RuntimeException error){try{PeerSafeFiles.deletePrivateTree(outgoing,directory);}catch(IOException cleanup){error.addSuppressed(cleanup);}finally{reservation.close();}throw error;}
    }
    public PeerAssetStore.Verified commitPartial(PeerAssetManifest manifest,Path files,BooleanSupplier current)throws IOException {
        try(var job=begin(manifest,current)) {
            byte[] buffer=new byte[PeerLimits.CHUNK_BYTES];
            for(int i=0;i<manifest.resources().size();i++) {
                long offset=0;
                try(var input=Files.newInputStream(PeerSafeFiles.existing(files,manifest.resources().get(i).path()),LinkOption.NOFOLLOW_LINKS)) {
                    int count;while((count=input.read(buffer))!=-1){job.append(i,offset,Arrays.copyOf(buffer,count));offset+=count;}
                }
                job.finishFile(i);
            }
            return job.commit();
        }
    }
}
