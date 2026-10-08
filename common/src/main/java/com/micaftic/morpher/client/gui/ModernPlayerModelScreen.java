package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.storage.ModelStoragePaths;

import com.micaftic.morpher.capability.PlayerCapability;

import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.util.SmExecutors;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.cloud.client.CloudAssetSummary;
import com.micaftic.morpher.cloud.client.CloudAssetPage;
import com.micaftic.morpher.cloud.client.CloudInstanceRegistry;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.core.gpu.BlurStack;

import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.util.LocalStarModelsStore;
import com.micaftic.morpher.util.ModelIdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
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
    private static final ResourceLocation MODEL_PANEL_ICONS = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "texture/model_panel_icons.png");
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
    private EditBox cloudSearchBox;
    private EditBox resourceSearchBox;
    private EditBox siteEditBox;
    private EditBox categoryEditBox;
    private EditBox cloudAccountBox;
    private EditBox cloudPasswordBox;
    private Component cloudPanelStatus = Component.empty();
    private Screen afterCloudLogin;
    private boolean registerCloudAccount;
    private boolean cloudFormBusy;
    private long cloudFormGeneration;
    private String cloudAccountDraft = "";
    private String cloudAddressDraft = "";
    private String cloudNameDraft = "";
    private enum CloudAccountPage { ACCOUNT, INSTANCES, SCOPES, IDENTITY, CLAIM, APPROVAL }
    private CloudAccountPage cloudAccountPage = CloudAccountPage.ACCOUNT;
    private int cloudAccountScroll;
    private int cloudDetailScroll;
    private com.micaftic.morpher.cloud.client.CloudIdentityWorkflow cloudIdentityFlow;
    private CloudIdentityPanelGateway cloudIdentityGateway;
    private EditBox cloudIdentityCodeBox;
    private int cloudIdentityChoicePage;
    private EditBox cloudInstanceNameBox;
    private EditBox cloudOriginBox;
    private EditBox cloudScopeIdBox;
    private EditBox cloudScopeNameBox;
    private EditBox cloudWorldEpochBox;
    private Component status = Component.empty();
    private ChatFormatting statusColor = ChatFormatting.GRAY;
    private boolean resourceStatusMessage;
    private boolean draggingResourceScroll;
    private String previewModelId = "";
    private String previewTextureId = "";
    private String pendingModelApplyId;
    private CloudAssetSummary pendingCloudAsset;
    private long cloudRequestGeneration;
    private final Set<String> pendingVisibilityChanges = new HashSet<>();
    private final Set<String> pendingCloudImports = new HashSet<>();
    private final Set<String> selectedCloudAssetIds = new LinkedHashSet<>();
    private final Set<String> ownedCloudAssetIds = new HashSet<>();
    private CloudClientRuntime.RuntimeState cloudActionsRuntime;
    private String focusedCloudAssetId = "";
    private String cloudVisibilityFilter = "";
    private boolean cloudMultiSelectMode;
    private boolean cloudVisibilityBatchRunning;
    private int cloudTexturePage;
    private long cloudImportGeneration;
    private CloudAssetSummary cloudSelectionCandidate;
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
        this((BiConsumer<String, String>) null, "self");
    }

    public ModernPlayerModelScreen(BiConsumer<String, String> modelSelectionTarget) {
        this(modelSelectionTarget, "self");
    }

    public ModernPlayerModelScreen(BiConsumer<String, String> modelSelectionTarget, String stateKey) {
        super(Component.translatable("key.sparkle_morpher.player_model.desc"));
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
        this((BiConsumer<String, String>) null, "self");
        STATE.activeTab = tab;
        if (tab == ModelPanelState.Tab.RESOURCE) {
            STATE.resourceLoaded = false;
        }
    }

    public ModernPlayerModelScreen(ModelPanelState.Tab tab, String stateKey) {
        this((BiConsumer<String, String>) null, stateKey);
        STATE.activeTab = tab;
        if (tab == ModelPanelState.Tab.RESOURCE) {
            STATE.resourceLoaded = false;
        }
    }

    public static ModernPlayerModelScreen resourceStation() {
        return new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE, "resource");
    }

    public static ModernPlayerModelScreen settings() {
        return new ModernPlayerModelScreen(ModelPanelState.Tab.SETTINGS, "self");
    }

    public static ModernPlayerModelScreen downloads() {
        ModernPlayerModelScreen screen = new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE, "resource");
        screen.STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
        return screen;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT && (cloudAccountPage == CloudAccountPage.INSTANCES || cloudAccountPage == CloudAccountPage.SCOPES)) cloudAccountPage = CloudAccountPage.ACCOUNT;
        else if (STATE.activeTab == ModelPanelState.Tab.INSTANCE && cloudAccountPage == CloudAccountPage.ACCOUNT) cloudAccountPage = CloudAccountPage.INSTANCES;
        this.layout = ModelPanelLayout.create(this.width, this.height);
        this.modelSearchBox = null;
        this.cloudSearchBox = null;
        this.resourceSearchBox = null;
        this.siteEditBox = null;
        this.categoryEditBox = null;
        this.cloudAccountBox = null;
        this.cloudPasswordBox = null;
        this.cloudInstanceNameBox = null;
        this.cloudOriginBox = null;
        this.cloudScopeIdBox = null;
        this.cloudScopeNameBox = null;
        this.cloudWorldEpochBox = null;
        this.cloudIdentityCodeBox = null;
        if (isCloudIdentityPage()) {
            ensureCloudIdentityFlow();
            if (cloudAccountPage == CloudAccountPage.CLAIM && cloudIdentityFlow != null) {
                var identityLayout = cloudDetailLayout();
                cloudIdentityCodeBox = cloudField(identityLayout.fieldX(), identityLayout.detailY() + 246,
                        identityLayout.fieldWidth(), "identity_panel.code_hint", 128);
                cloudIdentityCodeBox.setValue(cloudIdentityFlow.codeDraft());
                cloudIdentityCodeBox.setResponder(value -> { if (cloudIdentityFlow != null) cloudIdentityFlow.codeDraft(value); });
                cloudIdentityCodeBox.visible = false;
            }
        }
        boolean cloudFormOpen = STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE;
        if (!cloudFormOpen && STATE.activeTab == ModelPanelState.Tab.MODEL) {
            if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
                this.modelSearchBox = new EditBox(this.font, modelListX(), this.layout.contentTop + 30, modelListW(), 16, Component.translatable("gui.sparkle_morpher.resource_station.search"));
                this.modelSearchBox.setMaxLength(256);
                this.modelSearchBox.setValue(STATE.modelSearchText);
                this.modelSearchBox.setTextColor(TEXT);
                addWidget(this.modelSearchBox);
            } else {
                this.cloudSearchBox = new EditBox(this.font, modelListX(), this.layout.contentTop + 30, modelListW(), 16, Component.translatable("gui.sparkle_morpher.model_source.search"));
                this.cloudSearchBox.setMaxLength(128);
                this.cloudSearchBox.setValue(STATE.cloudSearchText);
                this.cloudSearchBox.setTextColor(TEXT);
                addWidget(this.cloudSearchBox);
                ensureCloudPageLoaded();
            }
        } else if (!cloudFormOpen && STATE.activeTab == ModelPanelState.Tab.RESOURCE) {
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
        } else if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN) {
            int x = secondaryPanelX() + 16;
            int w = secondaryPanelW() - 32;
            this.cloudAccountBox = cloudField(x, cloudFormFieldY(0), w, "account", 128);
            this.cloudAccountBox.setValue(cloudAccountDraft);
            this.cloudAccountBox.setResponder(value -> cloudAccountDraft = value);
            this.cloudPasswordBox = cloudField(x, cloudFormFieldY(1), w, "password", 1024);
            CloudManagementScreen.maskPassword(this.cloudPasswordBox);
            setInitialFocus(this.cloudAccountBox);
        } else if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) {
            int x = secondaryPanelX() + 16;
            int w = secondaryPanelW() - 32;
            this.cloudOriginBox = cloudField(x, cloudFormFieldY(0), w, "address", 2048);
            this.cloudOriginBox.setValue(cloudAddressDraft);
            this.cloudOriginBox.setResponder(value -> cloudAddressDraft = value);
            this.cloudInstanceNameBox = cloudField(x, cloudFormFieldY(1), w, "optional_name", 128);
            this.cloudInstanceNameBox.setValue(cloudNameDraft);
            this.cloudInstanceNameBox.setResponder(value -> cloudNameDraft = value);
            setInitialFocus(this.cloudOriginBox);
        } else if (STATE.activeTab == ModelPanelState.Tab.INSTANCE && cloudAccountPage == CloudAccountPage.SCOPES) {
            AccountPanelLayout accountLayout = cloudDetailLayout();
            int fieldX = accountLayout.fieldX();
            int fieldW = accountLayout.fieldWidth();
            var selected = CloudManagementScreen.management().snapshot().selectedScope();
            this.cloudScopeIdBox = cloudField(fieldX, accountLayout.fieldY(0), fieldW, "scope_id", 128);
            this.cloudScopeNameBox = cloudField(fieldX, accountLayout.fieldY(1), fieldW, "scope_name", 128);
            this.cloudWorldEpochBox = cloudField(fieldX, accountLayout.fieldY(2), fieldW, "world_epoch", 128);
            if (selected != null) {
                cloudScopeIdBox.setValue(selected.scopeId());
                cloudScopeNameBox.setValue(selected.name());
                cloudWorldEpochBox.setValue(selected.worldEpoch());
            }
        }
    }

    private EditBox cloudField(int x, int y, int width, String key, int maxLength) {
        EditBox field = new EditBox(this.font, x, y, width, 18,
                Component.translatable("gui.sparkle_morpher.cloud.manage." + key));
        field.setMaxLength(maxLength);
        field.setHint(Component.translatable("gui.sparkle_morpher.cloud.manage." + key));
        field.setTextColor(TEXT);
        addWidget(field);
        return field;
    }

    private AccountPanelLayout accountPanelLayout() {
        return AccountPanelLayout.of(this.layout.contentLeft, this.layout.contentTop,
                this.layout.contentWidth, this.layout.contentHeight);
    }

    @Override
    public void removed() {
        if (cloudIdentityFlow != null) cloudIdentityFlow.invalidate();
        cloudIdentityFlow = null; cloudIdentityGateway = null;
        this.controller.invalidateGeneration();
            this.cloudRequestGeneration++;
            STATE.cloudLoading = false;
            cloudActionsRuntime = null;
            pendingCloudImports.clear(); pendingVisibilityChanges.clear();
            cloudVisibilityBatchRunning = false;
            if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) this.pendingModelApplyId = null;
        closeCloudForm();
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
    public void tick() {
        super.tick();
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) ensureCloudPageLoaded();
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
        if (this.cloudSearchBox != null && !Objects.equals(STATE.cloudSearchText, this.cloudSearchBox.getValue())) {
            STATE.cloudSearchText = this.cloudSearchBox.getValue();
            resetCloudPage();
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.hits.clear();
        boolean modal = STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE;
        int mainMouseX = modal ? -1 : mouseX;
        int mainMouseY = modal ? -1 : mouseY;
        renderTransparentBackground(g);
        fill(g, this.layout.left, this.layout.top, this.layout.width, this.layout.height, BG);
        border(g, this.layout.left, this.layout.top, this.layout.width, this.layout.height, 0x44FFFFFF);
        renderTabs(g, mainMouseX, mainMouseY);
        blurGlass(g, this.layout.contentLeft, this.layout.contentTop, this.layout.contentWidth, this.layout.contentHeight, 0x1CFFFFFF, 6.0f);
        fill(g, this.layout.contentLeft, this.layout.contentTop, this.layout.contentWidth, this.layout.contentHeight, 0x18171A1D);
        switch (STATE.activeTab) {
            case MODEL -> renderModelTab(g, mainMouseX, mainMouseY, partialTick);
            case RESOURCE -> renderResourceTab(g, mainMouseX, mainMouseY, partialTick);
            case ACCOUNT, INSTANCE -> renderCloudAccountPanel(g, mainMouseX, mainMouseY, this.layout.contentLeft, this.layout.contentTop, this.layout.contentWidth, this.layout.contentHeight, partialTick);
            case SETTINGS -> renderSettingsTab(g, mainMouseX, mainMouseY);
        }
        renderFooter(g);
        renderSecondaryPanel(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        if (this.layout.verticalTabs) {
            int railX = this.layout.left + 3;
            int railW = this.layout.railWidth - 5;
            int th = 26;
            int y = this.layout.top + 6;
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.MODEL, railX, y, railW, th, IconGlyph.MODEL, Component.translatable("gui.sparkle_morpher.model_panel.model"));
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.RESOURCE, railX, y + th + 4, railW, th, IconGlyph.RESOURCE, Component.translatable("gui.sparkle_morpher.resource_station.title"));
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.ACCOUNT, railX, y + (th + 4) * 2, railW, th, IconGlyph.LOCK, Component.translatable("gui.sparkle_morpher.cloud.manage.account_management"));
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.INSTANCE, railX, y + (th + 4) * 3, railW, th, IconGlyph.RESOURCE, cloudText("instance_management"));
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.SETTINGS, railX, y + (th + 4) * 4, railW, th, IconGlyph.SETTINGS, Component.translatable("gui.sparkle_morpher.model_panel.settings"));
            return;
        }
        int tabWidth = this.layout.width / 5;
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.MODEL, this.layout.left, tabWidth, IconGlyph.MODEL, Component.translatable("gui.sparkle_morpher.model_panel.model"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.RESOURCE, this.layout.left + tabWidth, tabWidth, IconGlyph.RESOURCE, Component.translatable("gui.sparkle_morpher.resource_station.title"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.ACCOUNT, this.layout.left + tabWidth * 2, tabWidth, IconGlyph.LOCK, Component.translatable("gui.sparkle_morpher.cloud.manage.account_management"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.INSTANCE, this.layout.left + tabWidth * 3, tabWidth, IconGlyph.RESOURCE, cloudText("instance_management"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.SETTINGS, this.layout.left + tabWidth * 4, this.layout.width - tabWidth * 4, IconGlyph.SETTINGS, Component.translatable("gui.sparkle_morpher.model_panel.settings"));
    }

    private void renderTab(GuiGraphics g, int mouseX, int mouseY, ModelPanelState.Tab tab, int x, int w, IconGlyph icon, Component label) {
        boolean selected = STATE.activeTab == tab;
        boolean hover = inside(mouseX, mouseY, x, this.layout.top, w, this.layout.tabHeight);
        fill(g, x, this.layout.top, w, this.layout.tabHeight, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x40303030);
        int total = 16 + 6 + this.font.width(label);
        int tx = x + (w - total) / 2;
        int ty = this.layout.top + (this.layout.tabHeight - 16) / 2;
        drawIcon(g, icon, tx, ty);
        g.drawString(this.font, label, tx + 22, this.layout.top + (this.layout.tabHeight - this.font.lineHeight) / 2 + 1, selected ? 0xFFFFFFFF : MUTED, false);
        hit(x, this.layout.top, w, this.layout.tabHeight, label, () -> switchTab(tab));
    }

    private void renderVerticalTab(GuiGraphics g, int mouseX, int mouseY, ModelPanelState.Tab tab, int x, int y, int w, int h, IconGlyph icon, Component label) {
        boolean selected = STATE.activeTab == tab;
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        fill(g, x, y, w, h, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x40303030);
        border(g, x, y, w, h, selected ? RED : 0x33FFFFFF);
        drawIcon(g, icon, x + (w - 16) / 2, y + (h - 16) / 2);
        hit(x, y, w, h, label, () -> switchTab(tab));
    }

    private void switchTab(ModelPanelState.Tab tab) {
        if (STATE.activeTab != tab) {
            closeCloudForm();
            STATE.activeTab = tab;
            if (tab == ModelPanelState.Tab.ACCOUNT) cloudAccountPage = CloudAccountPage.ACCOUNT;
            if (tab == ModelPanelState.Tab.INSTANCE) cloudAccountPage = CloudAccountPage.INSTANCES;
            cloudAccountScroll = 0;
            cloudDetailScroll = 0;
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
        if (compactModelLayout() || STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
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
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) return Math.min(420, max);
        return clamp(this.layout.width * 2 / 3, Math.min(520, max), max);
    }

    private int secondaryPanelH() {
        int max = Math.max(96, this.layout.height - 32);
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) return Math.min(236, max);
        return clamp(this.layout.height * 2 / 3, Math.min(300, max), max);
    }

    private int secondaryPanelX() {
        return this.layout.left + (this.layout.width - secondaryPanelW()) / 2;
    }

    private int secondaryPanelY() {
        return this.layout.top + (this.layout.height - secondaryPanelH()) / 2 + 8;
    }

    private void renderModelTab(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean compact = compactModelLayout();
        int x = modelLeftX();
        int y = this.layout.contentTop + 8;
        int leftW = modelLeftW();
        int rightW = modelRightW();
        int listX = modelListX();
        int listW = modelListW();
        int detailX = listX + listW + 10;
        int contentBottom = this.layout.footerTop - 6;

        renderModelSourceTabs(g, mouseX, mouseY, listX, y, listW);

        if (compact && STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            int filtersY = y + 44;
            renderChip(g, listX, filtersY, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.all"), STATE.modelFilter == ModelPanelState.ModelFilter.ALL, () -> setModelFilter(ModelPanelState.ModelFilter.ALL));
            renderChip(g, listX + 42, filtersY, 42, Component.translatable("gui.sparkle_morpher.model_panel.filter.auth"), STATE.modelFilter == ModelPanelState.ModelFilter.AUTH, () -> setModelFilter(ModelPanelState.ModelFilter.AUTH));
            renderChip(g, listX + 88, filtersY, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.star"), STATE.modelFilter == ModelPanelState.ModelFilter.STAR, () -> setModelFilter(ModelPanelState.ModelFilter.STAR));
            renderChip(g, listX + 130, filtersY, 68, Component.translatable("gui.sparkle_morpher.model_panel.filter.server_available"), STATE.modelFilter == ModelPanelState.ModelFilter.SERVER_AVAILABLE, () -> setModelFilter(ModelPanelState.ModelFilter.SERVER_AVAILABLE));
            renderChip(g, listX + 202, filtersY, 56, Component.translatable("gui.sparkle_morpher.model_panel.filter.local_only"), STATE.modelFilter == ModelPanelState.ModelFilter.LOCAL_ONLY, () -> setModelFilter(ModelPanelState.ModelFilter.LOCAL_ONLY));
            int actionsX = listX;
            int actionsY = y + 62;
            renderIconButton(g, mouseX, mouseY, actionsX, actionsY, IconGlyph.IMPORT, Component.translatable("gui.sparkle_morpher.import.tooltip"), () -> openImportPanel());
            renderIconButton(g, mouseX, mouseY, actionsX + 24, actionsY, IconGlyph.FOLDER, Component.translatable("gui.sparkle_morpher.open_model_folder.open"), this::openModelFolder);
            renderIconButton(g, mouseX, mouseY, actionsX + 48, actionsY, IconGlyph.ROULETTE, Component.translatable("key.sparkle_morpher.animation_roulette.desc"), this::openRoulette);
            renderIconButton(g, mouseX, mouseY, actionsX + 72, actionsY, IconGlyph.CATEGORY, Component.translatable("gui.sparkle_morpher.model_select.new_category"), () -> openCategoryPanel(""));
            renderIconButton(g, mouseX, mouseY, actionsX + 96, actionsY, IconGlyph.MULTI, Component.translatable("gui.sparkle_morpher.model_panel.multi_select"), () -> {
                STATE.multiSelectMode = !STATE.multiSelectMode;
                this.selectedModelIds.clear();
            });
        } else if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            glassPanel(g, x, y, leftW, contentBottom - y);
            drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.model"), x + 8, y + 8);
            renderCurrentModelSummary(g, x + 8, y + 26, leftW - 16);
            drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.filters"), x + 8, y + 88);
            renderChip(g, x + 8, y + 104, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.all"), STATE.modelFilter == ModelPanelState.ModelFilter.ALL, () -> setModelFilter(ModelPanelState.ModelFilter.ALL));
            renderChip(g, x + 50, y + 104, 42, Component.translatable("gui.sparkle_morpher.model_panel.filter.auth"), STATE.modelFilter == ModelPanelState.ModelFilter.AUTH, () -> setModelFilter(ModelPanelState.ModelFilter.AUTH));
            renderChip(g, x + 96, y + 104, 38, Component.translatable("gui.sparkle_morpher.model_panel.filter.star"), STATE.modelFilter == ModelPanelState.ModelFilter.STAR, () -> setModelFilter(ModelPanelState.ModelFilter.STAR));
            renderChip(g, x + 8, y + 124, 68, Component.translatable("gui.sparkle_morpher.model_panel.filter.server_available"), STATE.modelFilter == ModelPanelState.ModelFilter.SERVER_AVAILABLE, () -> setModelFilter(ModelPanelState.ModelFilter.SERVER_AVAILABLE));
            renderChip(g, x + 80, y + 124, 56, Component.translatable("gui.sparkle_morpher.model_panel.filter.local_only"), STATE.modelFilter == ModelPanelState.ModelFilter.LOCAL_ONLY, () -> setModelFilter(ModelPanelState.ModelFilter.LOCAL_ONLY));
            drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.actions"), x + 8, y + 160);
            renderIconButton(g, mouseX, mouseY, x + 8, y + 176, IconGlyph.IMPORT, Component.translatable("gui.sparkle_morpher.import.tooltip"), () -> openImportPanel());
            renderIconButton(g, mouseX, mouseY, x + 32, y + 176, IconGlyph.FOLDER, Component.translatable("gui.sparkle_morpher.open_model_folder.open"), this::openModelFolder);
            renderIconButton(g, mouseX, mouseY, x + 56, y + 176, IconGlyph.ROULETTE, Component.translatable("key.sparkle_morpher.animation_roulette.desc"), this::openRoulette);
            renderIconButton(g, mouseX, mouseY, x + 80, y + 176, IconGlyph.CATEGORY, Component.translatable("gui.sparkle_morpher.model_select.new_category"), () -> openCategoryPanel(""));
            renderIconButton(g, mouseX, mouseY, x + 104, y + 176, IconGlyph.MULTI, Component.translatable("gui.sparkle_morpher.model_panel.multi_select"), () -> {
                STATE.multiSelectMode = !STATE.multiSelectMode;
                this.selectedModelIds.clear();
            });
        }

        if (this.modelSearchBox != null) {
            this.modelSearchBox.render(g, mouseX, mouseY, partialTick);
        }
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) renderCloudActions(g, mouseX, mouseY, listX, y + 44, listW);
        if (this.cloudSearchBox != null) {
            this.cloudSearchBox.render(g, mouseX, mouseY, partialTick);
        }
        int pathY = STATE.modelSource == ModelPanelState.ModelSource.LOCAL ? (compact ? y + 88 : y + 52) : y + cloudPathOffset();
        if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            renderPathBar(g, listX, pathY, listW);
        } else {
            renderCloudViewTabs(g, mouseX, mouseY, listX, pathY, listW);
        }
        int gridY = pathY + 20;
        int actionsBandY = contentBottom - 28;
        int detailStripH = compactDetailStripH();
        int reserve = detailStripH > 0 ? detailStripH + 3 : 0;
        int gridH = STATE.modelSource == ModelPanelState.ModelSource.LOCAL
                ? Math.max(compact ? 34 : 50, actionsBandY - 4 - reserve - gridY)
                : cloudModelViewport().gridHeight();
        if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            renderModelGrid(g, mouseX, mouseY, listX, gridY, listW, gridH);
        } else {
            renderCloudGrid(g, mouseX, mouseY, listX, gridY, listW, gridH);
        }
        if (detailStripH > 0) {
            renderCompactDetail(g, mouseX, mouseY, listX, gridY + gridH + 3, listW, detailStripH, partialTick);
        }
        renderModelBottomActions(g, mouseX, mouseY, listX, actionsBandY, listW, gridH);
        if (!compact) {
            if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) renderModelDetails(g, mouseX, mouseY, detailX, y, rightW, contentBottom - y, partialTick);
            else renderCloudDetails(g, mouseX, mouseY, detailX, y, rightW, contentBottom - y, partialTick);
        }
    }

    private void renderModelSourceTabs(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        var profiles = CloudManagementScreen.management().registry().profiles();
        if (STATE.selectedCloudInstanceId.isBlank() && STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
            CloudManagementScreen.management().registry().selected().ifPresent(profile -> {
                STATE.selectedCloudInstanceId = profile.instanceId();
                CloudManagementScreen.management().selectInstance(profile.instanceId());
            });
        }
        int localW = Math.min(74, Math.max(38, w / 4));
        int addW = 24;
        int available = Math.max(1, w - localW - addW);
        boolean overflow = profiles.size() * 92 > available;
        int navW = overflow ? 36 : 0;
        int cloudW = Math.max(1, Math.min(available - navW, 148 * Math.max(1, profiles.size())));
        int visible = overflow ? Math.max(1, cloudW / 92) : Math.max(1, profiles.size());
        STATE.cloudTabOffset = Math.min(STATE.cloudTabOffset, Math.max(0, profiles.size() - visible));
        renderCloudBrowserTab(g, mouseX, mouseY, x, y, localW,
                Component.translatable("gui.sparkle_morpher.model_source.local"),
                STATE.modelSource == ModelPanelState.ModelSource.LOCAL,
                () -> switchModelSource(ModelPanelState.ModelSource.LOCAL));
        int tabW = Math.max(1, cloudW / visible);
        for (int i = 0; i < visible && STATE.cloudTabOffset + i < profiles.size(); i++) {
            var profile = profiles.get(STATE.cloudTabOffset + i);
            boolean connected = CloudClientRuntime.state(profile.instanceId()) != null;
            String name = CloudManagementScreen.displayName(profile);
            String account = CloudManagementScreen.management().accountId(profile.instanceId());
            boolean selected = STATE.modelSource != ModelPanelState.ModelSource.LOCAL
                    && profile.instanceId().equals(STATE.selectedCloudInstanceId);
            String label = (connected ? "● " : "○ ") + name;
            renderCloudBrowserTab(g, mouseX, mouseY, x + localW + i * tabW, y, tabW,
                    Component.literal(label), selected, () -> {
                        switchCloudInstance(profile);
                        if (!connected) openCloudAccount();
                    });
        }
        if (overflow) {
            int navX = x + localW + cloudW;
            renderTextButton(g, mouseX, mouseY, navX, y, 18, 18, Component.literal("‹"),
                    () -> STATE.cloudTabOffset = Math.max(0, STATE.cloudTabOffset - 1));
            renderTextButton(g, mouseX, mouseY, navX + 18, y, 18, 18, Component.literal("›"),
                    () -> STATE.cloudTabOffset = Math.min(Math.max(0, profiles.size() - visible), STATE.cloudTabOffset + 1));
        }
        renderTextButton(g, mouseX, mouseY, x + localW + cloudW + navW, y, addW, 18, Component.literal("+"),
                this::openCloudInstanceForm);
    }

    private void renderCloudBrowserTab(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w,
                                       Component label, boolean selected, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, 18);
        fill(g, x + 1, y + 1, w - 2, 17, selected ? 0xEE344C5C : hover ? 0xCC435B6B : 0xCC27333D);
        fill(g, x + 1, y, w - 2, 1, selected ? RED : 0x99D5E5EF);
        fill(g, x, y + 2, 1, 16, 0x55FFFFFF);
        fill(g, x + w - 1, y + 2, 1, 16, 0x55FFFFFF);
        if (!selected) fill(g, x + 1, y + 17, w - 2, 1, 0x55FFFFFF);
        drawCentered(g, Component.literal(trim(label.getString(), w - 10)), x + w / 2, y + 5,
                selected ? 0xFFFFFFFF : TEXT);
        hit(x, y, w, 18, label, action);
    }

    private void switchCloudInstance(CloudInstanceRegistry.CloudInstanceProfile profile) {
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) STATE.saveCloudTab();
        CloudManagementScreen.management().selectInstance(profile.instanceId());
        CloudManagementScreen.resumeOfficialAccount();
        STATE.selectedCloudInstanceId = profile.instanceId();
        STATE.modelSource = CloudInstanceRegistry.isBuiltinOfficial(profile)
                ? ModelPanelState.ModelSource.SPM_CLOUD : ModelPanelState.ModelSource.COMMUNITY_CLOUD;
        STATE.restoreCloudTab(profile.instanceId());
        cloudVisibilityFilter = "";
        STATE.currentPath = "";
        STATE.modelScroll = 0;
        cloudImportGeneration++; pendingCloudImports.clear();
        cloudSelectionCandidate = null; this.pendingModelApplyId = null;
        this.cloudRequestGeneration++;
        init();
        ensureCloudPageLoaded();
    }

    private void switchModelSource(ModelPanelState.ModelSource source) {
        if (STATE.modelSource == source && source == ModelPanelState.ModelSource.LOCAL) return;
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) STATE.saveCloudTab();
        STATE.modelSource = source;
        STATE.currentPath = "";
        STATE.modelScroll = 0;
        cloudImportGeneration++; pendingCloudImports.clear();
        cloudSelectionCandidate = null; this.pendingModelApplyId = null;
        resetCloudPage();
        init();
    }

    private void resetCloudPage() {
        this.cloudRequestGeneration++;
        selectedCloudAssetIds.clear();
        STATE.cloudEntries.clear();
        STATE.cloudScroll = 0;
        STATE.cloudCursor = "";
        STATE.cloudLoadedKey = "";
        STATE.cloudLoaded = false;
        STATE.cloudLoading = false;
        STATE.cloudHasMore = false;
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) ensureCloudPageLoaded();
    }

    private String cloudPageKey() {
        return STATE.modelSource.name() + "|" + STATE.cloudView.name() + "|"
                + STATE.cloudSearchText.trim().toLowerCase(Locale.ROOT) + "|" + this.controller.cloudInstanceId()
                + "|" + this.controller.cloudAvailable() + "|" + System.identityHashCode(CloudClientRuntime.state(this.controller.cloudInstanceId()));
    }

    private void ensureCloudPageLoaded() {
        if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) return;
        ensureCloudOwnership();
        if (STATE.cloudLoading) return;
        String key = cloudPageKey();
        if (key.equals(STATE.cloudLoadedKey) && STATE.cloudLoaded) return;
        String instanceId = this.controller.cloudInstanceId();
        if (!this.controller.cloudAvailable()) {
            STATE.cloudEntries.clear();
            STATE.cloudLoadedKey = key;
            STATE.cloudLoaded = true;
            setStatus(Component.translatable("gui.sparkle_morpher.cloud.disconnected"), ChatFormatting.YELLOW);
            return;
        }
        if (STATE.modelSource == ModelPanelState.ModelSource.SPM_CLOUD && !"official".equalsIgnoreCase(instanceId)) {
            STATE.cloudEntries.clear();
            STATE.cloudLoadedKey = key;
            STATE.cloudLoaded = true;
            setStatus(Component.translatable("gui.sparkle_morpher.model_source.official_required"), ChatFormatting.YELLOW);
            return;
        }
        if (STATE.cloudView == ModelPanelState.CloudView.RECENT || STATE.cloudView == ModelPanelState.CloudView.FAVORITES) {
            STATE.cloudEntries.clear();
            STATE.cloudEntries.addAll(STATE.cloudView == ModelPanelState.CloudView.RECENT
                    ? this.controller.recentCloudAssets() : this.controller.favoriteCloudAssets());
            STATE.cloudLoadedKey = key;
            STATE.cloudLoaded = true;
            return;
        }
        if (STATE.cloudSearchRequired()) {
            STATE.cloudEntries.clear();
            STATE.cloudLoadedKey = key;
            STATE.cloudLoaded = true;
            return;
        }
        requestCloudPage(false);
    }

    private void requestCloudPage(boolean append) {
        if (STATE.cloudLoading) return;
        String key = cloudPageKey();
        long generation = ++this.cloudRequestGeneration;
        String query = STATE.cloudSearchText.trim();
        String scope = STATE.cloudCatalogScope();
        String cursor = append && !STATE.cloudCursor.isBlank() ? STATE.cloudCursor : null;
        STATE.cloudLoading = true;
        this.controller.listCloudAssets(scope, query, cursor, 40).whenComplete((page, failure) -> Minecraft.getInstance().execute(() -> {
            if (generation != this.cloudRequestGeneration || !key.equals(cloudPageKey())) return;
            STATE.cloudLoading = false;
            STATE.cloudLoaded = true;
            STATE.cloudLoadedKey = key;
            if (failure != null) {
                STATE.cloudEntries.clear();
                STATE.cloudCursor = "";
                STATE.cloudHasMore = false;
                setStatus(Component.translatable("gui.sparkle_morpher.cloud.search_failed", rootMessage(failure)), ChatFormatting.RED);
                return;
            }
            if (!append) STATE.cloudEntries.clear();
            STATE.cloudEntries.addAll(page.entries());
            STATE.cloudCursor = page.nextCursor() == null ? "" : page.nextCursor();
            STATE.cloudHasMore = page.hasMore();
        }));
    }

    private void renderCloudViewTabs(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        List<ModelPanelState.CloudView> views = STATE.cloudViews();
        int tabW = w / views.size();
        for (int i = 0; i < views.size(); i++) {
            var view = views.get(i);
            String label = view == ModelPanelState.CloudView.ALL ? "all_models" : view.name().toLowerCase(Locale.ROOT);
            renderCloudBrowserTab(g, mouseX, mouseY, x + i * tabW, y, i == views.size() - 1 ? w - i * tabW : tabW,
                    Component.translatable("gui.sparkle_morpher.model_source." + label), STATE.cloudView == view, () -> switchCloudView(view));
        }
    }

    private void switchCloudView(ModelPanelState.CloudView view) {
        if (STATE.cloudView == view) return;
        STATE.cloudView = view;
        cloudVisibilityFilter = "";
        resetCloudPage();
    }

    private void renderCurrentModelSummary(GuiGraphics g, int x, int y, int w) {
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

    private void renderPathBar(GuiGraphics g, int x, int y, int w) {
        fill(g, x, y, w, 16, GLASS_DARK);
        String path = STATE.currentPath.isBlank() ? "/" : "/" + STATE.currentPath;
        drawMuted(g, Component.literal(trim(path, w - 54)), x + 5, y + 4);
        renderIconButton(g, -1, -1, x + w - 44, y - 1, IconGlyph.UP, Component.translatable("gui.back"), this::navigateUp);
        renderIconButton(g, -1, -1, x + w - 22, y - 1, IconGlyph.ROOT, Component.translatable("gui.sparkle_morpher.model_panel.root"), () -> {
            STATE.currentPath = "";
            STATE.modelScroll = 0;
        });
    }

    private void renderModelGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
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

    private void renderCloudGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<CloudAssetSummary> visible = cloudVisibleEntries();
        glassPanel(g, x, y, w, h);
        if (!this.controller.cloudAvailable()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.cloud.disconnected"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        if (STATE.cloudLoading && visible.isEmpty()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.cloud.loading"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        if (STATE.cloudSearchRequired()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.model_source.public_search_required"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        if (visible.isEmpty()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.cloud.no_models"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        CloudModelPage page = cloudGridPage(w, h);
        g.enableScissor(x, y, x + w, y + h);
        if (gridMode(w, h) == ModelPickerLayout.GridMode.CARDS) {
            ModelPickerLayout.Cards cards = ModelPickerLayout.cards(w, h);
            ensureCardPool(cards.capacity());
            for (int i = page.start(); i < page.end(); i++) {
                int slot = i - page.start();
                int cx = x + cards.originX(w) + (slot % cards.cols()) * (cards.cellW() + ModelPickerLayout.CARD_GAP);
                int cy = y + cards.originY(h) + (slot / cards.cols()) * (cards.cellH() + ModelPickerLayout.CARD_GAP);
                renderCloudCard(g, visible.get(i), slot, cx, cy, cards.cellW(), cards.cellH(), mouseX, mouseY);
            }
        } else {
            ModelListMetrics metrics = modelListMetrics(w, h, visible.size());
            for (int i = page.start(); i < page.end(); i++) {
                int slot = i - page.start();
                int cx = x + (slot % metrics.cols()) * metrics.cellW() + 3;
                int cy = y + (slot / metrics.cols()) * metrics.cellH() + 3;
                renderCloudTextEntry(g, visible.get(i), cx, cy, metrics.cellW() - 6, metrics.cellH() - 6, metrics.dense(), mouseX, mouseY);
            }
        }
        g.disableScissor();
    }

    private CloudModelPage cloudGridPage(int w, int h) {
        List<CloudAssetSummary> visible = cloudVisibleEntries();
        CloudModelPage page;
        if (gridMode(w, h) == ModelPickerLayout.GridMode.CARDS) {
            page = CloudModelPage.cards(visible.size(), ModelPickerLayout.cards(w, h).capacity(), STATE.cloudScroll);
        } else {
            ModelListMetrics metrics = modelListMetrics(w, h, visible.size());
            page = CloudModelPage.rows(visible.size(), metrics.cols(), metrics.rows(), STATE.cloudScroll);
        }
        STATE.cloudScroll = page.scroll();
        return page;
    }

    private boolean canChangeCloudVisibility(CloudAssetSummary entry) {
        return STATE.canChangeCloudVisibility(ownedCloudAssetIds.contains(entry.ref().assetId()));
    }

    private String cloudEntryName(CloudAssetSummary entry) {
        return entry.name().isBlank() ? entry.ref().assetId() : entry.name();
    }

    private String cloudEntryDetail(CloudAssetSummary entry) {
        String visibility = Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (entry.isPublic() ? "public" : "private")).getString();
        return entry.format().toUpperCase(Locale.ROOT) + " · " + visibility;
    }

    private Component cloudEntryTooltip(CloudAssetSummary entry) {
        return Component.literal(cloudEntryName(entry) + "\n" + entry.ref().assetId() + "\n" + cloudEntryDetail(entry));
    }

    private Component cloudVisibilityAction(CloudAssetSummary entry) {
        return Component.translatable("gui.sparkle_morpher.cloud_upload." +
                (pendingVisibilityChanges.contains(entry.ref().assetId()) ? "changing_visibility" : entry.isPublic() ? "make_private" : "make_public"));
    }

    private void renderCloudCard(GuiGraphics g, CloudAssetSummary entry, int slot, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        String modelId = this.controller.cloudModelId(entry);
        boolean selected = cloudMultiSelectMode ? selectedCloudAssetIds.contains(entry.ref().assetId()) : modelId.equals(STATE.selectedModelId);
        boolean hover = inside(mouseX, mouseY, cx, cy, cw, ch);
        boolean managed = canChangeCloudVisibility(entry) && !cloudMultiSelectMode;
        int actionH = managed && ch >= 70 ? 22 : 0;
        int nameH = Math.min(ch - 8, ModelPickerLayout.nameBandH(ch) + actionH);
        int coverH = Math.max(1, ch - nameH);
        fill(g, cx, cy, cw, ch, selected ? CARD_SELECT : hover ? CARD_BASE_HOVER : CARD_BASE);
        ModelAssembly assembly = this.controller.assemblyOrNull(modelId);
        if (assembly != null) {
            renderCardMonster(g, slot, modelId, cx, cy, cw, ch, coverH);
        } else if (pendingCloudImports.contains(modelId)) {
            drawCardLoading(g, cx, cy, cw, coverH);
        } else {
            int size = Math.max(1, Math.min(30, Math.min(cw - 12, coverH - 20)));
            drawIconScaled(g, IconGlyph.MODEL, cx + (cw - size) / 2, cy + Math.max(2, (coverH - size) / 2 - 4), size);
            if (coverH >= 50) drawCentered(g, Component.literal(trim(Component.translatable("gui.sparkle_morpher.model_panel.cloud.download_use").getString(), cw - 8)),
                    cx + cw / 2, cy + coverH - 14, MUTED);
        }
        fill(g, cx, cy + coverH, cw, nameH, CARD_NAME_BG);
        drawCardName(g, new ModelEntry(modelId, cloudEntryName(entry), cloudEntryDetail(entry), false, false),
                cx, cy + coverH, cw, nameH - actionH, false);
        if (cloudMultiSelectMode) renderCloudSelection(g, entry, cx + 4, cy + 4);
        else drawIcon(g, entry.isPublic() ? IconGlyph.SITES : IconGlyph.LOCK, cx + 3, cy + 3);

        border(g, cx, cy, cw, ch, selected ? RED : hover ? CARD_NAME_TEXT : 0x55FFFFFF);
        hit(cx, cy, cw, ch, cloudEntryTooltip(entry), () -> clickCloudAsset(entry));
        renderCloudFavoriteControl(g, entry, mouseX, mouseY, cx + cw - 20, cy + 2);
        if (managed) {
            if (actionH > 0) renderTextButton(g, mouseX, mouseY, cx + 4, cy + ch - 21, cw - 8, 18, cloudVisibilityAction(entry), () -> changeCloudVisibility(entry));
            else renderIconButton(g, mouseX, mouseY, cx + 2, cy + 2, entry.isPublic() ? IconGlyph.LOCK : IconGlyph.SITES,
                    cloudVisibilityAction(entry), () -> changeCloudVisibility(entry));
        }
    }

    private void renderCloudTextEntry(GuiGraphics g, CloudAssetSummary entry, int cx, int cy, int cw, int ch, boolean dense, int mouseX, int mouseY) {
        boolean selected = cloudMultiSelectMode ? selectedCloudAssetIds.contains(entry.ref().assetId()) : this.controller.cloudModelId(entry).equals(STATE.selectedModelId);
        boolean hover = inside(mouseX, mouseY, cx, cy, cw, ch);
        boolean managed = canChangeCloudVisibility(entry) && !cloudMultiSelectMode;
        fill(g, cx, cy, cw, ch, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x3E30363B);
        border(g, cx, cy, cw, ch, selected ? RED : 0x33FFFFFF);
        int iconY = dense ? cy + Math.max(0, (ch - 16) / 2) : cy + 3;
        if (cloudMultiSelectMode) renderCloudSelection(g, entry, cx + 4, iconY + 2);
        else drawIcon(g, entry.isPublic() ? IconGlyph.SITES : IconGlyph.LOCK, cx + 4, iconY);
        boolean favorite = this.controller.isCloudFavorite(entry);
        int trailing = managed ? 44 : 22;
        drawText(g, Component.literal(trim(cloudEntryName(entry), cw - 28 - trailing)), cx + 22,
                dense ? cy + (ch - this.font.lineHeight) / 2 + 1 : cy + 6);
        if (!dense) drawMuted(g, Component.literal(trim(cloudEntryDetail(entry), cw - 12)), cx + 6, cy + 22);

        hit(cx, cy, cw, ch, cloudEntryTooltip(entry), () -> clickCloudAsset(entry));
        renderCloudFavoriteControl(g, entry, mouseX, mouseY, cx + cw - 20, iconY - 1);
        if (managed) renderIconButton(g, mouseX, mouseY, cx + cw - 42, iconY - 1, entry.isPublic() ? IconGlyph.LOCK : IconGlyph.SITES,
                cloudVisibilityAction(entry), () -> changeCloudVisibility(entry));
    }

    private void advanceCloudPage(int w, int h) {
        CloudModelPage page = cloudGridPage(w, h);
        if (page.hasNext()) STATE.cloudScroll = page.next();
        else if (STATE.cloudHasMore && !STATE.cloudLoading) requestCloudPage(true);
    }

    private void renderCloudPageControls(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<CloudAssetSummary> visible = cloudVisibleEntries();
        if (visible.isEmpty() && !STATE.cloudHasMore) return;
        CloudModelPage page = cloudGridPage(w, h);
        int nextX = x + w - MODEL_PAGE_BUTTON_WIDTH - 6;
        int prevX = nextX - MODEL_PAGE_BUTTON_WIDTH - 4;
        String range = (visible.isEmpty() ? 0 : page.start() + 1) + "-" + page.end() + "/" + visible.size() + (STATE.cloudHasMore ? "+" : "");
        int room = prevX - (x + 106) - 8;
        if (room >= 18) drawMuted(g, Component.literal(trim(range, room)), x + 106, y + 8);
        renderTextButton(g, mouseX, mouseY, prevX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT,
                Component.translatable("gui.sparkle_morpher.pre_page"), () -> STATE.cloudScroll = page.previous());
        Component next = page.hasNext() ? Component.translatable("gui.sparkle_morpher.next_page")
                : Component.translatable(STATE.cloudLoading ? "gui.sparkle_morpher.cloud.loading" : STATE.cloudHasMore
                ? "gui.sparkle_morpher.model_panel.cloud.load_more" : "gui.sparkle_morpher.next_page");
        renderTextButton(g, mouseX, mouseY, nextX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT,
                next, () -> advanceCloudPage(w, h));
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
            if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) STATE.modelScroll = 0;
            else STATE.cloudScroll = 0;
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
        STATE.cloudScroll = 0;
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
    private void renderModelTextGrid(GuiGraphics g, int mouseX, int mouseY, List<ModelEntry> entries, int x, int y, int w, int h) {
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
    private void renderModelCards(GuiGraphics g, int mouseX, int mouseY, List<ModelEntry> entries, int x, int y, int w, int h) {
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
    private PlayerPreviewEntity cardPreviewEntity(int slot, String modelId, String textureId, ModelAssembly asm) {
        if (slot < 0 || slot >= this.cardPreviews.size()) {
            return null;
        }
        PlayerPreviewEntity entity = this.cardPreviews.get(slot);
        // A same-ID reload replaces the assembly; failed first bindings must also retry.
        if (!modelId.equals(this.cardPreviewModels.get(slot)) || !Objects.equals(textureId, this.cardPreviewTextures.get(slot))
                || entity.getModelAssembly() != asm || !entity.isModelReady()) {
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
    private void renderModelCard(GuiGraphics g, ModelEntry entry, int slot, int cx, int cy, int cw, int ch, boolean hover, Set<String> starredModels) {
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
    private void drawCardName(GuiGraphics g, ModelEntry entry, int bx, int by, int cw, int nameH, boolean folder) {
        int w = Math.max(6, cw - 6);
        String title = trim(entry.title(), w);
        String sub = entry.subtitle();
        boolean twoLine = !folder && nameH >= 28 && sub != null && !sub.isEmpty() && !"folder".equals(sub);
        int tx = bx + 3;
        if (twoLine) {
            g.drawString(this.font, Component.literal(title), tx, by + 3, folder ? 0xFF2B2B2B : CARD_NAME_TEXT, false);
            g.drawString(this.font, Component.literal(trim(sub, w)), tx, by + nameH - this.font.lineHeight - 3, MUTED, false);
        } else {
            g.drawString(this.font, Component.literal(title), tx, by + Math.max(2, (nameH - this.font.lineHeight) / 2), folder ? 0xFF2B2B2B : CARD_NAME_TEXT, false);
        }
    }

    /** 文件夹卡封面：ysm_pack.json 里的贴图，缺省用内置默认图标。 */
    private void drawFolderCover(GuiGraphics g, ModelEntry entry, int x, int y, int w, int h) {
        AbstractTexture texture = null;
        try {
            var pack = ClientModelManager.getModelPackMap().get(entry.modelId());
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
     * 走 {@link ModelPreviewRenderer#renderLivingEntityPreview} 的显式 yaw 重载（1.21.1 分支仍保留）。
     */
    private void renderCardMonster(GuiGraphics g, int slot, String modelId, int cx, int cy, int cw, int ch, int coverH) {
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
    private boolean renderCardFigure(GuiGraphics g, int slot, String modelId, ModelAssembly asm, int cx, int cy, int cw, int coverH) {
        try {
            String textureId = selectedTextureOrDefault(asm);
            PlayerPreviewEntity entity = cardPreviewEntity(slot, modelId, textureId, asm);
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
            ModelPreviewRenderer.renderLivingEntityPreview(cx + cw / 2.0f, cy + coverH - 3.0f, scale, 0.0f, entity,
                    RendererManager.getPlayerRenderer(), disableRotation, true, yaw);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void drawCardLoading(GuiGraphics g, int cx, int cy, int cw, int coverH) {
        String s = "…";
        g.drawString(this.font, Component.literal(s), cx + (cw - this.font.width(s)) / 2, cy + Math.max(4, (coverH - this.font.lineHeight) / 2), CARD_NAME_TEXT, false);
    }

    /** 把封面等比 fit 进 (x,y,w,h)，居中不拉伸；拿不到资源/尺寸时返回 false。 */
    private boolean drawCoverImage(GuiGraphics g, AbstractTexture tex, int x, int y, int w, int h) {
        if (tex == null || w <= 0 || h <= 0) {
            return false;
        }
        int[] dims = coverDimensions(tex);
        if (dims == null) {
            return false;
        }
        var loc = this.controller.textureLocation(tex, dims[0]);
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
        g.blit(loc, dx, dy, dw, dh, 0, 0, texW, texH, texW, texH);
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
    private void drawIconScaled(GuiGraphics g, IconGlyph icon, int x, int y, int size) {
        if (size <= 0) {
            return;
        }
        g.blit(MODEL_PANEL_ICONS, x, y, size, size, icon.u, icon.v, 16, 16, 128, 64);
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
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) return cloudModelViewport().gridHeight();
        int y = this.layout.contentTop + 8;
        int contentBottom = this.layout.footerTop - 6;
        boolean compact = compactModelLayout();
        int pathY = STATE.modelSource == ModelPanelState.ModelSource.LOCAL ? (compact ? y + 88 : y + 52) : y + cloudPathOffset();
        int gridY = pathY + 20;
        int detailStripH = compactDetailStripH();
        int reserve = detailStripH > 0 ? detailStripH + 3 : 0;
        return Math.max(compact ? 34 : 50, contentBottom - 28 - 4 - reserve - gridY);
    }

    private void renderModelBottomActions(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int gridH) {
        fill(g, x, y, w, 24, GLASS_DARK);
        int bx = x + 6;
        if (STATE.multiSelectMode && STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.DELETE, Component.translatable("gui.sparkle_morpher.model_panel.delete"), this::deleteSelectedModels);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.MOVE, Component.translatable("gui.sparkle_morpher.model_panel.move"), () -> openCategoryPanel(""));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.CREATE, Component.translatable("gui.sparkle_morpher.model_select.new_category"), () -> openCategoryPanel(""));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.CHECK, Component.translatable("gui.sparkle_morpher.model_select.tooltip.select_all"), this::selectAllVisibleModels);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.CLEAR, Component.translatable("gui.sparkle_morpher.model_panel.clear_selection"), this::clearModelSelection);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.SITES, Component.translatable("gui.sparkle_morpher.model_panel.upload_cloud"), this::openCloudUpload);
            Component msg = Component.translatable("gui.sparkle_morpher.model_panel.selected_count", this.selectedModelIds.size());
            drawMuted(g, msg, Math.min(x + w - this.font.width(msg) - 8, bx + 30), y + 8);
        } else if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.TEXTURE, Component.translatable("gui.sparkle_morpher.model_panel.use_texture"), this::applySelectedTexture);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.STAR, Component.translatable("gui.sparkle_morpher.model_panel.toggle_favorite"), this::toggleSelectedStar);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.RELOAD, Component.translatable("gui.sparkle_morpher.model_panel.reload_models"), () -> this.controller.reloadLocalModels(this::setStatus));
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.SITES, Component.translatable("gui.sparkle_morpher.model_panel.upload_cloud"), this::openCloudUpload);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.MODE, pickerStyleLabel(pickerStyle()), this::cyclePickerStyle);
        } else {
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.INFO, Component.translatable("gui.sparkle_morpher.cloud_upload.login"), this::openCloudAccount);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.RELOAD, Component.translatable("gui.sparkle_morpher.model_panel.reload_models"), this::resetCloudPage);
            bx += 24;
            renderIconButton(g, mouseX, mouseY, bx, y + 3, IconGlyph.MODE, pickerStyleLabel(pickerStyle()), this::cyclePickerStyle);
        }
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
            ModelAssembly assembly = selectedAssembly();
            renderIconButton(g, mouseX, mouseY, x + 78, y + 3, IconGlyph.TEXTURE,
                    Component.translatable("gui.sparkle_morpher.model_panel.cloud.open_textures"), () -> {
                        if (assembly != null) openCloudTexturePicker(assembly, STATE.selectedModelId);
                        else setStatus(Component.translatable("gui.sparkle_morpher.model_panel.select_model"), ChatFormatting.YELLOW);
                    });
        }
        renderModelPageControls(g, mouseX, mouseY, x, y, w, gridH);
    }

    private void renderModelPageControls(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int gridH) {
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
            renderCloudPageControls(g, mouseX, mouseY, x, y, w, gridH);
            return;
        }
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

    private void renderModelDetails(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
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
        Metadata metadata = assembly.getModelData() == null ? null : assembly.getModelData().getExtraInfo();
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
        List<String> textures = assembly.getTextureNames();
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

    private void renderSelectedModelPreview(GuiGraphics g, ModelAssembly assembly, String modelId, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
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
                ModelPreviewRenderer.renderLivingEntityPreview(x + w / 2.0f, y + h - 6.0f, scale, partialTick, this.previewEntity, RendererManager.getPlayerRenderer(), false, true, ModelPreviewRenderer.FRONT_FACING_YAW, previewCenterX - previewHalfSize, previewCenterY - previewHalfSize, previewCenterX + previewHalfSize, previewCenterY + previewHalfSize, mouseX, mouseY);
            } catch (Exception ignored) {
                drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
            }
        } else {
            drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
        }
    }

    private void renderResourceTab(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = resourceListX();
        int y = this.layout.contentTop + 8;
        int rightW = resourceRightW();
        int listW = resourceListW();
        int rightX = x + listW + 10;
        int bottom = this.layout.footerTop - 6;
        int bx = resourceToolbarX();

        if (this.resourceSearchBox != null) {
            this.resourceSearchBox.render(g, mouseX, mouseY, partialTick);
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

    private void renderResourceList(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
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
            g.drawString(this.font, trim(entry.name(), ww - 78), x + 8, rowY + 5, TEXT, false);
            g.drawString(this.font, trim(resourceDetail(entry), ww - 100), x + 8, rowY + 15, MUTED, false);
            hit(x + 3, rowY + 2, ww - 38, ROW - 4, Component.literal(entry.name()), () -> this.controller.clickResource(entry));
            renderIconButton(g, mouseX, mouseY, x + ww - 28, rowY + 4, this.controller.isQueued(entry) ? IconGlyph.QUEUE : IconGlyph.DOWNLOAD, Component.translatable("gui.sparkle_morpher.model_panel.download"), () -> enqueueResource(entry));
        }
        if (showBar) {
            renderScrollbar(g, mouseX, mouseY, x + w - 7, y + 3, 4, h - 6, entries.size(), rows, STATE.resourceScroll);
        }
    }

    private void renderResourceRightPane(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
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

    private void renderTaskRow(GuiGraphics g, int x, int y, int w, ModernPlayerModelScreenController.TaskView task) {
        fill(g, x, y, w, 20, GLASS_DARK);
        drawText(g, Component.literal(trim(task.name(), w - 58)), x + 4, y + 3);
        int barX = x + 4;
        int barY = y + 14;
        int fillW = (int) ((w - 8) * clamp(task.progress(), 0f, 1f));
        fill(g, barX, barY, w - 8, 3, 0xAA101010);
        fill(g, barX, barY, fillW, 3, task.color());
    }

    private void renderSettingsTab(GuiGraphics g, int mouseX, int mouseY) {
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

    private void renderSettingGroups(GuiGraphics g, int x, int y, int w) {
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

    private void renderSettingRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, SettingRow row) {
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
            g.drawString(this.font, trim(label.getString(), sx - x - 12), x + 6, y + 6, TEXT, false);
            renderSegmentedOption(g, sx, y + 2, leftW, 15, segmented.left(), segmented.leftSelected(), segmented.leftAction());
            renderSegmentedOption(g, sx + leftW + 2, y + 2, rightW, 15, segmented.right(), !segmented.leftSelected(), segmented.rightAction());
            return;
        }
        g.drawString(this.font, trim(label.getString(), w - 82), x + 6, y + 6, TEXT, false);
        if (row.booleanValue() != null) {
            int bx = x + w - 34;
            fill(g, bx, y + 4, 26, 11, row.booleanValue() ? RED_SOFT : 0x55303030);
            fill(g, bx + (row.booleanValue() ? 15 : 2), y + 5, 9, 9, 0xFFEDE1CC);
            hit(x, y, w, 19, label, row.action());
        } else {
            if (row.action() != null && row.decrement() == null && row.increment() == null) {
                int actionX = x + w - 76;
                fill(g, actionX, y + 2, 68, 15, 0x55303030);
                border(g, actionX, y + 2, 68, 15, 0x33FFFFFF);
                drawCentered(g, Component.literal(trim(row.valueText(), 62)), actionX + 34, y + 5, TEXT);
                hit(x, y, w, 19, label, row.action());
                return;
            }
            renderIconButton(g, mouseX, mouseY, x + w - 50, y + 1, IconGlyph.MINUS, Component.translatable(row.labelKey()), row.decrement());
            renderIconButton(g, mouseX, mouseY, x + w - 24, y + 1, IconGlyph.PLUS, Component.translatable(row.labelKey()), row.increment());
            drawMuted(g, Component.literal(row.valueText()), x + w - 90, y + 6);
        }
    }

    private void renderSegmentedOption(GuiGraphics g, int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
        fill(g, x, y, w, h, selected ? PANEL_ACTIVE : 0x55303030);
        border(g, x, y, w, h, selected ? RED : 0x33FFFFFF);
        drawCentered(g, Component.literal(trim(label.getString(), w - 6)), x + w / 2, y + 4, selected ? 0xFFFFFFFF : TEXT);
        hit(x, y, w, h, label, action);
    }

    private void renderSecondaryPanel(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
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
            closeCloudForm();
            init();
            if (afterCloudLogin != null) {
                Screen next = afterCloudLogin;
                afterCloudLogin = null;
                InputUtil.setScreen(next);
            }
        });
        switch (STATE.secondaryPanel) {
            case SITES -> renderSitesPanel(g, mouseX, mouseY, x, y, w, h, partialTick);
            case CATEGORIES -> renderCategoryPanel(g, mouseX, mouseY, x, y, w, h, partialTick);
            case IMPORT -> renderImportPanel(g, mouseX, mouseY, x, y, w, h);
            case CLOUD_LOGIN, CLOUD_INSTANCE -> renderCloudForm(g, mouseX, mouseY, x, y, w, h, partialTick);
            default -> {
            }
        }
    }

    private void renderCloudAccountPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        var selected = CloudManagementScreen.management().registry().selected();
        String instanceId = selected.map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse("");
        boolean connected = CloudClientRuntime.state(instanceId) != null;
        AccountPanelLayout panels = accountPanelLayout();
        drawTitle(g, cloudText(STATE.activeTab == ModelPanelState.Tab.ACCOUNT ? "account_management" : "instance_management"), x + 8, y + 10);
        drawMuted(g, Component.literal(trim(selected.map(CloudManagementScreen::displayName).orElse("—") + "  ·  "
                + CloudManagementScreen.text(connected ? "connected" : "disconnected"), w - 132)), x + 126, y + 12);
        glassPanel(g, panels.listX(), panels.listY(), panels.listWidth(), panels.listHeight());
        glassPanel(g, panels.detailX(), panels.detailY(), panels.detailWidth(), panels.detailHeight());
        renderCloudAccountList(g, mouseX, mouseY, panels);
        var details = cloudDetailLayout();
        int firstDetailHit = this.hits.size();
        g.enableScissor(panels.detailX(), panels.detailY(), panels.detailX() + panels.detailWidth(), panels.detailY() + panels.detailHeight());
        switch (cloudAccountPage) {
            case ACCOUNT -> renderCloudAccountDetails(g, mouseX, mouseY, details, connected, selected.map(CloudInstanceRegistry::isBuiltinOfficial).orElse(false), partialTick);
            case INSTANCES -> renderCloudInstances(g, mouseX, mouseY, details, partialTick);
            case SCOPES -> renderCloudScopes(g, mouseX, mouseY, details, partialTick);
            case IDENTITY, CLAIM, APPROVAL -> renderCloudIdentityDetails(g, mouseX, mouseY, details, partialTick);
        }
        g.disableScissor();
        this.hits.subList(firstDetailHit, this.hits.size()).removeIf(hit -> hit.y() < panels.detailY() || hit.y() + hit.h() > panels.detailY() + panels.detailHeight());
        int detailContentHeight = cloudIdentityContentHeight();
        if (detailContentHeight > panels.detailHeight()) renderScrollbar(g, mouseX, mouseY, panels.detailX() + panels.detailWidth() - 6, panels.detailY() + 4, 4, panels.detailHeight() - 8, detailContentHeight, panels.detailHeight(), cloudDetailScroll);
        Component panelMessage = isCloudIdentityPage() && cloudIdentityFlow != null
                ? cloudText("identity_panel." + (cloudIdentityFlow.busy() ? "working" : cloudIdentityFlow.message())) : cloudPanelStatus;
        drawText(g, Component.literal(trim(panelMessage.getString(), w - 16)), x + 8, y + h - 18);
    }

    private void renderCloudAccountList(GuiGraphics g, int mouseX, int mouseY, AccountPanelLayout panels) {
        if (isCloudIdentityPage()) { renderCloudIdentityList(g, mouseX, mouseY, panels); return; }
        int x = panels.listX(), y = panels.listY(), w = panels.listWidth();
        int listY = y + 38;
        int rows = panels.listRows();
        var management = CloudManagementScreen.management();
        drawTitle(g, cloudText(cloudAccountPage == CloudAccountPage.ACCOUNT ? "multi_account" : cloudAccountPage == CloudAccountPage.SCOPES ? "scopes" : "instances"), x + 10, y + 12);
        if (cloudAccountPage == CloudAccountPage.SCOPES) {
            var scopes = management.snapshot().scopes();
            cloudAccountScroll = clamp(cloudAccountScroll, 0, Math.max(0, scopes.size() - rows));
            for (int i = 0; i < rows && cloudAccountScroll + i < scopes.size(); i++) {
                var scope = scopes.get(cloudAccountScroll + i);
                renderRowButton(g, mouseX, mouseY, x + 8, listY + i * 36, w - 16, 30,
                        Component.literal(trim(scope.name(), w - 28)), management.snapshot().selectedScope() != null && management.snapshot().selectedScope().scopeId().equals(scope.scopeId()), () -> selectCloudScope(scope.scopeId()));
            }
            if (scopes.size() > rows) renderScrollbar(g, mouseX, mouseY, x + w - 6, listY, 4, panels.listHeight() - 46, scopes.size(), rows, cloudAccountScroll);
            return;
        }
        var profiles = management.registry().profiles();
        cloudAccountScroll = clamp(cloudAccountScroll, 0, Math.max(0, profiles.size() - rows));
        for (int i = 0; i < rows && cloudAccountScroll + i < profiles.size(); i++) {
            var profile = profiles.get(cloudAccountScroll + i);
            int rowY = listY + i * 36;
            boolean active = management.registry().selected().map(current -> current.instanceId().equals(profile.instanceId())).orElse(false);
            boolean hover = inside(mouseX, mouseY, x + 8, rowY, w - 16, 32);
            fill(g, x + 8, rowY, w - 16, 32, active ? 0xCC344C5C : hover ? PANEL_HOVER : 0x8827333D);
            if (active) fill(g, x + 8, rowY, 2, 32, RED);
            drawText(g, Component.literal(trim(CloudManagementScreen.displayName(profile), w - 32)), x + 16, rowY + 5);
            String account = management.accountId(profile.instanceId());
            String detail = cloudAccountPage == CloudAccountPage.ACCOUNT
                    ? (CloudClientRuntime.state(profile.instanceId()) == null ? CloudManagementScreen.text("disconnected") : account)
                    : profile.instance().origin().toString();
            drawMuted(g, Component.literal(trim(detail, w - 32)), x + 16, rowY + 19);
            hit(x + 8, rowY, w - 16, 32, Component.literal(CloudManagementScreen.displayName(profile) + " · " + detail), () -> selectCloudInstance(profile));
        }
        if (profiles.size() > rows) renderScrollbar(g, mouseX, mouseY, x + w - 6, listY, 4, panels.listHeight() - 46, profiles.size(), rows, cloudAccountScroll);
    }

    private boolean isCloudIdentityPage() {
        return cloudAccountPage == CloudAccountPage.IDENTITY || cloudAccountPage == CloudAccountPage.CLAIM || cloudAccountPage == CloudAccountPage.APPROVAL;
    }

    private int cloudIdentityContentHeight() {
        return switch (cloudAccountPage) {
            case IDENTITY -> 300; case CLAIM -> 390; case APPROVAL -> 440;
            case SCOPES -> 268; case INSTANCES -> 232; default -> 180;
        };
    }

    private void ensureCloudIdentityFlow() {
        if (cloudIdentityFlow != null && cloudIdentityGateway.current()) return;
        if (cloudIdentityFlow != null) cloudIdentityFlow.invalidate();
        cloudIdentityFlow = null; cloudIdentityGateway = null;
        if (cloudIdentityCodeBox != null) cloudIdentityCodeBox.setValue("");
        try {
            cloudIdentityGateway = new CloudIdentityPanelGateway();
            cloudIdentityFlow = new com.micaftic.morpher.cloud.client.CloudIdentityWorkflow(cloudIdentityGateway, task -> Minecraft.getInstance().execute(task));
            cloudIdentityFlow.refresh();
        } catch (RuntimeException failure) { cloudPanelStatus = cloudText("identity_panel.login_required"); }
    }

    private void openCloudIdentityPage(CloudAccountPage page) {
        STATE.activeTab = ModelPanelState.Tab.ACCOUNT;
        cloudAccountPage = page; cloudAccountScroll = 0; cloudDetailScroll = 0; cloudIdentityChoicePage = 0;
        if (CloudManagementScreen.management().snapshot().scopes().isEmpty()) refreshCloudScopes();
        init();
    }

    private int cloudIdentityListSize() {
        return cloudAccountPage == CloudAccountPage.IDENTITY
                ? (cloudIdentityFlow == null ? 0 : cloudIdentityFlow.catalog().identities().size())
                : CloudManagementScreen.management().snapshot().scopes().size();
    }

    private void renderCloudIdentityList(GuiGraphics g, int mouseX, int mouseY, AccountPanelLayout panels) {
        ensureCloudIdentityFlow();
        int x = panels.listX() + 8, y = panels.listY(), w = panels.listWidth() - 16;
        boolean identities = cloudAccountPage == CloudAccountPage.IDENTITY;
        drawTitle(g, cloudText("identity_panel." + (identities ? "identities" : "worlds")), x, y + 12);
        int rows = panels.listRows(), count = cloudIdentityListSize();
        cloudAccountScroll = clamp(cloudAccountScroll, 0, Math.max(0, count - rows));
        if (count == 0) drawMuted(g, Component.literal(trim(cloudText("identity_panel." + (identities ? "no_identities" : "no_worlds")).getString(), w)), x, y + 42);
        for (int i = 0; i < rows && cloudAccountScroll + i < count; i++) {
            int index = cloudAccountScroll + i;
            if (identities) {
                var identity = cloudIdentityFlow.catalog().identities().get(index);
                boolean selected = identity.equals(cloudIdentityFlow.selectedIdentity());
                renderRowButton(g, mouseX, mouseY, x, y + 38 + i * 36, w, 30,
                        Component.literal(trim(identity.displayName() + " · " + cloudText("identity_panel." + ("VERIFIED".equals(identity.verificationStatus()) ? "verified_short" : "offline_short")).getString(), w - 12)),
                        selected, () -> cloudIdentityFlow.selectIdentity(identity.identityId()));
            } else {
                var scope = CloudManagementScreen.management().snapshot().scopes().get(index);
                var selected = CloudManagementScreen.management().snapshot().selectedScope();
                renderRowButton(g, mouseX, mouseY, x, y + 38 + i * 36, w, 30, Component.literal(trim(scope.name(), w - 12)),
                        scope.equals(selected), () -> selectCloudScope(scope.scopeId()));
            }
        }
        if (count > rows) renderScrollbar(g, mouseX, mouseY, x + w - 4, y + 38, 4, panels.listHeight() - 46, count, rows, cloudAccountScroll);
    }

    private void identityAction(GuiGraphics g, int mx, int my, int x, int y, int w, String key, boolean enabled, Runnable action) {
        Component label = cloudText("identity_panel." + key);
        if (enabled) renderTextButton(g, mx, my, x, y, w, 20, label, action);
        else { fill(g, x, y, w, 20, 0x4427333D); drawCentered(g, Component.literal(trim(label.getString(), w - 8)), x + w / 2, y + 6, MUTED); }
    }

    private void identityLine(GuiGraphics g, String key, int x, int y, int w) {
        drawMuted(g, Component.literal(trim(cloudText("identity_panel." + key).getString(), w)), x, y);
    }

    private void renderCloudIdentityDetails(GuiGraphics g, int mx, int my, AccountPanelLayout panels, float partialTick) {
        ensureCloudIdentityFlow();
        if (cloudIdentityCodeBox != null) cloudIdentityCodeBox.visible = false;
        int x = panels.fieldX(), y = panels.detailY(), w = panels.fieldWidth();
        identityAction(g, mx, my, x, y + 8, w, "back", true, () -> {
            cloudAccountPage = CloudAccountPage.ACCOUNT; cloudDetailScroll = 0; init();
        });
        if (cloudIdentityFlow == null) { identityLine(g, "login_required", x, y + 44, w); return; }
        var flow = cloudIdentityFlow;
        drawTitle(g, cloudText("identity_panel." + switch (cloudAccountPage) { case CLAIM -> "claim_title"; case APPROVAL -> "admin_title"; default -> "title"; }), x, y + 42);
        drawText(g, Component.literal(trim(flow.context().profileName(), w)), x, y + 62);
        if (cloudAccountPage == CloudAccountPage.IDENTITY) {
            identityLine(g, "identity_help", x, y + 82, w);
            identityAction(g, mx, my, x, y + 106, w, "refresh", !flow.busy(), flow::refresh);
            if (flow.catalog().providers().isEmpty()) identityLine(g, "no_provider", x, y + 138, w);
            else {
                identityAction(g, mx, my, x, y + 134, w, "verify", !flow.busy(), flow::verify);
                var provider = flow.catalog().providers().stream().filter(p -> p.providerId().equals(flow.providerId())).findFirst().orElse(null);
                if (provider != null) {
                    Component name = Component.literal(trim(provider.displayName(), w - 8));
                    if (flow.catalog().providers().size() > 1) renderTextButton(g, mx, my, x, y + 162, w, 20, name, flow::nextProvider);
                    else drawMuted(g, name, x, y + 170);
                }
            }
            var selectedIdentity = flow.selectedIdentity();
            if (selectedIdentity != null) identityLine(g, "VERIFIED".equals(selectedIdentity.verificationStatus()) ? "verified" : "registered", x, y + 194, w);
            identityAction(g, mx, my, x, y + 224, w, "use_world", !flow.busy(), () -> openCloudIdentityPage(CloudAccountPage.CLAIM));
            return;
        }
        var scope = flow.context().scope();
        if (scope == null) {
            identityLine(g, "choose_world", x, y + 86, w);
            identityAction(g, mx, my, x, y + 112, w, "refresh_worlds", !flow.busy(), this::refreshCloudScopes);
            return;
        }
        drawMuted(g, Component.literal(trim(scope.name(), w)), x, y + 82);
        if (!flow.loaded()) { identityLine(g, "loading", x, y + 106, w); identityAction(g, mx, my, x, y + 132, w, "refresh", !flow.busy(), flow::refresh); return; }
        if (flow.catalog().permissions() == null) { identityLine(g, "server_update", x, y + 106, w); identityAction(g, mx, my, x, y + 134, w, "refresh", !flow.busy(), flow::refresh); return; }
        if (cloudAccountPage == CloudAccountPage.APPROVAL) { renderCloudIdentityAdmin(g, mx, my, x, y, w); return; }
        identityLine(g, "claim_help", x, y + 102, w);
        if (flow.canManage()) identityAction(g, mx, my, x, y + (flow.claimPolicy() ? 274 : 340), w, "manage", !flow.busy(), () -> openCloudIdentityPage(CloudAccountPage.APPROVAL));
        if ("DISABLED".equals(flow.policy())) { identityLine(g, "disabled", x, y + 136, w); return; }
        if (!flow.canEdit()) { identityLine(g, "need_edit", x, y + 136, w); return; }
        if (flow.localOfflineIdentity() == null) {
            identityLine(g, "register_help", x, y + 136, w);
            identityAction(g, mx, my, x, y + 164, w, "register", flow.canRegister(), flow::register);
            return;
        }
        if (flow.hasBinding()) {
            var binding = flow.catalog().bindings().stream().filter(b -> b.entityUuid().equals(flow.context().profileId().toString()) && b.accountId().equals(flow.context().accountId())
                    && ("APPROVED".equals(b.status()) || "PENDING_APPROVAL".equals(b.status()))).findFirst().orElseThrow();
            identityLine(g, "APPROVED".equals(binding.status()) ? "binding_approved" : "requested", x, y + 136, w);
            drawText(g, Component.literal(trim(flow.targetName(binding.targetId()), w)), x, y + 156);
            identityAction(g, mx, my, x, y + 184, w, "refresh", !flow.busy(), flow::refresh);
            return;
        }
        if ("STRICT_APPROVAL".equals(flow.policy())) {
            renderCloudIdentityTargets(g, mx, my, x, y + 126, w);
            identityAction(g, mx, my, x, y + 246, w, "request", flow.canRequest(), flow::request);
            identityLine(g, "approval_help", x, y + 278, w);
            identityAction(g, mx, my, x, y + 318, w, "refresh", !flow.busy(), flow::refresh);
        } else if (flow.claimPolicy()) {
            identityLine(g, "code_help", x, y + 134, w);
            if (cloudIdentityCodeBox != null) {
                cloudIdentityCodeBox.setY(y + 158);
                cloudIdentityCodeBox.visible = true; cloudIdentityCodeBox.setEditable(!flow.busy());
                cloudIdentityCodeBox.render(g, mx, my, partialTick);
            }
            identityAction(g, mx, my, x, y + 190, w, "redeem", flow.canRedeem(), flow::redeem);
            identityAction(g, mx, my, x, y + 242, w, "refresh", !flow.busy(), flow::refresh);
        }
    }

    private void renderCloudIdentityTargets(GuiGraphics g, int mx, int my, int x, int y, int w) {
        var flow = cloudIdentityFlow;
        if (flow.catalog().targets().isEmpty()) { identityLine(g, "no_targets", x, y + 6, w); return; }
        var range = CloudScreenPagination.range(flow.catalog().targets().size(), 3, cloudIdentityChoicePage);
        cloudIdentityChoicePage = range.page();
        int row = 0;
        for (var target : flow.catalog().targets().subList(range.startInclusive(), range.endExclusive())) {
            renderRowButton(g, mx, my, x, y + row++ * 26, w, 22, Component.literal(trim(target.displayName(), w - 12)),
                    target.equals(flow.selectedTarget()), () -> flow.selectTarget(target.targetId()));
        }
        if (range.pageCount() > 1) {
            int half = Math.max(30, (w - 6) / 2);
            identityAction(g, mx, my, x, y + 82, half, "previous", !flow.busy() && range.page() > 0, () -> cloudIdentityChoicePage--);
            identityAction(g, mx, my, x + half + 6, y + 82, half, "next", !flow.busy() && range.page() + 1 < range.pageCount(), () -> cloudIdentityChoicePage++);
        }
    }

    private void renderCloudIdentityAdmin(GuiGraphics g, int mx, int my, int x, int y, int w) {
        var flow = cloudIdentityFlow;
        if (!flow.canManage()) { identityLine(g, "permission_denied", x, y + 110, w); return; }
        identityAction(g, mx, my, x, y + 102, w, "refresh", !flow.busy(), flow::refresh);
        if ("STRICT_APPROVAL".equals(flow.policy())) {
            if (flow.pending().isEmpty()) { identityLine(g, "no_requests", x, y + 140, w); return; }
            var range = CloudScreenPagination.range(flow.pending().size(), 3, cloudIdentityChoicePage);
            cloudIdentityChoicePage = range.page();
            int row = 0;
            for (var binding : flow.pending().subList(range.startInclusive(), range.endExclusive())) {
                String player = binding.identityDisplayName() != null ? binding.identityDisplayName() : flow.catalog().players().stream().filter(p -> p.id().toString().equals(binding.entityUuid())).findFirst()
                        .map(com.micaftic.morpher.cloud.client.CloudIdentityWorkflow.Player::name).orElse(cloudText("identity_panel.offline_player").getString());
                renderRowButton(g, mx, my, x, y + 134 + row++ * 28, w, 24, Component.literal(trim(player + " → " + flow.targetName(binding.targetId()), w - 12)),
                        binding.equals(flow.selectedBinding()), () -> flow.selectBinding(binding.bindingId()));
            }
            int half = Math.max(30, (w - 6) / 2);
            identityAction(g, mx, my, x, y + 222, half, "previous", !flow.busy() && range.page() > 0, () -> cloudIdentityChoicePage--);
            identityAction(g, mx, my, x + half + 6, y + 222, half, "next", !flow.busy() && range.page() + 1 < range.pageCount(), () -> cloudIdentityChoicePage++);
            identityAction(g, mx, my, x, y + 252, half, "approve", flow.canApprove(), () -> flow.approve(true));
            identityAction(g, mx, my, x + half + 6, y + 252, half, "reject", flow.canApprove(), () -> flow.approve(false));
            identityLine(g, "review_help", x, y + 284, w);
        } else if (flow.claimPolicy()) {
            renderCloudIdentityTargets(g, mx, my, x, y + 134, w);
            var player = flow.selectedPlayer();
            Component playerName = Component.literal(trim(player == null ? cloudText("identity_panel.no_players").getString() : player.name(), w - 8));
            renderTextButton(g, mx, my, x, y + 248, w, 20, playerName, flow::nextPlayer);
            identityAction(g, mx, my, x, y + 278, w, "issue", flow.canIssue(), flow::issue);
            if (flow.issued() != null) {
                identityLine(g, flow.issuedExpired() ? "code_expired" : "issued", x, y + 308, w);
                identityAction(g, mx, my, x, y + 330, w, "copy", !flow.busy() && !flow.issuedExpired(), () -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(flow.issued().code());
                    flow.copied();
                });
                identityAction(g, mx, my, x, y + 358, w, "revoke", !flow.busy(), flow::revoke);
            }
            identityLine(g, "invite_help", x, y + 394, w);
        } else identityLine(g, "disabled", x, y + 140, w);
    }

    private void renderCloudAccountDetails(GuiGraphics g, int mouseX, int mouseY,
                                           AccountPanelLayout panels, boolean connected, boolean official, float partialTick) {
        int x = panels.fieldX();
        int y = panels.detailY();
        int w = panels.fieldWidth();
        drawTitle(g, Component.translatable("gui.sparkle_morpher.cloud.manage.account"), x, y + 12);
        if (connected) {
            String instanceId = CloudManagementScreen.management().registry().selected()
                    .map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse("");
            drawSection(g, Component.translatable("gui.sparkle_morpher.cloud.manage.account"), x, y + 40);
            drawText(g, Component.translatable("gui.sparkle_morpher.cloud_upload.account",
                    CloudManagementScreen.management().accountId(instanceId)), x, y + 56);
            int half = panels.buttonWidth(2);
            renderTextButton(g, mouseX, mouseY, x, y + 82, half, 20,
                    Component.translatable("gui.sparkle_morpher.cloud.manage.logout"), () -> {
                        CloudManagementScreen.management().logout();
                        resetCloudPage();
                        init();
                    });
            renderTextButton(g, mouseX, mouseY, x + half + 6, y + 82, half, 20,
                    Component.translatable("gui.sparkle_morpher.cloud.manage.switch_account"), () -> {
                        CloudManagementScreen.management().logout();
                        resetCloudPage();
                        openCloudLoginForm(false);
                    });
            renderTextButton(g, mouseX, mouseY, x, y + 116, w, 20,
                    Component.translatable("gui.sparkle_morpher.cloud.manage.identity.bind_current"), () -> {
                        var context = CloudManagementScreen.accountContext();
                        var runtime = context.runtime();
                        cloudPanelStatus = Component.translatable("gui.sparkle_morpher.cloud.manage.working");
                        CloudManagementScreen.bindCurrentGameAccount().whenComplete((identity, failure) ->
                                Minecraft.getInstance().execute(() -> {
                                    try { context.check(runtime); } catch (java.util.concurrent.CancellationException stale) { return; }
                                    cloudPanelStatus = failure == null
                                            ? Component.translatable("gui.sparkle_morpher.cloud.manage.identity.bound", identity.displayName())
                                            : Component.literal(CloudManagementScreen.errorText(failure));
                                }));
                    });
            renderTextButton(g, mouseX, mouseY, x, y + 144, w, 20,
                    cloudText("identity_panel.title"),
                    () -> openCloudIdentityPage(CloudAccountPage.IDENTITY));
        } else {
            drawMuted(g, Component.literal(trim(cloudText("account_intro").getString(), w)), x, y + 40);
            renderTextButton(g, mouseX, mouseY, x, y + 66, w, 24, cloudText("quick_connect"),
                    () -> { if (!cloudFormBusy) runCloudAccount(CloudManagementScreen.connectOfficialAccount(), "quick_success"); });
            int half = panels.buttonWidth(2);
            renderTextButton(g, mouseX, mouseY, x, y + 102, half, 20, cloudText("login"), () -> openCloudLoginForm(false));
            renderTextButton(g, mouseX, mouseY, x + half + 6, y + 102, half, 20, cloudText("register_short"), () -> openCloudLoginForm(true));
        }
    }

    private void renderCloudInstances(GuiGraphics g, int mouseX, int mouseY, AccountPanelLayout panels, float partialTick) {
        int x = panels.fieldX(), y = panels.detailY(), w = panels.fieldWidth();
        drawTitle(g, cloudText("instance_management"), x, y + 12);
        var selected = CloudManagementScreen.management().registry().selected();
        if (selected.isPresent()) {
            var profile = selected.get();
            drawText(g, Component.literal(trim(CloudManagementScreen.displayName(profile), w)), x, y + 40);
            drawMuted(g, Component.literal(trim(profile.instance().origin().toString(), w)), x, y + 58);
            boolean connected = CloudClientRuntime.state(profile.instanceId()) != null;
            drawText(g, cloudText(connected ? "connected" : "disconnected"), x, y + 80);
            renderTextButton(g, mouseX, mouseY, x, y + 106, w, 22, cloudText("manage_account"), this::openCloudAccount);
            if (!CloudInstanceRegistry.isBuiltinOfficial(profile)) renderTextButton(g, mouseX, mouseY, x, y + 136, w, 20, cloudText("remove_instance"), this::removeCloudInstance);
        }
        renderTextButton(g, mouseX, mouseY, x, y + 166, w, 22, cloudText("add_instance"), this::openCloudInstanceForm);
        renderTextButton(g, mouseX, mouseY, x, y + 196, w, 20, cloudText("scopes"), () -> setCloudAccountPage(CloudAccountPage.SCOPES));
    }

    private void renderCloudScopes(GuiGraphics g, int mouseX, int mouseY,
                                   AccountPanelLayout panels, float partialTick) {
        int x = panels.fieldX();
        renderTextButton(g, mouseX, mouseY, x, panels.detailY() + 8, panels.fieldWidth(), 20, cloudText("back_instances"), () -> setCloudAccountPage(CloudAccountPage.INSTANCES));
        drawSection(g, Component.translatable("gui.sparkle_morpher.cloud.manage.scope_id"), x, panels.fieldY(0) - 14);
        drawSection(g, Component.translatable("gui.sparkle_morpher.cloud.manage.scope_name"), x, panels.fieldY(1) - 14);
        drawSection(g, Component.translatable("gui.sparkle_morpher.cloud.manage.world_epoch"), x, panels.fieldY(2) - 14);
        if (cloudScopeIdBox != null) cloudScopeIdBox.render(g, mouseX, mouseY, partialTick);
        if (cloudScopeNameBox != null) cloudScopeNameBox.render(g, mouseX, mouseY, partialTick);
        if (cloudWorldEpochBox != null) cloudWorldEpochBox.render(g, mouseX, mouseY, partialTick);
        int bw = panels.buttonWidth(3);
        int buttonY = panels.actionY(3);
        renderTextButton(g, mouseX, mouseY, x, buttonY, bw, 20,
                Component.translatable("gui.sparkle_morpher.cloud.manage.refresh_scopes"),
                this::refreshCloudScopes);
        renderTextButton(g, mouseX, mouseY, x + bw + 6, buttonY, bw, 20,
                Component.translatable("gui.sparkle_morpher.cloud.manage.create_scope"), this::createCloudScope);
        renderTextButton(g, mouseX, mouseY, x + (bw + 6) * 2, buttonY, bw, 20,
                Component.translatable("gui.sparkle_morpher.cloud.manage.join_selected"), this::joinCloudScope);
        if (CloudManagementScreen.management().snapshot().selectedScope() != null) {
            renderTextButton(g, mouseX, mouseY, x, buttonY + 28, panels.fieldWidth(), 20,
                    Component.translatable("gui.sparkle_morpher.cloud.manage.targets_acl"), () ->
                            InputUtil.setScreen(new CloudTargetManagementScreen(this, CloudManagementScreen.management())));
        }
    }

    private void setCloudAccountPage(CloudAccountPage page) {
        if (cloudAccountPage == page) return;
        cloudAccountPage = page;
        cloudAccountScroll = 0;
        cloudDetailScroll = 0;
        cloudPanelStatus = Component.empty();
        init();
    }

    private void selectCloudInstance(CloudInstanceRegistry.CloudInstanceProfile profile) {
        try {
            closeCloudForm();
            switchCloudInstance(profile);
            CloudManagementScreen.management().saveInstances();
            cloudPanelStatus = Component.literal(CloudManagementScreen.text("selected", CloudManagementScreen.displayName(profile)));
        } catch (java.io.IOException | RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void saveCloudInstance() {
        if (cloudFormBusy || cloudOriginBox == null || cloudInstanceNameBox == null) return;
        cloudAddressDraft = cloudOriginBox.getValue().trim();
        cloudNameDraft = cloudInstanceNameBox.getValue().trim();
        if (cloudAddressDraft.isBlank()) { cloudPanelStatus = cloudText("address_required"); return; }
        cloudFormBusy = true;
        long generation = ++cloudFormGeneration;
        cloudPanelStatus = cloudText("checking_instance");
        try {
            com.micaftic.morpher.cloud.client.CloudHttpClient.discoverProfile(cloudAddressDraft, cloudNameDraft)
                    .whenComplete((profile, failure) -> Minecraft.getInstance().execute(() -> {
                        if (generation != cloudFormGeneration || STATE.secondaryPanel != ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) return;
                        cloudFormBusy = false;
                        if (failure != null) { cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure)); return; }
                        var registry = CloudManagementScreen.management().registry();
                        var existing = registry.find(profile.instanceId());
                        if (existing.isPresent() && !existing.get().instance().origin().equals(profile.instance().origin())) {
                            cloudPanelStatus = cloudText("instance_conflict");
                            return;
                        }
                        try {
                            if (existing.isEmpty()) registry.addOrReplace(profile);
                            CloudManagementScreen.management().saveInstances();
                            closeCloudForm();
                            selectCloudInstance(existing.orElse(profile));
                            cloudPanelStatus = cloudText("instance_added");
                        } catch (java.io.IOException | RuntimeException error) {
                            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(error));
                        }
                    }));
        } catch (RuntimeException failure) {
            cloudFormBusy = false;
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void removeCloudInstance() {
        try {
            var selected = CloudManagementScreen.management().registry().selected().orElseThrow();
            if (CloudInstanceRegistry.isBuiltinOfficial(selected)) return;
            CloudManagementScreen.management().logout();
            CloudManagementScreen.management().registry().remove(selected.instanceId());
            CloudManagementScreen.management().saveInstances();
            CloudManagementScreen.management().registry().selected().ifPresent(this::switchCloudInstance);
            init();
            cloudPanelStatus = Component.literal(CloudManagementScreen.text("instance_removed", selected.name()));
        } catch (IOException | RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void selectCloudScope(String id) {
        try {
            CloudManagementScreen.management().selectScope(id);
            init();
        } catch (RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void refreshCloudScopes() {
        try {
            runCloudManagementAction(CloudManagementScreen.management().refreshScopes(), "scopes_refreshed");
        } catch (RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void createCloudScope() {
        try {
            var create = new com.micaftic.morpher.cloud.client.CloudScopeClient.CloudScopeCreate(
                    cloudScopeIdBox.getValue().trim(), cloudScopeNameBox.getValue().trim(),
                    cloudWorldEpochBox.getValue().trim(), null);
            runCloudManagementAction(CloudManagementScreen.management().createScope(create), "scope_created");
        } catch (RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void joinCloudScope() {
        try {
            CloudManagementScreen.management().selectScope(cloudScopeIdBox.getValue().trim());
            runCloudManagementAction(CloudManagementScreen.management().enterSelectedScope(), "scope_joined");
        } catch (RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void runCloudManagementAction(java.util.concurrent.CompletableFuture<?> request, String successKey) {
        cloudPanelStatus = Component.translatable("gui.sparkle_morpher.cloud.manage.working");
        request.whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
            cloudPanelStatus = failure == null
                    ? Component.literal(CloudManagementScreen.text(successKey))
                    : Component.literal(CloudManagementScreen.errorText(failure));
            if (failure == null && (STATE.activeTab == ModelPanelState.Tab.ACCOUNT || STATE.activeTab == ModelPanelState.Tab.INSTANCE)) init();
        }));
    }

    public static void openCloudManagement(Screen parent) {
        ModernPlayerModelScreen screen = new ModernPlayerModelScreen();
        InputUtil.setScreen(screen);
        screen.openCloudAccount();
    }

    void openCloudAccount() {
        closeCloudForm();
        STATE.activeTab = ModelPanelState.Tab.ACCOUNT;
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
        cloudAccountPage = CloudAccountPage.ACCOUNT;
        cloudAccountScroll = 0;
        cloudPanelStatus = Component.empty();
        registerCloudAccount = false;
        init();
    }

    void openCloudAccountForUpload(Screen uploadScreen, String instanceId) {
        if (CloudManagementScreen.management().registry().find(instanceId).isPresent()) {
            CloudManagementScreen.management().selectInstance(instanceId);
        }
        afterCloudLogin = uploadScreen;
        openCloudAccount();
    }

    private void submitCloudAccount(boolean register) {
        if (cloudAccountBox == null || cloudPasswordBox == null) return;
        if (cloudFormBusy) return;
        String account = cloudAccountBox.getValue().trim();
        cloudAccountDraft = account;
        String password = cloudPasswordBox.getValue();
        if (account.isBlank() || password.isEmpty()) { cloudPanelStatus = cloudText("credentials_required"); return; }
        if (register && password.length() < 8) { cloudPanelStatus = cloudText("register_hint"); return; }
        cloudPasswordBox.setValue("");
        try {
            runCloudAccount(CloudManagementScreen.submitAccount(register, account, password),
                    register ? "register_success" : "login_success");
        } catch (RuntimeException failure) {
            cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
        }
    }

    private void runCloudAccount(java.util.concurrent.CompletableFuture<?> request, String successKey) {
        var context = CloudManagementScreen.accountContext();
        long generation = cloudFormGeneration;
        cloudFormBusy = true;
        cloudPanelStatus = Component.translatable("gui.sparkle_morpher.cloud.manage.working");
        request.whenComplete((ignored, failure) -> Minecraft.getInstance().execute(() -> {
            try { context.check(); } catch (java.util.concurrent.CancellationException stale) { return; }
            if (generation != cloudFormGeneration) return;
            cloudFormBusy = false;
            if (failure != null) {
                cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(failure));
            } else {
                if (ignored instanceof com.micaftic.morpher.cloud.client.CloudSession session) {
                    try { context.checkSession(session); } catch (java.util.concurrent.CancellationException stale) { return; }
                }
                closeCloudForm();
                resetCloudPage();
                var runtime = context.runtime();
                CloudManagementScreen.currentIdentityBound(context).whenComplete((bound, identityFailure) -> Minecraft.getInstance().execute(() -> {
                    try { context.check(runtime); } catch (java.util.concurrent.CancellationException stale) { return; }
                    if (identityFailure != null) {
                        cloudPanelStatus = Component.literal(CloudManagementScreen.errorText(identityFailure));
                    } else if (!bound) {
                        cloudPanelStatus = Component.translatable("gui.sparkle_morpher.cloud.manage.identity.binding_required");
                    } else {
                        setStatus(Component.literal(CloudManagementScreen.text(successKey)), ChatFormatting.GREEN);
                        com.micaftic.morpher.cloud.client.CloudPlayerModelSync.requestIdentityRefresh(runtime);
                        if (afterCloudLogin != null) {
                            Screen next = afterCloudLogin; afterCloudLogin = null;
                            InputUtil.setScreen(next);
                            return;
                        }
                    }
                    init();
                }));
            }
        }));
    }

    private void renderSitesPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.sites"), x + 10, y + 10);
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.url"), x + 16, y + 28);
        if (this.siteEditBox != null) {
            this.siteEditBox.render(g, mouseX, mouseY, partialTick);
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

    private void renderCategoryPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.categories"), x + 10, y + 10);
        drawSection(g, Component.translatable("gui.sparkle_morpher.model_panel.name_target"), x + 16, y + 28);
        if (this.categoryEditBox != null) {
            this.categoryEditBox.render(g, mouseX, mouseY, partialTick);
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

    private void renderImportPanel(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h) {
        drawTitle(g, Component.translatable("gui.sparkle_morpher.import.title"), x + 10, y + 10);
        renderIconButton(g, mouseX, mouseY, x + 12, y + 34, IconGlyph.IMPORT, Component.translatable("gui.sparkle_morpher.import.choose_file"), this::openFilePicker);
        int yy = y + 70;
        drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.drop_files_hint"), x + 12, yy);
        yy += 20;
        if (this.controller.localImportInProgress()) {
            drawText(g, Component.translatable("gui.sparkle_morpher.model_panel.importing"), x + 12, yy);
        } else {
            drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.no_active_import"), x + 12, yy);
        }
    }

    private void renderFooter(GuiGraphics g) {
        fill(g, this.layout.left, this.layout.footerTop, this.layout.width, 1, 0x55303030);
        Component line = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? this.controller.queueStatus() : this.status;
        if (this.modelSelectionTarget == null && STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
            String cloudSync = com.micaftic.morpher.cloud.client.CloudPlayerModelSync.status();
            if (line.getString().isBlank() || cloudSync.contains("失败")) line = Component.literal(cloudSync);
        }
        ChatFormatting color = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? this.controller.queueStatusColor() : this.statusColor;
        if (this.modelSelectionTarget != null) {
            var feedback = com.micaftic.morpher.cloud.client.CloudEntityModelSync.feedback(this.stateKeyValue);
            if (feedback != null) {
                line = feedback.message();
                color = feedback.failed() ? ChatFormatting.RED : feedback.pending() ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            } else {
                line = Component.translatable("gui.sparkle_morpher.cloud.entity.share_hint");
                color = ChatFormatting.GRAY;
            }
        }
        int c = color.getColor() == null ? MUTED : 0xFF000000 | color.getColor();
        g.drawString(this.font, trim(line.getString(), this.layout.width - 20), this.layout.left + 10, this.layout.footerTop + 8, c, false);
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (!inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (hit.tooltip().getString().isBlank()) return;
            int maxWidth = Math.max(8, Math.min(220, this.width - 18));
            int maxLines = Math.max(1, (this.height - 18) / this.font.lineHeight);
            var lines = this.font.split(hit.tooltip(), maxWidth).stream().limit(maxLines).toList();
            int tw = lines.stream().mapToInt(this.font::width).max().orElse(0) + 10;
            int th = lines.size() * this.font.lineHeight + 10;
            int tx = Math.max(4, Math.min(mouseX + 10, this.width - tw - 4));
            int ty = Math.max(4, Math.min(mouseY + 10, this.height - th - 4));
            fill(g, tx, ty, tw, th, 0xEE101010);
            border(g, tx, ty, tw, th, 0x88FFFFFF);
            for (int line = 0; line < lines.size(); line++)
                g.drawString(this.font, lines.get(line), tx + 5, ty + 5 + line * this.font.lineHeight, 0xFFFFFFFF, false);
            return;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginResourceScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudAccountBox != null
                && this.cloudAccountBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudAccountBox);
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudPasswordBox != null
                && this.cloudPasswordBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudPasswordBox);
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudInstanceNameBox != null
                && this.cloudInstanceNameBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudInstanceNameBox);
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudOriginBox != null
                && this.cloudOriginBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudOriginBox);
            return true;
        }
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT && this.cloudScopeIdBox != null
                && this.cloudScopeIdBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudScopeIdBox);
            return true;
        }
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT && this.cloudScopeNameBox != null
                && this.cloudScopeNameBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudScopeNameBox);
            return true;
        }
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT && this.cloudWorldEpochBox != null
                && this.cloudWorldEpochBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudWorldEpochBox);
            return true;
        }
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) {
            if (this.cloudOriginBox != null && this.cloudOriginBox.mouseClicked(mouseX, mouseY, button)) { setFocused(this.cloudOriginBox); return true; }
            if (this.cloudInstanceNameBox != null && this.cloudInstanceNameBox.mouseClicked(mouseX, mouseY, button)) { setFocused(this.cloudInstanceNameBox); return true; }
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE) {
            if (this.siteEditBox != null && this.siteEditBox.mouseClicked(mouseX, mouseY, button)) {
                setFocused(this.siteEditBox);
                return true;
            }
            if (this.categoryEditBox != null && this.categoryEditBox.mouseClicked(mouseX, mouseY, button)) {
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
        if (this.modelSearchBox != null && this.modelSearchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.modelSearchBox);
            return true;
        }
        if (this.cloudIdentityCodeBox != null && this.cloudIdentityCodeBox.visible
                && inside(mouseX, mouseY, accountPanelLayout().detailX(), accountPanelLayout().detailY(),
                accountPanelLayout().detailWidth(), accountPanelLayout().detailHeight())
                && this.cloudIdentityCodeBox.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.cloudIdentityCodeBox);
            return true;
        }
        if (this.cloudSearchBox != null && this.cloudSearchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.cloudSearchBox);
            return true;
        }
        if (this.resourceSearchBox != null && this.resourceSearchBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.resourceSearchBox);
            return true;
        }
        if (this.siteEditBox != null && this.siteEditBox.mouseClicked(mouseX, mouseY, button)) {
            setFocused(this.siteEditBox);
            return true;
        }
        if (this.categoryEditBox != null && this.categoryEditBox.mouseClicked(mouseX, mouseY, button)) {
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) return true;
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT || STATE.activeTab == ModelPanelState.Tab.INSTANCE) {
            AccountPanelLayout panels = accountPanelLayout();
            if (inside(mouseX, mouseY, panels.detailX(), panels.detailY(), panels.detailWidth(), panels.detailHeight())) {
                cloudDetailScroll += delta * 24;
                cloudDetailLayout();
                if (cloudAccountPage == CloudAccountPage.SCOPES || isCloudIdentityPage()) init();
                return true;
            }
            if (!inside(mouseX, mouseY, panels.listX(), panels.listY(), panels.listWidth(), panels.listHeight())) {
                return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            }
            int count = isCloudIdentityPage() ? cloudIdentityListSize() : cloudAccountPage == CloudAccountPage.SCOPES
                    ? CloudManagementScreen.management().snapshot().scopes().size()
                    : CloudManagementScreen.management().registry().profiles().size();
            cloudAccountScroll = clamp(cloudAccountScroll + delta, 0, Math.max(0, count - panels.listRows()));
            return true;
        }
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
                if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
                    CloudModelPage page = cloudGridPage(modelListW(), currentModelGridH());
                    if (delta > 0 && !page.hasNext() && STATE.cloudHasMore && !STATE.cloudLoading) requestCloudPage(true);
                    else STATE.cloudScroll = clamp(STATE.cloudScroll + delta, 0, page.maxScroll());
                    return true;
                }
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
            case ACCOUNT, INSTANCE -> { }
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
            if (!STATE.modelFilter.matchesAvailability(this.controller.isLocalOnlyModel(modelId))) continue;
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
        if (!STATE.modelFilter.matchesAvailability(this.controller.isLocalOnlyModel(modelId))) {
            return false;
        }
        return switch (STATE.modelFilter) {
            case ALL -> true;
            case AUTH -> auth.contains(modelId) || !assembly.getTextureRegistry().isAuthModel();
            case STAR -> stars.contains(modelId);
            case SERVER_AVAILABLE, LOCAL_ONLY -> true;
        };
    }

    private boolean matchesModelSearch(String modelId, ModelAssembly assembly, String query) {
        if (query.startsWith("@")) {
            Metadata metadata = assembly.getModelData() == null ? null : assembly.getModelData().getExtraInfo();
            return authors(metadata).toLowerCase(Locale.ROOT).contains(query.substring(1));
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

    private void changeCloudVisibility(CloudAssetSummary entry) {
        focusCloudAsset(entry);
        startCloudVisibilityBatch(List.of(entry), entry.isPublic() ? "PRIVATE" : "PUBLIC");
    }

    private void clickCloudAsset(CloudAssetSummary summary) {
        if (!this.controller.cloudAvailable()) {
            setStatus(Component.translatable("gui.sparkle_morpher.cloud.disconnected"), ChatFormatting.YELLOW); return;
        }
        focusCloudAsset(summary);
        if (cloudMultiSelectMode) {
            if (!selectedCloudAssetIds.add(summary.ref().assetId())) selectedCloudAssetIds.remove(summary.ref().assetId());
            return;
        }
        String cloudModelId = this.controller.cloudModelId(summary);
        STATE.selectedModelId = cloudModelId;
        ModelAssembly assembly = this.controller.assemblyOrNull(cloudModelId);
        if (assembly == null) {
            if (!pendingCloudImports.add(cloudModelId)) return;
            setStatus(Component.translatable("gui.sparkle_morpher.cloud.loading"), ChatFormatting.YELLOW);
            this.pendingCloudAsset = summary;
            setStatus(Component.translatable("gui.sparkle_morpher.cloud.loading"), ChatFormatting.YELLOW);
            int generation = this.controller.generation();
            long importGeneration = cloudImportGeneration;
            var expectedRuntime = CloudClientRuntime.state(this.controller.cloudInstanceId());
            this.controller.importCloudAsset(summary, error -> {
                if (generation != this.controller.generation() || importGeneration != cloudImportGeneration) return;
                this.pendingCloudAsset = null;
                pendingCloudImports.remove(cloudModelId);
                if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL || CloudClientRuntime.state(this.controller.cloudInstanceId()) != expectedRuntime) return;
                if (!STATE.selectedModelId.equals(cloudModelId)) return;
                if (error != null && !error.getString().isBlank()) {
                    setStatus(error, ChatFormatting.RED);
                    return;
                }
                this.pendingModelApplyId = cloudModelId;
                setStatus(Component.translatable("gui.sparkle_morpher.cloud.imported", summary.name()), ChatFormatting.GREEN);
            });
            return;
        }
        STATE.selectedTextureId = selectedTextureOrDefault(assembly);
        applyModelAndTexture(cloudModelId, STATE.selectedTextureId, assembly);
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
        if (result == ModernPlayerModelScreenController.ApplyResult.REQUESTED_TARGET) {
            setStatus(Component.translatable("gui.sparkle_morpher.cloud.manage.working"), ChatFormatting.YELLOW);
            return;
        }
        if (result == ModernPlayerModelScreenController.ApplyResult.APPLIED_TO_PLAYER) {
            if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL && this.controller.cloudAvailable()) {
                if (cloudSelectionCandidate != null && modelId.equals(this.controller.cloudModelId(cloudSelectionCandidate))) {
                    this.controller.markCloudApplied(cloudSelectionCandidate);
                } else for (CloudAssetSummary entry : STATE.cloudEntries) {
                    if (modelId.equals(this.controller.cloudModelId(entry))) { this.controller.markCloudApplied(entry); break; }
                }
            }
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.applied_model", modelId), ChatFormatting.GREEN);
        }
    }

    /** §24.7：转发到 Service（保留本方法名，行为不变）。 */
    private void toggleSelectedStar() {
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
            for (CloudAssetSummary entry : STATE.cloudEntries) {
                if (this.controller.cloudAvailable() && this.controller.cloudModelId(entry).equals(STATE.selectedModelId)) {
                    boolean favorite = this.controller.toggleCloudFavorite(entry);
                    setStatus(Component.translatable(favorite ? "gui.sparkle_morpher.model_source.favorite_added" : "gui.sparkle_morpher.model_source.favorite_removed"), ChatFormatting.GREEN);
                    if (STATE.cloudView == ModelPanelState.CloudView.FAVORITES && !favorite) resetCloudPage();
                    return;
                }
            }
            return;
        }
        boolean wasStarred = starModels().contains(STATE.selectedModelId);
        if (this.controller.toggleStar(STATE.selectedModelId)) {
            setStatus(Component.translatable(wasStarred
                    ? "gui.sparkle_morpher.model_source.favorite_removed"
                    : "gui.sparkle_morpher.model_source.favorite_added"), ChatFormatting.GREEN);
        } else {
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.setting.local_save_failed"), ChatFormatting.RED);
        }
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
        setFocused(null);
    }

    private void openFilePicker() {
        Component error = this.controller.pickYsmFile();
        if (error != null) {
            setStatus(error, ChatFormatting.RED);
        }
    }

    private void openModelFolder() {
        try {
            Files.createDirectories(ModelStoragePaths.custom());
            Util.getPlatform().openFile(ModelStoragePaths.custom().toFile());
            setStatus(Component.literal(ModelStoragePaths.custom().toString()), ChatFormatting.GRAY);
        } catch (IOException e) {
            setStatus(Component.translatable("gui.sparkle_morpher.import.error.open_folder", e.getMessage()), ChatFormatting.RED);
        }
    }

    private void openCloudUpload() {
        Collection<String> candidates = this.selectedModelIds.isEmpty() && STATE.selectedModelId != null && !STATE.selectedModelId.isBlank()
                ? List.of(STATE.selectedModelId)
                : new LinkedHashSet<>(this.selectedModelIds);
        List<String> localModels = candidates.stream().filter(ClientModelManager::isLocalOnlyModel).toList();
        Minecraft.getInstance().setScreen(new CloudModelUploadScreen(this, localModels));
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
            if (modelAssembly != null && !modelAssembly.isGltf() && modelAssembly.getModelData() != null
                    && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
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
                com.micaftic.morpher.YesSteveModel.LOGGER.debug("Panel state rejected: {}", errors);
                setStatus(Component.translatable("gui.sparkle_morpher.model_panel.developer.action.apply_state.invalid"), ChatFormatting.RED);
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
        rows.add(actionRow(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.classic_hud_layout", () -> minecraft.setScreen(new HudLayoutScreen(this,
                Component.translatable("gui.sparkle_morpher.classic_hud_layout.title"),
                ExtraPlayerRenderConfig.CLASSIC_HUD_LAYOUT, HudLayoutScreen.classicPreview()))));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.modern_hud_rendering", ExtraPlayerRenderConfig.ENABLE_MODERN_HUD_RENDER));
        rows.add(actionRow(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.modern_hud_layout", () -> minecraft.setScreen(new HudLayoutScreen(this,
                Component.translatable("gui.sparkle_morpher.modern_hud_layout.title"),
                ExtraPlayerRenderConfig.MODERN_HUD_LAYOUT, HudLayoutScreen.modernPreview()))));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_external_fp_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.shader_glow_compatibility", GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK));
        rows.add(bool(ModelPanelState.SettingGroup.RENDERING, "gui.sparkle_morpher.model_panel.setting.disable_face_culling", GeneralConfig.DISABLE_MODEL_FACE_CULLING));
        rows.add(rendererModeRow(ModelPanelState.SettingGroup.PERFORMANCE));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.java_vector_renderer", GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER));
        rows.add(bool(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.lazy_model_loading", GeneralConfig.LAZY_MODEL_LOADING));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.gpu_cache_limit", GeneralConfig.MAX_CACHED_GPU_MODELS, 0, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.cpu_cache_limit", GeneralConfig.MAX_RESIDENT_CPU_MODELS, 1, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.unused_model_ttl", GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 30, 86400, 30, "s"));
        rows.add(localSoundModeRow(ModelPanelState.SettingGroup.GENERAL));
        rows.add(actionRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.model_panel.setting.local_default",this::saveSelectedLocalDefault));
        rows.add(actionRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.model_panel.setting.hide_selected",this::hideSelectedLocalModel));
        rows.add(actionRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.model_panel.setting.restore_hidden",()->editLocalPreferences(com.micaftic.morpher.client.LocalDisplayPreferences::restoreHidden)));
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
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.show_model_id_first", GeneralConfig.SHOW_MODEL_ID_FIRST));
        return rows.stream().filter(row -> row.group() == STATE.settingGroup).toList();
    }

    private SettingRow bool(ModelPanelState.SettingGroup group, String labelKey, ForgeConfigSpec.BooleanValue value) {
        boolean current = safeBool(value);
        return new SettingRow(group, labelKey, current, "", () -> {
            value.set(!safeBool(value));
            value.save();
        }, null, null, null, null);
    }

    private SettingRow invertedBool(ModelPanelState.SettingGroup group, String labelKey, ForgeConfigSpec.BooleanValue value) {
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

    @FunctionalInterface private interface LocalPreferenceEdit{void run()throws java.io.IOException;}
    private void editLocalPreferences(LocalPreferenceEdit edit){
        try{edit.run();setStatus(Component.translatable("gui.sparkle_morpher.model_panel.setting.local_saved"),ChatFormatting.GREEN);}
        catch(java.io.IOException|IllegalArgumentException error){setStatus(Component.translatable("gui.sparkle_morpher.model_panel.setting.local_save_failed"),ChatFormatting.RED);YesSteveModel.LOGGER.warn("[SPM] Local preference edit failed",error);}
    }
    private SettingRow localSoundModeRow(ModelPanelState.SettingGroup group){
        int mode=com.micaftic.morpher.client.LocalDisplayPreferences.snapshot().soundMode();
        String value=Component.translatable("gui.sparkle_morpher.model_panel.setting.local_sound_mode.value."+mode).getString();
        return new SettingRow(group,"gui.sparkle_morpher.model_panel.setting.local_sound_mode",null,value,()->editLocalPreferences(()->{
            com.micaftic.morpher.client.LocalDisplayPreferences.soundMode((mode+1)%3);this.controller.reloadLocalModels(this::setStatus);
        }),null,null,null,null);
    }
    private boolean selectedLocalPreferenceModel(){
        if(STATE.selectedModelId==null||com.micaftic.morpher.core.model.CloudAssetIdentity.isRuntimeModelId(STATE.selectedModelId)||selectedAssembly()==null){
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.setting.select_local_model"),ChatFormatting.YELLOW);return false;
        }return true;
    }
    private void saveSelectedLocalDefault(){
        if(selectedLocalPreferenceModel())editLocalPreferences(()->com.micaftic.morpher.model.LocalModelService.saveDefaultModel(STATE.selectedModelId,selectedTextureOrDefault(selectedAssembly())));
    }
    private void hideSelectedLocalModel(){
        if(selectedLocalPreferenceModel())editLocalPreferences(()->com.micaftic.morpher.client.LocalDisplayPreferences.hide(STATE.selectedModelId));
    }

    private SettingRow section(ModelPanelState.SettingGroup group, String sectionKey) {
        return new SettingRow(group, sectionKey, null, "", null, null, null, null, sectionKey);
    }

    private SettingRow privacyModeRow(ModelPanelState.SettingGroup group) {
        boolean current = PrivacyMode.isConfigured();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.privacy_mode", current, "", () -> {
            boolean enabled = !PrivacyMode.isConfigured();
            GeneralConfig.PRIVACY_MODE.set(enabled);
            GeneralConfig.PRIVACY_MODE.save();
            PrivacyMode.onConfigChanged(enabled);
        }, null, null, null, null);
    }

    private SettingRow intRow(ModelPanelState.SettingGroup group, String labelKey, ForgeConfigSpec.IntValue value, int min, int max, int step, String suffix) {
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

    private SettingRow doubleRow(ModelPanelState.SettingGroup group, String labelKey, ForgeConfigSpec.DoubleValue value, double min, double max, double step, String suffix) {
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
                ), null);
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
        return ClientModelManager.getModelContext(STATE.selectedModelId).orElse(null);
    }

    private ModelRepoEntry selectedResource() {
        return this.controller.selectedResource();
    }

    private String selectedTextureOrDefault(ModelAssembly assembly) {
        List<String> names = assembly.getTextureNames();
        if (STATE.selectedTextureId != null && !STATE.selectedTextureId.isBlank() && names.contains(STATE.selectedTextureId)) {
            return STATE.selectedTextureId;
        }
        return names.isEmpty() ? "" : names.get(0);
    }

    private Set<String> authModels() {
        Set<String> auth = new HashSet<>();
        java.util.stream.Stream.concat(
                        this.controller.availableModelIds().stream(),
                        this.controller.modelAssemblyMap().keySet().stream())
                .filter(this.controller::isAuthModel)
                .forEach(auth::add);
        return Set.copyOf(auth);
    }

    private Set<String> starModels() {
        return LocalStarModelsStore.load();
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
            parts.add(assembly.getTextureNames().size() + " tex");
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

    private void renderChip(GuiGraphics g, int x, int y, int w, Component label, boolean selected, Runnable action) {
        fill(g, x, y, w, 15, selected ? RED_SOFT : 0x55303030);
        drawCentered(g, Component.literal(trim(label.getString(), w - 4)), x + w / 2, y + 4, selected ? 0xFFFFFFFF : MUTED);
        hit(x, y, w, 15, label, action);
    }

    private void renderIconButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, IconGlyph icon, Component tooltip, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, ICON, ICON);
        fill(g, x, y, ICON, ICON, hover ? PANEL_HOVER : 0x66303030);
        border(g, x, y, ICON, ICON, hover ? RED : 0x33FFFFFF);
        drawIcon(g, icon, x + 1, y + 1);
        hit(x, y, ICON, ICON, tooltip, action);
    }

    private void drawIcon(GuiGraphics g, IconGlyph icon, int x, int y) {
        g.blit(MODEL_PANEL_ICONS, x, y, 16, 16, icon.u, icon.v, 16, 16, 128, 64);
    }

    private void renderTextButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        fill(g, x, y, w, h, hover ? PANEL_HOVER : 0x66303030);
        border(g, x, y, w, h, hover ? RED : 0x33FFFFFF);
        drawCentered(g, Component.literal(trim(label.getString(), w - 8)), x + w / 2, y + 5, 0xFFFFFFFF);
        hit(x, y, w, h, label, action);
    }

    private void renderModeButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        boolean hover = inside(mouseX, mouseY, x, y, w, ICON);
        boolean mainland = isMainlandResourceMode();
        fill(g, x, y, w, ICON, hover ? PANEL_HOVER : mainland ? RED_SOFT : 0x66303030);
        border(g, x, y, w, ICON, mainland ? RED : hover ? RED : 0x33FFFFFF);
        drawCentered(g, Component.literal(trim(modeLabel().getString(), w - 8)), x + w / 2, y + 5, 0xFFFFFFFF);
        hit(x, y, w, ICON, modeLabel(), this::toggleResourceMode);
    }

    private void renderRowButton(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, boolean selected, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        fill(g, x, y, w, h, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x55303030);
        g.drawString(this.font, label, x + 5, y + 4, selected ? 0xFFFFFFFF : TEXT, false);
        hit(x, y, w, h, label, action);
    }

    private void drawCentered(GuiGraphics g, Component text, int centerX, int y, int color) {
        g.drawString(this.font, text, centerX - this.font.width(text) / 2, y, color, false);
    }

    private void drawTitle(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.font, text.copy().withStyle(ChatFormatting.BOLD), x, y, TEXT, false);
    }

    private void drawSection(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.font, text.copy().withStyle(ChatFormatting.GRAY), x, y, MUTED, false);
    }

    private void drawText(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.font, text, x, y, TEXT, false);
    }

    private void drawMuted(GuiGraphics g, Component text, int x, int y) {
        g.drawString(this.font, text, x, y, MUTED, false);
    }

    private void fill(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    private void glassPanel(GuiGraphics g, int x, int y, int w, int h) {
        blurGlass(g, x, y, w, h, 0x34F1FBFF, 8.0f);
        fill(g, x, y, w, h, GLASS);
        border(g, x, y, w, h, BORDER);
    }

    private void secondaryGlassPanel(GuiGraphics g, int x, int y, int w, int h) {
        blurGlass(g, x, y, w, h, 0x58F7FBFF, 12.0f);
        fill(g, x, y, w, h, 0xD21F282E);
        border(g, x, y, w, h, 0xC8E4F5FF);
    }

    private void blurGlass(GuiGraphics g, int x, int y, int w, int h, int tint, float radius) {
        if (w <= 0 || h <= 0) {
            return;
        }
        BlurStack.pushBlur(x, y, w, h, 0.0f, radius, tint);
        BlurStack.flush(g);
    }

    private void border(GuiGraphics g, int x, int y, int w, int h, int color) {
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

    private static boolean safeBool(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Exception e) {
            return false;
        }
    }

    private static int safeInt(ForgeConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double safeDouble(ForgeConfigSpec.DoubleValue value, double fallback) {
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
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) return cloudModelViewport().detailHeight();
        return STATE.compactPreviewExpanded ? 112 : 18;
    }

    private void renderCompactDetail(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
            renderCloudCompactDetail(g, mouseX, mouseY, x, y, w, h, partialTick); return;
        }
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
        List<String> textures = assembly.getTextureNames();
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

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, int total, int visible, int scroll) {
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingResourceScroll) {
            updateResourceScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingResourceScroll && button == 0) {
            this.draggingResourceScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    private List<CloudAssetSummary> cloudVisibleEntries() {
        String query = STATE.cloudView == ModelPanelState.CloudView.RECENT || STATE.cloudView == ModelPanelState.CloudView.FAVORITES ? STATE.cloudSearchText : "";
        return CloudModelBrowserFilter.filter(STATE.cloudEntries, query, STATE.cloudView == ModelPanelState.CloudView.PUBLIC ? "PUBLIC" : cloudVisibilityFilter);
    }

    private List<CloudAssetSummary> cloudActionTargets() {
        if (cloudMultiSelectMode) return cloudVisibleEntries().stream().filter(entry -> selectedCloudAssetIds.contains(entry.ref().assetId())).toList();
        CloudAssetSummary focused = focusedCloudAsset();
        return focused == null ? List.of() : List.of(focused);
    }

    private CloudAssetSummary focusedCloudAsset() {
        if (!this.controller.cloudAvailable()) return null;
        for (CloudAssetSummary entry : STATE.cloudEntries) {
            if (entry.ref().assetId().equals(focusedCloudAssetId)) return entry;
            if (focusedCloudAssetId.isEmpty() && this.controller.cloudModelId(entry).equals(STATE.selectedModelId)) return entry;
        }
        return null;
    }

    private void focusCloudAsset(CloudAssetSummary entry) {
        if (!this.controller.cloudAvailable()) return;
        focusedCloudAssetId = entry.ref().assetId();
        cloudSelectionCandidate = entry;
        String modelId = this.controller.cloudModelId(entry);
        if (!STATE.selectedModelId.equals(modelId)) { STATE.selectedModelId = modelId; STATE.selectedTextureId = ""; cloudTexturePage = 0; }
    }

    private void renderCloudSelection(GuiGraphics g, CloudAssetSummary entry, int x, int y) {
        border(g, x, y, 12, 12, BORDER);
        if (selectedCloudAssetIds.contains(entry.ref().assetId())) fill(g, x + 2, y + 2, 8, 8, 0xFF80CFB6);
    }

    private void renderCloudFavoriteControl(GuiGraphics g, CloudAssetSummary entry, int mouseX, int mouseY, int x, int y) {
        boolean favorite = this.controller.isCloudFavorite(entry);
        if (favorite) fill(g, x, y, ICON, ICON, PANEL_ACTIVE);
        renderIconButton(g, mouseX, mouseY, x, y, IconGlyph.STAR,
                Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (favorite ? "unfavorite" : "favorite")), () -> {
                    focusCloudAsset(entry);
                    setCloudFavorites(List.of(entry), !this.controller.isCloudFavorite(entry));
                });
    }

    private void renderCloudActions(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        int bw = Math.max(1, (w - 9) / 4);
        List<CloudAssetSummary> targets = cloudActionTargets();
        Component multi = Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (cloudMultiSelectMode ? "multi_done" : "multi"), selectedCloudAssetIds.size());
        renderTextButton(g, mouseX, mouseY, x, y, bw, 18, multi, () -> { cloudMultiSelectMode = !cloudMultiSelectMode; selectedCloudAssetIds.clear(); });
        boolean allFavorite = !targets.isEmpty() && targets.stream().allMatch(this.controller::isCloudFavorite);
        renderTextButton(g, mouseX, mouseY, x + bw + 3, y, bw, 18,
                Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (allFavorite ? "unfavorite" : "favorite")), () -> setCloudFavorites(cloudActionTargets(), !allFavorite));
        renderTextButton(g, mouseX, mouseY, x + (bw + 3) * 2, y, bw, 18,
                Component.translatable("gui.sparkle_morpher.cloud_upload.make_public"), () -> changeCloudVisibilityBatch("PUBLIC"));
        renderTextButton(g, mouseX, mouseY, x + (bw + 3) * 3, y, w - (bw + 3) * 3, 18,
                Component.translatable("gui.sparkle_morpher.cloud_upload.make_private"), () -> changeCloudVisibilityBatch("PRIVATE"));
        if (cloudPathOffset() < 86) return;
        int rowY = y + 20;
        renderTextButton(g, mouseX, mouseY, x, rowY, bw, 18,
                Component.translatable("gui.sparkle_morpher.model_panel.cloud.select_results"), () -> {
                    cloudMultiSelectMode = true; selectedCloudAssetIds.clear();
                    for (CloudAssetSummary entry : cloudVisibleEntries()) selectedCloudAssetIds.add(entry.ref().assetId());
                });
        renderTextButton(g, mouseX, mouseY, x + bw + 3, rowY, bw, 18,
                Component.translatable("gui.sparkle_morpher.model_panel.clear_selection"), () -> selectedCloudAssetIds.clear());
        Component filter = Component.translatable("gui.sparkle_morpher.model_panel.cloud.filter_" +
                (cloudVisibilityFilter.isEmpty() ? "all" : cloudVisibilityFilter.toLowerCase(Locale.ROOT)));
        renderTextButton(g, mouseX, mouseY, x + (bw + 3) * 2, rowY, bw, 18, filter, () -> {
            if (STATE.cloudView == ModelPanelState.CloudView.PUBLIC) return;
            cloudVisibilityFilter = cloudVisibilityFilter.isEmpty() ? "PRIVATE" : cloudVisibilityFilter.equals("PRIVATE") ? "PUBLIC" : "";
            STATE.cloudScroll = 0; selectedCloudAssetIds.clear();
        });
        renderTextButton(g, mouseX, mouseY, x + (bw + 3) * 3, rowY, w - (bw + 3) * 3, 18,
                Component.translatable("gui.sparkle_morpher.model_panel.upload_cloud"), this::openCloudUpload);
    }

    private void setCloudFavorites(List<CloudAssetSummary> targets, boolean value) {
        if (targets.isEmpty()) { setStatus(Component.translatable("gui.sparkle_morpher.model_panel.select_model"), ChatFormatting.YELLOW); return; }
        try {
            this.controller.setCloudFavorites(targets, value);
            if (STATE.cloudView == ModelPanelState.CloudView.FAVORITES && !value) {
                Set<String> removed = targets.stream().map(entry -> entry.ref().assetId()).collect(java.util.stream.Collectors.toSet());
                STATE.cloudEntries.removeIf(entry -> removed.contains(entry.ref().assetId()));
                selectedCloudAssetIds.removeAll(removed);
            }
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (value ? "favorited_count" : "unfavorited_count"), targets.size()), ChatFormatting.GREEN);
        } catch (RuntimeException error) {
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.cloud.favorite_failed", rootMessage(error)), ChatFormatting.RED);
        }
    }

    private void ensureCloudOwnership() {
        var runtime = CloudClientRuntime.state(this.controller.cloudInstanceId());
        if (runtime == cloudActionsRuntime) return;
        cloudActionsRuntime = runtime;
        cloudImportGeneration++; pendingCloudImports.clear();
        cloudSelectionCandidate = null; this.pendingModelApplyId = null;
        ownedCloudAssetIds.clear(); selectedCloudAssetIds.clear(); pendingVisibilityChanges.clear();
        focusedCloudAssetId = ""; cloudVisibilityBatchRunning = false; cloudTexturePage = 0;
        this.cloudRequestGeneration++; STATE.cloudLoading = false; STATE.cloudLoaded = false;
        if (runtime != null && STATE.modelSource != ModelPanelState.ModelSource.LOCAL) requestCloudOwnership("", runtime);
    }

    private void requestCloudOwnership(String cursor, CloudClientRuntime.RuntimeState expected) {
        if (CloudClientRuntime.state(this.controller.cloudInstanceId()) != expected || cloudActionsRuntime != expected) return;
        int generation = this.controller.generation();
        this.controller.listCloudAssets("mine", "", cursor, 80).whenComplete((page, failure) -> Minecraft.getInstance().execute(() -> {
            if (generation != this.controller.generation() || CloudClientRuntime.state(this.controller.cloudInstanceId()) != expected || cloudActionsRuntime != expected) return;
            if (failure != null) return; // The explicit My Models page remains available for a retry.
            for (CloudAssetSummary entry : page.entries()) ownedCloudAssetIds.add(entry.ref().assetId());
            if (STATE.cloudView == ModelPanelState.CloudView.FAVORITES) {
                List<CloudAssetSummary> favorites = this.controller.favoriteCloudAssets();
                STATE.cloudEntries.replaceAll(entry -> favorites.stream().filter(favorite -> favorite.ref().assetId().equals(entry.ref().assetId())).findFirst().orElse(entry));
            }
            if (page.hasMore() && page.nextCursor() != null && !page.nextCursor().isEmpty() && !page.nextCursor().equals(cursor)) requestCloudOwnership(page.nextCursor(), expected);
        }));
    }

    private void changeCloudVisibilityBatch(String visibility) {
        startCloudVisibilityBatch(cloudActionTargets(), visibility);
    }

    private void runCloudVisibilityBatch(List<CloudAssetSummary> targets, String visibility, int index, int successes, List<String> failures,
                                         CloudClientRuntime.RuntimeState expected, int generation) {
        if (generation != this.controller.generation() || CloudClientRuntime.state(this.controller.cloudInstanceId()) != expected || cloudActionsRuntime != expected) return;
        if (index >= targets.size()) {
            cloudVisibilityBatchRunning = false;
            resetCloudPage();
            Component result = Component.translatable("gui.sparkle_morpher.model_panel.cloud.batch_result", successes, failures.size());
            if (!failures.isEmpty()) result = Component.literal(result.getString() + "\n" + String.join("\n", failures));
            setStatus(result, failures.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED);
            return;
        }
        CloudAssetSummary entry = targets.get(index);
        pendingVisibilityChanges.add(entry.ref().assetId());
        setStatus(Component.translatable("gui.sparkle_morpher.model_panel.cloud.batch_progress", index + 1, targets.size()), ChatFormatting.YELLOW);
        this.controller.setCloudVisibility(entry, visibility).whenComplete((updated, failure) -> Minecraft.getInstance().execute(() -> {
            if (generation != this.controller.generation() || CloudClientRuntime.state(this.controller.cloudInstanceId()) != expected || cloudActionsRuntime != expected) return;
            pendingVisibilityChanges.remove(entry.ref().assetId());
            if (failure != null) failures.add(cloudEntryName(entry) + ": " + rootMessage(failure));
            else STATE.cloudEntries.replaceAll(item -> item.ref().assetId().equals(updated.ref().assetId()) ? updated : item);
            runCloudVisibilityBatch(targets, visibility, index + 1, successes + (failure == null ? 1 : 0), failures, expected, generation);
        }));
    }

    private void renderCloudDetails(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        glassPanel(g, x, y, w, h);
        drawTitle(g, Component.translatable("gui.sparkle_morpher.model_panel.details"), x + 8, y + 8);
        CloudAssetSummary entry = focusedCloudAsset();
        if (entry == null) { drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.select_model"), x + 8, y + 28); return; }
        String modelId = this.controller.cloudModelId(entry);
        ModelAssembly assembly = this.controller.assemblyOrNull(modelId);
        int yy = y + 26;
        drawText(g, Component.literal(trim(cloudEntryName(entry), w - 16)), x + 8, yy); yy += 13;
        drawMuted(g, Component.literal(trim(cloudEntryDetail(entry), w - 16)), x + 8, yy); yy += 13;
        drawMuted(g, Component.literal(trim(this.controller.formatBytes(entry.byteLength()) + " · r" + entry.ref().revision(), w - 16)), x + 8, yy); yy += 15;
        renderTextButton(g, mouseX, mouseY, x + 8, yy, w - 16, 18,
                Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (this.controller.isCloudFavorite(entry) ? "unfavorite" : "favorite")),
                () -> setCloudFavorites(List.of(entry), !this.controller.isCloudFavorite(entry))); yy += 21;
        if (canChangeCloudVisibility(entry)) {
            renderTextButton(g, mouseX, mouseY, x + 8, yy, w - 16, 18, cloudVisibilityAction(entry), () -> changeCloudVisibility(entry)); yy += 21;
        }
        if (assembly == null) {
            renderTextButton(g, mouseX, mouseY, x + 8, yy, w - 16, 18,
                    Component.translatable("gui.sparkle_morpher.model_panel.cloud.download_use"), () -> clickCloudAsset(entry));
            return;
        }
        int previewH = Math.min(98, Math.max(0, h - (yy - y) - 98));
        if (previewH >= 40) {
            renderSelectedModelPreview(g, assembly, modelId, x + 8, yy, w - 16, previewH, mouseX, mouseY, partialTick); yy += previewH + 6;
        }
        Metadata metadata = assembly.getModelData().getExtraInfo();
        if (metadata != null) {
            String authorText = metadata.getAuthors() != null && !metadata.getAuthors().isEmpty() ? authors(metadata) : "";
            String tips = metadata.getTips() == null ? "" : metadata.getTips();
            String info = authorText + (authorText.isBlank() || tips.isBlank() ? "" : "\n") + tips;
            if (!info.isBlank()) {
                drawMuted(g, Component.literal(trim(info.replace('\n', ' '), w - 16)), x + 8, yy);
                hit(x + 8, yy, w - 16, 12, Component.literal(info), () -> {}); yy += 14;
            }
        }
        renderCloudTextures(g, mouseX, mouseY, assembly, modelId, x + 8, yy, w - 16, Math.max(36, y + h - 8 - yy));
    }

    private void renderCloudTextures(GuiGraphics g, int mouseX, int mouseY, ModelAssembly assembly, String modelId, int x, int y, int w, int h) {
        List<String> textures = new ArrayList<>(assembly.getAnimationBundle().getTextures().keySet());
        renderTextButton(g, mouseX, mouseY, x, y - 2, Math.max(36, w - 70), 16,
                Component.translatable("gui.sparkle_morpher.model_panel.cloud.open_textures"), () -> openCloudTexturePicker(assembly, modelId));
        if (textures.isEmpty()) return;
        int rows = Math.max(1, (h - 14) / 16);
        CloudModelPage page = CloudModelPage.cards(textures.size(), rows, cloudTexturePage);
        int pages = page.maxScroll() + 1;
        cloudTexturePage = page.scroll();
        int start = page.start();
        for (int i = start; i < page.end(); i++) {
            String texture = textures.get(i);
            renderRowButton(g, mouseX, mouseY, x, y + 12 + (i - start) * 16, w, 14,
                    Component.literal(trim(texture, w - 10)), texture.equals(selectedTextureOrDefault(assembly)), () -> {
                        focusCloudAssetByModelId(modelId);
                        STATE.selectedTextureId = texture; applySelectedTexture();
                    });
        }
        if (pages > 1) {
            renderTextButton(g, mouseX, mouseY, x + w - 66, y - 2, 18, 16, Component.literal("‹"), () -> cloudTexturePage = Math.max(0, cloudTexturePage - 1));
            drawMuted(g, Component.literal((cloudTexturePage + 1) + "/" + pages), x + w - 43, y + 3);
            renderTextButton(g, mouseX, mouseY, x + w - 18, y - 2, 18, 16, Component.literal("›"), () -> cloudTexturePage = Math.min(pages - 1, cloudTexturePage + 1));
        }
    }

    private void focusCloudAssetByModelId(String modelId) {
        STATE.selectedModelId = modelId;
    }

    private void startCloudVisibilityBatch(List<CloudAssetSummary> targets, String visibility) {
        if (cloudVisibilityBatchRunning || !pendingVisibilityChanges.isEmpty()) return;
        if (targets.isEmpty()) { setStatus(Component.translatable("gui.sparkle_morpher.model_panel.select_model"), ChatFormatting.YELLOW); return; }
        if (targets.stream().anyMatch(entry -> !canChangeCloudVisibility(entry))) {
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.cloud.only_owned"), ChatFormatting.YELLOW); return;
        }
        targets = targets.stream().filter(entry -> entry.isPublic() != visibility.equals("PUBLIC")).toList();
        if (targets.isEmpty()) { setStatus(Component.translatable("gui.sparkle_morpher.model_panel.cloud.visibility_unchanged"), ChatFormatting.GRAY); return; }
        var expected = CloudClientRuntime.state(this.controller.cloudInstanceId());
        if (expected == null) return;
        cloudVisibilityBatchRunning = true;
        runCloudVisibilityBatch(targets, visibility, 0, 0, new ArrayList<>(), expected, this.controller.generation());
    }

    private void renderCloudCompactDetail(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        fill(g, x, y, w, h, GLASS_DARK); border(g, x, y, w, h, 0x33FFFFFF);
        renderIconButton(g, mouseX, mouseY, x + 1, y, STATE.compactPreviewExpanded ? IconGlyph.MINUS : IconGlyph.PLUS,
                Component.translatable("gui.sparkle_morpher.model_panel.details"), () -> STATE.compactPreviewExpanded = !STATE.compactPreviewExpanded);
        CloudAssetSummary entry = focusedCloudAsset();
        if (entry == null) { drawMuted(g, Component.translatable("gui.sparkle_morpher.model_panel.select_model"), x + 22, y + 5); return; }
        String modelId = this.controller.cloudModelId(entry);
        ModelAssembly assembly = this.controller.assemblyOrNull(modelId);
        String label = cloudEntryName(entry) + " · " + (assembly == null ? cloudEntryDetail(entry) : selectedTextureOrDefault(assembly));
        drawText(g, Component.literal(trim(label, w - 48)), x + 22, y + 5);
        renderIconButton(g, mouseX, mouseY, x + w - 19, y, assembly == null ? IconGlyph.INFO : IconGlyph.TEXTURE,
                assembly == null ? cloudEntryTooltip(entry) : Component.translatable("gui.sparkle_morpher.model_panel.cloud.open_textures"), () -> {
                    if (assembly == null) STATE.compactPreviewExpanded = true;
                    else openCloudTexturePicker(assembly, modelId);
                });
        if (!STATE.compactPreviewExpanded || h < 54) return;
        if (assembly == null) {
            renderTextButton(g, mouseX, mouseY, x + 4, y + 24, w - 8, 18,
                    Component.translatable("gui.sparkle_morpher.model_panel.cloud.download_use"), () -> clickCloudAsset(entry)); return;
        }
        int ph = h - 24; int previewW = Math.min(w / 3, Math.max(56, ph));
        renderSelectedModelPreview(g, assembly, modelId, x + 3, y + 21, previewW, ph, mouseX, mouseY, partialTick);
        renderCloudTextures(g, mouseX, mouseY, assembly, modelId, x + previewW + 8, y + 22, w - previewW - 12, ph - 2);
    }

    private int cloudPathOffset() { return this.layout.contentHeight < 185 ? 66 : 86; }
    private CloudModelViewport cloudModelViewport() {
        int gridY = this.layout.contentTop + 8 + cloudPathOffset() + 20;
        int room = this.layout.footerTop - 6 - 28 - 4 - gridY;
        return CloudModelViewport.measure(room, compactModelLayout(), STATE.compactPreviewExpanded);
    }
    private void openCloudTexturePicker(ModelAssembly assembly, String modelId) {
        if (!this.controller.cloudAvailable()) return;
        var expected = CloudClientRuntime.state(this.controller.cloudInstanceId());
        CloudAssetSummary focused = focusedCloudAsset();
        String name = focused != null && this.controller.cloudModelId(focused).equals(modelId) ? cloudEntryName(focused) : displayName(modelId, assembly);
        InputUtil.setScreen(new CloudTextureSelectionScreen(this, name, new ArrayList<>(assembly.getAnimationBundle().getTextures().keySet()),
                selectedTextureOrDefault(assembly), texture -> {
                    if (CloudClientRuntime.state(this.controller.cloudInstanceId()) != expected) return;
                    STATE.selectedModelId = modelId; STATE.selectedTextureId = texture;
                    applySelectedTexture();
                }));
    }

    private Component cloudText(String key) {
        return Component.translatable("gui.sparkle_morpher.cloud.manage." + key);
    }

    private int cloudFormFieldY(int index) {
        return secondaryPanelY() + 64 + index * (secondaryPanelH() < 210 ? 30 : 40);
    }

    private void closeCloudForm() {
        cloudFormGeneration++;
        cloudFormBusy = false;
        if (cloudPasswordBox != null) cloudPasswordBox.setValue("");
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
    }

    private void openCloudLoginForm(boolean register) {
        if (cloudAccountBox != null) cloudAccountDraft = cloudAccountBox.getValue().trim();
        else cloudAccountDraft = CloudManagementScreen.management().registry().selected()
                .map(profile -> CloudManagementScreen.management().accountId(profile.instanceId())).orElse("");
        closeCloudForm();
        registerCloudAccount = register;
        cloudPanelStatus = Component.empty();
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.CLOUD_LOGIN;
        init();
    }

    private void openCloudInstanceForm() {
        closeCloudForm();
        cloudAddressDraft = "";
        cloudNameDraft = "";
        cloudPanelStatus = Component.empty();
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.CLOUD_INSTANCE;
        init();
    }

    private void renderCloudForm(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        boolean login = STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN;
        drawTitle(g, cloudText(login ? (registerCloudAccount ? "register_short" : "login") : "add_instance"), x + 16, y + 12);
        if (login) {
            int tabW = (w - 32) / 2;
            renderCloudBrowserTab(g, mouseX, mouseY, x + 16, y + 30, tabW, cloudText("login"), !registerCloudAccount, () -> { if (!cloudFormBusy) openCloudLoginForm(false); });
            renderCloudBrowserTab(g, mouseX, mouseY, x + 16 + tabW, y + 30, w - 32 - tabW, cloudText("register_short"), registerCloudAccount, () -> { if (!cloudFormBusy) openCloudLoginForm(true); });
            drawSection(g, cloudText("account"), x + 16, cloudFormFieldY(0) - 12);
            drawSection(g, cloudText("password"), x + 16, cloudFormFieldY(1) - 12);
            if (cloudAccountBox != null) cloudAccountBox.render(g, mouseX, mouseY, partialTick);
            if (cloudPasswordBox != null) cloudPasswordBox.render(g, mouseX, mouseY, partialTick);
        } else {
            drawMuted(g, cloudText("instance_intro"), x + 16, y + 32);
            drawSection(g, cloudText("address"), x + 16, cloudFormFieldY(0) - 12);
            drawSection(g, cloudText("optional_name"), x + 16, cloudFormFieldY(1) - 12);
            if (cloudOriginBox != null) cloudOriginBox.render(g, mouseX, mouseY, partialTick);
            if (cloudInstanceNameBox != null) cloudInstanceNameBox.render(g, mouseX, mouseY, partialTick);
        }
        int buttonY = cloudFormFieldY(1) + 26;
        renderTextButton(g, mouseX, mouseY, x + 16, buttonY, w - 32, 22,
                cloudText(cloudFormBusy ? "working" : login ? (registerCloudAccount ? "register_and_login" : "login") : "add_instance"),
                () -> { if (!cloudFormBusy) { if (login) submitCloudAccount(registerCloudAccount); else saveCloudInstance(); } });
        String hint = cloudPanelStatus.getString().isBlank() ? (login ? CloudManagementScreen.text(registerCloudAccount ? "register_hint" : "login_hint") : "https://cloud.example.com") : cloudPanelStatus.getString();
        drawText(g, Component.literal(trim(hint, w - 32)), x + 16, y + h - 14);
        hit(x + 16, y + h - 16, w - 32, 14, Component.literal(hint), () -> { });
    }

    private AccountPanelLayout cloudDetailLayout() {
        var panels = accountPanelLayout();
        int requiredHeight = cloudIdentityContentHeight();
        cloudDetailScroll = clamp(cloudDetailScroll, 0, Math.max(0, requiredHeight - panels.detailHeight()));
        return new AccountPanelLayout(panels.listX(), panels.listY(), panels.listWidth(), panels.listHeight(),
                panels.detailX(), panels.detailY() - cloudDetailScroll, panels.detailWidth(), panels.detailHeight());
    }

    @Override
    public void onClose() {
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE) {
            closeCloudForm();
            init();
            return;
        }
        super.onClose();
    }
}
