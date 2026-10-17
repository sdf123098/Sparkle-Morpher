package com.micaftic.morpher.peer;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;

/** Loader-independent room lifecycle and bounded control/asset scheduling. */
public final class PeerRoomRuntime implements AutoCloseable {
    private final ScheduledExecutorService control = Executors.newSingleThreadScheduledExecutor(r -> worker(r, "SPM-Peer-Control"));
    private final ThreadPoolExecutor transfers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(8), r -> worker(r, "SPM-Peer-Asset"));
    private final ThreadPoolExecutor completions = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32),r->worker(r,"SPM-Peer-Grant"));
    private final Semaphore controlSlots = new Semaphore(64);
    private final PeerDownloadCoalescer downloads = new PeerDownloadCoalescer();
    private final ConcurrentHashMap<PeerDownloadCoalescer.Ticket,PeerAssetStore.Lease> downloadLeases = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<PeerDownloadCoalescer.Ticket,PeerRoomCoordinator.Member> downloadOwners=new ConcurrentHashMap<>();
    private record QueuedMotion(PeerMotionReceipt receipt,long received){}
    private final Map<UUID,QueuedMotion> queuedMotions=new HashMap<>();
    private PeerRoomCoordinator.Snapshot queuedSnapshot;private List<PeerObservation.Receipt> queuedReceipts=List.of();private boolean roomEventQueued;
    private final Executor events;
    private final Consumer<PeerRoomCoordinator.Snapshot> snapshots;
    private final Consumer<String> status;
    private final Path root;
    private final AtomicLong selection = new AtomicLong();
    private volatile PeerRoomClient client;
    private volatile PeerRoomServer server;
    private volatile PeerAssetStore store;
    private volatile boolean closed;
    private volatile Instant automaticExpiry;
    private volatile List<PeerEndpoint> listenerCoverage=List.of();
    public List<PeerEndpoint> listenerCoverage(){return listenerCoverage;}
    private volatile PeerFailure terminalFailure;
    private PeerAssetStore.Lease offered;
    private record Watch(Supplier<LocalShareSourceResolver.Selection> resolver,String texture,long revision,PeerModelIdentity model) {}
    private volatile Watch watch;private volatile boolean checkingSource;private long nextSourceCheck;
    private volatile Consumer<List<PeerObservation.Receipt>> observations = ignored -> {};
    private volatile Consumer<List<PeerMotionReceipt>> motions=ignored->{};
    private volatile PeerAppearance publishedAppearance;
    private final java.util.concurrent.atomic.AtomicReference<PeerMotion> outgoingMotion=new java.util.concurrent.atomic.AtomicReference<>();
    private final ConcurrentHashMap<UUID, PeerObservation.Report> pendingObservations = new ConcurrentHashMap<>();
    public PeerRoomRuntime(Path root, Executor events, Consumer<PeerRoomCoordinator.Snapshot> snapshots, Consumer<String> status) {
        this.root = root; this.events = events; this.snapshots = snapshots; this.status = status;
    }
    private static Thread worker(Runnable task, String name) { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; }
    public PeerCapabilities capabilities() {var joined=client;return joined==null?null:joined.capabilities();}
    public int pendingDownloads() {return downloads.activeJobs();}
    public boolean connected() { return !closed && client != null; }
    public boolean isClosed() { return closed; }
    public PeerAssetStore store() { return store; }
    public PeerFailure failure() { return terminalFailure; }
    public void onObservations(Consumer<List<PeerObservation.Receipt>> receiver) { observations = Objects.requireNonNull(receiver); }
    public void onMotions(Consumer<List<PeerMotionReceipt>> receiver){motions=Objects.requireNonNull(receiver);}
    public PeerAppearance publishedAppearance(){return publishedAppearance;}
    public void motion(PeerMotion motion){if(!closed)outgoingMotion.set(motion);}
    /** Coalesce per owner; room poll drains a bounded number without an unbounded command queue. */
    public void observe(PeerObservation.Report report) { if(!closed) synchronized(pendingObservations) {if(pendingObservations.size()<PeerLimits.CAPACITY_CEILING || pendingObservations.containsKey(report.owner().memberId())) pendingObservations.put(report.owner().memberId(), report);} }
    private void report(String message) { events.execute(() -> { if(!closed) status.accept(message); }); }
    private void report(String message, BooleanSupplier current) { events.execute(() -> { if(!closed && current.getAsBoolean()) status.accept(message); }); }
    /** Rotate the whole automatic room before credentials expire; never revive its old sessions/grants. */
    public boolean automaticInvitationNeedsRenewal(Instant now) { return automaticExpiry != null && !now.isBefore(automaticExpiry.minusSeconds(60)); }
    public void host(PeerEndpoint endpoint, UUID player, Collection<UUID> approved, Consumer<PeerInvitation> invitation) {
        host(endpoint, false, player, approved, invitation);
    }
    public void hostAutomatic(UUID player, Collection<UUID> approved, Consumer<PeerInvitation> invitation) {
        host(PeerEndpoint.parse("127.0.0.1:26437"), true, player, approved, invitation);
    }
    private void host(PeerEndpoint endpoint, boolean automatic, UUID player, Collection<UUID> approved, Consumer<PeerInvitation> invitation) {
        var listeners=List.of(new PeerEndpoint(endpoint.host().contains(":")?"::":"0.0.0.0",endpoint.port()));
        host(new PeerModelEntrances(listeners,List.of(endpoint)),automatic,player,approved,invitation);
    }
    public void host(PeerModelEntrances entrances,UUID player,Collection<UUID> approved,Consumer<PeerInvitation> invitation){host(entrances,false,player,approved,invitation);}
    private void host(PeerModelEntrances entrances,boolean automatic,UUID player,Collection<UUID> approved,Consumer<PeerInvitation> invitation) {
        command(() -> {
            char[] password = UUID.randomUUID().toString().toCharArray();
            PeerFailure.Stage stage = PeerFailure.Stage.LISTEN;
            try {
                store = new PeerAssetStore(root); var identity = PeerTlsIdentity.create(password);
                byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
                var invite = new PeerInvitation(UUID.randomUUID(), entrances.advertised(), identity.pin(), Instant.now().plusSeconds(86400), secret);
                var host = new PeerRoomServer(invite, PeerLimits.CAPACITY_CEILING, identity, password, approved, store);
                if(automatic) host.requireConnectionBindings();
                server = host;
                if(closed) { host.close(); return; }
                listenerCoverage=automatic?PeerModelEntrances.automatic(host,identity.pin()):PeerModelEntrances.listen(host,entrances.listeners(),identity.pin());
                if(automatic)invite=new PeerInvitation(invite.roomId(),listenerCoverage,invite.pin(),invite.expiresAt(),invite.secret());
                var local=new PeerInvitation(invite.roomId(),listenerCoverage,invite.pin(),invite.expiresAt(),invite.secret());
                if(automatic) local = host.invitationFor(local, player);
                if(automatic) automaticExpiry = invite.expiresAt();
                stage = PeerFailure.Stage.CONNECT;
                connect(local, player);
                var advertised = invite;
                events.execute(() -> { if(!closed) invitation.accept(advertised); });
            } catch(Exception failure) { fail(stage, failure); }
            finally { Arrays.fill(password, '\0'); }
        }, () -> fail(PeerFailure.Stage.CONTROL,new PeerFailure(PeerFailure.Stage.CONTROL,PeerFrame.ErrorCode.BUSY)));
    }
    public void join(PeerInvitation invitation, UUID player) {
        command(() -> { try { store = new PeerAssetStore(root); connect(invitation, player); } catch(Exception failure) { fail(PeerFailure.Stage.CONNECT, failure); } }, () -> fail(PeerFailure.Stage.CONTROL,new PeerFailure(PeerFailure.Stage.CONTROL,PeerFrame.ErrorCode.BUSY)));
    }
    private void connect(PeerInvitation invite, UUID player) throws IOException {
        var joined = PeerRoomClient.join(invite, player, 8000); client = joined;
        if(closed) { joined.close(); return; }
        report("P2P 已连接；默认不分享模型");
        control.scheduleWithFixedDelay(() -> {
            if(closed) return;
            try {
                drainWithdrawal(joined);
                var motion=outgoingMotion.getAndSet(null);if(motion!=null&&publishedAppearance!=null&&publishedAppearance.revision()==motion.appearanceRevision())joined.publishMotion(motion);
                for(var entry : pendingObservations.entrySet()) if(pendingObservations.remove(entry.getKey(), entry.getValue())) joined.observe(entry.getValue());
                var snapshot = joined.poll(); var receipts = joined.observations();var state=joined.motions();probeSource();
                roomEvent(joined,snapshot,receipts,state);
            }
            catch(Exception failure) { fail(PeerFailure.Stage.CONTROL, failure); }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }
    /** One outstanding UI event. Merge delta motion until delivery and charge queue delay against its TTL. */
    private void roomEvent(PeerRoomClient joined,PeerRoomCoordinator.Snapshot snapshot,List<PeerObservation.Receipt> receipts,List<PeerMotionReceipt> state) {
        synchronized(queuedMotions){
            queuedSnapshot=snapshot;queuedReceipts=receipts;
            for(var receipt:state)if(queuedMotions.size()<PeerLimits.CAPACITY_CEILING||queuedMotions.containsKey(receipt.owner().memberId()))queuedMotions.put(receipt.owner().memberId(),new QueuedMotion(receipt,System.nanoTime()));
            if(roomEventQueued)return;roomEventQueued=true;
        }
        events.execute(()->{
            PeerRoomCoordinator.Snapshot latest;List<PeerObservation.Receipt> reports;List<PeerMotionReceipt> motionsToApply=new ArrayList<>();
            synchronized(queuedMotions){latest=queuedSnapshot;reports=queuedReceipts;roomEventQueued=false;
                for(var pending:queuedMotions.values()){long age=pending.receipt.transitAgeMillis()+Math.max(0,(System.nanoTime()-pending.received)/1_000_000L);if(age<pending.receipt.motion().ttlMillis())motionsToApply.add(new PeerMotionReceipt(pending.receipt.owner(),pending.receipt.motion(),age));}
                queuedMotions.clear();queuedSnapshot=null;queuedReceipts=List.of();
            }
            if(!closed&&client==joined&&latest!=null){snapshots.accept(latest);observations.accept(reports);motions.accept(List.copyOf(motionsToApply));}
        });
    }
    private void fail(PeerFailure.Stage stage, Throwable error) {
        if(closed) return;
        var failure = PeerFailure.classify(stage, error);
        terminalFailure = failure;
        System.getLogger("SPM-Peer").log(System.Logger.Level.WARNING, "Room failed: {0}", failure.getMessage());
        close();
        events.execute(() -> status.accept("P2P 连接失败：" + failure.getMessage()));
    }
    public void approve(UUID player) { var host = server; if(host != null && !closed) host.approve(player); }
    public PeerInvitation invitationFor(PeerInvitation template, UUID player) {
        var host = server;
        if(host == null || closed) throw new IllegalStateException("Room unavailable");
        host.approve(player); return host.invitationFor(template, player);
    }
    public void approveWorldPlayers(Collection<UUID> players) { var host = server; if(host != null && !closed) host.approveWorldPlayers(players); }
    public void share(Supplier<LocalShareSourceResolver.Selection> resolver, String texture) {
        watch=null;publishedAppearance=null;outgoingMotion.set(null);
        long revision = selection.incrementAndGet(); var joined = client;
        if(joined == null || closed) return;
        BooleanSupplier current = () -> !closed && joined == client && selection.get() == revision;
        assetTask(() -> {
            String stage = "来源检查";
            try {
                var selected = resolver.get();
                stage = "打包或预算检查";
                try(var frozen = store.freeze(selected, current)) {
                    var identity = new PeerModelIdentity(frozen.manifest().contentRoot(), selected.profile());
                    var retained = store.retain(identity.contentRoot());
                    try {
                        var asset = store.copy(frozen, current);
                        var appearance = new PeerAppearance(revision, identity, texture);
                        stage = "房间发布";
                        CompletableFuture<Boolean> accepted = new CompletableFuture<>();
                        command(() -> {
                            try {
                                if(!current.getAsBoolean()) { accepted.complete(false); return; }
                                joined.publish(appearance);
                                publishedAppearance=appearance;outgoingMotion.set(null);
                                if(offered != null) offered.close(); offered = retained;
                                accepted.complete(true);
                            } catch(Exception failure) { accepted.completeExceptionally(failure); }
                        }, () -> accepted.completeExceptionally(new PeerFailure(PeerFailure.Stage.CONTROL,PeerFrame.ErrorCode.BUSY)));
                        if(!accepted.get(12, TimeUnit.SECONDS)) { retained.close(); return; }
                        stage = "模型传输";
                        joined.upload(appearance, asset, current);
                        if(current.getAsBoolean())watch=new Watch(resolver,texture,revision,identity);
                        report("P2P 已分享当前本地模型与贴图", current);
                    } catch(Exception failure) { retained.close(); throw failure; }
                }
            } catch(Exception failure) {
                if(current.getAsBoolean() && selection.compareAndSet(revision, revision + 1)) {
                    System.getLogger("SPM-Peer").log(System.Logger.Level.WARNING,
                            "Local model publication failed at {0}: {1}", stage, failure.getClass().getSimpleName());
                    var reason = PeerFailure.classify(PeerFailure.Stage.TRANSFER, failure);
                    withdraw(revision + 1, "P2P 分享失败：" + stage + " (" + reason.getMessage() + ")");
                }
            }
        });
    }
    public void clear() {
        clear("P2P 已停止分享");
    }
    public void clear(String message) { withdraw(selection.incrementAndGet(), message); }
    private record Withdrawal(long revision,String message) {}
    private volatile Withdrawal withdrawal;
    private void withdraw(long revision,String message) { watch=null;publishedAppearance=null;outgoingMotion.set(null);if(client!=null&&!closed) withdrawal=new Withdrawal(revision,message); }
    private void probeSource() {
        var watched=watch;long now=System.nanoTime();
        if(watched==null||checkingSource||now<nextSourceCheck)return;
        checkingSource=true;nextSourceCheck=now+5_000_000_000L;
        assetTask(()->{
            java.util.function.BooleanSupplier current=()->!closed&&watch==watched&&selection.get()==watched.revision;
            try {
                if(!current.getAsBoolean())return;var source=watched.resolver.get();
                var identity=new PeerModelIdentity(LocalShareSourceResolver.fingerprint(source,current),source.profile());
                if(!identity.equals(watched.model)&&current.getAsBoolean())share(watched.resolver,watched.texture);
            }catch(Exception failure){if(current.getAsBoolean())withdraw(selection.incrementAndGet(),"P2P 源文件失效，已停止分享");}
            finally{checkingSource=false;}
        },()->checkingSource=false);
    }
    private void drainWithdrawal(PeerRoomClient joined)throws IOException {
        var pending=withdrawal;if(pending==null)return;
        if(selection.get()==pending.revision()) {
            joined.publish(new PeerAppearance(pending.revision(),null,null));
            publishedAppearance=null;outgoingMotion.set(null);
            if(offered!=null){offered.close();offered=null;}report(pending.message(),()->selection.get()==pending.revision());
        }
        if(withdrawal==pending)withdrawal=null;
    }
    public void download(PeerRoomCoordinator.Member member, BooleanSupplier current, BiConsumer<PeerAssetStore.Verified,PeerAssetStore.Lease> success, Runnable failure) {
        download(member, current, success, ignored -> failure.run());
    }
    public void download(PeerRoomCoordinator.Member member, BooleanSupplier current, BiConsumer<PeerAssetStore.Verified,PeerAssetStore.Lease> success, Consumer<PeerFailure> failure) {
        var joined=client;PeerDownloadCoalescer.Ticket ticket;PeerAssetStore.Lease lease;
        if(joined==null||closed){events.execute(()->failure.accept(new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED)));return;}
        lease=store.retain(member.appearance().model().contentRoot());
        try {ticket=downloads.join(member.appearance().model(),()->!closed&&joined==client&&current.getAsBoolean());}
        catch(IOException rejected){lease.close();events.execute(()->failure.accept(PeerFailure.classify(PeerFailure.Stage.TRANSFER,rejected)));return;}
        downloadLeases.put(ticket,lease);downloadOwners.put(ticket,member);
        ticket.result().whenCompleteAsync((asset,error)->{
            try {
                if(error!=null)throw new CompletionException(error);
                if(closed||!current.getAsBoolean())throw new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);
                joined.authorizeDownload(member,()->!closed&&joined==client&&current.getAsBoolean());
                events.execute(()->{
                    downloadLeases.remove(ticket,lease);downloadOwners.remove(ticket);ticket.close();
                    if(!closed&&current.getAsBoolean()) {
                        try {success.accept(asset,lease);}catch(RuntimeException rejected){lease.close();failure.accept(PeerFailure.classify(PeerFailure.Stage.APPLY,rejected));}
                    }else{lease.close();failure.accept(new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED));}
                });
            }catch(Exception rejected){downloadLeases.remove(ticket,lease);downloadOwners.remove(ticket);ticket.close();lease.close();events.execute(()->failure.accept(PeerFailure.classify(PeerFailure.Stage.TRANSFER,rejected)));}
        },completions).exceptionally(error->{downloadLeases.remove(ticket,lease);downloadOwners.remove(ticket);ticket.close();lease.close();events.execute(()->failure.accept(new PeerFailure(PeerFailure.Stage.TRANSFER,closed?PeerFrame.ErrorCode.CANCELLED:PeerFrame.ErrorCode.BUSY)));return null;});
        if(ticket.leader())assetTask(()->{
            var tried=new HashSet<PeerSession>();Exception last=new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.CANCELLED);
            while(ticket.current()) {
                var candidate=downloadOwners.entrySet().stream().filter(entry->entry.getKey().model().equals(ticket.model())&&entry.getKey().callerCurrent()&&!tried.contains(entry.getValue().session())).findFirst().orElse(null);
                if(candidate==null)break;var owner=candidate.getValue();tried.add(owner.session());
                try{ticket.complete(joined.download(owner,store,ticket::current));return;}catch(Exception rejected){last=rejected;}
            }
            ticket.fail(last);
        },()->ticket.fail(new PeerFailure(PeerFailure.Stage.TRANSFER,PeerFrame.ErrorCode.BUSY)));
    }
    private void command(Runnable task,Runnable rejected) {
        if(closed||!controlSlots.tryAcquire()){events.execute(rejected);return;}
        try {control.execute(()->{try{if(!closed)task.run();else rejected.run();}finally{controlSlots.release();}});}
        catch(RejectedExecutionException full){controlSlots.release();events.execute(rejected);}
    }
    private void assetTask(Runnable task) { assetTask(task, () -> report("P2P 资产队列已满，请稍后重试")); }
    private void assetTask(Runnable task, Runnable rejected) {
        try { transfers.execute(task); } catch(RejectedExecutionException full) { events.execute(rejected); }
    }
    @Override public synchronized void close() {
        if(closed) return; closed = true; selection.incrementAndGet();
        listenerCoverage=List.of();pendingObservations.clear();withdrawal=null;watch=null;publishedAppearance=null;outgoingMotion.set(null);downloads.close();downloadLeases.values().forEach(PeerAssetStore.Lease::close);downloadLeases.clear();downloadOwners.clear(); synchronized(queuedMotions){queuedMotions.clear();queuedSnapshot=null;queuedReceipts=List.of();}
        var joined = client; if(joined != null) joined.close(); var host = server; if(host != null) host.close();
        control.shutdownNow(); transfers.shutdownNow();completions.shutdownNow(); if(offered != null) { offered.close(); offered = null; }
        var cache=store;if(cache!=null)cache.close();
    }
}
