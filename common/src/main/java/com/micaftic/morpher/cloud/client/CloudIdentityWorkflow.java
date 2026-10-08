package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.identity.CloudIdentityRef;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Main-thread identity workflow. Names are displayed; identifiers never need user input. */
public final class CloudIdentityWorkflow {
    public record Context(String accountId, CloudScopeClient.CloudScope scope, UUID profileId, String profileName) { }
    public record Player(UUID id, String name) { }
    public record Catalog(List<CloudIdentityClient.IdentityProvider> providers,
                          List<CloudIdentityClient.CloudIdentity> identities,
                          List<CloudScopeClient.CloudTarget> targets,
                          List<CloudIdentityBindingClient.CloudBinding> bindings,
                          CloudScopeClient.CloudScopePermissions permissions, List<Player> players) {
        public Catalog {
            providers = List.copyOf(providers); identities = List.copyOf(identities);
            targets = List.copyOf(targets); bindings = List.copyOf(bindings); players = List.copyOf(players);
        }
    }
    public interface Gateway {
        Context context();
        void check();
        CompletableFuture<Catalog> load();
        CompletableFuture<CloudIdentityClient.CloudIdentity> verify(String providerId);
        CompletableFuture<CloudIdentityClient.CloudIdentity> register();
        CompletableFuture<CloudIdentityBindingClient.CloudBinding> request(String identityId, String targetId);
        CompletableFuture<CloudIdentityBindingClient.CloudBinding> redeem(String code, String identityId);
        CompletableFuture<CloudIdentityBindingClient.CloudClaimCode> issue(String targetId, UUID playerId);
        CompletableFuture<Void> revoke(String code);
        CompletableFuture<CloudIdentityBindingClient.CloudBinding> approve(String bindingId, String status, long revision);
    }
    private final Gateway gateway;
    private final Executor executor;
    private Catalog catalog = new Catalog(List.of(), List.of(), List.of(), List.of(), null, List.of());
    private long generation;
    private boolean busy, loaded;
    private String message = "loading", providerId, identityId, targetId, bindingId;
    private UUID playerId;
    private String codeDraft = "";
    private CloudIdentityBindingClient.CloudClaimCode issued;
    private long issuedAt;

    public CloudIdentityWorkflow(Gateway gateway, Executor executor) {
        this.gateway = Objects.requireNonNull(gateway); this.executor = Objects.requireNonNull(executor);
    }
    public Context context() { return gateway.context(); }
    public Catalog catalog() { return catalog; }
    public boolean busy() { return busy; }
    public boolean loaded() { return loaded; }
    public String message() { return message; }
    public String codeDraft() { return codeDraft; }
    public void copied() { message = "copied"; }
    public void codeDraft(String value) { codeDraft = value == null ? "" : value.strip(); }
    public CloudIdentityBindingClient.CloudClaimCode issued() { return issued; }
    public boolean issuedExpired() { return issued != null && System.currentTimeMillis() - issuedAt >= issued.expiresInSeconds() * 1000; }
    public void invalidate() { generation++; busy = false; loaded = false; issued = null; codeDraft = ""; }
    public void refresh() { refresh(false); }
    private void refresh(boolean preserveMessage) {
        if (!preserveMessage) message = "loading";
        run(gateway::load, next -> {
            var scope = context().scope();
            if (scope != null && next.permissions() != null && !scope.scopeId().equals(next.permissions().scopeId()))
                throw new IllegalArgumentException("Permissions do not match selected scope");
            catalog = new Catalog(next.providers(), next.identities(),
                    next.targets().stream().filter(t -> "PLAYER".equals(t.kind()) && scope != null && scope.scopeId().equals(t.scopeId())).toList(),
                    next.bindings().stream().filter(b -> scope != null && scope.scopeId().equals(b.scopeId()) && scope.worldEpoch().equals(b.worldEpoch())
                            && (next.permissions() != null && next.permissions().canManage() || context().accountId().equals(b.accountId()))).toList(),
                    next.permissions(), next.players());
            loaded = true;
            if (catalog.providers().stream().noneMatch(p -> p.providerId().equals(providerId)))
                providerId = catalog.providers().isEmpty() ? null : catalog.providers().get(0).providerId();
            if (catalog.identities().stream().noneMatch(i -> i.identityId().equals(identityId))) {
                identityId = catalog.identities().stream().filter(i -> i.identityRef().profileUuid().equals(context().profileId()))
                        .findFirst().map(CloudIdentityClient.CloudIdentity::identityId).orElse(null);
            }
            if (catalog.targets().stream().noneMatch(t -> t.targetId().equals(targetId)))
                targetId = catalog.targets().isEmpty() ? null : catalog.targets().get(0).targetId();
            if (pending().stream().noneMatch(b -> b.bindingId().equals(bindingId)))
                bindingId = pending().isEmpty() ? null : pending().get(0).bindingId();
            if (catalog.players().stream().noneMatch(p -> p.id().equals(playerId)))
                playerId = catalog.players().stream().filter(p -> p.id().equals(context().profileId())).findFirst()
                        .or(() -> catalog.players().stream().findFirst()).map(Player::id).orElse(null);
            if (!preserveMessage) message = scope != null && next.permissions() == null ? "server_update" : "ready";
        }, false);
    }
    public CloudIdentityClient.CloudIdentity selectedIdentity() { return catalog.identities().stream().filter(i -> i.identityId().equals(identityId)).findFirst().orElse(null); }
    public CloudScopeClient.CloudTarget selectedTarget() { return catalog.targets().stream().filter(t -> t.targetId().equals(targetId)).findFirst().orElse(null); }
    public Player selectedPlayer() { return catalog.players().stream().filter(p -> p.id().equals(playerId)).findFirst().orElse(null); }
    public String providerId() { return providerId; }
    public void selectIdentity(String id) { if (!busy && catalog.identities().stream().anyMatch(i -> i.identityId().equals(id))) identityId = id; }
    public void selectTarget(String id) { if (!busy && catalog.targets().stream().anyMatch(t -> t.targetId().equals(id))) targetId = id; }
    public void nextProvider() {
        if (busy || catalog.providers().isEmpty()) return;
        int index = 0;
        for (int i = 0; i < catalog.providers().size(); i++) if (catalog.providers().get(i).providerId().equals(providerId)) index = i;
        providerId = catalog.providers().get((index + 1) % catalog.providers().size()).providerId();
    }
    public void nextPlayer() {
        if (busy || catalog.players().isEmpty()) return;
        int index = 0;
        for (int i = 0; i < catalog.players().size(); i++) if (catalog.players().get(i).id().equals(playerId)) index = i;
        playerId = catalog.players().get((index + 1) % catalog.players().size()).id();
    }
    public void selectBinding(String id) { if (!busy && pending().stream().anyMatch(b -> b.bindingId().equals(id))) bindingId = id; }
    public List<CloudIdentityBindingClient.CloudBinding> pending() { return catalog.bindings().stream().filter(b -> "PENDING_APPROVAL".equals(b.status())).toList(); }
    public CloudIdentityBindingClient.CloudBinding selectedBinding() { return pending().stream().filter(b -> b.bindingId().equals(bindingId)).findFirst().orElse(null); }
    public String targetName(String id) {
        return catalog.targets().stream().filter(t -> t.targetId().equals(id)).findFirst()
                .map(CloudScopeClient.CloudTarget::displayName).orElseGet(() -> catalog.bindings().stream()
                        .filter(b -> b.targetId().equals(id) && b.targetDisplayName() != null)
                        .map(CloudIdentityBindingClient.CloudBinding::targetDisplayName).findFirst().orElse(context().profileName()));
    }
    public CloudIdentityClient.CloudIdentity localOfflineIdentity() {
        var scope = context().scope();
        return catalog.identities().stream().filter(i -> i.identityRef().kind() == CloudIdentityRef.Kind.OFFLINE
                && scope != null && scope.scopeId().equals(i.identityRef().scopeId()) && context().profileId().equals(i.identityRef().profileUuid()))
                .findFirst().orElse(null);
    }
    public boolean canManage() { return loaded && catalog.permissions() != null && catalog.permissions().canManage(); }
    public boolean canEdit() { return loaded && catalog.permissions() != null && catalog.permissions().canEdit(); }
    public String policy() { return catalog.permissions() == null ? "DISABLED" : catalog.permissions().offlinePolicy(); }
    public boolean canRegister() { return !busy && canEdit() && !"DISABLED".equals(policy()) && localOfflineIdentity() == null; }
    public boolean hasBinding() {
        return catalog.bindings().stream().anyMatch(b -> context().profileId().toString().equals(b.entityUuid())
                && context().accountId().equals(b.accountId()) && ("PENDING_APPROVAL".equals(b.status()) || "APPROVED".equals(b.status())));
    }
    public boolean canRequest() { return !busy && canEdit() && "STRICT_APPROVAL".equals(policy()) && localOfflineIdentity() != null && selectedTarget() != null && !hasBinding(); }
    public boolean claimPolicy() { return "CLAIM_CODE".equals(policy()) || "FIRST_CLAIM".equals(policy()); }
    public boolean canRedeem() { return !busy && canEdit() && claimPolicy() && localOfflineIdentity() != null && !codeDraft.isBlank() && !hasBinding(); }
    public boolean canIssue() { return !busy && canManage() && claimPolicy() && selectedTarget() != null && selectedPlayer() != null && (issued == null || issuedExpired()); }
    public boolean canApprove() { return !busy && canManage() && "STRICT_APPROVAL".equals(policy()) && selectedBinding() != null; }
    public void verify() { if (!busy && providerId != null) run(() -> gateway.verify(providerId), i -> message = "verified", true); }
    public void register() { if (canRegister()) run(gateway::register, i -> message = "registered", true); }
    public void request() { if (canRequest()) run(() -> gateway.request(localOfflineIdentity().identityId(), targetId), b -> message = "requested", true); }
    public void redeem() { if (canRedeem()) run(() -> gateway.redeem(codeDraft, localOfflineIdentity().identityId()), b -> { codeDraft = ""; message = "redeemed"; }, true); }
    public void issue() { if (canIssue()) run(() -> gateway.issue(targetId, playerId), code -> { issued = code; issuedAt = System.currentTimeMillis(); message = "issued"; }, false); }
    public void revoke() { if (!busy && canManage() && issued != null) run(() -> gateway.revoke(issued.code()), ignored -> { issued = null; message = "revoked"; }, false); }
    public void approve(boolean accepted) {
        if (canApprove()) {
            var binding = selectedBinding();
            run(() -> gateway.approve(binding.bindingId(), accepted ? "APPROVED" : "REJECTED", binding.revision()),
                    ignored -> message = accepted ? "approved" : "rejected", true);
        }
    }
    private <T> void run(Supplier<CompletableFuture<T>> operation, Consumer<T> result, boolean reload) {
        if (busy) return;
        final long expected = generation;
        try {
            gateway.check(); busy = true;
            operation.get().whenComplete((value, failure) -> executor.execute(() -> {
                if (expected != generation) return;
                try { gateway.check(); } catch (RuntimeException stale) { invalidate(); return; }
                busy = false;
                if (failure != null) { message = error(failure); return; }
                try { result.accept(value); if (reload) refresh(true); }
                catch (RuntimeException invalid) { message = error(invalid); }
            }));
        } catch (RuntimeException failure) { busy = false; message = error(failure); }
    }
    private static String error(Throwable failure) {
        while (failure.getCause() != null && (failure instanceof java.util.concurrent.CompletionException || failure instanceof java.util.concurrent.ExecutionException)) failure = failure.getCause();
        if (failure instanceof CloudHttpException http) return switch (http.statusCode()) {
            case 400 -> "invalid_code"; case 401 -> "login_required"; case 403 -> "permission_denied";
            case 404 -> "not_found"; case 409 -> "conflict"; case 429 -> "slow_down"; default -> "unavailable";
        };
        return "unavailable";
    }
}
