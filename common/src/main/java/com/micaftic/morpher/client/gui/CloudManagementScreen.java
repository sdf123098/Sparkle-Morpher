package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.cloud.client.CloudConnectionController;
import com.micaftic.morpher.cloud.client.CloudInstanceRegistry;
import com.micaftic.morpher.cloud.client.CloudManagementController;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Client UI for connecting to an official or community SPM Cloud instance. */
public final class CloudManagementScreen extends Screen {
    private static final String CLIENT_VERSION = "2.0.0";
    private static CloudManagementController management;

    private final Screen parent;
    private EditBox instanceId;
    private EditBox instanceName;
    private EditBox origin;
    private EditBox accountId;
    private EditBox password;
    private EditBox scopeId;
    private EditBox scopeName;
    private EditBox worldEpoch;
    private Button statusButton;
    private int scopePage;
    private String statusMessage;
    private boolean active;
    private long lifecycleGeneration;

    public CloudManagementScreen(Screen parent) {
        super(Component.literal("SPM Cloud"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        InputUtil.setScreen(new CloudManagementScreen(parent));
    }

    static synchronized CloudManagementController management() {
        if (management == null) {
            Path config = Minecraft.getInstance().gameDirectory.toPath()
                    .resolve("config").resolve("sparkle-morpher").resolve("cloud-instances.json");
            CloudInstanceRegistry registry = new CloudInstanceRegistry(config);
            management = new CloudManagementController(registry, new CloudConnectionController(),
                    CloudClientRuntime.defaultCacheRoot(), CLIENT_VERSION);
            try {
                management.loadInstances();
            } catch (IOException failure) {
                throw new IllegalStateException("Failed to load the SPM Cloud instance registry", failure);
            }
        }
        return management;
    }

    @Override
    protected void init() {
        this.active = true;
        clearWidgets();
        int left = Math.max(8, (this.width - 332) / 2);
        int width = Math.min(104, (this.width - 24) / 3);
        this.instanceId = field(left, 28, width, "Instance ID");
        this.instanceName = field(left + width + 6, 28, width, "Name");
        this.origin = field(left + (width + 6) * 2, 28, width, "https://cloud.example");
        this.accountId = field(left, 52, width + 54, "Cloud account");
        this.password = field(left + width + 60, 52, width + 54, "Password");
        this.password.setMaxLength(256);
        this.password.setSuggestion("Password");
        this.scopeId = field(left, 76, width, "Scope ID");
        this.scopeName = field(left + width + 6, 76, width, "Scope name");
        this.worldEpoch = field(left + (width + 6) * 2, 76, width, "World epoch");

        var snapshot = management().snapshot();
        String state = this.statusMessage != null ? this.statusMessage
                : snapshot.authenticated() ? "Connected" : "Disconnected";
        int switchX = this.width - left - 100;
        int statusWidth = Math.max(64, switchX - left - 52);
        this.statusButton = addRenderableWidget(Button.builder(Component.literal(state), button -> { })
                .bounds(left, 103, statusWidth, 20).build());
        Button previousScopePage = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            this.scopePage--;
            init();
        }).bounds(left + statusWidth + 2, 103, 24, 20).build());
        Button nextScopePage = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            this.scopePage++;
            init();
        }).bounds(left + statusWidth + 28, 103, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Next instance"), button -> switchInstance())
                .bounds(switchX, 103, 100, 20).build());
        management().registry().selected().ifPresent(profile -> {
            this.instanceId.setValue(profile.instanceId());
            this.instanceName.setValue(profile.name());
            this.origin.setValue(profile.instance().origin().toString());
        });
        addRenderableWidget(Button.builder(Component.literal("Save instance"), button -> saveInstance())
                .bounds(left, 127, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Login / refresh"), button -> login())
                .bounds(left + width + 6, 127, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Logout"), button -> logout())
                .bounds(left + (width + 6) * 2, 127, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Create scope"), button -> createScope())
                .bounds(left, 151, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh scopes"), button -> refreshScopes())
                .bounds(left + width + 6, 151, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Join selected"), button -> joinScope())
                .bounds(left + (width + 6) * 2, 151, width, 20).build());

        if (snapshot.selectedScope() != null) {
            this.scopeId.setValue(snapshot.selectedScope().scopeId());
            this.scopeName.setValue(snapshot.selectedScope().name());
            this.worldEpoch.setValue(snapshot.selectedScope().worldEpoch());
        }
        int rowsVisible = Math.max(1, (this.height - 32 - 177) / 22);
        var scopeRange = CloudScreenPagination.range(snapshot.scopes().size(), rowsVisible, this.scopePage);
        this.scopePage = scopeRange.page();
        previousScopePage.active = scopeRange.page() > 0;
        nextScopePage.active = scopeRange.page() + 1 < scopeRange.pageCount();
        int row = 0;
        for (var scope : snapshot.scopes().subList(scopeRange.startInclusive(), scopeRange.endExclusive())) {
            final String id = scope.scopeId();
            int y = 177 + row++ * 22;
            addRenderableWidget(Button.builder(Component.literal(scope.name() + "  [" + id + "]"), button -> {
                try {
                    management().selectScope(id);
                    this.scopeId.setValue(id);
                    this.worldEpoch.setValue(scope.worldEpoch());
                    setStatus("Selected " + scope.name());
                } catch (RuntimeException failure) {
                    setStatus(errorText(failure));
                }
            }).bounds(left, y, Math.max(180, this.width - left * 2 - 82), 20).build());
            addRenderableWidget(Button.builder(Component.literal("Targets / ACL"), button -> {
                try {
                    management().selectScope(id);
                    InputUtil.setScreen(new CloudTargetManagementScreen(this, management()));
                } catch (RuntimeException failure) {
                    setStatus(errorText(failure));
                }
            }).bounds(this.width - left - 78, y, 78, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 50, this.height - 27, 100, 20).build());
    }

    private EditBox field(int x, int y, int width, String hint) {
        EditBox editBox = new EditBox(this.font, x, y, width, 20, Component.literal(hint));
        editBox.setMaxLength(256);
        editBox.setHint(Component.literal(hint));
        addRenderableWidget(editBox);
        return editBox;
    }

    private void saveInstance() {
        try {
            CloudInstanceConfig config = CloudInstanceConfig.v1(instanceId.getValue(), URI.create(origin.getValue().trim()));
            CloudInstanceRegistry.CloudInstanceProfile profile = new CloudInstanceRegistry.CloudInstanceProfile(
                    config, instanceName.getValue().isBlank() ? config.instanceId() : instanceName.getValue().trim());
            management().registry().addOrReplace(profile);
            management().selectInstance(config.instanceId());
            management().saveInstances();
            setStatus("Saved " + config.instanceId());
        } catch (IOException | RuntimeException failure) {
            setStatus(errorText(failure));
        }
    }

    private void switchInstance() {
        try {
            var profiles = management().registry().profiles();
            if (profiles.isEmpty()) {
                setStatus("No saved Cloud instances");
                return;
            }
            String selected = management().registry().selected().map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse(null);
            int current = -1;
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).instanceId().equals(selected)) current = i;
            }
            var next = profiles.get((current + 1) % profiles.size());
            management().selectInstance(next.instanceId());
            management().saveInstances();
            init();
            setStatus("Selected " + next.name());
        } catch (IOException | RuntimeException failure) {
            setStatus(errorText(failure));
        }
    }

    private void login() {
        try {
            String secret = password.getValue();
            password.setValue("");
            run(management().login(accountId.getValue(), secret), "Cloud login succeeded");
        } catch (RuntimeException failure) {
            setStatus(errorText(failure));
        }
    }

    private void logout() {
        management().logout();
        password.setValue("");
        setStatus("Logged out");
    }

    private void refreshScopes() {
        try {
            run(management().refreshScopes(), "Scopes refreshed");
        } catch (RuntimeException failure) {
            setStatus(errorText(failure));
        }
    }

    private void createScope() {
        try {
            var create = new com.micaftic.morpher.cloud.client.CloudScopeClient.CloudScopeCreate(
                    scopeId.getValue().trim(), scopeName.getValue().trim(), worldEpoch.getValue().trim(), null);
            run(management().createScope(create), "Scope created");
        } catch (RuntimeException failure) {
            setStatus(errorText(failure));
        }
    }

    private void joinScope() {
        try {
            if (management().snapshot().selectedScope() == null) management().selectScope(scopeId.getValue().trim());
            run(management().enterSelectedScope(), "Joined Cloud scope");
        } catch (RuntimeException failure) {
            setStatus(errorText(failure));
        }
    }

    private void run(CompletableFuture<?> future, String success) {
        long expectedGeneration = this.lifecycleGeneration;
        setStatus("Working...");
        future.whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
            if (!this.active || expectedGeneration != this.lifecycleGeneration) return;
            setStatus(failure == null ? success : errorText(failure));
            if (failure == null) init();
        }));
    }

    @Override
    public void removed() {
        this.active = false;
        this.lifecycleGeneration++;
    }

    private void setStatus(String message) {
        this.statusMessage = message;
        if (this.statusButton != null) this.statusButton.setMessage(Component.literal(message));
    }

    static String errorText(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    @Override
    public void onClose() {
        InputUtil.setScreen(this.parent);
    }
}
