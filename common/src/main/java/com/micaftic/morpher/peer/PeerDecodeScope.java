package com.micaftic.morpher.peer;

/** Strict limits belong to the receiving Peer task; legacy/local/Cloud tasks keep their existing policy. */
public final class PeerDecodeScope implements AutoCloseable {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Integer> READS = new ThreadLocal<>();
    private final Boolean previous;
    private final Integer previousReads;
    private static final ThreadLocal<PeerDecodeScope> CURRENT=new ThreadLocal<>();
    private final PeerDecodeScope parent;
    private final java.util.List<PeerResourceBudget.Lease> reservations=new java.util.ArrayList<>();
    private boolean closed;
    private long charged,reserved;
    public PeerDecodeScope() { previous = ACTIVE.get(); previousReads = READS.get();parent=CURRENT.get(); CURRENT.set(this);ACTIVE.set(true); READS.set(0); }
    public static boolean active() { return Boolean.TRUE.equals(ACTIVE.get()); }
    public static void expanded(long bytes) {
        if(active() && (bytes < 0 || bytes > PeerLimits.RESOURCE_BYTES)) throw new IllegalArgumentException("Peer decompression exceeds budget");
    }
    public static void scalar(int value) {
        if(active() && (value < 0 || value > 1_048_576)) throw new IllegalArgumentException("Peer binary scalar exceeds budget");
        read();
    }
    public static void read() { if(active()) { int reads = READS.get() + 1; READS.set(reads); if(reads > 262144) throw new IllegalArgumentException("Peer binary object budget exceeded");allocate(192); } }
    public static void allocate(long bytes) {
        var scope=CURRENT.get();if(scope==null)return;
        try{
            long next=Math.addExact(scope.charged,bytes);
            if(next>scope.reserved){long added=Math.max(65536,next-scope.reserved);scope.reservations.add(PeerResourceBudget.DECODE.reserve(added));scope.reserved+=added;}
            scope.charged=next;
        }
        catch(java.io.IOException exceeded){throw new IllegalArgumentException("Peer decode reservation exceeded",exceeded);}
    }
    public static void array(int size, int remaining) {
        if(active() && (size < 0 || size > remaining || size > PeerLimits.RESOURCE_BYTES)) throw new IllegalArgumentException("Peer binary array exceeds payload");
        allocate(size);
    }
    public static byte[] boundedBytes(java.io.InputStream input,int limit)throws java.io.IOException {
        if(!active()){byte[] bytes=input.readNBytes(limit+1);if(bytes.length>limit)throw new java.io.IOException("Peer input exceeds budget");return bytes;}
        allocate(16384);var bytes=new java.io.ByteArrayOutputStream(8192);byte[] buffer=new byte[8192];int count;
        while((count=input.read(buffer))!=-1){if((long)bytes.size()+count>limit)throw new java.io.IOException("Peer input exceeds budget");allocate(6L*count);bytes.write(buffer,0,count);}
        return bytes.toByteArray();
    }
    @Override public void close() {
        if(closed)return;closed=true;reservations.forEach(PeerResourceBudget.Lease::close);if(parent==null)CURRENT.remove();else CURRENT.set(parent);
        if(previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        if(previousReads == null) READS.remove(); else READS.set(previousReads);
    }
}
