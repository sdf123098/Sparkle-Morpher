package com.micaftic.morpher.peer;

import java.util.*;

/** Fence-backed frame evidence; successful GPU work does not prove unoccluded screen pixels. */
public final class PeerFrameEvidence {
    public static final PeerFrameEvidence GLOBAL=new PeerFrameEvidence();
    private static final class Entry {long version,requestedFrame=-1,completedFrame=-1,submittedFrame=-1,requestedAt,completedAt;boolean dirty,inFlight;String state="unobserved";}
    private record Stamp(String key,Entry identity,long version,boolean draw) {}
    public static final class Batch {
        private final long epoch,frame;private final List<Stamp> stamps;
        private Batch(long epoch,long frame,List<Stamp> stamps){this.epoch=epoch;this.frame=frame;this.stamps=List.copyOf(stamps);}
        public long epoch(){return epoch;}
    }
    private final Map<String,Entry> uploads=new LinkedHashMap<>(),draws=new LinkedHashMap<>();
    private long epoch;
    public synchronized long epoch(){return epoch;}
    public synchronized void uploaded(String model) {
        if(model==null||!model.startsWith("peer:"))return;
        Entry entry=entry(uploads,model,128);if(entry==null)return;
        entry.version++;entry.dirty=true;entry.inFlight=false;entry.completedFrame=-1;entry.requestedFrame=-1;entry.state="pending";
        draws.forEach((key,draw)->{if(key.endsWith("\n"+model)){draw.version++;draw.completedFrame=-1;draw.requestedFrame=-1;draw.inFlight=false;draw.dirty=false;draw.state="pending";}});
    }
    public synchronized void draw(String owner,String model) {
        if(model==null||!model.startsWith("peer:"))return;
        Entry entry=entry(draws,owner+"\n"+model,256);if(entry!=null&&entry.completedFrame<0&&!entry.inFlight)entry.dirty=true;
    }
    private Entry entry(Map<String,Entry> values,String key,int max) {
        Entry value=values.get(key);if(value==null&&values.size()<max){value=new Entry();values.put(key,value);}return value;
    }
    public synchronized Batch seal(long frame) {
        List<Stamp> stamps=new ArrayList<>();seal(uploads,false,frame,stamps);seal(draws,true,frame,stamps);
        return stamps.isEmpty()?null:new Batch(epoch,frame,stamps);
    }
    private void seal(Map<String,Entry> entries,boolean draw,long frame,List<Stamp> result) {
        entries.forEach((key,value)->{if(value.dirty&&!value.inFlight){value.dirty=false;value.inFlight=true;value.state="pending";if(value.requestedFrame<0){value.requestedFrame=frame;value.requestedAt=System.nanoTime();}result.add(new Stamp(key,value,value.version,draw));}});
    }
    public synchronized void complete(Batch batch,boolean success) {
        complete(batch,success,batch.frame);
    }
    public synchronized void complete(Batch batch,boolean success,long observedFrame) {
        if(batch.epoch!=epoch)return;
        for(var stamp:batch.stamps){Entry entry=(stamp.draw?draws:uploads).get(stamp.key);
            if(entry!=stamp.identity||entry.version!=stamp.version)continue;
            entry.inFlight=false;entry.state=success?"completed":"fence_failed";if(success){entry.completedFrame=observedFrame;entry.submittedFrame=batch.frame;entry.completedAt=System.nanoTime();}
        }
    }
    public synchronized String describe(String owner,String model) {
        Entry upload=uploads.get(model),draw=draws.get(owner+"\n"+model);
        return "texture_gpu_completed_frame="+frame(upload)+" first_render_submitted_frame="+(draw==null||draw.requestedFrame<0?"unobserved":draw.requestedFrame)
            +" gpu_completed_frame="+frame(draw)+" gpu_completed_submission_frame="+(draw==null||draw.completedFrame<0?"unobserved":draw.submittedFrame)
            +" gpu_completion_observed_ms="+(draw==null||draw.completedFrame<0?"unobserved":Math.max(0,(draw.completedAt-draw.requestedAt)/1_000_000L))
            +" fence_status="+(draw==null?"unobserved":draw.state)+" screen_pixels=requires_visual_test";
    }
    private String frame(Entry value){return value==null||value.completedFrame<0?"unobserved":Long.toString(value.completedFrame);}
    public synchronized void forget(String model){uploads.remove(model);draws.keySet().removeIf(key->key.endsWith("\n"+model));}
    public synchronized void removeOwner(String owner){draws.keySet().removeIf(key->key.startsWith(owner+"\n"));}
    public synchronized void reset(){epoch++;uploads.clear();draws.clear();}
}
