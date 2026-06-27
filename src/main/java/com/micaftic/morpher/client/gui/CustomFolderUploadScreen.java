package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.button.FlatColorButton;
import com.micaftic.morpher.client.gui.button.IconButton;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.util.ClientUiUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

/**
 * Dedicated batch-upload screen for files already present in
 * {@code config/sparkle_morpher/custom/}. It is a separate channel from
 * {@link ModelUploadScreen} (which handles drag-drop / single-file pick) and
 * from the multi-select "选择 → 上传" flow on {@link PlayerModelScreen}.
 */
public class CustomFolderUploadScreen extends Screen implements ModelUploadSession.Listener {

    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 38;
    private static final int LIST_BOTTOM_PADDING = 56;

    private final Screen parentScreen;

    private final List<Entry> entries = new ArrayList<>();
    private final Queue<Entry> pendingUploads = new ArrayDeque<>();

    private FlatColorButton uploadAllButton;
    private FlatColorButton refreshButton;

    private Component error = Component.empty();
    private Component status = Component.empty();
    private ChatFormatting statusColor = ChatFormatting.GRAY;

    private boolean refreshInProgress;
    private int scrollOffset;

    public CustomFolderUploadScreen(Screen parent) {
        super(Component.translatable("gui.sparkle_morpher.upload_custom_folder.title"));
        this.parentScreen = parent;
    }

    @Override
    public void init() {
        clearWidgets();
        ModelUploadSession.addListener(this);

        int buttonY = 10;
        int toolbarX = Math.max(10, this.width - 100);
        this.refreshButton = new IconButton(
                toolbarX, buttonY, 18, 18, 48, 16,
                button -> refreshList(true));
        this.refreshButton.setTooltipText("gui.sparkle_morpher.upload_custom_folder.refresh");
        addRenderableWidget(this.refreshButton);

        addRenderableWidget(new IconButton(
                toolbarX + 24, buttonY, 18, 18, 128, 48,
                button -> openModelFolder()).setTooltipText("gui.sparkle_morpher.upload_custom_folder.open_folder"));

        this.uploadAllButton = new IconButton(
                toolbarX + 48, buttonY, 18, 18, 96, 16,
                button -> uploadAll());
        this.uploadAllButton.setTooltipText("gui.sparkle_morpher.upload_custom_folder.upload_all");
        addRenderableWidget(this.uploadAllButton);

        addRenderableWidget(new IconButton(
                toolbarX + 72, buttonY, 18, 18, 0, 32,
                button -> Minecraft.getInstance().setScreen(this.parentScreen)).setTooltipText("gui.sparkle_morpher.model.return"));

        rebuildEntries();
        updateActionButtonsState();
    }

    @Override
    public void removed() {
        ModelUploadSession.removeListener(this);
        ModelUploadSession.clearIfTerminal();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parentScreen);
    }

    @Override
    public void onSessionUpdate(ModelUploadSession session) {
        if (session == null) {
            return;
        }
        Entry target = findEntryByModelId(session.getModelId());
        if (target != null) {
            target.lastSession = session;
        }
        ModelUploadSession.State state = session.getState();
        if (state == ModelUploadSession.State.COMPLETED) {
            if (target != null) {
                target.completed = true;
                target.queued = false;
                target.failureMessage = null;
            }
            this.status = Component.translatable(
                    "gui.sparkle_morpher.upload_custom_folder.status.completed",
                    session.getModelId());
            this.statusColor = ChatFormatting.GREEN;
            updateActionButtonsState();
        } else if (state == ModelUploadSession.State.FAILED) {
            if (target != null) {
                target.queued = false;
                target.failureMessage = session.getMessage();
            }
            this.status = Component.translatable(
                    "gui.sparkle_morpher.upload_custom_folder.status.failed",
                    session.getModelId(),
                    session.getMessage());
            this.statusColor = ChatFormatting.RED;
            updateActionButtonsState();
        } else {
            this.status = Component.translatable(
                    "gui.sparkle_morpher.upload_custom_folder.status.uploading",
                    session.getModelId());
            this.statusColor = ChatFormatting.YELLOW;
        }
    }

    @Override
    public void tick() {
        startNextUploadIfIdle();
    }

    private void rebuildEntries() {
        this.entries.clear();
        Map<String, Path> sources = ClientModelManager.snapshotLocalCustomSources();
        for (Map.Entry<String, Path> e : sources.entrySet()) {
            this.entries.add(new Entry(e.getKey(), e.getValue()));
        }
        this.scrollOffset = 0;
    }

    private void refreshList(boolean reloadFromDisk) {
        if (this.refreshInProgress) {
            return;
        }
        this.error = Component.empty();
        if (!reloadFromDisk) {
            rebuildEntries();
            updateActionButtonsState();
            return;
        }
        this.refreshInProgress = true;
        this.status = Component.translatable("gui.sparkle_morpher.import.state.folder_reloading");
        this.statusColor = ChatFormatting.YELLOW;
        updateActionButtonsState();
        ClientModelManager.reloadLocalModels(reloadError -> {
            this.refreshInProgress = false;
            if (reloadError != null) {
                this.status = reloadError;
                this.statusColor = ChatFormatting.RED;
                this.error = reloadError;
                updateActionButtonsState();
                return;
            }
            this.status = Component.translatable("gui.sparkle_morpher.import.state.folder_reloaded");
            this.statusColor = ChatFormatting.GREEN;
            rebuildEntries();
            updateActionButtonsState();
        });
    }

    private void openModelFolder() {
        try {
            Files.createDirectories(ServerModelManager.CUSTOM);
            ClientUiUtil.openFile(ServerModelManager.CUSTOM.toFile());
        } catch (IOException e) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.open_folder", e.getMessage());
        }
    }

    private void uploadAll() {
        if (!preflightUpload()) {
            return;
        }
        int queued = 0;
        for (Entry entry : this.entries) {
            if (entry.queued || entry.completed) {
                continue;
            }
            entry.queued = true;
            this.pendingUploads.add(entry);
            queued++;
        }
        if (queued == 0) {
            this.status = Component.translatable("gui.sparkle_morpher.upload_custom_folder.status.nothing_to_upload");
            this.statusColor = ChatFormatting.GRAY;
        } else {
            this.status = Component.translatable("gui.sparkle_morpher.upload_custom_folder.status.queued", queued);
            this.statusColor = ChatFormatting.YELLOW;
        }
        updateActionButtonsState();
        startNextUploadIfIdle();
    }

    private void uploadOne(Entry entry) {
        if (entry == null || entry.queued || entry.completed) {
            return;
        }
        if (!preflightUpload()) {
            return;
        }
        entry.queued = true;
        this.pendingUploads.add(entry);
        this.status = Component.translatable("gui.sparkle_morpher.upload_custom_folder.status.queued_one", entry.modelId);
        this.statusColor = ChatFormatting.YELLOW;
        updateActionButtonsState();
        startNextUploadIfIdle();
    }

    private boolean preflightUpload() {
        if (!ClientModelManager.isOysmServer()) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.waiting_handshake");
            return false;
        }
        if (!ClientModelManager.isAllowUpload()) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.disabled_by_server");
            return false;
        }
        return true;
    }

    private void startNextUploadIfIdle() {
        ModelUploadSession existing = ModelUploadSession.getInstance();
        if (existing != null && !existing.isTerminal()) {
            return;
        }
        Entry next = this.pendingUploads.poll();
        while (next != null) {
            if (uploadEntry(next)) {
                return;
            }
            next = this.pendingUploads.poll();
        }
        updateActionButtonsState();
    }

    private boolean uploadEntry(Entry entry) {
        if (entry.completed) {
            return false;
        }
        Path path = entry.sourcePath;
        if (path == null || !Files.exists(path)) {
            entry.queued = false;
            entry.failureMessage = Component.translatable("gui.sparkle_morpher.import.error.local_source_missing", entry.modelId);
            this.error = entry.failureMessage;
            return false;
        }
        try {
            String fileName;
            byte[] data;
            if (Files.isDirectory(path)) {
                ModelImportFilePicker.PickedFile packed = ModelImportFilePicker.packDirectory(path);
                fileName = entry.modelId + ".zip";
                data = packed.data();
            } else {
                String ext = importExtension(path);
                if (ext.isBlank()) {
                    entry.queued = false;
                    entry.failureMessage = Component.translatable("gui.sparkle_morpher.import.error.invalid_extension");
                    this.error = entry.failureMessage;
                    return false;
                }
                fileName = entry.modelId + ext;
                data = Files.readAllBytes(path);
            }
            Component uploadError = ModelUploadSession.start(entry.modelId, fileName, data);
            if (uploadError != null) {
                entry.queued = false;
                entry.failureMessage = uploadError;
                this.error = uploadError;
                return false;
            }
            this.status = Component.translatable("gui.sparkle_morpher.upload_custom_folder.status.uploading", entry.modelId);
            this.statusColor = ChatFormatting.YELLOW;
            return true;
        } catch (IOException e) {
            entry.queued = false;
            entry.failureMessage = Component.translatable("gui.sparkle_morpher.import.error.read_file", e.getMessage());
            this.error = entry.failureMessage;
            return false;
        }
    }

    private static String importExtension(Path path) {
        String lower = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ysm")) {
            return ".ysm";
        }
        if (lower.endsWith(".zip")) {
            return ".zip";
        }
        if (lower.endsWith(".bbmodel")) {
            return ".bbmodel";
        }
        return "";
    }

    private Entry findEntryByModelId(String modelId) {
        if (modelId == null) {
            return null;
        }
        for (Entry e : this.entries) {
            if (modelId.equals(e.modelId)) {
                return e;
            }
        }
        return null;
    }

    private void updateActionButtonsState() {
        boolean canUpload = ClientModelManager.isOysmServer() && ClientModelManager.isAllowUpload();
        boolean hasUploadable = false;
        for (Entry e : this.entries) {
            if (!e.queued && !e.completed) {
                hasUploadable = true;
                break;
            }
        }
        if (this.uploadAllButton != null) {
            this.uploadAllButton.active = canUpload && hasUploadable;
        }
        if (this.refreshButton != null) {
            this.refreshButton.active = !this.refreshInProgress;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        if (event.button() == 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            int listTop = LIST_TOP;
            int listBottom = this.height - LIST_BOTTOM_PADDING;
            if (mouseY >= listTop && mouseY <= listBottom && mouseX >= 16 && mouseX <= this.width - 16) {
                int rowIndex = (int) ((mouseY - listTop) / ROW_HEIGHT) + this.scrollOffset;
                if (rowIndex >= 0 && rowIndex < this.entries.size()) {
                    Entry entry = this.entries.get(rowIndex);
                    int btnX = this.width - 86;
                    int btnY = listTop + (rowIndex - this.scrollOffset) * ROW_HEIGHT + 2;
                    if (mouseX >= btnX && mouseX <= btnX + 60 && mouseY >= btnY && mouseY <= btnY + 16) {
                        uploadOne(entry);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        int visibleRows = visibleRowCount();
        int maxOffset = Math.max(0, this.entries.size() - visibleRows);
        if (deltaY > 0) {
            this.scrollOffset = Math.max(0, this.scrollOffset - 1);
        } else if (deltaY < 0) {
            this.scrollOffset = Math.min(maxOffset, this.scrollOffset + 1);
        }
        return true;
    }

    private int visibleRowCount() {
        int listTop = LIST_TOP;
        int listBottom = this.height - LIST_BOTTOM_PADDING;
        return Math.max(1, (listBottom - listTop) / ROW_HEIGHT);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        drawBorder(g, 0, 0, this.width, this.height, 2, 0x66808080);

        Component title = Component.translatable("gui.sparkle_morpher.upload_custom_folder.title").copy().withStyle(ChatFormatting.WHITE);
        g.text(this.font, title, 16, 14, 0xFFFFFFFF);

        int listTop = LIST_TOP;
        int listBottom = this.height - LIST_BOTTOM_PADDING;
        g.fill(12, listTop - 4, this.width - 12, listBottom + 4, 0x66101010);

        if (this.entries.isEmpty()) {
            renderEmptyState(g, listTop, listBottom);
        } else {
            renderListRows(g, mouseX, mouseY, listTop, listBottom);
        }

        renderFooter(g);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void renderListRows(GuiGraphicsExtractor g, int mouseX, int mouseY, int listTop, int listBottom) {
        int visibleRows = visibleRowCount();
        int maxOffset = Math.max(0, this.entries.size() - visibleRows);
        if (this.scrollOffset > maxOffset) {
            this.scrollOffset = maxOffset;
        }
        for (int row = 0; row < visibleRows; row++) {
            int idx = this.scrollOffset + row;
            if (idx >= this.entries.size()) {
                break;
            }
            Entry entry = this.entries.get(idx);
            int rowY = listTop + row * ROW_HEIGHT;
            int rowEndY = rowY + ROW_HEIGHT - 1;
            boolean hover = mouseY >= rowY && mouseY < rowEndY && mouseX >= 16 && mouseX < this.width - 16;
            g.fill(16, rowY, this.width - 16, rowEndY, hover ? 0x66303030 : 0x33202020);

            g.text(this.font, Component.literal(entry.modelId), 22, rowY + 3, 0xFFFFFFFF);
            String rel = relativizeToCustom(entry.sourcePath);
            g.text(this.font, Component.literal(rel).withStyle(ChatFormatting.GRAY), 22, rowY + 12, 0xFFAAAAAA);

            Component statusText;
            ChatFormatting color;
            ModelUploadSession session = entry.lastSession;
            if (entry.completed) {
                statusText = Component.translatable("gui.sparkle_morpher.upload_custom_folder.row.completed");
                color = ChatFormatting.GREEN;
            } else if (entry.failureMessage != null) {
                statusText = entry.failureMessage;
                color = ChatFormatting.RED;
            } else if (session != null && !session.isTerminal()) {
                int pct = (int) (session.getProgress() * 100f);
                statusText = Component.translatable("gui.sparkle_morpher.upload_custom_folder.row.uploading", pct);
                color = ChatFormatting.YELLOW;
            } else if (entry.queued) {
                statusText = Component.translatable("gui.sparkle_morpher.upload_custom_folder.row.queued");
                color = ChatFormatting.YELLOW;
            } else {
                statusText = Component.translatable("gui.sparkle_morpher.upload_custom_folder.row.idle");
                color = ChatFormatting.DARK_GRAY;
            }
            MutableComponent styled = statusText.copy().withStyle(color);
            int statW = this.font.width(styled);
            g.text(this.font, styled, this.width - 96 - statW - 8, rowY + 7, 0xFFFFFFFF);

            int btnX = this.width - 86;
            int btnY = rowY + 2;
            boolean btnEnabled = !entry.queued && !entry.completed && ClientModelManager.isOysmServer() && ClientModelManager.isAllowUpload();
            int btnBg = btnEnabled ? (mouseX >= btnX && mouseX <= btnX + 60 && mouseY >= btnY && mouseY <= btnY + 16 ? 0xFF2E7D32 : 0xFF1B5E20) : 0xFF424242;
            g.fill(btnX, btnY, btnX + 60, btnY + 16, btnBg);
            Component btnLabel = Component.translatable("gui.sparkle_morpher.upload_custom_folder.upload_one");
            int lblW = this.font.width(btnLabel);
            g.text(this.font, btnLabel, btnX + (60 - lblW) / 2, btnY + 4, btnEnabled ? 0xFFFFFFFF : 0xFFAAAAAA);
        }

        if (this.entries.size() > visibleRows) {
            int trackX = this.width - 14;
            g.fill(trackX, listTop, trackX + 2, listBottom, 0x55404040);
            int thumbH = Math.max(8, (listBottom - listTop) * visibleRows / this.entries.size());
            int thumbY = listTop + (listBottom - listTop - thumbH) * this.scrollOffset / Math.max(1, maxOffset);
            g.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xCCAAAAAA);
        }
    }

    private void renderEmptyState(GuiGraphicsExtractor g, int listTop, int listBottom) {
        Component msg;
        if (!ClientModelManager.isOysmServer()) {
            msg = Component.translatable("gui.sparkle_morpher.upload_custom_folder.disabled_reason.handshake")
                    .copy().withStyle(ChatFormatting.GRAY);
        } else if (!ClientModelManager.isAllowUpload()) {
            msg = Component.translatable("gui.sparkle_morpher.upload_custom_folder.disabled_reason.server_disabled")
                    .copy().withStyle(ChatFormatting.GRAY);
        } else {
            msg = Component.translatable("gui.sparkle_morpher.upload_custom_folder.empty")
                    .copy().withStyle(ChatFormatting.GRAY);
        }
        int w = this.font.width(msg);
        g.text(this.font, msg, (this.width - w) / 2, (listTop + listBottom) / 2 - 4, 0xFFFFFFFF);
    }

    private void renderFooter(GuiGraphicsExtractor g) {
        int y = this.height - 36;
        int cx = this.width / 2;
        if (!this.status.getString().isEmpty()) {
            MutableComponent line = this.status.copy().withStyle(this.statusColor);
            int w = this.font.width(line);
            g.text(this.font, line, cx - w / 2, y, 0xFFFFFFFF);
            y += 12;
        }
        if (!this.error.getString().isEmpty()) {
            MutableComponent err = this.error.copy().withStyle(ChatFormatting.RED);
            int w = this.font.width(err);
            g.text(this.font, err, cx - w / 2, y, 0xFFFFFFFF);
        }
    }

    private static String relativizeToCustom(Path source) {
        if (source == null) {
            return "";
        }
        try {
            return ServerModelManager.CUSTOM.toAbsolutePath().normalize()
                    .relativize(source.toAbsolutePath().normalize()).toString();
        } catch (Exception ignored) {
            return source.getFileName() == null ? "" : source.getFileName().toString();
        }
    }

    private static void drawBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int w, int color) {
        g.fill(x1, y1, x2, y1 + w, color);
        g.fill(x1, y2 - w, x2, y2, color);
        g.fill(x1, y1, x1 + w, y2, color);
        g.fill(x2 - w, y1, x2, y2, color);
    }

    private static final class Entry {
        final String modelId;
        final Path sourcePath;
        boolean queued;
        boolean completed;
        Component failureMessage;
        ModelUploadSession lastSession;

        Entry(String modelId, Path sourcePath) {
            this.modelId = modelId;
            this.sourcePath = sourcePath;
        }
    }
}
