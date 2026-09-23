package com.micaftic.morpher.cloud.client;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * UI-neutral management facade for instance, scope, target and ACL screens.
 * It owns selection state only; credentials remain in {@link CloudConnectionController}.
 */
public final class CloudManagementController {
    private final CloudInstanceRegistry registry;
    private final CloudConnectionController connection;
    private final Path cacheRoot;
    private final String clientVersion;
    private volatile List<CloudScopeClient.CloudScope> scopes = List.of();
    private volatile List<CloudScopeClient.CloudTarget> targets = List.of();
    private volatile List<CloudScopeClient.CloudAclEntry> acl = List.of();
    private volatile CloudScopeClient.CloudScope selectedScope;
    private volatile CloudScopeClient.CloudTarget selectedTarget;

    public CloudManagementController(
            CloudInstanceRegistry registry,
            CloudConnectionController connection,
            Path cacheRoot,
            String clientVersion
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.connection = Objects.requireNonNull(connection, "connection");
        this.cacheRoot = Objects.requireNonNull(cacheRoot, "cacheRoot");
        if (clientVersion == null || clientVersion.isBlank()) throw new IllegalArgumentException("clientVersion must not be blank");
        this.clientVersion = clientVersion;
    }

    public void loadInstances() throws IOException {
        registry.load();
    }

    public void saveInstances() throws IOException {
        registry.save();
    }

    public CloudInstanceRegistry registry() {
        return registry;
    }

    public synchronized CloudInstanceRegistry.CloudInstanceProfile selectInstance(String instanceId) {
        CloudInstanceRegistry.CloudInstanceProfile next = registry.find(instanceId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Cloud instance: " + instanceId));
        CloudInstanceRegistry.CloudInstanceProfile active = connection.profile();
        if (active != null && !active.instanceId().equals(next.instanceId())) logout();
        registry.select(next.instanceId());
        return next;
    }

    public CompletableFuture<CloudSession> login(String accountId, String password) {
        CloudInstanceRegistry.CloudInstanceProfile profile = registry.selected()
                .orElseThrow(() -> new IllegalStateException("No Cloud instance is selected"));
        return connection.login(profile, accountId, password, cacheRoot, clientVersion)
                .thenCompose(session -> refreshScopes().thenApply(ignored -> session));
    }

    public CompletableFuture<CloudSession> refreshSession() {
        return connection.refresh(cacheRoot, clientVersion);
    }

    public void logout() {
        connection.logout();
        scopes = List.of();
        targets = List.of();
        acl = List.of();
        selectedScope = null;
        selectedTarget = null;
    }

    public CompletableFuture<List<CloudScopeClient.CloudScope>> refreshScopes() {
        return requireRuntime().scopes().listScopes().thenApply(next -> {
            scopes = List.copyOf(next);
            if (selectedScope != null) selectedScope = findScope(selectedScope.scopeId()).orElse(null);
            return scopes;
        });
    }

    public void selectScope(String scopeId) {
        selectedScope = findScope(scopeId).orElseThrow(() -> new IllegalArgumentException("Unknown Cloud scope: " + scopeId));
        selectedTarget = null;
        targets = List.of();
        acl = List.of();
    }

    public CompletableFuture<CloudScopeClient.CloudScope> createScope(CloudScopeClient.CloudScopeCreate create) {
        return requireRuntime().scopes().createScope(create).thenCompose(created -> refreshScopes().thenApply(ignored -> {
            selectedScope = created;
            return created;
        }));
    }

    public CompletableFuture<List<CloudScopeClient.CloudTarget>> refreshTargets() {
        CloudScopeClient.CloudScope scope = requireSelectedScope();
        return requireRuntime().scopes().listTargets(scope.scopeId()).thenApply(next -> {
            targets = List.copyOf(next);
            if (selectedTarget != null) selectedTarget = findTarget(selectedTarget.targetId()).orElse(null);
            return targets;
        });
    }

    public void selectTarget(String targetId) {
        selectedTarget = findTarget(targetId).orElseThrow(() -> new IllegalArgumentException("Unknown Cloud target: " + targetId));
    }

    public CompletableFuture<CloudScopeClient.CloudTarget> createTarget(CloudScopeClient.CloudTargetCreate create) {
        return requireRuntime().scopes().createTarget(create).thenCompose(created -> refreshTargets().thenApply(ignored -> {
            selectedTarget = created;
            return created;
        }));
    }

    public CompletableFuture<List<CloudScopeClient.CloudAclEntry>> refreshScopeAcl() {
        return requireRuntime().scopes().listScopeAcl(requireSelectedScope().scopeId()).thenApply(next -> {
            acl = List.copyOf(next);
            return acl;
        });
    }

    public CompletableFuture<List<CloudScopeClient.CloudAclEntry>> refreshTargetAcl() {
        return requireRuntime().scopes().listTargetAcl(requireSelectedTarget().targetId()).thenApply(next -> {
            acl = List.copyOf(next);
            return acl;
        });
    }

    public CompletableFuture<CloudScopeClient.CloudAclEntry> setScopeAcl(CloudScopeClient.CloudAclUpdate update) {
        return requireRuntime().scopes().setScopeAcl(requireSelectedScope().scopeId(), update);
    }

    public CompletableFuture<CloudScopeClient.CloudAclEntry> setTargetAcl(CloudScopeClient.CloudAclUpdate update) {
        return requireRuntime().scopes().setTargetAcl(requireSelectedTarget().targetId(), update);
    }

    public CompletableFuture<Void> enterSelectedScope() {
        CloudScopeClient.CloudScope scope = requireSelectedScope();
        return CloudClientRuntime.joinScope(scope.scopeId(), scope.worldEpoch());
    }

    public CloudManagementSnapshot snapshot() {
        return new CloudManagementSnapshot(
                connection.profile(), connection.isAuthenticated(), scopes, selectedScope,
                targets, selectedTarget, acl);
    }

    private CloudClientRuntime.RuntimeState requireRuntime() {
        if (!connection.isAuthenticated()) throw new IllegalStateException("Cloud session is not authenticated");
        return Objects.requireNonNull(CloudClientRuntime.state(), "Cloud runtime");
    }

    private CloudScopeClient.CloudScope requireSelectedScope() {
        return Objects.requireNonNull(selectedScope, "No Cloud scope is selected");
    }

    private CloudScopeClient.CloudTarget requireSelectedTarget() {
        return Objects.requireNonNull(selectedTarget, "No Cloud target is selected");
    }

    private java.util.Optional<CloudScopeClient.CloudScope> findScope(String scopeId) {
        return scopes.stream().filter(scope -> scope.scopeId().equals(scopeId)).findFirst();
    }

    private java.util.Optional<CloudScopeClient.CloudTarget> findTarget(String targetId) {
        return targets.stream().filter(target -> target.targetId().equals(targetId)).findFirst();
    }

    public record CloudManagementSnapshot(
            CloudInstanceRegistry.CloudInstanceProfile profile,
            boolean authenticated,
            List<CloudScopeClient.CloudScope> scopes,
            CloudScopeClient.CloudScope selectedScope,
            List<CloudScopeClient.CloudTarget> targets,
            CloudScopeClient.CloudTarget selectedTarget,
            List<CloudScopeClient.CloudAclEntry> acl
    ) {
        public CloudManagementSnapshot {
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
            targets = targets == null ? List.of() : List.copyOf(targets);
            acl = acl == null ? List.of() : List.copyOf(acl);
        }
    }
}
