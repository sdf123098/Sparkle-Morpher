package com.micaftic.morpher.client.gui;

/** Fair, bounded card work; the main preview is independent of this grid budget. */
final class CardPreviewRefreshBudget {
    static final int MAX_REFRESHES=4;
    static final long MAX_WORK_NANOS=3_000_000L;
    private static final long ANIMATION_INTERVAL=100_000_000L;
    private int cursor,start,slots,updates;
    private long workNanos;

    void beginFrame(long now,int visibleSlots){
        slots=Math.max(0,visibleSlots);updates=0;workNanos=0;
        start=slots==0?0:Math.floorMod(cursor,slots);
        cursor=slots==0?0:(start+MAX_REFRESHES)%slots;
    }
    boolean mayRefresh(int slot){
        return slots>0&&slot>=0&&slot<slots&&updates<MAX_REFRESHES&&workNanos<MAX_WORK_NANOS
                &&Math.floorMod(slot-start,slots)<Math.min(slots,MAX_REFRESHES);
    }
    void recordRefresh(long elapsedNanos){updates++;workNanos+=Math.max(0,elapsedNanos);}
    static long animationRevision(long now,int slot){
        return Math.floorDiv(now+Math.floorMod(slot,27)*16_000_000L,ANIMATION_INTERVAL);
    }
}
