package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.gui.button.IconButton;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.util.ClientUiUtil;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.ModelIdUtil;
import com.micaftic.morpher.util.PerformanceProfiler;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public class ModelUploadScreen extends Screen implements ModelUploadSession.Listener {
    private static final long MODEL_FOLDER_POLL_INTERVAL_MS = 1000L;
    private static final long MODEL_FOLDER_POLL_WINDOW_MS = 60000L;
    private final Screen parentScreen;
    private final Queue<ModelImportFilePicker.PickedFile> pendingImports = new ArrayDeque<>();
    private final Queue<LocalUploadFile> pendingLocalUploads = new ArrayDeque<>();
    private long lastFlashTime = 0L;
    private Component error = Component.empty();
    private Component localStatus = Component.empty();
    private ChatFormatting localStatusColor = ChatFormatting.GRAY;
    private Component serverStatus = Component.empty();
    private ChatFormatting serverStatusColor = ChatFormatting.GRAY;
    private boolean localImportInProgress;
    private boolean modelFolderReloadInProgress;
    private long modelFolderPollUntilMs;
    private long nextModelFolderPollMs;
    private long lastModelFolderStamp = Long.MIN_VALUE;
    private float displayedProgress = 0f;
    private float prevProgressTarget = -1f;

    public ModelUploadScreen(Screen parent) {
        super(Component.translatable("gui.sparkle_morpher.import.title"));
        this.parentScreen = parent;
    }

    public ModelUploadScreen(Screen parent, Collection<String> localModelIds) {
        this(parent);
        enqueueLocalModels(localModelIds);
    }

    private static void drawBorder(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int w, int color) {
        g.fill(x1, y1, x2, y1 + w, color);
        g.fill(x1, y2 - w, x2, y2, color);
        g.fill(x1, y1, x1 + w, y2, color);
        g.fill(x2 - w, y1, x2, y2, color);
    }

    @Override
    public void init() {
        clearWidgets();
        ModelUploadSession.addListener(this);
        int buttonY = 10;
        int toolbarX = Math.max(10, this.width - 76);
        addRenderableWidget(new IconButton(toolbarX, buttonY, 18, 18, 48, 0, button -> openFilePicker()).setTooltipText("gui.sparkle_morpher.import.choose_file"));
        addRenderableWidget(new IconButton(toolbarX + 24, buttonY, 18, 18, 64, 0, button -> openModelFolder()).setTooltipText("gui.sparkle_morpher.open_model_folder.open"));
        addRenderableWidget(new IconButton(toolbarX + 48, buttonY, 18, 18, 80, 32, button -> InputUtil.setScreen(this.parentScreen)).setTooltipText("gui.sparkle_morpher.model.return"));
    }

    @Override
    public void removed() {
        ModelUploadSession.removeListener(this);
        ModelUploadSession.clearIfTerminal();
        ModelImportFilePicker.cancelPicking();
    }

    @Override
    public void onSessionUpdate(ModelUploadSession session) {
        if (session == null) {
            return;
        }
        this.serverStatus = session.getMessage();
        this.serverStatusColor = switch (session.getState()) {
            case COMPLETED -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
            default -> ChatFormatting.YELLOW;
        };
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths.isEmpty()) {
            return;
        }
        this.error = Component.empty();
        this.lastFlashTime = ClientUiUtil.getMillis();
        for (Path path : paths) {
            enqueuePath(path);
        }
        startNextImportIfIdle();
    }

    private void openFilePicker() {
        this.error = Component.empty();
        this.localStatus = Component.empty();
        this.serverStatus = Component.empty();
        this.lastFlashTime = ClientUiUtil.getMillis();
        Component err = ModelImportFilePicker.pickYsmFile();
        if (err != null) {
            this.error = err;
        }
    }

    private void enqueuePath(Path path) {
        String fileName = path.getFileName().toString();
        try {
            if (Files.isDirectory(path)) {
                this.pendingImports.add(ModelImportFilePicker.packDirectory(path));
                return;
            }
            if (!ModelImportFilePicker.isImportFileName(fileName)) {
                this.error = Component.translatable("gui.sparkle_morpher.import.error.invalid_extension");
                return;
            }
            this.pendingImports.add(new ModelImportFilePicker.PickedFile(fileName, Files.readAllBytes(path)));
        } catch (IOException e) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.read_file", e.getMessage());
        }
    }

    private boolean importPickedFile(ModelImportFilePicker.PickedFile file) {
        this.error = Component.empty();
        this.lastFlashTime = ClientUiUtil.getMillis();
        String fileName = file.fileName() == null ? "imported.bin" : file.fileName();
        if (!ModelImportFilePicker.isImportFileName(fileName)) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.invalid_extension");
            return false;
        }
        ModelUploadSession existing = ModelUploadSession.getInstance();
        if (existing != null && !existing.isTerminal()) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.in_progress");
            return false;
        }
        String stem = stripImportExtension(fileName);
        String modelId = normalizeModelId(stem);
        if (modelId.isEmpty()) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.model_id_from_filename", stem);
            return false;
        }
        this.localImportInProgress = true;
        this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.local_importing", modelId);
        this.localStatusColor = ChatFormatting.YELLOW;
        this.serverStatus = Component.translatable("gui.sparkle_morpher.import.state.server_waiting");
        this.serverStatusColor = ChatFormatting.GRAY;
        ClientModelManager.importLocalModel(modelId, fileName, file.data(), localError -> onLocalImportFinished(modelId, fileName, file.data(), localError));
        return true;
    }

    private void onLocalImportFinished(String modelId, String fileName, byte[] data, Component localError) {
        this.localImportInProgress = false;
        if (localError != null) {
            this.localStatus = localError;
            this.localStatusColor = ChatFormatting.RED;
            this.serverStatus = Component.translatable("gui.sparkle_morpher.import.state.server_skipped");
            this.serverStatusColor = ChatFormatting.GRAY;
            this.error = localError;
            return;
        }
        this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.local_imported_as", modelId);
        this.localStatusColor = ChatFormatting.GREEN;

        Component uploadError = ModelUploadSession.start(modelId, fileName, data);
        if (uploadError != null) {
            this.serverStatus = Component.translatable("gui.sparkle_morpher.import.state.server_upload_failed", uploadError);
            this.serverStatusColor = ChatFormatting.RED;
            return;
        }
        this.serverStatus = Component.translatable("gui.sparkle_morpher.import.state.server_uploading");
        this.serverStatusColor = ChatFormatting.YELLOW;
    }

    private void openModelFolder() {
        try {
            Files.createDirectories(ServerModelManager.CUSTOM);
            ClientUiUtil.openFile(ServerModelManager.CUSTOM.toFile());
            this.lastModelFolderStamp = modelFolderStamp(ServerModelManager.CUSTOM);
            this.modelFolderPollUntilMs = ClientUiUtil.getMillis() + MODEL_FOLDER_POLL_WINDOW_MS;
            this.nextModelFolderPollMs = 0L;
            this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.folder_polling");
            this.localStatusColor = ChatFormatting.GRAY;
        } catch (IOException e) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.open_folder", e.getMessage());
        }
    }

    private static String stripImportExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            if (lower.endsWith(extension)) {
                return fileName.substring(0, fileName.length() - extension.length());
            }
        }
        return fileName;
    }

    private static String normalizeModelId(String text) {
        return stripImportExtension(ModelIdUtil.normalizeImportModelId(text));
    }

    private void enqueueLocalModels(Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }
        int queued = 0;
        for (String modelId : modelIds) {
            if (!ClientModelManager.isLocalOnlyModel(modelId)) {
                continue;
            }
            Optional<Path> source = ClientModelManager.getLocalModelSourcePath(modelId);
            if (source.isEmpty()) {
                this.error = Component.translatable("gui.sparkle_morpher.import.error.local_source_missing", modelId);
                continue;
            }
            try {
                Path path = source.get();
                if (Files.isDirectory(path)) {
                    ModelImportFilePicker.PickedFile packed = ModelImportFilePicker.packDirectory(path);
                    this.pendingLocalUploads.add(new LocalUploadFile(modelId, modelId + ".zip", packed.data()));
                    queued++;
                    continue;
                }
                String ext = importExtension(path);
                if (ext.isBlank()) {
                    this.error = Component.translatable("gui.sparkle_morpher.import.error.invalid_extension");
                    continue;
                }
                this.pendingLocalUploads.add(new LocalUploadFile(modelId, modelId + ext, Files.readAllBytes(path)));
                queued++;
            } catch (IOException e) {
                this.error = Component.translatable("gui.sparkle_morpher.import.error.read_file", e.getMessage());
            }
        }
        if (queued > 0) {
            this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.local_upload_queued", queued);
            this.localStatusColor = ChatFormatting.YELLOW;
        }
    }

    private boolean uploadLocalModelFile(LocalUploadFile file) {
        this.error = Component.empty();
        this.lastFlashTime = ClientUiUtil.getMillis();
        ModelUploadSession existing = ModelUploadSession.getInstance();
        if (existing != null && !existing.isTerminal()) {
            this.error = Component.translatable("gui.sparkle_morpher.import.error.in_progress");
            return false;
        }
        this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.local_upload_ready", file.modelId());
        this.localStatusColor = ChatFormatting.GREEN;
        Component uploadError = ModelUploadSession.start(file.modelId(), file.fileName(), file.data());
        if (uploadError != null) {
            this.serverStatus = Component.translatable("gui.sparkle_morpher.import.state.server_upload_failed", uploadError);
            this.serverStatusColor = ChatFormatting.RED;
            return false;
        }
        this.serverStatus = Component.translatable("gui.sparkle_morpher.import.state.server_uploading");
        this.serverStatusColor = ChatFormatting.YELLOW;
        return true;
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

    private void startNextImportIfIdle() {
        if (this.localImportInProgress) {
            return;
        }
        ModelUploadSession existing = ModelUploadSession.getInstance();
        if (existing != null && !existing.isTerminal()) {
            return;
        }
        LocalUploadFile localUpload = this.pendingLocalUploads.poll();
        if (localUpload != null) {
            if (!uploadLocalModelFile(localUpload)) {
                this.pendingLocalUploads.clear();
            }
            return;
        }
        ModelImportFilePicker.PickedFile next = this.pendingImports.poll();
        if (next != null && !importPickedFile(next)) {
            this.pendingImports.clear();
        }
    }

    private void pollModelFolderReload() {
        if (this.modelFolderPollUntilMs <= 0L || this.modelFolderReloadInProgress) {
            return;
        }
        long now = ClientUiUtil.getMillis();
        if (now > this.modelFolderPollUntilMs) {
            this.modelFolderPollUntilMs = 0L;
            if (this.localStatus.getString().isEmpty() || this.localStatusColor == ChatFormatting.GRAY) {
                this.localStatus = Component.empty();
            }
            return;
        }
        if (now < this.nextModelFolderPollMs) {
            return;
        }
        this.nextModelFolderPollMs = now + MODEL_FOLDER_POLL_INTERVAL_MS;
        long stamp;
        try {
            stamp = modelFolderStamp(ServerModelManager.CUSTOM);
        } catch (IOException e) {
            this.modelFolderPollUntilMs = 0L;
            this.error = Component.translatable("gui.sparkle_morpher.import.error.local_reload_failed", e.getMessage());
            return;
        }
        if (stamp == this.lastModelFolderStamp) {
            return;
        }
        this.lastModelFolderStamp = stamp;
        this.modelFolderReloadInProgress = true;
        this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.folder_reloading");
        this.localStatusColor = ChatFormatting.YELLOW;
        ClientModelManager.reloadLocalModels(reloadError -> {
            this.modelFolderReloadInProgress = false;
            if (reloadError != null) {
                this.localStatus = reloadError;
                this.localStatusColor = ChatFormatting.RED;
                this.error = reloadError;
                return;
            }
            this.localStatus = Component.translatable("gui.sparkle_morpher.import.state.folder_reloaded");
            this.localStatusColor = ChatFormatting.GREEN;
        });
    }

    private static long modelFolderStamp(Path root) throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            return 0L;
        }
        long perfStart = PerformanceProfiler.start();
        long result = 1125899906842597L;
        try (Stream<Path> stream = Files.walk(root, 6)) {
            int count = 0;
            for (var iterator = stream.iterator(); iterator.hasNext(); ) {
                Path path = iterator.next();
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                result = result * 31 + root.relativize(path).toString().hashCode();
                result = result * 31 + attrs.lastModifiedTime().toMillis();
                result = result * 31 + attrs.size();
                count++;
            }
            PerformanceProfiler.logElapsed("model_folder_stamp", root.getFileName() == null ? "custom" : root.getFileName().toString(),
                    perfStart, "paths=" + count);
        }
        return result;
    }

    @Override
    public void tick() {
        ModelImportFilePicker.PickedFile pickedFile;
        while ((pickedFile = ModelImportFilePicker.pollCompleted()) != null) {
            this.pendingImports.add(pickedFile);
        }
        pollModelFolderReload();
        startNextImportIfIdle();
        Component pickerError = ModelImportFilePicker.consumeLastError();
        if (!pickerError.getString().isEmpty()) {
            this.error = pickerError;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        GuiGraphicsExtractor g = extractor;
        g.fill(0, 0, this.width, this.height, 0xC0000000);

        long sinceFlash = ClientUiUtil.getMillis() - this.lastFlashTime;
        int borderColor;
        int borderWidth;
        if (sinceFlash < 900) {
            float t = 1f - (float) sinceFlash / 900;
            int alpha = Math.min(255, Math.max(0, (int) (255 * t)));
            borderColor = (alpha << 24) | 0x00FFC107;
            borderWidth = 4;
        } else {
            borderColor = 0x66808080;
            borderWidth = 2;
        }
        drawBorder(g, 0, 0, this.width, this.height, borderWidth, borderColor);

        ModelUploadSession session = ModelUploadSession.getInstance();
        if (session == null) {
            renderEmptyState(g);
        } else {
            renderSessionState(g, session);
        }

        if (!this.error.getString().isEmpty()) {
            MutableComponent err = this.error.copy().withStyle(ChatFormatting.RED);
            int w = this.font.width(err);
            g.text(this.font, err, (this.width - w) / 2, this.height - 60, 0xFFFFFFFF);
        }

        renderImportStatusLines(g);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void renderImportStatusLines(GuiGraphicsExtractor guiGraphics) {
        int cx = this.width / 2;
        int y = this.height - 38;
        if (!this.localStatus.getString().isEmpty()) {
            Component line = Component.translatable("gui.sparkle_morpher.import.status.local", this.localStatus).copy().withStyle(this.localStatusColor);
            int w = this.font.width(line);
            guiGraphics.text(this.font, line, cx - w / 2, y, 0xFFFFFFFF);
            y += 12;
        }
        if (!this.serverStatus.getString().isEmpty()) {
            Component line = Component.translatable("gui.sparkle_morpher.import.status.server", this.serverStatus).copy().withStyle(this.serverStatusColor);
            int w = this.font.width(line);
            guiGraphics.text(this.font, line, cx - w / 2, y, 0xFFFFFFFF);
        }
    }

    private void renderEmptyState(GuiGraphicsExtractor guiGraphics) {
        MutableComponent main = Component.translatable(ModelImportFilePicker.isPicking() ? "gui.sparkle_morpher.import.select_in_manager" : "gui.sparkle_morpher.import.empty").withStyle(ChatFormatting.WHITE);
        MutableComponent sub = Component.translatable("gui.sparkle_morpher.import.standalone_only").withStyle(ChatFormatting.GRAY);
        int cx = this.width / 2;
        int cy = this.height / 2;
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(cx, cy - 14);
        guiGraphics.pose().scale(2.0f, 2.0f);
        int mw = this.font.width(main);
        guiGraphics.text(this.font, main, -mw / 2, 0, 0xFFFFFFFF);
        guiGraphics.pose().popMatrix();
        int sw = this.font.width(sub);
        guiGraphics.text(this.font, sub, cx - sw / 2, cy + 22, 0xFFAAAAAA);
        if (ModelUploadSession.hasServerLimits()) {
            MutableComponent limit = Component.translatable("gui.sparkle_morpher.import.size_limit", ModelUploadSession.formatBytes(ModelUploadSession.getLastMaxTotalBytes())).withStyle(ChatFormatting.DARK_GRAY);
            int lw = this.font.width(limit);
            guiGraphics.text(this.font, limit, cx - lw / 2, cy + 36, 0xFFFFFFFF);
        }
    }

    private void renderSessionState(GuiGraphicsExtractor guiGraphics, ModelUploadSession session) {
        int cx = this.width / 2;
        int cy = this.height / 2;
        ChatFormatting color = switch (session.getState()) {
            case COMPLETED -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
            default -> ChatFormatting.YELLOW;
        };
        Component title = session.getMessage().copy().withStyle(color);
        int tw = this.font.width(title);
        guiGraphics.text(this.font, title, cx - tw / 2, cy - 32, 0xFFFFFFFF);

        Component sub = Component.literal(session.getModelId()).withStyle(ChatFormatting.GRAY);
        int sw = this.font.width(sub);
        guiGraphics.text(this.font, sub, cx - sw / 2, cy - 16, 0xFFFFFFFF);

        int barW = 320;
        int barH = 14;
        int barX = cx - barW / 2;
        int barY = cy + 4;
        float target = session.getProgress();
        if (target < prevProgressTarget) {
            displayedProgress = target;
        }
        prevProgressTarget = target;
        displayedProgress += (target - displayedProgress) * 0.18f;
        if (Math.abs(target - displayedProgress) < 0.001f) {
            displayedProgress = target;
        }
        int fillW = (int) (barW * displayedProgress);
        int fillColor;
        if (session.getState() == ModelUploadSession.State.FAILED) {
            fillColor = 0xFFD23232;
        } else if (session.getState() == ModelUploadSession.State.COMPLETED) {
            fillColor = 0xFF4CAF50;
        } else {
            fillColor = 0xFFFFC107;
        }
        guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xFF2A2A2A);
        if (fillW > 0) {
            guiGraphics.fill(barX, barY, barX + fillW, barY + barH, fillColor);
        }
        if (session.getState() == ModelUploadSession.State.UPLOADING && fillW > 4) {
            long now = ClientUiUtil.getMillis();
            int period = 1400;
            int travel = fillW + 40;
            int shimmerX = (int) (((now % period) / (float) period) * travel) - 20;
            int shimmerW = 24;
            int left = barX + Math.max(0, shimmerX);
            int right = barX + Math.min(fillW, shimmerX + shimmerW);
            if (right > left) {
                guiGraphics.fill(left, barY + 1, right, barY + barH - 1, 0x55FFFFFF);
            }
        }
        guiGraphics.fill(barX, barY, barX + barW, barY + 1, -1);
        guiGraphics.fill(barX, barY + barH - 1, barX + barW, barY + barH, -1);
        guiGraphics.fill(barX, barY, barX + 1, barY + barH, -1);
        guiGraphics.fill(barX + barW - 1, barY, barX + barW, barY + barH, -1);

        String stat = ModelUploadSession.formatBytes(session.getSentBytes()) + " / " + ModelUploadSession.formatBytes(session.getTotalBytes());
        int statW = this.font.width(stat);
        guiGraphics.text(this.font, stat, cx - statW / 2, barY + barH + 6, 0xFFAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record LocalUploadFile(String modelId, String fileName, byte[] data) {
    }

    @Override
    public void onClose() {
        InputUtil.setScreen(this.parentScreen);
    }
}
