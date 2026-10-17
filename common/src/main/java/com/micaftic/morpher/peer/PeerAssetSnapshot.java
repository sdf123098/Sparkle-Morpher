package com.micaftic.morpher.peer;

import java.io.IOException;
import java.nio.file.Path;

/** Task-owned immutable bytes. Does not hold or close any Cloud/local runtime. */
public final class PeerAssetSnapshot implements AutoCloseable {
    private final Path stagingParent,root;
    private final PeerAssetManifest manifest;
    private boolean closed;
    private Runnable cleanup=()->{};
    PeerAssetSnapshot(Path stagingParent,Path root,PeerAssetManifest manifest){this.stagingParent=stagingParent;this.root=root;this.manifest=manifest;}
    public PeerAssetManifest manifest(){return manifest;}
    public synchronized Path file(String relative)throws IOException{if(closed)throw new IOException("Peer snapshot closed");return PeerSafeFiles.existing(root,relative);}
    public Path root(){return root;}
    synchronized void onClose(Runnable cleanup){if(closed)cleanup.run();else this.cleanup=cleanup;}
    @Override public synchronized void close()throws IOException{if(closed)return;closed=true;try{PeerSafeFiles.deletePrivateTree(stagingParent,root);}finally{cleanup.run();}}
}
