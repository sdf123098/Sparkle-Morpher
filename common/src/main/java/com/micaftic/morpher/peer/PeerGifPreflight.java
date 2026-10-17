package com.micaftic.morpher.peer;

import java.io.*;

/** Count frames from block headers before ImageIO can construct an unbounded frame index. */
final class PeerGifPreflight {
    private PeerGifPreflight(){}
    static long pixels(InputStream source)throws IOException {
        var in=new DataInputStream(source);byte[] header=new byte[6];in.readFully(header);if(header[0]!='G'||header[1]!='I'||header[2]!='F')throw new IOException("Invalid Peer GIF");
        int width=word(in),height=word(in),flags=in.readUnsignedByte();dimensions(width,height);in.skipNBytes(2);palette(in,flags);
        int frames=0;long pixels=0;
        for(;;){int block=in.readUnsignedByte();if(block==0x3b){if(frames==0)throw new IOException("Empty Peer GIF");return pixels;}
            if(block==0x21){in.readUnsignedByte();blocks(in);}
            else if(block==0x2c){in.skipNBytes(4);int w=word(in),h=word(in),packed=in.readUnsignedByte();dimensions(w,h);if(++frames>256||(pixels+=(long)w*h)>32_000_000L)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);palette(in,packed);in.readUnsignedByte();blocks(in);}
            else throw new IOException("Invalid Peer GIF block");
        }
    }
    private static int word(DataInputStream in)throws IOException {return in.readUnsignedByte()|in.readUnsignedByte()<<8;}
    private static void palette(DataInputStream in,int flags)throws IOException {if((flags&128)!=0)in.skipNBytes(3L*(1<<((flags&7)+1)));}
    private static void blocks(DataInputStream in)throws IOException {int size;while((size=in.readUnsignedByte())!=0)in.skipNBytes(size);}
    private static void dimensions(int w,int h)throws IOException {if(w<1||h<1||w>4096||h>4096)throw new PeerFailure(PeerFailure.Stage.IMPORT,PeerFrame.ErrorCode.BUDGET_EXCEEDED);}
}
