package com.micaftic.morpher.audio;

import com.micaftic.morpher.peer.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;

/** Applies actual decoded-output limits independently of untrusted Ogg granules. */
public final class PeerBoundedAudioStream implements IAudioStreamSupport {
    public static final PeerResourceBudget BUFFERS=new PeerResourceBudget(8L*1024*1024);
    @FunctionalInterface public interface Factory {IAudioStreamSupport open(ByteBuffer encoded)throws Exception;}
    private final IAudioStreamSupport delegate;private final AudioTrackData.PeerRead source;
    private final PeerResourceBudget.Lease buffers;private final long limit;private final AudioFormat format;private long decoded;private boolean closed;
    private PeerBoundedAudioStream(IAudioStreamSupport delegate,AudioTrackData.PeerRead source,PeerResourceBudget.Lease buffers)throws IOException {
        this.delegate=delegate;this.source=source;this.buffers=buffers;format=delegate.getFormat();
        if(!Float.isFinite(format.getSampleRate())||format.getSampleRate()<8000||format.getSampleRate()>96000||format.getChannels()<1||format.getChannels()>2||format.getFrameSize()<1||format.getFrameSize()>8)throw new IOException("Unsupported Peer PCM format");
        limit=Math.min(64L*1024*1024,(long)Math.ceil(format.getSampleRate())*format.getFrameSize()*300);
    }
    public static PeerBoundedAudioStream open(AudioTrackData track,Factory factory)throws IOException,javax.sound.sampled.UnsupportedAudioFileException {
        var source=track.retainPeerData();PeerResourceBudget.Lease buffers=null;IAudioStreamSupport delegate=null;
        try{buffers=BUFFERS.reserve(256*1024);delegate=factory.open(source.data());return new PeerBoundedAudioStream(delegate,source,buffers);}
        catch(Exception failure){try{if(delegate!=null)delegate.close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}finally{if(buffers!=null)buffers.close();source.close();}
            if(failure instanceof javax.sound.sampled.UnsupportedAudioFileException unsupported)throw unsupported;if(failure instanceof IOException io)throw io;throw new IOException("Peer audio failed",failure);}
    }
    public synchronized ByteBuffer read(int requested)throws IOException {
        if(requested<0)throw new IOException("Invalid Peer PCM request");if(closed||requested==0)return ByteBuffer.allocate(0);
        if(decoded>=limit){close();throw new PeerFailure(PeerFailure.Stage.RENDER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);}
        int bounded=(int)Math.min(Math.min(requested,32768),limit-decoded);
        try{var bytes=delegate.read(bounded);if(bytes.remaining()>bounded)throw new PeerFailure(PeerFailure.Stage.RENDER,PeerFrame.ErrorCode.BUDGET_EXCEEDED);decoded+=bytes.remaining();if(!bytes.hasRemaining())close();return bytes;}
        catch(IOException|RuntimeException failure){try{close();}catch(IOException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public AudioFormat getFormat(){return format;}
    public synchronized boolean isClosed(){return closed;}
    public synchronized void close()throws IOException {if(closed)return;closed=true;try{delegate.close();}finally{buffers.close();source.close();}}
}
