package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudIdentityClient;
import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.cloud.client.MinecraftSessionServiceJoiner;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Explicit game-identity verification and scope-local Offline approval tools. */
public final class CloudIdentityManagementScreen extends Screen {
    private final Screen parent;
    private final CloudManagementController management;
    private List<CloudIdentityClient.IdentityProvider> providers = List.of();
    private List<CloudIdentityClient.CloudIdentity> identities = List.of();
    private List<com.micaftic.morpher.cloud.client.CloudIdentityBindingClient.CloudBinding> bindings = List.of();
    private int providerIndex;
    private int identityIndex;
    private int bindingIndex;
    private EditBox identityId;
    private EditBox targetId;
    private EditBox entityUuid;
    private EditBox claimCode;
    private EditBox bindingId;
    private EditBox revision;
    private Button providerButton;
    private Button statusButton;
    private String status;
    private long generation;
    private boolean active;

    CloudIdentityManagementScreen(Screen parent, CloudManagementController management) {
        super(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.title"));
        this.parent = parent;
        this.management = management;
    }

    @Override
    protected void init() {
        active = true;
        clearWidgets();
        int left = Math.max(8, (width - 332) / 2);
        int col = Math.min(104, (width - 24) / 3);
        providerButton = addRenderableWidget(Button.builder(Component.literal(providerLabel()), b -> nextProvider())
                .bounds(left, 28, col * 2 + 6, 20).build());
        statusButton = addRenderableWidget(Button.builder(Component.literal(status == null ? text("identity.status") : status), b -> { })
                .bounds(left + (col + 6) * 2, 28, col, 20).build());
        identityId = field(left, 52, col * 2 + 6, text("identity.id"));
        targetId = field(left + (col + 6) * 2, 52, col, text("identity.target_id"));
        entityUuid = field(left, 76, col * 2 + 6, text("identity.entity_uuid"));
        claimCode = field(left + (col + 6) * 2, 76, col, text("identity.claim_code"));
        bindingId = field(left, 100, col * 2 + 6, text("identity.binding_id"));
        revision = field(left + (col + 6) * 2, 100, col, text("identity.revision"));

        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.refresh"), b -> refresh())
                .bounds(left, 128, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.verify"), b -> verify())
                .bounds(left + col + 6, 128, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.register_offline"), b -> registerOffline())
                .bounds(left + (col + 6) * 2, 128, col, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.request_approval"), b -> requestApproval())
                .bounds(left, 152, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.create_claim"), b -> createClaim())
                .bounds(left + col + 6, 152, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.redeem_claim"), b -> redeemClaim())
                .bounds(left + (col + 6) * 2, 152, col, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.revoke_claim"), b -> revokeClaim())
                .bounds(left, 176, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.approve_binding"), b -> approveBinding())
                .bounds(left + col + 6, 176, col + 54, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(this.width - left - col, 176, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.select_identity"), b -> selectIdentity())
                .bounds(left, 200, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.refresh_bindings"), b -> refreshBindings())
                .bounds(left + col + 6, 200, col, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.sparkle_morpher.cloud.manage.identity.select_binding"), b -> selectBinding())
                .bounds(left + (col + 6) * 2, 200, col, 20).build());
        refresh();
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox box = new EditBox(font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(256);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    private String providerLabel() {
        if (providers.isEmpty()) return text("identity.no_provider");
        CloudIdentityClient.IdentityProvider selected = providers.get(Math.floorMod(providerIndex, providers.size()));
        return selected.displayName() + "  [" + selected.providerId() + "]";
    }

    private void nextProvider() {
        if (!providers.isEmpty()) providerIndex = (providerIndex + 1) % providers.size();
        providerButton.setMessage(Component.literal(providerLabel()));
    }

    private void refresh() {
        long expected = generation;
        CompletableFuture<List<CloudIdentityClient.IdentityProvider>> providerRequest;
        CompletableFuture<List<CloudIdentityClient.CloudIdentity>> identityRequest;
        try {
            providerRequest = management.identityProviders();
            identityRequest = management.identities();
        } catch (RuntimeException failure) {
            setStatus(error(failure));
            return;
        }
        CompletableFuture.allOf(providerRequest, identityRequest).whenComplete((ignored, failure) ->
                Minecraft.getInstance().execute(() -> {
                    if (!active || expected != generation) return;
                    if (failure != null) setStatus(error(failure));
                    else {
                        providers = providerRequest.join();
                        identities = identityRequest.join();
                        providerIndex = Math.min(providerIndex, Math.max(0, providers.size() - 1));
                        providerButton.setMessage(Component.literal(providerLabel()));
                        setStatus(text("identity.loaded", identities.size()));
                        refreshBindings();
                    }
                }));
    }

    private void selectIdentity() {
        if (identities.isEmpty()) { setStatus(text("identity.none_loaded")); return; }
        var identity = identities.get(Math.floorMod(identityIndex++, identities.size()));
        identityId.setValue(identity.identityId());
        setStatus(identity.displayName() + " — " + identity.identity() + " [" + identity.verificationStatus() + "]");
    }

    private void refreshBindings() {
        if (management.snapshot().selectedScope() == null) return;
        long expected = generation;
        management.offlineBindings().whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            if (!active || expected != generation) return;
            if (failure == null) bindings = result;
            else setStatus(error(failure));
        }));
    }

    private void selectBinding() {
        if (bindings.isEmpty()) { setStatus(text("identity.no_bindings")); return; }
        var binding = bindings.get(Math.floorMod(bindingIndex++, bindings.size()));
        bindingId.setValue(binding.bindingId());
        revision.setValue(Long.toString(binding.revision()));
        identityId.setValue(binding.identityId());
        targetId.setValue(binding.targetId());
        setStatus(text("identity.binding_status", binding.status(), binding.entityUuid(), binding.revision()));
    }

    private void verify() {
        if (providers.isEmpty()) { setStatus(text("identity.refresh_first")); return; }
        try {
            var profile = MinecraftSessionServiceJoiner.currentProfile();
            var client = com.micaftic.morpher.cloud.client.CloudClientRuntime.state().identities();
            var provider = providers.get(Math.floorMod(providerIndex, providers.size()));
            run(client.createChallenge(provider.providerId(), profile.name(), profile.profileId().toString())
                    .thenCompose(challenge -> client.joinAndComplete(challenge, new MinecraftSessionServiceJoiner())),
                    identity -> text("identity.verified", identity.displayName(), identity.identityId()));
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void registerOffline() {
        try {
            var profile = MinecraftSessionServiceJoiner.currentProfile();
            run(management.registerOfflineIdentity(profile.profileId(), profile.name()),
                    identity -> text("identity.pending", identity.identityId()));
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void requestApproval() {
        try {
            run(management.requestOfflineApproval(identityId.getValue().trim(), targetId.getValue().trim()),
                    binding -> text("identity.approval_requested", binding.bindingId(), binding.revision()));
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void createClaim() {
        try {
            run(management.createClaimCode(targetId.getValue().trim(), entityUuid.getValue().trim(), 600L),
                    claim -> { claimCode.setValue(claim.code()); return text("identity.claim_created"); });
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void redeemClaim() {
        try {
            run(management.redeemClaimCode(claimCode.getValue().trim(), identityId.getValue().trim()),
                    binding -> text("identity.claim_redeemed", binding.bindingId()));
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void revokeClaim() {
        try { run(management.revokeClaimCode(claimCode.getValue().trim()), ignored -> text("identity.claim_revoked")); }
        catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void approveBinding() {
        try {
            long expectedRevision = Long.parseLong(revision.getValue().trim());
            run(management.approveBinding(bindingId.getValue().trim(), expectedRevision),
                    binding -> text("identity.binding_approved", binding.revision()));
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private <T> void run(CompletableFuture<T> future, Function<T, String> message) {
        long expected = generation;
        setStatus(text("working"));
        future.whenComplete((value, failure) -> Minecraft.getInstance().execute(() -> {
            if (!active || expected != generation) return;
            setStatus(failure == null ? message.apply(value) : error(failure));
            if (failure == null && value instanceof CloudIdentityClient.CloudIdentity) refresh();
        }));
    }

    private void setStatus(String value) {
        status = value;
        if (statusButton != null) statusButton.setMessage(Component.literal(value));
    }

    private static String text(String key, Object... args) {
        return CloudManagementScreen.text(key, args);
    }

    private static String error(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    @Override
    public void removed() {
        active = false;
        generation++;
    }

    @Override
    public void onClose() {
        InputUtil.setScreen(parent);
    }
}
