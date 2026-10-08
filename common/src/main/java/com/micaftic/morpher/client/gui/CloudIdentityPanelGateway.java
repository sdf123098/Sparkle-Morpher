package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.*;
import net.minecraft.client.Minecraft;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Captures the exact account, game world and selected scope for one panel workflow. */
final class CloudIdentityPanelGateway implements CloudIdentityWorkflow.Gateway {
    private final CloudManagementScreen.AccountContext account = CloudManagementScreen.accountContext();
    private final CloudClientRuntime.RuntimeState runtime = account.runtime();
    private final CloudScopeClient.CloudScope scope = account.controller().snapshot().selectedScope();
    private final Object level = Minecraft.getInstance().level;
    private final CloudIdentityWorkflow.Context context;

    CloudIdentityPanelGateway() {
        var profile = MinecraftSessionServiceJoiner.currentProfile();
        var player = Minecraft.getInstance().player;
        context = new CloudIdentityWorkflow.Context(account.controller().accountId(account.selected().instanceId()),
                scope, player == null ? profile.profileId() : player.getUUID(), profile.name());
    }
    @Override public CloudIdentityWorkflow.Context context() { return context; }
    @Override public void check() {
        account.check(runtime);
        if (!Objects.equals(scope, account.controller().snapshot().selectedScope()) || level != Minecraft.getInstance().level)
            throw new java.util.concurrent.CancellationException("Identity world changed");
    }
    boolean current() { try { check(); return true; } catch (RuntimeException stale) { return false; } }
    @Override public CompletableFuture<CloudIdentityWorkflow.Catalog> load() {
        check();
        var providers = runtime.identities().listProviders();
        var identities = runtime.identities().listIdentities();
        var targets = scope == null ? CompletableFuture.completedFuture(List.<CloudScopeClient.CloudTarget>of()) : runtime.scopes().listTargets(scope.scopeId());
        var bindings = scope == null ? CompletableFuture.completedFuture(List.<CloudIdentityBindingClient.CloudBinding>of()) : runtime.identityBindings().listScopeBindings(scope.scopeId());
        var permissions = scope == null ? CompletableFuture.<CloudScopeClient.CloudScopePermissions>completedFuture(null)
                : runtime.scopes().ownPermissions(scope.scopeId()).exceptionally(failure -> {
                    Throwable cause = failure;
                    while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) cause = cause.getCause();
                    if (cause instanceof CloudHttpException http && http.statusCode() == 404) return null;
                    throw new java.util.concurrent.CompletionException(cause);
                });
        var mc = Minecraft.getInstance();
        var players = mc.level == null ? List.<CloudIdentityWorkflow.Player>of()
                : mc.level.players().stream().limit(128).map(player -> new CloudIdentityWorkflow.Player(player.getUUID(), player.getName().getString())).toList();
        return CompletableFuture.allOf(providers, identities, targets, bindings, permissions).thenApply(ignored -> {
            check();
            return new CloudIdentityWorkflow.Catalog(providers.join(), identities.join(), targets.join(), bindings.join(), permissions.join(), players);
        });
    }
    @Override public CompletableFuture<CloudIdentityClient.CloudIdentity> verify(String providerId) { check(); return CloudManagementScreen.bindProvider(providerId); }
    @Override public CompletableFuture<CloudIdentityClient.CloudIdentity> register() {
        check(); return runtime.identities().registerOfflineIdentity(scope.scopeId(), context.profileId(), context.profileName());
    }
    @Override public CompletableFuture<CloudIdentityBindingClient.CloudBinding> request(String identityId, String targetId) {
        check(); return runtime.identityBindings().requestApproval(identityId, scope.scopeId(), scope.worldEpoch(), targetId);
    }
    @Override public CompletableFuture<CloudIdentityBindingClient.CloudBinding> redeem(String code, String identityId) {
        check(); return runtime.identityBindings().redeemClaimCode(code, identityId);
    }
    @Override public CompletableFuture<CloudIdentityBindingClient.CloudClaimCode> issue(String targetId, UUID playerId) {
        check();
        if (Minecraft.getInstance().level == null || Minecraft.getInstance().level.getPlayerByUUID(playerId) == null)
            return CompletableFuture.failedFuture(new IllegalStateException("Player is no longer loaded"));
        return runtime.identityBindings().createClaimCode(targetId, scope.worldEpoch(), playerId.toString(), 600L);
    }
    @Override public CompletableFuture<Void> revoke(String code) { check(); return runtime.identityBindings().revokeClaimCode(code); }
    @Override public CompletableFuture<CloudIdentityBindingClient.CloudBinding> approve(String id, String status, long revision) {
        check(); return runtime.identityBindings().approve(id, status, revision);
    }
}
