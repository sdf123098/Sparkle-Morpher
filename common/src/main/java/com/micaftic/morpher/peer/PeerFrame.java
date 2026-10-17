package com.micaftic.morpher.peer;

import java.io.*;
import java.util.Objects;

/** Versioned binary envelope. All declared lengths are checked before payload allocation. */
public record PeerFrame(Type type, long requestId, byte[] payload) {
    private static final int MAGIC=0x53504D31;
    public enum Type {
        HELLO(1),AUTH(2),WELCOME(3),ROSTER_SNAPSHOT(4),MEMBER_DELTA(5),LEFT(6),
        PUBLISH_APPEARANCE(7),APPEARANCE_ACCEPTED(8),APPEARANCE_DELTA(9),APPEARANCE_READY(10),APPEARANCE_FAILED(11),
        ASSET_OFFER(12),MANIFEST(13),ASSET_GRANT(14),REVOKE(15),ASSET_REQUEST(16),BLOCK_REQUEST(17),
        ASSET_BLOCK(18),ACK(19),MOTION_SNAPSHOT(20),MOTION_DELTA(21),PING(22),PONG(23),RESYNC(24),ERROR(25),LEAVE(26),CLOSE_ROOM(27),ASSET_AUTH(28),
        OBSERVATION_QUERY(29),OBSERVATION_SNAPSHOT(30),MOTION_QUERY(31);
        private final int id;
        Type(int id){this.id=id;}
        public int id(){return id;}
        static Type fromId(int id)throws IOException {
            for(var t:values())if(t.id==id)return t;
            throw new IOException("Unknown required Peer frame type");
        }
    }
    public enum ErrorCode { INVALID_INVITE, EXPIRED_INVITE, PIN_MISMATCH, AUTH_FAILED, UNAPPROVED_UUID,
        DUPLICATE_UUID, CAPACITY, STALE_SESSION, STALE_REVISION, REVOKED, UNSUPPORTED_PROFILE,
        INVALID_MANIFEST, BUDGET_EXCEEDED, CORRUPT_BLOCK, ENDPOINT_UNREACHABLE, PRIVACY, CANCELLED,
        BIND_FAILED, TLS_FAILED, NETWORK_IO, PROTOCOL_ERROR, ASSET_NOT_READY, BUSY, IMPORT_FAILED }
    public PeerFrame {
        Objects.requireNonNull(type); Objects.requireNonNull(payload);
        if(requestId<0 || payload.length>ceiling(type))throw new IllegalArgumentException("Invalid Peer frame");
        payload=payload.clone();
    }
    @Override public byte[] payload(){return payload.clone();}
    private static int ceiling(Type type){return type==Type.ASSET_BLOCK?PeerLimits.CHUNK_BYTES+128:PeerLimits.CONTROL_BYTES;}
    public void write(DataOutput out)throws IOException {
        out.writeInt(MAGIC);out.writeByte(PeerLimits.PROTOCOL_MAJOR);out.writeByte(PeerLimits.PROTOCOL_MINOR);
        out.writeShort(type.id);out.writeLong(requestId);out.writeInt(payload.length);out.write(payload);
    }
    public static PeerFrame read(DataInput in,boolean assetChannel)throws IOException {
        if(in.readInt()!=MAGIC || in.readUnsignedByte()!=PeerLimits.PROTOCOL_MAJOR
                || in.readUnsignedByte()!=PeerLimits.PROTOCOL_MINOR)throw new IOException("Incompatible Peer protocol");
        Type type=Type.fromId(in.readUnsignedShort());long id=in.readLong();int size=in.readInt();
        if(id<0 || size<0 || size>ceiling(type) || !assetChannel && type==Type.ASSET_BLOCK)
            throw new IOException("Invalid Peer frame length or channel");
        byte[] payload=new byte[size];in.readFully(payload);return new PeerFrame(type,id,payload);
    }
    @Override public String toString(){return "PeerFrame["+type+",request="+requestId+",bytes="+payload.length+"]";}
}
