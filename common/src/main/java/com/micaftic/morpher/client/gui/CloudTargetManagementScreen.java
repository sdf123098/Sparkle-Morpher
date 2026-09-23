package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.cloud.client.CloudScopeClient;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

/** Target directory and scope/target ACL controls for the selected Cloud scope. */
public final class CloudTargetManagementScreen extends Screen {
    private final Screen parent;
    private final CloudManagementController management;
    private EditBox targetId;
    private EditBox targetKind;
    private EditBox targetName;
    private EditBox accountId;
    private EditBox role;
    private Button statusButton;

    CloudTargetManagementScreen(Screen parent, CloudManagementController management) {
        super(Component.literal("SPM Cloud targets and ACL"));
        this.parent = parent;
        this.management = management;
    }

    @Override
    protected void init() {
        clearWidgets();
        int left = Math.max(8, (this.width - 332) / 2);
        int width = Math.min(104, (this.width - 24) / 3);
        this.targetId = field(left, 32, width, "Target ID (optional)");
        this.targetKind = field(left + width + 6, 32, width, "PLAYER / FAKE / MAID");
        this.targetName = field(left + (width + 6) * 2, 32, width, "Display name");
        this.accountId = field(left, 56, width + 54, "Cloud account ID");
        this.role = field(left + width + 60, 56, width + 54, "Role");
        this.statusButton = addRenderableWidget(Button.builder(Component.literal("Target and ACL management"), button -> { })
                .bounds(left, 82, Math.max(110, this.width - left * 2), 20).build());

        addRenderableWidget(Button.builder(Component.literal("Create target"), button -> createTarget())
                .bounds(left, 106, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh targets"), button -> refreshTargets())
                .bounds(left + width + 6, 106, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Select target ID"), button -> selectTarget())
                .bounds(left + (width + 6) * 2, 106, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh scope ACL"), button -> refreshScopeAcl())
                .bounds(left, 130, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Set scope ACL"), button -> setScopeAcl())
                .bounds(left + width + 6, 130, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh target ACL"), button -> refreshTargetAcl())
                .bounds(left + (width + 6) * 2, 130, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Set target ACL"), button -> setTargetAcl())
                .bounds(left + width + 6, 154, width, 20).build());

        var snapshot = management.snapshot();
        int row = 0;
        for (var target : snapshot.targets().stream().limit(6).toList()) {
            int y = 178 + row++ * 21;
            addRenderableWidget(Button.builder(Component.literal(target.displayName() + "  [" + target.targetId() + "]"), button -> {
                try {
                    management.selectTarget(target.targetId());
                    this.targetId.setValue(target.targetId());
                    setStatus("Selected " + target.displayName());
                } catch (RuntimeException failure) {
                    setStatus(CloudManagementScreen.errorText(failure));
                }
            }).bounds(left, y, Math.max(180, this.width - left * 2), 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 27, 100, 20).build());
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.literal(hint));
        box.setMaxLength(256);
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    private void createTarget() {
        try {
            String scope = management.snapshot().selectedScope().scopeId();
            run(management.createTarget(new CloudScopeClient.CloudTargetCreate(scope,
                    targetId.getValue().isBlank() ? null : targetId.getValue().trim(),
                    targetKind.getValue().trim(), targetName.getValue().trim())), "Target created");
        } catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void refreshTargets() {
        try { run(management.refreshTargets(), "Targets refreshed"); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void selectTarget() {
        try { management.selectTarget(targetId.getValue().trim()); setStatus("Target selected"); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void refreshScopeAcl() {
        try { run(management.refreshScopeAcl(), "Scope ACL refreshed"); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void refreshTargetAcl() {
        try { run(management.refreshTargetAcl(), "Target ACL refreshed"); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void setScopeAcl() {
        try { run(management.setScopeAcl(new CloudScopeClient.CloudAclUpdate(accountId.getValue().trim(), role.getValue().trim())), "Scope ACL updated"); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void setTargetAcl() {
        try { run(management.setTargetAcl(new CloudScopeClient.CloudAclUpdate(accountId.getValue().trim(), role.getValue().trim())), "Target ACL updated"); }
        catch (RuntimeException failure) { setStatus(CloudManagementScreen.errorText(failure)); }
    }

    private void run(CompletableFuture<?> future, String success) {
        setStatus("Working...");
        future.whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
            if (InputUtil.getCurrentScreen() != this) return;
            setStatus(failure == null ? success : CloudManagementScreen.errorText(failure));
            if (failure == null) init();
        }));
    }

    private void setStatus(String message) {
        if (this.statusButton != null) this.statusButton.setMessage(Component.literal(message));
    }

    @Override
    public void onClose() {
        InputUtil.setScreen(this.parent);
    }
}
