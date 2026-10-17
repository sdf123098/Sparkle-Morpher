package com.micaftic.morpher.peer;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One logical room across all listeners. Only explicitly approved Minecraft UUIDs authenticate. */
public final class PeerRoomServer implements AutoCloseable {
    private final PeerRoomCoordinator room;
    private final PeerInvitation roomInvitation;
    private volatile boolean connectionBindings;
    private final PeerTlsTransport transport;
    private final Set<UUID> approved=ConcurrentHashMap.newKeySet();
    private final PeerAssetStore assets;
    private final PeerRelayScheduler relay = new PeerRelayScheduler();
    private final Map<UUID,PeerCapabilities> capabilities = new ConcurrentHashMap<>();
    private final Map<UUID, PeerAssetStore.Lease> retained = new ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService deadlines = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "SPM-Peer-Relay-Deadline"); thread.setDaemon(true); return thread;
    });
    public PeerRoomServer(PeerInvitation invitation,int capacity,PeerTlsIdentity identity,char[] password,Collection<UUID> approved)throws java.security.GeneralSecurityException {
        this(invitation, capacity, identity, password, approved, null);
    }
    public PeerRoomServer(PeerInvitation invitation,int capacity,PeerTlsIdentity identity,char[] password,Collection<UUID> approved,PeerAssetStore assets)throws java.security.GeneralSecurityException {
        if(!identity.pin().equals(invitation.pin()))throw new IllegalArgumentException("Room invitation pin differs from listener key");
        roomInvitation = invitation;
        room=new PeerRoomCoordinator(invitation,capacity);transport=new PeerTlsTransport(identity.serverContext(password));this.approved.addAll(approved);
        this.assets = assets;
    }
    public InetSocketAddress listen(InetAddress address,int port)throws IOException{return transport.listen(address,port,this::connected);}
    public synchronized void approve(UUID minecraftUuid){approved.add(Objects.requireNonNull(minecraftUuid));}
    public void requireConnectionBindings() { connectionBindings = true; }
    /** An automatic room gives each game participant a distinct secret bound to its server-side UUID. */
    public PeerInvitation invitationFor(PeerInvitation template, UUID player) {
        if(!connectionBindings || !template.roomId().equals(roomInvitation.roomId()) || !approved.contains(player))
            throw new IllegalStateException("Automatic game binding unavailable");
        return new PeerInvitation(template.roomId(), template.endpoints(), template.pin(), template.expiresAt(), bindingSecret(player));
    }
    private byte[] bindingSecret(UUID player) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            byte[] key = roomInvitation.secret();
            try { mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA256")); }
            finally { Arrays.fill(key, (byte)0); }
            return mac.doFinal(java.nio.ByteBuffer.allocate(16).putLong(player.getMostSignificantBits()).putLong(player.getLeastSignificantBits()).array());
        } catch(java.security.GeneralSecurityException unavailable) { throw new IllegalStateException("Game binding unavailable", unavailable); }
    }
    /** Atomic with control authentication; departed world bindings immediately lose sessions and asset grants. */
    public void approveWorldPlayers(Collection<UUID> players) {
        var allowed = Set.copyOf(players);
        List<PeerSession> revoked;
        synchronized(this) {
            approved.retainAll(allowed); approved.addAll(allowed);
            revoked = room.retainMinecraftBindings(allowed);
        }
        for(var session : revoked) { var lease = retained.remove(session.memberId()); if(lease != null) lease.close(); }
    }
    private synchronized PeerRoomCoordinator.Welcome authenticate(UUID player, byte[] secret) throws PeerFailure {
        if(!approved.contains(player)) throw new PeerFailure(PeerFailure.Stage.AUTH, PeerFrame.ErrorCode.UNAPPROVED_UUID);
        if(!roomInvitation.validAt(Instant.now())) throw new PeerFailure(PeerFailure.Stage.AUTH, PeerFrame.ErrorCode.EXPIRED_INVITE);
        if(connectionBindings) {
            byte[] expected = bindingSecret(player);
            boolean valid = java.security.MessageDigest.isEqual(secret, expected);
            Arrays.fill(expected, (byte)0);
            if(!valid) throw new PeerFailure(PeerFailure.Stage.AUTH, PeerFrame.ErrorCode.AUTH_FAILED);
            byte[] roomSecret = roomInvitation.secret();
            try { return join(player, roomSecret); }
            finally { Arrays.fill(roomSecret, (byte)0); }
        }
        return join(player, secret);
    }
    private PeerRoomCoordinator.Welcome join(UUID player, byte[] secret) throws PeerFailure {
        try { return room.join(secret, player, true, Instant.now()); }
        catch(SecurityException rejected) { throw new PeerFailure(PeerFailure.Stage.AUTH, PeerFrame.ErrorCode.AUTH_FAILED); }
        catch(IllegalStateException rejected) { throw new PeerFailure(PeerFailure.Stage.AUTH,
                "Duplicate active Minecraft UUID".equals(rejected.getMessage()) ? PeerFrame.ErrorCode.DUPLICATE_UUID : PeerFrame.ErrorCode.CAPACITY); }
    }
    private void connected(SSLSocket socket)throws IOException {
        var in=new DataInputStream(socket.getInputStream());var out=new DataOutputStream(socket.getOutputStream());
        var first=PeerFrame.read(in,false);PeerSession session=null;
        long requestId = first.requestId(); PeerFailure.Stage stage = PeerFailure.Stage.AUTH;
        try {
            if(first.type()==PeerFrame.Type.ASSET_AUTH) {
                stage = PeerFailure.Stage.ASSET_AUTH;
                record AssetAuth(PeerSession session,byte[] ticket){}
                var auth=PeerRoomCodec.decode(first.payload(),input->{var s=PeerRoomCodec.session(input);byte[] ticket=new byte[32];input.readFully(ticket);return new AssetAuth(s,ticket);});
                if(!room.authenticateAsset(auth.session,auth.ticket))throw new IOException("Asset session rejected");
                new PeerFrame(PeerFrame.Type.ACK,first.requestId(),new byte[0]).write(out);
                stage = PeerFailure.Stage.TRANSFER;
                if(assets != null) transfer(auth.session, socket, in, out);
                return;
            }
            if(first.type()!=PeerFrame.Type.HELLO)throw new IOException("Expected room HELLO");
            var negotiated=PeerCapabilities.current().intersect(PeerCapabilities.decode(first.payload()));
            var authFrame=PeerFrame.read(in,false);if(authFrame.type()!=PeerFrame.Type.AUTH)throw new IOException("Expected room AUTH");
            requestId = authFrame.requestId();
            record Auth(UUID player,byte[] secret){}
            var auth=PeerRoomCodec.decode(authFrame.payload(),input->{var player=PeerRoomCodec.uuid(input);byte[] secret=new byte[32];input.readFully(secret);return new Auth(player,secret);});
            PeerRoomCoordinator.Welcome welcome;
            try { welcome=authenticate(auth.player,auth.secret); } finally { Arrays.fill(auth.secret,(byte)0); }
            session=welcome.session();capabilities.put(session.memberId(),negotiated);
            new PeerFrame(PeerFrame.Type.WELCOME,authFrame.requestId(),PeerRoomCodec.encode(output->{PeerRoomCodec.session(output,welcome.session());output.write(welcome.assetTicket());output.write(negotiated.encode());})).write(out);
            new PeerFrame(PeerFrame.Type.ROSTER_SNAPSHOT,authFrame.requestId(),PeerRoomCodec.snapshot(room.snapshot(session))).write(out);
            stage = PeerFailure.Stage.CONTROL;
            var controlBudget=new PeerControlBudget(System.nanoTime());
            while(room.current(session)) {
                var frame=PeerFrame.read(in,false);
                requestId = frame.requestId();
                if(!controlBudget.admit(frame.payload().length,System.nanoTime()))throw new PeerFailure(PeerFailure.Stage.CONTROL,PeerFrame.ErrorCode.BUSY);
                switch(frame.type()) {
                    case PUBLISH_APPEARANCE -> {
                        var appearance=PeerRoomCodec.decode(frame.payload(),PeerRoomCodec::appearance);
                        if(appearance!=null && !appearance.cleared()) negotiated.requireProfile(appearance.model().importerProfile());
                        if(appearance==null || !room.publish(session,appearance))throw new IOException("Stale appearance publication");
                        var old = retained.remove(session.memberId()); if(old != null) old.close();
                        new PeerFrame(PeerFrame.Type.APPEARANCE_ACCEPTED,frame.requestId(),new byte[0]).write(out);
                    }
                    case PING,RESYNC -> new PeerFrame(PeerFrame.Type.ROSTER_SNAPSHOT,frame.requestId(),PeerRoomCodec.snapshot(room.snapshot(session))).write(out);
                    case MOTION_DELTA -> {
                        if(!negotiated.motion())throw new IOException("Peer motion was not negotiated");
                        boolean accepted=room.publishMotion(session,PeerMotion.decode(frame.payload()),System.nanoTime());
                        new PeerFrame(PeerFrame.Type.ACK,frame.requestId(),new byte[]{(byte)(accepted?1:0)}).write(out);
                    }
                    case MOTION_QUERY -> {
                        if(!negotiated.motion())throw new IOException("Peer motion was not negotiated");
                        var query=PeerRoomCodec.motionQuery(frame.payload());
                        new PeerFrame(PeerFrame.Type.MOTION_SNAPSHOT,frame.requestId(),PeerRoomCodec.motionPage(room.motionPage(session,query.cursor(),query.known(),System.nanoTime()))).write(out);
                    }
                    case APPEARANCE_READY, APPEARANCE_FAILED -> {
                        var report = PeerRoomCodec.decode(frame.payload(), PeerRoomCodec::observation);
                        if((frame.type() == PeerFrame.Type.APPEARANCE_FAILED) != (report.state() == PeerObservation.State.FAILED)) throw new IOException("Observation type mismatch");
                        boolean accepted = room.observe(session, report);
                        new PeerFrame(PeerFrame.Type.ACK, frame.requestId(), new byte[] {(byte)(accepted ? 1 : 0)}).write(out);
                    }
                    case OBSERVATION_QUERY -> new PeerFrame(PeerFrame.Type.OBSERVATION_SNAPSHOT, frame.requestId(), PeerRoomCodec.observations(room.observations(session))).write(out);
                    case LEAVE -> {return;}
                    default -> throw new IOException("Unexpected room control message");
                }
            }
        } catch(IOException|SecurityException|IllegalStateException e){
            var failure = PeerFailure.classify(stage, e);
            try { new PeerFrame(PeerFrame.Type.ERROR, requestId, failure.payload()).write(out); } catch(IOException ignored) {}
            System.getLogger("SPM-Peer").log(System.Logger.Level.DEBUG, "Room connection ended: {0}", failure.getMessage());
            throw failure;
        }
        finally {if(session!=null){capabilities.remove(session.memberId());room.leave(session);var lease=retained.remove(session.memberId());if(lease!=null)lease.close();}}
    }
    private void transfer(PeerSession session, SSLSocket socket, DataInputStream in, DataOutputStream out) throws IOException {
        var request = PeerFrame.read(in, true);
        PeerRoomCoordinator.Grant requestedGrant = null;
        var policy=capabilities.get(session.memberId());
        if(policy==null)throw new PeerFailure(PeerFailure.Stage.ASSET_AUTH,PeerFrame.ErrorCode.STALE_SESSION);
        UUID ownerId=session.memberId();
        java.util.function.BooleanSupplier authorized=()->room.current(session);
        if(request.type() == PeerFrame.Type.ASSET_REQUEST) {
            record Request(UUID owner, long revision, boolean cacheHave) {}
            var value = PeerRoomCodec.decode(request.payload(), input -> new Request(PeerRoomCodec.uuid(input), input.readLong(), input.readBoolean()));
            var owner = room.member(value.owner);
            if(owner == null || owner.appearance() == null || owner.appearance().cleared() || owner.appearance().revision() != value.revision)
                throw new PeerFailure(PeerFailure.Stage.TRANSFER, PeerFrame.ErrorCode.STALE_REVISION);
            policy.requireProfile(owner.appearance().model().importerProfile());
            ownerId=owner.session().memberId();
            requestedGrant = room.grant(session, owner.session(), System.nanoTime(), 60_000_000_000L);
            if(!room.authorize(requestedGrant.id(), session, requestedGrant.appearance().model(), System.nanoTime()))
                throw new PeerFailure(PeerFailure.Stage.TRANSFER, PeerFrame.ErrorCode.REVOKED);
            new PeerFrame(PeerFrame.Type.ASSET_GRANT, request.requestId(), PeerRoomCodec.encode(output -> {
                PeerRoomCodec.session(output, owner.session()); PeerRoomCodec.appearance(output, owner.appearance());
            })).write(out);
            var liveGrant=requestedGrant;
            authorized=()->room.authorize(liveGrant.id(),session,liveGrant.appearance().model(),System.nanoTime());
            if(value.cacheHave) return; // No byte stream or relay slot; authorization still checked above.
        }
        var permit=relay.acquire(ownerId,session.memberId(),8_000_000_000L,authorized);
        var deadline = deadlines.schedule(() -> { try { socket.close(); } catch(IOException ignored) {} }, 300, java.util.concurrent.TimeUnit.SECONDS);
        try {
            if(request.type() == PeerFrame.Type.ASSET_OFFER) {
                var appearance = PeerRoomCodec.decode(request.payload(), PeerRoomCodec::appearance);
                if(appearance == null || appearance.cleared() || !room.owns(session, appearance)) throw new IOException("Unauthorized asset offer");
                policy.requireProfile(appearance.model().importerProfile());
                PeerAssetWire.receive(appearance.model().contentRoot(), assets, in, out, () -> room.owns(session, appearance),policy,appearance.model().importerProfile());
                var lease = assets.retain(appearance.model().contentRoot());
                if(!room.owns(session, appearance)) { lease.close(); throw new IOException("Asset offer superseded"); }
                var old = retained.put(session.memberId(), lease); if(old != null) old.close();
                // Never acquire the asset-store lock under the coordinator lock: commit guards acquire them in the reverse order.
                if(!room.owns(session, appearance)) { if(retained.remove(session.memberId(), lease)) lease.close(); throw new IOException("Asset offer superseded"); }
                new PeerFrame(PeerFrame.Type.ACK, request.requestId(), new byte[0]).write(out);
            } else if(request.type() == PeerFrame.Type.ASSET_REQUEST) {
                var grant = requestedGrant;
                var model = grant.appearance().model();
                try(var lease = assets.retain(model.contentRoot())) {
                    var asset = assets.find(model.contentRoot()); if(asset == null) throw new PeerFailure(PeerFailure.Stage.TRANSFER, PeerFrame.ErrorCode.ASSET_NOT_READY);
                    PeerAssetWire.send(asset, in, out, () -> room.renew(grant.id(), session, model, System.nanoTime()),policy,model.importerProfile());
                }
            } else throw new IOException("Unknown asset operation");
        } catch(SecurityException | IllegalArgumentException e) { throw new IOException("Asset authorization failed"); }
        finally { deadline.cancel(false); permit.close(); }
    }
    @Override public void close(){relay.close();capabilities.clear();room.close();transport.close();deadlines.shutdownNow();approved.clear();retained.values().forEach(PeerAssetStore.Lease::close);retained.clear();}
}
