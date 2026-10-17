package com.micaftic.morpher.peer;

import java.io.*;
import java.time.Instant;
import java.util.*;

/** Blocking core API used on an owned background task, never the Minecraft/render thread. */
public final class PeerRoomClient implements AutoCloseable {
    private final PeerDialer dialer;
    private final PeerDialer.Connection connection;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final PeerSession session;
    private final byte[] assetTicket;
    private final PeerCapabilities capabilities;
    private final Map<UUID,Long> knownMotions=new HashMap<>();
    private PeerRoomCoordinator.Snapshot snapshot;
    private long requests;
    private volatile boolean closed;
    private final PeerInvitation invitation;
    private final Set<PeerTlsTransport> assetTransfers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ScheduledExecutorService deadlines = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "SPM-Peer-Asset-Deadline"); thread.setDaemon(true); return thread;
    });
    private PeerRoomClient(PeerDialer dialer,PeerDialer.Connection connection,UUID player,PeerInvitation invite,PeerCapabilities offered)throws IOException {
        this.dialer=dialer;this.connection=connection;input=new DataInputStream(connection.socket().getInputStream());output=new DataOutputStream(connection.socket().getOutputStream());
        invitation = invite;
        send(PeerFrame.Type.HELLO,offered.encode());long request=send(PeerFrame.Type.AUTH,PeerRoomCodec.encode(out->{PeerRoomCodec.uuid(out,player);out.write(invite.secret());}));
        var welcome=receive(PeerFrame.Type.WELCOME,request);
        record Welcome(PeerSession session,byte[] ticket,PeerCapabilities capabilities){}
        var decoded=PeerRoomCodec.decode(welcome.payload(),in->{var s=PeerRoomCodec.session(in);byte[] ticket=new byte[32];in.readFully(ticket);return new Welcome(s,ticket,PeerCapabilities.decode(in.readNBytes(in.available())));});
        session=decoded.session;assetTicket=decoded.ticket;capabilities=decoded.capabilities;
        if(!capabilities.equals(offered.intersect(capabilities))) throw new IOException("Server raised negotiated limits");
        if(!session.roomId().equals(invite.roomId()) || !session.minecraftUuid().equals(player))throw new IOException("Room binding mismatch");
        install(receive(PeerFrame.Type.ROSTER_SNAPSHOT,request));
    }
    public static PeerRoomClient join(PeerInvitation invite,UUID player,int timeoutMs)throws IOException {
        return join(invite,player,timeoutMs,PeerCapabilities.current());
    }
    public static PeerRoomClient join(PeerInvitation invite,UUID player,int timeoutMs,PeerCapabilities offered)throws IOException {
        if(!invite.validAt(Instant.now()))throw new IOException("Expired Peer invitation");
        var dialer=new PeerDialer();PeerDialer.Connection connection=null;
        try{connection=dialer.connect(invite,timeoutMs);return new PeerRoomClient(dialer,connection,player,invite,offered);}
        catch(IOException|RuntimeException e){if(connection!=null)connection.close();dialer.close();throw e;}
    }
    public PeerSession session(){return session;}
    public PeerCapabilities capabilities(){return capabilities;}
    public synchronized PeerRoomCoordinator.Snapshot snapshot(){return snapshot;}
    public synchronized void publish(PeerAppearance appearance)throws IOException {
        long id=send(PeerFrame.Type.PUBLISH_APPEARANCE,PeerRoomCodec.encode(out->PeerRoomCodec.appearance(out,appearance)));
        receive(PeerFrame.Type.APPEARANCE_ACCEPTED,id);
    }
    public synchronized PeerRoomCoordinator.Snapshot poll()throws IOException {
        long id=send(PeerFrame.Type.PING,new byte[0]);install(receive(PeerFrame.Type.ROSTER_SNAPSHOT,id));return snapshot;
    }
    public synchronized boolean publishMotion(PeerMotion motion)throws IOException {
        if(!capabilities.motion())return false;
        long id=send(PeerFrame.Type.MOTION_DELTA,motion.encode());byte[] ack=receive(PeerFrame.Type.ACK,id).payload();
        if(ack.length!=1||ack[0]!=0&&ack[0]!=1)throw new IOException("Invalid motion ACK");return ack[0]==1;
    }
    public synchronized List<PeerMotionReceipt> motions()throws IOException {
        if(!capabilities.motion())return List.of();
        Set<UUID> active=new HashSet<>();snapshot.members().forEach(member->active.add(member.session().memberId()));knownMotions.keySet().retainAll(active);
        var result=new ArrayList<PeerMotionReceipt>();int cursor=0,pages=0;
        do {
            long began=System.nanoTime();long id=send(PeerFrame.Type.MOTION_QUERY,PeerRoomCodec.motionQuery(new PeerRoomCodec.MotionQuery(cursor,knownMotions)));
            var page=PeerRoomCodec.motionPage(receive(PeerFrame.Type.MOTION_SNAPSHOT,id).payload());
            long rtt=Math.max(0,(System.nanoTime()-began)/1_000_000L);
            for(var receipt:page.receipts()) {
                if(!receipt.owner().roomId().equals(session.roomId())||!receipt.owner().epoch().equals(session.epoch()))throw new IOException("Motion room binding mismatch");
                knownMotions.merge(receipt.owner().memberId(),receipt.motion().sequence(),Math::max);
                long age=receipt.transitAgeMillis()+rtt;if(age<receipt.motion().ttlMillis())result.add(new PeerMotionReceipt(receipt.owner(),receipt.motion(),age));
            }
            if(page.next()!=-1&&page.next()<=cursor)throw new IOException("Motion page did not advance");cursor=page.next();
            if(++pages>8)throw new IOException("Too many motion pages");
        }while(cursor!=-1);
        return List.copyOf(result);
    }
    public synchronized boolean observe(PeerObservation.Report report) throws IOException {
        long id = send(report.state() == PeerObservation.State.FAILED ? PeerFrame.Type.APPEARANCE_FAILED : PeerFrame.Type.APPEARANCE_READY,
                PeerRoomCodec.encode(out -> PeerRoomCodec.observation(out, report)));
        byte[] ack = receive(PeerFrame.Type.ACK, id).payload();
        if(ack.length != 1 || ack[0] != 0 && ack[0] != 1) throw new IOException("Invalid observation ACK");
        return ack[0] == 1;
    }
    public synchronized List<PeerObservation.Receipt> observations() throws IOException {
        long id = send(PeerFrame.Type.OBSERVATION_QUERY, new byte[0]);
        var receipts = PeerRoomCodec.observations(receive(PeerFrame.Type.OBSERVATION_SNAPSHOT, id).payload());
        for(var receipt : receipts) if(!receipt.report().owner().equals(session) || !receipt.observer().roomId().equals(session.roomId())
                || !receipt.observer().epoch().equals(session.epoch())) throw new IOException("Observation room binding mismatch");
        return receipts;
    }
    private record AssetConnection(PeerTlsTransport transport, DataInputStream input, DataOutputStream output, java.util.concurrent.ScheduledFuture<?> deadline) implements AutoCloseable {
        @Override public void close() { deadline.cancel(false); transport.close(); }
    }
    private AssetConnection openAsset() throws IOException {
        PeerTlsTransport transport;
        try { transport = new PeerTlsTransport(PeerPinnedTls.context(invitation.pin())); }
        catch(java.security.GeneralSecurityException e) { throw new IOException("Peer TLS unavailable", e); }
        assetTransfers.add(transport);
        try {
            if(closed) throw new IOException("Peer client closed");
            var socket = transport.connect((java.net.InetSocketAddress) connection.socket().getRemoteSocketAddress(), 5000);
            socket.setSoTimeout(10000);
            var in = new DataInputStream(socket.getInputStream()); var out = new DataOutputStream(socket.getOutputStream());
            new PeerFrame(PeerFrame.Type.ASSET_AUTH, 0, PeerRoomCodec.encode(data -> { PeerRoomCodec.session(data, session); data.write(assetTicket); })).write(out);
            var ack = checkedRead(in, true); if(ack.type() != PeerFrame.Type.ACK) throw new IOException("Asset ticket rejected");
            if(closed) throw new IOException("Peer client closed");
            return new AssetConnection(transport, in, out, deadlines.schedule(transport::close, 300, java.util.concurrent.TimeUnit.SECONDS));
        } catch(IOException | RuntimeException e) { assetTransfers.remove(transport); transport.close(); throw e; }
    }
    public void upload(PeerAppearance appearance, PeerAssetStore.Verified asset, java.util.function.BooleanSupplier current) throws IOException {
        if(appearance.cleared() || !asset.manifest().contentRoot().equals(appearance.model().contentRoot())) throw new IOException("Asset offer identity mismatch");
        capabilities.requireProfile(appearance.model().importerProfile());capabilities.requireManifest(asset.manifest());
        try(var channel = openAsset()) {
            try {
                new PeerFrame(PeerFrame.Type.ASSET_OFFER, 0, PeerRoomCodec.encode(out -> PeerRoomCodec.appearance(out, appearance))).write(channel.output);
                PeerAssetWire.send(asset, channel.input, channel.output, () -> !closed && current.getAsBoolean(),capabilities,appearance.model().importerProfile());
                if(checkedRead(channel.input, true).type() != PeerFrame.Type.ACK) throw new IOException("Asset not committed by relay");
            } finally { assetTransfers.remove(channel.transport); }
        }
    }
    public PeerAssetStore.Verified download(PeerRoomCoordinator.Member owner, PeerAssetStore store, java.util.function.BooleanSupplier current) throws IOException {
        if(owner.appearance() == null || owner.appearance().cleared()) throw new IOException("Owner has no shared model");
        capabilities.requireProfile(owner.appearance().model().importerProfile());
        PeerAssetStore.Verified cached = null;
        try { cached = store.find(owner.appearance().model().contentRoot()); }
        catch(IOException corrupt) { /* Re-download under a fresh grant; cache identity alone is never trusted. */ }
        if(cached!=null)capabilities.requireManifest(cached.manifest());
        if(!current.getAsBoolean()) throw new IOException("Peer download cancelled");
        boolean have = cached != null;
        // Even hot-cache callers must obtain a current room grant; bytes are never authorization.
        try(var channel = openAsset()) {
            try {
                new PeerFrame(PeerFrame.Type.ASSET_REQUEST, 0, PeerRoomCodec.encode(out -> { PeerRoomCodec.uuid(out, owner.session().memberId()); out.writeLong(owner.appearance().revision()); out.writeBoolean(have); })).write(channel.output);
                var grant = checkedRead(channel.input, true);
                if(grant.type() != PeerFrame.Type.ASSET_GRANT) throw new IOException("Expected current asset grant");
                record Binding(PeerSession owner, PeerAppearance appearance) {}
                var binding = PeerRoomCodec.decode(grant.payload(), in -> new Binding(PeerRoomCodec.session(in), PeerRoomCodec.appearance(in)));
                if(!binding.owner.equals(owner.session()) || !Objects.equals(binding.appearance, owner.appearance()) || !current.getAsBoolean())
                    throw new IOException("Asset grant binding changed");
                if(cached != null) {PeerDiagnostics.hotCacheHits.increment();return cached;}
                return PeerAssetWire.receive(owner.appearance().model().contentRoot(), store, channel.input, channel.output, () -> !closed && current.getAsBoolean(),capabilities,owner.appearance().model().importerProfile());
            } finally { assetTransfers.remove(channel.transport); }
        }
    }
    /** No byte stream. A coalesced waiter cannot borrow another owner's grant. */
    public void authorizeDownload(PeerRoomCoordinator.Member owner,java.util.function.BooleanSupplier current)throws IOException {
        if(owner.appearance()==null || owner.appearance().cleared())throw new IOException("Owner has no shared model");
        capabilities.requireProfile(owner.appearance().model().importerProfile());
        if(closed || !current.getAsBoolean())throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);
        try(var channel=openAsset()) {
            try {
                new PeerFrame(PeerFrame.Type.ASSET_REQUEST,0,PeerRoomCodec.encode(out->{PeerRoomCodec.uuid(out,owner.session().memberId());out.writeLong(owner.appearance().revision());out.writeBoolean(true);})).write(channel.output);
                var grant=checkedRead(channel.input,true);if(grant.type()!=PeerFrame.Type.ASSET_GRANT)throw new IOException("Expected current grant");
                PeerRoomCodec.decode(grant.payload(),in->{if(!PeerRoomCodec.session(in).equals(owner.session()) || !Objects.equals(PeerRoomCodec.appearance(in),owner.appearance()))throw new IOException("Grant binding changed");return true;});
                if(closed || !current.getAsBoolean())throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);
            }finally{assetTransfers.remove(channel.transport);}
        }
    }
    private void install(PeerFrame frame)throws IOException {
        var next=PeerRoomCodec.snapshot(frame.payload());
        if(!next.roomId().equals(session.roomId()) || !next.epoch().equals(session.epoch())
                || next.members().stream().noneMatch(m->m.session().equals(session))
                || snapshot!=null && next.rosterRevision()<snapshot.rosterRevision())throw new IOException("Stale room snapshot");
        if(snapshot!=null) {
            Map<UUID,PeerRoomCoordinator.Member> previous=new HashMap<>();snapshot.members().forEach(member->previous.put(member.session().memberId(),member));
            for(var member:next.members())if(!member.equals(previous.get(member.session().memberId())))knownMotions.remove(member.session().memberId());
        }
        snapshot=next;
    }
    private long send(PeerFrame.Type type,byte[] bytes)throws IOException {
        if(closed)throw new IOException("Peer client closed");long id=++requests;new PeerFrame(type,id,bytes).write(output);return id;
    }
    private PeerFrame receive(PeerFrame.Type type,long id)throws IOException {
        var frame=checkedRead(input,false);if(frame.type()!=type || frame.requestId()!=id)throw new IOException("Unexpected room response");return frame;
    }
    static PeerFrame checkedRead(DataInput in, boolean asset) throws IOException {
        var frame = PeerFrame.read(in, asset);
        if(frame.type() == PeerFrame.Type.ERROR) throw PeerFailure.decode(frame.payload());
        return frame;
    }
    /** Close socket directly to interrupt a blocked operation; never wait for the synchronized poll lock. */
    @Override public void close(){closed=true;connection.close();dialer.close();deadlines.shutdownNow();assetTransfers.forEach(PeerTlsTransport::close);assetTransfers.clear();Arrays.fill(assetTicket,(byte)0);}
}
