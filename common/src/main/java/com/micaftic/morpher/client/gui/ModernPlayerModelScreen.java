package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.LoadingStateConfig;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import com.micaftic.morpher.network.message.C2SSetStarModelPacket;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.util.ClientUiUtil;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.ModelIdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ModernPlayerModelScreen extends Screen {
    private static final ModelPanelState STATE = new ModelPanelState();
    private static final Identifier MODEL_PANEL_ICONS = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/model_panel_icons.png");
    private static final int BG = 0x90171A1D;
    private static final int PANEL = 0x4A34424A;
    private static final int PANEL_HOVER = 0x66576B76;
    private static final int PANEL_ACTIVE = 0x625E7784;
    private static final int GLASS = 0x60405058;
    private static final int GLASS_DARK = 0x3822292E;
    private static final int BORDER = 0x6EE4F5FF;
    private static final int RED = 0xFFE05252;
    private static final int RED_SOFT = 0x77E05252;
    private static final int TEXT = 0xFFEDE1CC;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int ROW = 24;
    private static final int ICON = 18;
    private static final ExecutorService RESOURCE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "SM Modern Resource");
        thread.setDaemon(true);
        return thread;
    });

    private final List<Hit> hits = new ArrayList<>();
    private final List<ModelRepoEntry> resourceEntries = new ArrayList<>();
    private final Set<String> selectedModelIds = new LinkedHashSet<>();
    private final Set<String> selectedResourceUrls = new LinkedHashSet<>();
    private final Queue<ModelImportFilePicker.PickedFile> pendingImports = new ArrayDeque<>();
    private final PlayerPreviewEntity previewEntity = new PlayerPreviewEntity();
    private ModelPanelLayout layout;
    private EditBox modelSearchBox;
    private EditBox resourceSearchBox;
    private EditBox siteEditBox;
    private EditBox categoryEditBox;
    private ResourceStationConfig.State resourceConfig = ResourceStationConfig.load();
    private Component status = Component.empty();
    private ChatFormatting statusColor = ChatFormatting.GRAY;
    private boolean localImportInProgress;
    private boolean resourceStatusMessage;
    private int screenGeneration;
    private String previewModelId = "";
    private String previewTextureId = "";

    private enum IconGlyph {
        MODEL(0, 0),
        RESOURCE(16, 0),
        SETTINGS(32, 0),
        IMPORT(48, 0),
        FOLDER(64, 0),
        ROULETTE(80, 0),
        CATEGORY(96, 0),
        MULTI(112, 0),
        APPLY(0, 16),
        TEXTURE(16, 16),
        STAR(32, 16),
        RELOAD(48, 16),
        UP(64, 16),
        ROOT(80, 16),
        REFRESH(96, 16),
        MODE(112, 16),
        SITES(0, 32),
        QUEUE(16, 32),
        DOWNLOAD(32, 32),
        CLEAR(48, 32),
        CANCEL(64, 32),
        CLOSE(80, 32),
        SAVE(96, 32),
        DELETE(112, 32),
        CREATE(0, 48),
        MOVE(16, 48),
        PLUS(32, 48),
        MINUS(48, 48),
        FILE(64, 48),
        LOCK(80, 48),
        INFO(96, 48),
        CHECK(112, 48);

        final int u;
        final int v;

        IconGlyph(int u, int v) {
            this.u = u;
            this.v = v;
        }
    }

    public ModernPlayerModelScreen() {
        super(Component.translatable("key.sparkle_morpher.player_model.desc"));
    }

    public ModernPlayerModelScreen(ModelPanelState.Tab tab) {
        this();
        STATE.activeTab = tab;
        if (tab == ModelPanelState.Tab.RESOURCE) {
            STATE.resourceLoaded = false;
        }
    }

    public static ModernPlayerModelScreen resourceStation() {
        return new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE);
    }

    public static ModernPlayerModelScreen settings() {
        return new ModernPlayerModelScreen(ModelPanelState.Tab.SETTINGS);
    }

    public static ModernPlayerModelScreen downloads() {
        ModernPlayerModelScreen screen = new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE);
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
        return screen;
    }

    @Override
    protected void init() {
        clearWidgets();
        this.layout = ModelPanelLayout.create(this.width, this.height);
        this.modelSearchBox = null;
        this.resourceSearchBox = null;
        this.siteEditBox = null;
        this.categoryEditBox = null;
        if (STATE.activeTab == ModelPanelState.Tab.MODEL) {
            this.modelSearchBox = new EditBox(this.font, modelListX(), this.layout.contentTop + 8, modelListW(), 16, Component.translatable("gui.sparkle_morpher.resource_station.search"));
            this.modelSearchBox.setMaxLength(256);
            this.modelSearchBox.setValue(STATE.modelSearchText);
            this.modelSearchBox.setTextColor(TEXT);
            addWidget(this.modelSearchBox);
        } else if (STATE.activeTab == ModelPanelState.Tab.RESOURCE) {
            this.resourceSearchBox = new EditBox(this.font, resourceListX(), this.layout.contentTop + 8, resourceSearchW(), 16, Component.translatable("gui.sparkle_morpher.resource_station.search"));
            this.resourceSearchBox.setMaxLength(256);
            this.resourceSearchBox.setValue(STATE.resourceSearchText);
            this.resourceSearchBox.setTextColor(TEXT);
            addWidget(this.resourceSearchBox);
            if (!STATE.resourceLoaded && !STATE.resourceLoading) {
                refreshResources(false);
            }
        }
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.SITES) {
            int panelX = secondaryPanelX();
            int panelY = secondaryPanelY();
            int panelW = secondaryPanelW();
            this.siteEditBox = new EditBox(this.font, panelX + 16, panelY + 42, panelW - 32, 16, Component.translatable("gui.sparkle_morpher.model_panel.url"));
            this.siteEditBox.setMaxLength(2048);
            this.siteEditBox.setValue(STATE.siteEditText.isBlank() ? this.resourceConfig.selectedUrl() : STATE.siteEditText);
            this.siteEditBox.setTextColor(TEXT);
            addWidget(this.siteEditBox);
        } else if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CATEGORIES) {
            int panelX = secondaryPanelX();
            int panelY = secondaryPanelY();
            int panelW = secondaryPanelW();
            this.categoryEditBox = new EditBox(this.font, panelX + 16, panelY + 42, panelW - 32, 16, Component.translatable("gui.sparkle_morpher.model_panel.category"));
            this.categoryEditBox.setMaxLength(160);
            this.categoryEditBox.setValue(STATE.categoryEditText);
            this.categoryEditBox.setTextColor(TEXT);
            addWidget(this.categoryEditBox);
        }
    }

    @Override
    public void removed() {
        this.screenGeneration++;
        ModelImportFilePicker.cancelPicking();
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        ResourceDownloadManager.tick();
        pollImports();
        if (this.modelSearchBox != null && !Objects.equals(STATE.modelSearchText, this.modelSearchBox.getValue())) {
            STATE.modelSearchText = this.modelSearchBox.getValue();
            STATE.modelScroll = 0;
        }
        if (this.resourceSearchBox != null && !Objects.equals(STATE.resourceSearchText, this.resourceSearchBox.getValue())) {
            STATE.resourceSearchText = this.resourceSearchBox.getValue();
            STATE.resourceScroll = 0;
        }
        if (this.siteEditBox != null) {
            STATE.siteEditText = this.siteEditBox.getValue();
        }
        if (this.categoryEditBox != null) {
            STATE.categoryEditText = this.categoryEditBox.getValue();
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        STATE.activeTab = ModelPanelState.Tab.MODEL;
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.IMPORT;
        for (Path path : paths) {
            enqueueImportPath(path);
        }
        startNextImportIfIdle();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        this.hits.clear();
        boolean modal = STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE;
        int mainMouseX = modal ? -1 : mouseX;
        int mainMouseY = modal ? -1 : mouseY;
        extractTransparentBackground(g);
        fill(g, this.layout.left, this.layout.top, this.layout.width, this.layout.height, BG);
        border(g, this.layout.left, this.layout.top, this.layout.width, this.layout.height, 0x44FFFFFF);
        renderTabs(g, mainMouseX, mainMouseY);
        blurGlass(g, this.layout.contentLeft, this.layout.contentTop, this.layout.contentWidth, this.layout.contentHeight, 0x1CFFFFFF, 6.0f);
        fill(g, this.layout.contentLeft, this.layout.contentTop, this.layout.contentWidth, this.layout.contentHeight, 0x18171A1D);
        switch (STATE.activeTab) {
            case MODEL -> renderModelTab(g, mainMouseX, mainMouseY, partialTick);
            case RESOURCE -> renderResourceTab(g, mainMouseX, mainMouseY, partialTick);
            case SETTINGS -> renderSettingsTab(g, mainMouseX, mainMouseY);
        }
        renderFooter(g);
        renderSecondaryPanel(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int tabWidth = this.layout.width / 3;
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.MODEL, this.layout.left, tabWidth, IconGlyph.MODEL, Component.translatable("gui.sparkle_morpher.model_panel.model"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.RESOURCE, this.layout.left + tabWidth, tabWidth, IconGlyph.RESOURCE, Component.translatable("gui.sparkle_morpher.resource_station.title"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.SETTINGS, this.layout.left + tabWidth * 2, this.layout.width - tabWidth * 2, IconGlyph.SETTINGS, Component.translatable("gui.sparkle_morpher.model_panel.settings"));
    }

    private void renderTab(GuiGraphicsExtractor g, int mouseX, int mouseY, ModelPanelState.Tab tab, int x, int w, IconGlyph icon, Component label) {
        boolean selected = STATE.activeTab == tab;
        boolean hover = inside(mouseX, mouseY, x, this.layout.top, w, this.layout.tabHeight);
        fill(g, x, this.layout.top, w, this.layout.tabHeight, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x40303030);
        int total = 16 + 6 + this.font.width(label);
        int tx = x + (w - total) / 2;
        int ty = this.layout.top + 6;
        drawIcon(g, icon, tx, ty);
        g.text(this.font, label, tx + 22, this.layout.top + 10, selected ? 0xFFFFFFFF : MUTED, false);
        hit(x, this.layout.top, w, this.layout.tabHeight, label, () -> {
            if (STATE.activeTab != tab) {
                STATE.activeTab = tab;
                STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
                if (tab == ModelPanelState.Tab.RESOURCE) {
                    STATE.resourceLoaded = false;
                } else if (this.resourceStatusMessage) {
                    setStatus(Component.empty());
                }
                init();
            }
        });
    }

    private int modelLeftX() {
        return this.layout.contentLeft + 8;
    }

    private int modelLeftW() {
        int minList = modelListMinW();
        int max = Math.max(72, Math.min(140, this.layout.contentWidth - minList - 86 - 28));
        return clamp(this.layout.contentWidth / 4, 72, max);
    }

    private int modelRightW() {
        int max = Math.max(72, Math.min(180, this.layout.contentWidth - modelLeftW() - modelListMinW() - 28));
        return clamp(this.layout.contentWidth / 3, 72, max);
    }

    private int modelListX() {
        return modelLeftX() + modelLeftW() + 10;
    }

    private int modelListW() {
        return Math.max(24, this.layout.contentWidth - modelLeftW() - modelRightW() - 28);
    }

    private int modelListMinW() {
        return this.layout.contentWidth < 420 ? 54 : 90;
    }

    private int resourceListX() {
        return this.layout.contentLeft + 8;
    }

    private int resourceRightW() {
        int minRight = this.layout.contentWidth < 420 ? 90 : 170;
        int minList = this.layout.contentWidth < 420 ? 90 : 120;
        int max = Math.max(minRight, Math.min(260, this.layout.contentWidth - minList - 22));
        return clamp(this.layout.contentWidth / 3, minRight, max);
    }

    private int resourceListW() {
        return Math.max(60, this.layout.contentWidth - resourceRightW() - 22);
    }

    private int resourceToolbarW() {
        return ICON * 4 + 58 + 24;
    }

    private int resourceToolbarX() {
        return Math.max(resourceListX(), resourceListX() + resourceListW() - resourceToolbarW());
    }

    private int resourceSearchW() {
        return Math.max(40, resourceToolbarX() - resourceListX() - 8);
    }

    private int secondaryPanelW() {
        int max = Math.max(120, this.layout.width - 32);
        return clamp(this.layout.width * 2 / 3, Math.min(520, max), max);
    }

    private int secondaryPanelH() {
        int max = Math.max(96, this.layout.height - 32);
        return clamp(this.layout.height * 2 / 3, Math.min(300, max), max);
    }

    private int secondaryPanelX() {
        return this.layout.left + (this.layout.width - secondaryPanelW()) / 2;
    }

    private int secondaryPanelY() {
        return this.layout.top + (this.layout.height - secondaryPanelH()) / 2 + 8;
    }

    private void renderModelTab(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = modelLeftX();
        int y = this.layout.contentTop + 8;
        int leftW = modelLeftW();
        int rightW = modelRightW();
        int listX = modelListX();
        int listW = modelListW();
        int detailX = listX + listW + 10;
        int contentBottom = this.layout.footerTop - 6;

        glassPanel(g, x, y, leftW, contentBottom - y);
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.model"), x + 8, y + 8);
        renderCurrentModelSummary(g, x + 8, y + 26, leftW - 16);
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.filters"), x + 8, y + 88);
        renderChip(g, x + 8, y + 104, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.all"), STATE.modelFilter == ModelPanelState.ModelFilter.ALL, () -> setModelFilter(ModelPanelState.ModelFilter.ALL));
        renderChip(g, x + 50, y + 104, 42, Component.translatable("gui.sparkle_morpher.model_panel.filter.auth"), STATE.modelFilter == ModelPanelState.ModelFilter.AUTH, () -> setModelFilter(ModelPanelState.ModelFilter.AUTH));
        renderChip(g, x + 96, y + 104, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.star"), STATE.modelFilter == ModelPanelState.ModelFilter.STAR, () -> setModelFilter(ModelPanelState.ModelFilter.STAR));
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.actions"), x + 8, y + 136);
        renderIconButton(g, mouseX, mouseY, x + 8, y + 152, IconGlyph.IMPORT, Component.translatable("gui.sparkle_morpher.import.tooltip"), () -> openImportPanel());
        renderIconButton(g, mouseX, mouseY, x + 32, y + 152, IconGlyph.FOLDER, Component.translatable("gui.sparkle_morpher.open_model_folder.open"), this::openModelFolder);
        renderIconButton(g, mouseX, mouseY, x + 56, y + 152, IconGlyph.ROULETTE, Component.translatable("key.sparkle_morpher.animation_roulette.desc"), this::openRoulette);
        renderIconButton(g, mouseX, mouseY, x + 80, y + 152, IconGlyph.CATEGORY, Component.translatable("gui.sparkle_morpher.model_select.new_category"), () -> openCategoryPanel(""));
        renderIconButton(g, mouseX, mouseY, x + 104, y + 152, IconGlyph.MULTI, Component.translatable("gui.sparkle_morpher.model_panel.multi_select"), () -> {
            STATE.multiSelectMode = !STATE.multiSelectMode;
            this.selectedModelIds.clear();
        });

        if (this.modelSearchBox != null) {
            this.modelSearchBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        }
        renderPathBar(g, listX, y + 30, listW);
        int gridY = y + 50;
        int gridH = Math.max(50, contentBottom - 28 - gridY - 4);
        renderModelGrid(g, mouseX, mouseY, listX, gridY, listW, gridH);
        renderModelBottomActions(g, mouseX, mouseY, listX, contentBottom - 28, listW);
        renderModelDetails(g, mouseX, mouseY, detailX, y, rightW, contentBottom - y, partialTick);
    }

    private void renderCurrentModelSummary(GuiGraphicsExtractor g, int x, int y, int w) {
        LocalPlayer player = Minecraft.getInstance().player;
        String model = "default";
        String texture = "";
        if (player != null) {
            Optional<PlayerCapability> cap = PlayerCapability.get(player);
            if (cap.isPresent()) {
                model = cap.get().getModelId();
                texture = cap.get().getCurrentTextureName();
            }
        }
        drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.selected"), x, y);
        drawText(g, Component.literal(trim(model, w)), x, y + 12);
        if (!texture.isBlank()) {
            drawMuted(g, Component.literal(trim(texture, w)), x, y + 24);
        }
        int count = ClientModelManager.getModelAssemblyMap().size();
        drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.loaded_count", count), x, y + 46);
    }

    private void renderPathBar(GuiGraphicsExtractor g, int x, int y, int w) {
        fill(g, x, y, w, 16, GLASS_DARK);
        String path = STATE.currentPath.isBlank() ? "/" : "/" + STATE.currentPath;
        drawMuted(g, Component.literal(trim(path, w - 54)), x + 5, y + 4);
        renderIconButton(g, -1, -1, x + w - 44, y - 1, IconGlyph.UP, Component.translatable("gui.back"), this::navigateUp);
        renderIconButton(g, -1, -1, x + w - 22, y - 1, IconGlyph.ROOT, Component.translatable("gui.sparkle_morpher.model_panel.root"), () -> {
            STATE.currentPath = "";
            STATE.modelScroll = 0;
        });
    }

    private void renderModelGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        glassPanel(g, x, y, w, h);
        List<ModelEntry> entries = collectModelEntries();
        int cellW = Math.max(92, Math.min(150, w / Math.max(1, w / 116)));
        int cols = Math.max(1, w / cellW);
        int cellH = 50;
        int rows = Math.max(1, h / cellH);
        int maxScroll = Math.max(0, (entries.size() + cols - 1) / cols - rows);
        STATE.modelScroll = clamp(STATE.modelScroll, 0, maxScroll);
        if (entries.isEmpty()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.model_panel.no_models"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        int start = STATE.modelScroll * cols;
        for (int i = 0; i < rows * cols && start + i < entries.size(); i++) {
            ModelEntry entry = entries.get(start + i);
            int cx = x + (i % cols) * cellW + 3;
            int cy = y + (i / cols) * cellH + 3;
            int cw = cellW - 6;
            boolean selected = entry.modelId().equals(STATE.selectedModelId) || this.selectedModelIds.contains(entry.modelId());
            boolean hover = inside(mouseX, mouseY, cx, cy, cw, cellH - 6);
            fill(g, cx, cy, cw, cellH - 6, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x3E30363B);
            border(g, cx, cy, cw, cellH - 6, selected ? RED : 0x33FFFFFF);
            drawIcon(g, entry.folder() ? IconGlyph.FOLDER : entry.locked() ? IconGlyph.LOCK : IconGlyph.MODEL, cx + 4, cy + 4);
            drawText(g, Component.literal(trim(entry.title(), cw - 28)), cx + 22, cy + 6);
            drawMuted(g, Component.literal(trim(entry.subtitle(), cw - 12)), cx + 6, cy + 22);
            hit(cx, cy, cw, cellH - 6, Component.literal(entry.title()), () -> clickModelEntry(entry));
        }
    }

    private void renderModelBottomActions(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w) {
        fill(g, x, y, w, 24, GLASS_DARK);
        int bx = x + 6;
        if (STATE.multiSelectMode) {
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.DELETE, Component.translatable("gui.sparkle_morpher.model_panel.delete"), this::deleteSelectedModels);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.MOVE, Component.translatable("gui.sparkle_morpher.model_panel.move"), () -> openCategoryPanel(""));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.CREATE, Component.translatable("gui.sparkle_morpher.model_select.new_category"), () -> openCategoryPanel(""));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.CHECK, Component.translatable("gui.sparkle_morpher.model_select.tooltip.select_all"), this::selectAllVisibleModels);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.CLEAR, Component.translatable("gui.sparkle_morpher.model_panel.clear_selection"), this::clearModelSelection);
            Component msg = Component.translatable("gui.sparkle_morpher.model_panel.selected_count", this.selectedModelIds.size());
            drawMuted(g, msg, Math.min(x + w - this.font.width(msg) - 8, bx + 30), y + 8);
        } else {
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.TEXTURE, Component.translatable("gui.sparkle_morpher.model_panel.use_texture"), this::applySelectedTexture);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.STAR, Component.translatable("gui.sparkle_morpher.model_panel.toggle_favorite"), this::toggleSelectedStar);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.RELOAD, Component.translatable("gui.sparkle_morpher.model_panel.reload_models"), () -> ClientModelManager.reloadLocalModels(this::setStatus));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.UP, getCustomFolderUploadTooltip(), this::openCustomFolderUpload);
        }
    }

    private void renderModelDetails(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        glassPanel(g, x, y, w, h);
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.details"), x + 8, y + 8);
        ModelAssembly assembly = selectedAssembly();
        if (assembly == null) {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.select_model"), x + 8, y + 28);
            return;
        }
        String modelId = STATE.selectedModelId;
        int previewTop = y + 26;
        int previewH = Math.min(118, Math.max(72, h / 3));
        renderSelectedModelPreview(g, assembly, modelId, x + 8, previewTop, w - 16, previewH, mouseX, mouseY, partialTick);
        drawText(g, Component.literal(trim(displayName(modelId, assembly), w - 16)), x + 8, previewTop + previewH + 8);
        drawMuted(g, Component.literal(trim(modelId, w - 16)), x + 8, previewTop + previewH + 20);
        Metadata metadata = assembly.getModelData().getExtraInfo();
        int yy = previewTop + previewH + 38;
        if (metadata != null && metadata.getAuthors() != null && !metadata.getAuthors().isEmpty()) {
            drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.authors"), x + 8, yy);
            yy += 12;
            drawMuted(g, Component.literal(trim(authors(metadata), w - 16)), x + 8, yy);
            yy += 18;
        }
        if (metadata != null && metadata.getTips() != null && !metadata.getTips().isBlank()) {
            drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.info"), x + 8, yy);
            yy += 12;
            for (String line : wrap(metadata.getTips(), w - 16, 3)) {
                drawMuted(g, Component.literal(line), x + 8, yy);
                yy += 10;
            }
            yy += 6;
        }
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.textures"), x + 8, yy);
        yy += 12;
        List<String> textures = new ArrayList<>(assembly.getAnimationBundle().getTextures().keySet());
        for (int i = 0; i < Math.min(8, textures.size()) && yy + 16 < y + h - 8; i++) {
            String texture = textures.get(i);
            boolean selected = texture.equals(selectedTextureOrDefault(assembly));
            renderRowButton(g, mouseX, mouseY, x + 8, yy, w - 16, 14, Component.literal(trim(texture, w - 28)), selected, () -> {
                STATE.selectedTextureId = texture;
                applySelectedTexture();
            });
            yy += 16;
        }
    }

    private void renderSelectedModelPreview(GuiGraphicsExtractor g, ModelAssembly assembly, String modelId, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        fill(g, x, y, w, h, GLASS_DARK);
        border(g, x, y, w, h, 0x33FFFFFF);
        String textureId = selectedTextureOrDefault(assembly);
        if (!Objects.equals(this.previewModelId, modelId) || !Objects.equals(this.previewTextureId, textureId)) {
            this.previewEntity.initModelWithTexture(modelId, textureId);
            this.previewModelId = modelId;
            this.previewTextureId = textureId;
        }
        if (this.previewEntity.isModelReady()) {
            try {
                float scale = Math.max(28.0f, Math.min(54.0f, h * 0.43f));
                int previewCenterX = x + w / 2;
                int previewCenterY = y + h / 2;
                int previewHalfSize = Math.max(24, Math.min(w, h) / 2);
                ModelPreviewRenderer.renderLivingEntityPreview(g, previewCenterX - previewHalfSize, previewCenterY - previewHalfSize, previewCenterX + previewHalfSize, previewCenterY + previewHalfSize, x + w / 2.0f, y + h - 6.0f, scale, partialTick, this.previewEntity, RendererManager.getPlayerRenderer(), false, true, mouseX, mouseY);
            } catch (Exception ignored) {
                drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
            }
        } else {
            drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
        }
    }

    private void renderResourceTab(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = resourceListX();
        int y = this.layout.contentTop + 8;
        int rightW = resourceRightW();
        int listW = resourceListW();
        int rightX = x + listW + 10;
        int bottom = this.layout.footerTop - 6;
        int bx = resourceToolbarX();

        if (this.resourceSearchBox != null) {
            this.resourceSearchBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        }
        renderIconButton(g, mouseX, mouseY, bx, y - 1, IconGlyph.REFRESH, Component.translatable("gui.sparkle_morpher.resource_station.refresh"), () -> refreshResources(true));
        renderModeButton(g, mouseX, mouseY, bx + 24, y - 1, 58);
        renderIconButton(g, mouseX, mouseY, bx + 88, y - 1, IconGlyph.MULTI, Component.translatable("gui.sparkle_morpher.model_panel.multi_select"), this::toggleResourceMultiSelect);
        renderIconButton(g, mouseX, mouseY, bx + 112, y - 1, IconGlyph.SITES, Component.translatable("gui.sparkle_morpher.model_panel.sites"), () -> openSitesPanel());
        renderIconButton(g, mouseX, mouseY, bx + 136, y - 1, IconGlyph.QUEUE, Component.translatable("gui.sparkle_morpher.model_panel.queue_selected"), this::enqueueSelectedResources);

        int contentY = y + 26;
        int contentH = bottom - y - 26;
        renderResourceList(g, mouseX, mouseY, x, contentY, listW, contentH);
        renderResourceRightPane(g, mouseX, mouseY, rightX, contentY, rightW, contentH);
    }

    private void renderResourceList(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        glassPanel(g, x, y, w, h);
        List<ModelRepoEntry> entries = filteredResources();
        int rows = Math.max(1, h / ROW);
        int maxScroll = Math.max(0, entries.size() - rows);
        STATE.resourceScroll = clamp(STATE.resourceScroll, 0, maxScroll);
        if (STATE.resourceLoading) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.resource_station.loading"), x + w / 2, y + 42, TEXT);
            return;
        }
        if (entries.isEmpty()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.resource_station.no_results"), x + w / 2, y + 42, MUTED);
            return;
        }
        for (int i = 0; i < rows && STATE.resourceScroll + i < entries.size(); i++) {
            ModelRepoEntry entry = entries.get(STATE.resourceScroll + i);
            int rowY = y + i * ROW;
            boolean selected = entry.url().equals(STATE.selectedResourceUrl) || this.selectedResourceUrls.contains(entry.url());
            boolean hover = inside(mouseX, mouseY, x + 3, rowY + 2, w - 6, ROW - 4);
            fill(g, x + 3, rowY + 2, w - 6, ROW - 4, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : (i & 1) == 0 ? 0x3E30363B : 0x3630363B);
            g.text(this.font, trim(entry.name(), w - 78), x + 8, rowY + 5, TEXT, false);
            g.text(this.font, trim(resourceDetail(entry), w - 100), x + 8, rowY + 15, MUTED, false);
            hit(x + 3, rowY + 2, w - 38, ROW - 4, Component.literal(entry.name()), () -> clickResource(entry));
            renderIconButton(g, mouseX, mouseY, x + w - 28, rowY + 4, ResourceDownloadManager.isQueued(entry) ? IconGlyph.QUEUE : IconGlyph.DOWNLOAD, Component.translatable("gui.sparkle_morpher.model_panel.download"), () -> enqueueResource(entry));
        }
    }

    private void renderResourceRightPane(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        glassPanel(g, x, y, w, h);
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.downloads"), x + 8, y + 8);
        ModelRepoEntry selected = selectedResource();
        int yy = y + 28;
        if (selected != null) {
            drawText(g, Component.literal(trim(selected.name(), w - 16)), x + 8, yy);
            yy += 12;
            for (String line : wrap(selected.description(), w - 16, 4)) {
                drawMuted(g, Component.literal(line), x + 8, yy);
                yy += 10;
            }
            drawMuted(g, Component.literal(trim(resourceDetail(selected), w - 16)), x + 8, yy + 4);
            yy += 22;
        }
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.queue"), x + 8, yy);
        yy += 12;
        List<ResourceDownloadManager.TaskSnapshot> rows = new ArrayList<>();
        rows.addAll(snapshot.unfinishedTasks());
        rows.addAll(snapshot.finishedTasks().stream().limit(8).toList());
        if (rows.isEmpty()) {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.no_downloads"), x + 8, yy);
            yy += 14;
        } else {
            for (ResourceDownloadManager.TaskSnapshot task : rows) {
                if (yy + 22 > y + h - 30) {
                    break;
                }
                renderTaskRow(g, x + 8, yy, w - 16, task);
                yy += 24;
            }
        }
        renderIconButton(g, mouseX, mouseY, x + 8, y + h - 24, IconGlyph.CLEAR, Component.translatable("gui.sparkle_morpher.resource_station.clear_finished"), ResourceDownloadManager::clearFinished);
        renderIconButton(g, mouseX, mouseY, x + 32, y + h - 24, IconGlyph.CANCEL, Component.translatable("gui.sparkle_morpher.model_panel.cancel_current"), ResourceDownloadManager::cancelCurrent);
    }

    private void renderTaskRow(GuiGraphicsExtractor g, int x, int y, int w, ResourceDownloadManager.TaskSnapshot task) {
        fill(g, x, y, w, 20, GLASS_DARK);
        drawText(g, Component.literal(trim(task.name(), w - 58)), x + 4, y + 3);
        int barX = x + 4;
        int barY = y + 14;
        int fillW = (int) ((w - 8) * clamp(task.progress(), 0f, 1f));
        fill(g, barX, barY, w - 8, 3, 0xAA101010);
        fill(g, barX, barY, fillW, 3, stateColor(task.state()));
    }

    private void renderSettingsTab(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.layout.contentLeft + 8;
        int y = this.layout.contentTop + 8;
        int w = this.layout.contentWidth - 16;
        int bottom = this.layout.footerTop - 6;
        glassPanel(g, x, y, w, bottom - y);
        renderSettingGroups(g, x + 8, y + 10, w - 16);
        List<SettingRow> rows = settingsRows();
        int visible = Math.max(1, (bottom - y - 44) / 22);
        int maxScroll = Math.max(0, rows.size() - visible);
        STATE.settingsScroll = clamp(STATE.settingsScroll, 0, maxScroll);
        int yy = y + 38;
        for (int i = 0; i < visible && STATE.settingsScroll + i < rows.size(); i++) {
            SettingRow row = rows.get(STATE.settingsScroll + i);
            renderSettingRow(g, mouseX, mouseY, x + 8, yy, w - 16, row);
            yy += 22;
        }
    }

    private void renderSettingGroups(GuiGraphicsExtractor g, int x, int y, int w) {
        int chipW = Math.max(54, (w - 20) / ModelPanelState.SettingGroup.values().length);
        int xx = x;
        for (ModelPanelState.SettingGroup group : ModelPanelState.SettingGroup.values()) {
            int width = group == ModelPanelState.SettingGroup.MISC ? x + w - xx : chipW;
            renderChip(g, xx, y, width, settingGroupLabel(group), STATE.settingGroup == group, () -> {
                STATE.settingGroup = group;
                STATE.settingsScroll = 0;
            });
            xx += width + 5;
        }
    }

    private Component settingGroupLabel(ModelPanelState.SettingGroup group) {
        return switch (group) {
            case GENERAL -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.general");
            case RENDERING -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.rendering");
            case PERFORMANCE -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.performance");
            case DEBUG -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.debug");
            case MISC -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.misc");
        };
    }

    private void renderSettingRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, SettingRow row) {
        fill(g, x, y, w, 19, 0x44202020);
        Component label = Component.translatable(row.labelKey());
        if (row.segmented() != null) {
            SegmentedSetting segmented = row.segmented();
            int leftW = Math.max(50, this.font.width(segmented.left()) + 16);
            int rightW = Math.max(64, this.font.width(segmented.right()) + 16);
            int totalW = Math.min(w - 96, leftW + rightW + 2);
            if (totalW < leftW + rightW + 2) {
                leftW = Math.max(42, (totalW - 2) / 2);
                rightW = Math.max(42, totalW - 2 - leftW);
            }
            int sx = x + w - totalW - 8;
            g.text(this.font, trim(label.getString(), sx - x - 12), x + 6, y + 6, TEXT, false);
            renderSegmentedOption(g, sx, y + 2, leftW, 15, segmented.left(), segmented.leftSelected(), segmented.leftAction());
            renderSegmentedOption(g, sx + leftW + 2, y + 2, rightW, 15, segmented.right(), !segmented.leftSelected(), segmented.rightAction());
            return;
        }
        g.text(this.font, trim(label.getString(), w - 82), x + 6, y + 6, TEXT, false);
        if (row.booleanValue() != null) {
            int bx = x + w - 34;
            fill(g, bx, y + 4, 26, 11, row.booleanValue() ? RED_SOFT : 0x55303030);
            fill(g, bx + (row.booleanValue() ? 15 : 2), y + 5, 9, 9, 0xFFEDE1CC);
            hit(bx - 4, y, 34, 19, label, row.action());
        } else {
            renderIconButton(g, mouseX, mouseY, x + w - 50, y + 1, IconGlyph.MINUS, Component.translatable(row.labelKey()), row.decrement());
            renderIconButton(g, mouseX, mouseY, x + w - 24, y + 1, IconGlyph.PLUS, Component.translatable(row.labelKey()), row.increment());
            drawMuted(g, Component.literal(row.valueText()), x + w - 90, y + 6);
        }
    }

    private void renderSegmentedOption(GuiGraphicsExtractor g, int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
        fill(g, x, y, w, h, selected ? PANEL_ACTIVE : 0x55303030);
        border(g, x, y, w, h, selected ? RED : 0x33FFFFFF);
        drawCentered(g, Component.literal(trim(label.getString(), w - 6)), x + w / 2, y + 4, selected ? 0xFFFFFFFF : TEXT);
        hit(x, y, w, h, label, action);
    }

    private void renderSecondaryPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.NONE) {
            return;
        }
        int x = secondaryPanelX();
        int y = secondaryPanelY();
        int w = secondaryPanelW();
        int h = secondaryPanelH();
        this.hits.clear();
        fill(g, this.layout.left, this.layout.top, this.layout.width, this.layout.height, 0x42000000);
        hit(this.layout.left, this.layout.top, this.layout.width, this.layout.height, Component.empty(), () -> setFocused(null));
        secondaryGlassPanel(g, x, y, w, h);
        border(g, x, y, w, h, BORDER);
        renderIconButton(g, mouseX, mouseY, x + w - 24, y + 6, IconGlyph.CLOSE, Component.translatable("gui.sparkle_morpher.model_panel.close"), () -> {
            STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
            init();
        });
        switch (STATE.secondaryPanel) {
            case SITES -> renderSitesPanel(g, mouseX, mouseY, x, y, w, h, partialTick);
            case CATEGORIES -> renderCategoryPanel(g, mouseX, mouseY, x, y, w, h, partialTick);
            case IMPORT -> renderImportPanel(g, mouseX, mouseY, x, y, w, h);
            default -> {
            }
        }
    }

    private void renderSitesPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.sites"), x + 10, y + 10);
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.url"), x + 16, y + 28);
        if (this.siteEditBox != null) {
            this.siteEditBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        }
        int by = y + 66;
        renderIconButton(g, mouseX, mouseY, x + 16, by, IconGlyph.CREATE, Component.translatable("gui.sparkle_morpher.model_panel.add_site"), this::saveSite);
        renderIconButton(g, mouseX, mouseY, x + 42, by, IconGlyph.SAVE, Component.translatable("gui.sparkle_morpher.model_panel.save"), this::saveSite);
        renderIconButton(g, mouseX, mouseY, x + 68, by, IconGlyph.DELETE, Component.translatable("gui.sparkle_morpher.model_panel.delete"), this::deleteSite);
        int listX = x + 16;
        int listY = y + 96;
        int listW = w - 32;
        glassPanel(g, listX, listY, listW, h - 108);
        int rows = Math.max(1, (h - 112) / 20);
        List<String> urls = this.resourceConfig.urls();
        int maxScroll = Math.max(0, urls.size() - rows);
        STATE.sitesScroll = clamp(STATE.sitesScroll, 0, maxScroll);
        for (int i = 0; i < rows && STATE.sitesScroll + i < urls.size(); i++) {
            String url = urls.get(STATE.sitesScroll + i);
            boolean selected = url.equals(this.resourceConfig.selectedUrl());
            int rowY = listY + 4 + i * 20;
            renderRowButton(g, mouseX, mouseY, listX + 4, rowY, listW - 8, 17, Component.literal(trim(url, listW - 18)), selected, () -> selectSite(url));
        }
    }

    private void renderCategoryPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.categories"), x + 10, y + 10);
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.name_target"), x + 16, y + 28);
        if (this.categoryEditBox != null) {
            this.categoryEditBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        }
        int by = y + 66;
        renderIconButton(g, mouseX, mouseY, x + 16, by, IconGlyph.CREATE, Component.translatable("gui.sparkle_morpher.model_panel.create"), () -> setStatus(ModelPanelFileActions.createCategory(STATE.categoryEditText)));
        renderIconButton(g, mouseX, mouseY, x + 42, by, IconGlyph.MOVE, Component.translatable("gui.sparkle_morpher.model_panel.move"), () -> moveSelectionToCategory(STATE.categoryEditText));
        renderIconButton(g, mouseX, mouseY, x + 68, by, IconGlyph.DELETE, Component.translatable("gui.sparkle_morpher.model_panel.delete"), () -> setStatus(ModelPanelFileActions.deleteCategory(STATE.categoryEditText, false)));
        List<String> categories = ModelPanelFileActions.listCategories();
        int listX = x + 16;
        int listY = y + 96;
        int listW = w - 32;
        glassPanel(g, listX, listY, listW, h - 108);
        int rows = Math.max(1, (h - 112) / 20);
        int maxScroll = Math.max(0, categories.size() - rows);
        STATE.categoryScroll = clamp(STATE.categoryScroll, 0, maxScroll);
        for (int i = 0; i < rows && STATE.categoryScroll + i < categories.size(); i++) {
            String category = categories.get(STATE.categoryScroll + i);
            int rowY = listY + 4 + i * 20;
            renderRowButton(g, mouseX, mouseY, listX + 4, rowY, listW - 8, 17, Component.literal(trim(category, listW - 18)), category.equals(STATE.categoryEditText), () -> {
                STATE.categoryEditText = category;
                if (this.categoryEditBox != null) {
                    this.categoryEditBox.setValue(category);
                }
            });
        }
    }

    private void renderImportPanel(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.import_upload"), x + 10, y + 10);
        renderIconButton(g, mouseX, mouseY, x + 12, y + 34, IconGlyph.IMPORT, Component.translatable("gui.sparkle_morpher.import.choose_file"), this::openFilePicker);
        int yy = y + 70;
        drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.drop_files_hint"), x + 12, yy);
        yy += 20;
        ModelUploadSession session = ModelUploadSession.getInstance();
        if (session != null) {
            drawText(g, session.getMessage(), x + 12, yy);
            yy += 14;
            int barW = w - 24;
            fill(g, x + 12, yy, barW, 8, 0xAA101010);
            fill(g, x + 12, yy, (int) (barW * clamp(session.getProgress(), 0f, 1f)), 8, session.getState() == ModelUploadSession.State.FAILED ? 0xFFD23232 : RED);
            yy += 16;
            drawMuted(g, Component.literal(ModelUploadSession.formatBytes(session.getSentBytes()) + " / " + ModelUploadSession.formatBytes(session.getTotalBytes())), x + 12, yy);
        } else if (this.localImportInProgress) {
            drawText(g, Component.translatable("gui.sparkle_morpher.model_panel.importing"), x + 12, yy);
        } else {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.no_active_import"), x + 12, yy);
        }
    }

    private void renderFooter(GuiGraphicsExtractor g) {
        fill(g, this.layout.left, this.layout.footerTop, this.layout.width, 1, 0x55303030);
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        Component line = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? snapshot.status() : this.status;
        ChatFormatting color = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? snapshot.statusColor() : this.statusColor;
        int c = chatColor(color, MUTED);
        g.text(this.font, trim(line.getString(), this.layout.width - 20), this.layout.left + 10, this.layout.footerTop + 8, c, false);
    }

    private static int chatColor(ChatFormatting color, int fallback) {
        if (color == null) {
            return fallback;
        }
        return switch (color) {
            case BLACK -> 0xFF000000;
            case DARK_BLUE -> 0xFF0000AA;
            case DARK_GREEN -> 0xFF00AA00;
            case DARK_AQUA -> 0xFF00AAAA;
            case DARK_RED -> 0xFFAA0000;
            case DARK_PURPLE -> 0xFFAA00AA;
            case GOLD -> 0xFFFFAA00;
            case GRAY -> 0xFFAAAAAA;
            case DARK_GRAY -> 0xFF555555;
            case BLUE -> 0xFF5555FF;
            case GREEN -> 0xFF55FF55;
            case AQUA -> 0xFF55FFFF;
            case RED -> 0xFFFF5555;
            case LIGHT_PURPLE -> 0xFFFF55FF;
            case YELLOW -> 0xFFFFFF55;
            case WHITE -> 0xFFFFFFFF;
            default -> fallback;
        };
    }

    private void renderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (Hit hit : this.hits) {
            if (inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                String text = hit.tooltip().getString();
                if (text.isBlank()) {
                    return;
                }
                int tw = Math.min(220, this.font.width(text) + 10);
                int tx = Math.min(mouseX + 10, this.width - tw - 4);
                int ty = Math.min(mouseY + 10, this.height - 18);
                fill(g, tx, ty, tw, 16, 0xEE101010);
                border(g, tx, ty, tw, 16, 0x88FFFFFF);
                g.text(this.font, trim(text, tw - 8), tx + 5, ty + 5, 0xFFFFFFFF, false);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE) {
            if (this.siteEditBox != null && this.siteEditBox.mouseClicked(event, flag)) {
                setFocused(this.siteEditBox);
                return true;
            }
            if (this.categoryEditBox != null && this.categoryEditBox.mouseClicked(event, flag)) {
                setFocused(this.categoryEditBox);
                return true;
            }
            for (int i = this.hits.size() - 1; i >= 0; i--) {
                Hit hit = this.hits.get(i);
                if (inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
            setFocused(null);
            return true;
        }
        if (this.modelSearchBox != null && this.modelSearchBox.mouseClicked(event, flag)) {
            setFocused(this.modelSearchBox);
            return true;
        }
        if (this.resourceSearchBox != null && this.resourceSearchBox.mouseClicked(event, flag)) {
            setFocused(this.resourceSearchBox);
            return true;
        }
        if (this.siteEditBox != null && this.siteEditBox.mouseClicked(event, flag)) {
            setFocused(this.siteEditBox);
            return true;
        }
        if (this.categoryEditBox != null && this.categoryEditBox.mouseClicked(event, flag)) {
            setFocused(this.categoryEditBox);
            return true;
        }
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return true;
            }
        }
        setFocused(null);
        return super.mouseClicked(event, flag);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.SITES) {
            STATE.sitesScroll = Math.max(0, STATE.sitesScroll + delta);
            return true;
        }
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CATEGORIES) {
            STATE.categoryScroll = Math.max(0, STATE.categoryScroll + delta);
            return true;
        }
        switch (STATE.activeTab) {
            case MODEL -> STATE.modelScroll = Math.max(0, STATE.modelScroll + delta);
            case RESOURCE -> STATE.resourceScroll = Math.max(0, STATE.resourceScroll + delta);
            case SETTINGS -> STATE.settingsScroll = Math.max(0, STATE.settingsScroll + delta);
        }
        return true;
    }

    private List<ModelEntry> collectModelEntries() {
        List<ModelEntry> out = new ArrayList<>();
        String query = STATE.modelSearchText.trim().toLowerCase(Locale.ROOT);
        boolean searching = !query.isBlank();
        if (!searching) {
            for (String pack : ClientModelManager.getModelPackMap().keySet()) {
                if (isDirectChild(STATE.currentPath, pack)) {
                    String name = pack.substring(STATE.currentPath.length()).replaceAll("/+$", "");
                    out.add(ModelEntry.folder(pack, name));
                }
            }
        }
        Set<String> auth = authModels();
        Set<String> stars = starModels();
        for (var entry : ClientModelManager.getModelAssemblyMap().entrySet()) {
            String modelId = entry.getKey();
            ModelAssembly assembly = entry.getValue();
            if (!searching && !isDirectModel(STATE.currentPath, modelId)) {
                continue;
            }
            if (!matchesModelFilter(modelId, assembly, auth, stars)) {
                continue;
            }
            if (searching && !matchesModelSearch(modelId, assembly, query)) {
                continue;
            }
            boolean locked = assembly.getTextureRegistry().isAuthModel() && !auth.contains(modelId);
            out.add(ModelEntry.model(modelId, displayName(modelId, assembly), modelSubtitle(modelId, assembly), locked));
        }
        out.sort(Comparator.<ModelEntry, Boolean>comparing(ModelEntry::folder).reversed().thenComparing(e -> e.title().toLowerCase(Locale.ROOT)));
        return out;
    }

    private boolean matchesModelFilter(String modelId, ModelAssembly assembly, Set<String> auth, Set<String> stars) {
        return switch (STATE.modelFilter) {
            case ALL -> true;
            case AUTH -> auth.contains(modelId) || !assembly.getTextureRegistry().isAuthModel();
            case STAR -> stars.contains(modelId);
        };
    }

    private boolean matchesModelSearch(String modelId, ModelAssembly assembly, String query) {
        if (query.startsWith("@")) {
            return authors(assembly.getModelData().getExtraInfo()).toLowerCase(Locale.ROOT).contains(query.substring(1));
        }
        String haystack = modelId + " " + displayName(modelId, assembly) + " " + modelSubtitle(modelId, assembly);
        return haystack.toLowerCase(Locale.ROOT).contains(query.startsWith("#") ? query.substring(1) : query);
    }

    private static boolean isDirectChild(String path, String packPath) {
        if (packPath == null || !packPath.startsWith(path) || packPath.equals(path)) {
            return false;
        }
        String rest = packPath.substring(path.length()).replaceAll("/+$", "");
        return !rest.isBlank() && !rest.contains("/");
    }

    private static boolean isDirectModel(String path, String modelId) {
        if (path.isBlank()) {
            return !modelId.contains("/");
        }
        if (!modelId.startsWith(path)) {
            return false;
        }
        String rest = modelId.substring(path.length());
        return !rest.isBlank() && !rest.contains("/");
    }

    private void clickModelEntry(ModelEntry entry) {
        if (entry.folder()) {
            STATE.currentPath = entry.modelId();
            STATE.modelScroll = 0;
            return;
        }
        if (STATE.multiSelectMode) {
            if (!this.selectedModelIds.add(entry.modelId())) {
                this.selectedModelIds.remove(entry.modelId());
            }
            return;
        }
        STATE.selectedModelId = entry.modelId();
        ModelAssembly assembly = ClientModelManager.getModelAssemblyMap().get(entry.modelId());
        if (assembly != null) {
            STATE.selectedTextureId = selectedTextureOrDefault(assembly);
            if (entry.locked()) {
                setStatus(Component.translatable("message.sparkle_morpher.model.need_auth"), ChatFormatting.YELLOW);
                return;
            }
            applyModelAndTexture(entry.modelId(), STATE.selectedTextureId, assembly);
        }
    }

    private void applySelectedModel() {
        ModelAssembly assembly = selectedAssembly();
        if (assembly == null || STATE.selectedModelId.isBlank()) {
            return;
        }
        applyModelAndTexture(STATE.selectedModelId, selectedTextureOrDefault(assembly), assembly);
    }

    private void applySelectedTexture() {
        ModelAssembly assembly = selectedAssembly();
        if (assembly == null || STATE.selectedModelId.isBlank()) {
            return;
        }
        applyModelAndTexture(STATE.selectedModelId, selectedTextureOrDefault(assembly), assembly);
    }

    private void applyModelAndTexture(String modelId, String textureId, ModelAssembly assembly) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PlayerCapability.get(player).ifPresent(cap -> {
            ClientModelManager.rememberSelectedModel(modelId, textureId);
            if (ClientModelManager.isLocalOnlyModel(modelId)) {
                cap.initModelWithTexture(modelId, textureId);
            } else if (NetworkHandler.isClientConnected()) {
                if (ClientModelManager.isLocalOnlyModel(cap.getModelId())) {
                    cap.initModelWithTexture(modelId, textureId);
                }
                NetworkHandler.sendToServer(new C2SRequestSwitchModelPacket(modelId, textureId));
            } else {
                cap.initModelWithTexture(modelId, textureId);
            }
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.applied_model", modelId), ChatFormatting.GREEN);
        });
    }

    private void toggleSelectedStar() {
        if (STATE.selectedModelId.isBlank() || Minecraft.getInstance().player == null) {
            return;
        }
        StarModelsCapability.get(Minecraft.getInstance().player).ifPresent(cap -> {
            if (cap.containsModel(STATE.selectedModelId)) {
                cap.removeModel(STATE.selectedModelId);
                NetworkHandler.sendToServer(C2SSetStarModelPacket.remove(STATE.selectedModelId));
            } else {
                cap.addModel(STATE.selectedModelId);
                NetworkHandler.sendToServer(C2SSetStarModelPacket.add(STATE.selectedModelId));
            }
        });
    }

    private void deleteSelectedModels() {
        Collection<String> models = this.selectedModelIds.isEmpty() && !STATE.selectedModelId.isBlank() ? List.of(STATE.selectedModelId) : new HashSet<>(this.selectedModelIds);
        if (models.isEmpty()) {
            return;
        }
        setStatus(ModelPanelFileActions.deleteModels(models));
        this.selectedModelIds.clear();
        STATE.selectedModelId = "";
        STATE.selectedTextureId = "";
        STATE.multiSelectMode = false;
        ClientModelManager.reloadLocalModels(this::setStatus);
    }

    private void selectAllVisibleModels() {
        for (ModelEntry entry : collectModelEntries()) {
            if (!entry.folder() && !entry.locked()) {
                this.selectedModelIds.add(entry.modelId());
            }
        }
    }

    private void clearModelSelection() {
        this.selectedModelIds.clear();
        STATE.multiSelectMode = false;
    }

    private void refreshResources(boolean manual) {
        int requestId = ++STATE.resourceRequestId;
        int generation = this.screenGeneration;
        ResourceStationConfig.State config = this.resourceConfig;
        STATE.resourceLoading = true;
        STATE.resourceLoaded = false;
        STATE.resourceScroll = 0;
        STATE.selectedResourceUrl = "";
        this.resourceEntries.clear();
        if (STATE.activeTab == ModelPanelState.Tab.RESOURCE) {
            setResourceStatus(Component.translatable("gui.sparkle_morpher.resource_station.loading"), ChatFormatting.YELLOW);
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.list(config.selectedUrl(), config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RESOURCE_EXECUTOR).orTimeout(Math.max(15_000L, config.timeoutMs() * 3L), TimeUnit.MILLISECONDS).whenComplete((result, error) ->
                ((Executor) Minecraft.getInstance()).execute(() -> {
                    if (generation != this.screenGeneration || requestId != STATE.resourceRequestId) {
                        return;
                    }
                    STATE.resourceLoading = false;
                    if (error != null) {
                        if (STATE.activeTab == ModelPanelState.Tab.RESOURCE) {
                            setResourceStatus(Component.translatable("gui.sparkle_morpher.resource_station.error", rootMessage(error)), ChatFormatting.RED);
                        }
                    } else {
                        this.resourceEntries.clear();
                        this.resourceEntries.addAll(result);
                        this.resourceEntries.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
                        STATE.resourceLoaded = true;
                        if (STATE.activeTab == ModelPanelState.Tab.RESOURCE) {
                            setResourceStatus(Component.translatable("gui.sparkle_morpher.resource_station.loaded", result.size()), manual ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                            init();
                        }
                    }
                }));
    }

    private List<ModelRepoEntry> filteredResources() {
        String query = STATE.resourceSearchText.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return new ArrayList<>(this.resourceEntries);
        }
        List<ModelRepoEntry> out = new ArrayList<>();
        for (ModelRepoEntry entry : this.resourceEntries) {
            String text = entry.name() + " " + entry.fileName() + " " + entry.description() + " " + entry.author() + " " + entry.tags();
            if (text.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(entry);
            }
        }
        return out;
    }

    private void clickResource(ModelRepoEntry entry) {
        if (STATE.resourceMultiSelectMode) {
            if (!this.selectedResourceUrls.add(entry.url())) {
                this.selectedResourceUrls.remove(entry.url());
            }
            return;
        }
        STATE.selectedResourceUrl = entry.url();
    }

    private void toggleResourceMultiSelect() {
        STATE.resourceMultiSelectMode = !STATE.resourceMultiSelectMode;
        if (!STATE.resourceMultiSelectMode) {
            this.selectedResourceUrls.clear();
            STATE.selectedResourceUrl = "";
        }
    }

    private void enqueueResource(ModelRepoEntry entry) {
        if (ResourceDownloadManager.enqueue(entry, this.resourceConfig)) {
            setStatus(Component.translatable("gui.sparkle_morpher.resource_station.queued", entry.name()), ChatFormatting.YELLOW);
        }
    }

    private void enqueueSelectedResources() {
        List<ModelRepoEntry> selected = this.resourceEntries.stream().filter(e -> this.selectedResourceUrls.contains(e.url())).toList();
        int added = ResourceDownloadManager.enqueueAll(selected.isEmpty() ? filteredResources() : selected, this.resourceConfig);
        setStatus(Component.translatable("gui.sparkle_morpher.resource_station.queue_added", added), added > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
    }

    private void openSitesPanel() {
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.SITES;
        STATE.siteEditText = this.resourceConfig.selectedUrl();
        init();
    }

    private void selectSite(String url) {
        this.resourceConfig = new ResourceStationConfig.State(this.resourceConfig.urls(), url, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.mainlandChinaMode(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        STATE.siteEditText = url;
        STATE.resourceLoaded = false;
        refreshResources(false);
        init();
    }

    private void saveSite() {
        String url = STATE.siteEditText.trim();
        if (url.isBlank()) {
            return;
        }
        List<String> urls = new ArrayList<>(this.resourceConfig.urls());
        if (!urls.contains(url)) {
            urls.add(0, url);
        }
        this.resourceConfig = new ResourceStationConfig.State(urls, url, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.mainlandChinaMode(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        STATE.resourceLoaded = false;
        refreshResources(false);
    }

    private void deleteSite() {
        String url = STATE.siteEditText.trim();
        List<String> urls = new ArrayList<>(this.resourceConfig.urls());
        if (urls.size() <= 1 || !urls.remove(url)) {
            setStatus(Component.translatable("gui.sparkle_morpher.resource_station.cannot_delete"), ChatFormatting.RED);
            return;
        }
        String selected = urls.get(0);
        this.resourceConfig = new ResourceStationConfig.State(urls, selected, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.mainlandChinaMode(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        STATE.siteEditText = selected;
        refreshResources(false);
        init();
    }

    private void toggleResourceMode() {
        this.resourceConfig = new ResourceStationConfig.State(this.resourceConfig.urls(), this.resourceConfig.selectedUrl(), this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), !this.resourceConfig.mainlandChinaMode(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        STATE.resourceLoaded = false;
        refreshResources(false);
    }

    private void openCategoryPanel(String seed) {
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.CATEGORIES;
        STATE.categoryEditText = seed == null ? "" : seed;
        init();
    }

    private void moveSelectionToCategory(String category) {
        Collection<String> models = this.selectedModelIds.isEmpty() && !STATE.selectedModelId.isBlank() ? List.of(STATE.selectedModelId) : new HashSet<>(this.selectedModelIds);
        if (models.isEmpty()) {
            return;
        }
        setStatus(ModelPanelFileActions.moveModels(models, category));
        this.selectedModelIds.clear();
        STATE.multiSelectMode = false;
    }

    private void openImportPanel() {
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.IMPORT;
        init();
    }

    private void openFilePicker() {
        Component error = ModelImportFilePicker.pickYsmFile();
        if (error != null) {
            setStatus(error, ChatFormatting.RED);
        }
    }

    private void openModelFolder() {
        try {
            Files.createDirectories(ServerModelManager.CUSTOM);
            ClientUiUtil.openFile(ServerModelManager.CUSTOM.toFile());
            setStatus(Component.literal(ServerModelManager.CUSTOM.toString()), ChatFormatting.GRAY);
        } catch (IOException e) {
            setStatus(Component.translatable("gui.sparkle_morpher.import.error.open_folder", e.getMessage()), ChatFormatting.RED);
        }
    }

    private void openCustomFolderUpload() {
        InputUtil.setScreen(new CustomFolderUploadScreen(this));
    }

    private Component getCustomFolderUploadTooltip() {
        if (ClientModelManager.isAllowUpload() && ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip");
        }
        if (!ClientModelManager.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip.waiting");
        }
        return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip.disabled");
    }

    private void pollImports() {
        ModelImportFilePicker.PickedFile picked;
        while ((picked = ModelImportFilePicker.pollCompleted()) != null) {
            this.pendingImports.add(picked);
        }
        Component pickerError = ModelImportFilePicker.consumeLastError();
        if (!pickerError.getString().isEmpty()) {
            setStatus(pickerError, ChatFormatting.RED);
        }
        startNextImportIfIdle();
    }

    private void enqueueImportPath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                this.pendingImports.add(ModelImportFilePicker.packDirectory(path));
            } else if (ModelImportFilePicker.isImportFileName(path.getFileName().toString())) {
                this.pendingImports.add(new ModelImportFilePicker.PickedFile(path.getFileName().toString(), Files.readAllBytes(path)));
            }
        } catch (IOException e) {
            setStatus(Component.translatable("gui.sparkle_morpher.import.error.read_file", e.getMessage()), ChatFormatting.RED);
        }
    }

    private void startNextImportIfIdle() {
        if (this.localImportInProgress) {
            return;
        }
        ModelUploadSession existing = ModelUploadSession.getInstance();
        if (existing != null && !existing.isTerminal()) {
            return;
        }
        ModelImportFilePicker.PickedFile file = this.pendingImports.poll();
        if (file == null) {
            return;
        }
        String fileName = file.fileName() == null ? "imported.bin" : file.fileName();
        String modelId = stripImportExtension(ModelIdUtil.normalizeImportModelId(fileName));
        if (modelId.isBlank()) {
            setStatus(Component.translatable("gui.sparkle_morpher.import.error.model_id_from_filename", fileName), ChatFormatting.RED);
            return;
        }
        this.localImportInProgress = true;
        setStatus(Component.translatable("gui.sparkle_morpher.import.state.local_importing", modelId), ChatFormatting.YELLOW);
        ClientModelManager.importLocalModel(modelId, fileName, file.data(), error -> {
            this.localImportInProgress = false;
            if (error != null) {
                setStatus(error, ChatFormatting.RED);
                return;
            }
            Component uploadError = ModelUploadSession.start(modelId, fileName, file.data());
            if (uploadError != null) {
                setStatus(Component.translatable("gui.sparkle_morpher.import.state.local_imported_as", modelId), ChatFormatting.GREEN);
            }
        });
    }

    private void openRoulette() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        PlayerCapability.get(minecraft.player).ifPresent(cap -> {
            String modelId = cap.getModelId();
            ModelAssembly modelAssembly = cap.getModelAssembly();
            if (modelAssembly != null && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                InputUtil.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
            }
        });
    }

    private List<SettingRow> settingsRows() {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.sound_roulette_message", GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG));
        rows.add(doubleRow(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.sound_volume", GeneralConfig.SOUND_VOLUME, 0, 100, 5, "%"));
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.disable_self_model", GeneralConfig.DISABLE_SELF_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.disable_other_model", GeneralConfig.DISABLE_OTHER_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.disable_self_hands", GeneralConfig.DISABLE_SELF_HANDS));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_player_render", ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_external_fp_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.shader_glow_compatibility", GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK));
        rows.add(rendererModeRow(ModelPanelState.SettingGroup.PERFORMANCE));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.native_simd_renderer", GeneralConfig.USE_NATIVE_SIMD_RENDERER));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.java_vector_renderer", GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER));
        rows.add(intRow(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.gpu_cache_limit", GeneralConfig.MAX_CACHED_GPU_MODELS, 0, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.unused_model_ttl", GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 30, 86400, 30, "s"));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.resource_monitor_log", GeneralConfig.RESOURCE_STATION_MONITOR_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.model_memory_profiler", GeneralConfig.MODEL_MEMORY_PROFILER));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.import_performance_log", GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.gpu_debug_log", GeneralConfig.GPU_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.gpu_debug_verbose_log", GeneralConfig.GPU_DEBUG_VERBOSE_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.animation_frame_profiler", GeneralConfig.ANIMATION_FRAME_PROFILER));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.animation_debug_log", GeneralConfig.ANIMATION_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.input_debug_log", GeneralConfig.INPUT_STATE_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.show_model_id_first", GeneralConfig.SHOW_MODEL_ID_FIRST));
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_disabled", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN));
        return rows.stream().filter(row -> row.group() == STATE.settingGroup).toList();
    }

    private SettingRow bool(ModelPanelState.SettingGroup group, String labelKey, ModConfigSpec.BooleanValue value) {
        boolean current = safeBool(value);
        return new SettingRow(group, labelKey, current, "", () -> {
            value.set(!safeBool(value));
            value.save();
        }, null, null, null);
    }

    private SettingRow intRow(ModelPanelState.SettingGroup group, String labelKey, ModConfigSpec.IntValue value, int min, int max, int step, String suffix) {
        int current = safeInt(value, min);
        return new SettingRow(group, labelKey, null, current + suffix, null,
                () -> {
                    value.set(clamp(current - step, min, max));
                    value.save();
                },
                () -> {
                    value.set(clamp(current + step, min, max));
                    value.save();
                }, null);
    }

    private SettingRow doubleRow(ModelPanelState.SettingGroup group, String labelKey, ModConfigSpec.DoubleValue value, double min, double max, double step, String suffix) {
        double current = safeDouble(value, min);
        return new SettingRow(group, labelKey, null, String.format(Locale.ROOT, "%.0f%s", current, suffix), null,
                () -> {
                    value.set(Math.max(min, current - step));
                    value.save();
                },
                () -> {
                    value.set(Math.min(max, current + step));
                    value.save();
                }, null);
    }

    private SettingRow rendererModeRow(ModelPanelState.SettingGroup group) {
        boolean gpu = safeBool(GeneralConfig.USE_GPU_RENDERER);
        boolean compatibility = safeBool(GeneralConfig.USE_COMPATIBILITY_RENDERER);
        if (gpu == compatibility) {
            setRendererMode(gpu);
            compatibility = !gpu;
        }
        boolean gpuSelected = gpu && !compatibility;
        return new SettingRow(group, "gui.sparkle_morpher.config.renderer", null, "", null, null, null,
                new SegmentedSetting(
                        Component.translatable("gui.sparkle_morpher.config.renderer.gpu"),
                        Component.translatable("gui.sparkle_morpher.config.renderer.compatibility"),
                        gpuSelected,
                        () -> setRendererMode(true),
                        () -> setRendererMode(false)
                ));
    }

    private void setRendererMode(boolean useGpuRenderer) {
        GeneralConfig.USE_COMPATIBILITY_RENDERER.set(!useGpuRenderer);
        GeneralConfig.USE_COMPATIBILITY_RENDERER.save();
        GeneralConfig.USE_GPU_RENDERER.set(useGpuRenderer);
        GeneralConfig.USE_GPU_RENDERER.save();
    }

    private ModelAssembly selectedAssembly() {
        if (STATE.selectedModelId == null || STATE.selectedModelId.isBlank()) {
            return null;
        }
        return ClientModelManager.getModelAssemblyMap().get(STATE.selectedModelId);
    }

    private ModelRepoEntry selectedResource() {
        return this.resourceEntries.stream().filter(e -> e.url().equals(STATE.selectedResourceUrl)).findFirst().orElse(null);
    }

    private String selectedTextureOrDefault(ModelAssembly assembly) {
        if (STATE.selectedTextureId != null && !STATE.selectedTextureId.isBlank() && assembly.getAnimationBundle().getTextures().containsKey(STATE.selectedTextureId)) {
            return STATE.selectedTextureId;
        }
        return assembly.getAnimationBundle().getDefaultTextureName();
    }

    private Set<String> authModels() {
        if (Minecraft.getInstance().player == null) {
            return Set.of();
        }
        return AuthModelsCapability.get(Minecraft.getInstance().player).map(AuthModelsCapability::getAuthModels).orElse(Set.of());
    }

    private Set<String> starModels() {
        if (Minecraft.getInstance().player == null) {
            return Set.of();
        }
        return StarModelsCapability.get(Minecraft.getInstance().player).map(StarModelsCapability::getStarModels).orElse(Set.of());
    }

    private void setModelFilter(ModelPanelState.ModelFilter filter) {
        STATE.modelFilter = filter;
        STATE.modelScroll = 0;
    }

    private void navigateUp() {
        if (STATE.currentPath.isBlank()) {
            return;
        }
        String path = STATE.currentPath.replaceAll("/+$", "");
        int slash = path.lastIndexOf('/');
        STATE.currentPath = slash < 0 ? "" : path.substring(0, slash + 1);
        STATE.modelScroll = 0;
    }

    private Component modeLabel() {
        return this.resourceConfig.mainlandChinaMode()
                ? Component.translatable("gui.sparkle_morpher.resource_station.mode.mainland")
                : Component.translatable("gui.sparkle_morpher.resource_station.mode.native");
    }

    private boolean isMainlandResourceMode() {
        return this.resourceConfig.mainlandChinaMode();
    }

    private String displayName(String modelId, ModelAssembly assembly) {
        try {
            String name = assembly.getDisplayName(modelId);
            return StringUtils.isBlank(name) ? modelId : name;
        } catch (Exception ignored) {
            return modelId;
        }
    }

    private String modelSubtitle(String modelId, ModelAssembly assembly) {
        List<String> parts = new ArrayList<>();
        if (ClientModelManager.isLocalOnlyModel(modelId)) {
            parts.add("local");
        }
        if (assembly.getTextureRegistry().isAuthModel()) {
            parts.add("auth");
        }
        parts.add(assembly.getAnimationBundle().getTextures().size() + " tex");
        return String.join(" | ", parts);
    }

    private String authors(Metadata metadata) {
        if (metadata == null || metadata.getAuthors() == null || metadata.getAuthors().isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>();
        for (AuthorInfo author : metadata.getAuthors()) {
            if (author != null && author.getName() != null && !author.getName().isBlank()) {
                names.add(author.getName());
            }
        }
        return String.join(", ", names);
    }

    private String resourceDetail(ModelRepoEntry entry) {
        List<String> parts = new ArrayList<>();
        parts.add(entry.fileName());
        if (entry.size() > 0) {
            parts.add(ModelUploadSession.formatBytes((int) Math.min(Integer.MAX_VALUE, entry.size())));
        }
        if (!entry.author().isBlank()) {
            parts.add(entry.author());
        }
        if (!entry.tags().isBlank()) {
            parts.add(entry.tags());
        }
        return String.join(" | ", parts);
    }

    private void setStatus(Component component) {
        setStatus(component, ChatFormatting.GRAY);
    }

    private void setStatus(Component component, ChatFormatting color) {
        this.status = component == null ? Component.empty() : component;
        this.statusColor = color == null ? ChatFormatting.GRAY : color;
        this.resourceStatusMessage = false;
    }

    private void setResourceStatus(Component component, ChatFormatting color) {
        this.status = component == null ? Component.empty() : component;
        this.statusColor = color == null ? ChatFormatting.GRAY : color;
        this.resourceStatusMessage = true;
    }

    private void renderChip(GuiGraphicsExtractor g, int x, int y, int w, Component label, boolean selected, Runnable action) {
        fill(g, x, y, w, 15, selected ? RED_SOFT : 0x55303030);
        drawCentered(g, Component.literal(trim(label.getString(), w - 4)), x + w / 2, y + 4, selected ? 0xFFFFFFFF : MUTED);
        hit(x, y, w, 15, label, action);
    }

    private void renderIconButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, IconGlyph icon, Component tooltip, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, ICON, ICON);
        fill(g, x, y, ICON, ICON, hover ? PANEL_HOVER : 0x66303030);
        border(g, x, y, ICON, ICON, hover ? RED : 0x33FFFFFF);
        drawIcon(g, icon, x + 1, y + 1);
        hit(x, y, ICON, ICON, tooltip, action);
    }

    private void drawIcon(GuiGraphicsExtractor g, IconGlyph icon, int x, int y) {
        g.blit(MODEL_PANEL_ICONS, x, y, x + 16, y + 16, icon.u / 128.0f, (icon.u + 16) / 128.0f, icon.v / 64.0f, (icon.v + 16) / 64.0f);
    }

    private void renderModeButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w) {
        boolean hover = inside(mouseX, mouseY, x, y, w, ICON);
        boolean mainland = isMainlandResourceMode();
        fill(g, x, y, w, ICON, hover ? PANEL_HOVER : mainland ? RED_SOFT : 0x66303030);
        border(g, x, y, w, ICON, mainland ? RED : hover ? RED : 0x33FFFFFF);
        drawCentered(g, Component.literal(trim(modeLabel().getString(), w - 8)), x + w / 2, y + 5, 0xFFFFFFFF);
        hit(x, y, w, ICON, modeLabel(), this::toggleResourceMode);
    }

    private void renderTextButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        fill(g, x, y, w, h, hover ? PANEL_HOVER : 0x66303030);
        border(g, x, y, w, h, hover ? RED : 0x33FFFFFF);
        drawCentered(g, Component.literal(trim(label.getString(), w - 8)), x + w / 2, y + 5, 0xFFFFFFFF);
        hit(x, y, w, h, label, action);
    }

    private void renderRowButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        fill(g, x, y, w, h, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x55303030);
        g.text(this.font, label, x + 5, y + 4, selected ? 0xFFFFFFFF : TEXT, false);
        hit(x, y, w, h, label, action);
    }

    private void drawCentered(GuiGraphicsExtractor g, Component text, int centerX, int y, int color) {
        g.text(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void drawTitle(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(this.font, text.copy().withStyle(ChatFormatting.BOLD), x, y, TEXT, false);
    }

    private void drawSection(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(this.font, text.copy().withStyle(ChatFormatting.GRAY), x, y, MUTED, false);
    }

    private void drawText(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(this.font, text, x, y, TEXT, false);
    }

    private void drawMuted(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(this.font, text, x, y, MUTED, false);
    }

    private void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fillGradient(x, y, x + w, y + h, color, color);
    }

    private void glassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        blurGlass(g, x, y, w, h, 0x34F1FBFF, 8.0f);
        fill(g, x, y, w, h, GLASS);
        border(g, x, y, w, h, BORDER);
    }

    private void secondaryGlassPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        blurGlass(g, x, y, w, h, 0x58F7FBFF, 12.0f);
        fill(g, x, y, w, h, 0xD21F282E);
        border(g, x, y, w, h, 0xC8E4F5FF);
    }

    private void blurGlass(GuiGraphicsExtractor g, int x, int y, int w, int h, int tint, float radius) {
        if (w <= 0 || h <= 0) {
            return;
        }
        BlurStack.pushBlur(x, y, w, h, 0.0f, radius, tint);
        BlurStack.flush(g);
    }

    private void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        fill(g, x, y, w, 1, color);
        fill(g, x, y + h - 1, w, 1, color);
        fill(g, x, y, 1, h, color);
        fill(g, x + w - 1, y, 1, h, color);
    }

    private void hit(int x, int y, int w, int h, Component tooltip, Runnable action) {
        this.hits.add(new Hit(x, y, w, h, tooltip, action));
    }

    private static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    private String trim(String value, int maxWidth) {
        String text = value == null ? "" : value;
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int keep = text.length();
        while (keep > 0 && this.font.width(text.substring(0, keep) + ellipsis) > maxWidth) {
            keep--;
        }
        return text.substring(0, Math.max(0, keep)) + ellipsis;
    }

    private List<String> wrap(String value, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return lines;
        }
        String remaining = value.replace('\n', ' ').trim();
        while (!remaining.isBlank() && lines.size() < maxLines) {
            int keep = remaining.length();
            while (keep > 0 && this.font.width(remaining.substring(0, keep)) > maxWidth) {
                keep--;
            }
            if (keep <= 0) {
                break;
            }
            int space = remaining.lastIndexOf(' ', keep);
            if (space > 8) {
                keep = space;
            }
            lines.add(remaining.substring(0, keep).trim());
            remaining = remaining.substring(keep).trim();
        }
        return lines;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean safeBool(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Exception e) {
            return false;
        }
    }

    private static int safeInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double safeDouble(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (Exception e) {
            return fallback;
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

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static int stateColor(ResourceDownloadManager.TaskState state) {
        return switch (state) {
            case DONE -> 0xFF4CAF50;
            case FAILED -> 0xFFD23232;
            case CANCELLED -> 0xFF8F8F8F;
            default -> RED;
        };
    }

    @SuppressWarnings("unused")
    private static String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Hit(int x, int y, int w, int h, Component tooltip, Runnable action) {
    }

    private record ModelEntry(String modelId, String title, String subtitle, boolean folder, boolean locked) {
        static ModelEntry folder(String path, String title) {
            return new ModelEntry(path, title, "folder", true, false);
        }

        static ModelEntry model(String modelId, String title, String subtitle, boolean locked) {
            return new ModelEntry(modelId, title, subtitle, false, locked);
        }
    }

    private record SettingRow(ModelPanelState.SettingGroup group, String labelKey, Boolean booleanValue, String valueText, Runnable action, Runnable decrement, Runnable increment, SegmentedSetting segmented) {
    }

    private record SegmentedSetting(Component left, Component right, boolean leftSelected, Runnable leftAction, Runnable rightAction) {
    }
}
