package com.micaftic.morpher.peer;

import com.micaftic.morpher.audio.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import static org.junit.jupiter.api.Assertions.*;

class PeerAudioLifetimeTest {
    @Test void playingEncodedDataKeepsItsResidentLeaseAfterTheAssemblyOwnerCloses()throws Exception {
        long before=PeerResidentReservation.CPU.used();AudioTrackData track;
        try(var scope=new PeerDecodeScope()){track=new AudioTrackData(ByteBuffer.wrap(new byte[]{1,2,3}),1,8000,100);}
        var reservation=PeerResidentReservation.reserve(3,1,1,1);track.ownPeerReservation(reservation);
        try(var reading=track.retainPeerData()){track.close();reservation.close();assertNull(track.getData());assertEquals(3,reading.data().remaining());assertTrue(PeerResidentReservation.CPU.used()>before);}
        assertEquals(before,PeerResidentReservation.CPU.used());track.close();reservation.close();
    }
    @Test void actualPcmIsBoundedEvenWhenTheOggMetadataClaimsZeroDuration()throws Exception {
        AudioTrackData track;try(var scope=new PeerDecodeScope()){track=new AudioTrackData(ByteBuffer.wrap(new byte[]{1,2}),1,8000,0);}
        class Decoder implements IAudioStreamSupport {
            boolean closed;int maximum;
            public ByteBuffer read(int count){maximum=Math.max(maximum,count);return ByteBuffer.allocate(count);}
            public AudioFormat getFormat(){return new AudioFormat(8000,16,1,true,false);}
            public boolean isClosed(){return closed;}
            public void close(){closed=true;}
        }
        var decoder=new Decoder();try(var stream=PeerBoundedAudioStream.open(track,ignored->decoder)) {
            assertThrows(PeerFailure.class,()->{for(int i=0;i<1000;i++)stream.read(Integer.MAX_VALUE);});assertTrue(stream.isClosed());assertTrue(decoder.closed);assertTrue(decoder.maximum<=32768);
        }finally{track.close();}
        assertEquals(0,PeerBoundedAudioStream.BUFFERS.used());
    }
    @Test void failedDecoderCreationReleasesBufferAndSourceClaims()throws Exception {
        AudioTrackData track;try(var scope=new PeerDecodeScope()){track=new AudioTrackData(ByteBuffer.wrap(new byte[]{1}),1,8000,1);}
        assertThrows(java.io.IOException.class,()->PeerBoundedAudioStream.open(track,ignored->{throw new java.io.IOException("bad decoder");}));track.close();assertEquals(0,PeerBoundedAudioStream.BUFFERS.used());
    }
}
