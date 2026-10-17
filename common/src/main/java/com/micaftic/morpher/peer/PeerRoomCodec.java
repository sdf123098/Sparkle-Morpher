package com.micaftic.morpher.peer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Fixed-size identity fields and bounded strings/lists for V1 room control metadata. */
public final class PeerRoomCodec {
    @FunctionalInterface public interface Encoder{void write(DataOutputStream out)throws IOException;}
    @FunctionalInterface public interface Decoder<T>{T read(DataInputStream in)throws IOException;}
    private PeerRoomCodec(){}
    public record MotionQuery(int cursor,Map<UUID,Long> known){public MotionQuery{known=Map.copyOf(known);if(cursor<0||cursor>PeerLimits.CAPACITY_CEILING||known.size()>PeerLimits.CAPACITY_CEILING)throw new IllegalArgumentException("Invalid motion query");}}
    public static byte[] motionQuery(MotionQuery query)throws IOException {return encode(out->{out.writeByte(query.cursor);out.writeByte(query.known.size());for(var entry:query.known.entrySet()){uuid(out,entry.getKey());out.writeLong(entry.getValue());}});}
    public static MotionQuery motionQuery(byte[] bytes)throws IOException {return decode(bytes,in->{int cursor=in.readUnsignedByte(),count=in.readUnsignedByte();if(count>PeerLimits.CAPACITY_CEILING)throw new IOException("Too many motion cursors");Map<UUID,Long> known=new HashMap<>();for(int i=0;i<count;i++){var id=uuid(in);long seq=in.readLong();if(seq<0||known.put(id,seq)!=null)throw new IOException("Invalid motion cursor");}return new MotionQuery(cursor,known);});}
    public static byte[] motionPage(PeerRoomCoordinator.MotionPage page)throws IOException {return encode(out->{out.writeInt(page.next());out.writeByte(page.receipts().size());for(var receipt:page.receipts()){session(out,receipt.owner());out.writeLong(receipt.transitAgeMillis());byte[] payload=receipt.motion().encode();out.writeInt(payload.length);out.write(payload);}});}
    public static PeerRoomCoordinator.MotionPage motionPage(byte[] bytes)throws IOException {return decode(bytes,in->{int next=in.readInt(),count=in.readUnsignedByte();if(next < -1||next>PeerLimits.CAPACITY_CEILING||count>4)throw new IOException("Invalid motion page");var receipts=new ArrayList<PeerMotionReceipt>();var seen=new HashSet<PeerSession>();for(int i=0;i<count;i++){var owner=session(in);long age=in.readLong();int size=in.readInt();if(size<0||size>PeerMotion.BYTES||!seen.add(owner))throw new IOException("Invalid motion receipt");receipts.add(new PeerMotionReceipt(owner,PeerMotion.decode(in.readNBytes(size)),age));}return new PeerRoomCoordinator.MotionPage(next,receipts);});}
    public static byte[] encode(Encoder action)throws IOException{
        var bytes=new ByteArrayOutputStream();action.write(new DataOutputStream(bytes));
        if(bytes.size()>PeerLimits.CONTROL_BYTES)throw new IOException("Room metadata too large");return bytes.toByteArray();
    }
    public static <T>T decode(byte[] bytes,Decoder<T> action)throws IOException {
        if(bytes.length>PeerLimits.CONTROL_BYTES)throw new IOException("Room metadata too large");
        var in=new DataInputStream(new ByteArrayInputStream(bytes));
        try {T value=action.read(in);if(in.available()!=0)throw new IOException("Trailing room metadata");return value;}
        catch(IllegalArgumentException e){throw new IOException("Invalid room metadata");}
    }
    public static void uuid(DataOutput out,UUID value)throws IOException{out.writeLong(value.getMostSignificantBits());out.writeLong(value.getLeastSignificantBits());}
    public static UUID uuid(DataInput in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    public static void text(DataOutput out,String value,int ceiling)throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);if(bytes.length>ceiling)throw new IOException("Room text too large");out.writeShort(bytes.length);out.write(bytes);
    }
    public static String text(DataInput in,int ceiling)throws IOException {
        int count=in.readUnsignedShort();if(count>ceiling)throw new IOException("Room text too large");byte[] bytes=new byte[count];in.readFully(bytes);
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    public static void session(DataOutput out,PeerSession s)throws IOException{uuid(out,s.roomId());uuid(out,s.epoch());uuid(out,s.memberId());uuid(out,s.memberSession());uuid(out,s.minecraftUuid());}
    public static PeerSession session(DataInput in)throws IOException{return new PeerSession(uuid(in),uuid(in),uuid(in),uuid(in),uuid(in));}
    public static void observation(DataOutput out, PeerObservation.Report report) throws IOException {
        session(out, report.owner()); out.writeLong(report.revision()); out.writeByte(report.state().ordinal());
        out.writeByte(report.failure() == null ? 255 : report.failure().ordinal());
    }
    public static PeerObservation.Report observation(DataInput in) throws IOException {
        var owner = session(in); long revision = in.readLong(); int state = in.readUnsignedByte(), code = in.readUnsignedByte();
        if(state >= PeerObservation.State.values().length || code != 255 && code >= PeerFrame.ErrorCode.values().length)
            throw new IOException("Invalid Peer observation state");
        return new PeerObservation.Report(owner, revision, PeerObservation.State.values()[state], code == 255 ? null : PeerFrame.ErrorCode.values()[code]);
    }
    public static byte[] observations(List<PeerObservation.Receipt> receipts) throws IOException {
        return encode(out -> { out.writeByte(receipts.size()); for(var receipt : receipts) { session(out, receipt.observer()); observation(out, receipt.report()); } });
    }
    public static List<PeerObservation.Receipt> observations(byte[] bytes) throws IOException {
        return decode(bytes, in -> {
            int count = in.readUnsignedByte(); if(count > PeerLimits.CAPACITY_CEILING) throw new IOException("Too many observation receipts");
            var receipts = new ArrayList<PeerObservation.Receipt>(); var seen = new HashSet<PeerSession>();
            for(int i = 0; i < count; i++) { var observer = session(in); if(!seen.add(observer)) throw new IOException("Duplicate observation receipt"); receipts.add(new PeerObservation.Receipt(observer, observation(in))); }
            return List.copyOf(receipts);
        });
    }
    public static void appearance(DataOutput out,PeerAppearance appearance)throws IOException {
        out.writeBoolean(appearance!=null);if(appearance==null)return;
        out.writeLong(appearance.revision());out.writeBoolean(!appearance.cleared());
        if(!appearance.cleared()){out.write(appearance.model().contentRoot().bytes());text(out,appearance.model().importerProfile(),64);text(out,appearance.textureVariant(),1024);}
    }
    public static PeerAppearance appearance(DataInput in)throws IOException {
        if(!in.readBoolean())return null;long revision=in.readLong();if(!in.readBoolean())return new PeerAppearance(revision,null,null);
        byte[] hash=new byte[32];in.readFully(hash);return new PeerAppearance(revision,new PeerModelIdentity(new PeerContentRoot(HexFormat.of().formatHex(hash)),text(in,64)),text(in,1024));
    }
    public static byte[] snapshot(PeerRoomCoordinator.Snapshot snapshot)throws IOException {
        return encode(out->{uuid(out,snapshot.roomId());uuid(out,snapshot.epoch());out.writeLong(snapshot.rosterRevision());out.writeByte(snapshot.members().size());
            for(var member:snapshot.members()){session(out,member.session());appearance(out,member.appearance());}});
    }
    public static PeerRoomCoordinator.Snapshot snapshot(byte[] bytes)throws IOException {
        return decode(bytes,in->{UUID room=uuid(in),epoch=uuid(in);long revision=in.readLong();int count=in.readUnsignedByte();
            if(revision<0 || count>PeerLimits.CAPACITY_CEILING)throw new IOException("Invalid room snapshot size/revision");
            List<PeerRoomCoordinator.Member> members=new ArrayList<>();Set<UUID> ids=new HashSet<>(),uuids=new HashSet<>();
            for(int i=0;i<count;i++) {var session=session(in);
                if(!session.roomId().equals(room) || !session.epoch().equals(epoch) || !ids.add(session.memberId()) || !uuids.add(session.minecraftUuid()))throw new IOException("Invalid room snapshot identity");
                members.add(new PeerRoomCoordinator.Member(session,appearance(in)));}
            return new PeerRoomCoordinator.Snapshot(room,epoch,revision,members);});
    }
}
