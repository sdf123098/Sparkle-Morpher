package com.micaftic.morpher.peer;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** Pure bounded state machine. Does no networking, disk I/O or callbacks while its lock is held. */
public final class PeerRoomCoordinator implements AutoCloseable {
    public record Member(PeerSession session,PeerAppearance appearance){}
    public record Snapshot(UUID roomId,UUID epoch,long rosterRevision,List<Member> members){public Snapshot{members=List.copyOf(members);}}
    public record Event(long rosterRevision,UUID memberId,Member member){}
    public record Welcome(PeerSession session,byte[] assetTicket){public Welcome{assetTicket=assetTicket.clone();}@Override public byte[] assetTicket(){return assetTicket.clone();}@Override public String toString(){return "Welcome[session="+session+",ticket=redacted]";}}
    public record Grant(UUID id,PeerSession recipient,PeerSession owner,PeerAppearance appearance,long expiresAtNanos){}
    private static final int EVENT_COUNT=256;
    private final UUID roomId,epoch=UUID.randomUUID();
    private final int capacity;
    private final byte[] secret;
    private final Instant expiry;
    private final SecureRandom random=new SecureRandom();
    private final Map<UUID,Member> members=new LinkedHashMap<>();
    private final Map<UUID,byte[]> tickets=new HashMap<>();
    private final Map<UUID,Deque<Event>> queues=new HashMap<>();
    private final Set<UUID> resync=new HashSet<>();
    private final Map<UUID,Grant> grants=new HashMap<>();
    private record ObservationKey(UUID observer, UUID owner) {}
    private final Map<ObservationKey, PeerObservation.Receipt> observations = new HashMap<>();
    private record MotionEntry(PeerSession owner,PeerMotion motion,long receivedAt) {}
    private final Map<UUID,MotionEntry> motions=new HashMap<>();
    public record MotionPage(int next,List<PeerMotionReceipt> receipts){public MotionPage{receipts=List.copyOf(receipts);}}
    private long revision;
    private boolean closed;
    public PeerRoomCoordinator(PeerInvitation invite,int capacity) {
        Objects.requireNonNull(invite);if(capacity<1 || capacity>PeerLimits.CAPACITY_CEILING)throw new IllegalArgumentException("Invalid room capacity");
        roomId=invite.roomId();secret=invite.secret();expiry=invite.expiresAt();this.capacity=capacity;
    }
    public synchronized Welcome join(byte[] credential,UUID minecraftUuid,boolean approved,Instant now) {
        if(closed || !now.isBefore(expiry) || credential==null || !MessageDigest.isEqual(secret,credential))throw new SecurityException("Room authentication failed");
        Objects.requireNonNull(minecraftUuid);
        if(!approved)throw new SecurityException("Minecraft binding requires host approval");
        if(members.values().stream().anyMatch(m->m.session.minecraftUuid().equals(minecraftUuid)))throw new IllegalStateException("Duplicate active Minecraft UUID");
        if(members.size()>=capacity)throw new IllegalStateException("Room capacity reached");
        var session=new PeerSession(roomId,epoch,UUID.randomUUID(),UUID.randomUUID(),minecraftUuid);
        var member=new Member(session,null);members.put(session.memberId(),member);queues.put(session.memberId(),new ArrayDeque<>());
        byte[] ticket=new byte[32];random.nextBytes(ticket);tickets.put(session.memberId(),ticket);emit(session.memberId(),member);
        return new Welcome(session,ticket);
    }
    public synchronized boolean authenticateAsset(PeerSession session,byte[] ticket) {
        return current(session) && ticket!=null && MessageDigest.isEqual(tickets.get(session.memberId()),ticket);
    }
    public synchronized boolean current(PeerSession session) {
        if(closed || session==null)return false;var member=members.get(session.memberId());return member!=null && member.session.equals(session);
    }
    public synchronized boolean publish(PeerSession session,PeerAppearance appearance) {
        requireCurrent(session);Objects.requireNonNull(appearance);var old=members.get(session.memberId());
        if(old.appearance!=null && appearance.revision()<=old.appearance.revision())return false;
        grants.values().removeIf(g->g.owner.equals(session));
        observations.keySet().removeIf(key -> key.owner.equals(session.memberId()));
        motions.remove(session.memberId());
        var next=new Member(session,appearance);members.put(session.memberId(),next);emit(session.memberId(),next);return true;
    }
    public synchronized Member member(UUID id) { return members.get(id); }
    public synchronized boolean publishMotion(PeerSession owner,PeerMotion motion,long now) {
        requireCurrent(owner);var appearance=members.get(owner.memberId()).appearance;
        if(appearance==null||appearance.cleared()||appearance.revision()!=motion.appearanceRevision())return false;
        var previous=motions.get(owner.memberId());if(previous!=null&&previous.motion.sequence()>=motion.sequence())return false;
        motions.put(owner.memberId(),new MotionEntry(owner,motion,now));return true;
    }
    public synchronized MotionPage motionPage(PeerSession observer,int cursor,Map<UUID,Long> known,long now) {
        requireCurrent(observer);if(cursor<0||cursor>PeerLimits.CAPACITY_CEILING||known.size()>PeerLimits.CAPACITY_CEILING)throw new IllegalArgumentException("Invalid motion cursor");
        var all=List.copyOf(members.values());var receipts=new ArrayList<PeerMotionReceipt>();int next=cursor;
        for(;next<all.size()&&receipts.size()<4;next++) {
            var member=all.get(next);var entry=motions.get(member.session.memberId());if(entry==null||member.session.equals(observer)||entry.motion.sequence()<=known.getOrDefault(member.session.memberId(),0L))continue;
            long age=Math.max(0,(now-entry.receivedAt)/1_000_000);if(age>=entry.motion.ttlMillis())continue;
            receipts.add(new PeerMotionReceipt(entry.owner,entry.motion,age));
        }
        return new MotionPage(next<all.size()?next:-1,receipts);
    }
    public synchronized boolean observe(PeerSession observer, PeerObservation.Report report) {
        requireCurrent(observer);
        var owner = members.get(report.owner().memberId());
        if(owner == null || !owner.session.equals(report.owner()) || observer.equals(owner.session)
                || owner.appearance == null || owner.appearance.cleared() || owner.appearance.revision() != report.revision()) return false;
        observations.put(new ObservationKey(observer.memberId(), owner.session.memberId()), new PeerObservation.Receipt(observer, report));
        return true;
    }
    public synchronized List<PeerObservation.Receipt> observations(PeerSession publisher) {
        requireCurrent(publisher);
        return observations.values().stream().filter(receipt -> receipt.report().owner().equals(publisher)).toList();
    }
    public synchronized List<PeerSession> retainMinecraftBindings(Set<UUID> allowed) {
        var revoked = members.values().stream().map(Member::session)
                .filter(session -> !allowed.contains(session.minecraftUuid())).toList();
        revoked.forEach(this::leave);
        return revoked;
    }
    public synchronized boolean owns(PeerSession session, PeerAppearance appearance) {
        return current(session) && Objects.equals(members.get(session.memberId()).appearance, appearance);
    }
    public synchronized Snapshot snapshot(PeerSession observer) {
        requireCurrent(observer);queues.get(observer.memberId()).clear();resync.remove(observer.memberId());
        return new Snapshot(roomId,epoch,revision,List.copyOf(members.values()));
    }
    public synchronized List<Event> drain(PeerSession observer) {
        requireCurrent(observer);if(resync.contains(observer.memberId()))throw new IllegalStateException("Room snapshot required");
        var queue=queues.get(observer.memberId());var result=List.copyOf(queue);queue.clear();return result;
    }
    public synchronized Grant grant(PeerSession recipient,PeerSession owner,long nowNanos,long ttlNanos) {
        requireCurrent(recipient);requireCurrent(owner);
        if(ttlNanos<1 || ttlNanos>60_000_000_000L)throw new IllegalArgumentException("Invalid grant TTL");
        var selection=members.get(owner.memberId()).appearance;
        if(selection==null || selection.cleared())throw new SecurityException("Owner has not shared an appearance");
        grants.values().removeIf(g->!valid(g,nowNanos));
        // At most one current grant per directed member pair, bounded by capacity squared.
        grants.values().removeIf(g->g.recipient.equals(recipient) && g.owner.equals(owner));
        var grant=new Grant(UUID.randomUUID(),recipient,owner,selection,Math.addExact(nowNanos,ttlNanos));grants.put(grant.id,grant);return grant;
    }
    public synchronized boolean authorize(UUID grantId,PeerSession recipient,PeerModelIdentity model,long nowNanos) {
        var grant=grants.get(grantId);return grant!=null && grant.recipient.equals(recipient)
                && Objects.equals(grant.appearance.model(),model) && valid(grant,nowNanos);
    }
    /** Progress renews a live lease; a revoked/expired grant can never be revived. */
    public synchronized boolean renew(UUID grantId,PeerSession recipient,PeerModelIdentity model,long nowNanos) {
        if(!authorize(grantId,recipient,model,nowNanos))return false;
        var old=grants.get(grantId);grants.put(grantId,new Grant(old.id,old.recipient,old.owner,old.appearance,Math.addExact(nowNanos,60_000_000_000L)));return true;
    }
    private boolean valid(Grant grant,long now) {
        if(now>=grant.expiresAtNanos || !current(grant.owner) || !current(grant.recipient))return false;
        return grant.appearance.equals(members.get(grant.owner.memberId()).appearance);
    }
    public synchronized void leave(PeerSession session) {
        if(!current(session))return;members.remove(session.memberId());tickets.remove(session.memberId());queues.remove(session.memberId());resync.remove(session.memberId());motions.remove(session.memberId());
        grants.values().removeIf(g->g.owner.equals(session) || g.recipient.equals(session));emit(session.memberId(),null);
        observations.keySet().removeIf(key -> key.owner.equals(session.memberId()) || key.observer.equals(session.memberId()));
    }
    private void requireCurrent(PeerSession session){if(!current(session))throw new SecurityException("Stale room session");}
    private void emit(UUID member,Member value) {
        var event=new Event(++revision,member,value);
        for(var entry:queues.entrySet()) {
            if(resync.contains(entry.getKey()))continue;
            if(entry.getValue().size()==EVENT_COUNT){entry.getValue().clear();resync.add(entry.getKey());}
            else entry.getValue().addLast(event);
        }
    }
    @Override public synchronized void close(){if(closed)return;closed=true;Arrays.fill(secret,(byte)0);tickets.values().forEach(t->Arrays.fill(t,(byte)0));tickets.clear();members.clear();queues.clear();resync.clear();grants.clear();observations.clear();motions.clear();}
}
