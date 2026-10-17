package com.micaftic.morpher.peer;

import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Peer-only process token bucket. Waiting observes cancellation and never runs on the game thread. */
public final class PeerBandwidth {
    public static final PeerBandwidth GLOBAL=new PeerBandwidth(8L*1024*1024,1024*1024);
    private final long bytesPerSecond,burst;private double tokens;private long updated=System.nanoTime();
    public PeerBandwidth(long bytesPerSecond,long burst){if(bytesPerSecond<1 || burst<1)throw new IllegalArgumentException("Invalid bandwidth");this.bytesPerSecond=bytesPerSecond;this.burst=burst;tokens=burst;}
    public synchronized void acquire(int bytes,BooleanSupplier current)throws IOException {
        if(bytes<0 || bytes>burst)throw new IllegalArgumentException("Invalid transfer charge");
        while(true) {
            if(!current.getAsBoolean())throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);
            long now=System.nanoTime();tokens=Math.min(burst,tokens+(now-updated)/1e9*bytesPerSecond);updated=now;
            if(tokens>=bytes){tokens-=bytes;return;}
            try {wait(Math.max(1,Math.min(100,(long)Math.ceil((bytes-tokens)*1000/bytesPerSecond))));}
            catch(InterruptedException cancelled){Thread.currentThread().interrupt();throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);}
        }
    }
}
