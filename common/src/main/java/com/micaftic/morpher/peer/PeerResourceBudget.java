package com.micaftic.morpher.peer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Process-wide Peer-only reservations, before transfer buffers or decoded arrays are allocated. */
public final class PeerResourceBudget {
    public static final PeerResourceBudget STREAM=new PeerResourceBudget(16L*1024*1024);
    public static final PeerResourceBudget DECODE=new PeerResourceBudget(384L*1024*1024);
    private final long capacity;private long used,peak;
    public PeerResourceBudget(long capacity){if(capacity<1)throw new IllegalArgumentException("Invalid budget");this.capacity=capacity;}
    public synchronized Lease reserve(long bytes)throws IOException {
        if(bytes<0 || bytes>capacity-used)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);
        used+=bytes;peak=Math.max(peak,used);return new Lease(bytes);
    }
    public synchronized long used(){return used;}
    public synchronized long peak(){return peak;}
    public long capacity(){return capacity;}
    public final class Lease implements AutoCloseable {
        private final long bytes;private final AtomicBoolean released=new AtomicBoolean();
        private Lease(long bytes){this.bytes=bytes;}
        @Override public void close(){if(released.compareAndSet(false,true))synchronized(PeerResourceBudget.this){used-=bytes;}}
    }
}
