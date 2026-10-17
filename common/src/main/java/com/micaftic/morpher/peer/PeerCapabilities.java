package com.micaftic.morpher.peer;

import java.io.*;
import java.util.*;

/** Executable importer/transfer contract, distinct from the names reserved by the protocol. */
public record PeerCapabilities(Set<String> profiles, int chunkBytes, long sourceBytes,
        int resources, boolean resume, boolean motion) {
    public PeerCapabilities {
        profiles = Set.copyOf(profiles);
        if(profiles.isEmpty() || !PeerProfiles.ENABLED.containsAll(profiles)
                || chunkBytes < 16384 || chunkBytes > PeerLimits.CHUNK_BYTES || Integer.bitCount(chunkBytes) != 1
                || sourceBytes < 1 || sourceBytes > PeerLimits.SOURCE_BYTES || resources < 1 || resources > PeerLimits.RESOURCE_COUNT)
            throw new IllegalArgumentException("Invalid Peer capabilities");
    }
    public static PeerCapabilities current() {
        return new PeerCapabilities(PeerProfiles.ENABLED, PeerLimits.CHUNK_BYTES, PeerLimits.SOURCE_BYTES,
                PeerLimits.RESOURCE_COUNT, true, true);
    }
    public PeerCapabilities intersect(PeerCapabilities other) {
        Set<String> common = new TreeSet<>(profiles); common.retainAll(other.profiles);
        return new PeerCapabilities(common, Math.min(chunkBytes, other.chunkBytes), Math.min(sourceBytes, other.sourceBytes),
                Math.min(resources, other.resources), resume && other.resume, motion && other.motion);
    }
    public void requireProfile(String profile) throws PeerFailure {
        if(!profiles.contains(profile)) throw new PeerFailure(PeerFailure.Stage.CONTROL, PeerFrame.ErrorCode.UNSUPPORTED_PROFILE);
    }
    public void requireManifest(PeerAssetManifest manifest) throws PeerFailure {
        if(manifest.totalBytes() > sourceBytes || manifest.resources().size() > resources)
            throw new PeerFailure(PeerFailure.Stage.TRANSFER, PeerFrame.ErrorCode.BUDGET_EXCEEDED);
    }
    public byte[] encode() throws IOException {
        return PeerRoomCodec.encode(out -> {
            out.writeByte(profiles.size()); for(String profile : new TreeSet<>(profiles)) PeerRoomCodec.text(out,profile,64);
            out.writeInt(chunkBytes); out.writeLong(sourceBytes); out.writeInt(resources); out.writeBoolean(resume); out.writeBoolean(motion);
        });
    }
    public static PeerCapabilities decode(byte[] payload) throws IOException {
        return PeerRoomCodec.decode(payload,in -> {
            int count=in.readUnsignedByte(); if(count<1 || count>PeerProfiles.ENABLED.size()) throw new IOException("Invalid profile count");
            Set<String> profiles=new HashSet<>();
            for(int i=0;i<count;i++) if(!profiles.add(PeerRoomCodec.text(in,64))) throw new IOException("Duplicate profile");
            return new PeerCapabilities(profiles,in.readInt(),in.readLong(),in.readInt(),in.readBoolean(),in.readBoolean());
        });
    }
}
