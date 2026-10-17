package com.micaftic.morpher.peer;

import java.io.*;

/** Resume range requests contain no paths and must match the granted source and transfer specification. */
public final class PeerResumeState {
    private PeerResumeState(){}
    public static byte[] encode(PeerAssetManifest manifest,String profile,int chunk,long[] offsets)throws IOException {
        check(manifest,chunk,offsets);
        return PeerRoomCodec.encode(out->{out.write(manifest.contentRoot().bytes());out.writeInt(manifest.schema());PeerRoomCodec.text(out,profile,64);out.writeInt(chunk);out.writeInt(offsets.length);for(long offset:offsets)out.writeLong(offset);});
    }
    public static long[] decode(byte[] payload,PeerAssetManifest manifest,String profile,int chunk)throws IOException {
        return PeerRoomCodec.decode(payload,in->{
            byte[] root=new byte[32];in.readFully(root);
            if(!java.security.MessageDigest.isEqual(root,manifest.contentRoot().bytes()) || in.readInt()!=manifest.schema()
                    || !PeerRoomCodec.text(in,64).equals(profile) || in.readInt()!=chunk || in.readInt()!=manifest.resources().size())throw new IOException("Resume specification mismatch");
            long[] offsets=new long[manifest.resources().size()];for(int i=0;i<offsets.length;i++)offsets[i]=in.readLong();check(manifest,chunk,offsets);return offsets;
        });
    }
    private static void check(PeerAssetManifest manifest,int chunk,long[] offsets)throws IOException {
        if(chunk<16384 || chunk>PeerLimits.CHUNK_BYTES || Integer.bitCount(chunk)!=1 || offsets.length!=manifest.resources().size())throw new IOException("Invalid resume specification");
        for(int i=0;i<offsets.length;i++)if(offsets[i]<0 || offsets[i]>manifest.resources().get(i).byteSize()
                || offsets[i]!=manifest.resources().get(i).byteSize() && offsets[i]%chunk!=0)throw new IOException("Invalid resume range");
    }
}
