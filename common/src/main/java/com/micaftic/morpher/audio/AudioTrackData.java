package com.micaftic.morpher.audio;

import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class AudioTrackData implements AutoCloseable {

    @Nullable
    private ByteBuffer data;
    private boolean closed,storageReleased;
    private final boolean peer=com.micaftic.morpher.peer.PeerDecodeScope.active();
    private int peerReaders;
    private AutoCloseable peerReservation;
    public boolean isPeer(){return peer;}
    public synchronized void ownPeerReservation(com.micaftic.morpher.peer.PeerResidentReservation reservation){if(peer&&peerReservation==null&&!storageReleased)peerReservation=reservation.retain();}
    public synchronized PeerRead retainPeerData()throws java.io.IOException {if(!peer||closed||data==null)throw new java.io.IOException("Peer audio is unavailable");peerReaders++;return new PeerRead(data.asReadOnlyBuffer());}
    public final class PeerRead implements AutoCloseable {
        private final ByteBuffer data;private boolean released;
        private PeerRead(ByteBuffer data){this.data=data;}
        public ByteBuffer data(){return data;}
        public void close(){synchronized(AudioTrackData.this){if(released)return;released=true;peerReaders--;if(closed&&peerReaders==0)releaseStorage();}}
    }

    private final AudioCodec codec;

    private final int sampleRate;

    private final long duration;
    private final int byteSize;
    private final boolean directAllocated;

    public AudioTrackData(@Nullable ByteBuffer byteBuffer, int i, int i2, long j) {
        AudioCodec codec;
        if (byteBuffer != null) {
            com.micaftic.morpher.peer.PeerDecodeScope.allocate(byteBuffer.remaining());
            if (i == 2) {
                this.data = MemoryUtil.memAlloc(byteBuffer.remaining());
                this.directAllocated = true;
                ResourceLifecycleStats.onDirectBufferAllocated(null, byteBuffer.remaining());
            } else {
                this.data = ByteBuffer.allocate(byteBuffer.remaining());
                this.directAllocated = false;
            }
            this.byteSize = this.data.capacity();
            this.data.duplicate().put(byteBuffer.duplicate());
        } else {
            this.data = null;
            this.byteSize = 0;
            this.directAllocated = false;
        }
        switch (i) {
            case 1:
                codec = AudioCodec.VORBIS;
                break;
            case 2:
                codec = AudioCodec.OPUS;
                break;
            default:
                codec = AudioCodec.UNDEFINED;
                break;
        }
        this.codec = codec;
        this.sampleRate = i2;
        this.duration = j;
    }

    public long getDuration() {
        return this.duration;
    }

    public int getSampleRate() {
        return this.sampleRate;
    }

    public AudioCodec getCodec() {
        return this.codec;
    }

    @Nullable
    public synchronized ByteBuffer getData() {
        if(closed)return null;
        return this.data;
    }

    public int getByteSize() {
        return this.byteSize;
    }

    @Override
    public synchronized void close() {
        if(closed)return;closed=true;
        if(!peer||peerReaders==0)releaseStorage();
    }
    private void releaseStorage(){
        if(storageReleased)return;storageReleased=true;
        ByteBuffer buffer = this.data;
        this.data = null;
        if (buffer != null && this.directAllocated) {
            MemoryUtil.memFree(buffer);
            ResourceLifecycleStats.onDirectBufferFreed(null, this.byteSize);
        }
        if(peerReservation!=null){try{peerReservation.close();}catch(Exception failure){throw new IllegalStateException(failure);}finally{peerReservation=null;}}
    }
}
