package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.util.SmExecutors;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.client.renderer.preview.GuiModelRenderer;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.render.NativeSimdValidator;
import com.micaftic.morpher.config.LoadingStateConfig;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.core.vector.VectorApiCapability;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import com.micaftic.morpher.network.message.C2SSetStarModelPacket;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.util.ClientUiUtil;
import com.micaftic.morpher.util.LocalStarModelsStore;
import com.micaftic.morpher.util.ModelIdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import com.micaftic.morpher.core.config.ConfigPolicies;

public class ModernPlayerModelScreen extends Screen {
    private static final java.util.Map<String, ModelPanelState> STATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int STATE_CACHE_LIMIT = 64;
    private final ModelPanelState STATE;
    private final String stateKeyValue;
    private static final Identifier MODEL_PANEL_ICONS = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "texture/model_panel_icons.png");
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
    private static final int DENSE_MODEL_ROW = 24;
    private static final int MODEL_PAGE_BUTTON_WIDTH = 46;
    private static final int MODEL_PAGE_BUTTON_HEIGHT = 18;
    private static final int ICON = 18;

    // ---- 目录卡片（3D 卡片网格）视觉常量 ----
    private static final int CARD_BASE = 0xFF434242;
    private static final int CARD_BASE_HOVER = 0xFF505755;
    private static final int CARD_SELECT = 0xFF58636E;
    private static final int CARD_FOLDER = 0xFF9B51E0;
    private static final int CARD_FOLDER_HOVER = 0xFFB17BEA;
    private static final int CARD_NAME_TEXT = 0xFFF3EFE0;
    private static final int CARD_DIM = 0x9F222222;
    private static final int CARD_NAME_BG = 0xC6000000;

    /**
     * 卡片封面贴图像素尺寸缓存。键为弱引用，贴图回收后随之失效；
     * 仅渲染线程读写，用 synchronized 保护跨线程可见性。
     */
    private static final java.util.Map<AbstractTexture, int[]> COVER_DIMENSIONS = new WeakHashMap<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Set<String> selectedModelIds = new LinkedHashSet<>();
    private final PlayerPreviewEntity previewEntity = new PlayerPreviewEntity();
    /**
     * 卡片网格的实时 3D 预览实体池，按「当前页内的槽位」索引。
     *
     * <p>每张可见卡都渲染实时小人——模型本身就是卡面（多数模型并不内嵌 gui_background
     * 之类的静态卡图，只渲染悬停/选中两张会让其余卡面全空）。开销靠两处封顶：
     * 池子大小 ≤ {@link ModelPickerLayout#MAX_COLS}×{@link ModelPickerLayout#MAX_ROWS}（每页容量），
     * 翻页/改选时槽位按需换绑模型，不会随目录规模增长。</p>
     */
    private final List<PlayerPreviewEntity> cardPreviews = new ArrayList<>();
    private final List<String> cardPreviewModels = new ArrayList<>();
    private final List<String> cardPreviewTextures = new ArrayList<>();
    /** 上一帧实际生效的网格形态；形态变化时重置滚动量（页号与行号语义不通用）。 */
    private ModelPickerLayout.GridMode lastGridMode;
    private final Screen parentScreen;
    private final BiConsumer<String, String> modelSelectionTarget;
    /**
     * 本次选择是否写入「玩家自己的模型选择」（{@link ClientModelManager#rememberSelectedModel} →
     * {@code LocalModelSelectionStore} 本地持久化）。
     *
     * <p>只有玩家自己的面板为 true。女仆 / NPC 等非玩家目标只改目标实体的模型，若也写进玩家选择，
     * 下次玩家进服时 {@code restorePersistedModelSelection} 会把目标实体的模型自动套到玩家身上。
     * 第三方整合（NPC 面板等）可用 {@link #setRememberPlayerSelection(boolean)} 精确覆写。</p>
     */
    private boolean rememberPlayerSelection;
    private ModelPanelLayout layout;
    private EditBox modelSearchBox;
    private EditBox resourceSearchBox;
    private EditBox siteEditBox;
    private EditBox categoryEditBox;
    private Component status = Component.empty();
    private ChatFormatting statusColor = ChatFormatting.GRAY;
    private boolean resourceStatusMessage;
    private boolean draggingResourceScroll;
    private String previewModelId = "";
    private String previewTextureId = "";
    private String pendingModelApplyId;
    /**
     * 1.2.7 §24.7：本屏的服务层。所有对底层系统（资源站网络、下载队列、导入/上传会话、
     * 模型目录读写、配置持久化）的调用都经此转发 —— Screen 不再直接操作底层系统。
     */
    private final ModernPlayerModelScreenController controller;

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
        this((Screen) null, null, "self");
    }

    public ModernPlayerModelScreen(Screen parentScreen) {
        this(parentScreen, null, "self");
    }

    public ModernPlayerModelScreen(Screen parentScreen, BiConsumer<String, String> modelSelectionTarget) {
        this(parentScreen, modelSelectionTarget, "self");
    }

    public ModernPlayerModelScreen(Screen parentScreen, BiConsumer<String, String> modelSelectionTarget, String stateKey) {
        super(Component.translatable("key.sparkle_morpher.player_model.desc"));
        this.parentScreen = parentScreen;
        this.modelSelectionTarget = modelSelectionTarget;
        // 非玩家目标（女仆 / NPC）默认不写玩家选择，避免把目标实体的模型当成玩家自己的模型
        this.rememberPlayerSelection = modelSelectionTarget == null;
        this.STATE = resolveState(stateKey);
        this.stateKeyValue = stateKey == null || stateKey.isBlank() ? "self" : stateKey;
        // §24.7：服务层持有全部底层系统交互；Host 回调只回填 footer 状态与重建控件。
        this.controller = new ModernPlayerModelScreenController(this.STATE, new ModernPlayerModelScreenController.Host() {
            @Override
            public void postStatus(Component component, ChatFormatting color) {
                setStatus(component, color);
            }

            @Override
            public void postResourceStatus(Component component, ChatFormatting color) {
                setResourceStatus(component, color);
            }

            @Override
            public void requestRerender() {
                ModernPlayerModelScreen.this.init();
            }
        });
    }

    /** 本次选择是否记忆为玩家自己的模型选择（默认：只有玩家自己的面板为 true）。 */
    public boolean shouldRememberPlayerSelection() {
        return this.rememberPlayerSelection;
    }

    /** 覆写本次选择是否记忆为玩家选择，供非玩家面板复用本界面时精确控制。 */
    public void setRememberPlayerSelection(boolean value) {
        this.rememberPlayerSelection = value;
    }

    private static ModelPanelState resolveState(String stateKey) {
        if (STATE_CACHE.size() > STATE_CACHE_LIMIT) {
            STATE_CACHE.clear();
        }
        return STATE_CACHE.computeIfAbsent(stateKey == null || stateKey.isBlank() ? "self" : stateKey,
                key -> new ModelPanelState());
    }

        /**
         * Developer-options: the context key this instance resolved its state from
         * ("self", "maid:<uuid>", "resource").
         */
        public String stateKey() {
            return this.stateKeyValue;
        }

        /** Developer-options: read-only snapshot of this panel's state. */
        public java.util.Map<String, Object> stateSnapshot() {
            return this.STATE.devSnapshot();
        }

    public ModernPlayerModelScreen(ModelPanelState.Tab tab) {
        this(tab, null, "self");
    }

    public ModernPlayerModelScreen(ModelPanelState.Tab tab, Screen parentScreen) {
        this(tab, parentScreen, "self");
    }

    public ModernPlayerModelScreen(ModelPanelState.Tab tab, Screen parentScreen, String stateKey) {
        this(parentScreen, null, stateKey);
        STATE.activeTab = tab;
        if (tab == ModelPanelState.Tab.RESOURCE) {
            STATE.resourceLoaded = false;
        }
    }

    public static ModernPlayerModelScreen resourceStation() {
        return new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE, null, "resource");
    }

    public static ModernPlayerModelScreen settings() {
        return settings(null, "self");
    }

    public static ModernPlayerModelScreen settings(Screen parentScreen) {
        return settings(parentScreen, "self");
    }

    public static ModernPlayerModelScreen settings(Screen parentScreen, String stateKey) {
        return new ModernPlayerModelScreen(ModelPanelState.Tab.SETTINGS, parentScreen, stateKey);
    }

    public static ModernPlayerModelScreen downloads() {
        ModernPlayerModelScreen screen = new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE, null, "resource");
        screen.STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
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
            this.siteEditBox.setValue(STATE.siteEditText.isBlank() ? this.controller.selectedSite() : STATE.siteEditText);
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
        this.controller.invalidateGeneration();
        this.STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
        this.controller.cancelPicking();
        // 释放卡片预览实体池持有的模型引用；本屏重开时会按需重建。
        this.cardPreviewModels.clear();
        this.cardPreviewTextures.clear();
        for (PlayerPreviewEntity entity : this.cardPreviews) {
            try {
                entity.resetModel();
            } catch (Exception ignored) {
            }
        }
        this.cardPreviews.clear();
        super.removed();
    }

    @Override
    public void onClose() {
        if (this.parentScreen != null && this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.controller.tickDownloads();
        this.controller.pollImports();
        if (this.pendingModelApplyId != null) {
            String pendingId = this.pendingModelApplyId;
            this.controller.lookupAssembly(pendingId).ifPresent(assembly -> {
                this.pendingModelApplyId = null;
                if (pendingId.equals(STATE.selectedModelId)) {
                    STATE.selectedTextureId = selectedTextureOrDefault(assembly);
                    applyModelAndTexture(pendingId, STATE.selectedTextureId, assembly);
                }
            });
        }
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
            this.controller.enqueueImportPath(path);
        }
        this.controller.startNextImportIfIdle();
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
        if (this.layout.verticalTabs) {
            int railX = this.layout.left + 3;
            int railW = this.layout.railWidth - 5;
            int th = 26;
            int y = this.layout.top + 6;
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.MODEL, railX, y, railW, th, IconGlyph.MODEL, Component.translatable("gui.sparkle_morpher.model_panel.model"));
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.RESOURCE, railX, y + th + 4, railW, th, IconGlyph.RESOURCE, Component.translatable("gui.sparkle_morpher.resource_station.title"));
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.SETTINGS, railX, y + (th + 4) * 2, railW, th, IconGlyph.SETTINGS, Component.translatable("gui.sparkle_morpher.model_panel.settings"));
            return;
        }
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
        int ty = this.layout.top + (this.layout.tabHeight - 16) / 2;
        drawIcon(g, icon, tx, ty);
        g.text(this.font, label, tx + 22, this.layout.top + (this.layout.tabHeight - this.font.lineHeight) / 2 + 1, selected ? 0xFFFFFFFF : MUTED, false);
        hit(x, this.layout.top, w, this.layout.tabHeight, label, () -> switchTab(tab));
    }

    private void renderVerticalTab(GuiGraphicsExtractor g, int mouseX, int mouseY, ModelPanelState.Tab tab, int x, int y, int w, int h, IconGlyph icon, Component label) {
        boolean selected = STATE.activeTab == tab;
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        fill(g, x, y, w, h, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x40303030);
        border(g, x, y, w, h, selected ? RED : 0x33FFFFFF);
        drawIcon(g, icon, x + (w - 16) / 2, y + (h - 16) / 2);
        hit(x, y, w, h, label, () -> switchTab(tab));
    }

    private void switchTab(ModelPanelState.Tab tab) {
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
    }

    private int modelLeftX() {
        return this.layout.contentLeft + 8;
    }

    private boolean listPriority() {
        return this.layout.contentWidth < 680 || this.layout.contentHeight < 260;
    }

    private boolean compactModelLayout() {
        return listPriority();
    }

    private int modelLeftW() {
        if (compactModelLayout()) {
            return 0;
        }
        int minList = modelListMinW();
        int max = Math.max(72, Math.min(140, this.layout.contentWidth - minList - 86 - 28));
        return clamp(this.layout.contentWidth / 4, 72, max);
    }

    private int modelRightW() {
        if (compactModelLayout()) {
            return 0;
        }
        int max = Math.max(72, Math.min(180, this.layout.contentWidth - modelLeftW() - modelListMinW() - 28));
        return clamp(this.layout.contentWidth / 3, 72, max);
    }

    private int modelListX() {
        if (compactModelLayout()) {
            return this.layout.contentLeft + 8;
        }
        return modelLeftX() + modelLeftW() + 10;
    }

    private int modelListW() {
        if (compactModelLayout()) {
            return Math.max(60, this.layout.contentWidth - 16);
        }
        return Math.max(24, this.layout.contentWidth - modelLeftW() - modelRightW() - 28);
    }

    private int modelListMinW() {
        return this.layout.contentWidth < 420 ? 54 : 90;
    }

    private int resourceListX() {
        return this.layout.contentLeft + 8;
    }

    private int resourceRightW() {
        boolean priority = listPriority();
        int minRight = this.layout.contentWidth < 420 ? 90 : (priority ? 118 : 170);
        int minList = this.layout.contentWidth < 420 ? 90 : 120;
        int max = Math.max(minRight, Math.min(priority ? 190 : 260, this.layout.contentWidth - minList - 22));
        return clamp(this.layout.contentWidth / (priority ? 4 : 3), minRight, max);
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
        boolean compact = compactModelLayout();
        int x = modelLeftX();
        int y = this.layout.contentTop + 8;
        int leftW = modelLeftW();
        int rightW = modelRightW();
        int listX = modelListX();
        int listW = modelListW();
        int detailX = listX + listW + 10;
        int contentBottom = this.layout.footerTop - 6;

        if (compact) {
            int filtersY = y + 24;
            renderChip(g, listX, filtersY, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.all"), STATE.modelFilter == ModelPanelState.ModelFilter.ALL, () -> setModelFilter(ModelPanelState.ModelFilter.ALL));
            renderChip(g, listX + 42, filtersY, 42, Component.translatable("gui.sparkle_morpher.model_panel.filter.auth"), STATE.modelFilter == ModelPanelState.ModelFilter.AUTH, () -> setModelFilter(ModelPanelState.ModelFilter.AUTH));
            renderChip(g, listX + 88, filtersY, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.star"), STATE.modelFilter == ModelPanelState.ModelFilter.STAR, () -> setModelFilter(ModelPanelState.ModelFilter.STAR));
            boolean stackedControls = listW < 260;
            int actionsX = stackedControls ? listX : Math.max(listX, Math.min(listX + listW - 120, listX + 132));
            int actionsY = stackedControls ? y + 42 : y + 22;
            renderIconButton(g, mouseX, mouseY, actionsX, actionsY, IconGlyph.IMPORT, Component.translatable("gui.sparkle_morpher.import.tooltip"), () -> openImportPanel());
            renderIconButton(g, mouseX, mouseY, actionsX + 24, actionsY, IconGlyph.FOLDER, Component.translatable("gui.sparkle_morpher.open_model_folder.open"), this::openModelFolder);
            renderIconButton(g, mouseX, mouseY, actionsX + 48, actionsY, IconGlyph.ROULETTE, Component.translatable("key.sparkle_morpher.animation_roulette.desc"), this::openRoulette);
            renderIconButton(g, mouseX, mouseY, actionsX + 72, actionsY, IconGlyph.CATEGORY, Component.translatable("gui.sparkle_morpher.model_select.new_category"), () -> openCategoryPanel(""));
            renderIconButton(g, mouseX, mouseY, actionsX + 96, actionsY, IconGlyph.MULTI, Component.translatable("gui.sparkle_morpher.model_panel.multi_select"), () -> {
                STATE.multiSelectMode = !STATE.multiSelectMode;
                this.selectedModelIds.clear();
            });
        } else {
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
        }

        if (this.modelSearchBox != null) {
            this.modelSearchBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
        }
        boolean stackedControls = compact && listW < 260;
        int pathY = compact ? stackedControls ? y + 68 : y + 48 : y + 30;
        renderPathBar(g, listX, pathY, listW);
        int gridY = pathY + 20;
        int actionsBandY = contentBottom - 28;
        int detailStripH = compactDetailStripH();
        int reserve = detailStripH > 0 ? detailStripH + 3 : 0;
        int gridH = Math.max(compact ? 34 : 50, actionsBandY - 4 - reserve - gridY);
        renderModelGrid(g, mouseX, mouseY, listX, gridY, listW, gridH);
        if (detailStripH > 0) {
            renderCompactDetail(g, mouseX, mouseY, listX, gridY + gridH + 3, listW, detailStripH, partialTick);
        }
        renderModelBottomActions(g, mouseX, mouseY, listX, actionsBandY, listW, gridH);
        if (!compact) {
            renderModelDetails(g, mouseX, mouseY, detailX, y, rightW, contentBottom - y, partialTick);
        }
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
        int count = this.controller.availableModelCount();
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
        if (entries.isEmpty()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.model_panel.no_models"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        ModelPickerLayout.GridMode mode = gridMode(w, h);
        if (mode == ModelPickerLayout.GridMode.CARDS) {
            renderModelCards(g, mouseX, mouseY, entries, x, y, w, h);
            return;
        }
        renderModelTextGrid(g, mouseX, mouseY, entries, x, y, w, h);
    }

    /**
     * 当前生效的网格形态。形态只在 {@code pickerStyle} 变化或窗口尺寸变化时改变，
     * 因此在生效形态切换的那一帧重置 {@code modelScroll}——卡片模式下它是页号、
     * 文字网格下是像素滚动量，二者语义不通用，混用会出现页数错位。
     */
    private ModelPickerLayout.GridMode gridMode(int w, int h) {
        ModelPickerLayout.Style style = pickerStyle();
        ModelPickerLayout.GridMode mode = ModelPickerLayout.resolve(style, w, h);
        if (mode != this.lastGridMode) {
            STATE.modelScroll = 0;
            this.lastGridMode = mode;
        }
        return mode;
    }

    /** 展示偏好：用户在本屏切换过就以本屏为准，否则读配置默认值。 */
    private ModelPickerLayout.Style pickerStyle() {
        if (!STATE.pickerStylePinned) {
            ModelPickerLayout.Style configured = configuredPickerStyle();
            if (configured != null) {
                STATE.pickerStyle = configured;
                STATE.pickerStylePinned = true;
            }
        }
        return STATE.pickerStyle;
    }

    private static ModelPickerLayout.Style configuredPickerStyle() {
        try {
            GeneralConfig.ModelPickerStyle style = com.micaftic.morpher.core.config.ConfigPolicies.modelPickerStyle();
            return style == null ? null : ModelPickerLayout.Style.valueOf(style.name());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** AUTO → CARDS → LIST 循环；切到 LIST 后再次点击回到 AUTO。 */
    private void cyclePickerStyle() {
        ModelPickerLayout.Style next = switch (pickerStyle()) {
            case AUTO -> ModelPickerLayout.Style.CARDS;
            case CARDS -> ModelPickerLayout.Style.LIST;
            case LIST -> ModelPickerLayout.Style.AUTO;
        };
        STATE.pickerStyle = next;
        STATE.pickerStylePinned = true;
        STATE.modelScroll = 0;
    }

    private Component pickerStyleLabel(ModelPickerLayout.Style style) {
        String key = switch (style) {
            case AUTO -> "gui.sparkle_morpher.model_panel.picker.auto";
            case CARDS -> "gui.sparkle_morpher.model_panel.picker.cards";
            case LIST -> "gui.sparkle_morpher.model_panel.picker.list";
        };
        return Component.translatable(key);
    }

    /** 经典图标+文字网格（AUTO 面积不足时的降级路径，也是 LIST 强制形态）。 */
    private void renderModelTextGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, List<ModelEntry> entries, int x, int y, int w, int h) {
        Set<String> starredModels = starModels();
        ModelListMetrics metrics = modelListMetrics(w, h, entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, metrics.maxScroll());
        int start = STATE.modelScroll * metrics.cols();
        for (int i = 0; i < metrics.rows() * metrics.cols() && start + i < entries.size(); i++) {
            ModelEntry entry = entries.get(start + i);
            int cx = x + (i % metrics.cols()) * metrics.cellW() + 3;
            int cy = y + (i / metrics.cols()) * metrics.cellH() + 3;
            int cw = metrics.cellW() - 6;
            int ch = metrics.cellH() - 6;
            boolean selected = entry.modelId().equals(STATE.selectedModelId) || this.selectedModelIds.contains(entry.modelId());
            boolean hover = inside(mouseX, mouseY, cx, cy, cw, ch);
            boolean starred = !entry.folder() && starredModels.contains(entry.modelId());
            fill(g, cx, cy, cw, ch, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x3E30363B);
            border(g, cx, cy, cw, ch, selected ? RED : 0x33FFFFFF);
            int iconY = metrics.dense() ? cy + Math.max(0, (ch - 16) / 2) : cy + 3;
            drawIcon(g, entry.folder() ? IconGlyph.FOLDER : entry.locked() ? IconGlyph.LOCK : IconGlyph.MODEL, cx + 4, iconY);
            if (metrics.dense()) {
                if (starred) {
                    drawIcon(g, IconGlyph.STAR, cx + cw - 18, iconY);
                }
                int titleW = starred ? cw - 44 : cw - 26;
                drawText(g, Component.literal(trim(entry.title(), titleW)), cx + 22, cy + (ch - this.font.lineHeight) / 2 + 1);
            } else {
                if (starred) {
                    drawIcon(g, IconGlyph.STAR, cx + cw - 16, cy + metrics.cellH() - 22);
                }
                drawText(g, Component.literal(trim(entry.title(), cw - 28)), cx + 22, cy + 6);
                drawMuted(g, Component.literal(trim(entry.subtitle(), cw - 12)), cx + 6, cy + 22);
            }
            hit(cx, cy, cw, ch, Component.literal(entry.title()), () -> clickModelEntry(entry));
        }
    }

    /**
     * 目录卡片网格。整块按可用面积居中；每张可见模型卡都渲染实时 3D 小人（模型即卡面），
     * 未就绪的懒加载模型先显示加载提示并在后台拉起。
     */
    private void renderModelCards(GuiGraphicsExtractor g, int mouseX, int mouseY, List<ModelEntry> entries, int x, int y, int w, int h) {
        ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h);
        int pages = m.totalPages(entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, pages - 1);
        int start = STATE.modelScroll * m.capacity();
        int x0 = x + m.originX(w);
        int y0 = y + m.originY(h);
        Set<String> starred = starModels();
        ensureCardPool(m.capacity());
        int hoverIndex = -1;
        for (int i = 0; i < m.capacity() && start + i < entries.size(); i++) {
            int r = i / m.cols();
            int c = i % m.cols();
            int cx = x0 + c * (m.cellW() + ModelPickerLayout.CARD_GAP);
            int cy = y0 + r * (m.cellH() + ModelPickerLayout.CARD_GAP);
            if (inside(mouseX, mouseY, cx, cy, m.cellW(), m.cellH())) {
                hoverIndex = start + i;
                break;
            }
        }
        for (int i = 0; i < m.capacity() && start + i < entries.size(); i++) {
            int r = i / m.cols();
            int c = i % m.cols();
            int cx = x0 + c * (m.cellW() + ModelPickerLayout.CARD_GAP);
            int cy = y0 + r * (m.cellH() + ModelPickerLayout.CARD_GAP);
            boolean hover = start + i == hoverIndex;
            renderModelCard(g, entries.get(start + i), i, cx, cy, m.cellW(), m.cellH(), hover, starred);
        }
    }

    /** 把预览实体池扩到至少 size 个槽位；上限由每页容量常量决定，不会随目录规模增长。 */
    private void ensureCardPool(int size) {
        while (this.cardPreviews.size() < size) {
            this.cardPreviews.add(new PlayerPreviewEntity());
            this.cardPreviewModels.add("");
            this.cardPreviewTextures.add("");
        }
    }

    /** 取/换绑某槽位的预览实体；换模型或贴图时重初始化。失败返回 null（调用方回退图标）。 */
    private PlayerPreviewEntity cardPreviewEntity(int slot, String modelId, String textureId) {
        if (slot < 0 || slot >= this.cardPreviews.size()) {
            return null;
        }
        PlayerPreviewEntity entity = this.cardPreviews.get(slot);
        if (!modelId.equals(this.cardPreviewModels.get(slot)) || !Objects.equals(textureId, this.cardPreviewTextures.get(slot))) {
            try {
                entity.initModelWithTexture(modelId, textureId);
            } catch (Exception e) {
                return null;
            }
            this.cardPreviewModels.set(slot, modelId);
            this.cardPreviewTextures.set(slot, textureId);
        }
        return entity;
    }

    /** 单张卡：底 → 封面/实时小人 → 名字条 → 角标 → 描边 → 点击区。 */
    private void renderModelCard(GuiGraphicsExtractor g, ModelEntry entry, int slot, int cx, int cy, int cw, int ch, boolean hover, Set<String> starredModels) {
        boolean folder = entry.folder();
        boolean selected = entry.modelId().equals(STATE.selectedModelId) || this.selectedModelIds.contains(entry.modelId());
        boolean multiSelected = !folder && !entry.modelId().equals(STATE.selectedModelId) && this.selectedModelIds.contains(entry.modelId());
        boolean starred = !folder && starredModels.contains(entry.modelId());
        boolean locked = !folder && entry.locked();
        int nameH = ModelPickerLayout.nameBandH(ch);
        int coverH = Math.max(1, ch - nameH);

        if (folder) {
            fill(g, cx, cy, cw, ch, hover ? CARD_FOLDER_HOVER : CARD_FOLDER);
        } else {
            fill(g, cx, cy, cw, ch, selected ? CARD_SELECT : hover ? CARD_BASE_HOVER : CARD_BASE);
        }

        if (folder) {
            drawFolderCover(g, entry, cx + 1, cy + 1, cw - 2, coverH - 1);
        } else if (locked) {
            int size = Math.min(26, Math.min(cw - 8, coverH - 8));
            drawIconScaled(g, IconGlyph.LOCK, cx + Math.max(0, (cw - size) / 2), cy + Math.max(1, (coverH - size) / 2), size);
        } else {
            renderCardMonster(g, slot, entry.modelId(), cx, cy, cw, ch, coverH);
        }

        // 名字条压在封面之上，保证不被 3D 小人遮挡。
        fill(g, cx, cy + coverH, cw, nameH, CARD_NAME_BG);
        drawCardName(g, entry, cx, cy + coverH, cw, nameH, folder);

        if (locked) {
            fill(g, cx, cy, cw, ch, CARD_DIM);
        } else if (!folder) {
            if (multiSelected) {
                drawIcon(g, IconGlyph.CHECK, cx + cw - 18, cy + 3);
            } else if (starred) {
                drawIcon(g, IconGlyph.STAR, cx + cw - 18, cy + 3);
            }
        }

        border(g, cx, cy, cw, ch, selected ? RED : hover ? CARD_NAME_TEXT : 0x55FFFFFF);
        hit(cx, cy, cw, ch, Component.literal(entry.title()), () -> clickModelEntry(entry));
    }

    /** 卡片名字条：单行居中；卡够高且有条目副标题时排两行。超长省略，悬停 tooltip 给全名。 */
    private void drawCardName(GuiGraphicsExtractor g, ModelEntry entry, int bx, int by, int cw, int nameH, boolean folder) {
        int w = Math.max(6, cw - 6);
        String title = trim(entry.title(), w);
        String sub = entry.subtitle();
        boolean twoLine = !folder && nameH >= 28 && sub != null && !sub.isEmpty() && !"folder".equals(sub);
        int tx = bx + 3;
        if (twoLine) {
            g.text(this.font, Component.literal(title), tx, by + 3, folder ? 0xFF2B2B2B : CARD_NAME_TEXT, false);
            g.text(this.font, Component.literal(trim(sub, w)), tx, by + nameH - this.font.lineHeight - 3, MUTED, false);
        } else {
            g.text(this.font, Component.literal(title), tx, by + Math.max(2, (nameH - this.font.lineHeight) / 2), folder ? 0xFF2B2B2B : CARD_NAME_TEXT, false);
        }
    }

    /** 文件夹卡封面：ysm_pack.json 里的贴图，缺省用内置默认图标。 */
    private void drawFolderCover(GuiGraphicsExtractor g, ModelEntry entry, int x, int y, int w, int h) {
        AbstractTexture texture = null;
        try {
            var pack = this.controller.modelPack(entry.modelId());
            texture = pack == null ? null : pack.getTexture();
        } catch (Exception ignored) {
        }
        if (!drawCoverImage(g, texture, x, y, w, h)) {
            int size = Math.min(30, Math.min(w - 6, h - 6));
            drawIconScaled(g, IconGlyph.FOLDER, x + Math.max(0, (w - size) / 2), y + Math.max(1, (h - size) / 2), size);
        }
    }

    /**
     * 卡片里的实时 3D 小人：YSM 卡面三层构图（背景图 → 模型 → 前景边框）的中间层。
     * 用 {@link GuiModelRenderer#enqueueLivingPreview} 的显式 yaw 入口（兼容 facade 已不暴露 yaw）。
     */
    private void renderCardMonster(GuiGraphicsExtractor g, int slot, String modelId, int cx, int cy, int cw, int ch, int coverH) {
        if (cw < 18 || coverH < 26) {
            return;
        }
        // getModelContext 对懒模型会调度后台加载并返回 empty——正好用来「请求加载」；
        // 落地后下一帧这里就拿到常驻 assembly，卡片自动升级为实时小人。
        ModelAssembly asm = this.controller.assemblyOrNull(modelId);
        if (asm == null) {
            drawCardLoading(g, cx, cy, cw, coverH);
            return;
        }
        // YSM 卡面是三层构图：背景图 → 实时模型 → 前景边框。背景整幅铺在整张卡上
        // （含名字条区域），前景边框最后叠回，模型的头/脚就落在边框留白里，不会被裁。
        ModelDisplayAssets assets = asm.getTextureRegistry();
        AbstractTexture background = assets == null ? null : assets.getGuiBackground();
        AbstractTexture foreground = assets == null ? null : assets.getGuiForeground();
        boolean hasBackground = drawCoverImage(g, background, cx, cy, cw, ch);
        if (!hasBackground) {
            // 无卡图：退回前景（可能也是边框）或纯卡底色即可，无需额外处理。
            drawCoverImage(g, foreground, cx, cy, cw, ch);
        }
        boolean drewFigure = false;
        if (!asm.isGltf()) {
            drewFigure = renderCardFigure(g, slot, modelId, asm, cx, cy, cw, coverH);
        }
        // 前景边框叠在小人之上（其中心透明，正好露出小人）；未被当作背景用过才叠。
        if (foreground != null && foreground != background) {
            drawCoverImage(g, foreground, cx, cy, cw, ch);
        }
        if (!drewFigure && !hasBackground && foreground == null) {
            int size = Math.min(34, Math.min(cw - 8, coverH - 12));
            drawIconScaled(g, IconGlyph.MODEL, cx + Math.max(0, (cw - size) / 2), cy + Math.max(2, (coverH - size) / 2), size);
        }
    }

    /**
     * 卡片内的实时小人。返回是否真的排了渲染。
     *
     * <p>缩放取「封面高 × {@link ModelPickerLayout#CARD_FIGURE_SCALE}」——与右侧详情栏同一比例，
     * 保证整只模型落进卡面而不被上沿裁掉；预览盒用整块封面（而非居中正方形），
     * 竖卡才不会在上下留出空档。</p>
     */
    private boolean renderCardFigure(GuiGraphicsExtractor g, int slot, String modelId, ModelAssembly asm, int cx, int cy, int cw, int coverH) {
        try {
            String textureId = selectedTextureOrDefault(asm);
            PlayerPreviewEntity entity = cardPreviewEntity(slot, modelId, textureId);
            if (entity == null) {
                return false;
            }
            this.controller.markModelUsed(modelId);
            if (!entity.isModelReady()) {
                return false;
            }
            boolean disableRotation;
            try {
                var props = asm.getModelData().getModelProperties();
                disableRotation = props != null && props.isDisablePreviewRotation();
            } catch (Exception ignored) {
                disableRotation = false;
            }
            float scale = ModelPickerLayout.figureScale(coverH);
            float yaw = disableRotation ? 180.0f : 176.0f;
            int left = cx + 1;
            int top = cy + 1;
            int right = cx + cw - 1;
            int bottom = cy + coverH - 1;
            GuiModelRenderer.enqueueLivingPreview(g, left, top, right, bottom,
                    cx + cw / 2.0f, cy + coverH - 3.0f, scale, 0.0f, entity,
                    RendererManager.getPlayerRenderer(), disableRotation, true, yaw, 0.0f, false, GuiModelRenderer.DollOptions.GUI_PREVIEW);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void drawCardLoading(GuiGraphicsExtractor g, int cx, int cy, int cw, int coverH) {
        String s = "…";
        g.text(this.font, Component.literal(s), cx + (cw - this.font.width(s)) / 2, cy + Math.max(4, (coverH - this.font.lineHeight) / 2), CARD_NAME_TEXT, false);
    }

    /** 把封面等比 fit 进 (x,y,w,h)，居中不拉伸；拿不到资源/尺寸时返回 false。 */
    private boolean drawCoverImage(GuiGraphicsExtractor g, AbstractTexture tex, int x, int y, int w, int h) {
        if (tex == null || w <= 0 || h <= 0) {
            return false;
        }
        int[] dims = coverDimensions(tex);
        if (dims == null) {
            return false;
        }
        Identifier loc = this.controller.textureLocation(tex, dims[0]);
        if (loc == null) {
            return false;
        }
        int texW = dims[0];
        int texH = dims[1];
        double fit = Math.min((double) w / texW, (double) h / texH);
        int dw = Math.max(1, (int) Math.floor(texW * fit));
        int dh = Math.max(1, (int) Math.floor(texH * fit));
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        g.blit(loc, dx, dy, dx + dw, dy + dh, 0.0f, 1.0f, 0.0f, 1.0f);
        return true;
    }

    /** 封面像素尺寸：GUI 图一定是 OuterFileTexture（构建链 toPng），直接读 PNG IHDR。 */
    private int[] coverDimensions(AbstractTexture tex) {
        if (!(tex instanceof OuterFileTexture outer)) {
            return null;
        }
        synchronized (COVER_DIMENSIONS) {
            int[] dims = COVER_DIMENSIONS.get(tex);
            if (dims == null) {
                dims = pngHeaderSize(outer.getResourceData());
                if (dims != null) {
                    COVER_DIMENSIONS.put(tex, dims);
                }
            }
            return dims;
        }
    }

    private static int[] pngHeaderSize(byte[] data) {
        if (data == null || data.length < 24) {
            return null;
        }
        if ((data[0] & 0xFF) != 0x89 || data[1] != 0x50 || data[2] != 0x4E || data[3] != 0x47) {
            return null;
        }
        int w = readIntBE(data, 16);
        int h = readIntBE(data, 20);
        return w > 0 && h > 0 && w <= 16384 && h <= 16384 ? new int[]{w, h} : null;
    }

    private static int readIntBE(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16) | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    /** 图标纹理按 size 等比放大绘制（源是 16px 格）。 */
    private void drawIconScaled(GuiGraphicsExtractor g, IconGlyph icon, int x, int y, int size) {
        if (size <= 0) {
            return;
        }
        g.blit(MODEL_PANEL_ICONS, x, y, x + size, y + size, icon.u / 128.0f, (icon.u + 16) / 128.0f, icon.v / 64.0f, (icon.v + 16) / 64.0f);
    }

    private ModelListMetrics modelListMetrics(int w, int h, int entryCount) {
        boolean dense = compactModelLayout() || h < 132;
        int targetW = dense ? 104 : 116;
        int minW = dense ? 86 : 92;
        int maxW = dense ? 132 : 150;
        int cellW = Math.max(minW, Math.min(maxW, w / Math.max(1, w / targetW)));
        int cols = Math.max(1, w / cellW);
        int cellH = dense ? DENSE_MODEL_ROW : h < 90 ? 42 : 50;
        int rows = Math.max(1, h / cellH);
        int maxScroll = Math.max(0, (entryCount + cols - 1) / cols - rows);
        return new ModelListMetrics(cellW, cellH, cols, rows, maxScroll, dense);
    }

    private List<ModelEntry> visibleModelEntries() {
        List<ModelEntry> entries = collectModelEntries();
        int listW = modelListW();
        int listH = currentModelGridH();
        if (gridMode(listW, listH) == ModelPickerLayout.GridMode.CARDS) {
            ModelPickerLayout.Cards cards = ModelPickerLayout.cards(listW, listH);
            int page = clamp(STATE.modelScroll, 0, cards.totalPages(entries.size()) - 1);
            STATE.modelScroll = page;
            int start = page * cards.capacity();
            int end = Math.min(entries.size(), start + cards.capacity());
            return start >= end ? List.of() : entries.subList(start, end);
        }
        ModelListMetrics metrics = modelListMetrics(listW, listH, entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, metrics.maxScroll());
        int start = STATE.modelScroll * metrics.cols();
        int end = Math.min(entries.size(), start + metrics.rows() * metrics.cols());
        return start >= end ? List.of() : entries.subList(start, end);
    }

    private int currentModelGridH() {
        int y = this.layout.contentTop + 8;
        int contentBottom = this.layout.footerTop - 6;
        boolean compact = compactModelLayout();
        boolean stackedControls = compact && modelListW() < 260;
        int pathY = compact ? stackedControls ? y + 68 : y + 48 : y + 30;
        int gridY = pathY + 20;
        int detailStripH = compactDetailStripH();
        int reserve = detailStripH > 0 ? detailStripH + 3 : 0;
        return Math.max(compact ? 34 : 50, contentBottom - 28 - 4 - reserve - gridY);
    }

    private void renderModelBottomActions(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int gridH) {
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
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.RELOAD, Component.translatable("gui.sparkle_morpher.model_panel.reload_models"), () -> this.controller.reloadLocalModels(this::setStatus));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.UP, getCustomFolderUploadTooltip(), this::openCustomFolderUpload);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.MODE, pickerStyleLabel(pickerStyle()), this::cyclePickerStyle);
        }
        renderModelPageControls(g, mouseX, mouseY, x, y, w, gridH);
    }

    private void renderModelPageControls(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int gridH) {
        List<ModelEntry> entries = collectModelEntries();
        if (entries.isEmpty()) {
            return;
        }
        int nextX = x + w - MODEL_PAGE_BUTTON_WIDTH - 6;
        int prevX = nextX - MODEL_PAGE_BUTTON_WIDTH - 4;
        if (gridMode(w, gridH) == ModelPickerLayout.GridMode.CARDS) {
            // 卡片模式：滚动量是页号，整页前进/后退。
            ModelPickerLayout.Cards cards = ModelPickerLayout.cards(w, gridH);
            int pages = cards.totalPages(entries.size());
            int page = clamp(STATE.modelScroll, 0, pages - 1);
            STATE.modelScroll = page;
            int start = page * cards.capacity();
            int end = Math.min(entries.size(), start + cards.capacity());
            Component range = Component.literal((start + 1) + "-" + end + "/" + entries.size());
            int labelX = Math.max(x + 82, prevX - this.font.width(range) - 8);
            drawMuted(g, range, labelX, y + 8);
            renderTextButton(g, mouseX, mouseY, prevX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.pre_page"), () -> {
                if (page > 0) {
                    STATE.modelScroll = page - 1;
                }
            });
            renderTextButton(g, mouseX, mouseY, nextX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.next_page"), () -> {
                if (page < pages - 1) {
                    STATE.modelScroll = page + 1;
                }
            });
            return;
        }
        ModelListMetrics metrics = modelListMetrics(w, gridH, entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, metrics.maxScroll());
        int visible = metrics.rows() * metrics.cols();
        int start = Math.min(entries.size(), STATE.modelScroll * metrics.cols());
        int end = Math.min(entries.size(), start + visible);
        Component range = Component.literal((start + 1) + "-" + end + "/" + entries.size());
        int labelX = Math.max(x + 82, prevX - this.font.width(range) - 8);
        drawMuted(g, range, labelX, y + 8);
        renderTextButton(g, mouseX, mouseY, prevX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.pre_page"), () -> {
            STATE.modelScroll = Math.max(0, STATE.modelScroll - metrics.rows());
        });
        renderTextButton(g, mouseX, mouseY, nextX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.next_page"), () -> {
            STATE.modelScroll = Math.min(metrics.maxScroll(), STATE.modelScroll + metrics.rows());
        });
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
        if (assembly.isGltf()) {
            drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
            drawCentered(g, Component.literal("glTF"), x + w / 2, y + h - 12, MUTED);
            return;
        }
        String textureId = selectedTextureOrDefault(assembly);
        boolean wasTrimmed = this.controller.isGpuCacheTrimmed(modelId);
        this.controller.markModelUsed(modelId);
        if (wasTrimmed || !Objects.equals(this.previewModelId, modelId) || !Objects.equals(this.previewTextureId, textureId)) {
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
        boolean showBar = entries.size() > rows;
        int ww = showBar ? w - 9 : w;
        for (int i = 0; i < rows && STATE.resourceScroll + i < entries.size(); i++) {
            ModelRepoEntry entry = entries.get(STATE.resourceScroll + i);
            int rowY = y + i * ROW;
            boolean selected = this.controller.isResourceSelected(entry);
            boolean hover = inside(mouseX, mouseY, x + 3, rowY + 2, ww - 6, ROW - 4);
            fill(g, x + 3, rowY + 2, ww - 6, ROW - 4, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : (i & 1) == 0 ? 0x3E30363B : 0x3630363B);
            g.text(this.font, trim(entry.name(), ww - 78), x + 8, rowY + 5, TEXT, false);
            g.text(this.font, trim(resourceDetail(entry), ww - 100), x + 8, rowY + 15, MUTED, false);
            hit(x + 3, rowY + 2, ww - 38, ROW - 4, Component.literal(entry.name()), () -> this.controller.clickResource(entry));
            renderIconButton(g, mouseX, mouseY, x + ww - 28, rowY + 4, this.controller.isQueued(entry) ? IconGlyph.QUEUE : IconGlyph.DOWNLOAD, Component.translatable("gui.sparkle_morpher.model_panel.download"), () -> enqueueResource(entry));
        }
        if (showBar) {
            renderScrollbar(g, mouseX, mouseY, x + w - 7, y + 3, 4, h - 6, entries.size(), rows, STATE.resourceScroll);
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
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.queue"), x + 8, yy);
        yy += 12;
        List<ModernPlayerModelScreenController.TaskView> rows = this.controller.queueRows();
        if (rows.isEmpty()) {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.no_downloads"), x + 8, yy);
            yy += 14;
        } else {
            for (ModernPlayerModelScreenController.TaskView task : rows) {
                if (yy + 22 > y + h - 30) {
                    break;
                }
                renderTaskRow(g, x + 8, yy, w - 16, task);
                yy += 24;
            }
        }
        renderIconButton(g, mouseX, mouseY, x + 8, y + h - 24, IconGlyph.CLEAR, Component.translatable("gui.sparkle_morpher.resource_station.clear_finished"), this.controller::clearFinishedDownloads);
        renderIconButton(g, mouseX, mouseY, x + 32, y + h - 24, IconGlyph.CANCEL, Component.translatable("gui.sparkle_morpher.model_panel.cancel_current"), this.controller::cancelCurrentDownload);
    }

    private void renderTaskRow(GuiGraphicsExtractor g, int x, int y, int w, ModernPlayerModelScreenController.TaskView task) {
        fill(g, x, y, w, 20, GLASS_DARK);
        drawText(g, Component.literal(trim(task.name(), w - 58)), x + 4, y + 3);
        int barX = x + 4;
        int barY = y + 14;
        int fillW = (int) ((w - 8) * clamp(task.progress(), 0f, 1f));
        fill(g, barX, barY, w - 8, 3, 0xAA101010);
        fill(g, barX, barY, fillW, 3, task.color());
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
            case CACHE -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.cache");
            case DEVELOPER -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.developer");
            case MISC -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.misc");
        };
    }

    private void renderSettingRow(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, SettingRow row) {
        if (row.sectionKey() != null) {
            drawSection(g, Component.translatable(row.sectionKey()), x + 6, y + 6);
            return;
        }
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
        if (row.booleanValue() != null) {
            g.text(this.font, trim(label.getString(), w - 82), x + 6, y + 6, TEXT, false);
            int bx = x + w - 34;
            fill(g, bx, y + 4, 26, 11, row.booleanValue() ? RED_SOFT : 0x55303030);
            fill(g, bx + (row.booleanValue() ? 15 : 2), y + 5, 9, 9, 0xFFEDE1CC);
            hit(x, y, w, 19, label, row.action());
        } else if (row.decrement() == null && row.increment() == null) {
            int valueW = Math.min(104, Math.max(48, this.font.width(row.valueText()) + 14));
            int valueX = x + w - valueW - 8;
            g.text(this.font, trim(label.getString(), valueX - x - 12), x + 6, y + 6, TEXT, false);
            if (row.action() != null) {
                hit(x, y, w, 19, label, row.action());
            }
            renderTextButton(g, mouseX, mouseY, valueX, y + 2, valueW, 15, Component.literal(row.valueText()), row.action());
        } else {
            g.text(this.font, trim(label.getString(), w - 82), x + 6, y + 6, TEXT, false);
            if (row.decrement() != null) {
                renderIconButton(g, mouseX, mouseY, x + w - 50, y + 1, IconGlyph.MINUS, Component.translatable(row.labelKey()), row.decrement());
            }
            if (row.increment() != null) {
                renderIconButton(g, mouseX, mouseY, x + w - 24, y + 1, IconGlyph.PLUS, Component.translatable(row.labelKey()), row.increment());
            }
            drawMuted(g, Component.literal(row.valueText()), x + w - 90, y + 6);
            if (row.action() != null) {
                hit(x, y, w - 56, 19, label, row.action());
            }
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
        List<String> urls = this.controller.siteUrls();
        int maxScroll = Math.max(0, urls.size() - rows);
        STATE.sitesScroll = clamp(STATE.sitesScroll, 0, maxScroll);
        for (int i = 0; i < rows && STATE.sitesScroll + i < urls.size(); i++) {
            String url = urls.get(STATE.sitesScroll + i);
            boolean selected = url.equals(this.controller.selectedSite());
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
        renderIconButton(g, mouseX, mouseY, x + 16, by, IconGlyph.CREATE, Component.translatable("gui.sparkle_morpher.model_panel.create"), () -> setStatus(this.controller.createCategory(STATE.categoryEditText)));
        renderIconButton(g, mouseX, mouseY, x + 42, by, IconGlyph.MOVE, Component.translatable("gui.sparkle_morpher.model_panel.move"), () -> moveSelectionToCategory(STATE.categoryEditText));
        renderIconButton(g, mouseX, mouseY, x + 68, by, IconGlyph.DELETE, Component.translatable("gui.sparkle_morpher.model_panel.delete"), () -> setStatus(this.controller.deleteCategory(STATE.categoryEditText, false)));
        List<String> categories = this.controller.listCategories();
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
        if (this.controller.uploadSessionActive()) {
            drawText(g, this.controller.uploadSessionMessage(), x + 12, yy);
            yy += 14;
            int barW = w - 24;
            fill(g, x + 12, yy, barW, 8, 0xAA101010);
            fill(g, x + 12, yy, (int) (barW * clamp(this.controller.uploadSessionProgress(), 0f, 1f)), 8, this.controller.uploadSessionFailed() ? 0xFFD23232 : RED);
            yy += 16;
            drawMuted(g, Component.literal(this.controller.uploadSessionBytesText()), x + 12, yy);
        } else if (this.controller.localImportInProgress()) {
            drawText(g, Component.translatable("gui.sparkle_morpher.model_panel.importing"), x + 12, yy);
        } else {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.no_active_import"), x + 12, yy);
        }
    }

    private void renderFooter(GuiGraphicsExtractor g) {
        fill(g, this.layout.left, this.layout.footerTop, this.layout.width, 1, 0x55303030);
        Component line = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? this.controller.queueStatus() : this.status;
        ChatFormatting color = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? this.controller.queueStatusColor() : this.statusColor;
        int c = color.getColor() == null ? MUTED : 0xFF000000 | color.getColor();
        g.text(this.font, trim(line.getString(), this.layout.width - 20), this.layout.left + 10, this.layout.footerTop + 8, c, false);
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
        if (button == 0 && beginResourceScrollDrag(mouseX, mouseY)) {
            return true;
        }
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
            case MODEL -> {
                if (gridMode(modelListW(), currentModelGridH()) == ModelPickerLayout.GridMode.CARDS) {
                    List<ModelEntry> entries = collectModelEntries();
                    int pages = entries.isEmpty() ? 1
                            : ModelPickerLayout.cards(modelListW(), currentModelGridH()).totalPages(entries.size());
                    STATE.modelScroll = clamp(STATE.modelScroll + delta, 0, pages - 1);
                } else {
                    STATE.modelScroll = Math.max(0, STATE.modelScroll + delta);
                }
            }
            case RESOURCE -> STATE.resourceScroll = Math.max(0, STATE.resourceScroll + delta);
            case SETTINGS -> STATE.settingsScroll = Math.max(0, STATE.settingsScroll + delta);
        }
        return true;
    }

    private List<ModelEntry> collectModelEntries() {
        List<ModelEntry> out = new ArrayList<>();
        String query = STATE.modelSearchText.trim().toLowerCase(Locale.ROOT);
        boolean searching = !query.isBlank();
        Set<String> folderPaths = new HashSet<>();
        if (!searching) {
            for (String pack : this.controller.modelPackPaths()) {
                if (isDirectChild(STATE.currentPath, pack)) {
                    String name = pack.substring(STATE.currentPath.length()).replaceAll("/+$", "");
                    if (folderPaths.add(pack)) {
                        out.add(ModelEntry.folder(pack, name));
                    }
                }
            }
            // Synthesize folder nodes for nested models placed under custom/<subfolder>/
            // without ysm-pack.json entries. Otherwise those models are loaded but
            // cannot be reached through the model browser path navigation.
            for (String modelId : this.controller.availableModelIds()) {
                if (!modelId.startsWith(STATE.currentPath)) {
                    continue;
                }
                String rest = modelId.substring(STATE.currentPath.length());
                int slash = rest.indexOf('/');
                if (slash <= 0) {
                    continue;
                }
                String segment = rest.substring(0, slash);
                String folderPath = STATE.currentPath + segment + "/";
                if (folderPaths.add(folderPath)) {
                    out.add(ModelEntry.folder(folderPath, segment));
                }
            }
        }
        Set<String> auth = authModels();
        Set<String> stars = starModels();
        Map<String, ModelAssembly> assemblyMap = this.controller.modelAssemblyMap();
        for (var entry : assemblyMap.entrySet()) {
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
        for (String modelId : this.controller.availableModelIds()) {
            if (assemblyMap.containsKey(modelId)) continue;
            if (!searching && !isDirectModel(STATE.currentPath, modelId)) continue;
            boolean authModel = this.controller.isAuthModel(modelId);
            if (STATE.modelFilter == ModelPanelState.ModelFilter.STAR && !stars.contains(modelId)) continue;
            if (STATE.modelFilter == ModelPanelState.ModelFilter.AUTH && authModel && !auth.contains(modelId)) continue;
            String lazyTitle = lazyModelDisplayName(modelId);
            if (searching) {
                String needle = query.startsWith("#") ? query.substring(1) : query;
                String haystack = (modelId + " " + lazyTitle).toLowerCase(Locale.ROOT);
                if (!haystack.contains(needle)) continue;
            }
            boolean locked = authModel && !auth.contains(modelId);
            out.add(ModelEntry.model(modelId, lazyTitle,
                    Component.translatable("gui.sparkle_morpher.model_panel.model.on_demand").getString(), locked));
        }
        out.sort(Comparator
                .<ModelEntry, Boolean>comparing(e -> !stars.contains(e.modelId()))
                .thenComparing(Comparator.<ModelEntry, Boolean>comparing(entry -> entry.folder()).reversed())
                .thenComparing(e -> e.title().toLowerCase(Locale.ROOT)));
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
        if (entry.locked()) {
            setStatus(Component.translatable("message.sparkle_morpher.model.need_auth"), ChatFormatting.YELLOW);
            return;
        }
        ModelAssembly assembly = this.controller.assemblyOrNull(entry.modelId());
        if (assembly == null) {
            this.pendingModelApplyId = entry.modelId();
            setStatus(Component.translatable("gui.sparkle_morpher.sync_hint.loading"), ChatFormatting.YELLOW);
            return;
        }
        this.pendingModelApplyId = null;
        if (assembly != null) {
            STATE.selectedTextureId = selectedTextureOrDefault(assembly);
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

    /** §24.7：转发到 Service（保留本方法名，行为不变）。 */
    private void applyModelAndTexture(String modelId, String textureId, ModelAssembly assembly) {
        ModernPlayerModelScreenController.ApplyResult result = this.controller.applyModel(modelId, textureId, this.modelSelectionTarget, this.rememberPlayerSelection);
        if (result == ModernPlayerModelScreenController.ApplyResult.APPLIED_TO_TARGET
                || result == ModernPlayerModelScreenController.ApplyResult.APPLIED_TO_PLAYER) {
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.applied_model", modelId), ChatFormatting.GREEN);
        }
    }

    /** §24.7：转发到 Service（保留本方法名，行为不变）。 */
    private void toggleSelectedStar() {
        this.controller.toggleStar(STATE.selectedModelId);
    }

    /** §24.7：转发到 Service（保留本方法名，行为不变）。 */
    private void deleteSelectedModels() {
        Collection<String> models = this.selectedModelIds.isEmpty() && !STATE.selectedModelId.isBlank() ? List.of(STATE.selectedModelId) : new HashSet<>(this.selectedModelIds);
        if (models.isEmpty()) {
            return;
        }
        setStatus(this.controller.deleteModels(models));
        this.selectedModelIds.clear();
        STATE.selectedModelId = "";
        STATE.selectedTextureId = "";
        STATE.multiSelectMode = false;
        this.controller.reloadLocalModels(this::setStatus);
    }

    private void selectAllVisibleModels() {
        for (ModelEntry entry : visibleModelEntries()) {
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
        this.controller.refreshResources(manual);
    }

    private List<ModelRepoEntry> filteredResources() {
        return this.controller.filteredResources();
    }

    private void clickResource(ModelRepoEntry entry) {
        this.controller.clickResource(entry);
    }

    private void toggleResourceMultiSelect() {
        this.controller.toggleResourceMultiSelect();
    }

    private void enqueueResource(ModelRepoEntry entry) {
        if (this.controller.enqueueResource(entry)) {
            setStatus(Component.translatable("gui.sparkle_morpher.resource_station.queued", entry.name()), ChatFormatting.YELLOW);
        }
    }

    private void enqueueSelectedResources() {
        int added = this.controller.enqueueSelectedResources();
        setStatus(Component.translatable("gui.sparkle_morpher.resource_station.queue_added", added), added > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
    }

    private void openSitesPanel() {
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.SITES;
        STATE.siteEditText = this.controller.selectedSite();
        init();
    }

    private void selectSite(String url) {
        this.controller.selectSite(url);
        STATE.siteEditText = url;
        STATE.resourceLoaded = false;
        refreshResources(false);
        init();
    }

    private void saveSite() {
        String url = STATE.siteEditText.trim();
        if (!this.controller.addSite(url)) {
            return;
        }
        STATE.resourceLoaded = false;
        refreshResources(false);
    }

    private void deleteSite() {
        String url = STATE.siteEditText.trim();
        String selected = this.controller.removeSite(url);
        if (selected == null) {
            setStatus(Component.translatable("gui.sparkle_morpher.resource_station.cannot_delete"), ChatFormatting.RED);
            return;
        }
        STATE.siteEditText = selected;
        refreshResources(false);
        init();
    }

    private void toggleResourceMode() {
        this.controller.toggleMainlandChinaMode();
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
        setStatus(this.controller.moveModels(models, category));
        this.selectedModelIds.clear();
        STATE.multiSelectMode = false;
    }

    private void openImportPanel() {
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.IMPORT;
        init();
    }

    private void openFilePicker() {
        Component error = this.controller.pickYsmFile();
        if (error != null) {
            setStatus(error, ChatFormatting.RED);
        }
    }

    private void openModelFolder() {
        this.controller.openModelFolder();
    }

    private void openCustomFolderUpload() {
        Minecraft.getInstance().setScreen(new CustomFolderUploadScreen(this));
    }

    private Component getCustomFolderUploadTooltip() {
        if (this.controller.isAllowUpload() && this.controller.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip");
        }
        if (!this.controller.isOysmServer()) {
            return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip.waiting");
        }
        return Component.translatable("gui.sparkle_morpher.upload_custom_folder.tooltip.disabled");
    }

    /** §24.7：导入推进已外提到 Service，保留本方法供内部/测试调用。 */
    private void pollImports() {
        this.controller.pollImports();
    }

    private void enqueueImportPath(Path path) {
        this.controller.enqueueImportPath(path);
    }

    private void startNextImportIfIdle() {
        this.controller.startNextImportIfIdle();
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
                minecraft.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
            }
        });
    }

        /** Developer-options: serialise this panel's state to JSON and copy it to the clipboard. */
        private void dumpPanelState() {
            String json = stateToJson(this.STATE.devSnapshot());
            Minecraft.getInstance().keyboardHandler.setClipboard(json);
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.developer.action.dump_state.done"), ChatFormatting.GREEN);
        }

        /** Developer-options: drop every memoised panel state, keeping the developer group selected. */
        private void resetPanelState() {
            STATE_CACHE.clear();
            this.STATE.settingGroup = ModelPanelState.SettingGroup.DEVELOPER;
            this.STATE.settingsScroll = 0;
            init();
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.developer.action.reset_state.done"), ChatFormatting.GREEN);
        }

        /** Developer-options (gated): read JSON from the clipboard and apply it all-or-nothing. */
        private void applyStateFromClipboard() {
            String raw = Minecraft.getInstance().keyboardHandler.getClipboard();
            java.util.Map<String, String> parsed = parseStateJson(raw);
            if (parsed == null) {
                setStatus(Component.translatable("gui.sparkle_morpher.model_panel.developer.action.apply_state.invalid"), ChatFormatting.RED);
                return;
            }
            java.util.List<String> errors = new java.util.ArrayList<>();
            if (!this.STATE.applyDevState(parsed, errors)) {
                setStatus(Component.literal("panel state rejected: " + String.join(", ", errors)), ChatFormatting.RED);
                return;
            }
            init();
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.developer.action.apply_state.done"), ChatFormatting.GREEN);
        }

        /** Minimal flat JSON writer for the map produced by devSnapshot(). */
        private static String stateToJson(java.util.Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (java.util.Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"').append(e.getKey()).append("\":");
                Object v = e.getValue();
                if (v instanceof Boolean || v instanceof Number) {
                    sb.append(v);
                } else {
                    sb.append('"').append(escapeJson(String.valueOf(v))).append('"');
                }
            }
            return sb.append('}').toString();
        }

        private static String escapeJson(String s) {
            StringBuilder sb = new StringBuilder(s.length() + 8);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
            return sb.toString();
        }

        /** Parse the flat JSON written by stateToJson; null when malformed. */
        private static java.util.Map<String, String> parseStateJson(String raw) {
            if (raw == null) {
                return null;
            }
            String s = raw.trim();
            if (!s.startsWith("{") || !s.endsWith("}")) {
                return null;
            }
            s = s.substring(1, s.length() - 1).trim();
            java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
            if (s.isEmpty()) {
                return out;
            }
            int i = 0;
            while (i < s.length()) {
                i = skipWs(s, i);
                if (i >= s.length() || s.charAt(i) != '"') {
                    return null;
                }
                int[] keyEnd = {-1};
                String key = readJsonString(s, i, keyEnd);
                if (key == null) {
                    return null;
                }
                i = skipWs(s, keyEnd[0]);
                if (i >= s.length() || s.charAt(i) != ':') {
                    return null;
                }
                i = skipWs(s, i + 1);
                String value;
                if (i < s.length() && s.charAt(i) == '"') {
                    int[] valEnd = {-1};
                    value = readJsonString(s, i, valEnd);
                    if (value == null) {
                        return null;
                    }
                    i = valEnd[0];
                } else {
                    int j = i;
                    while (j < s.length() && s.charAt(j) != ',') {
                        j++;
                    }
                    value = s.substring(i, j).trim();
                    i = j;
                }
                out.put(key, value);
                i = skipWs(s, i);
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length()) {
                    return null;
                }
            }
            return out;
        }

        private static int skipWs(String s, int i) {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            return i;
        }

        /** Reads a JSON string literal at the opening quote; writes the index past it. */
        private static String readJsonString(String s, int start, int[] endOut) {
            StringBuilder sb = new StringBuilder();
            int i = start + 1;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '"') {
                    endOut[0] = i + 1;
                    return sb.toString();
                }
                if (c == '\\') {
                    if (i + 1 >= s.length()) {
                        return null;
                    }
                    char n = s.charAt(i + 1);
                    switch (n) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'u' -> {
                            if (i + 5 >= s.length()) {
                                return null;
                            }
                            try {
                                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                            } catch (NumberFormatException ex) {
                                return null;
                            }
                            i += 4;
                        }
                        default -> {
                            return null;
                        }
                    }
                    i += 2;
                    continue;
                }
                sb.append(c);
                i++;
            }
            return null;
        }

    private List<SettingRow> settingsRows() {
        List<SettingRow> rows = new ArrayList<>();
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.sound_roulette_message", GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG));
        rows.add(doubleRow(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.sound_volume", GeneralConfig.SOUND_VOLUME, 0, 100, 5, "%"));
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.disable_self_model", GeneralConfig.DISABLE_SELF_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.disable_other_model", GeneralConfig.DISABLE_OTHER_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.model_panel.setting.disable_self_hands", GeneralConfig.DISABLE_SELF_HANDS));
        rows.add(privacyModeRow(ModelPanelState.SettingGroup.GENERAL));
        rows.add(invertedBool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.classic_hud_rendering", ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER));
        rows.add(actionRow(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.classic_hud_layout", () -> InputUtil.setScreen(new HudLayoutScreen(this,
                Component.translatable("gui.sparkle_morpher.classic_hud_layout.title"),
                ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT, HudLayoutScreen.classicPreview()))));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.modern_hud_rendering", ExtraPlayerRenderConfig.ENABLE_MODERN_HUD_RENDER));
        rows.add(actionRow(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.modern_hud_layout", () -> InputUtil.setScreen(new HudLayoutScreen(this,
                Component.translatable("gui.sparkle_morpher.modern_hud_layout.title"),
                ExtraPlayerRenderConfig.MODERN_HUD_LAYOUT, HudLayoutScreen.modernPreview()))));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_external_fp_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.shader_glow_compatibility", GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_face_culling", GeneralConfig.DISABLE_MODEL_FACE_CULLING));
        rows.add(rendererModeRow(ModelPanelState.SettingGroup.PERFORMANCE));
        rows.add(nativeSimdPolicyRow(ModelPanelState.SettingGroup.PERFORMANCE));
        rows.add(javaVectorRendererRow(ModelPanelState.SettingGroup.PERFORMANCE));
        rows.add(bool(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.lazy_model_loading", GeneralConfig.LAZY_MODEL_LOADING));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.gpu_cache_limit", GeneralConfig.MAX_CACHED_GPU_MODELS, 0, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.cpu_cache_limit", GeneralConfig.MAX_RESIDENT_CPU_MODELS, 1, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.unused_model_ttl", GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 30, 86400, 30, "s"));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.enable_global_bandwidth_limit", ServerConfig.ENABLE_GLOBAL_BANDWIDTH_LIMIT));
        rows.add(intRow(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.bandwidth_limit", ServerConfig.BANDWIDTH_LIMIT, 1, 999, 10, "Mbps"));
        // ---- Developer options: panel state ----
        rows.add(section(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.section.panel_state"));
        rows.add(actionRow(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.action.dump_state", this::dumpPanelState));
        rows.add(actionRow(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.action.reset_state", this::resetPanelState));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.developer_state_writeback", GeneralConfig.DEVELOPER_STATE_WRITEBACK));
        if (ConfigPolicies.developerStateWriteback()) {
            rows.add(actionRow(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.action.apply_state", this::applyStateFromClipboard));
        }
        // ---- Developer options: logging ----
        rows.add(section(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.section.logging"));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.resource_monitor_log", GeneralConfig.RESOURCE_STATION_MONITOR_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.animation_debug_log", GeneralConfig.ANIMATION_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.animation_roulette_debug_log", GeneralConfig.ANIMATION_ROULETTE_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.input_debug_log", GeneralConfig.INPUT_STATE_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.config.network_online_debug_log", GeneralConfig.NETWORK_ONLINE_DEBUG_LOG));
        // ---- Developer options: profiling ----
        rows.add(section(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.section.profiling"));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.model_memory_profiler", GeneralConfig.MODEL_MEMORY_PROFILER));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.import_performance_log", GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.animation_frame_profiler", GeneralConfig.ANIMATION_FRAME_PROFILER));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.config.warn_repeated_animation_evaluation", GeneralConfig.WARN_REPEATED_ANIMATION_EVALUATION));
        // ---- Developer options: renderer diagnostics ----
        rows.add(section(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.developer.section.rendering"));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.native_simd_compatibility_log", GeneralConfig.NATIVE_SIMD_COMPATIBILITY_LOG));
        rows.add(nativeSimdValidationRow(ModelPanelState.SettingGroup.DEVELOPER));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.gpu_debug_log", GeneralConfig.GPU_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.gpu_debug_verbose_log", GeneralConfig.GPU_DEBUG_VERBOSE_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.show_model_id_first", GeneralConfig.SHOW_MODEL_ID_FIRST));
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_disabled", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN));
        rows.add(loadingPositionRow(ModelPanelState.SettingGroup.MISC));
        rows.add(intRow(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_offset_x", LoadingStateConfig.LOADING_STATE_OFFSET_X, -10000, 10000, 10, "px"));
        rows.add(intRow(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_offset_y", LoadingStateConfig.LOADING_STATE_OFFSET_Y, -10000, 10000, 10, "px"));
        rows.add(intRow(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_auto_hide", LoadingStateConfig.LOADING_STATE_AUTO_HIDE_SECONDS, 1, 30, 1, "s"));
        return rows.stream().filter(row -> row.group() == STATE.settingGroup).toList();
    }

    private SettingRow bool(ModelPanelState.SettingGroup group, String labelKey, ModConfigSpec.BooleanValue value) {
        boolean current = safeBool(value);
        return new SettingRow(group, labelKey, current, "", () -> {
            value.set(!safeBool(value));
            value.save();
        }, null, null, null, null);
    }

    private SettingRow privacyModeRow(ModelPanelState.SettingGroup group) {
        boolean current = this.controller.privacyModeConfigured();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.privacy_mode", current, "", this.controller::togglePrivacyMode, null, null, null, null);
    }

    private SettingRow invertedBool(ModelPanelState.SettingGroup group, String labelKey, ModConfigSpec.BooleanValue value) {
        boolean current = !safeBool(value);
        return new SettingRow(group, labelKey, current, "", () -> {
            value.set(!safeBool(value));
            value.save();
        }, null, null, null, null);
    }

    private SettingRow actionRow(ModelPanelState.SettingGroup group, String labelKey, Runnable action) {
        return new SettingRow(group, labelKey, null,
                Component.translatable("gui.sparkle_morpher.model_panel.setting.configure").getString(),
                action, null, null, null, null);
    }

    private SettingRow section(ModelPanelState.SettingGroup group, String sectionKey) {
        return new SettingRow(group, sectionKey, null, "", null, null, null, null, sectionKey);
    }

    private SettingRow javaVectorRendererRow(ModelPanelState.SettingGroup group) {
        boolean current = this.controller.javaVectorRendererEnabled();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.java_vector_renderer", current, "", this.controller::toggleJavaVectorRenderer, null, null, null, null);
    }

    private SettingRow nativeSimdPolicyRow(ModelPanelState.SettingGroup group) {
        GeneralConfig.NativeSimdPolicy current = this.controller.nativeSimdPolicy();
        String valueText = Component.translatable("gui.sparkle_morpher.model_panel.setting.native_simd_policy.value." + current.name().toLowerCase(Locale.ROOT)).getString();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.native_simd_policy", null, valueText, this.controller::cycleNativeSimdPolicy, null, null, null, null);
    }

    private SettingRow nativeSimdValidationRow(ModelPanelState.SettingGroup group) {
        GeneralConfig.NativeSimdValidationMode current = this.controller.nativeSimdValidationMode();
        String valueText = Component.translatable("gui.sparkle_morpher.model_panel.setting.native_simd_validation.value." + current.name().toLowerCase(Locale.ROOT)).getString();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.native_simd_validation", null, valueText, this.controller::cycleNativeSimdValidationMode, null, null, null, null);
    }

    private SettingRow loadingPositionRow(ModelPanelState.SettingGroup group) {
        LoadingStateConfig.Position selected = this.controller.loadingPosition();
        String valueText = Component.translatable("gui.sparkle_morpher.config.loading_state_position.value." + selected.name().toLowerCase(Locale.ROOT)).getString();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.loading_state_position", null, valueText, null,
                () -> this.controller.stepLoadingPosition(false),
                () -> this.controller.stepLoadingPosition(true), null, null);
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
                }, null, null);
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
                }, null, null);
    }

    private SettingRow rendererModeRow(ModelPanelState.SettingGroup group) {
        boolean gpuSelected = this.controller.gpuRendererSelected();
        return new SettingRow(group, "gui.sparkle_morpher.config.renderer", null, "", null, null, null,
                new SegmentedSetting(
                        Component.translatable("gui.sparkle_morpher.config.renderer.gpu"),
                        Component.translatable("gui.sparkle_morpher.config.renderer.compatibility"),
                        gpuSelected,
                        () -> this.controller.setRendererMode(true),
                        () -> this.controller.setRendererMode(false)
                ), null);
    }

    private void setRendererMode(boolean useGpuRenderer) {
        this.controller.setRendererMode(useGpuRenderer);
    }

    private ModelAssembly selectedAssembly() {
        if (STATE.selectedModelId == null || STATE.selectedModelId.isBlank()) {
            return null;
        }
        return this.controller.assemblyOrNull(STATE.selectedModelId);
    }

    private ModelRepoEntry selectedResource() {
        return this.controller.selectedResource();
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
        return this.controller.mainlandChinaMode()
                ? Component.translatable("gui.sparkle_morpher.resource_station.mode.mainland")
                : Component.translatable("gui.sparkle_morpher.resource_station.mode.native");
    }

    private boolean isMainlandResourceMode() {
        return this.controller.mainlandChinaMode();
    }

    private String displayName(String modelId, ModelAssembly assembly) {
        try {
            String name = assembly.getDisplayName(modelId);
            return StringUtils.isBlank(name) ? modelId : name;
        } catch (Exception ignored) {
            return modelId;
        }
    }

    /** Title for models that are catalogued but not yet fully loaded into {@code modelAssemblyMap}. */
    private String lazyModelDisplayName(String modelId) {
        String sniffed = this.controller.lazyModelDisplayName(modelId);
        return StringUtils.isBlank(sniffed) ? modelId : sniffed;
    }

    private String modelSubtitle(String modelId, ModelAssembly assembly) {
        List<String> parts = new ArrayList<>();
        if (this.controller.isLocalOnlyModel(modelId)) {
            parts.add("local");
        }
        if (assembly.getTextureRegistry().isAuthModel()) {
            parts.add("auth");
        }
        if (assembly.isRuntimeResident()) {
            parts.add(assembly.getAnimationBundle().getTextures().size() + " tex");
        }
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
            parts.add(this.controller.formatBytes(entry.size()));
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
        return ModernPlayerModelScreenController.stripImportExtension(fileName);
    }

    private static String rootMessage(Throwable throwable) {
        return ModernPlayerModelScreenController.rootMessage(throwable);
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

    private int compactDetailStripH() {
        if (!compactModelLayout()) {
            return 0;
        }
        return STATE.compactPreviewExpanded ? 112 : 18;
    }

    private void renderCompactDetail(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        int barH = 18;
        fill(g, x, y, w, h, GLASS_DARK);
        border(g, x, y, w, h, 0x33FFFFFF);
        boolean expanded = STATE.compactPreviewExpanded;
        renderIconButton(g, mouseX, mouseY, x + 1, y, expanded ? IconGlyph.MINUS : IconGlyph.PLUS,
                Component.translatable("gui.sparkle_morpher.model_panel.details"), () -> STATE.compactPreviewExpanded = !STATE.compactPreviewExpanded);
        ModelAssembly assembly = selectedAssembly();
        String modelId = STATE.selectedModelId;
        if (assembly == null) {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.select_model"), x + 22, y + 5);
            return;
        }
        List<String> textures = new ArrayList<>(assembly.getAnimationBundle().getTextures().keySet());
        String selectedTexture = selectedTextureOrDefault(assembly);
        int quickButtonW = 52;
        int quickCount = Math.min(textures.size(), Math.max(0, Math.min(3, (w - 150) / (quickButtonW + 2))));
        int quickStartIndex = 0;
        int selectedTextureIndex = textures.indexOf(selectedTexture);
        if (quickCount > 0 && selectedTextureIndex >= quickCount) {
            quickStartIndex = Math.min(selectedTextureIndex, textures.size() - quickCount);
        }
        int quickStartX = x + w - 22 - quickCount * (quickButtonW + 2);
        drawText(g, Component.literal(trim(displayName(modelId, assembly), Math.max(24, quickStartX - x - 26))), x + 22, y + 5);
        for (int i = 0; i < quickCount; i++) {
            String texture = textures.get(quickStartIndex + i);
            int buttonX = quickStartX + i * (quickButtonW + 2);
            renderRowButton(g, mouseX, mouseY, buttonX, y + 2, quickButtonW, 14,
                    Component.literal(trim(texture, quickButtonW - 10)), texture.equals(selectedTexture), () -> {
                        STATE.selectedTextureId = texture;
                        applySelectedTexture();
                    });
        }
        renderIconButton(g, mouseX, mouseY, x + w - 19, y, IconGlyph.INFO,
                Component.translatable("gui.sparkle_morpher.model_panel.info"), () -> STATE.compactPreviewExpanded = true);
        if (!expanded) {
            return;
        }
        int py = y + barH + 2;
        int ph = h - barH - 4;
        int previewW = Math.min(w - 8, Math.max(56, ph));
        renderSelectedModelPreview(g, assembly, modelId, x + 3, py, previewW, ph, mouseX, mouseY, partialTick);
        int ix = x + previewW + 8;
        int iw = x + w - 4 - ix;
        if (iw > 40) {
            int iy = py + 2;
            drawMuted(g, Component.literal(trim(modelId, iw)), ix, iy);
            iy += 12;
            drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.textures"), ix, iy);
            iy += 11;
            int visibleTextures = Math.min(textures.size(), Math.max(1, Math.min(4, (py + ph - iy) / 16)));
            int textureStart = 0;
            if (visibleTextures > 0 && selectedTextureIndex >= visibleTextures) {
                textureStart = Math.min(selectedTextureIndex, textures.size() - visibleTextures);
            }
            for (int i = 0; i < visibleTextures; i++) {
                String texture = textures.get(textureStart + i);
                renderRowButton(g, mouseX, mouseY, ix, iy, iw, 14, Component.literal(trim(texture, iw - 10)),
                        texture.equals(selectedTexture), () -> {
                            STATE.selectedTextureId = texture;
                            applySelectedTexture();
                        });
                iy += 16;
            }
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, int total, int visible, int scroll) {
        fill(g, x, y, w, h, 0x50101418);
        int maxScroll = Math.max(1, total - visible);
        int thumbH = Math.max(14, (int) ((long) h * visible / total));
        int span = Math.max(1, h - thumbH);
        int thumbY = y + (int) ((long) span * clamp(scroll, 0, maxScroll) / maxScroll);
        boolean hover = this.draggingResourceScroll || inside(mouseX, mouseY, x - 2, thumbY, w + 4, thumbH);
        fill(g, x, thumbY, w, thumbH, hover ? 0xFFB8C6D0 : 0xC093A2AC);
        border(g, x, thumbY, w, thumbH, 0x66FFFFFF);
    }

    private int resourceListContentY() {
        return this.layout.contentTop + 8 + 26;
    }

    private int resourceListContentH() {
        return (this.layout.footerTop - 6) - (this.layout.contentTop + 8) - 26;
    }

    private int[] resourceScrollbarTrack() {
        List<ModelRepoEntry> entries = filteredResources();
        int h = resourceListContentH();
        int rows = Math.max(1, h / ROW);
        if (entries.size() <= rows) {
            return null;
        }
        int x = resourceListX() + resourceListW() - 7;
        return new int[]{x, resourceListContentY() + 3, 4, h - 6};
    }

    private boolean beginResourceScrollDrag(double mouseX, double mouseY) {
        if (STATE.activeTab != ModelPanelState.Tab.RESOURCE || STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE) {
            return false;
        }
        int[] track = resourceScrollbarTrack();
        if (track == null) {
            return false;
        }
        if (inside(mouseX, mouseY, track[0] - 3, track[1], track[2] + 6, track[3])) {
            this.draggingResourceScroll = true;
            updateResourceScrollFromMouse(mouseY);
            return true;
        }
        return false;
    }

    private void updateResourceScrollFromMouse(double mouseY) {
        List<ModelRepoEntry> entries = filteredResources();
        int h = resourceListContentH();
        int rows = Math.max(1, h / ROW);
        int maxScroll = Math.max(0, entries.size() - rows);
        if (maxScroll <= 0) {
            STATE.resourceScroll = 0;
            return;
        }
        int trackY = resourceListContentY() + 3;
        int trackH = h - 6;
        int thumbH = Math.max(14, (int) ((long) trackH * rows / entries.size()));
        int span = Math.max(1, trackH - thumbH);
        double rel = (mouseY - trackY - thumbH / 2.0) / span;
        STATE.resourceScroll = clamp((int) Math.round(rel * maxScroll), 0, maxScroll);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingResourceScroll) {
            updateResourceScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingResourceScroll && event.button() == 0) {
            this.draggingResourceScroll = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private record Hit(int x, int y, int w, int h, Component tooltip, Runnable action) {
    }

    private record ModelListMetrics(int cellW, int cellH, int cols, int rows, int maxScroll, boolean dense) {
    }

    private record ModelEntry(String modelId, String title, String subtitle, boolean folder, boolean locked) {
        static ModelEntry folder(String path, String title) {
            return new ModelEntry(path, title, "folder", true, false);
        }

        static ModelEntry model(String modelId, String title, String subtitle, boolean locked) {
            return new ModelEntry(modelId, title, subtitle, false, locked);
        }
    }

    private record SettingRow(ModelPanelState.SettingGroup group, String labelKey, Boolean booleanValue, String valueText, Runnable action, Runnable decrement, Runnable increment, SegmentedSetting segmented, String sectionKey) {
    }

    private record SegmentedSetting(Component left, Component right, boolean leftSelected, Runnable leftAction, Runnable rightAction) {
    }
}
