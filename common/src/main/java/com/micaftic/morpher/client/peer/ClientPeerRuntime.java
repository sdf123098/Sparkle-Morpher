package com.micaftic.morpher.client.peer;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.peer.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread Minecraft adapter; automatic rooms are scoped to published integrated worlds. */
public final class ClientPeerRuntime {
    private record View(PeerRoomCoordinator.Member member, Player entity) {}
    private static final Map<UUID,Player> owned = new ConcurrentHashMap<>();
    private static volatile Map<UUID,View> views = Map.of();
    private static final Map<UUID, View> pending = new HashMap<>();
    private static final Map<UUID,Long> retryAt = new HashMap<>();
    private static final Map<UUID,PeerAssetStore.Lease> leases = new HashMap<>();
    private static final Map<UUID,String> ownedModels = new ConcurrentHashMap<>();
    private static volatile Set<UUID> cloudCandidates = Set.of();
    private static PeerRoomRuntime runtime;
    private static Object world, connection;
    private static volatile long generation;
    private static String status = "P2P 未启用", invitation, lastSelection;
    private static boolean sharing;
    private static boolean hosting;
    private static Set<UUID> approvedWorldPlayers = Set.of();
    private static long nextPlayerApproval;
    private static int members;
    private static java.util.function.BooleanSupplier bootstrapAvailable = () -> false;
    private static Runnable bootstrapRequest = () -> {};
    private static Object autoConnection, advertisedServer;
    private static boolean autoSuppressed, automatic;
    private static boolean shareSuppressed;
    private static final Map<UUID, String> receiveErrors = new HashMap<>();
    private static Map<UUID, PeerRoomCoordinator.Member> roomMembers = Map.of();
    private static final Map<UUID, PeerObservation.Report> localReports = new ConcurrentHashMap<>();
    private static final Map<UUID,PeerDiagnostics.Timeline> timelines=new ConcurrentHashMap<>();
    private static List<PeerObservation.Receipt> observerReceipts = List.of();
    private static long nextBootstrap, nextAutomaticAttempt;
    private static int automaticFailures;
    private static boolean automaticRetryExhausted;
    private static final java.util.concurrent.atomic.AtomicBoolean probing=new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile PeerDialer probeDialer;
    private static Component probeStatus=Component.translatable("gui.sparkle_morpher.settings.peer.probe_idle");
    private ClientPeerRuntime() {}
    public static String status() { return status; }
    public static Component statusComponent() {
        if(!com.micaftic.morpher.core.network.NetworkAccess.state().preset().allowsPeer(false)) return Component.translatable("gui.sparkle_morpher.settings.peer.status.preset_paused");
        if(status.startsWith("P2P 连接失败：")) {
            String[] fields = status.substring("P2P 连接失败：".length()).split("/");
            if(fields.length == 2) return Component.translatable("gui.sparkle_morpher.settings.peer.connection_error",
                    Component.translatable("gui.sparkle_morpher.settings.peer.stage." + fields[0]),
                    Component.translatable("gui.sparkle_morpher.settings.peer.error." + fields[1]));
        }
        String baseStatus = status.contains(" (") ? status.substring(0, status.indexOf(" (")) : status;
        String key = switch(baseStatus) {
            case "P2P 未启用" -> "inactive";
            case "请先进入世界，并关闭隐私模式后重新连接" -> "needs_world";
            case "P2P 正在连接" -> "connecting";
            case "请输入可供同伴连接的 LAN/VPN 地址和模型端口" -> "invalid_endpoint";
            case "邀请无效或已过期" -> "invalid_invitation";
            case "已批准当前世界内玩家的 UUID（房间邀请仍须保密）" -> "approved";
            case "P2P 已退出" -> "left";
            case "P2P 已连接；默认不分享模型" -> "connected";
            case "P2P 连接失败或已断开，请检查地址、房主批准及模型端口" -> "connection_failed";
            case "P2P 已断开，请重新加入房间" -> "disconnected";
            case "P2P 已分享当前本地模型与贴图" -> "shared";
            case "当前来源无法分享，或资源超过 P2P 预算" -> "cannot_share";
            case "P2P 已停止分享" -> "withdrawn";
            case "P2P 等待局域网房主提供自动接入" -> "waiting_lan";
            case "此服务器尚未提供局域网自动 P2P" -> "lan_unavailable";
            case "P2P 已连接；当前未选择可分享的本地模型" -> "no_local_model";
            case "当前本地模型已隐藏，暂停发布" -> "model_hidden";
            case "P2P 分享失败：来源检查" -> "share_source_failed";
            case "P2P 分享失败：打包或预算检查" -> "share_package_failed";
            case "P2P 分享失败：房间发布" -> "share_publish_failed";
            case "P2P 分享失败：模型传输" -> "share_transfer_failed";
            case "P2P 资产队列已满，请稍后重试" -> "busy";
            default -> null;
        };
        return key == null ? Component.literal(status) : Component.translatable("gui.sparkle_morpher.settings.peer.status." + key);
    }
    public static String invitation() { return invitation; }
    public static boolean sharing() { return sharing; }
    public static int members() { return members; }
    public static boolean running() { return runtime != null && !runtime.isClosed(); }
    public static boolean automatic() { return automatic; }
    public static Component probeStatusComponent(){return probeStatus;}
    public static String configuredListeners(){try{return com.micaftic.morpher.config.GeneralConfig.PEER_LISTENERS.get();}catch(IllegalStateException unavailable){return "0.0.0.0:26437";}}
    public static String configuredEntrances(){try{return com.micaftic.morpher.config.GeneralConfig.PEER_ENTRANCES.get();}catch(IllegalStateException unavailable){return "";}}
    public static String listenerCoverage(){var active=runtime;return active==null?"":active.listenerCoverage().stream().map(PeerEndpoint::authority).collect(java.util.stream.Collectors.joining(", "));}
    public static Component receiveStatusComponent() {
        String key = (receiveErrors.containsValue("decode") || receiveErrors.values().stream().anyMatch(reason -> reason.startsWith("gpu_upload:"))) ? "decode_failed" : !receiveErrors.isEmpty() ? "download_failed"
                : !pending.isEmpty() ? "receiving" : "received";
        return Component.translatable("gui.sparkle_morpher.settings.peer." + key, owned.size(), views.size());
    }
    public static Component observerStatusComponent() {
        long submitted = observerReceipts.stream().filter(r -> r.report().state() == PeerObservation.State.RENDER_SUBMITTED).count();
        long failed = observerReceipts.stream().filter(r -> r.report().state() == PeerObservation.State.FAILED).count();
        return Component.translatable("gui.sparkle_morpher.settings.peer.observers", submitted, Math.max(0, members - 1), failed);
    }
    public static String diagnostics() {
        var lines = new StringBuilder("SPM Peer protocol 1.2\n").append(status).append('\n');
        localReports.forEach((uuid, report) -> lines.append("receive owner=").append(uuid).append(" revision=").append(report.revision())
                .append(" state=").append(report.state()).append(" failure=").append(report.failure()).append('\n'));
        receiveErrors.forEach((uuid, error) -> lines.append("receive error owner=").append(uuid).append(" reason=").append(error).append('\n'));
        observerReceipts.forEach(receipt -> lines.append("observer=").append(receipt.observer().minecraftUuid()).append(" revision=")
                .append(receipt.report().revision()).append(" state=").append(receipt.report().state()).append(" failure=").append(receipt.report().failure()).append('\n'));
        lines.append(PeerDiagnostics.resources()).append('\n');
        var active=runtime;if(active!=null)lines.append("listeners=").append(listenerCoverage()).append(" cold_jobs=").append(active.pendingDownloads()).append(" capabilities=").append(active.capabilities()).append('\n');
        timelines.forEach((uuid,time)->lines.append("owner_timing=").append(uuid).append(' ').append(time.describe()).append('\n'));
        ownedModels.forEach((uuid,model)->{var upload=PeerDiagnostics.textureSubmission(model);lines.append("texture_upload_command_submitted owner=").append(uuid).append(" observed=").append(upload!=null).append(' ').append(PeerFrameEvidence.GLOBAL.describe(uuid.toString(),model)).append('\n');});
        return lines.toString();
    }
    private static void observe(PeerRoomCoordinator.Member member, PeerObservation.State state, PeerFrame.ErrorCode failure) {
        if(runtime == null || member.appearance() == null || member.appearance().cleared()) return;
        timelines.computeIfAbsent(member.session().minecraftUuid(),ignored->new PeerDiagnostics.Timeline()).observe(state);
        var report = new PeerObservation.Report(member.session(), member.appearance().revision(), state, failure);
        if(!report.equals(localReports.put(member.session().minecraftUuid(), report))) runtime.observe(report);
    }
    /** Successful renderer submission is diagnostic evidence, not GPU completion or a visual PASS. */
    public static void renderState(Player player, String modelId, PeerObservation.State state) {
        UUID uuid = player.getUUID(); var view = views.get(uuid);
        if(view == null || view.entity != player || owned.get(uuid) != player || !Objects.equals(ownedModels.get(uuid), modelId)) return;
        if(state == PeerObservation.State.RENDER_SUBMITTED && PlayerCapability.get(player).map(cap -> !cap.isModelReady()).orElse(true)) state = PeerObservation.State.WAITING_RENDER;
        if(state == PeerObservation.State.RENDER_SUBMITTED) PeerFrameEvidence.GLOBAL.draw(uuid.toString(), modelId);
        observe(view.member, state, state == PeerObservation.State.FAILED ? PeerFrame.ErrorCode.IMPORT_FAILED : null);
    }
    public static void configureBootstrap(java.util.function.BooleanSupplier available, Runnable request) {
        bootstrapAvailable = Objects.requireNonNull(available); bootstrapRequest = Objects.requireNonNull(request);
    }
    /** Receiver captures its connection before scheduling, so late offers cannot join a different world. */
    public static void acceptAutomaticOffer(Object sourceConnection, String encoded) {
        Minecraft mc = Minecraft.getInstance();
        if(sourceConnection != mc.getConnection() || autoSuppressed || PrivacyMode.isActive()
                || mc.player == null || mc.level == null || runtime != null) return;
        if(encoded.isEmpty()) { status = "此服务器尚未提供局域网自动 P2P"; return; }
        try {
            var offered = PeerInvitation.decode(encoded);
            var remote = mc.getConnection().getConnection().getRemoteAddress();
            if(!(remote instanceof java.net.InetSocketAddress address) || address.getAddress() == null) return;
            // Follow the route the game already uses, including VPN addresses; never guess a NIC.
            var endpoint = PeerModelEntrances.automaticRoute(address.getAddress(),offered.endpoints());
            var invite = new PeerInvitation(offered.roomId(), List.of(endpoint), offered.pin(), offered.expiresAt(), offered.secret());
            if(begin()) {
                automatic = true; sharing = !shareSuppressed;
                runtime.join(invite, mc.player.getUUID());
            }
        } catch(RuntimeException invalid) { status = "邀请无效或已过期"; }
    }
    private static void tickAutomatic(Minecraft mc) {
        if(!com.micaftic.morpher.core.network.NetworkAccess.allowsPeer()) return;
        if(autoConnection != mc.getConnection()) {
            autoConnection = mc.getConnection(); autoSuppressed = shareSuppressed = automaticRetryExhausted = false;
            automaticFailures = 0; nextBootstrap = nextAutomaticAttempt = 0;
        }
        if(mc.player == null || mc.level == null || mc.getConnection() == null || PrivacyMode.isActive() || autoSuppressed) return;
        if(runtime != null || automaticRetryExhausted || System.nanoTime() < nextAutomaticAttempt) return;
        var integrated = mc.getSingleplayerServer();
        if(integrated != null && integrated.isPublished()) {
            if(!begin()) return;
            automatic = true; sharing = !shareSuppressed; hosting = true;
            var active = runtime; long token = generation;
            approvedWorldPlayers = Set.copyOf(currentWorldPlayers(mc));
            active.hostAutomatic(mc.player.getUUID(), approvedWorldPlayers, invite -> {
                if(token != generation || active != runtime || mc.getSingleplayerServer() != integrated || !integrated.isPublished()) return;
                advertisedServer = integrated;
                PeerLanBootstrap.publish(integrated, invite.expiresAt(), uuid -> active.invitationFor(invite, uuid));
                invitation = null; // Automatic invites are private to the connected game participants.
            });
        } else if(integrated == null && System.nanoTime() >= nextBootstrap) {
            nextBootstrap = System.nanoTime() + 3_000_000_000L;
            if(bootstrapAvailable.getAsBoolean()) {
                status = "P2P 等待局域网房主提供自动接入";
                bootstrapRequest.run();
            } else status = "此服务器尚未提供局域网自动 P2P";
        }
    }
    /** Explicit leave suppresses automatic rejoining until the next game connection. */
    public static void leave() { autoSuppressed = true; disconnect(); }
    public static void enableAutomatic() { autoSuppressed = automaticRetryExhausted = false; automaticFailures = 0; nextBootstrap = nextAutomaticAttempt = 0; }
    public static void retrySharing() { lastSelection = null; retryAt.clear(); receiveErrors.clear(); }
    public static boolean ownsAppearance(UUID uuid) { return owned.containsKey(uuid); }
    static PeerRoomCoordinator.Member motionMember(UUID uuid) { return roomMembers.get(uuid); }
    static boolean retainsMotionPolicy(String model){return views.values().stream().anyMatch(view->view.member.appearance()!=null&&!view.member.appearance().cleared()&&view.member.appearance().model().runtimeModelId().equals(model));}
    private static boolean cloudPreferred(UUID uuid) {
        return com.micaftic.morpher.core.network.NetworkAccess.state().preset().prefersCloud()
                && cloudCandidates.contains(uuid);
    }
    public static void yieldToCloud(UUID uuid) {
        if(!com.micaftic.morpher.core.network.NetworkAccess.state().preset().prefersCloud()) return;
        if(com.micaftic.morpher.cloud.client.CloudPlayerModelSync.readyCandidate(uuid)
                || com.micaftic.morpher.cloud.client.CloudEntityModelSync.readyCandidate(uuid)) {
            var next=new HashSet<>(cloudCandidates); next.add(uuid); cloudCandidates=Set.copyOf(next); release(uuid);
        }
    }
    private static boolean begin() {
        if(!com.micaftic.morpher.core.network.NetworkAccess.allowsPeer()) { status = "P2P 已被网络预设暂停"; return false; }
        Minecraft mc = Minecraft.getInstance();
        if(mc.player == null || mc.level == null || PrivacyMode.isActive()) { status = "请先进入世界，并关闭隐私模式后重新连接"; return false; }
        disconnect(); world = mc.level; connection = mc.getConnection(); long token = generation;
        runtime = new PeerRoomRuntime(ModelStoragePaths.cache().resolve("peer"), mc,
                snapshot -> { if(token == generation) snapshot(snapshot); },
                message -> { if(token == generation) status = message; });
        runtime.onObservations(receipts -> { if(token == generation) observerReceipts = receipts; });
        runtime.onMotions(receipts -> { if(token == generation) PeerMotionSync.receive(receipts); });
        status = "P2P 正在连接"; return true;
    }
    public static void host(String authority) {
        try {
            var endpoint = PeerEndpoint.parse(authority);
            if(endpoint.hasLocalScope() || endpoint.host().equals("0.0.0.0") || endpoint.host().equals("::")) throw new IllegalArgumentException();
            if(!begin()) return; var mc = Minecraft.getInstance();
            autoSuppressed = true;
            var approved = currentWorldPlayers(mc);
            runtime.host(endpoint, mc.player.getUUID(), approved, invite -> invitation = invite.encode());
            hosting = true;
            approvedWorldPlayers = Set.copyOf(approved);
        } catch(RuntimeException invalid) { status = "请输入可供同伴连接的 LAN/VPN 地址和模型端口"; }
    }
    public static void hostEntrances(String listeners,String entrances) {
        try {
            var configured=PeerModelEntrances.parse(listeners,entrances);
            com.micaftic.morpher.config.GeneralConfig.PEER_LISTENERS.set(listeners.trim());
            com.micaftic.morpher.config.GeneralConfig.PEER_ENTRANCES.set(entrances.trim());
            com.micaftic.morpher.config.GeneralConfig.PEER_LISTENERS.save();
            if(!begin())return;var mc=Minecraft.getInstance();autoSuppressed=true;hosting=true;
            approvedWorldPlayers=Set.copyOf(currentWorldPlayers(mc));
            runtime.host(configured,mc.player.getUUID(),approvedWorldPlayers,invite->invitation=invite.encode());
        }catch(RuntimeException invalid){status=Component.translatable("gui.sparkle_morpher.settings.peer.status.invalid_endpoint").getString();}
    }
    public static void probeInvitation(String encoded) {probeInvitation(encoded,"");}
    public static void probeInvitation(String encoded,String localRoute) {
        if(!com.micaftic.morpher.core.network.NetworkAccess.allowsPeer()||!probing.compareAndSet(false,true))return;
        final PeerInvitation invite;try{invite=PeerModelEntrances.localRoute(PeerInvitation.decode(encoded),localRoute);if(!invite.validAt(java.time.Instant.now()))throw new IllegalArgumentException();}catch(RuntimeException invalid){probing.set(false);probeStatus=Component.translatable("gui.sparkle_morpher.settings.peer.status.invalid_invitation");return;}
        long token=generation;probeStatus=Component.translatable("gui.sparkle_morpher.settings.peer.probe_running");
        Thread.ofVirtual().name("SPM-Peer-Endpoint-Probe").start(()->{
            Component result;
            try(var dialer=new PeerDialer()){
                probeDialer=dialer;if(token!=generation)throw new IOException("Probe cancelled");
                long began=System.nanoTime();try(var connected=dialer.connect(invite,8000)){var address=(java.net.InetSocketAddress)connected.socket().getRemoteSocketAddress();result=Component.translatable("gui.sparkle_morpher.settings.peer.probe_tls",new PeerEndpoint(address.getAddress().getHostAddress(),address.getPort()).authority(),(System.nanoTime()-began)/1_000_000L);}
            }catch(Exception error){var failure=PeerFailure.classify(PeerFailure.Stage.CONNECT,error);result=Component.translatable("gui.sparkle_morpher.settings.peer.connection_error",Component.translatable("gui.sparkle_morpher.settings.peer.stage."+failure.stage().name().toLowerCase(Locale.ROOT)),Component.translatable("gui.sparkle_morpher.settings.peer.error."+failure.code().name().toLowerCase(Locale.ROOT)));}
            finally{probeDialer=null;probing.set(false);}
            var finished=result;Minecraft.getInstance().execute(()->{if(token==generation&&com.micaftic.morpher.core.network.NetworkAccess.allowsPeer())probeStatus=finished;});
        });
    }
    public static void join(String encoded) {join(encoded,"");}
    public static void join(String encoded,String localRoute) {
        try { var invite = PeerModelEntrances.localRoute(PeerInvitation.decode(encoded),localRoute); if(begin()) { autoSuppressed = true; runtime.join(invite, Minecraft.getInstance().player.getUUID()); } }
        catch(RuntimeException invalid) { status = "邀请无效或已过期"; }
    }
    public static void setSharing(boolean approved) { sharing = approved; shareSuppressed = !approved; lastSelection = null; if(!approved && runtime != null) runtime.clear(); }
    public static void approvePlayers() {
        if(runtime != null && Minecraft.getInstance().level != null) {
            currentWorldPlayers(Minecraft.getInstance()).forEach(runtime::approve);
            status = "已批准当前世界内玩家的 UUID（房间邀请仍须保密）";
        }
    }
    private static void snapshot(PeerRoomCoordinator.Snapshot snapshot) {
        Minecraft mc = Minecraft.getInstance(); if(mc.level == null || mc.level != world) return;
        Map<UUID,View> next = new HashMap<>(); members = snapshot.members().size();
        Map<UUID, PeerRoomCoordinator.Member> nextMembers = new HashMap<>();
        for(var member : snapshot.members()) {
            UUID uuid = member.session().minecraftUuid(); if(uuid.equals(mc.player.getUUID())) continue;
            nextMembers.put(uuid, member);
            Player player = mc.level.players().stream().filter(p -> p.getUUID().equals(uuid)).findFirst().orElse(null);
            if(player != null && member.appearance() != null && !member.appearance().cleared()) {
                var view = new View(member, player); View previous = views.get(uuid);
                next.put(uuid, view.equals(previous) ? previous : view);
            } else if(player == null) observe(member, PeerObservation.State.NOT_TRACKED, null);
        }
        for(var entry : views.entrySet()) if(!Objects.equals(next.get(entry.getKey()), entry.getValue())) release(entry.getKey());
        views = Map.copyOf(next);
        roomMembers = Map.copyOf(nextMembers);
        localReports.entrySet().removeIf(entry -> { var member = roomMembers.get(entry.getKey()); return member == null || member.appearance() == null
                || member.appearance().cleared() || member.appearance().revision() != entry.getValue().revision() || !member.session().equals(entry.getValue().owner()); });
    }
    private static void release(UUID uuid) {
        PeerFrameEvidence.GLOBAL.removeOwner(uuid.toString());
        PeerMotionSync.remove(uuid);timelines.remove(uuid);
        receiveErrors.remove(uuid);
        pending.remove(uuid); retryAt.remove(uuid);
        Player player = owned.remove(uuid);
        String expectedModel = ownedModels.get(uuid);
        if(player != null) PlayerCapability.get(player).ifPresent(cap -> {
            if(Objects.equals(expectedModel, cap.getModelId())) { cap.clearModel(); cap.setForceDisabled(true); }
        });
        var lease = leases.remove(uuid); if(lease != null) lease.close();
        String model = ownedModels.remove(uuid);
        if(model != null && !ownedModels.containsValue(model) && pending.values().stream().noneMatch(view -> view.member.appearance().model().runtimeModelId().equals(model))) {
            ClientModelManager.releasePeerModel(model); PeerMotionSync.forget(model);PeerDiagnostics.forget(model);
        }
        com.micaftic.morpher.cloud.client.CloudPlayerModelSync.peerOwnershipChanged(uuid);
        com.micaftic.morpher.cloud.client.CloudEntityModelSync.peerOwnershipChanged(uuid);
    }
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        PeerMotionSync.keepLocalModel(mc.player==null?null:PlayerCapability.get(mc.player).map(PlayerCapability::getModelId).orElse(null));
        tickAutomatic(mc);
        var active = runtime; if(active == null) return;
        if(mc.level != world || mc.getConnection() != connection || mc.player == null || PrivacyMode.isActive() || active.isClosed()) {
            String reason = active.failure() == null ? status : "P2P 连接失败：" + active.failure().getMessage();
            boolean retryFailure = automatic && active.isClosed();
            disconnect();
            if(retryFailure) {
                automaticFailures++; automaticRetryExhausted = automaticFailures >= 6;
                nextAutomaticAttempt = System.nanoTime() + Math.min(30, 1 << Math.min(automaticFailures - 1, 5)) * 1_000_000_000L;
            } else nextAutomaticAttempt = System.nanoTime() + 1_000_000_000L;
            if(active.isClosed()) status = reason; return;
        }
        if(automatic && hosting && (mc.getSingleplayerServer() == null || !mc.getSingleplayerServer().isPublished())) { disconnect(); return; }
        if(automatic && hosting && active.automaticInvitationNeedsRenewal(java.time.Instant.now())) {
            disconnect(); nextAutomaticAttempt = 0; return;
        }
        if(!active.connected()) return;
        automaticFailures = 0;
        ownedModels.forEach((uuid,model)->{
            var error=PeerDiagnostics.uploadFailure(model);var view=views.get(uuid);
            if(error!=null&&view!=null){receiveErrors.put(uuid,"gpu_upload:"+error);observe(view.member,PeerObservation.State.FAILED,PeerFrame.ErrorCode.IMPORT_FAILED);}
            else if(receiveErrors.getOrDefault(uuid,"").startsWith("gpu_upload:"))receiveErrors.remove(uuid);
        });
        // World membership already supplies the host's allow-list. Keep the invitation secret requirement intact.
        if(hosting && System.nanoTime() >= nextPlayerApproval) {
            nextPlayerApproval = System.nanoTime() + 250_000_000L;
            Set<UUID> currentPlayers = currentWorldPlayers(mc);
            if(!currentPlayers.equals(approvedWorldPlayers)) {
                active.approveWorldPlayers(currentPlayers);
                approvedWorldPlayers = Set.copyOf(currentPlayers);
            }
        }
        if(sharing) PlayerCapability.get(mc.player).ifPresent(cap -> {
            String id = cap.getModelId(), texture = Objects.requireNonNullElse(cap.currentTextureName, "default");
            String selected = id + "\n" + texture + "\n" + cap.isForceDisabled() + "\n" + ClientModelManager.localSourceRevision(id);
            if(!selected.equals(lastSelection)) {
                lastSelection = selected;
                var source = ClientModelManager.MODEL_LIBRARY.source(id);
                if(source == null || source.type() != com.micaftic.morpher.core.model.ModelSourceType.LOCAL)
                    active.clear("P2P 已连接；当前未选择可分享的本地模型");
                else if(cap.isForceDisabled()) active.clear("当前本地模型已隐藏，暂停发布");
                else active.share(() -> {
                    try { return LocalShareSourceResolver.resolve(ClientModelManager.MODEL_LIBRARY, id, ModelStoragePaths.custom(), true); }
                    catch(IOException invalid) { throw new java.io.UncheckedIOException(invalid); }
                }, texture);
            }
        });
        PlayerCapability.get(mc.player).ifPresent(cap -> PeerMotionSync.tick(cap.getModelId(), active, sharing && !cap.isForceDisabled()));
        var readyCloud = new HashSet<UUID>();
        if(com.micaftic.morpher.core.network.NetworkAccess.state().preset().prefersCloud()) for(UUID uuid : views.keySet()) {
            if(com.micaftic.morpher.cloud.client.CloudPlayerModelSync.readyCandidate(uuid)
                    || com.micaftic.morpher.cloud.client.CloudEntityModelSync.readyCandidate(uuid)) readyCloud.add(uuid);
        }
        cloudCandidates = Set.copyOf(readyCloud);
        for(var entry : views.entrySet()) {
            UUID uuid = entry.getKey(); View view = entry.getValue();
            if(!mc.level.players().contains(view.entity)) { release(uuid); continue; }
            if(cloudPreferred(uuid)) { if(owned.containsKey(uuid)) release(uuid); continue; }
            if(owned.get(uuid) == view.entity) {
                PlayerCapability.get(view.entity).ifPresent(cap -> {
                    String id = view.member.appearance().model().runtimeModelId();
                    if(!id.equals(cap.getModelId()) || !Objects.equals(view.member.appearance().textureVariant(), cap.currentTextureName))
                        cap.initModelWithTexture(id, view.member.appearance().textureVariant());
                    cap.setForceDisabled(false);
                }); continue;
            }
            if(pending.containsKey(uuid) || System.nanoTime() < retryAt.getOrDefault(uuid, 0L)) continue;
            pending.put(uuid, view); long token = generation;
            observe(view.member, PeerObservation.State.DOWNLOADING, null);
            java.util.function.BooleanSupplier current = () -> token == generation && views.get(uuid) == view
                    && com.micaftic.morpher.core.network.NetworkAccess.allowsPeer() && !cloudPreferred(uuid);
            active.download(view.member, current, (asset, lease) -> {
                if(!current.getAsBoolean()) { lease.close(); pending.remove(uuid, view); return; }
                observe(view.member, PeerObservation.State.VERIFIED, null);
                observe(view.member, PeerObservation.State.IMPORTING, null);
                var importReason=new java.util.concurrent.atomic.AtomicReference<PeerFailure>();
                ClientModelManager.loadPeerModel(view.member.appearance().model(), asset, current, error -> {
                    if(token != generation) { lease.close(); return; }
                    pending.remove(uuid, view);
                    if(error != null || !current.getAsBoolean()) {
                        lease.close(); if(!current.getAsBoolean()) return;
                        retryAt.put(uuid, System.nanoTime() + 3_000_000_000L);
                        if(error != null && current.getAsBoolean()) {
                            receiveErrors.put(uuid, "decode"); observe(view.member, PeerObservation.State.FAILED, importReason.get()==null?PeerFrame.ErrorCode.IMPORT_FAILED:importReason.get().code());
                        }
                        return;
                    }
                    receiveErrors.remove(uuid);
                    var oldLease = leases.put(uuid, lease); if(oldLease != null) oldLease.close();
                    ownedModels.put(uuid, view.member.appearance().model().runtimeModelId()); owned.put(uuid, view.entity);
                    com.micaftic.morpher.cloud.client.CloudPlayerModelSync.peerOwnershipChanged(uuid);
                    com.micaftic.morpher.cloud.client.CloudEntityModelSync.peerOwnershipChanged(uuid);
                    PlayerCapability.get(view.entity).ifPresent(cap -> {
                        cap.initModelWithTexture(view.member.appearance().model().runtimeModelId(), view.member.appearance().textureVariant()); cap.setForceDisabled(false);
                        observe(view.member, PeerObservation.State.APPLIED, null);
                    });
                },importReason::set);
            }, failure -> { if(token == generation && pending.remove(uuid, view) && current.getAsBoolean()) {
                retryAt.put(uuid, System.nanoTime() + 3_000_000_000L);
                receiveErrors.put(uuid, failure.getMessage());
                observe(view.member, PeerObservation.State.FAILED, failure.code());
                System.getLogger("SPM-Peer").log(System.Logger.Level.WARNING, "Receive failed for {0} revision {1}: {2}", uuid, view.member.appearance().revision(), failure.getMessage());
            } });
        }
    }
    public static void disconnect() {
        var probingNow=probeDialer;if(probingNow!=null)probingNow.close();probeStatus=Component.translatable("gui.sparkle_morpher.settings.peer.probe_idle");
        if(advertisedServer != null) { PeerLanBootstrap.clear(advertisedServer); advertisedServer = null; }
        PeerMotionSync.reset();timelines.clear();PeerDiagnostics.reset(); generation++; var active = runtime; runtime = null; if(active != null) active.close();
        for(UUID uuid : List.copyOf(owned.keySet())) release(uuid);
        views = Map.of(); pending.clear(); retryAt.clear(); leases.values().forEach(PeerAssetStore.Lease::close); leases.clear(); ownedModels.clear();
        receiveErrors.clear();
        roomMembers = Map.of(); localReports.clear(); observerReceipts = List.of(); cloudCandidates = Set.of();
        if(active != null) ClientModelManager.releasePeerModels();
        world = connection = null; sharing = false; invitation = lastSelection = null; members = 0; status = "P2P 已退出";
        hosting = false; approvedWorldPlayers = Set.of(); nextPlayerApproval = 0;
        automatic = false;
    }
    public static String suggestedEndpoint() {
        try {
            var adapters = NetworkInterfaces.addresses();
            return adapters.isEmpty() ? "127.0.0.1:26437" : adapters.get(0) + ":26437";
        } catch(Exception ignored) { return "127.0.0.1:26437"; }
    }
    public static String nextSuggestedEndpoint(String current) {
        try {
            var addresses = NetworkInterfaces.addresses();
            if(addresses.isEmpty()) return current;
            int port = 26437;
            String host = "";
            try { var endpoint = PeerEndpoint.parse(current); host = endpoint.host(); port = endpoint.port(); }
            catch(IllegalArgumentException ignored) {}
            int index = addresses.indexOf(host);
            return addresses.get((index + 1) % addresses.size()) + ":" + port;
        } catch(java.net.SocketException ignored) { return current; }
    }
    private static Set<UUID> currentWorldPlayers(Minecraft mc) {
        // Use the game connection roster, not just nearby tracked entities, so distance does not revoke room members.
        Set<UUID> players = new HashSet<>();
        if(mc.getConnection() != null) players.addAll(mc.getConnection().getOnlinePlayerIds());
        else if(mc.level != null) mc.level.players().forEach(player -> players.add(player.getUUID()));
        if(mc.player != null) players.add(mc.player.getUUID());
        return players;
    }
    private static final class NetworkInterfaces {
        static List<String> addresses() throws java.net.SocketException {
            var values = new ArrayList<String>(); var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while(interfaces.hasMoreElements()) { var adapter = interfaces.nextElement(); if(!adapter.isUp() || adapter.isLoopback()) continue;
                var addresses = adapter.getInetAddresses(); while(addresses.hasMoreElements()) { var address = addresses.nextElement();
                    if(address instanceof java.net.Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()
                            && !address.getHostAddress().startsWith("198.18.") && !address.getHostAddress().startsWith("198.19.")) values.add(address.getHostAddress()); }
            } return values;
        }
    }
}
