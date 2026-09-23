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
        super(Component.literal("SPM Cloud identities"));
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
        statusButton = addRenderableWidget(Button.builder(Component.literal(status == null ? "Identity and Offline tools" : status), b -> { })
                .bounds(left + (col + 6) * 2, 28, col, 20).build());
        identityId = field(left, 52, col * 2 + 6, "Identity ID / Offline ID");
        targetId = field(left + (col + 6) * 2, 52, col, "Target ID");
        entityUuid = field(left, 76, col * 2 + 6, "Entity UUID for claim");
        claimCode = field(left + (col + 6) * 2, 76, col, "Claim code");
        bindingId = field(left, 100, col * 2 + 6, "Pending binding ID");
        revision = field(left + (col + 6) * 2, 100, col, "Expected revision");

        addRenderableWidget(Button.builder(Component.literal("Refresh providers / identities"), b -> refresh())
                .bounds(left, 128, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Verify current game identity"), b -> verify())
                .bounds(left + col + 6, 128, col + 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Register Offline"), b -> registerOffline())
                .bounds(left + (col + 6) * 2 + 54, 128, col - 54, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Request Offline approval"), b -> requestApproval())
                .bounds(left, 152, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Create target claim code"), b -> createClaim())
                .bounds(left + col + 6, 152, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Redeem claim code"), b -> redeemClaim())
                .bounds(left + (col + 6) * 2, 152, col, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Revoke claim code"), b -> revokeClaim())
                .bounds(left, 176, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Approve binding revision"), b -> approveBinding())
                .bounds(left + col + 6, 176, col + 54, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width - left - col, 176, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Select identity"), b -> selectIdentity())
                .bounds(left, 200, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh scope approvals"), b -> refreshBindings())
                .bounds(left + col + 6, 200, col, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Select pending binding"), b -> selectBinding())
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
        if (providers.isEmpty()) return "No trusted provider loaded";
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
                        setStatus("Loaded " + identities.size() + " Cloud identities; use Select identity");
                        refreshBindings();
                    }
                }));
    }

    private void selectIdentity() {
        if (identities.isEmpty()) { setStatus("No identities loaded"); return; }
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
        if (bindings.isEmpty()) { setStatus("No Offline bindings in the selected scope"); return; }
        var binding = bindings.get(Math.floorMod(bindingIndex++, bindings.size()));
        bindingId.setValue(binding.bindingId());
        revision.setValue(Long.toString(binding.revision()));
        identityId.setValue(binding.identityId());
        targetId.setValue(binding.targetId());
        setStatus(binding.status() + " — entity " + binding.entityUuid() + " (rev " + binding.revision() + ")");
    }

    private void verify() {
        if (providers.isEmpty()) { setStatus("Refresh the trusted provider list first"); return; }
        try {
            var profile = MinecraftSessionServiceJoiner.currentProfile();
            var client = com.micaftic.morpher.cloud.client.CloudClientRuntime.state().identities();
            var provider = providers.get(Math.floorMod(providerIndex, providers.size()));
            run(client.createChallenge(provider.providerId(), profile.name(), profile.profileId().toString())
                    .thenCompose(challenge -> client.joinAndComplete(challenge, new MinecraftSessionServiceJoiner())),
                    identity -> "Verified " + identity.displayName() + " (" + identity.identityId() + ")");
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void registerOffline() {
        try {
            var profile = MinecraftSessionServiceJoiner.currentProfile();
            run(management.registerOfflineIdentity(profile.profileId(), profile.name()),
                    identity -> "Pending Offline identity: " + identity.identityId());
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void requestApproval() {
        try {
            run(management.requestOfflineApproval(identityId.getValue().trim(), targetId.getValue().trim()),
                    binding -> "Approval requested: " + binding.bindingId() + " rev " + binding.revision());
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void createClaim() {
        try {
            run(management.createClaimCode(targetId.getValue().trim(), entityUuid.getValue().trim(), 600L),
                    claim -> { claimCode.setValue(claim.code()); return "One-time claim code created (10 min)"; });
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void redeemClaim() {
        try {
            run(management.redeemClaimCode(claimCode.getValue().trim(), identityId.getValue().trim()),
                    binding -> "Claim redeemed: " + binding.bindingId());
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void revokeClaim() {
        try { run(management.revokeClaimCode(claimCode.getValue().trim()), ignored -> "Claim code revoked"); }
        catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private void approveBinding() {
        try {
            long expectedRevision = Long.parseLong(revision.getValue().trim());
            run(management.approveBinding(bindingId.getValue().trim(), expectedRevision),
                    binding -> "Binding approved at revision " + binding.revision());
        } catch (RuntimeException failure) { setStatus(error(failure)); }
    }

    private <T> void run(CompletableFuture<T> future, Function<T, String> message) {
        long expected = generation;
        setStatus("Working...");
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
