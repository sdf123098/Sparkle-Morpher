package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.picker.FilePickerCoordinator;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.cloud.client.CloudInstanceRegistry;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import net.minecraft.Util;
import com.micaftic.morpher.util.InputUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Unified local model upload workspace, shared by all entry points. */
public class CloudUploadWorkspaceScreen extends Screen implements ModelUploadSession.Listener {
    private static final int TEXT = 0xFFE9E3D5, MUTED = 0xFFA1B4B2, ACCENT = 0xFF79D7BE, ERROR = 0xFFFF9990;
    private final Screen parent;
    private final Map<String, Path> sources = new LinkedHashMap<>();
    private final Set<String> selected = new LinkedHashSet<>();
    private final Map<String, String> states = new HashMap<>();
    private final Map<String, Component> failures = new HashMap<>();
    private final ArrayDeque<String> pending = new ArrayDeque<>();
    private final List<FlatColorButton> settings = new ArrayList<>();
    private String instanceId, uploadInstanceId, activeId;
    private ModelUploadSession activeSession, handledSession;
    private CompletableFuture<PreparedUpload> preparation;
    private boolean publicVisibility, uploading, listening, refreshing;
    private EditBox search;
    private FlatColorButton primary, failureDetails;
    private int scroll;
    private Component status = Component.empty();

    public CloudUploadWorkspaceScreen(Screen parent, Collection<String> modelIds) {
        super(Component.translatable("gui.sparkle_morpher.cloud_upload.title"));
        this.parent = parent;
        reloadSnapshot();
        for (String id : modelIds) if (sources.containsKey(id)) selected.add(id);
        instanceId = CloudManagementScreen.management().registry().selected()
                .map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse("");
    }

    private void reloadSnapshot() {
        sources.clear();
        ClientModelManager.snapshotLocalCustomSources().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(entry -> sources.put(entry.getKey(), entry.getValue()));
        selected.retainAll(sources.keySet());
        states.keySet().retainAll(sources.keySet());
        failures.keySet().retainAll(sources.keySet());
        sources.keySet().forEach(id -> states.putIfAbsent(id, "waiting"));
        scroll = 0;
    }

    private int panelW() { return Math.min(880, width - 16); }
    private int panelH() { return Math.min(560, height - 16); }
    private int panelX() { return (width - panelW()) / 2; }
    private int panelY() { return (height - panelH()) / 2; }
    private int listY() { return panelY() + 104; }
    private int listH() { return Math.max(1, panelH() - 172); }
    private int rowH() { return height < 360 ? 24 : 32; }
    private int rows() { return Math.max(1, listH() / rowH()); }
    private Component fit(Component text, int width) {
        return font.width(text) <= width ? text : Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(0, width - font.width("…"))) + "…");
    }
    private Component label(String key) { return Component.translatable("gui.sparkle_morpher.cloud_upload." + key); }
    private Component targetLabel() {
        String name = CloudManagementScreen.management().registry().find(instanceId)
                .map(CloudManagementScreen::displayName).orElse("—");
        return Component.translatable("gui.sparkle_morpher.cloud_upload.target", name);
    }
    private Component visibilityLabel() {
        return Component.translatable("gui.sparkle_morpher.import.visibility." + (publicVisibility ? "public" : "private"));
    }
    private FlatColorButton setting(int x, int y, int w, int h, Component label, net.minecraft.client.gui.components.Button.OnPress action) {
        FlatColorButton button = addRenderableWidget(new FlatColorButton(x, y, w, h, label, action));
        settings.add(button);
        return button;
    }

    @Override
    protected void init() {
        String query = search == null ? "" : search.getValue();
        clearWidgets();
        settings.clear();
        if (!listening) { ModelUploadSession.addListener(this); listening = true; }
        int x = panelX() + 12, y = panelY(), w = panelW() - 24, targetW = w - 136;
        setting(x, y + 32, targetW, 20, fit(targetLabel(), targetW - 12), button -> cycleTarget())
                .setTooltipLines(List.of(targetLabel()));
        setting(x + targetW + 4, y + 32, 64, 20, visibilityLabel(), button -> {
            publicVisibility = !publicVisibility;
            states.replaceAll((id, state) -> "waiting");
            failures.clear();
            status = Component.empty();
            button.setMessage(visibilityLabel());
        }).setTooltipText("gui.sparkle_morpher.import.visibility.tooltip");
        setting(x + w - 64, y + 32, 64, 20, label("login"), button -> openAccount());
        search = new EditBox(font, x, y + 58, w - 52, 18, label("search"));
        search.setMaxLength(128);
        search.setHint(label("search"));
        search.setValue(query);
        search.setResponder(value -> scroll = 0);
        addRenderableWidget(search);
        setting(x + w - 48, y + 57, 22, 20, Component.literal("↻"), button -> refresh())
                .setTooltipText("gui.sparkle_morpher.upload_custom_folder.refresh");
        setting(x + w - 22, y + 57, 22, 20, Component.literal("…"), button -> openFolder())
                .setTooltipText("gui.sparkle_morpher.upload_custom_folder.open_folder");
        setting(x, y + 82, 70, 16, label("select_all"), button -> selected.addAll(filtered()));
        setting(x + 74, y + 82, 70, 16, label("clear_selection"), button -> selected.clear());
        int bottom = y + panelH() - 30;
        failureDetails = addRenderableWidget(new FlatColorButton(x, bottom, Math.min(112, w - 190), 20, label("failure_details"),
                button -> openFailure(null)));
        primary = addRenderableWidget(new FlatColorButton(x + w - 182, bottom, 112, 20, label("start"),
                button -> { if (uploading) cancel(); else begin(); }));
        addRenderableWidget(new FlatColorButton(x + w - 64, bottom, 64, 20, Component.translatable("gui.done"), button -> onClose()));
        updateButtons();
    }

    private List<String> filtered() {
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        return sources.keySet().stream().filter(id -> query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    private void updateButtons() {
        if (failureDetails != null) failureDetails.active = !uploading && !failures.isEmpty();
        settings.forEach(button -> button.active = !uploading && !refreshing);
        if (search != null) search.setEditable(!uploading);
        if (primary != null) {
            primary.setMessage(label(uploading ? "cancel" : "start"));
            primary.active = uploading || (!refreshing && selected.stream().anyMatch(id -> !"complete".equals(states.get(id))));
        }
    }
    private void cycleTarget() {
        var profiles = CloudManagementScreen.management().registry().profiles();
        if (uploading || profiles.isEmpty()) return;
        int index = -1;
        for (int i = 0; i < profiles.size(); i++) if (profiles.get(i).instanceId().equals(instanceId)) index = i;
        instanceId = profiles.get((index + 1) % profiles.size()).instanceId();
        states.replaceAll((id, state) -> "waiting");
        failures.clear();
        status = Component.empty();
        init();
    }
    private void openAccount() {
        if (parent instanceof ModernPlayerModelScreen panel) {
            InputUtil.setScreen(panel);
            panel.openCloudAccountForUpload(this, instanceId);
        } else CloudManagementScreen.open(this);
    }
    private void refresh() {
        if (uploading || refreshing) return;
        refreshing = true;
        status = Component.translatable("gui.sparkle_morpher.import.state.folder_reloading");
        updateButtons();
        ClientModelManager.reloadLocalModels(error -> Minecraft.getInstance().execute(() -> {
            refreshing = false;
            status = error == null ? Component.translatable("gui.sparkle_morpher.import.state.folder_reloaded") : error;
            if (error == null) reloadSnapshot();
            updateButtons();
        }));
    }
    private void openFolder() {
        try {
            Files.createDirectories(ModelStoragePaths.custom());
            Util.getPlatform().openFile(ModelStoragePaths.custom().toFile());
        } catch (IOException error) {
            status = Component.translatable("gui.sparkle_morpher.import.error.open_folder", error.getMessage());
        }
    }
    private void begin() {
        if (uploading || refreshing) return;
        if (selected.isEmpty()) { status = label("empty_selection"); return; }
        if (CloudClientRuntime.uploadTransport(instanceId) == null) { status = label("unavailable"); return; }
        ModelUploadSession current = ModelUploadSession.getInstance();
        if (current != null && !current.isTerminal()) {
            status = Component.translatable("gui.sparkle_morpher.import.error.in_progress"); return;
        }
        pending.clear();
        for (String id : selected) if (!"complete".equals(states.get(id))) {
            pending.add(id); states.put(id, "waiting"); failures.remove(id);
        }
        uploadInstanceId = instanceId;
        uploading = true;
        updateButtons();
        startNext();
    }
    private void startNext() {
        activeSession = null;
        activeId = pending.poll();
        if (activeId == null) {
            uploading = false;
            long failed = selected.stream().filter(failures::containsKey).count();
            status = failed == 0 ? label("complete") : Component.translatable("gui.sparkle_morpher.cloud_upload.partial_failure", failed);
            updateButtons(); return;
        }
        String id = activeId;
        Path source = sources.get(id);
        states.put(id, "preparing");
        status = Component.translatable("gui.sparkle_morpher.cloud_upload.preparing", id);
        preparation = CompletableFuture.supplyAsync(() -> {
            try {
                if (source == null || !Files.exists(source)) throw new IOException("Local model source is missing: " + id);
                if (!Files.isDirectory(source)) return new PreparedUpload(source, source.getFileName().toString(), false);
                Path temp = Files.createTempFile("spm-cloud-folder-", ".zip");
                try {
                    Files.write(temp, FilePickerCoordinator.packDirectory(source).data());
                    return new PreparedUpload(temp, id + ".zip", true);
                } catch (IOException | RuntimeException error) { Files.deleteIfExists(temp); throw error; }
            } catch (IOException error) { throw new java.util.concurrent.CompletionException(error); }
        });
    }
    @Override
    public void tick() {
        super.tick();
        updateButtons();
        if (!uploading || preparation == null || !preparation.isDone()) return;
        var ready = preparation;
        preparation = null;
        PreparedUpload prepared = null;
        try {
            prepared = ready.join();
            Component error = ModelUploadSession.start(activeId, prepared.fileName(), prepared.source(),
                    publicVisibility ? "PUBLIC" : "PRIVATE", uploadInstanceId, prepared.temporary());
            if (error != null) { prepared.cleanUp(); fail(error); }
            else {
                states.put(activeId, "uploading");
                activeSession = ModelUploadSession.getInstance();
                handleSession(activeSession);
            }
        } catch (RuntimeException error) {
            if (prepared != null) prepared.cleanUp();
            fail(Component.literal(CloudManagementScreen.errorText(error)));
        }
    }
    private void fail(Component reason) { states.put(activeId, "failed"); failures.put(activeId, reason); startNext(); }
    private void openFailure(String selectedId) {
        var details = sources.keySet().stream().filter(failures::containsKey)
                .map(id -> new CloudUploadFailureScreen.Failure(id, sources.get(id).getFileName().toString(), failures.get(id))).toList();
        if (!uploading && !details.isEmpty()) InputUtil.setScreen(new CloudUploadFailureScreen(this, details, selectedId));
    }
    private void cancel() {
        if (!uploading) return;
        uploading = false;
        pending.forEach(id -> states.put(id, "cancelled"));
        pending.clear();
        if (activeId != null) states.put(activeId, "cancelled");
        // supplyAsync cancellation cannot stop IO; clean its eventual result instead.
        if (preparation != null) { preparation.thenAccept(PreparedUpload::cleanUp); preparation = null; }
        if (activeSession != null && ModelUploadSession.getInstance() == activeSession && !activeSession.isTerminal())
            ModelUploadSession.failCurrent(label("cancelled"));
        activeSession = null; activeId = null;
        status = label("cancelled"); updateButtons();
    }
    @Override
    public void onSessionUpdate(ModelUploadSession session) { Minecraft.getInstance().execute(() -> handleSession(session)); }
    private void handleSession(ModelUploadSession session) {
        if (!uploading || session == null || session != activeSession) return;
        if (!session.isTerminal()) { status = session.getMessage(); return; }
        if (handledSession == session) return;
        handledSession = session;
        if (session.getState() == ModelUploadSession.State.FAILED) fail(session.getMessage());
        else { states.put(activeId, "complete"); failures.remove(activeId); ModelUploadSession.clearIfTerminal(); startNext(); }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = panelX(), y = panelY(), w = panelW(), h = panelH();
        g.fill(0, 0, width, height, 0xDA0A1417);
        g.fill(x, y, x + w, y + h, 0xF019282D);
        g.fill(x, y, x + 3, y + h, ACCENT);
        g.drawString(font, title, x + 12, y + 12, TEXT);
        String account = CloudManagementScreen.management().accountId(instanceId);
        Component accountText = Component.translatable("gui.sparkle_morpher.cloud_upload.account", account.isBlank() ? "—" : account);
        Component shown = fit(accountText, Math.max(30, w - font.width(title) - 42));
        g.drawString(font, shown, x + w - 12 - font.width(shown), y + 12, MUTED);
        if (mouseY >= y + 8 && mouseY < y + 25 && mouseX > x + w - 12 - font.width(shown))
            g.renderComponentTooltip(font, List.of(accountText), mouseX, mouseY);
        g.drawString(font, fit(Component.translatable("gui.sparkle_morpher.cloud_upload.selection", selected.size(), sources.size()), w - 180),
                x + 166, y + 86, MUTED);
        g.fill(x + 12, listY(), x + w - 12, listY() + listH(), 0xFF102025);
        List<String> visible = filtered();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - rows())));
        if (visible.isEmpty()) g.drawString(font, fit(label("no_results"), w - 42), x + 22, listY() + 10, MUTED);
        g.enableScissor(x + 12, listY(), x + w - 12, listY() + listH());
        List<Component> rowTooltip = null;
        for (int i = 0; i < rows() && scroll + i < visible.size(); i++) {
            String id = visible.get(scroll + i);
            int rowY = listY() + i * rowH();
            boolean checked = selected.contains(id);
            boolean hovered = mouseX >= x + 12 && mouseX < x + w - 12 && mouseY >= rowY && mouseY < rowY + rowH();
            g.fill(x + 12, rowY, x + w - 12, rowY + rowH() - 1,
                    checked ? 0xFF284B4B : hovered ? 0xFF25383E : (i % 2 == 0 ? 0xFF1C3036 : 0xFF17292F));
            g.fill(x + 20, rowY + 7, x + 29, rowY + 16, checked ? ACCENT : MUTED);
            if (!checked) g.fill(x + 21, rowY + 8, x + 28, rowY + 15, 0xFF17292F);
            g.drawString(font, fit(Component.literal(id), w - 140), x + 38, rowY + 5, TEXT);
            String file = sources.get(id).getFileName().toString();
            if (rowH() >= 32) g.drawString(font, fit(Component.literal(file), w - 140), x + 38, rowY + 18, MUTED);
            String state = states.getOrDefault(id, "waiting");
            Component stateText = label("state." + state);
            if (id.equals(activeId) && activeSession != null && !activeSession.isTerminal())
                stateText = Component.literal(Math.round(activeSession.getProgress() * 100) + "%");
            g.drawString(font, fit(stateText, 72), x + w - 94, rowY + 8, state.equals("failed") ? ERROR : ACCENT);
            if (hovered) {
                List<Component> tooltip = new ArrayList<>(List.of(Component.literal(id), Component.literal(file)));
                if (failures.containsKey(id)) tooltip.add(failures.get(id));
                if (failures.containsKey(id)) tooltip.add(label("inspect_failure"));
                rowTooltip = tooltip;
            }
        }
        if (visible.size() > rows()) {
            int thumbH = Math.max(8, listH() * rows() / visible.size());
            int thumbY = listY() + (listH() - thumbH) * scroll / Math.max(1, visible.size() - rows());
            g.fill(x + w - 15, thumbY, x + w - 13, thumbY + thumbH, ACCENT);
        }
        g.disableScissor();
        int footerY = y + h - 61;
        g.fill(x + 12, footerY, x + w - 12, footerY + 2, 0xFF31464B);
        if (uploading && activeSession != null) g.fill(x + 12, footerY,
                x + 12 + (int) ((w - 24) * activeSession.getProgress()), footerY + 2, ACCENT);
        int lineY = footerY + 7;
        for (var line : font.split(status, w - 24).stream().limit(2).toList()) {
            g.drawString(font, line, x + 12, lineY, failures.isEmpty() ? MUTED : ERROR); lineY += 10;
        }
        if (!status.getString().isBlank() && mouseY >= footerY + 3 && mouseY < footerY + 29)
            g.renderComponentTooltip(font, List.of(status), mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        settings.forEach(button -> button.renderTooltip(g, this, mouseX, mouseY));
        if (rowTooltip != null) g.renderComponentTooltip(font, rowTooltip, mouseX, mouseY);
    }
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && !uploading && !refreshing && mx >= panelX() + 12 && mx < panelX() + panelW() - 12
                && my >= listY() && my < listY() + listH()) {
            int row = ((int) my - listY()) / rowH(), index = scroll + row;
            List<String> visible = filtered();
            if (row < rows() && index < visible.size()) {
                String id = visible.get(index);
                if (failures.containsKey(id) && mx >= panelX() + 34) { openFailure(id); return true; }
                if (!selected.add(id)) selected.remove(id);
                else if ("complete".equals(states.get(id))) states.put(id, "waiting");
                updateButtons(); return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (mx >= panelX() + 12 && mx < panelX() + panelW() - 12 && my >= listY() && my < listY() + listH() && scrollY != 0) {
            scroll = Math.max(0, Math.min(Math.max(0, filtered().size() - rows()), scroll + (scrollY > 0 ? -1 : 1))); return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
    @Override
    public void removed() {
        cancel();
        if (listening) { ModelUploadSession.removeListener(this); listening = false; }
    }
    @Override
    public void onClose() { cancel(); InputUtil.setScreen(parent); }
    @Override
    public boolean isPauseScreen() { return false; }
    private record PreparedUpload(Path source, String fileName, boolean temporary) {
        void cleanUp() { if (temporary) try { Files.deleteIfExists(source); } catch (IOException ignored) { } }
    }
}
