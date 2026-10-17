package com.micaftic.morpher.peer;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Render-thread work admission. One atomic upload may exceed the soft time/byte target. */
public final class PeerFrameWork {
    public static final PeerFrameWork PEER=new PeerFrameWork(4096,2,4L*1024*1024,2_000_000L,System::nanoTime);
    private record Job(Object key,long bytes,BooleanSupplier current,Runnable action) {}
    private final int capacity,maxJobs;private final long maxBytes,maxNanos;private final LongSupplier clock;
    private final ArrayDeque<Job> jobs=new ArrayDeque<>();
    private final Set<Object> keys=Collections.newSetFromMap(new IdentityHashMap<>());
    private final LinkedHashSet<String> admittedModels=new LinkedHashSet<>();
    private final ThreadLocal<Object> executing=new ThreadLocal<>();
    private long failures,uploadedBytes,frames,maxFrameWorkNanos,largestAtomicBytes;private int coldModels;
    public PeerFrameWork(int capacity,int maxJobs,long maxBytes,long maxNanos,LongSupplier clock) {
        if(capacity<1||maxJobs<1||maxBytes<1||maxNanos<1)throw new IllegalArgumentException();
        this.capacity=capacity;this.maxJobs=maxJobs;this.maxBytes=maxBytes;this.maxNanos=maxNanos;this.clock=Objects.requireNonNull(clock);
    }
    public synchronized boolean enqueue(Object key,long bytes,BooleanSupplier current,Runnable action) {
        Objects.requireNonNull(key);Objects.requireNonNull(current);Objects.requireNonNull(action);
        if(bytes<0)throw new IllegalArgumentException();if(keys.contains(key))return true;if(jobs.size()>=capacity)return false;
        jobs.addLast(new Job(key,bytes,current,action));keys.add(key);return true;
    }
    public boolean executing(Object key){return executing.get()==key;}
    public synchronized void cancel(Object key){jobs.removeIf(job->job.key==key);keys.remove(key);}
    public void frame() {
        long start=clock.getAsLong();
        try{drain();}finally{synchronized(this){maxFrameWorkNanos=Math.max(maxFrameWorkNanos,Math.max(0,clock.getAsLong()-start));}}
    }
    private void drain() {
        synchronized(this){coldModels=0;frames++;}
        long began=clock.getAsLong(),bytes=0;int count=0,examined=0;
        while(count<maxJobs&&examined++<capacity&&clock.getAsLong()-began<maxNanos) {
            Job job;
            synchronized(this){job=jobs.peekFirst();if(job==null)return;if(count>0&&(bytes>=maxBytes||job.bytes>maxBytes-bytes))return;jobs.removeFirst();keys.remove(job.key);}
            try {
                if(!job.current.getAsBoolean())continue;
                count++;bytes=Math.addExact(bytes,job.bytes);synchronized(this){largestAtomicBytes=Math.max(largestAtomicBytes,job.bytes);}executing.set(job.key);job.action.run();
                synchronized(this){uploadedBytes+=job.bytes;}
            } catch(RuntimeException failure){synchronized(this){failures++;}}
            finally{executing.remove();}
        }
    }
    public synchronized boolean admitModel(String model) {
        if(model==null||!model.startsWith("peer:")||admittedModels.contains(model))return true;
        if(coldModels>=1)return false;coldModels++;
        if(admittedModels.size()>=128)admittedModels.remove(admittedModels.iterator().next());admittedModels.add(model);return true;
    }
    public synchronized void forget(String model){admittedModels.remove(model);}
    public synchronized void reset(){jobs.clear();keys.clear();admittedModels.clear();coldModels=0;}
    public synchronized int queued(){return jobs.size();}
    public synchronized long failures(){return failures;}
    public synchronized String diagnostics(){return "peer_upload_queue="+jobs.size()+" upload_failures="+failures+" upload_estimated_bytes="+uploadedBytes+" rendered_frames="+frames+" max_uploads_per_frame="+maxJobs+" soft_bytes_per_frame="+maxBytes+" soft_nanos_per_frame="+maxNanos+" peak_upload_work_nanos="+maxFrameWorkNanos+" largest_atomic_upload_bytes="+largestAtomicBytes+" cold_models_per_frame=1";}
}
