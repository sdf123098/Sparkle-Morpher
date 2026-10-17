package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;

/** The existing player supports Ogg Vorbis/Opus. Inspect duration/channel bounds without PCM allocation. */
public final class PeerAudioPreflight {
    private PeerAudioPreflight(){}
    public static long pcmBytes(byte[] source)throws IOException {
        int at=0,rate=0,channels=0,serial=0,pages=0;long granule=0;boolean end=false;
        while(at<source.length) {
            if(source.length-at<27 || source[at]!='O'||source[at+1]!='g'||source[at+2]!='g'||source[at+3]!='S'||source[at+4]!=0)throw new IOException("Invalid Peer Ogg page");
            var header=ByteBuffer.wrap(source,at,27).slice().order(ByteOrder.LITTLE_ENDIAN);
            int flags=Byte.toUnsignedInt(source[at+5]),id=header.getInt(14),sequence=header.getInt(18),segments=Byte.toUnsignedInt(source[at+26]);
            long position=header.getLong(6);
            if(at+27+segments>source.length || pages>0 && (id!=serial || sequence!=pages) || end || (flags&~7)!=0)throw new IOException("Unsupported chained/malformed Peer Ogg");
            int body=0;for(int i=0;i<segments;i++)body+=Byte.toUnsignedInt(source[at+27+i]);int payload=at+27+segments;
            if(body>source.length-payload)throw new IOException("Truncated Peer Ogg page");
            if(pages==0) {
                serial=id;if(sequence!=0 || (flags&2)==0 || (flags&1)!=0)throw new IOException("Missing Peer Ogg stream start");
                if(body>=30 && source[payload]==1 && new String(source,payload+1,6,StandardCharsets.US_ASCII).equals("vorbis")) {
                    channels=Byte.toUnsignedInt(source[payload+11]);rate=ByteBuffer.wrap(source,payload+12,4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                } else if(body>=19 && new String(source,payload,8,StandardCharsets.US_ASCII).equals("OpusHead")) {channels=Byte.toUnsignedInt(source[payload+9]);rate=48000;}
                else throw new IOException("Unsupported Peer audio codec");
                if(channels<1 || channels>2 || rate<8000 || rate>96000)throw new IOException("Peer audio channel/rate budget exceeded");
            }
            if(position>=0){if(position<granule)throw new IOException("Peer Ogg granule moved backwards");granule=position;}
            if(granule>300L*rate)throw new IOException("Peer audio duration exceeds 300 seconds");
            end=(flags&4)!=0;at=payload+body;if(++pages>65536)throw new IOException("Peer audio page budget exceeded");
        }
        if(!end || granule<1)throw new IOException("Peer audio has no bounded duration");
        long bytes=Math.multiplyExact(granule,channels*2L);if(bytes>64L*1024*1024)throw new IOException("Peer PCM exceeds budget");return bytes;
    }
    public static long pcmBytes(InputStream input)throws IOException {
        byte[] source=input.readNBytes(16*1024*1024+1);if(source.length>16*1024*1024)throw new IOException("Peer audio source exceeds budget");return pcmBytes(source);
    }
}
