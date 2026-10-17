package com.micaftic.morpher.peer;

import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Round-robin owner admission; a member cannot fill the queue or occupy both relay slots. */
public final class PeerRelayScheduler implements AutoCloseable {
    private record Key(UUID owner,UUID recipient) {}
    private final LinkedHashMap<UUID,ArrayDeque<Key>> waiting=new LinkedHashMap<>();
    private final Set<Key> active=new HashSet<>();
    private final Set<UUID> activeOwners=new HashSet<>();
    private final int slots,queueLimit,perOwner;
    private boolean closed;
    public PeerRelayScheduler() { this(2,32,2); }
    public PeerRelayScheduler(int slots,int queueLimit,int perOwner) {
        if(slots<1 || queueLimit<1 || perOwner<1) throw new IllegalArgumentException("Invalid relay limits");
        this.slots=slots;this.queueLimit=queueLimit;this.perOwner=perOwner;
    }
    public synchronized Permit acquire(UUID owner,UUID recipient,long timeoutNanos,BooleanSupplier current) throws IOException {
        var key=new Key(owner,recipient);
        int queued=waiting.values().stream().mapToInt(Deque::size).sum();
        var queue=waiting.computeIfAbsent(owner,ignored->new ArrayDeque<>());
        if(closed || queued>=queueLimit || queue.size()>=perOwner || queue.contains(key) || active.contains(key)) {
            if(queue.isEmpty())waiting.remove(owner);
            throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);
        }
        queue.addLast(key); long deadline=System.nanoTime()+timeoutNanos;
        try {
            while(true) {
                if(closed || !current.getAsBoolean()) throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.REVOKED);
                Key next=null;
                if(active.size()<slots) for(var entry:waiting.entrySet()) if(!activeOwners.contains(entry.getKey())) { next=entry.getValue().peekFirst();break; }
                if(key.equals(next)) {
                    queue.removeFirst();waiting.remove(owner);if(!queue.isEmpty())waiting.put(owner,queue);
                    active.add(key);activeOwners.add(owner);notifyAll();return new Permit(key);
                }
                long remaining=deadline-System.nanoTime();
                if(remaining<=0)throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY);
                try { wait(Math.max(1,Math.min(100,remaining/1_000_000))); }
                catch(InterruptedException cancelled) {Thread.currentThread().interrupt();throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);}
            }
        } finally {
            queue.remove(key);if(queue.isEmpty())waiting.remove(owner);notifyAll();
        }
    }
    public final class Permit implements AutoCloseable {
        private final Key key;private boolean released;
        private Permit(Key key){this.key=key;}
        @Override public void close(){synchronized(PeerRelayScheduler.this){if(released)return;released=true;active.remove(key);activeOwners.remove(key.owner);PeerRelayScheduler.this.notifyAll();}}
    }
    public synchronized int queued(){return waiting.values().stream().mapToInt(Deque::size).sum();}
    @Override public synchronized void close(){closed=true;notifyAll();}
}
