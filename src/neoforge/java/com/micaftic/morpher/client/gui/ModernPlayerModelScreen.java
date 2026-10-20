package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.cloud.client.CloudAssetPage;
import com.micaftic.morpher.cloud.client.CloudInstanceRegistry;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.cloud.client.CloudAssetSummary;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.client.renderer.preview.GuiModelRenderer;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.LocalStarModelsStore;
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

import java.nio.file.Path;
import java.security.MessageDigest;
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
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.core.gui.UiElementLayout;
import com.micaftic.morpher.core.gui.RoundedRectGeometry;

public class ModernPlayerModelScreen extends Screen {
    private static final java.util.Map<String, ModelPanelState> STATE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int STATE_CACHE_LIMIT = 64;
    private final ModelPanelState STATE;
    private final com.micaftic.morpher.cloud.client.CloudAssetPageCursorGuard cloudCursorGuard =
            new com.micaftic.morpher.cloud.client.CloudAssetPageCursorGuard();
    private SettingsPanel settingsPanel;
    private final java.util.Map<String,SettingsViewport> settingsViewports=new java.util.HashMap<>();
    private final java.util.List<EditBox> settingsViewportFields=new java.util.ArrayList<>();
    private boolean settingsDrawingBody;
    private int settingsDrawingOffset,settingsContentBottom;
    private String settingsViewportKey(){
        if(STATE.secondaryPanel!=ModelPanelState.SecondaryPanel.NONE)return "form:"+STATE.secondaryPanel+":"+sourceEditMode;
        if(isCloudSettingsPage())return "cloud:"+cloudAccountPage+":"+STATE.selectedCloudInstanceId;
        if(STATE.activeTab==ModelPanelState.Tab.RESOURCE)return "resource:"+resourceRoute;
        return settingsPanel().selectedPage().id()+":"+peerSettingsSection+":"+networkSettingsSection;
    }
    private SettingsViewport settingsViewport(){return settingsViewports.computeIfAbsent(settingsViewportKey(),key->new SettingsViewport());}
    private void settingsExtent(int bottom){if(settingsDrawingBody)settingsContentBottom=Math.max(settingsContentBottom,bottom);}
    private final UiAppearancePreferences appearance=UiAppearancePreferences.current();
    private boolean fontMenuOpen;
    private int fontPage,keyboardHit=-1,textureMenuPage;
    private EditBox peerListenerBox, peerEntranceBox, peerLocalRouteBox;
    private String peerLocalRouteDraft="";
    private String peerListenerDraft = "", peerEntranceDraft = "";
    private EditBox peerEndpointBox;
    private EditBox peerInvitationBox;
    private String peerEndpointDraft = "";
    private String peerInvitationDraft = "";
    private enum PeerSettingsSection { ROOM, SHARING, CONNECTION, PUBLIC, TUNNEL, PROBE }
    private enum NetworkSettingsSection { PRESETS, PRIVACY, STATUS, SERVER }
    private PeerSettingsSection peerSettingsSection = PeerSettingsSection.ROOM;
    private NetworkSettingsSection networkSettingsSection = NetworkSettingsSection.PRESETS;
    private final String stateKeyValue;
    private static final Identifier MODEL_PANEL_ICONS = com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(YesSteveModel.MOD_ID, "texture/ui/lucide.png");
    private static final int BG = 0xC0202020;
    private static final int PANEL = 0x66303030;
    private static final int PANEL_HOVER = 0x88505050;
    private static final int PANEL_ACTIVE = 0x77404040;
    private static final int GLASS = 0x60303030;
    private static final int GLASS_DARK = 0x38222222;
    private static final int BORDER = 0x555F5F5F;
    private static final int RED = 0xFFE05252;
    private static final int RED_SOFT = 0x77E05252;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int ROW = 24;
    private static final int DENSE_MODEL_ROW = 20;
    private static final int MODEL_PAGE_BUTTON_WIDTH = 18;
    private static final int MODEL_PAGE_BUTTON_HEIGHT = 18;
    private static final int ICON = 18;

    // ---- 目录卡片（3D 卡片网格）视觉常量 ----
    private static final int CARD_BASE = 0xB0323232;
    private static final int CARD_BASE_HOVER = 0xD0444444;
    private static final int CARD_SELECT = 0xC03A3A3A;
    private static final int CARD_FOLDER = 0xB0383838;
    private static final int CARD_FOLDER_HOVER = 0xD0484848;
    private static final int CARD_NAME_TEXT = 0xFFF0F0F0;
    private static final int CARD_DIM = 0x9F222222;
    private static final int CARD_NAME_BG = 0xC6000000;

    /**
     * 卡片封面贴图像素尺寸缓存。键为弱引用，贴图回收后随之失效；
     * 仅渲染线程读写，用 synchronized 保护跨线程可见性。
     */
    private static final java.util.Map<AbstractTexture, int[]> COVER_DIMENSIONS = new WeakHashMap<>();
    private static final java.util.Map<AbstractTexture, ModelCardArtwork> CARD_ARTWORK = new WeakHashMap<>();
    private float cardPartialTick;
    private final long previewViewId=VIEW_IDS.incrementAndGet();
    private static final java.util.concurrent.atomic.AtomicLong VIEW_IDS=new java.util.concurrent.atomic.AtomicLong();
    private record CardPreviewKey(long view,int slot,String model,String texture,int assembly,int width,int height,String animation){}
    private final CardPreviewRefreshBudget cardRefreshBudget=new CardPreviewRefreshBudget();
    private long cardFrameNanos;
    private int previewHitX,previewHitY,previewHitW,previewHitH;
    private boolean previewDragging,extractingPage;
    private float detailYaw=176,detailPitch=0,detailZoom=1;
    private List<ModelEntry> frameModelEntries;
    private final NavigationHistory<CloudRoute> cloudHistory=new NavigationHistory<>();
    private record CloudRoute(ModelPanelState.Tab tab,String settingsPage,CloudAccountPage cloudPage,ModelPanelState.SecondaryPanel form,boolean editing,String address,String name){}
    private CloudRoute cloudRoute(){return new CloudRoute(STATE.activeTab,settingsPanel().selectedPage().id(),cloudAccountPage,STATE.secondaryPanel,editingCloudInstance,cloudAddressDraft,cloudNameDraft);}
    private ModelPanelState.Tab cloudReturnTab=ModelPanelState.Tab.MODEL;
    private String cloudReturnSettingsPage="general";
    private String tooltipIdentity="";private long tooltipSince;
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
    private EditBox cloudSearchBox;
    private long cloudRequestGeneration;
    private String cloudPageError="";
    private final Set<String> pendingVisibilityChanges = new HashSet<>();
    private final Set<String> pendingCloudImports = new HashSet<>();
    private final Set<String> selectedCloudAssetIds = new LinkedHashSet<>();
    private final Set<String> ownedCloudAssetIds = new HashSet<>();
    private CloudClientRuntime.RuntimeState cloudActionsRuntime;
    private String focusedCloudAssetId = "";
    private String cloudVisibilityFilter = "";
    private boolean cloudMultiSelectMode;
    private boolean sourceTabMenu;
    private boolean textureMenuOpen,modelMenuOpen;
    private int inlineTexturePage;
    private String textureMenuModel="";
    private String draggingSourceTab;
    private final List<SourceTabHit> sourceTabHits = new ArrayList<>();
    private record SourceTabHit(String instanceId,int x,int y,int w) {}
    private boolean cloudVisibilityBatchRunning;
    private int cloudTexturePage;
    private long cloudImportGeneration;
    private CloudAssetSummary cloudSelectionCandidate;
    private EditBox resourceSearchBox;
    private enum ResourceRoute { CATALOG,DETAIL,QUEUE,ERROR }
    private ResourceRoute resourceRoute=ResourceRoute.CATALOG;
    private com.micaftic.morpher.client.gui.resource.download.DownloadQueue.TaskSnapshot resourceFailure;
    private boolean sourceEditMode;
    private Runnable pendingConfirmation;
    private Component confirmationTitle=Component.empty(),confirmationBody=Component.empty();
    private String sourceNameDraft="";
    private EditBox sourceNameBox;

    private EditBox siteEditBox;
    private EditBox categoryEditBox;
    private EditBox cloudAccountBox;
    private EditBox instanceSearchBox;
    private String instanceSearchDraft="", inspectedInstanceId="";
    private boolean editingCloudInstance;
    private boolean scopeCreateForm;
    private boolean identityProvidersTab;
    private boolean performanceAdvanced, developerStateMenu, developerAdvanced;
    private Component confirmationAction=Component.translatable("gui.sparkle_morpher.model_panel.delete");
    private EditBox cloudPasswordBox;
    private Component cloudPanelStatus = Component.empty();
    private Screen afterCloudLogin;
    private boolean registerCloudAccount;
    private boolean cloudFormBusy;
    private long cloudFormGeneration;
    private String cloudAccountDraft = "";
    private String cloudAddressDraft = "";
    private String cloudNameDraft = "";
    private enum CloudAccountPage { ACCOUNT, INSTANCES, SCOPES, IDENTITY, CLAIM, APPROVAL, PERFORMANCE, OFFLINE }
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
        CHECK(112, 48),
        USER(0, 64), STAR_OFF(16, 64), CLOCK(32, 64), CLOUD(48, 64),
        UPLOAD(64, 64), LOGOUT(80, 64), GLOBE(96, 64), PREVIOUS(112, 64),
        NEXT(0, 80), MORE(16, 80), CIRCLE_CHECK(32,80), CHEVRON_DOWN(48,80), BACK(64,16), EDIT(0,96);

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
        STATE.activeTab = tab;STATE.secondaryPanel=ModelPanelState.SecondaryPanel.NONE;
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

    /** Opens a network page in the same SPM settings shell, without starting any network service. */
    public static ModernPlayerModelScreen networkSettings(String pageId) {
        ModernPlayerModelScreen screen = settings();
        screen.settingsPanel().selectPage(pageId);
        if ("peer_public".equals(pageId)) screen.peerSettingsSection = PeerSettingsSection.PUBLIC;
        if ("peer_tunnel".equals(pageId)) screen.peerSettingsSection = PeerSettingsSection.TUNNEL;
        if ("server".equals(pageId)) screen.networkSettingsSection = NetworkSettingsSection.SERVER;
        return screen;
    }

    public static ModernPlayerModelScreen downloads() {
        ModernPlayerModelScreen screen = new ModernPlayerModelScreen(ModelPanelState.Tab.RESOURCE, null, "resource");
        screen.STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
        screen.resourceRoute=ResourceRoute.QUEUE;
        return screen;
    }

    @Override
    protected void init() {
        if(STATE.modelSource==ModelPanelState.ModelSource.LOCAL&&STATE.selectedModelId.isBlank()&&Minecraft.getInstance().player!=null)PlayerCapability.get(Minecraft.getInstance().player).ifPresent(cap->{STATE.selectedModelId=cap.getModelId();STATE.selectedTextureId=cap.getCurrentTextureName();});
        normalizeCloudSettingsEntry();
        if(STATE.cloudView==ModelPanelState.CloudView.PUBLIC) STATE.cloudView=ModelPanelState.CloudView.ALL;
        clearWidgets();tooltipIdentity="";previewDragging=false;
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT && (cloudAccountPage == CloudAccountPage.INSTANCES || cloudAccountPage == CloudAccountPage.SCOPES)) cloudAccountPage = CloudAccountPage.ACCOUNT;
        else if (STATE.activeTab == ModelPanelState.Tab.INSTANCE && cloudAccountPage == CloudAccountPage.ACCOUNT) cloudAccountPage = CloudAccountPage.INSTANCES;
        this.layout = ModelPanelLayout.create(this.width, this.height);
        this.modelSearchBox = null;
        this.cloudSearchBox = null;
        this.resourceSearchBox = null;
        this.siteEditBox = null;
        this.categoryEditBox = null;
        this.cloudAccountBox = null;
        this.instanceSearchBox=null;
        this.cloudPasswordBox = null;
        this.cloudInstanceNameBox = null;
        this.cloudOriginBox = null;
        this.cloudScopeIdBox = null;
        this.cloudScopeNameBox = null;
        this.cloudWorldEpochBox = null;
        this.cloudIdentityCodeBox = null;
        this.peerListenerBox = this.peerEntranceBox = this.peerLocalRouteBox = null;
        this.peerEndpointBox = null;
        this.peerInvitationBox = null;
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
        if(isCloudSettingsPage()&&cloudAccountPage==CloudAccountPage.INSTANCES){
            var rect=FinalUiLayout.scene("instances",this.width,this.height).control(0);
            instanceSearchBox=new EditBox(this.font,rect.x()+6,rect.y()+6,rect.w()-12,12,cloudText("instances"));
            instanceSearchBox.setBordered(false);instanceSearchBox.setValue(instanceSearchDraft);instanceSearchBox.setHint(Component.translatable("gui.sparkle_morpher.ui.search_instances"));
            instanceSearchBox.setResponder(v->{instanceSearchDraft=v;cloudAccountScroll=0;});addWidget(instanceSearchBox);
        }
        boolean cloudFormOpen = STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE;
        if (!cloudFormOpen && STATE.activeTab == ModelPanelState.Tab.MODEL) {
            if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
                this.modelSearchBox = new EditBox(this.font, modelListX()+7, this.layout.top+38, compactLocalSearchWidth()-14, 12, Component.translatable("gui.sparkle_morpher.resource_station.search"));
                this.modelSearchBox.setMaxLength(256);
                this.modelSearchBox.setValue(STATE.modelSearchText);
                this.modelSearchBox.setBordered(false);
                this.modelSearchBox.setHint(Component.translatable("gui.sparkle_morpher.ui.search_models"));
                this.modelSearchBox.setTextColor(TEXT);
                addWidget(this.modelSearchBox);
            } else {
                this.cloudSearchBox = new EditBox(this.font, modelListX()+7, this.layout.top+38, compactCloudSearchWidth()-14, 12, Component.translatable("gui.sparkle_morpher.model_source.search"));
                this.cloudSearchBox.setMaxLength(128);
                this.cloudSearchBox.setValue(STATE.cloudSearchText);
                this.cloudSearchBox.setBordered(false);
                this.cloudSearchBox.setHint(Component.translatable("gui.sparkle_morpher.ui.search_models"));
                this.cloudSearchBox.setTextColor(TEXT);
                addWidget(this.cloudSearchBox);
                ensureCloudPageLoaded();
            }
        } else if (!cloudFormOpen && STATE.activeTab == ModelPanelState.Tab.RESOURCE) {
            this.resourceSearchBox = new EditBox(this.font, this.layout.contentLeft+7, this.layout.top+76, Math.min(320,this.layout.contentWidth/2)-14, 12, Component.translatable("gui.sparkle_morpher.resource_station.search"));
            this.resourceSearchBox.setBordered(false);
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
            this.siteEditBox = new EditBox(this.font, panelX + 22, panelY + 237, Math.min(530,panelW-32)-12, 12, Component.translatable("gui.sparkle_morpher.model_panel.url"));
            this.siteEditBox.setBordered(false);
            this.sourceNameBox=new EditBox(this.font,panelX+22,panelY+85,Math.min(530,panelW-32)-12,12,Component.literal("名称"));sourceNameBox.setBordered(false);sourceNameBox.setValue(sourceNameDraft);sourceNameBox.setResponder(v->sourceNameDraft=v);addWidget(sourceNameBox);
            this.siteEditBox.setMaxLength(2048);
            this.siteEditBox.setValue(STATE.siteEditText.isBlank() ? this.controller.selectedSite() : STATE.siteEditText);
            this.siteEditBox.setTextColor(TEXT);
            addWidget(this.siteEditBox);
        } else if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CATEGORIES) {
            int panelX = secondaryPanelX();
            int panelY = secondaryPanelY();
            int panelW = secondaryPanelW();
            this.categoryEditBox = new EditBox(this.font, panelX+6, panelY+68, Math.min(260,panelW-32)-12, 12, Component.translatable("gui.sparkle_morpher.model_panel.category"));
            this.categoryEditBox.setBordered(false);
            this.categoryEditBox.setMaxLength(160);
            this.categoryEditBox.setValue(STATE.categoryEditText);
            this.categoryEditBox.setTextColor(TEXT);
            addWidget(this.categoryEditBox);
        } else if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN) {
            int x = secondaryPanelX() + 16;
            int w = Math.min(530,secondaryPanelW()-32);
            this.cloudAccountBox = cloudField(x, cloudFormFieldY(0), w, "account", 128);
            this.cloudAccountBox.setValue(cloudAccountDraft);
            this.cloudAccountBox.setResponder(value -> cloudAccountDraft = value);
            this.cloudPasswordBox = cloudField(x, cloudFormFieldY(1), w, "password", 1024);
            CloudManagementScreen.maskPassword(this.cloudPasswordBox);
            setInitialFocus(this.cloudAccountBox);
        } else if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) {
            int x = secondaryPanelX() + 16;
            int w = Math.min(530,secondaryPanelW()-32);
            this.cloudOriginBox = cloudField(x, cloudFormFieldY(0), w, "address", 2048);
            this.cloudOriginBox.setValue(cloudAddressDraft);
            this.cloudOriginBox.setResponder(value -> cloudAddressDraft = value);
            this.cloudInstanceNameBox = cloudField(x, cloudFormFieldY(1), w, "optional_name", 128);
            this.cloudInstanceNameBox.setValue(cloudNameDraft);
            this.cloudInstanceNameBox.setResponder(value -> cloudNameDraft = value);
            setInitialFocus(this.cloudOriginBox);
        } else if (isCloudSettingsPage() && cloudAccountPage == CloudAccountPage.SCOPES) {
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
        if (STATE.activeTab == ModelPanelState.Tab.SETTINGS
                && settingsPanel().selectedPage().kind() == SettingsPageRegistry.Kind.PEER_LAN) {
            var columns = settingsColumns();
            int fieldWidth = Math.max(24, SettingsPanelLayout.sections(columns).detailsWidth() - 116);
            if (peerEndpointDraft.isBlank()) peerEndpointDraft = com.micaftic.morpher.client.peer.ClientPeerRuntime.suggestedEndpoint();
            peerEndpointBox = peerField(fieldWidth, "endpoint", 300);
            peerEndpointBox.setValue(peerEndpointDraft);
            peerEndpointBox.setResponder(value -> peerEndpointDraft = value);
            peerInvitationBox = peerField(fieldWidth, "invitation", 8192);
            peerInvitationBox.setValue(peerInvitationDraft);
            peerInvitationBox.setResponder(value -> peerInvitationDraft = value);
            if(peerListenerDraft.isBlank())peerListenerDraft=com.micaftic.morpher.client.peer.ClientPeerRuntime.configuredListeners();
            if(peerEntranceDraft.isBlank())peerEntranceDraft=com.micaftic.morpher.client.peer.ClientPeerRuntime.configuredEntrances();
            peerListenerBox=peerField(fieldWidth,"listeners",600);peerListenerBox.setValue(peerListenerDraft);peerListenerBox.setResponder(value->peerListenerDraft=value);
            peerEntranceBox=peerField(fieldWidth,"entrances",2400);peerEntranceBox.setValue(peerEntranceDraft);peerEntranceBox.setResponder(value->peerEntranceDraft=value);
            peerLocalRouteBox=peerField(fieldWidth,"local_route",2400);peerLocalRouteBox.setValue(peerLocalRouteDraft);peerLocalRouteBox.setResponder(value->peerLocalRouteDraft=value);
        }
    }


    private Component cloudText(String key) {
        return Component.translatable("gui.sparkle_morpher.cloud.manage." + key);
    }

    private int cloudFormFieldY(int index){return secondaryPanelY()+(width<560?56:width<800?64:79)+index*(width<560?42:width<800?58:76);}

    private void closeCloudForm() {
        cloudFormGeneration++;
        cloudFormBusy = false;
        if (cloudPasswordBox != null) cloudPasswordBox.setValue("");
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.NONE;
    }

    private void openCloudLoginForm(boolean register) {
        if(STATE.secondaryPanel!=ModelPanelState.SecondaryPanel.CLOUD_LOGIN)cloudHistory.push(cloudRoute());
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
        cloudHistory.push(cloudRoute());
        closeCloudForm();
        editingCloudInstance=false;
        cloudAddressDraft = "";
        cloudNameDraft = "";
        cloudPanelStatus = Component.empty();
        STATE.secondaryPanel = ModelPanelState.SecondaryPanel.CLOUD_INSTANCE;
        init();
    }

    private void renderCloudForm(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,float partialTick){
        boolean login=STATE.secondaryPanel==ModelPanelState.SecondaryPanel.CLOUD_LOGIN;
        pageHeader(g,mx,my,editingCloudInstance&&!login?Component.literal("实例设置"):cloudText(login?(registerCloudAccount?"register_short":"login"):"add_instance"),Component.empty(),this::backFromCloud);
        int bh=uiButtonHeight(),fx=x+8,fw=Math.min(530,w-16);
        drawSection(g,cloudText(login?"account":editingCloudInstance?"optional_name":"address"),fx,cloudFormFieldY(0)-13);
        drawSection(g,cloudText(login?"password":editingCloudInstance?"address":"optional_name"),fx,cloudFormFieldY(1)-13);
        EditBox first=login?cloudAccountBox:editingCloudInstance?cloudInstanceNameBox:cloudOriginBox,second=login?cloudPasswordBox:editingCloudInstance?cloudOriginBox:cloudInstanceNameBox;
        renderReferenceField(g,mx,my,partialTick,first,fx,cloudFormFieldY(0),fw,bh);
        renderReferenceField(g,mx,my,partialTick,second,fx,cloudFormFieldY(1),fw,bh);
        int afterFields=cloudFormFieldY(1)+bh+12;
        if(login){
            renderTextButton(g,mx,my,fx,afterFields,Math.min(160,fw),bh,cloudText(registerCloudAccount?"login":"register_short"),()->openCloudLoginForm(!registerCloudAccount));
        }
        if(editingCloudInstance&&!login){
            var profile=CloudManagementScreen.management().registry().find(inspectedInstanceId);
            String[] labels={"发现的实例 ID","连接状态","世界与访问范围"};String[] values={profile.map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse(""),profile.map(p->CloudClientRuntime.state(p.instanceId())==null?"未登录":"已登录").orElse("未登录"),"打开 ›"};
            int stride=width<560?26:width<800?34:47,valueW=Math.min(150,fw/2);
            for(int i=0;i<3;i++){int yy=afterFields+i*stride;
                drawText(g,Component.literal(labels[i]),fx,UiElementLayout.centeredY(yy,bh,font.lineHeight));
                if(i==2)renderTextButton(g,mx,my,fx+fw-valueW,yy,valueW,bh,Component.literal(values[i]),()->{setCloudAccountPage(CloudAccountPage.SCOPES);refreshCloudScopes();});
                else drawMuted(g,Component.literal(trim(values[i],valueW-8)),fx+fw-valueW,UiElementLayout.centeredY(yy,bh,font.lineHeight));
            }
        }
        int by=this.height-this.layout.top-bh-10;
        if(!cloudPanelStatus.getString().isBlank())drawMuted(g,Component.literal(trim(cloudPanelStatus.getString(),w-16)),fx,by-16);
        renderTextButton(g,mx,my,x+w-192,by,76,bh,Component.translatable("gui.cancel"),this::backFromCloud);
        renderPrimaryButton(g,mx,my,x+w-108,by,100,bh,cloudText(cloudFormBusy?"working":login?(registerCloudAccount?"register_and_login":"login"):editingCloudInstance?"check_save":"add_instance"),()->{if(!cloudFormBusy){if(login)submitCloudAccount(registerCloudAccount);else saveCloudInstance();}});
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

    private AccountPanelLayout accountPanelLayout(){
        int x=this.layout.contentLeft,y=this.layout.top+62,w=this.layout.contentWidth,h=this.layout.height-100;
        if(cloudAccountPage==CloudAccountPage.ACCOUNT||cloudAccountPage==CloudAccountPage.PERFORMANCE)return new AccountPanelLayout(x,y,0,0,x,y,w,h);
        if(cloudAccountPage==CloudAccountPage.INSTANCES){var a=FinalUiLayout.scene("instances",this.width,this.height);var l=a.area("listRect");var d=a.area("detailRect");return new AccountPanelLayout(l.x(),l.y(),l.w(),h,d.x(),d.y(),d.w(),h);}
        int lw=Math.min(240,w/3);return new AccountPanelLayout(x,y,lw,h,x+lw+12,y,w-lw-12,h);
    }

    private AccountPanelLayout cloudDetailLayout() {
        var panels = accountPanelLayout();
        int requiredHeight = cloudIdentityContentHeight();
        cloudDetailScroll = clamp(cloudDetailScroll, 0, Math.max(0, requiredHeight - panels.detailHeight()));
        return new AccountPanelLayout(panels.listX(), panels.listY(), panels.listWidth(), panels.listHeight(),
                panels.detailX(), panels.detailY() - cloudDetailScroll, panels.detailWidth(), panels.detailHeight());
    }

    @Override
    public void removed() {
        peerInvitationDraft = "";
        if (peerInvitationBox != null) peerInvitationBox.setValue("");
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
        GuiModelRenderer.clearCardPreviews();
        super.removed();
    }

    @Override
    public void onClose() {
        if(pendingConfirmation!=null){pendingConfirmation=null;return;}
        if(fontMenuOpen){fontMenuOpen=false;return;}
        if(textureMenuOpen){textureMenuOpen=false;return;}
        if(modelMenuOpen){modelMenuOpen=false;return;}
        if(sourceTabMenu){sourceTabMenu=false;return;}
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE || isCloudSettingsPage()) {backFromCloud();return;}
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE) {
            closeCloudForm();
            init();
            return;
        }
        if (this.parentScreen != null && this.minecraft != null) {
            InputUtil.setScreen(this.parentScreen);
        } else {
            super.onClose();
        }
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
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        GuiModelRenderer.beginCardPreviewFrame();
        this.hits.clear();frameModelEntries=null;extractingPage=true;previewHitW=0;cardPartialTick=partialTick;
        for(var child:children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget widget){widget.visible=false;widget.active=false;}
        try {
        boolean modal = STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE;
        int mainMouseX = modal ? -1 : mouseX;
        int mainMouseY = modal ? -1 : mouseY;
        com.micaftic.morpher.core.gui.RoulettePanelStyle.glassPanel(g,this.layout.left,this.layout.top,this.layout.width,this.layout.height);
        if(!modal)renderTabs(g, mainMouseX, mainMouseY);

        if (!modal) switch (STATE.activeTab) {
            case MODEL -> renderModelTab(g, mainMouseX, mainMouseY, partialTick);
            case RESOURCE -> renderResourceTab(g, mainMouseX, mainMouseY, partialTick);
            case ACCOUNT, INSTANCE -> renderCloudAccountPanel(g, mainMouseX, mainMouseY, this.layout.contentLeft, this.layout.contentTop, this.layout.contentWidth, this.layout.contentHeight, partialTick);
            case SETTINGS -> renderSettingsTab(g, mainMouseX, mainMouseY, partialTick);
        }
        if(!modal)renderFooter(g);
        renderSecondaryPanel(g, mouseX, mouseY, partialTick);
        if(keyboardHit>=0&&keyboardHit<hits.size()){Hit focus=hits.get(keyboardHit);border(g,focus.x()-1,focus.y()-1,focus.w()+2,focus.h()+2,RED);}
        renderConfirmation(g,mouseX,mouseY);
        renderTooltip(g, mouseX, mouseY);
        } finally {extractingPage=false;}
    }

    private void renderTabs(GuiGraphicsExtractor g,int mx,int my){
        int p=this.layout.left,side=this.layout.railWidth,logoSize=this.width<560?20:this.width<800?22:26;
        roundedFill(g,p,p,side,this.layout.height,5,0x59080808);
        fill(g,p+side,p,1,this.layout.height,0x1AFFFFFF);
        com.micaftic.morpher.core.gui.RoulettePanelStyle.applicationLogo(g,p+(side-logoSize)/2,p+6,logoSize);
        int y=p+logoSize+17,x=p+3,w=side-6;
        if(!this.stateKeyValue.startsWith("fake:"))renderVerticalTab(g,mx,my,ModelPanelState.Tab.MODEL,x,y,w,24,IconGlyph.MODEL,Component.translatable("gui.sparkle_morpher.model_panel.model"));
        if(this.stateKeyValue.startsWith("fake:")){drawIconScaled(g,IconGlyph.MODEL,p+(side-12)/2,y+6,12);hit(x,y,w,24,Component.translatable("gui.sparkle_morpher.model_panel.model"),()->InputUtil.setScreen(new ModernPlayerModelScreen()));}
        renderVerticalTab(g,mx,my,ModelPanelState.Tab.RESOURCE,x,y+30,w,24,IconGlyph.RESOURCE,Component.translatable("gui.sparkle_morpher.resource_station.title"));
        if(this.stateKeyValue.startsWith("fake:")){roundedFill(g,x,y+60,w,24,3,0x18FFFFFF);fill(g,p,y+65,2,14,RED);}
        drawIconScaled(g,IconGlyph.USER,p+(side-12)/2,y+66,12);
        hit(x,y+60,w,24,Component.translatable("gui.sparkle_morpher.ui.fake_players"),FakePlayerManagerScreen::open);
        renderVerticalTab(g,mx,my,ModelPanelState.Tab.SETTINGS,x,y+90,w,24,IconGlyph.SETTINGS,Component.translatable("gui.sparkle_morpher.model_panel.settings"));
        drawIconScaled(g,IconGlyph.LOGOUT,p+(side-12)/2,this.height-p-24,12);
        hit(x,this.height-p-28,w,23,Component.translatable("gui.back"),this::onClose);
    }



    private void renderVerticalTab(GuiGraphicsExtractor g,int mx,int my,ModelPanelState.Tab tab,int x,int y,int w,int h,IconGlyph icon,Component label){
        boolean selected=STATE.activeTab==tab;
        if(selected||inside(mx,my,x,y,w,h))roundedSurface(g,x,y,w,h,3,0x18FFFFFF,0x00FFFFFF);
        if(selected)fill(g,this.layout.left,y+4,2,h-8,RED);
        drawIconScaled(g,icon,x+(w-12)/2,y+(h-12)/2,12);
        hit(x,y,w,h,label,()->switchTab(tab));
    }

    private void switchTab(ModelPanelState.Tab tab) {
        if (tab == ModelPanelState.Tab.ACCOUNT || tab == ModelPanelState.Tab.INSTANCE) {
            openCloudSettings(tab == ModelPanelState.Tab.ACCOUNT ? CloudAccountPage.ACCOUNT : CloudAccountPage.INSTANCES);
            return;
        }
        if (STATE.activeTab != tab) {
            cloudHistory.clear();
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

    private boolean compactModelLayout() { return false; }

    private int modelLeftW() {
        return 0;
    }

    private int modelRightW(){return Math.max(24,this.layout.contentWidth-modelDetailListW()-4);}

    private int modelListX(){return this.layout.contentLeft;}

    private int modelListW(){return ModelPickerLayout.listWidth(this.layout.contentWidth,currentModelGridH());}

    private int modelDetailListW(){
        int w=modelListW(),h=currentModelGridH();
        return gridMode(w,h)==ModelPickerLayout.GridMode.CARDS?ModelPickerLayout.cards(w,h).blockW():w;
    }

    private int modelDetailHeight(){
        int w=modelListW(),h=currentModelGridH();
        return gridMode(w,h)==ModelPickerLayout.GridMode.CARDS?ModelPickerLayout.cards(w,h).blockH():h;
    }

    private int modelListMinW() {
        return this.layout.contentWidth < 420 ? 54 : 90;
    }

    private boolean denseModelToolbar() {
        return compactModelLayout() && modelListW() >= 400
                && (this.layout.contentWidth <= 640 || this.layout.contentHeight <= 360);
    }

    private int compactLocalSearchWidth() { return toolbarSearchWidth(); }



    private boolean compactCloudActionsInSearchRow() {
        return STATE.modelSource != ModelPanelState.ModelSource.LOCAL && modelListW() >= 260;
    }

    private int compactCloudSearchWidth() { return toolbarSearchWidth(); }

    private boolean hasSelectedModel() {
        return STATE.selectedModelId != null && !STATE.selectedModelId.isBlank();
    }







    private int resourceListX(){return this.layout.contentLeft;}



    private int resourceListW(){return this.layout.contentWidth;}







    private int secondaryPanelW(){return this.layout.contentWidth;}

    private int secondaryPanelH(){return this.layout.height;}

    private int secondaryPanelX(){return this.layout.contentLeft;}

    private int secondaryPanelY(){return this.layout.top;}

    private void renderModelTab(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = modelListX(), y = this.layout.top + 4, w = modelListW();
        int detailX = x + modelDetailListW() + 4, gridY = modelGridTop(), gridH = currentModelGridH();
        renderModelSourceTabs(g, mouseX, mouseY, x, y, this.layout.contentWidth - 4);
        roundedSurface(g,x,this.layout.top+32,toolbarSearchWidth(),this.width<560?18:22,3,0x0CFFFFFF,0x22FFFFFF);
        if (modelSearchBox != null) {modelSearchBox.visible=true;modelSearchBox.active=true;modelSearchBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);}
        if (cloudSearchBox != null) {cloudSearchBox.visible=true;cloudSearchBox.active=true;cloudSearchBox.extractWidgetRenderState(g, mouseX, mouseY, partialTick);}
        int tx = x + toolbarSearchWidth() + 6, ty = this.layout.top + 32;
        if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) {
            this.controller.requestCloudPreviews(List.of());
            renderLocalToolbar(g, mouseX, mouseY, tx, ty);
            if (!STATE.currentPath.isBlank()) {
                hit(x, y, 34, 18, Component.literal("/" + STATE.currentPath), this::navigateUp);
            }
            renderModelGrid(g, mouseX, mouseY, x, gridY, w, gridH);
            renderModelDetails(g, mouseX, mouseY, detailX, gridY, modelRightW(), modelDetailHeight(), partialTick);
        } else {
            renderCloudToolbar(g, mouseX, mouseY, tx, ty);
            renderCloudGrid(g, mouseX, mouseY, x, gridY, w, gridH);
            renderCloudDetails(g, mouseX, mouseY, detailX, gridY, modelRightW(), modelDetailHeight(), partialTick);
        }
        renderModelBottomActions(g, mouseX, mouseY, x, this.height-this.layout.top-(this.width<560?18:this.width<800?20:22)-8, w, gridH);
        boolean appliedNow = Minecraft.getInstance().player != null && PlayerCapability.get(Minecraft.getInstance().player)
                .map(cap -> cap.getModelId().equals(STATE.selectedModelId)).orElse(false);
        Component applied = Component.translatable(appliedNow ? "gui.sparkle_morpher.ui.current_applied" : "gui.sparkle_morpher.ui.preview");
        drawMuted(g, Component.literal(trim(applied.getString(), modelRightW())), detailX, this.height-this.layout.top-22);
        renderSourceTabMenu(g,mouseX,mouseY);
        renderModelMoreMenu(g,mouseX,mouseY);
    }

    private void renderModelSourceTabs(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w){
        var profiles=CloudManagementScreen.management().registry().profiles();
        for(var profile:profiles)if(!STATE.cloudTabOrder.contains(profile.instanceId()))STATE.cloudTabOrder.add(profile.instanceId());
        var open=profiles.stream().filter(p->!STATE.closedCloudTabs.contains(p.instanceId()))
                .sorted(java.util.Comparator.comparingInt(p->STATE.cloudTabOrder.indexOf(p.instanceId()))).toList();
        sourceTabHits.clear();
        int localW=this.width<560?37:49,addW=24,available=Math.max(1,w-localW-addW);
        int visible=Math.max(1,available/80),navW=open.size()>visible?36:0;
        int tabW=Math.max(40,Math.min(this.width<560?89:103,(available-navW)/Math.max(1,Math.min(visible,open.size()))));
        visible=Math.max(1,(available-navW)/tabW);
        STATE.cloudTabOffset=clamp(STATE.cloudTabOffset,0,Math.max(0,open.size()-visible));
        renderCloudBrowserTab(g,mx,my,x,y,localW,Component.translatable("gui.sparkle_morpher.model_source.local"),STATE.modelSource==ModelPanelState.ModelSource.LOCAL,()->switchModelSource(ModelPanelState.ModelSource.LOCAL));
        int xx=x+localW+3;
        for(int i=STATE.cloudTabOffset;i<Math.min(open.size(),STATE.cloudTabOffset+visible);i++){
            var profile=open.get(i);int bx=xx;
            boolean selected=STATE.modelSource!=ModelPanelState.ModelSource.LOCAL&&profile.instanceId().equals(STATE.selectedCloudInstanceId);
            renderCloudBrowserTab(g,mx,my,bx,y,tabW,Component.literal(CloudManagementScreen.displayName(profile)),selected,()->{
                draggingSourceTab=profile.instanceId();switchCloudInstance(profile);
                if(CloudClientRuntime.state(profile.instanceId())==null)openCloudAccount();
            });
            sourceTabHits.add(new SourceTabHit(profile.instanceId(),bx,y,tabW));xx+=tabW+3;
        }
        if(navW>0){
            int count=visible;
            renderTextButton(g,mx,my,xx,y,18,18,Component.literal("‹"),()->STATE.cloudTabOffset=Math.max(0,STATE.cloudTabOffset-1));
            renderTextButton(g,mx,my,xx+18,y,18,18,Component.literal("›"),()->STATE.cloudTabOffset=Math.min(Math.max(0,open.size()-count),STATE.cloudTabOffset+1));xx+=36;
        }
        renderIconButton(g,mx,my,xx+2,y+1,IconGlyph.PLUS,Component.translatable("gui.sparkle_morpher.ui.open_tab"),()->sourceTabMenu=!sourceTabMenu);
        if(stateKeyValue.startsWith("fake:")&&x+w-xx>190){
            String target=com.micaftic.morpher.fakeplayer.FakePlayerListCache.entries().stream().filter(e->stateKeyValue.equals("fake:"+e.uuid())).map(com.micaftic.morpher.fakeplayer.FakePlayerListEntry::displayName).findFirst().orElse("目标假人");
            drawMuted(g,Component.literal(trim("为"+target+"选择外观",x+w-xx-36)),xx+36,y+8);
        }
    }

    private void renderCloudBrowserTab(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,Component label,boolean selected,Runnable action){
        roundedSurface(g,x,y,w,uiButtonHeight(),4,selected?0x16FFFFFF:inside(mx,my,x,y,w,uiButtonHeight())?0x10FFFFFF:0x06FFFFFF,selected?0x22FFFFFF:0x0EFFFFFF);
        drawText(g,Component.literal(trim(label.getString(),w-12)),x+6,UiElementLayout.centeredY(y,uiButtonHeight(),font.lineHeight));
        hit(x,y,w,uiButtonHeight(),label,action);
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
        this.controller.retryCloudPreviews();
        this.cloudRequestGeneration++;
        this.cloudCursorGuard.reset();
        selectedCloudAssetIds.clear();
        STATE.cloudEntries.clear();
        STATE.cloudScroll = 0;
        STATE.cloudCursor = "";
        STATE.cloudPages = new com.micaftic.morpher.cloud.client.CloudCatalogCursorHistory();
        STATE.cloudBatchSize = 0;
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
            STATE.cloudEntries.addAll(STATE.cloudView == ModelPanelState.CloudView.RECENT ? this.controller.recentCloudAssets() : this.controller.favoriteCloudAssets());
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

    private void requestCloudPage(boolean next) {
        requestCloudBatch(next ? STATE.cloudPages.next() : "");
    }

    private void requestCloudBatch(String cursor) {
        if (STATE.cloudLoading || cursor == null || this.layout == null) return;
        String key = cloudPageKey();
        long generation = ++this.cloudRequestGeneration;
        String query = STATE.cloudSearchText.trim();
        String scope = STATE.cloudCatalogScope();
        if (STATE.cloudBatchSize == 0) {
            int w = modelListW(), h = cloudModelViewport().gridHeight();
            if (gridMode(w, h) == ModelPickerLayout.GridMode.CARDS)
                STATE.cloudBatchSize = Math.min(80, ModelPickerLayout.cards(w, h, 0, denseModelToolbar()).capacity());
            else {
                ModelListMetrics metrics = modelListMetrics(w, h, 0);
                STATE.cloudBatchSize = Math.min(80, metrics.cols() * metrics.rows());
            }
            STATE.cloudBatchSize = Math.max(1, STATE.cloudBatchSize);
        }
        STATE.cloudLoading = true;
        cloudPageError="";
        this.controller.listCloudAssets(scope, query, cursor, STATE.cloudBatchSize).whenComplete((page, failure) -> Minecraft.getInstance().execute(() -> {
            if (generation != this.cloudRequestGeneration || !key.equals(cloudPageKey())) return;
            STATE.cloudLoading = false;
            if (failure != null) {
                // Keep the current page and cursor; a failed next page can be retried.
                STATE.cloudLoaded = true;
                STATE.cloudLoadedKey = key;
                cloudPageError=rootMessage(failure);
                setStatus(Component.translatable("gui.sparkle_morpher.cloud.search_failed", cloudPageError), ChatFormatting.RED);
                return;
            }
            STATE.cloudLoaded = true;
            STATE.cloudLoadedKey = key;
            STATE.cloudEntries.clear();
            STATE.cloudEntries.addAll(page.entries());
            STATE.cloudPages.accept(cursor, page.nextCursor(), page.hasMore());
            STATE.cloudCursor = STATE.cloudPages.next() == null ? "" : STATE.cloudPages.next();
            STATE.cloudHasMore = STATE.cloudPages.next() != null;
            STATE.cloudScroll = 0;
            selectedCloudAssetIds.clear();
            ownedCloudAssetIds.clear();
            for (CloudAssetSummary entry : page.entries())
                if (entry.owned() || scope.equals("mine")) ownedCloudAssetIds.add(entry.ref().assetId());
        }));
    }



    private void switchCloudView(ModelPanelState.CloudView view) {
        if (STATE.cloudView == view) return;
        STATE.cloudView = view;
        cloudVisibilityFilter = "";
        resetCloudPage();
    }

    private void renderCloudGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<CloudAssetSummary> visible = cloudVisibleEntries();
        if (!this.controller.cloudAvailable()) {
            renderEmptyState(g,mouseX,mouseY,x,y,w,h,Component.translatable("gui.sparkle_morpher.cloud.disconnected"),Component.literal("当前实例尚未连接；已保存的模型信息不会删除。"),cloudText("login"),this::openCloudAccount);
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
            renderEmptyState(g,mouseX,mouseY,x,y,w,h,Component.translatable("gui.sparkle_morpher.cloud.no_models"),Component.literal(cloudPageError.isBlank()?"保留搜索条件，可清除筛选后重试。":cloudPageError),Component.literal(cloudPageError.isBlank()?"清除筛选":"重试"),()->{if(cloudPageError.isBlank()){STATE.cloudSearchText="";switchCloudView(ModelPanelState.CloudView.ALL);init();}resetCloudPage();});
            return;
        }
        CloudModelPage page = cloudGridPage(w, h);
        this.controller.requestCloudPreviews(visible.subList(page.start(), page.end()));
        g.enableScissor(x, y, x + w, y + h);
        if (gridMode(w, h) == ModelPickerLayout.GridMode.CARDS) {
            ModelPickerLayout.Cards cards = ModelPickerLayout.cards(w, h, visible.size(), denseModelToolbar());
            ensureCardPool(cards.capacity());
            cardFrameNanos=System.nanoTime();cardRefreshBudget.beginFrame(cardFrameNanos,page.end()-page.start());
            for (int i = page.start(); i < page.end(); i++) {
                int slot = i - page.start();
                int cx = x + cards.originX(w) + cards.columnX(w,slot % cards.cols());
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
            page = CloudModelPage.cards(visible.size(), ModelPickerLayout.cards(w, h, visible.size(), denseModelToolbar()).capacity(), STATE.cloudScroll);
        } else {
            ModelListMetrics metrics = modelListMetrics(w, h, visible.size());
            page = CloudModelPage.rows(visible.size(), metrics.cols(), metrics.rows(), STATE.cloudScroll);
        }
        STATE.cloudScroll = page.scroll();
        return page;
    }

    private boolean canChangeCloudVisibility(CloudAssetSummary entry) {
        return STATE.canChangeCloudVisibility(entry.owned() || ownedCloudAssetIds.contains(entry.ref().assetId()));
    }

    private String cloudEntryName(CloudAssetSummary entry) {
        return entry.name().isBlank() ? entry.ref().assetId() : entry.name();
    }

    private String cloudEntryDetail(CloudAssetSummary entry) {
        String visibility = Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (entry.isPublic() ? "public" : "private")).getString();
        return entry.format().toUpperCase(Locale.ROOT) + " · " + visibility;
    }

    private Component cloudEntryTooltip(CloudAssetSummary entry) {
        Component error=this.controller.cloudPreviewError(entry);
        return Component.literal(cloudEntryName(entry)+"\n"+entry.ref().assetId()+"\n"+cloudEntryDetail(entry)+(error==null?"":"\n"+error.getString()));
    }

    private Component cloudVisibilityAction(CloudAssetSummary entry) {
        return Component.translatable("gui.sparkle_morpher.cloud_upload." +
                (pendingVisibilityChanges.contains(entry.ref().assetId()) ? "changing_visibility" : entry.isPublic() ? "make_private" : "make_public"));
    }

    private void renderCloudCard(GuiGraphicsExtractor g, CloudAssetSummary entry, int slot, int cx, int cy, int cw, int ch, int mouseX, int mouseY) {
        String modelId = this.controller.cloudModelId(entry);
        boolean selected = cloudMultiSelectMode ? selectedCloudAssetIds.contains(entry.ref().assetId()) : modelId.equals(STATE.selectedModelId);
        boolean hover = inside(mouseX, mouseY, cx, cy, cw, ch);
        var artwork = modelCardArtwork(modelId);
        int coverH = artwork.coverHeight(ch,ModelPickerLayout.nameBandH(ch));
        int nameH = ch-coverH;
        roundedFill(g,cx,cy,cw,ch,UiElementLayout.CARD_RADIUS,selected?CARD_SELECT:hover?CARD_BASE_HOVER:CARD_BASE);
        ModelAssembly assembly = this.controller.assemblyOrNull(modelId);
        if (assembly != null) {
            renderCardMonster(g, slot, modelId, cx, cy, cw, ch, coverH);
        } else if(this.controller.cloudPreviewError(entry)!=null){
            drawIconScaled(g,IconGlyph.INFO,cx+cw/2-7,cy+coverH/2-7,14);
        } else if (pendingCloudImports.contains(modelId) || this.controller.cloudAvailable()) {
            drawCardLoading(g, cx, cy, cw, coverH);
        } else {
            int size = Math.max(1, Math.min(30, Math.min(cw - 12, coverH - 20)));
            drawIconScaled(g, IconGlyph.MODEL, cx + (cw - size) / 2, cy + Math.max(2, (coverH - size) / 2), size);
        }
        if(!artwork.hasFramedNameArea())fillCardBand(g,cx,cy,cw,ch,cy+coverH,CARD_NAME_BG);
        drawCardName(g, new ModelEntry(modelId, cloudEntryName(entry), cloudEntryDetail(entry), false, false),
                cx, cy + coverH, cw, nameH, false, artwork);
        if (cloudMultiSelectMode) renderCloudSelection(g, entry, cx + 4, cy + 4);

        cardBorder(g,cx,cy,cw,ch,selected?RED:hover?CARD_NAME_TEXT:0x55FFFFFF);
        Component previewFailure=this.controller.cloudPreviewError(entry);
        hit(cx, cy, cw, ch, previewFailure==null?cloudEntryTooltip(entry):previewFailure, () -> clickCloudAsset(entry));
    }

    private void renderCloudTextEntry(GuiGraphicsExtractor g, CloudAssetSummary entry, int cx, int cy, int cw, int ch, boolean dense, int mouseX, int mouseY) {
        boolean selected = cloudMultiSelectMode ? selectedCloudAssetIds.contains(entry.ref().assetId()) : this.controller.cloudModelId(entry).equals(STATE.selectedModelId);
        boolean hover = inside(mouseX, mouseY, cx, cy, cw, ch);
        fill(g, cx, cy, cw, ch, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x3E30363B);
        border(g, cx, cy, cw, ch, selected ? RED : 0x33FFFFFF);
        int iconY = dense ? cy + Math.max(0, (ch - 16) / 2) : cy + 3;
        if (cloudMultiSelectMode) renderCloudSelection(g, entry, cx + 4, iconY + 2);
        drawText(g, Component.literal(trim(cloudEntryName(entry), cw - 26)), cx + 22,
                dense ? cy + (ch - this.font.lineHeight) / 2 + 1 : cy + 6);
        if (!dense) drawMuted(g, Component.literal(trim(entry.format().toUpperCase(Locale.ROOT), cw - 12)), cx + 6, cy + 22);

        hit(cx, cy, cw, ch, cloudEntryTooltip(entry), () -> clickCloudAsset(entry));
    }

    private void advanceCloudPage(int w, int h) {
        CloudModelPage page = cloudGridPage(w, h);
        if (page.hasNext()) STATE.cloudScroll = page.next();
        else if (STATE.cloudHasMore && !STATE.cloudLoading) requestCloudPage(true);
    }

    private void renderCloudPageControls(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h){
        List<CloudAssetSummary> visible=cloudVisibleEntries();CloudModelPage page=cloudGridPage(w,h);int index=STATE.cloudPages.index()+1;
        drawPagination(g,mx,my,x,y,w,index+" / "+(STATE.cloudHasMore?"…":index),Math.max(0,page.end()-page.start())+" / "+visible.size()+(STATE.cloudHasMore?"+":"")+" 个模型",!STATE.cloudLoading&&(page.scroll()>0||STATE.cloudPages.hasPrevious()),!STATE.cloudLoading&&(page.hasNext()||STATE.cloudHasMore),()->{if(page.scroll()>0)STATE.cloudScroll=page.previous();else if(STATE.cloudPages.hasPrevious())requestCloudBatch(STATE.cloudPages.previous());},()->advanceCloudPage(w,h));
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
            int generation = this.controller.generation();
            long importGeneration = cloudImportGeneration;
            var expectedRuntime = CloudClientRuntime.state(this.controller.cloudInstanceId());
            this.controller.importCloudAsset(summary, error -> {
                if (generation != this.controller.generation() || importGeneration != cloudImportGeneration) return;
                pendingCloudImports.remove(cloudModelId);
                if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL || CloudClientRuntime.state(this.controller.cloudInstanceId()) != expectedRuntime) return;
                if (!STATE.selectedModelId.equals(cloudModelId)) return;
                if (error != null && !error.getString().isBlank()) { setStatus(error, ChatFormatting.RED); return; }
                this.pendingModelApplyId = cloudModelId;
            });
            return;
        }
        STATE.selectedTextureId = selectedTextureOrDefault(assembly);
        applyModelAndTexture(cloudModelId, STATE.selectedTextureId, assembly);
    }




    private void renderCardStage(GuiGraphicsExtractor g,int x,int y,int w,int h){
        if(w<=0||h<=0)return;
        RoundedRectGeometry.fill((rx,ry,rw,rh,unused)->g.fillGradient(rx,ry,rx+rw,ry+rh,UiElementLayout.mix(0xFF353535,0xFF292929,(ry-y)/(float)h),UiElementLayout.mix(0xFF353535,0xFF292929,(ry+rh-y)/(float)h)),x,y,w,h,UiElementLayout.CARD_RADIUS-1,0);
        int sw=Math.max(4,w*2/3),sh=Math.min(5,Math.max(1,h/24));for(int row=0;row<sh;row++){int inset=Math.abs(row-sh/2)*2;fill(g,x+(w-sw)/2+inset,y+h-sh-3+row,Math.max(1,sw-inset*2),1,0x55202020);}
    }
    private void drawPagination(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,String label,String status,boolean previous,boolean next,Runnable prevAction,Runnable nextAction){
        var p=PaginationLayout.measure(x+4,y,w-8,font.width(appearanceText(Component.literal(label))),uiButtonHeight());int ty=p.label().y()+(p.label().h()-9)/2;
        if(p.status().w()>0)drawMuted(g,Component.literal(trim(status,p.status().w())),p.status().x(),ty);
        drawCentered(g,Component.literal(trim(label,p.label().w()-6)),p.label().x()+p.label().w()/2,ty,MUTED);
        for(int i=0;i<2;i++){var r=i==0?p.previous():p.next();boolean active=i==0?previous:next;roundedSurface(g,r.x(),r.y(),r.w(),r.h(),3,active&&inside(mx,my,r.x(),r.y(),r.w(),r.h())?0x18FFFFFF:0x08FFFFFF,0x22FFFFFF);drawIconScaled(g,i==0?IconGlyph.PREVIOUS:IconGlyph.NEXT,r.x()+(r.w()-10)/2,r.y()+(r.h()-10)/2,10);if(active)hit(r.x(),r.y(),r.w(),r.h(),Component.translatable(i==0?"gui.sparkle_morpher.pre_page":"gui.sparkle_morpher.next_page"),i==0?prevAction:nextAction);}
    }
    private void backFromCloud(){
        if(STATE.secondaryPanel==ModelPanelState.SecondaryPanel.NONE&&cloudAccountPage==CloudAccountPage.ACCOUNT){
            cloudHistory.clear();afterCloudLogin=null;closeCloudForm();
            STATE.activeTab=ModelPanelState.Tab.SETTINGS;selectSettingsPage("general");
        }else returnFromCloud();
    }
    private void returnFromCloud(){
        var previous=cloudHistory.back();closeCloudForm();
        if(previous.isPresent()){
            var route=previous.get();STATE.activeTab=route.tab();settingsPanel().selectPage(route.settingsPage());
            cloudAccountPage=route.cloudPage();STATE.secondaryPanel=route.form();editingCloudInstance=route.editing();
            cloudAddressDraft=route.address();cloudNameDraft=route.name();cloudAccountScroll=0;cloudDetailScroll=0;init();return;
        }
        if(afterCloudLogin!=null){InputUtil.setScreen(afterCloudLogin);return;}
        STATE.activeTab=cloudReturnTab;if(cloudReturnTab==ModelPanelState.Tab.SETTINGS)settingsPanel().selectPage(cloudReturnSettingsPage.equals("cloud")?"general":cloudReturnSettingsPage);init();
    }

    private void renderModelGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h) {
        List<ModelEntry> entries = collectModelEntries();
        if (entries.isEmpty()) {
            renderEmptyState(g,mouseX,mouseY,x,y,w,h,Component.translatable("gui.sparkle_morpher.model_panel.no_models"),Component.translatable("gui.sparkle_morpher.model_panel.drop_files_hint"),Component.literal("清除筛选 / 导入模型"),()->{STATE.modelSearchText="";STATE.modelFilter=ModelPanelState.ModelFilter.ALL;if(collectModelEntries().isEmpty())openImportPanel();else init();});
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
            if (STATE.modelSource == ModelPanelState.ModelSource.LOCAL) STATE.modelScroll = 0;
            else STATE.cloudScroll = 0;
            this.lastGridMode = mode;
        }
        return mode;
    }

    /** 展示偏好：用户在本屏切换过就以本屏为准，否则读配置默认值。 */
    private ModelPickerLayout.Style pickerStyle() { return ModelPickerLayout.Style.CARDS; }



    /** AUTO → CARDS → LIST 循环；切到 LIST 后再次点击回到 AUTO。 */




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
            boolean selected = STATE.multiSelectMode ? this.selectedModelIds.contains(entry.modelId()) : entry.modelId().equals(STATE.selectedModelId);
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
        ModelPickerLayout.Cards m = ModelPickerLayout.cards(w, h, entries.size(), denseModelToolbar());
        int pages = m.totalPages(entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, pages - 1);
        int start = STATE.modelScroll * m.capacity();
        int x0 = x + m.originX(w);
        int y0 = y + m.originY(h);
        Set<String> starred = starModels();
        ensureCardPool(m.capacity());
        cardFrameNanos=System.nanoTime();cardRefreshBudget.beginFrame(cardFrameNanos,Math.min(m.capacity(),entries.size()-start));
        int hoverIndex = -1;
        for (int i = 0; i < m.capacity() && start + i < entries.size(); i++) {
            int r = i / m.cols();
            int c = i % m.cols();
            int cx = x0 + m.columnX(w,c);
            int cy = y0 + r * (m.cellH() + ModelPickerLayout.CARD_GAP);
            if (inside(mouseX, mouseY, cx, cy, m.cellW(), m.cellH())) {
                hoverIndex = start + i;
                break;
            }
        }
        for (int i = 0; i < m.capacity() && start + i < entries.size(); i++) {
            int r = i / m.cols();
            int c = i % m.cols();
            int cx = x0 + m.columnX(w,c);
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
    private void renderModelCard(GuiGraphicsExtractor g, ModelEntry entry, int slot, int cx, int cy, int cw, int ch, boolean hover, Set<String> starredModels) {
        boolean folder = entry.folder();
        boolean selected = STATE.multiSelectMode ? this.selectedModelIds.contains(entry.modelId()) : entry.modelId().equals(STATE.selectedModelId);
        boolean multiSelected = !folder && !entry.modelId().equals(STATE.selectedModelId) && this.selectedModelIds.contains(entry.modelId());
        boolean starred = !folder && starredModels.contains(entry.modelId());
        boolean locked = !folder && entry.locked();
        var artwork = folder || locked ? ModelCardArtwork.NONE : modelCardArtwork(entry.modelId());
        int coverH = artwork.coverHeight(ch,ModelPickerLayout.nameBandH(ch));
        int nameH = ch-coverH;

        if (folder) {
            roundedFill(g,cx,cy,cw,ch,UiElementLayout.CARD_RADIUS,hover?CARD_FOLDER_HOVER:CARD_FOLDER);
        } else {
            roundedFill(g,cx,cy,cw,ch,UiElementLayout.CARD_RADIUS,selected?CARD_SELECT:hover?CARD_BASE_HOVER:CARD_BASE);
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
        if(!artwork.hasFramedNameArea())fillCardBand(g,cx,cy,cw,ch,cy+coverH,CARD_NAME_BG);
        drawCardName(g, entry, cx, cy + coverH, cw, nameH, folder, artwork);

        if (locked) {
            roundedFill(g,cx,cy,cw,ch,UiElementLayout.CARD_RADIUS,CARD_DIM);
        } else if (!folder) {
            if (selected || multiSelected) {
                drawIconScaled(g,IconGlyph.CIRCLE_CHECK,cx+cw-12,cy+4,8);
            } else if (starred) {
                drawIcon(g, IconGlyph.STAR, cx + cw - 18, cy + 3);
            }
        }

        cardBorder(g,cx,cy,cw,ch,selected?RED:hover?CARD_NAME_TEXT:0x55FFFFFF);
        hit(cx, cy, cw, ch, Component.literal(entry.title()), () -> clickModelEntry(entry));
    }

    /** A single readable model name; the author artwork supplies its own frame. */
    private void drawCardName(GuiGraphicsExtractor g,ModelEntry entry,int bx,int by,int cw,int nameH,boolean folder,ModelCardArtwork artwork){
        boolean authoredFrame=artwork.hasFramedNameArea();
        int inset=authoredFrame?Math.max(4,cw/16):3;
        String title=trim(entry.title(),Math.max(6,cw-inset*2));
        int color=authoredFrame?artwork.nameTextColor():CARD_NAME_TEXT;
        int y=by+Math.max(2,(nameH-font.lineHeight)/2);
        g.text(this.font,appearanceText(Component.literal(title)),bx+inset,y,color,true);
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
    private void renderCardMonster(GuiGraphicsExtractor g,int slot,String modelId,int cx,int cy,int cw,int ch,int coverH){
        if(cw<18||coverH<26)return;
        ModelAssembly assembly=this.controller.assemblyOrNull(modelId);
        if(assembly==null){drawCardLoading(g,cx,cy,cw,coverH);return;}
        var assets=assembly.getTextureRegistry();
        renderCardStage(g,cx+1,cy+1,cw-2,coverH-2);
        var frame=cardArtwork(assets.getGuiForeground());
        var art=frame==ModelCardArtwork.NONE?cardArtwork(assets.getGuiBackground()):frame;
        // Preserve authored transparent name space; cover-only artwork stays above the name band.
        int canvasH=art.canvasHeightForCard(ch,coverH);
        drawCardArtwork(g,assets.getGuiBackground(),cx+1,cy+1,cw-2,canvasH-2);
        int figureH=frame.figureHeight(canvasH,coverH);
        if(!assembly.getPresentationCapabilities().cardPreviewAvailable()||!renderCardFigure(g,slot,modelId,assembly,cx,cy,cw,figureH))
            drawIconScaled(g,IconGlyph.MODEL,cx+cw/2-7,cy+coverH/2-7,14);
        drawCardArtwork(g,assets.getGuiForeground(),cx+1,cy+1,cw-2,canvasH-2);
    }

    private static ModelCardArtwork cardArtwork(AbstractTexture texture){
        if(!(texture instanceof OuterFileTexture outer))return ModelCardArtwork.NONE;
        synchronized(CARD_ARTWORK){return CARD_ARTWORK.computeIfAbsent(texture,key->ModelCardArtwork.read(outer.getResourceData()));}
    }
    private ModelCardArtwork modelCardArtwork(String modelId){
        var assembly=controller.assemblyOrNull(modelId);
        if(assembly==null)return ModelCardArtwork.NONE;
        var assets=assembly.getTextureRegistry();
        var art=cardArtwork(assets.getGuiForeground());
        if(art==ModelCardArtwork.NONE)art=cardArtwork(assets.getGuiBackground());
        return art;
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
            if(!ModelPreviewRenderer.isDirectGuiPreviewSupported())return false;
            String textureId=modelId.equals(STATE.selectedModelId)?selectedTextureOrDefault(asm):asm.getAnimationBundle().getDefaultTextureName();
            var props=asm.getModelData().getModelProperties();
            boolean disableRotation=props!=null&&props.isDisablePreviewRotation();
            String animation=ModelCardArtwork.previewAnimation(props==null?null:props.getPreviewAnimation(),asm.getAnimationBundle().getMainAnimations().keySet());
            Object key=new CardPreviewKey(previewViewId,slot,modelId,textureId,System.identityHashCode(asm),cw,coverH,animation);
            long revision=animation.isEmpty()?0:CardPreviewRefreshBudget.animationRevision(cardFrameNanos,slot);
            boolean refresh=cardRefreshBudget.mayRefresh(slot);
            long started=System.nanoTime();
            PlayerPreviewEntity entity=refresh?cardPreviewEntity(slot,modelId,textureId,asm):null;
            refresh=refresh&&entity!=null&&entity.isModelReady();
            if(refresh){
                this.controller.markModelUsed(modelId);
                entity.getAnimationStateMachine().setQueuedAnimation(animation);
            }
            float scale=ModelPickerLayout.figureScale(coverH);
            boolean available=GuiModelRenderer.enqueueCachedLivingPreview(g,cx+UiElementLayout.CARD_RADIUS,cy+UiElementLayout.CARD_RADIUS,
                    cx+cw-UiElementLayout.CARD_RADIUS,cy+coverH-1,cx+cw/2.0f,cy+coverH-3.0f,
                    scale,animation.isEmpty()?0.0f:cardPartialTick,entity,RendererManager.getPlayerRenderer(),
                    disableRotation,true,disableRotation?180.0f:176.0f,0.0f,false,GuiModelRenderer.DollOptions.GUI_PREVIEW,key,revision,refresh);
            if(refresh)cardRefreshBudget.recordRefresh(System.nanoTime()-started);
            return available;
        }catch(Exception failed){return false;}
    }

    private void drawCardLoading(GuiGraphicsExtractor g, int cx, int cy, int cw, int coverH) {
        String s = "…";
        g.text(this.font, Component.literal(s), cx + (cw - this.font.width(s)) / 2, cy + Math.max(4, (coverH - this.font.lineHeight) / 2), CARD_NAME_TEXT, false);
    }

    /** 背景按 cover 铺满 (x,y,w,h)，等比居中裁切；拿不到资源/尺寸时返回 false。 */
    private boolean drawCoverImage(GuiGraphicsExtractor g,AbstractTexture tex,int x,int y,int w,int h){
        if(tex==null||w<=0||h<=0)return false;int[] dims=coverDimensions(tex);if(dims==null)return false;
        Identifier loc=controller.textureLocation(tex,dims[0]);if(loc==null)return false;
        blitRounded(g,loc,x,y,w,h,UiElementLayout.cover(dims[0],dims[1],w,h));return true;
    }


    private boolean drawCardArtwork(GuiGraphicsExtractor g,AbstractTexture tex,int x,int y,int w,int h){
        if(tex==null||w<=0||h<=0)return false;int[] dims=coverDimensions(tex);if(dims==null)return false;
        Identifier loc=controller.textureLocation(tex,dims[0]);if(loc==null)return false;
        blitRounded(g,loc,x,y,w,h,new UiElementLayout.Uv(0,0,1,1));return true;
    }
    private void blitRounded(GuiGraphicsExtractor g,Identifier image,int x,int y,int w,int h,UiElementLayout.Uv uv){
        RoundedRectGeometry.fill((rx,ry,rw,rh,unused)->{
            float u0=uv.u0()+(uv.u1()-uv.u0())*(rx-x)/w,u1=uv.u0()+(uv.u1()-uv.u0())*(rx+rw-x)/w;
            float v0=uv.v0()+(uv.v1()-uv.v0())*(ry-y)/h,v1=uv.v0()+(uv.v1()-uv.v0())*(ry+rh-y)/h;
            g.blit(image,rx,ry,rx+rw,ry+rh,u0,u1,v0,v1);
        },x,y,w,h,UiElementLayout.CARD_RADIUS-1,0);
    }
    private void fillCardBand(GuiGraphicsExtractor g,int x,int y,int w,int h,int bandTop,int color){
        RoundedRectGeometry.fill((rx,ry,rw,rh,c)->{int yy=Math.max(ry,bandTop),bottom=Math.min(y+h,ry+rh);if(bottom>yy)fill(g,rx,yy,rw,bottom-yy,c);},x,y,w,h,UiElementLayout.CARD_RADIUS,color);
    }
    private void cardBorder(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){
        com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedBorder(g,x,y,w,h,UiElementLayout.CARD_RADIUS,color);
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
        g.blit(MODEL_PANEL_ICONS, x, y, x + size, y + size, icon.u / 128.0f, (icon.u + 16) / 128.0f, icon.v / 128.0f, (icon.v + 16) / 128.0f);
    }

    private ModelListMetrics modelListMetrics(int w, int h, int entryCount) {
        boolean dense = compactModelLayout() || h < 132;
        int targetW = dense ? 88 : 116;
        int minW = dense ? 68 : 92;
        int maxW = dense ? 112 : 150;
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
            ModelPickerLayout.Cards cards = ModelPickerLayout.cards(listW, listH, entries.size(), denseModelToolbar());
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
    private int currentModelGridH(){return Math.max(1,this.layout.footerTop-10-modelGridTop());}

    private void renderModelBottomActions(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h){
        if(STATE.modelSource!=ModelPanelState.ModelSource.LOCAL){renderCloudPageControls(g,mx,my,x,y,w,h);return;}
        List<ModelEntry> entries=collectModelEntries();var cards=ModelPickerLayout.cards(w,h,entries.size());int pages=cards.totalPages(entries.size()),page=clamp(STATE.modelScroll,0,pages-1);STATE.modelScroll=page;
        drawPagination(g,mx,my,x,y,w,(page+1)+" / "+pages,Math.min(entries.size(),(page+1)*cards.capacity())+" / "+entries.size()+" 个模型",page>0,page<pages-1,()->STATE.modelScroll=Math.max(0,page-1),()->STATE.modelScroll=Math.min(pages-1,page+1));
    }



    private void renderModelDetails(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,float partialTick){
        glassPanel(g,x,y,w,h);drawMuted(g,Component.translatable("gui.sparkle_morpher.ui.preview"),x+6,y+7);
        ModelAssembly assembly=selectedAssembly();
        if(assembly==null){drawMuted(g,Component.literal(trim(Component.translatable("gui.sparkle_morpher.model_panel.select_model").getString(),w-12)),x+6,y+24);return;}
        var d=ModelDetailLayout.measure(y,h,w,false);
        renderSelectedModelPreview(g,assembly,STATE.selectedModelId,x+5,d.previewY(),w-10,d.previewHeight(),mx,my,partialTick);
        drawText(g,Component.literal(trim(displayName(STATE.selectedModelId,assembly),w-14)),x+7,d.nameY());
        drawMuted(g,Component.literal(trim("local | "+assembly.getTextureNames().size()+" tex",w-14)),x+7,d.metadataY());
        renderInlineTexture(g,mx,my,assembly,STATE.selectedModelId,x+7,d.textureY(),w-14,false);
    }

    private void renderSelectedModelPreview(GuiGraphicsExtractor g, ModelAssembly assembly, String modelId, int x, int y, int w, int h, int mouseX, int mouseY, float partialTick) {
        renderCardStage(g,x,y,w,h);
        previewHitX=x;previewHitY=y;previewHitW=w;previewHitH=h;
        cardBorder(g, x, y, w, h, 0x33FFFFFF);
        if (!assembly.getPresentationCapabilities().detailPreviewAvailable()) {
            drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
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
                float scale = Math.max(8.0f, h * 0.43f)*detailZoom;
                int previewCenterX = x + w / 2;
                int previewCenterY = y + h / 2;
                int previewHalfSize = Math.max(24, Math.min(w, h) / 2);
                GuiModelRenderer.enqueueLivingPreview(g,x,y,x+w,y+h,x+w/2.0f,y+h-6.0f,scale,partialTick,this.previewEntity,RendererManager.getPlayerRenderer(),false,true,detailYaw,detailPitch,false,GuiModelRenderer.DollOptions.GUI_PREVIEW);
            } catch (Exception ignored) {
                drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
            }
        } else {
            drawIcon(g, IconGlyph.MODEL, x + w / 2 - 8, y + h / 2 - 8);
        }
    }

    private void renderResourceTab(GuiGraphicsExtractor g,int mx,int my,float pt){
        if(resourceSearchBox!=null)resourceSearchBox.visible=resourceRoute==ResourceRoute.CATALOG;
        if(resourceRoute==ResourceRoute.DETAIL){renderResourceDetailsReference(g,mx,my);return;}
        if(resourceRoute==ResourceRoute.QUEUE){renderDownloadQueueReference(g,mx,my);return;}
        if(resourceRoute==ResourceRoute.ERROR){renderDownloadErrorReference(g,mx,my);return;}
        int x=this.layout.contentLeft,y=this.layout.top,w=this.layout.contentWidth;
        drawTitle(g,Component.translatable("gui.sparkle_morpher.resource_station.title"),x,y+10);
        renderReferenceTab(g,mx,my,x,y+35,92,22,Component.literal("资源目录"),true,()->{});
        renderReferenceTab(g,mx,my,x+98,y+35,92,22,Component.literal("下载队列"),false,()->resourceRoute=ResourceRoute.QUEUE);
        renderReferenceTab(g,mx,my,x+196,y+35,92,22,Component.literal("来源管理"),false,this::openSitesPanel);
        int searchW=Math.min(320,w/2),ty=y+70;roundedSurface(g,x,ty,searchW,22,3,0x0CFFFFFF,0x22FFFFFF);
        if(resourceSearchBox!=null){resourceSearchBox.visible=true;resourceSearchBox.active=true;resourceSearchBox.extractWidgetRenderState(g,mx,my,pt);}
        int bx=x+searchW+6;
        toolbarButton(g,mx,my,bx,ty,IconGlyph.SITES,Component.literal("选择资源来源"),false,this::openSitesPanel);
        toolbarButton(g,mx,my,bx+26,ty,IconGlyph.REFRESH,Component.literal("刷新"),false,()->refreshResources(true));
        toolbarButton(g,mx,my,bx+52,ty,IconGlyph.MULTI,Component.literal("多选资源"),STATE.resourceMultiSelectMode,this::toggleResourceMultiSelect);
        toolbarButton(g,mx,my,bx+78,ty,IconGlyph.DOWNLOAD,Component.literal("下载所选资源"),false,()->{enqueueSelectedResources();resourceRoute=ResourceRoute.QUEUE;});
        toolbarButton(g,mx,my,bx+104,ty,IconGlyph.MORE,Component.literal("来源与下载配置"),false,()->selectSettingsPage("resources"));
        var entries=filteredResources();int columns=this.width<560?6:9,rows=2,capacity=columns*rows,pages=Math.max(1,(entries.size()+capacity-1)/capacity);STATE.resourceScroll=clamp(STATE.resourceScroll,0,pages-1);
        int start=STATE.resourceScroll*capacity,end=Math.min(entries.size(),start+capacity),cw=(w-(columns-1)*6)/columns,ch=Math.min((int)Math.round(cw*280d/162),(this.height-(y+105)-54)/2);
        this.controller.requestResourcePreviews(entries.subList(Math.min(start,entries.size()),end));
        if(STATE.resourceLoading)drawMuted(g,Component.translatable("gui.sparkle_morpher.model_panel.resource_loading"),x,y+140);
        else if(entries.isEmpty()){drawTitle(g,Component.translatable("gui.sparkle_morpher.resource_station.no_results"),x+16,y+140);renderTextButton(g,mx,my,x+16,y+180,140,22,Component.literal("来源管理"),this::openSitesPanel);}
        for(int i=start;i<end;i++){
            var entry=entries.get(i);int n=i-start,cx=x+(n%columns)*(cw+6),cy=y+105+(n/columns)*(ch+6),nh=Math.max(30,ch/5);boolean selected=this.controller.isResourceSelected(entry);
            roundedFill(g,cx,cy,cw,ch,UiElementLayout.CARD_RADIUS,0xFF303030);
            renderCardStage(g,cx+1,cy+1,cw-2,ch-nh-2);
            var cover=this.controller.resourcePreview(entry);if(cover==null||!drawCoverImage(g,cover,cx+2,cy+2,cw-4,ch-nh-4))drawIconScaled(g,IconGlyph.MODEL,cx+cw/2-10,cy+(ch-nh)/2-10,20);
            fillCardBand(g,cx,cy,cw,ch,cy+ch-nh,0xD0101010);drawText(g,Component.literal(trim(entry.name(),cw-8)),cx+4,cy+ch-nh+5);drawMuted(g,Component.literal(trim(entry.author().isBlank()?(entry.size()>0?String.format(Locale.ROOT,"%.1f MB",entry.size()/1048576.0):"模型资源"):entry.author(),cw-8)),cx+4,cy+ch-13);
            cardBorder(g,cx,cy,cw,ch,selected?RED:0x55FFFFFF);
            hit(cx,cy,cw,ch,Component.literal(entry.name()+" · "+resourceDetail(entry)),()->{clickResource(entry);if(!STATE.resourceMultiSelectMode)resourceRoute=ResourceRoute.DETAIL;});
        }
        int fy=this.height-this.layout.top-(this.width<560?18:this.width<800?20:22)-8;drawPagination(g,mx,my,x,fy,w,(STATE.resourceScroll+1)+" / "+pages,Math.max(0,end-start)+" / "+entries.size()+" 个资源"+(status.getString().isBlank()?"":" · "+status.getString()),STATE.resourceScroll>0,STATE.resourceScroll<pages-1,()->STATE.resourceScroll=Math.max(0,STATE.resourceScroll-1),()->STATE.resourceScroll=Math.min(pages-1,STATE.resourceScroll+1));
    }







    private boolean isCloudSettingsPage() {
        return STATE.activeTab == ModelPanelState.Tab.SETTINGS && settingsPanel().selectedPage().kind() == SettingsPageRegistry.Kind.CLOUD;
    }

    private void normalizeCloudSettingsEntry() {
        if (STATE.activeTab == ModelPanelState.Tab.ACCOUNT || STATE.activeTab == ModelPanelState.Tab.INSTANCE) {
            if (STATE.activeTab == ModelPanelState.Tab.INSTANCE && cloudAccountPage == CloudAccountPage.ACCOUNT) cloudAccountPage = CloudAccountPage.INSTANCES;
            STATE.activeTab = ModelPanelState.Tab.SETTINGS;
            settingsPanel().selectPage("cloud");
        }
    }

    private void openCloudSettings(CloudAccountPage page){
        if(isCloudSettingsPage()){setCloudAccountPage(page);return;}
        cloudHistory.push(cloudRoute());cloudReturnTab=STATE.activeTab;cloudReturnSettingsPage=settingsPanel().selectedPage().id();
        closeCloudForm();STATE.activeTab=ModelPanelState.Tab.SETTINGS;settingsPanel().selectPage("cloud");
        cloudAccountPage=page;cloudAccountScroll=0;cloudDetailScroll=0;init();
    }

    private void selectPeerSection(PeerSettingsSection section) {
        peerSettingsSection = section;
        STATE.settingsScroll = 0;
        setFocused(null);
        init();
    }

    private void renderHierarchicalSettings(GuiGraphicsExtractor g,int mx,int my,float partialTick,SettingsPageRegistry.Page page,SettingsPanelLayout.Columns columns){
        boolean peer=page.kind()==SettingsPageRegistry.Kind.PEER_LAN;
        int x=columns.detailX(),y=columns.top(),w=columns.detailWidth(),d=Math.max(1,w/4);
        if(peer){int i=0;for(var section:PeerSettingsSection.values()){
            if(section==PeerSettingsSection.CONNECTION||section==PeerSettingsSection.PROBE)continue;
            var current=section;int xx=x+i++*d;
            renderReferenceTab(g,mx,my,xx,y,d-4,uiButtonHeight(),settingsText("peer.section."+section.name().toLowerCase(Locale.ROOT)),peerSettingsSection==section||section==PeerSettingsSection.ROOM&&peerSettingsSection==PeerSettingsSection.CONNECTION,()->selectPeerSection(current));
        }}else{int i=0;for(var section:NetworkSettingsSection.values()){
            var current=section;int xx=x+i++*d;
            renderReferenceTab(g,mx,my,xx,y,d-4,uiButtonHeight(),settingsText("network.section."+section.name().toLowerCase(Locale.ROOT)),networkSettingsSection==section,()->{networkSettingsSection=current;STATE.settingsScroll=0;setFocused(null);});
        }}
        int top=y+36,bottom=y+columns.height()-6,first=this.hits.size();
        if(peer)renderPeerSettingsDetail(g,mx,my,partialTick,x,top,w,bottom);else renderGeneralNetworkDetail(g,mx,my,x,top,w,bottom);

    }

    private void renderGeneralNetworkDetail(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int top, int w, int bottom) {
        if (networkSettingsSection == NetworkSettingsSection.PRESETS) {
            var presets = com.micaftic.morpher.core.network.NetworkPreset.values();
            var current = com.micaftic.morpher.client.peer.ClientNetworkPreferences.preset();
            var window = SettingsPanelLayout.window(top,top+presets.length*34,0,34,presets.length,0);
            for (int i = window.firstRow(); i < window.endRowExclusive(); i++) {
                var preset = presets[i]; int yy = top + (i-window.firstRow())*34;
                String key = preset.name().toLowerCase(Locale.ROOT); boolean selected = current == preset;
                roundedSurface(g,x,yy,w,30,3,selected?0x18FFFFFF:0x08FFFFFF,selected?RED:0x18FFFFFF);
                roundedFill(g,x+10,yy+14,9,9,4,selected?RED:0xFF777777);
                drawText(g,Component.literal(preset.name()),x+30,yy+12);
                Component help=settingsText("network.preset_help."+key);
                drawMuted(g,Component.literal(trim(help.getString(),Math.max(1,w-245))),x+225,yy+13);
                hit(x,yy,w,30,help,()->com.micaftic.morpher.client.peer.ClientNetworkPreferences.select(preset));
            }
            if(window.maxScroll()>0)renderScrollbar(g,mouseX,mouseY,x+w-4,top,4,bottom-top,presets.length,window.visibleRows(),window.scroll());
            return;
        }
        int count = networkSettingsSection == NetworkSettingsSection.PRESETS ? 10 : networkSettingsSection == NetworkSettingsSection.PRIVACY ? 6 : 7;
        var window = SettingsPanelLayout.window(top,top+count*24,0,24,count,0);
        int y = top - window.scroll() * 24;
        switch (networkSettingsSection) {
            case PRESETS -> {
                var current = com.micaftic.morpher.client.peer.ClientNetworkPreferences.preset();
                int row = 0;
                for (var preset : com.micaftic.morpher.core.network.NetworkPreset.values()) {
                    String key = preset.name().toLowerCase(Locale.ROOT);
                    renderRowButton(g, mouseX, mouseY, x, y + row++ * 24, w, 20, settingsText("network.preset." + key), current == preset,
                            () -> com.micaftic.morpher.client.peer.ClientNetworkPreferences.select(preset));
                }
                networkLine(g, settingsText("network.preset_help." + current.name().toLowerCase(Locale.ROOT)), x, y + 168, w);
                networkLine(g, settingsText("network.preset_privacy"), x, y + 192, w);
                networkLine(g, settingsText("network.preset_credentials"), x, y + 216, w);
            }
            case PRIVACY -> {
                renderSettingRow(g, mouseX, mouseY, x, y, w, privacyModeRow(ModelPanelState.SettingGroup.GENERAL));
                networkLine(g, settingsText("network.privacy_help"), x, y + 24, w);
                renderSettingRow(g, mouseX, mouseY, x, y + 72, w,
                        bool(ModelPanelState.SettingGroup.GENERAL, "gui.sparkle_morpher.config.network_online_debug_log", GeneralConfig.NETWORK_ONLINE_DEBUG_LOG));
                networkLine(g, settingsText("network.independent"), x, y + 120, w);
            }
            case STATUS -> {
                var selected = CloudManagementScreen.management().registry().selected();
                String id = selected.map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse("");
                networkLine(g, !com.micaftic.morpher.core.network.NetworkAccess.allowsCloud() ? settingsText("network.cloud_paused")
                        : settingsText("cloud.state", CloudManagementScreen.text(CloudClientRuntime.state(id) != null ? "connected" : "disconnected")), x, y, w);
                networkLine(g, com.micaftic.morpher.client.peer.ClientPeerRuntime.statusComponent(), x, y + 24, w);
                networkAction(g, mouseX, mouseY, x, y + 72, w, settingsText("page.cloud"), true, () -> selectSettingsPage("cloud"));
                networkAction(g, mouseX, mouseY, x, y + 96, w, settingsText("page.peer"), true, () -> selectSettingsPage("peer"));
                networkLine(g, settingsText("network.priority"), x, y + 144, w);
            }
            case SERVER -> {
                networkLine(g, settingsText(minecraft.getConnection() == null ? "server.offline" : "server.connected"), x, y, w);
                networkLine(g, settingsText("server.automatic"), x, y + 48, w);
                networkLine(g, settingsText("server.independent"), x, y + 96, w);
            }
        }
        if (window.maxScroll() > 0) renderScrollbar(g, mouseX, mouseY, x + w - 4, top, 4,
                Math.max(14, bottom - top), count, window.visibleRows(), window.scroll());
    }

    private void renderPeerSettingsDetail(GuiGraphicsExtractor g,int mx,int my,float pt,int x,int top,int w,int bottom){
        boolean ready=minecraft.player!=null&&minecraft.level!=null&&!com.micaftic.morpher.client.PrivacyMode.isActive(),running=com.micaftic.morpher.client.peer.ClientPeerRuntime.running();String invite=com.micaftic.morpher.client.peer.ClientPeerRuntime.invitation();
        if(peerLocalRouteBox!=null)peerLocalRouteBox.visible=false;if(peerListenerBox!=null)peerListenerBox.visible=false;if(peerEntranceBox!=null)peerEntranceBox.visible=false;if(peerEndpointBox!=null)peerEndpointBox.visible=false;if(peerInvitationBox!=null)peerInvitationBox.visible=false;
        if(peerSettingsSection==PeerSettingsSection.ROOM){
            drawTitle(g,settingsText("peer.section.room"),x,top+4);networkLine(g,settingsText("peer.automatic_help"),x,top+26,w);
            fill(g,x,top+58,w,24,0x0CFFFFFF);drawMuted(g,Component.literal("房间状态"),x+10,top+66);drawMuted(g,Component.literal("当前状态"),x+w/2,top+66);
            String[] labels={"网络","房间成员","模型接收","同步情况"};Component[] values={com.micaftic.morpher.client.peer.ClientPeerRuntime.statusComponent(),ready?settingsText("peer.members",com.micaftic.morpher.client.peer.ClientPeerRuntime.members()):settingsText("peer.world_required"),com.micaftic.morpher.client.peer.ClientPeerRuntime.receiveStatusComponent(),com.micaftic.morpher.client.peer.ClientPeerRuntime.observerStatusComponent()};
            for(int i=0;i<4;i++){int yy=top+82+i*38;drawText(g,Component.literal(labels[i]),x+10,yy+12);drawMuted(g,Component.literal(trim(values[i].getString(),w/2-20)),x+w/2,yy+12);}
            int yy=top+240;networkAction(g,mx,my,x,yy,126,settingsText("peer.retry"),ready,()->{if(running)com.micaftic.morpher.client.peer.ClientPeerRuntime.retrySharing();else com.micaftic.morpher.client.peer.ClientPeerRuntime.enableAutomatic();});networkAction(g,mx,my,x+134,yy,126,settingsText("peer.copy_diagnostics"),true,()->minecraft.keyboardHandler.setClipboard(com.micaftic.morpher.client.peer.ClientPeerRuntime.diagnostics()));networkAction(g,mx,my,x+268,yy,146,settingsText("peer.section.connection"),true,()->selectPeerSection(PeerSettingsSection.CONNECTION));networkAction(g,mx,my,x,yy+42,126,settingsText("peer.leave"),running,com.micaftic.morpher.client.peer.ClientPeerRuntime::leave);return;
        }
        if(peerSettingsSection==PeerSettingsSection.SHARING){
            boolean shared=com.micaftic.morpher.client.peer.ClientPeerRuntime.sharing();renderSettingRow(g,mx,my,x,top,w,new SettingRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.settings.peer.sharing",shared,"",()->{if(running)com.micaftic.morpher.client.peer.ClientPeerRuntime.setSharing(!shared);},null,null,null,null));networkLine(g,settingsText("peer.sharing_help"),x+10,top+30,w-20);
            String[] labels={"连接状态","模型接收"};Component[] values={com.micaftic.morpher.client.peer.ClientPeerRuntime.statusComponent(),com.micaftic.morpher.client.peer.ClientPeerRuntime.receiveStatusComponent()};for(int i=0;i<2;i++){int yy=top+58+i*47;drawText(g,Component.literal(labels[i]),x+10,yy+12);roundedSurface(g,x+w-160,yy+7,150,22,3,0x0CFFFFFF,0x22FFFFFF);drawCentered(g,Component.literal(trim(values[i].getString(),138)),x+w-85,yy+14,MUTED);}
            networkAction(g,mx,my,x,top+184,138,settingsText("peer.retry"),ready&&running,com.micaftic.morpher.client.peer.ClientPeerRuntime::retrySharing);return;
        }
        if(peerSettingsSection==PeerSettingsSection.PUBLIC||peerSettingsSection==PeerSettingsSection.TUNNEL){
            drawTitle(g,settingsText(peerSettingsSection==PeerSettingsSection.TUNNEL?"peer.section.tunnel":"peer.section.public"),x,top+4);networkLine(g,settingsText(peerSettingsSection==PeerSettingsSection.TUNNEL?"peer.sakura_help":"peer.public_help"),x,top+28,w);
            networkLine(g,settingsText("peer.listeners"),x,top+48,w);renderReferenceField(g,mx,my,pt,peerListenerBox,x,top+69,w,24);peerListenerBox.visible=true;
            networkLine(g,settingsText("peer.entrances"),x,top+124,w);renderReferenceField(g,mx,my,pt,peerEntranceBox,x,top+145,w,24);peerEntranceBox.visible=true;
            networkLine(g,settingsText("peer.mapping_help"),x,top+178,w);
            networkAction(g,mx,my,x,top+208,108,settingsText("peer.host"),ready&&!running,()->com.micaftic.morpher.client.peer.ClientPeerRuntime.hostEntrances(peerListenerDraft,peerEntranceDraft));networkAction(g,mx,my,x+116,top+208,108,settingsText("peer.copy"),invite!=null,()->minecraft.keyboardHandler.setClipboard(invite));networkAction(g,mx,my,x,top+248,180,Component.literal("访客加入 / 探测 ›"),true,()->selectPeerSection(PeerSettingsSection.PROBE));networkLine(g,com.micaftic.morpher.client.peer.ClientPeerRuntime.statusComponent(),x,top+288,w);return;
        }
    }



    private SettingsPanelLayout.Columns settingsColumns(){
        int top=this.layout.top+49,bottom=this.layout.top+this.layout.height-12;
        return SettingsPanelLayout.columns(this.layout.contentLeft,top,this.layout.contentWidth,bottom-top);
    }

    private Component settingsText(String key, Object... args) {
        return Component.translatable("gui.sparkle_morpher.settings." + key, args);
    }

    private void selectSettingsPage(String id) {
        if(id.equals("cloud")&&!isCloudSettingsPage()){cloudHistory.push(cloudRoute());cloudReturnTab=STATE.activeTab;cloudReturnSettingsPage=settingsPanel().selectedPage().id();cloudAccountPage=CloudAccountPage.ACCOUNT;}
        if(!id.equals("cloud"))cloudHistory.clear();
        settingsPanel().selectPage(id);
        if (!isCloudSettingsPage() && cloudIdentityFlow != null) {
            cloudIdentityFlow.invalidate(); cloudIdentityFlow = null; cloudIdentityGateway = null;
        }
        setFocused(null);
        init();
    }

    private void renderSettingsTab(GuiGraphicsExtractor g,int mx,int my,float partialTick){
        var columns=settingsColumns();var page=settingsPanel().selectedPage();
        if(page.kind()==SettingsPageRegistry.Kind.CLOUD){renderCloudAccountPanel(g,mx,my,layout.contentLeft,layout.top,layout.contentWidth,layout.height,partialTick);return;}
        if(page.kind()==SettingsPageRegistry.Kind.PEER_LAN&&(peerSettingsSection==PeerSettingsSection.CONNECTION||peerSettingsSection==PeerSettingsSection.PROBE)){
            pageHeader(g,mx,my,Component.literal(peerSettingsSection==PeerSettingsSection.PROBE?"访客加入 / 探测":"高级：局域网手动连接"),Component.literal("P2P / 高级连接"),()->selectPeerSection(PeerSettingsSection.ROOM));
            renderSettingsViewport(g,mx,my,layout.contentLeft,layout.top+42,layout.contentWidth,height-layout.top-12,offset->renderPeerConnectionReference(g,mx,my+offset,partialTick));return;
        }
        drawTitle(g,Component.translatable("gui.sparkle_morpher.model_panel.settings"),columns.navigationX(),layout.top+12);
        renderSettingGroups(g,mx,my,columns);
        drawTitle(g,Component.translatable(page.titleKey()),columns.detailX(),layout.top+12);

        renderSettingsViewport(g,mx,my,columns.detailX(),columns.top(),columns.detailWidth(),columns.top()+columns.height()-6,offset->{
            int mouseY=my+offset;
            if(page.kind()==SettingsPageRegistry.Kind.OPTIONS&&settingsPanel().selectedGroup()==ModelPanelState.SettingGroup.PERFORMANCE){renderPerformanceReference(g,mx,mouseY);return;}
            if(page.kind()==SettingsPageRegistry.Kind.OPTIONS&&settingsPanel().selectedGroup()==ModelPanelState.SettingGroup.DEVELOPER&&!developerAdvanced){renderDeveloperReference(g,mx,mouseY,columns);return;}
            if(page.kind()==SettingsPageRegistry.Kind.APPEARANCE){renderAppearanceSettings(g,mx,mouseY,columns);return;}
            if(page.kind()==SettingsPageRegistry.Kind.NETWORK||page.kind()==SettingsPageRegistry.Kind.PEER_LAN){renderHierarchicalSettings(g,mx,mouseY,partialTick,page,columns);return;}
            if(page.kind()==SettingsPageRegistry.Kind.OPTIONS){
                int rowY=columns.top();for(var row:settingsRows()){renderSettingRow(g,mx,mouseY,columns.detailX(),rowY,columns.detailWidth(),row);rowY += settingRowHeight();}
            }else renderNetworkSettings(g,mx,mouseY,partialTick,page,columns.detailX(),columns.top(),columns.detailWidth(),columns.top()+columns.height()-6);
        });
    }
    private void renderSettingsViewport(GuiGraphicsExtractor g,int mx,int my,int x,int top,int w,int bottom,java.util.function.IntConsumer render){
        var view=settingsViewport();int offset=view.offset(),first=hits.size();settingsViewportFields.clear();
        settingsDrawingOffset=offset;settingsContentBottom=top;settingsDrawingBody=true;
        g.enableScissor(x,top,x+w,bottom);g.pose().pushMatrix();g.pose().translate(0,-offset);
        try{render.accept(offset);}finally{g.pose().popMatrix();g.disableScissor();settingsDrawingBody=false;}
        var logical=new ArrayList<>(hits.subList(first,hits.size()));hits.subList(first,hits.size()).clear();
        for(var hit:logical){int yy=hit.y()-offset;if(yy>=top&&yy+hit.h()<=bottom)hits.add(new Hit(hit.x(),yy,hit.w(),hit.h(),hit.tooltip(),hit.action()));}
        for(var field:settingsViewportFields)if(field.getY()<top||field.getY()+field.getHeight()>bottom){field.visible=false;field.active=false;}
        view.measure(top,bottom,settingsContentBottom+4);
        if(view.maxOffset()>0)renderScrollbar(g,mx,my,x+w-4,top,4,bottom-top,view.maxOffset()+bottom-top,bottom-top,offset);
    }

    private void renderSettingGroups(GuiGraphicsExtractor g,int mx,int my,SettingsPanelLayout.Columns columns){
        var pages=SettingsPageRegistry.pages();int stride=28;
        int top=columns.top(),visible=Math.max(1,columns.height()/stride),scroll=settingsPanel().navigationScroll(pages.size(),visible);
        for(int i=scroll;i<Math.min(pages.size(),scroll+visible);i++){
            int y=top+(i-scroll)*stride;var page=pages.get(i);boolean selected=page.id().equals(settingsPanel().selectedPage().id());
            if(selected){roundedFill(g,columns.navigationX(),y,columns.navigationWidth(),24,3,0x18FFFFFF);fill(g,columns.navigationX(),y+5,2,14,RED);}
            drawMuted(g,Component.literal(trim(Component.translatable(page.titleKey()).getString(),columns.navigationWidth()-12)),columns.navigationX()+6,y+7);
            hit(columns.navigationX(),y,columns.navigationWidth(),24,Component.translatable(page.descriptionKey()),()->selectSettingsPage(page.id()));
        }
    }

    private EditBox peerField(int width, String key, int maxLength) {
        EditBox field = new EditBox(this.font, 0, 0, width, 16, settingsText("peer." + key));
        field.setMaxLength(maxLength);
        field.setTextColor(TEXT);
        field.setBordered(false);
        field.setHint(settingsText("peer." + key));
        field.visible = false;
        addWidget(field);
        return field;
    }



    private void networkLine(GuiGraphicsExtractor g, Component text, int x, int y, int w) {
        fill(g, x, y, w, 20, 0x44202020);
        drawText(g, Component.literal(trim(text.getString(), w - 12)), x + 6, y + 6);
        hit(x, y, w, 20, text, () -> setFocused(null));
    }

    private void networkAction(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w,
                               Component label, boolean enabled, Runnable action) {
        int h=uiButtonHeight();
        if (enabled) renderTextButton(g, mouseX, mouseY, x, y, w, h, label, action);
        else {
            roundedSurface(g,x,y,w,h,UiElementLayout.BUTTON_RADIUS,GLASS_DARK,0x33FFFFFF);
            drawCentered(g, Component.literal(trim(label.getString(), w - 8)), x + w / 2, UiElementLayout.centeredY(y,h,font.lineHeight), MUTED);
        }
    }

    private void joinPeerInvitation() {
        com.micaftic.morpher.client.peer.ClientPeerRuntime.join(peerInvitationDraft.trim(),peerSettingsSection==PeerSettingsSection.PUBLIC||peerSettingsSection==PeerSettingsSection.TUNNEL||peerSettingsSection==PeerSettingsSection.PROBE?peerLocalRouteDraft:"");
        peerInvitationDraft = "";
        if (peerInvitationBox != null) peerInvitationBox.setValue("");
    }

    private void renderNetworkSettings(GuiGraphicsExtractor g,int mx,int my,float pt,SettingsPageRegistry.Page page,int x,int top,int w,int bottom){
        String[] labels={"资源来源","下载队列","本地模型目录"};Runnable[] actions={this::openSitesPanel,()->{STATE.activeTab=ModelPanelState.Tab.RESOURCE;resourceRoute=ResourceRoute.QUEUE;init();},this::openModelFolder};
        for(int i=0;i<3;i++){int yy=top+i*47;drawText(g,Component.literal(labels[i]),x+10,yy+12);renderTextButton(g,mx,my,x+w-160,yy+7,150,22,Component.literal(i==1?"查看任务 ›":"打开 ›"),actions[i]);}
    }

    private void renderSettingRow(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,SettingRow row){
        int h=settingRowHeight(),bh=uiButtonHeight(),cy=y+(h-bh)/2,ty=UiElementLayout.centeredY(y,h,font.lineHeight);
        settingsExtent(y+h);
        if(row.sectionKey()!=null){drawSection(g,Component.translatable(row.sectionKey()),x+8,ty);return;}
        Component label=Component.translatable(row.labelKey());
        int controlW=Math.min(width<560?126:150,w/2),cx=x+w-controlW-8;
        drawText(g,Component.literal(trim(label.getString(),cx-x-16)),x+8,ty);
        if(row.segmented()!=null){var part=row.segmented();int half=(controlW-4)/2;renderSegmentedOption(g,cx,cy,half,bh,part.left(),part.leftSelected(),part.leftAction());renderSegmentedOption(g,cx+half+4,cy,half,bh,part.right(),!part.leftSelected(),part.rightAction());return;}
        if(row.booleanValue()!=null){int bx=x+w-34,sy=y+(h-12)/2;boolean enabled=Boolean.TRUE.equals(row.booleanValue());roundedFill(g,bx,sy,26,12,6,enabled?RED:0x66444444);roundedFill(g,bx+(enabled?15:2),sy+1,9,10,4,TEXT);hit(x,y,w,h-1,label,row.action());return;}
        if(row.action()!=null&&row.decrement()==null&&row.increment()==null){renderTextButton(g,mx,my,cx,cy,controlW,bh,Component.literal(trim(row.valueText(),controlW-12)),row.action());return;}
        roundedSurface(g,cx,cy,controlW,bh,4,inside(mx,my,cx,cy,controlW,bh)?0x18FFFFFF:0x0CFFFFFF,0x22FFFFFF);drawCentered(g,Component.literal(row.valueText()),cx+controlW/2,ty,MUTED);
        if(row.decrement()!=null)hit(cx,cy,controlW/2,bh,label.copy().append(" · 左侧减少"),row.decrement());if(row.increment()!=null)hit(cx+controlW/2,cy,controlW-controlW/2,bh,label.copy().append(" · 右侧增加"),row.increment());
    }

    private void renderSegmentedOption(GuiGraphicsExtractor g,int x,int y,int w,int h,Component label,boolean selected,Runnable action){
        roundedSurface(g,x,y,w,h,UiElementLayout.BUTTON_RADIUS,selected?PANEL_ACTIVE:0x55303030,selected?RED:0x33FFFFFF);
        drawCentered(g,Component.literal(trim(label.getString(),w-12)),x+w/2,UiElementLayout.centeredY(y,h,font.lineHeight),selected?0xFFFFFFFF:TEXT);hit(x,y,w,h,label,action);
    }

    private void renderSecondaryPanel(GuiGraphicsExtractor g,int mx,int my,float partialTick){
        if(STATE.secondaryPanel==ModelPanelState.SecondaryPanel.NONE)return;
        int x=secondaryPanelX(),y=secondaryPanelY(),w=secondaryPanelW(),h=secondaryPanelH();
        this.hits.clear();renderTabs(g,mx,my);
        switch(STATE.secondaryPanel){
            case SITES -> renderSitesPanel(g,mx,my,x,y,w,h,partialTick);
            case CATEGORIES -> renderCategoryPanel(g,mx,my,x,y,w,h,partialTick);
            case IMPORT -> renderImportPanel(g,mx,my,x,y,w,h);
            case CLOUD_LOGIN,CLOUD_INSTANCE -> renderCloudForm(g,mx,my,x,y,w,h,partialTick);
            default -> {}
        }
    }

    private void renderCloudAccountPanel(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,float partialTick){
        Component heading=switch(cloudAccountPage){
            case OFFLINE->cloudText("offline.title");case ACCOUNT->cloudText("title");case INSTANCES->cloudText("instances");case SCOPES->cloudText("scopes");case PERFORMANCE->settingsText("cloud.performance");
            case IDENTITY->cloudText("identity_panel.title");case CLAIM->cloudText("identity_panel.claim_title");case APPROVAL->cloudText("identity_panel.admin_title");
        };
        pageHeader(g,mx,my,heading,Component.empty(),()->{if(scopeCreateForm){scopeCreateForm=false;init();}else backFromCloud();});
        renderSettingsViewport(g,mx,my,layout.contentLeft,layout.top+34,layout.contentWidth,height-layout.top-24,offset->{
            int mouseY=my+offset;
            switch(cloudAccountPage){
                case OFFLINE->renderOfflineReference(g,mx,mouseY,partialTick);
                case ACCOUNT->renderAccountReference(g,mx,mouseY,partialTick);case INSTANCES->renderInstancesReference(g,mx,mouseY,partialTick);
                case PERFORMANCE->renderCloudPerformanceReference(g,mx,mouseY);case SCOPES->renderScopesReference(g,mx,mouseY,partialTick);
                case IDENTITY,CLAIM,APPROVAL->renderIdentityReference(g,mx,mouseY,partialTick);
            }
        });
        if(!cloudPanelStatus.getString().isBlank())drawMuted(g,Component.literal(trim(cloudPanelStatus.getString(),w)),x,height-layout.top-16);
    }



    private boolean isCloudIdentityPage() {
        return isCloudSettingsPage() && (cloudAccountPage == CloudAccountPage.IDENTITY || cloudAccountPage == CloudAccountPage.CLAIM || cloudAccountPage == CloudAccountPage.APPROVAL);
    }

    private int cloudPerformancePresetHeight() {
        int count = com.micaftic.morpher.cloud.client.CloudClientPerformance.Preset.values().length;
        int columns = Math.max(1, Math.min(count, accountPanelLayout().fieldWidth() / 72));
        return 32 + ((count + columns - 1) / columns) * 24;
    }

    private int cloudIdentityContentHeight() {
        return switch (cloudAccountPage) {
            case OFFLINE -> 360; case PERFORMANCE -> cloudPerformancePresetHeight() + 152; case IDENTITY -> 300; case CLAIM -> 390; case APPROVAL -> 440;
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

    private void openCloudIdentityPage(CloudAccountPage page){
        setCloudAccountPage(page);cloudIdentityChoicePage=0;
        if(CloudManagementScreen.management().snapshot().scopes().isEmpty())refreshCloudScopes();
    }

    private int cloudIdentityListSize() {
        return cloudAccountPage == CloudAccountPage.IDENTITY
                ? (cloudIdentityFlow == null ? 0 : cloudIdentityFlow.catalog().identities().size())
                : CloudManagementScreen.management().snapshot().scopes().size();
    }

    private void renderCloudIdentityList(GuiGraphicsExtractor g, int mouseX, int mouseY, AccountPanelLayout panels) {
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

    private void identityAction(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w, String key, boolean enabled, Runnable action) {
        Component label = cloudText("identity_panel." + key);
        if (enabled) renderTextButton(g, mx, my, x, y, w, 20, label, action);
        else { fill(g, x, y, w, 20, 0x4427333D); drawCentered(g, Component.literal(trim(label.getString(), w - 8)), x + w / 2, y + 6, MUTED); }
    }

    private void identityLine(GuiGraphicsExtractor g, String key, int x, int y, int w) {
        drawMuted(g, Component.literal(trim(cloudText("identity_panel." + key).getString(), w)), x, y);
    }



    private void renderCloudIdentityTargets(GuiGraphicsExtractor g, int mx, int my, int x, int y, int w) {
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









    private void setCloudAccountPage(CloudAccountPage page){
        if(cloudAccountPage==page&&STATE.secondaryPanel==ModelPanelState.SecondaryPanel.NONE)return;
        cloudHistory.push(cloudRoute());closeCloudForm();STATE.activeTab=ModelPanelState.Tab.SETTINGS;settingsPanel().selectPage("cloud");
        cloudAccountPage=page;cloudAccountScroll=0;cloudDetailScroll=0;cloudPanelStatus=Component.empty();init();
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
                            if (existing.isEmpty() || editingCloudInstance) registry.addOrReplace(profile);
                            CloudManagementScreen.management().saveInstances();
                            returnFromCloud();
                            selectCloudInstance(profile);
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
        } catch (java.io.IOException | RuntimeException failure) {
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
            if (failure == null && isCloudSettingsPage()) init();
        }));
    }

    public static void openCloudManagement(Screen parent) {
        ModernPlayerModelScreen screen = new ModernPlayerModelScreen(parent);
        InputUtil.setScreen(screen);
        screen.openCloudAccount();
    }

    void openCloudAccount(){openCloudSettings(CloudAccountPage.ACCOUNT);}

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
                if(STATE.secondaryPanel==ModelPanelState.SecondaryPanel.CLOUD_LOGIN)returnFromCloud();
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

    private void renderSitesPanel(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,float pt){
        if(sourceEditMode){renderSourceEditReference(g,mx,my,pt);return;}
        pageHeader(g,mx,my,Component.literal("来源管理"),Component.literal("已配置来源决定资源目录内容"),()->{closeCloudForm();init();});
        int pad=8,left=x+pad,right=x+w-pad,inner=right-left,bh=uiButtonHeight(),toolbarY=y+42;
        renderPrimaryButton(g,mx,my,right-(width<560?88:110),toolbarY,width<560?88:110,bh,Component.literal("添加来源"),()->{sourceNameDraft="";STATE.siteEditText="";sourceEditMode=true;init();});
        int hy=toolbarY+bh+12,rowH=width<560?38:width<800?46:58,footer=this.height-this.layout.top-bh-10;
        int addressX=left+inner/4,actionsX=right-2*bh-6;
        roundedFill(g,left,hy,inner,22,4,0x0CFFFFFF);drawMuted(g,Component.literal("来源名称"),left+8,hy+7);drawMuted(g,Component.literal("地址"),addressX,hy+7);
        var urls=this.controller.siteUrls();int visible=Math.max(1,(footer-hy-34)/rowH);STATE.sitesScroll=clamp(STATE.sitesScroll,0,Math.max(0,urls.size()-visible));
        for(int i=STATE.sitesScroll;i<Math.min(urls.size(),STATE.sitesScroll+visible);i++){
            String url=urls.get(i);int yy=hy+26+(i-STATE.sitesScroll)*rowH;boolean selected=url.equals(this.controller.selectedSite());
            roundedSurface(g,left,yy,inner,rowH-4,4,selected?0x16FFFFFF:0x06FFFFFF,selected?0x55DC4F5D:0x16FFFFFF);
            int ty=UiElementLayout.centeredY(yy,rowH-4,font.lineHeight);
            drawText(g,Component.literal(trim(ResourceSourceLabels.get(url),inner/4-16)),left+8,ty);
            drawMuted(g,Component.literal(trim(url,actionsX-addressX-12)),addressX,ty);
            hit(left,yy,inner,rowH-4,Component.literal(url),()->selectSite(url));
            int ay=yy+(rowH-4-bh)/2;
            toolbarButton(g,mx,my,actionsX,ay,IconGlyph.EDIT,Component.literal("编辑来源"),false,()->{selectSite(url);sourceNameDraft=ResourceSourceLabels.get(url);sourceEditMode=true;init();});
            toolbarButton(g,mx,my,right-bh,ay,IconGlyph.DELETE,Component.literal("删除来源"),false,()->{selectSite(url);deleteSite();});
        }
        renderTextButton(g,mx,my,left,footer,Math.min(160,inner),bh,Component.literal("资源与下载配置 ›"),()->{closeCloudForm();selectSettingsPage("resources");});
    }

    private void renderCategoryPanel(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,float pt){
        if(!settingsDrawingBody){Component heading=Component.translatable("gui.sparkle_morpher.model_panel.categories");Runnable back=()->{closeCloudForm();init();};pageHeader(g,mx,my,heading,Component.empty(),back);renderSettingsViewport(g,mx,my,layout.contentLeft,layout.top+40,layout.contentWidth,height-layout.top-12,offset->renderCategoryPanel(g,mx,my+offset,x,y,w,h,pt));return;}

        pageHeader(g,mx,my,Component.translatable("gui.sparkle_morpher.model_panel.categories"),Component.translatable("gui.sparkle_morpher.model_panel.name_target"),()->{closeCloudForm();init();});
        renderReferenceField(g,mx,my,pt,categoryEditBox,x,70,Math.min(260,w-40),22);toolbarButton(g,mx,my,x+w-22,70,IconGlyph.PLUS,Component.translatable("gui.sparkle_morpher.model_panel.create"),false,()->setStatus(this.controller.createCategory(categoryEditBox==null?"":categoryEditBox.getValue())));
        int hy=104;fill(g,x,hy,w,24,0x0CFFFFFF);drawMuted(g,Component.literal("目录"),x+10,hy+8);drawMuted(g,Component.literal("模型数量"),x+(int)(w*.3),hy+8);drawMuted(g,Component.literal("路径"),x+(int)(w*.51),hy+8);
        var categories=this.controller.listCategories();
        var ids=this.controller.availableModelIds();
        for(int i=0;i<categories.size();i++){String category=categories.get(i);int yy=hy+24+i*44;boolean selected=category.equals(STATE.categoryEditText);long count=ids.stream().filter(id->id.startsWith(category+"/")).count();
            if(selected){fill(g,x,yy,w,42,0x16FFFFFF);fill(g,x,yy+8,2,24,RED);}drawText(g,Component.literal(trim(category,w/3-20)),x+10,yy+16);drawMuted(g,Component.literal(Long.toString(count)),x+(int)(w*.3),yy+16);drawMuted(g,Component.literal(trim("custom / "+category,w/2-10)),x+(int)(w*.51),yy+16);hit(x,yy,w,42,Component.literal(category),()->{STATE.categoryEditText=category;if(categoryEditBox!=null)categoryEditBox.setValue(category);});
        }
        int by=Math.max(this.height-this.layout.top-34,hy+32+categories.size()*44);drawMuted(g,Component.literal(trim("移动到："+STATE.categoryEditText,w-220)),x,by+7);renderTextButton(g,mx,my,x+w-184,by,76,22,Component.translatable("gui.cancel"),()->{closeCloudForm();init();});renderPrimaryButton(g,mx,my,x+w-100,by,100,22,Component.translatable("gui.sparkle_morpher.model_panel.move"),()->moveSelectionToCategory(STATE.categoryEditText));
    }

    private void renderImportPanel(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h){
        pageHeader(g,mx,my,Component.translatable("gui.sparkle_morpher.import.title"),Component.translatable("gui.sparkle_morpher.model_panel.drop_files_hint"),()->{closeCloudForm();init();});
        roundedSurface(g,x,70,w,138,4,0x08FFFFFF,0x1CFFFFFF);drawTitle(g,Component.literal("选择模型文件"),x+20,96);drawMuted(g,Component.translatable("gui.sparkle_morpher.model_panel.drop_files_hint"),x+20,124);
        renderPrimaryButton(g,mx,my,x+20,158,110,22,Component.translatable("gui.sparkle_morpher.import.choose_file"),this::openFilePicker);toolbarButton(g,mx,my,x+138,158,IconGlyph.FOLDER,Component.translatable("gui.sparkle_morpher.open_model_folder.open"),false,this::openModelFolder);
        int ry=224;String[] labels={"本地模型文件夹","本地模型扫描","当前导入任务"};String[] details={com.micaftic.morpher.core.storage.ModelStoragePaths.custom().toString(),"重新扫描文件夹中的模型",this.controller.localImportInProgress()?"正在导入":"当前没有进行中的导入"};
        Runnable[] actions={this::openModelFolder,()->this.controller.reloadLocalModels(this::setStatus),this::openFilePicker};
        for(int i=0;i<3;i++){int yy=ry+i*47;drawText(g,Component.literal(labels[i]),x+10,yy+7);drawMuted(g,Component.literal(trim(details[i],w-190)),x+10,yy+23);renderTextButton(g,mx,my,x+w-160,yy+7,150,22,Component.literal(i==0?"打开目录 ›":i==1?"重新扫描":"选择文件…"),actions[i]);}
        renderTextButton(g,mx,my,x+w-130,this.height-this.layout.top-34,130,22,Component.literal("Cloud 上传 ›"),this::openCloudUpload);
    }

    private void renderFooter(GuiGraphicsExtractor g) {
        if(STATE.activeTab==ModelPanelState.Tab.RESOURCE)return;
        if (STATE.activeTab == ModelPanelState.Tab.MODEL) {
            if (!this.status.getString().isBlank()) hit(this.layout.contentLeft, this.layout.footerTop,
                    this.layout.contentWidth, this.layout.footerHeight, this.status, () -> {});
            return;
        }
        if (STATE.activeTab == ModelPanelState.Tab.SETTINGS && this.status.getString().isBlank()) return;
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
        int c = chatColor(color);
        g.text(this.font, trim(line.getString(), this.layout.contentWidth - 20), this.layout.contentLeft + 10, this.layout.footerTop + 8, c, false);
    }

    private int chatColor(ChatFormatting color) {
        if (color == null) {
            return MUTED;
        }
        return switch (color) {
            case RED, DARK_RED -> 0xFFE05252;
            case YELLOW, GOLD -> 0xFFFFC857;
            case GREEN, DARK_GREEN -> 0xFF4CAF50;
            case AQUA, DARK_AQUA, BLUE, DARK_BLUE -> 0xFF5ECAE8;
            case WHITE -> 0xFFFFFFFF;
            case BLACK, DARK_GRAY -> 0xFF6F757A;
            default -> MUTED;
        };
    }

    private void renderTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        for (int i = this.hits.size() - 1; i >= 0; i--) {
            Hit hit = this.hits.get(i);
            if (!inside(mouseX, mouseY, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            if (hit.tooltip().getString().isBlank()) return;
            String identity=hit.x()+":"+hit.y()+":"+hit.tooltip().getString();long now=System.nanoTime();if(!identity.equals(tooltipIdentity)){tooltipIdentity=identity;tooltipSince=now;return;}if(now-tooltipSince<450_000_000L)return;
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
                g.text(this.font, lines.get(line), tx + 5, ty + 5 + line * this.font.lineHeight, 0xFFFFFFFF, false);
            return;
        }
        tooltipIdentity="";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean flag) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button());
        if(STATE.activeTab==ModelPanelState.Tab.MODEL&&STATE.secondaryPanel==ModelPanelState.SecondaryPanel.NONE&&!textureMenuOpen&&!sourceTabMenu&&!modelMenuOpen&&pendingConfirmation==null&&button==0&&inside(mouseX,mouseY,previewHitX,previewHitY,previewHitW,previewHitH)){previewDragging=true;setDragging(true);return true;}
        if(pendingConfirmation!=null){if(button==0)for(int i=hits.size()-1;i>=0;i--){var h=hits.get(i);if(inside(mouseX,mouseY,h.x(),h.y(),h.w(),h.h())){h.action().run();break;}}return true;}

        if (STATE.activeTab == ModelPanelState.Tab.SETTINGS && STATE.secondaryPanel == ModelPanelState.SecondaryPanel.NONE) {
            if(peerLocalRouteBox!=null&&peerLocalRouteBox.visible&&peerLocalRouteBox.mouseClicked(event,flag)){setFocused(peerLocalRouteBox);return true;}
            if(peerListenerBox!=null&&peerListenerBox.visible&&peerListenerBox.mouseClicked(event,flag)){setFocused(peerListenerBox);return true;}
            if(peerEntranceBox!=null&&peerEntranceBox.visible&&peerEntranceBox.mouseClicked(event,flag)){setFocused(peerEntranceBox);return true;}
            if (peerEndpointBox != null && peerEndpointBox.visible && peerEndpointBox.mouseClicked(event, flag)) { setFocused(peerEndpointBox); return true; }
            if (peerInvitationBox != null && peerInvitationBox.visible && peerInvitationBox.mouseClicked(event, flag)) { setFocused(peerInvitationBox); return true; }
        }

        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudAccountBox != null
                && this.cloudAccountBox.mouseClicked(event, flag)) {
            setFocused(this.cloudAccountBox);
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudPasswordBox != null
                && this.cloudPasswordBox.mouseClicked(event, flag)) {
            setFocused(this.cloudPasswordBox);
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudInstanceNameBox != null
                && this.cloudInstanceNameBox.mouseClicked(event, flag)) {
            setFocused(this.cloudInstanceNameBox);
            return true;
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE && this.cloudOriginBox != null
                && this.cloudOriginBox.mouseClicked(event, flag)) {
            setFocused(this.cloudOriginBox);
            return true;
        }
        if (isCloudSettingsPage() && this.cloudScopeIdBox != null && this.cloudScopeIdBox.visible
                && inside(mouseX, mouseY, accountPanelLayout().detailX(), accountPanelLayout().detailY(), accountPanelLayout().detailWidth(), accountPanelLayout().detailHeight())
                && this.cloudScopeIdBox.mouseClicked(event, flag)) {
            setFocused(this.cloudScopeIdBox);
            return true;
        }
        if (isCloudSettingsPage() && this.cloudScopeNameBox != null && this.cloudScopeNameBox.visible
                && inside(mouseX, mouseY, accountPanelLayout().detailX(), accountPanelLayout().detailY(), accountPanelLayout().detailWidth(), accountPanelLayout().detailHeight())
                && this.cloudScopeNameBox.mouseClicked(event, flag)) {
            setFocused(this.cloudScopeNameBox);
            return true;
        }
        if (isCloudSettingsPage() && this.cloudWorldEpochBox != null && this.cloudWorldEpochBox.visible
                && inside(mouseX, mouseY, accountPanelLayout().detailX(), accountPanelLayout().detailY(), accountPanelLayout().detailWidth(), accountPanelLayout().detailHeight())
                && this.cloudWorldEpochBox.mouseClicked(event, flag)) {
            setFocused(this.cloudWorldEpochBox);
            return true;
        }
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) {
            if (this.cloudOriginBox != null && this.cloudOriginBox.mouseClicked(event, flag)) { setFocused(this.cloudOriginBox); return true; }
            if (this.cloudInstanceNameBox != null && this.cloudInstanceNameBox.mouseClicked(event, flag)) { setFocused(this.cloudInstanceNameBox); return true; }
        }
        if (STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE) {
            if(sourceEditMode&&sourceNameBox!=null&&sourceNameBox.mouseClicked(event,flag)){setFocused(sourceNameBox);return true;}
            if (sourceEditMode && this.siteEditBox != null && this.siteEditBox.mouseClicked(event, flag)) {
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
        if(this.sourceEditMode&&this.sourceNameBox!=null&&this.sourceNameBox.mouseClicked(event,flag)){setFocused(this.sourceNameBox);return true;}
        if(this.instanceSearchBox!=null&&this.instanceSearchBox.mouseClicked(event,flag)){setFocused(this.instanceSearchBox);return true;}
        if (this.modelSearchBox != null && this.modelSearchBox.mouseClicked(event, flag)) {
            setFocused(this.modelSearchBox);
            return true;
        }
        if (this.cloudIdentityCodeBox != null && this.cloudIdentityCodeBox.visible
                && inside(mouseX, mouseY, accountPanelLayout().detailX(), accountPanelLayout().detailY(), accountPanelLayout().detailWidth(), accountPanelLayout().detailHeight())
                && this.cloudIdentityCodeBox.mouseClicked(event, flag)) {
            setFocused(this.cloudIdentityCodeBox);
            return true;
        }
        if (this.cloudSearchBox != null && this.cloudSearchBox.mouseClicked(event, flag)) {
            setFocused(this.cloudSearchBox);
            return true;
        }
        if (this.resourceSearchBox != null && this.resourceSearchBox.mouseClicked(event, flag)) {
            setFocused(this.resourceSearchBox);
            return true;
        }
        if (sourceEditMode && this.siteEditBox != null && this.siteEditBox.mouseClicked(event, flag)) {
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
        if(textureMenuOpen&&scrollY!=0){inlineTexturePage=Math.max(0,inlineTexturePage+(scrollY>0?-1:1));return true;}
        if(STATE.activeTab==ModelPanelState.Tab.MODEL&&STATE.secondaryPanel==ModelPanelState.SecondaryPanel.NONE&&!textureMenuOpen&&!sourceTabMenu&&!modelMenuOpen&&inside(mouseX,mouseY,previewHitX,previewHitY,previewHitW,previewHitH)){detailZoom=Math.max(.5f,Math.min(2f,detailZoom*(float)Math.pow(1.12,scrollY)));return true;}
        if(scrollY==0)return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
        int delta = scrollY > 0 ? -1 : 1;
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_LOGIN || STATE.secondaryPanel == ModelPanelState.SecondaryPanel.CLOUD_INSTANCE) return true;
        if(isCloudSettingsPage()&&STATE.secondaryPanel==ModelPanelState.SecondaryPanel.NONE){
            if(cloudAccountPage==CloudAccountPage.INSTANCES){
                var r=FinalUiLayout.scene("instances",width,height).area("listRect");
                if(inside(mouseX,mouseY,r.x(),r.y(),r.w(),height-r.y()-layout.top-24)){
                    int rowH=width<560?43:66,visible=Math.max(1,(height-r.y()-45)/(rowH+6));
                    int count=CloudManagementScreen.management().registry().profiles().size();cloudAccountScroll=clamp(cloudAccountScroll+delta,0,Math.max(0,count-visible));return true;
                }
            }
            if(cloudAccountPage==CloudAccountPage.CLAIM){var p=accountPanelLayout();if(inside(mouseX,mouseY,p.listX(),p.listY(),p.listWidth(),p.listHeight())){cloudAccountScroll=clamp(cloudAccountScroll+delta,0,Math.max(0,cloudIdentityListSize()-p.listRows()));return true;}}
            if(inside(mouseX,mouseY,layout.contentLeft,layout.top+34,layout.contentWidth,height-layout.top-24-(layout.top+34)))return settingsViewport().scroll(scrollY);
            return super.mouseScrolled(mouseX,mouseY,scrollX,scrollY);
        }
        if((STATE.secondaryPanel==ModelPanelState.SecondaryPanel.SITES&&sourceEditMode)||STATE.secondaryPanel==ModelPanelState.SecondaryPanel.CATEGORIES){settingsViewport().scroll(scrollY);return true;}
        if (STATE.secondaryPanel == ModelPanelState.SecondaryPanel.SITES) {
            STATE.sitesScroll = Math.max(0, STATE.sitesScroll + delta);
            return true;
        }
        switch (STATE.activeTab) {
            case MODEL -> {
                if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) {
                    CloudModelPage page = cloudGridPage(modelListW(), currentModelGridH());
                    if(!STATE.cloudLoading){if(delta>0)advanceCloudPage(modelListW(),currentModelGridH());else if(page.scroll()>0)STATE.cloudScroll=page.previous();else if(STATE.cloudPages.hasPrevious())requestCloudBatch(STATE.cloudPages.previous());}
                    return true;
                }
                if (gridMode(modelListW(), currentModelGridH()) == ModelPickerLayout.GridMode.CARDS) {
                    List<ModelEntry> entries = collectModelEntries();
                    int pages = entries.isEmpty() ? 1
                            : ModelPickerLayout.cards(modelListW(), currentModelGridH(), entries.size(), denseModelToolbar()).totalPages(entries.size());
                    STATE.modelScroll = clamp(STATE.modelScroll + delta, 0, pages - 1);
                } else {
                    STATE.modelScroll = Math.max(0, STATE.modelScroll + delta);
                }
            }
            case RESOURCE -> {if(resourceRoute==ResourceRoute.DETAIL||resourceRoute==ResourceRoute.ERROR)settingsViewport().scroll(scrollY);else STATE.resourceScroll=Math.max(0,STATE.resourceScroll+delta);}
            case ACCOUNT, INSTANCE -> { }
            case SETTINGS -> {
                var columns=settingsColumns();
                if(fontMenuOpen){fontPage=Math.max(0,fontPage+delta);return true;}
                boolean connection=settingsPanel().selectedPage().kind()==SettingsPageRegistry.Kind.PEER_LAN&&(peerSettingsSection==PeerSettingsSection.CONNECTION||peerSettingsSection==PeerSettingsSection.PROBE);
                if(!connection&&inside(mouseX,mouseY,columns.navigationX(),columns.top(),columns.navigationWidth(),columns.height()))settingsPanel().scrollNavigationBy(delta);
                else if(inside(mouseX,mouseY,connection?layout.contentLeft:columns.detailX(),connection?layout.top+42:columns.top(),connection?layout.contentWidth:columns.detailWidth(),height-layout.top-12-(connection?layout.top+42:columns.top())))settingsViewport().scroll(scrollY);
            }
        }
        return true;
    }

    private List<ModelEntry> collectModelEntries() {
        if(extractingPage&&frameModelEntries!=null)return frameModelEntries;
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
            if (STATE.modelFilter == ModelPanelState.ModelFilter.NOT_STAR && stars.contains(modelId)) continue;
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
        if(extractingPage)frameModelEntries=out;return out;
    }

    private boolean matchesModelFilter(String modelId, ModelAssembly assembly, Set<String> auth, Set<String> stars) {
        if (!STATE.modelFilter.matchesAvailability(this.controller.isLocalOnlyModel(modelId))) {
            return false;
        }
        return switch (STATE.modelFilter) {
            case ALL -> true;
            case AUTH -> auth.contains(modelId) || !assembly.getTextureRegistry().isAuthModel();
            case STAR -> stars.contains(modelId);
            case NOT_STAR -> !stars.contains(modelId);
            case SERVER_AVAILABLE, LOCAL_ONLY -> true;
        };
    }

    private boolean matchesModelSearch(String modelId, ModelAssembly assembly, String query) {
        if (query.startsWith("@")) {
            Metadata metadata = assembly.getPresentationCapabilities().metadataAvailable()
                    ? assembly.getModelData().getExtraInfo() : null;
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
            STATE.selectedModelId = entry.modelId();
            if (!this.selectedModelIds.add(entry.modelId())) {
                this.selectedModelIds.remove(entry.modelId());
            }
            return;
        }
        STATE.selectedModelId = entry.modelId();
        if (entry.locked()) {
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.model_locked"), ChatFormatting.YELLOW);
            return;
        }
        ModelAssembly assembly = this.controller.assemblyOrNull(entry.modelId());
        if (assembly == null) {
            this.pendingModelApplyId = entry.modelId();
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.loading_model"), ChatFormatting.YELLOW);
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
                setStatus(Component.empty(), ChatFormatting.WHITE);
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
        if (STATE.multiSelectMode) {
            Set<String> stars = starModels();
            boolean remove = !selectedModelIds.isEmpty() && selectedModelIds.stream().allMatch(stars::contains);
            for (String id : List.copyOf(selectedModelIds)) if (stars.contains(id) == remove) this.controller.toggleStar(id);
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
    private void deleteSelectedModels(){
        List<String> models=List.copyOf(this.selectedModelIds.isEmpty()&&!STATE.selectedModelId.isBlank()?List.of(STATE.selectedModelId):this.selectedModelIds);if(models.isEmpty())return;
        confirmationAction=Component.translatable("gui.sparkle_morpher.model_panel.delete");confirmationTitle=Component.literal("删除 "+models.size()+" 个本地模型？");confirmationBody=Component.literal("仅删除所选本地模型。此操作无法撤销。\n"+String.join("\n",models.stream().limit(4).toList()));
        pendingConfirmation=()->{setStatus(this.controller.deleteModels(models));this.selectedModelIds.clear();STATE.selectedModelId="";STATE.selectedTextureId="";STATE.multiSelectMode=false;this.controller.reloadLocalModels(this::setStatus);};
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
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.download_queued", entry.name()), ChatFormatting.YELLOW);
        }
    }

    private void enqueueSelectedResources() {
        int added = this.controller.enqueueSelectedResources();
        setStatus(Component.translatable("gui.sparkle_morpher.model_panel.download_queued_count", added), added > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
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
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.source_required"), ChatFormatting.RED);
            return;
        }
        STATE.siteEditText = selected;
        refreshResources(false);
        init();
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

    private void openCloudUpload() {
        Collection<String> candidates = this.selectedModelIds.isEmpty() && STATE.selectedModelId != null && !STATE.selectedModelId.isBlank()
                ? List.of(STATE.selectedModelId)
                : new LinkedHashSet<>(this.selectedModelIds);
        List<String> localModels = candidates.stream().filter(ClientModelManager::isLocalOnlyModel).toList();
        InputUtil.setScreen(new CloudModelUploadScreen(this, localModels));
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
            if (modelAssembly != null && modelAssembly.getPresentationCapabilities().playerActionControlsAvailable()
                    && modelAssembly.getModelData() != null
                    && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                InputUtil.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
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
            settingsPanel().selectGroup(ModelPanelState.SettingGroup.DEVELOPER);
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
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.blaze3d_vulkan_gpu_renderer", GeneralConfig.ENABLE_BLAZE3D_VULKAN_GPU_RENDERER));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.blaze3d_in_pipeline_draw", GeneralConfig.ENABLE_BLAZE3D_IN_PIPELINE_DRAW));
        rows.add(bool(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.lazy_model_loading", GeneralConfig.LAZY_MODEL_LOADING));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.gpu_cache_limit", GeneralConfig.MAX_CACHED_GPU_MODELS, 0, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.cpu_cache_limit", GeneralConfig.MAX_RESIDENT_CPU_MODELS, 1, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.unused_model_ttl", GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 30, 86400, 30, "s"));
        rows.add(localSoundModeRow(ModelPanelState.SettingGroup.GENERAL));
        rows.add(actionRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.model_panel.setting.local_default",this::saveSelectedLocalDefault));
        rows.add(actionRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.model_panel.setting.hide_selected",this::hideSelectedLocalModel));
        rows.add(actionRow(ModelPanelState.SettingGroup.GENERAL,"gui.sparkle_morpher.model_panel.setting.restore_hidden",()->editLocalPreferences(com.micaftic.morpher.client.LocalDisplayPreferences::restoreHidden)));
        rows.add(bool(ModelPanelState.SettingGroup.DEVELOPER, "gui.sparkle_morpher.model_panel.setting.show_model_id_first", GeneralConfig.SHOW_MODEL_ID_FIRST));
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
        return rows.stream().filter(row -> row.group() == settingsPanel().selectedGroup()).toList();
    }

    private SettingsPanel settingsPanel() {
        if (this.settingsPanel == null) this.settingsPanel = new SettingsPanel(STATE);
        return this.settingsPanel;
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


    private SettingRow cloudConcurrencyRow(boolean upload) {
        int value = upload ? com.micaftic.morpher.cloud.client.CloudClientPerformance.uploads()
                : com.micaftic.morpher.cloud.client.CloudClientPerformance.processing();
        boolean custom = com.micaftic.morpher.cloud.client.CloudClientPerformance.preset()
                == com.micaftic.morpher.cloud.client.CloudClientPerformance.Preset.CUSTOM;
        return new SettingRow(ModelPanelState.SettingGroup.PERFORMANCE,
                "gui.sparkle_morpher.settings.cloud.performance." + (upload ? "uploads" : "processing"),
                null, value == 0 ? settingsText("cloud.performance.unlimited").getString() : Integer.toString(value), null,
                custom && value > 0 ? () -> com.micaftic.morpher.cloud.client.CloudClientPerformance.adjust(upload, -1) : null,
                custom && value < 64 ? () -> com.micaftic.morpher.cloud.client.CloudClientPerformance.adjust(upload, 1) : null,
                null, null);
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
        if (!assembly.getPresentationCapabilities().textureSelectionAvailable()) {
            return "";
        }
        if (STATE.selectedTextureId != null && !STATE.selectedTextureId.isBlank() && assembly.getAnimationBundle().getTextures().containsKey(STATE.selectedTextureId)) {
            return STATE.selectedTextureId;
        }
        return assembly.getAnimationBundle().getDefaultTextureName();
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
        if (assembly.getPresentationCapabilities().runtimeResident()) {
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





    private void renderIconButton(GuiGraphicsExtractor g,int mx,int my,int x,int y,IconGlyph icon,Component tooltip,Runnable action){
        int size=this.width<560?18:22;
        boolean hover=inside(mx,my,x,y,size,size);
        roundedSurface(g,x,y,size,size,3,hover?0x22FFFFFF:0x0CFFFFFF,0x22FFFFFF);
        int glyph=size<=18?12:14;
        drawIconScaled(g,icon,x+(size-glyph)/2,y+(size-glyph)/2,glyph);
        hit(x,y,size,size,tooltip,action);
    }

    private void drawIcon(GuiGraphicsExtractor g, IconGlyph icon, int x, int y) {
        g.blit(MODEL_PANEL_ICONS, x, y, x + 16, y + 16, icon.u / 128.0f, (icon.u + 16) / 128.0f, icon.v / 128.0f, (icon.v + 16) / 128.0f);
    }



    private void renderTextButton(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, int y, int w, int h, Component label, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        roundedSurface(g,x,y,w,h,UiElementLayout.BUTTON_RADIUS,hover?0x18FFFFFF:0x0CFFFFFF,0x22FFFFFF);
        drawCentered(g, Component.literal(trim(label.getString(), w - 8)), x + w / 2, UiElementLayout.centeredY(y,h,font.lineHeight), 0xFFFFFFFF);
        hit(x, y, w, h, label, action);
    }

    private void renderRowButton(GuiGraphicsExtractor g,int mouseX,int mouseY,int x,int y,int w,int h,Component label,boolean selected,Runnable action){
        boolean hover=inside(mouseX,mouseY,x,y,w,h);roundedSurface(g,x,y,w,h,UiElementLayout.BUTTON_RADIUS,selected?0x443F3F3F:hover?PANEL_HOVER:0x55303030,selected?RED:0x33FFFFFF);
        drawCentered(g,Component.literal(trim(label.getString(),w-12)),x+w/2,UiElementLayout.centeredY(y,h,font.lineHeight),TEXT);hit(x,y,w,h,label,action);
    }

    private void drawCentered(GuiGraphicsExtractor g,Component text,int centerX,int y,int color){
        settingsExtent(y+font.lineHeight);var styled=appearanceText(text);g.text(this.font,styled,centerX-this.font.width(styled)/2,y,color,false);
    }

    private void drawTitle(GuiGraphicsExtractor g,Component text,int x,int y){g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(1.25f,1.25f);g.text(this.font,appearanceText(text),0,0,TEXT,false);g.pose().popMatrix();}

    private void drawSection(GuiGraphicsExtractor g, Component text, int x, int y) {
        g.text(this.font, appearanceText(text.copy().withStyle(ChatFormatting.GRAY)), x, y, MUTED, false);
    }

    private void drawText(GuiGraphicsExtractor g, Component text, int x, int y) {
        settingsExtent(y+font.lineHeight);g.text(this.font, appearanceText(text), x, y, TEXT, false);
    }

    private void drawMuted(GuiGraphicsExtractor g, Component text, int x, int y) {
        settingsExtent(y+font.lineHeight);g.text(this.font, appearanceText(text), x, y, MUTED, false);
    }

    private void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fillGradient(x, y, x + w, y + h, color, color);
    }

    private void glassPanel(GuiGraphicsExtractor g,int x,int y,int w,int h){roundedSurface(g,x,y,w,h,4,0x09FFFFFF,0x1AFFFFFF);}

    private void secondaryGlassPanel(GuiGraphicsExtractor g,int x,int y,int w,int h){
        com.micaftic.morpher.core.gui.RoulettePanelStyle.secondaryGlassPanel(g,x,y,w,h);
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
        settingsExtent(y+h);this.hits.add(new Hit(x, y, w, h, tooltip, action));
    }

    private static boolean inside(double px, double py, int x, int y, int w, int h) {
        return px >= x && py >= y && px < x + w && py < y + h;
    }

    private String trim(String value,int maxWidth){
        String text=value==null?"":value;if(maxWidth<=0)return "";
        if(font.width(appearanceText(Component.literal(text)))<=maxWidth)return text;
        String ellipsis="...";int available=maxWidth-font.width(appearanceText(Component.literal(ellipsis)));
        if(available<=0)return "";
        return font.substrByWidth(appearanceText(Component.literal(text)),available).getString()+ellipsis;
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
        if (!hasSelectedModel()) return 0;
        if (STATE.modelSource != ModelPanelState.ModelSource.LOCAL) return cloudModelViewport().detailHeight();
        return STATE.compactPreviewExpanded ? 112 : 18;
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

    private int resourceListContentY(){return this.layout.top+105;}

    private int resourceListContentH(){return Math.max(1,this.height-this.layout.top-40-resourceListContentY());}

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
        if(previewDragging&&com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button())==0){detailYaw+=(float)dragX*.8f;detailPitch=Math.max(-35,Math.min(35,detailPitch+(float)dragY*.6f));return true;}
        if (com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button()) == 0 && draggingSourceTab != null) {
            for (var tab : sourceTabHits) if (inside(event.x(),event.y(),tab.x(),tab.y(),tab.w(),18) && !tab.instanceId().equals(draggingSourceTab)) {
                int from=STATE.cloudTabOrder.indexOf(draggingSourceTab),to=STATE.cloudTabOrder.indexOf(tab.instanceId());
                if(from>=0&&to>=0){STATE.cloudTabOrder.remove(from);STATE.cloudTabOrder.add(to,draggingSourceTab);}
                return true;
            }
        }

        if (this.draggingResourceScroll) {
            updateResourceScrollFromMouse(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if(previewDragging&&com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button())==0){previewDragging=false;setDragging(false);return true;}
        draggingSourceTab=null;
        if (this.draggingResourceScroll && com.micaftic.morpher.core.api.client.InputCodeAdapter.legacyMouseButtonFromSdlButton(event.button()) == 0) {
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

    private List<CloudAssetSummary> cloudVisibleEntries() {
        String query = STATE.cloudView == ModelPanelState.CloudView.RECENT || STATE.cloudView == ModelPanelState.CloudView.FAVORITES ? STATE.cloudSearchText : "";
        return CloudModelBrowserFilter.filter(STATE.cloudEntries, query, (STATE.cloudView == ModelPanelState.CloudView.PUBLIC || STATE.cloudView == ModelPanelState.CloudView.ALL) ? "PUBLIC" : cloudVisibilityFilter);
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

    private void renderCloudSelection(GuiGraphicsExtractor g, CloudAssetSummary entry, int x, int y) {
        border(g, x, y, 12, 12, BORDER);
        if (selectedCloudAssetIds.contains(entry.ref().assetId())) fill(g, x + 2, y + 2, 8, 8, 0xFF80CFB6);
    }

    private void renderCloudFavoriteControl(GuiGraphicsExtractor g, CloudAssetSummary entry, int mouseX, int mouseY, int x, int y) {
        boolean favorite = this.controller.isCloudFavorite(entry);
        if (favorite) fill(g, x, y, ICON, ICON, PANEL_ACTIVE);
        renderIconButton(g, mouseX, mouseY, x, y, IconGlyph.STAR,
                Component.translatable("gui.sparkle_morpher.model_panel.cloud." + (favorite ? "unfavorite" : "favorite")), () -> {
                    focusCloudAsset(entry);
                    setCloudFavorites(List.of(entry), !this.controller.isCloudFavorite(entry));
                });
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
        STATE.cloudPages = new com.micaftic.morpher.cloud.client.CloudCatalogCursorHistory();
        STATE.cloudBatchSize = 0;
        STATE.cloudCursor = "";
        STATE.cloudEntries.clear();
    }

    private void changeCloudVisibilityBatch(String visibility) {
        startCloudVisibilityBatch(cloudActionTargets(), visibility);
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

    private void renderCloudDetails(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,float partialTick){
        glassPanel(g,x,y,w,h);drawMuted(g,Component.translatable("gui.sparkle_morpher.ui.preview"),x+6,y+7);
        CloudAssetSummary entry=focusedCloudAsset();
        if(entry==null){drawMuted(g,Component.literal(trim(Component.translatable("gui.sparkle_morpher.model_panel.select_model").getString(),w-12)),x+6,y+24);return;}
        String id=this.controller.cloudModelId(entry);ModelAssembly assembly=this.controller.assemblyOrNull(id);boolean editable=canChangeCloudVisibility(entry);
        var d=ModelDetailLayout.measure(y,h,w,editable);
        if(assembly!=null)renderSelectedModelPreview(g,assembly,id,x+5,d.previewY(),w-10,d.previewHeight(),mx,my,partialTick);
        else drawCardLoading(g,x+5,d.previewY(),w-10,d.previewHeight());
        drawText(g,Component.literal(trim(cloudEntryName(entry),w-14)),x+7,d.nameY());
        drawMuted(g,Component.literal(trim(cloudEntryDetail(entry),w-14)),x+7,d.metadataY());
        if(assembly!=null)renderInlineTexture(g,mx,my,assembly,id,x+7,d.textureY(),w-14,true);
        if(editable){int vw=(w-18)/2;
            renderRowButton(g,mx,my,x+7,d.actionsY(),vw,d.controlHeight(),Component.translatable("gui.sparkle_morpher.model_panel.cloud.public"),entry.isPublic(),()->startCloudVisibilityBatch(List.of(entry),"PUBLIC"));
            renderRowButton(g,mx,my,x+11+vw,d.actionsY(),vw,d.controlHeight(),Component.translatable("gui.sparkle_morpher.model_panel.cloud.private"),!entry.isPublic(),()->startCloudVisibilityBatch(List.of(entry),"PRIVATE"));
        }
    }

        private int uiButtonHeight(){return this.width<560?18:this.width<800?20:22;}
    private int toolbarSearchWidth(){return this.width<560?106:this.width<800?170:260;}
    private int toolbarStride(){return this.width<560?21:this.width<800?24:26;}
    private int modelGridTop(){return this.layout.top+(this.width<560?58:66);}
    private int modelGridBottom(){return modelGridTop()+currentModelGridH();}
    private void toolbarButton(GuiGraphicsExtractor g,int mx,int my,int x,int y,IconGlyph icon,Component label,boolean selected,Runnable action){
        int h=uiButtonHeight(),size=this.width<560?11:this.width<800?12:14;
        boolean hover=inside(mx,my,x,y,h,h);
        roundedSurface(g,x,y,h,h,3,selected?0x21FFFFFF:hover?0x18FFFFFF:0x0CFFFFFF,selected?RED:0x22FFFFFF);
        drawIconScaled(g,icon,x+(h-size)/2,y+(h-size)/2,size);
        hit(x,y,h,h,label,action);
    }
    private void renderLocalToolbar(GuiGraphicsExtractor g,int mx,int my,int x,int y){
        if(STATE.multiSelectMode){renderBatchToolbar(g,mx,my,x,y,false);return;}
        int d=toolbarStride();
        toolbarButton(g,mx,my,x,y,IconGlyph.RELOAD,Component.translatable("gui.sparkle_morpher.model_panel.reload_models"),false,()->this.controller.reloadLocalModels(this::setStatus));
        toolbarButton(g,mx,my,x+d,y,IconGlyph.FOLDER,Component.translatable("gui.sparkle_morpher.model_select.new_category"),false,()->openCategoryPanel(""));
        toolbarDivider(g,x+d*2+1,y);
        ModelPanelState.ModelFilter[] filters={ModelPanelState.ModelFilter.ALL,ModelPanelState.ModelFilter.STAR,ModelPanelState.ModelFilter.NOT_STAR};
        IconGlyph[] icons={IconGlyph.RESOURCE,IconGlyph.STAR,IconGlyph.STAR_OFF};String[] keys={"all","star","not_star"};
        for(int i=0;i<3;i++){var f=filters[i];toolbarButton(g,mx,my,x+toolbarOffset(i+2),y,icons[i],Component.translatable("gui.sparkle_morpher.model_panel.filter."+keys[i]),STATE.modelFilter==f,()->setModelFilter(f));}
        toolbarDivider(g,x+toolbarOffset(5)-7,y);
        toolbarButton(g,mx,my,x+toolbarOffset(5),y,IconGlyph.UPLOAD,Component.translatable("gui.sparkle_morpher.model_panel.upload_cloud"),false,this::openCloudUpload);
        toolbarButton(g,mx,my,x+toolbarOffset(6),y,IconGlyph.IMPORT,Component.translatable("gui.sparkle_morpher.import.tooltip"),false,this::openImportPanel);
        toolbarButton(g,mx,my,x+toolbarOffset(7),y,IconGlyph.FOLDER,Component.translatable("gui.sparkle_morpher.open_model_folder.open"),false,this::openModelFolder);
        toolbarButton(g,mx,my,x+toolbarOffset(8),y,IconGlyph.MULTI,Component.translatable("gui.sparkle_morpher.model_panel.multi_select"),false,()->{STATE.multiSelectMode=true;selectedModelIds.clear();});
        toolbarButton(g,mx,my,x+toolbarOffset(9),y,IconGlyph.MORE,Component.translatable("gui.sparkle_morpher.ui.more"),modelMenuOpen,()->modelMenuOpen=!modelMenuOpen);
        toolbarDivider(g,x+toolbarOffset(10)-7,y);
        toolbarButton(g,mx,my,x+toolbarOffset(10),y,IconGlyph.ROULETTE,Component.translatable("key.sparkle_morpher.animation_roulette.desc"),false,this::openRoulette);
    }
    private void renderCloudToolbar(GuiGraphicsExtractor g,int mx,int my,int x,int y){
        if(cloudMultiSelectMode){renderBatchToolbar(g,mx,my,x,y,true);return;}
        int d=toolbarStride();
        toolbarButton(g,mx,my,x,y,IconGlyph.REFRESH,Component.translatable("gui.sparkle_morpher.model_panel.reload_models"),false,this::resetCloudPage);
        toolbarButton(g,mx,my,x+d,y,IconGlyph.FOLDER,Component.translatable("gui.sparkle_morpher.model_select.new_category"),false,()->openCategoryPanel(""));
        toolbarDivider(g,x+d*2+1,y);
        var views=STATE.cloudViews().stream().filter(v->v!=ModelPanelState.CloudView.PUBLIC).toList();
        IconGlyph[] icons={IconGlyph.RESOURCE,IconGlyph.CLOCK,IconGlyph.STAR,IconGlyph.USER};
        for(int i=0;i<views.size();i++){var v=views.get(i);String key=v==ModelPanelState.CloudView.ALL?"all_models":v.name().toLowerCase(Locale.ROOT);toolbarButton(g,mx,my,x+d*(i+2)+8,y,icons[i],Component.translatable("gui.sparkle_morpher.model_source."+key),STATE.cloudView==v,()->switchCloudView(v));}
        int bx=x+d*(views.size()+2)+16;toolbarDivider(g,bx-7,y);
        toolbarButton(g,mx,my,bx,y,IconGlyph.UPLOAD,Component.translatable("gui.sparkle_morpher.model_panel.upload_cloud"),false,this::openCloudUpload);
        toolbarButton(g,mx,my,bx+d,y,IconGlyph.MULTI,Component.translatable("gui.sparkle_morpher.model_panel.multi_select"),false,()->{cloudMultiSelectMode=true;selectedCloudAssetIds.clear();});
        toolbarButton(g,mx,my,bx+d*2,y,IconGlyph.MORE,Component.translatable("gui.sparkle_morpher.ui.more"),modelMenuOpen,()->modelMenuOpen=!modelMenuOpen);
    }
    private CloudModelViewport cloudModelViewport() { return CloudModelViewport.measure(currentModelGridH(), false, false); }
    private void renderInlineTexture(GuiGraphicsExtractor g,int mx,int my,ModelAssembly assembly,String modelId,int x,int y,int w,boolean cloud){
        List<String> textures=assembly.getTextureNames();if(textures.isEmpty())return;String texture=selectedTextureOrDefault(assembly);int h=uiButtonHeight();
        roundedSurface(g,x,y,w,h,3,0x0CFFFFFF,0x22FFFFFF);drawText(g,Component.literal(trim(Component.translatable("gui.sparkle_morpher.model_panel.textures").getString()+": "+texture,w-22)),x+6,UiElementLayout.centeredY(y,h,font.lineHeight));drawIconScaled(g,IconGlyph.CHEVRON_DOWN,x+w-15,y+(h-10)/2,10);
        hit(x,y,w,h,Component.literal(texture),()->{if(!modelId.equals(textureMenuModel)){textureMenuOpen=true;textureMenuModel=modelId;inlineTexturePage=0;}else textureMenuOpen=!textureMenuOpen;});
        if(textureMenuOpen&&modelId.equals(textureMenuModel)){
            int visible=Math.min(6,textures.size()),pages=(textures.size()+5)/6;inlineTexturePage=clamp(inlineTexturePage,0,pages-1);int extra=pages>1?20:0,top=Math.max(this.layout.top+32,y-visible*18-extra-3);
            roundedSurface(g,x,top,w,visible*18+extra+2,3,0xFC2C2C2C,0x33FFFFFF);
            for(int i=0;i<visible&&inlineTexturePage*6+i<textures.size();i++){String t=textures.get(inlineTexturePage*6+i);renderRowButton(g,mx,my,x+1,top+1+i*18,w-2,18,Component.literal(trim(t,w-12)),t.equals(texture),()->{STATE.selectedTextureId=t;textureMenuOpen=false;if(!(STATE.multiSelectMode||cloudMultiSelectMode))applySelectedTexture();});}
            if(pages>1){int yy=top+visible*18;renderTextButton(g,mx,my,x+2,yy,22,18,Component.literal("‹"),()->inlineTexturePage=Math.max(0,inlineTexturePage-1));drawCentered(g,Component.literal((inlineTexturePage+1)+" / "+pages),x+w/2,yy+5,MUTED);renderTextButton(g,mx,my,x+w-24,yy,22,18,Component.literal("›"),()->inlineTexturePage=Math.min(pages-1,inlineTexturePage+1));}
        }
    }
    private void renderSourceTabMenu(GuiGraphicsExtractor g,int mx,int my){
        if(!sourceTabMenu)return;
        var profiles=CloudManagementScreen.management().registry().profiles();
        int w=Math.min(180,this.layout.contentWidth-12),x=modelListX()+44,y=this.layout.contentTop+28;
        glassPanel(g,x,y,w,(profiles.size()+1)*21+8);
        for(int i=0;i<profiles.size();i++){
            var profile=profiles.get(i);
            renderTextButton(g,mx,my,x+4,y+4+i*21,w-8,18,Component.literal(CloudManagementScreen.displayName(profile)),()->{
                STATE.closedCloudTabs.remove(profile.instanceId());sourceTabMenu=false;switchCloudInstance(profile);
            });
        }
        renderTextButton(g,mx,my,x+4,y+4+profiles.size()*21,w-8,18,Component.translatable("gui.sparkle_morpher.ui.add_instance"),()->{sourceTabMenu=false;openCloudInstanceForm();});
    }
    private void roundedSurface(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color,int outline){com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedSurface(g,x,y,w,h,radius,color,outline);}
    private void roundedFill(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){com.micaftic.morpher.core.gui.RoulettePanelStyle.roundedFill(g,x,y,w,h,radius,color);}
    private int toolbarOffset(int index){return index*toolbarStride()+(index>=2?8:0)+(index>=5?8:0)+(index>=10?8:0);}
    private void toolbarDivider(GuiGraphicsExtractor g,int x,int y){fill(g,x,y+4,1,(this.width<560?18:22)-8,0x22FFFFFF);}
    private void renderBatchToolbar(GuiGraphicsExtractor g,int mx,int my,int x,int y,boolean cloud){
        int d=toolbarStride();
        toolbarButton(g,mx,my,x,y,IconGlyph.CHECK,Component.translatable("gui.done"),true,()->{if(cloud){cloudMultiSelectMode=false;selectedCloudAssetIds.clear();}else clearModelSelection();});
        toolbarButton(g,mx,my,x+d,y,IconGlyph.MULTI,Component.translatable("gui.sparkle_morpher.ui.select_page"),false,()->{
            if(cloud){CloudModelPage page=cloudGridPage(modelListW(),currentModelGridH());for(var e:cloudVisibleEntries().subList(page.start(),page.end()))selectedCloudAssetIds.add(e.ref().assetId());}
            else selectAllVisibleModels();
        });
        toolbarButton(g,mx,my,x+d*2,y,IconGlyph.CLOSE,Component.translatable("gui.sparkle_morpher.model_panel.clear_selection"),false,()->{if(cloud)selectedCloudAssetIds.clear();else selectedModelIds.clear();});
        toolbarDivider(g,x+d*3+1,y);int bx=x+d*3+8;
        toolbarButton(g,mx,my,bx,y,IconGlyph.STAR,Component.translatable("gui.sparkle_morpher.model_panel.cloud.favorite"),false,()->{if(cloud)setCloudFavorites(cloudActionTargets(),true);else setLocalSelectionFavorites(true);});
        toolbarButton(g,mx,my,bx+d,y,IconGlyph.STAR_OFF,Component.translatable("gui.sparkle_morpher.model_panel.cloud.unfavorite"),false,()->{if(cloud)setCloudFavorites(cloudActionTargets(),false);else setLocalSelectionFavorites(false);});
        if(cloud){
            toolbarButton(g,mx,my,bx+d*2,y,IconGlyph.GLOBE,Component.translatable("gui.sparkle_morpher.cloud_upload.make_public"),false,()->changeCloudVisibilityBatch("PUBLIC"));
            toolbarButton(g,mx,my,bx+d*3,y,IconGlyph.LOCK,Component.translatable("gui.sparkle_morpher.cloud_upload.make_private"),false,()->changeCloudVisibilityBatch("PRIVATE"));
        }else{
            toolbarButton(g,mx,my,bx+d*2,y,IconGlyph.FOLDER,Component.translatable("gui.sparkle_morpher.model_panel.move"),false,()->openCategoryPanel(""));
            toolbarButton(g,mx,my,bx+d*3,y,IconGlyph.UPLOAD,Component.translatable("gui.sparkle_morpher.model_panel.upload_cloud"),false,this::openCloudUpload);
            toolbarButton(g,mx,my,bx+d*4,y,IconGlyph.DELETE,Component.translatable("gui.sparkle_morpher.model_panel.delete"),false,this::deleteSelectedModels);
        }
    }
    private void setLocalSelectionFavorites(boolean value){
        Set<String> stars=starModels();for(String id:List.copyOf(selectedModelIds))if(stars.contains(id)!=value)this.controller.toggleStar(id);
    }
    private void renderTabClose(GuiGraphicsExtractor g,int mx,int my,int x,int y,Component label,Runnable action){drawIconScaled(g,IconGlyph.CLOSE,x,y,8);hit(x-2,y-3,12,14,label,action);}
    private void renderModelMoreMenu(GuiGraphicsExtractor g,int mx,int my){
        if(!modelMenuOpen)return;
        int w=150,x=this.width-this.layout.left-w-10,y=this.layout.top+60;
        roundedSurface(g,x,y,w,70,4,0xF0242424,0x33FFFFFF);
        renderTextButton(g,mx,my,x+4,y+4,w-8,18,Component.translatable("gui.sparkle_morpher.model_panel.toggle_favorite"),()->{modelMenuOpen=false;toggleSelectedStar();});
        renderTextButton(g,mx,my,x+4,y+26,w-8,18,Component.translatable("gui.sparkle_morpher.ui.cloud_account"),()->{modelMenuOpen=false;openCloudAccount();});
        renderTextButton(g,mx,my,x+4,y+48,w-8,18,Component.translatable("gui.sparkle_morpher.model_panel.delete"),()->{modelMenuOpen=false;if(STATE.modelSource==ModelPanelState.ModelSource.LOCAL)deleteSelectedModels();});
    }
    private int settingRowHeight(){return SettingsViewport.rowHeight(width);}
    private void renderAppearanceSettings(GuiGraphicsExtractor g,int mx,int my,SettingsPanelLayout.Columns columns){
        int x=columns.detailX(),y=columns.top(),w=columns.detailWidth(),bottom=y+columns.height()-8;
        boolean compact=this.width<560;int summaryH=compact?30:this.width<800?38:46;
        roundedSurface(g,x,y,w,summaryH,4,0x0AFFFFFF,0x22FFFFFF);
        drawText(g,Component.translatable("gui.sparkle_morpher.ui.frosted"),x+9,y+7);
        if(!compact)drawMuted(g,Component.translatable("gui.sparkle_morpher.ui.neutral_red"),x+9,y+summaryH-13);
        String selected=appearance.font.equals("minecraft:default")?"Minecraft 原生":SystemFontCatalog.family(appearance.font).orElse(appearance.font);
        String[] keys={"font","game_scale","blur","opacity"};
        String[] values={selected,"跟随游戏",appearance.blur==0?"关闭":Integer.toString(appearance.blur),(100-appearance.opacity)+"%"};
        int rowY=y+summaryH+8,rh=Math.min(compact?26:30,Math.max(26,(bottom-rowY-20)/4));
        int buttonW=Math.min(compact?110:this.width<800?130:180,w/2),bx=x+w-buttonW-8,bh=uiButtonHeight();
        for(int i=0;i<keys.length;i++){
            int yy=rowY+i*rh,index=i,cy=yy+(rh-bh)/2;
            drawText(g,Component.translatable("gui.sparkle_morpher.ui."+keys[i]),x+9,UiElementLayout.centeredY(yy,rh,font.lineHeight));
            if(index==1){roundedSurface(g,bx,cy,buttonW,bh,4,0x0CFFFFFF,0x22FFFFFF);drawCentered(g,Component.literal(values[i]),bx+buttonW/2,UiElementLayout.centeredY(cy,bh,font.lineHeight),MUTED);}
            else if(index==0)renderTextButton(g,mx,my,bx,cy,buttonW,bh,Component.literal(trim(values[i],buttonW-12)),()->{fontMenuOpen=!fontMenuOpen;fontPage=0;});
            else {
                int arrow=bh,gap=3;renderTextButton(g,mx,my,bx,cy,arrow,bh,Component.literal("−"),()->changeAppearance(index,-1));
                drawCentered(g,Component.literal(values[i]),bx+buttonW/2,UiElementLayout.centeredY(cy,bh,font.lineHeight),TEXT);
                renderTextButton(g,mx,my,bx+buttonW-arrow,cy,arrow,bh,Component.literal("+"),()->changeAppearance(index,1));
            }
        }
        if(rowY+4*rh+font.lineHeight<=bottom)drawText(g,Component.translatable("gui.sparkle_morpher.ui.font_preview"),x+9,rowY+4*rh+4);
        if(fontMenuOpen){
            var choices=new ArrayList<SystemFontCatalog.Entry>();choices.add(new SystemFontCatalog.Entry("minecraft:default","Minecraft 原生"));
            Minecraft.getInstance().getResourceManager().listResources("font",id->id.getPath().endsWith(".json")).keySet().stream()
                    .filter(id->!id.getNamespace().equals("minecraft")||id.getPath().equals("font/uniform.json"))
                    .map(id->id.getNamespace()+":"+id.getPath().substring(5,id.getPath().length()-5)).sorted()
                    .forEach(id->choices.add(new SystemFontCatalog.Entry(id,id)));
            choices.addAll(SystemFontCatalog.entries());
            int menuW=Math.min(w-8,compact?190:260),menuX=x+w-menuW-4,menuY=rowY+rh+2;
            int visible=Math.max(1,Math.min(8,(bottom-menuY-26)/21)),pages=Math.max(1,(choices.size()+visible-1)/visible);
            fontPage=clamp(fontPage,0,pages-1);int menuH=visible*21+26;
            roundedSurface(g,menuX,menuY,menuW,menuH,4,0xFF242424,0x55FFFFFF);
            hit(menuX,menuY,menuW,menuH,Component.empty(),()->{});
            for(int i=0;i<visible&&fontPage*visible+i<choices.size();i++){
                var choice=choices.get(fontPage*visible+i);
                renderRowButton(g,mx,my,menuX+3,menuY+3+i*21,menuW-6,19,Component.literal(trim(choice.family(),menuW-16)),appearance.font.equals(choice.id()),()->{appearance.font=choice.id();appearance.save();fontMenuOpen=false;});
            }
            int py=menuY+menuH-22;renderTextButton(g,mx,my,menuX+4,py,22,18,Component.literal("‹"),()->fontPage=Math.max(0,fontPage-1));
            drawCentered(g,Component.literal((fontPage+1)+" / "+pages),menuX+menuW/2,py+5,MUTED);
            renderTextButton(g,mx,my,menuX+menuW-26,py,22,18,Component.literal("›"),()->fontPage=Math.min(pages-1,fontPage+1));
        }
    }
    private void changeAppearance(int index,int delta){
        if(index==2)appearance.blur=clamp(appearance.blur+delta*2,0,16);
        if(index==3)appearance.opacity=clamp(appearance.opacity-delta*5,0,100);
        appearance.save();
    }

    private void renderScopesReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        int x=layout.contentLeft,w=layout.contentWidth,bh=uiButtonHeight(),rh=settingRowHeight()+8,toolbarY=layout.top+42;
        if(scopeCreateForm){
            int yy=toolbarY;EditBox[] fields={cloudScopeIdBox,cloudScopeNameBox,cloudWorldEpochBox};String[] keys={"scope_id","scope_name","world_epoch"};
            for(int i=0;i<fields.length;i++){drawText(g,cloudText(keys[i]),x+8,yy);renderReferenceField(g,mx,my,pt,fields[i],x+8,yy+15,Math.min(530,w-16),bh);yy+=bh+30;}
            renderTextButton(g,mx,my,x+w-192,yy,76,bh,Component.translatable("gui.cancel"),()->scopeCreateForm=false);
            renderPrimaryButton(g,mx,my,x+w-108,yy,100,bh,cloudText("create_scope"),()->{createCloudScope();scopeCreateForm=false;});return;
        }
        if(cloudScopeIdBox!=null)cloudScopeIdBox.visible=false;if(cloudScopeNameBox!=null)cloudScopeNameBox.visible=false;if(cloudWorldEpochBox!=null)cloudWorldEpochBox.visible=false;
        toolbarButton(g,mx,my,x+w-2*bh-12,toolbarY,IconGlyph.REFRESH,cloudText("refresh_scopes"),false,this::refreshCloudScopes);
        toolbarButton(g,mx,my,x+w-bh-8,toolbarY,IconGlyph.PLUS,cloudText("create_scope"),false,()->scopeCreateForm=true);
        int header=toolbarY+bh+10;fill(g,x,header,w,22,0x0CFFFFFF);drawMuted(g,cloudText("scope_name"),x+8,header+7);drawMuted(g,cloudText("scope_id"),x+w/3,header+7);drawMuted(g,Component.literal("访问权限"),x+w*2/3,header+7);
        var scopes=CloudManagementScreen.management().snapshot().scopes();var selected=CloudManagementScreen.management().snapshot().selectedScope();int yy=header+26;
        for(var scope:scopes){
            boolean chosen=selected!=null&&selected.scopeId().equals(scope.scopeId());
            roundedSurface(g,x+4,yy,w-8,rh-4,4,chosen?0x16FFFFFF:0x06FFFFFF,chosen?0x55DC4F5D:0x10FFFFFF);
            int ty=UiElementLayout.centeredY(yy,rh-4,font.lineHeight);drawText(g,Component.literal(trim(scope.name(),w/3-18)),x+10,ty);drawMuted(g,Component.literal(trim(scope.scopeId(),w/3-10)),x+w/3,ty);drawMuted(g,Component.literal("Cloud"),x+w*2/3,ty);
            hit(x+4,yy,w-8,rh-4,Component.literal(scope.name()),()->selectCloudScope(scope.scopeId()));yy+=rh;
        }
        yy+=8;String[] labels={"当前范围","角色与认领","模型与权限"};Runnable[] actions={this::refreshCloudScopes,()->openCloudIdentityPage(CloudAccountPage.CLAIM),()->InputUtil.setScreen(new CloudTargetManagementScreen(this,CloudManagementScreen.management()))};
        for(int i=0;i<3;i++){drawText(g,Component.literal(labels[i]),x+8,UiElementLayout.centeredY(yy,rh,font.lineHeight));renderTextButton(g,mx,my,x+w-158,yy+4,150,bh,cloudText(i==0?"refresh_scopes":i==1?"identity_panel.title":"targets_acl"),actions[i]);yy+=rh;}
        renderTextButton(g,mx,my,x+w-192,yy+8,76,bh,Component.translatable("gui.cancel"),this::backFromCloud);
        renderPrimaryButton(g,mx,my,x+w-108,yy+8,100,bh,cloudText("join_selected"),()->{if(selected!=null){cloudScopeIdBox.setValue(selected.scopeId());joinCloudScope();}});
    }
    private void renderIdentityReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        ensureCloudIdentityFlow();int x=this.layout.contentLeft,w=this.layout.contentWidth;
        String title=cloudAccountPage==CloudAccountPage.CLAIM?"identity_panel.claim_title":cloudAccountPage==CloudAccountPage.APPROVAL?"identity_panel.admin_title":"identity_panel.title";
        pageHeader(g,mx,my,cloudText(title),cloudText("account_intro"),this::backFromCloud);
        var flow=cloudIdentityFlow;
        if(flow==null){drawText(g,cloudText("identity_panel.login_required"),x+16,90);renderPrimaryButton(g,mx,my,x+16,130,140,22,cloudText("login"),()->openCloudLoginForm(false));return;}
        cloudPanelStatus=cloudText("identity_panel."+(flow.busy()?"working":flow.message()));
        if(cloudAccountPage==CloudAccountPage.IDENTITY){
            renderReferenceTab(g,mx,my,x,70,92,22,cloudText("identity_panel.identities"),!identityProvidersTab,()->identityProvidersTab=false);
            renderReferenceTab(g,mx,my,x+98,70,92,22,Component.literal("世界角色"),identityProvidersTab,()->identityProvidersTab=true);
            toolbarButton(g,mx,my,x+w-22,70,IconGlyph.REFRESH,cloudText("identity_panel.refresh"),false,flow::refresh);
            fill(g,x,108,w,24,0x0CFFFFFF);drawMuted(g,Component.literal("身份"),x+10,116);drawMuted(g,Component.literal("认证提供方"),x+w/2,116);drawMuted(g,Component.literal("状态"),x+w*4/5,116);
            int yy=132;
            for(var identity:flow.catalog().identities()){
                boolean chosen=identity.equals(flow.selectedIdentity());
                if(chosen){fill(g,x,yy,w,40,0x16FFFFFF);fill(g,x,yy+8,2,24,RED);}drawText(g,Component.literal(trim(identity.displayName(),w/2-20)),x+10,yy+16);drawMuted(g,Component.literal(identity.identityRef().kind().name()),x+w/2,yy+16);drawMuted(g,cloudText("identity_panel."+("VERIFIED".equals(identity.verificationStatus())?"verified_short":"offline_short")),x+w*4/5,yy+16);
                hit(x,yy,w,40,Component.literal(identity.displayName()),()->flow.selectIdentity(identity.identityId()));fill(g,x,yy+43,w,1,0x16FFFFFF);yy+=44;
            }
            int ry=yy+8;
            drawText(g,cloudText("identity_panel.verify"),x+10,ry+12);identityAction(g,mx,my,x+w-150,ry+7,150,"verify",!flow.busy(),flow::verify);
            drawText(g,cloudText("identity_panel.use_world"),x+10,ry+60);identityAction(g,mx,my,x+w-150,ry+55,150,"use_world",!flow.busy(),()->openCloudIdentityPage(CloudAccountPage.CLAIM));
            if(!flow.catalog().providers().isEmpty()){
                var provider=flow.catalog().providers().stream().filter(p->p.providerId().equals(flow.providerId())).findFirst().orElse(flow.catalog().providers().getFirst());
                int providerW=Math.max(80,Math.min(300,w-170));
                renderTextButton(g,mx,my,x+10,ry+103,providerW,uiButtonHeight(),Component.literal(provider.displayName()),flow::nextProvider);
                networkAction(g,mx,my,x+providerW+18,ry+103,Math.min(140,w-providerW-28),Component.literal("绑定当前游戏身份"),!cloudFormBusy&&!flow.busy(),()->bindCloudIdentity(provider.providerId()));
            }
            return;
        }
        if(cloudAccountPage==CloudAccountPage.CLAIM){
            var panels=accountPanelLayout();renderCloudIdentityList(g,mx,my,panels);
            int dx=panels.detailX()+8,dw=panels.detailWidth()-16;
            drawTitle(g,cloudText("identity_panel.claim_help"),dx,82);renderCloudIdentityTargets(g,mx,my,dx,122,dw);
            int buttons=246;identityAction(g,mx,my,dx,buttons,Math.min(160,dw/2-4),"register",flow.canRegister(),flow::register);identityAction(g,mx,my,dx+Math.min(168,dw/2+4),buttons,Math.min(180,dw/2-4),"request",flow.canRequest(),flow::request);
            drawText(g,cloudText("identity_panel.code_help"),dx,306);renderReferenceField(g,mx,my,pt,cloudIdentityCodeBox,dx,330,dw,24);
            identityAction(g,mx,my,dx+dw-110,382,110,"redeem",flow.canRedeem(),flow::redeem);return;
        }
        renderReferenceTab(g,mx,my,x,70,92,22,Component.literal("待审批"),!identityProvidersTab,()->identityProvidersTab=false);
        renderReferenceTab(g,mx,my,x+98,70,92,22,Component.literal("邀请码"),identityProvidersTab,()->identityProvidersTab=true);
        if(!flow.canManage()){identityLine(g,"permission_denied",x+16,120,w-32);return;}
        fill(g,x,108,w,24,0x0CFFFFFF);drawMuted(g,Component.literal("申请人"),x+10,116);drawMuted(g,Component.literal("世界角色"),x+w/3,116);drawMuted(g,Component.literal("状态"),x+w*2/3,116);
        int yy=132;for(var binding:flow.pending()){
            var current=binding;String name=binding.identityDisplayName()==null?binding.entityUuid():binding.identityDisplayName();
            drawText(g,Component.literal(trim(name,w/3-18)),x+10,yy+15);drawText(g,Component.literal(trim(flow.targetName(binding.targetId()),w/3-18)),x+w/3,yy+15);drawMuted(g,Component.literal(binding.status()),x+w*2/3,yy+15);
            hit(x,yy,w-180,38,Component.literal(name),()->flow.selectBinding(current.bindingId()));
            identityAction(g,mx,my,x+w-166,yy+7,76,"approve",!flow.busy(),()->{flow.selectBinding(current.bindingId());flow.approve(true);});identityAction(g,mx,my,x+w-84,yy+7,76,"reject",!flow.busy(),()->{flow.selectBinding(current.bindingId());flow.approve(false);});yy+=44;
        }
        if(flow.claimPolicy()){
            drawTitle(g,cloudText("identity_panel.issue"),x+10,yy+22);renderCloudIdentityTargets(g,mx,my,x+10,yy+54,Math.min(300,w/3));
            renderTextButton(g,mx,my,x+w/3+20,yy+54,Math.min(240,w/3),22,Component.literal(flow.selectedPlayer()==null?cloudText("identity_panel.no_players").getString():flow.selectedPlayer().name()),flow::nextPlayer);
            identityAction(g,mx,my,x+w-180,yy+54,170,"issue",flow.canIssue(),flow::issue);
            if(flow.issued()!=null){String code=flow.issued().code();drawText(g,Component.literal(flow.issuedExpired()?cloudText("identity_panel.code_expired").getString():code),x+10,yy+164);identityAction(g,mx,my,x+w-180,yy+148,80,"copy",!flow.issuedExpired(),()->{minecraft.keyboardHandler.setClipboard(code);flow.copied();});identityAction(g,mx,my,x+w-90,yy+148,80,"revoke",!flow.busy(),flow::revoke);}
        }
    }

    private void renderResourceDetailsReference(GuiGraphicsExtractor g,int mx,int my){
        if(!settingsDrawingBody){var selected=selectedResource();Component heading=Component.literal(selected==null?"资源详情":selected.name());Runnable back=()->resourceRoute=ResourceRoute.CATALOG;pageHeader(g,mx,my,heading,Component.empty(),back);renderSettingsViewport(g,mx,my,layout.contentLeft,layout.top+40,layout.contentWidth,height-layout.top-12,offset->renderResourceDetailsReference(g,mx,my+offset));return;}

        var entry=selectedResource();int x=this.layout.contentLeft,w=this.layout.contentWidth;pageHeader(g,mx,my,Component.literal(entry==null?"资源详情":entry.name()),Component.literal(this.controller.selectedSite()),()->resourceRoute=ResourceRoute.CATALOG);if(entry==null)return;
        int px=x,py=this.layout.top+62,pw=Math.min(220,w/3),ph=Math.min(324,this.height-py-100);roundedSurface(g,px,py,pw,ph,4,0x08FFFFFF,0x20FFFFFF);
        var cover=this.controller.resourcePreview(entry);if(cover==null||!drawCoverImage(g,cover,px+4,py+4,pw-8,ph-8))drawIconScaled(g,IconGlyph.MODEL,px+pw/2-16,py+ph/2-16,32);
        int dx=x+pw+18,dw=w-pw-18;drawTitle(g,Component.literal(entry.name()),dx,py+8);drawMuted(g,Component.literal(trim(entry.author()+" · "+this.controller.formatBytes(entry.size()),dw)),dx,py+38);drawText(g,Component.literal("资源说明"),dx,py+70);int yy=py+100;for(var line:wrap(entry.description(),dw,30)){drawMuted(g,Component.literal(line),dx,yy);yy+=14;}
        int ty=Math.max(yy+24,py+130);fill(g,dx,ty,dw,24,0x0CFFFFFF);drawMuted(g,Component.literal("文件"),dx+10,ty+8);drawMuted(g,Component.literal("大小"),dx+dw-110,ty+8);drawText(g,Component.literal(trim(entry.fileName(),dw-130)),dx+10,ty+42);drawMuted(g,Component.literal(this.controller.formatBytes(entry.size())),dx+dw-110,ty+42);drawMuted(g,Component.literal(trim(entry.tags(),dw)),dx,ty+100);
        int by=Math.max(this.height-this.layout.top-34,ty+126);renderTextButton(g,mx,my,x+w-184,by,76,22,Component.translatable("gui.cancel"),()->resourceRoute=ResourceRoute.CATALOG);renderPrimaryButton(g,mx,my,x+w-100,by,100,22,Component.literal("加入下载队列"),()->{enqueueResource(entry);resourceRoute=ResourceRoute.QUEUE;});
    }
    private void renderDownloadQueueReference(GuiGraphicsExtractor g,int mx,int my){
        int x=this.layout.contentLeft,w=this.layout.contentWidth;var tasks=this.controller.downloadTasks();pageHeader(g,mx,my,Component.literal("下载队列"),Component.literal(tasks.size()+" 个任务"),()->resourceRoute=ResourceRoute.CATALOG);
        renderTextButton(g,mx,my,x+w-110,this.layout.top+62,110,22,Component.literal("清除已完成"),this.controller::clearFinishedDownloads);
        int hy=this.layout.top+100;fill(g,x,hy,w,24,0x0CFFFFFF);String[] labels={"资源","状态 / 进度","大小","操作"};double[] cols={0,.25,.61,.86};for(int i=0;i<4;i++)drawMuted(g,Component.literal(labels[i]),x+10+(int)(w*cols[i]),hy+8);
        int rows=Math.max(1,(this.height-hy-90)/50),start=clamp(STATE.resourceScroll,0,Math.max(0,tasks.size()-rows));STATE.resourceScroll=start;
        for(int i=start;i<Math.min(tasks.size(),start+rows);i++){var task=tasks.get(i);int yy=hy+24+(i-start)*50;boolean failed=task.state()==com.micaftic.morpher.client.gui.resource.download.DownloadQueue.TaskState.FAILED;drawText(g,Component.literal(trim(task.name(),w/4-18)),x+10,yy+17);drawMuted(g,Component.literal(trim(task.message().getString(),w/3)),x+(int)(w*.25),yy+17);drawMuted(g,Component.literal(this.controller.formatBytes(task.byteLength())),x+(int)(w*.61),yy+17);fill(g,x+8,yy+42,(int)((w*.59-16)*task.progress()),2,failed?RED:0xFFBDBDBD);fill(g,x,yy+49,w,1,0x14FFFFFF);
            renderTextButton(g,mx,my,x+(int)(w*.86),yy+10,Math.max(60,w-(int)(w*.86)-8),22,Component.literal(failed?"重试":task.state()==com.micaftic.morpher.client.gui.resource.download.DownloadQueue.TaskState.DONE?"打开":"取消"),()->{if(failed)this.controller.retryDownload(task);else if(task.state()==com.micaftic.morpher.client.gui.resource.download.DownloadQueue.TaskState.DONE)openModelFolder();else this.controller.cancelCurrentDownload();});
            if(failed)hit(x,yy,(int)(w*.82),42,task.message(),()->{resourceFailure=task;resourceRoute=ResourceRoute.ERROR;});
        }
        renderTextButton(g,mx,my,x,this.height-this.layout.top-34,120,22,Component.literal("查看失败详情"),()->{resourceFailure=tasks.stream().filter(t->t.state()==com.micaftic.morpher.client.gui.resource.download.DownloadQueue.TaskState.FAILED).findFirst().orElse(null);if(resourceFailure!=null)resourceRoute=ResourceRoute.ERROR;});
    }
    private void renderDownloadErrorReference(GuiGraphicsExtractor g,int mx,int my){
        if(!settingsDrawingBody){Component heading=Component.literal("下载失败详情");Runnable back=()->resourceRoute=ResourceRoute.QUEUE;pageHeader(g,mx,my,heading,Component.empty(),back);renderSettingsViewport(g,mx,my,layout.contentLeft,layout.top+40,layout.contentWidth,height-layout.top-12,offset->renderDownloadErrorReference(g,mx,my+offset));return;}

        int x=this.layout.contentLeft,w=this.layout.contentWidth;pageHeader(g,mx,my,Component.literal("下载失败详情"),Component.literal(resourceFailure==null?"":resourceFailure.name()),()->resourceRoute=ResourceRoute.QUEUE);if(resourceFailure==null)return;
        int fw=Math.min(530,w-32),fx=x+16;drawText(g,Component.literal("失败原因"),fx,70);roundedSurface(g,fx,87,fw,24,3,0x0CFFFFFF,0x20FFFFFF);drawText(g,Component.literal(trim(resourceFailure.message().getString(),fw-12)),fx+6,94);drawMuted(g,Component.literal("保留来源与任务信息，便于定位"),fx,132);drawText(g,Component.literal("来源"),fx,163);roundedSurface(g,fx,185,fw,24,3,0x0CFFFFFF,0x20FFFFFF);drawText(g,Component.literal(trim(resourceFailure.sourceUrl(),fw-12)),fx+6,192);
        int by=this.height-this.layout.top-34;renderTextButton(g,mx,my,x+w-184,by,76,22,Component.translatable("gui.cancel"),()->resourceRoute=ResourceRoute.QUEUE);renderPrimaryButton(g,mx,my,x+w-100,by,100,22,Component.literal("重试"),()->{this.controller.retryDownload(resourceFailure);resourceRoute=ResourceRoute.QUEUE;});
    }
    private void renderSourceEditReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        if(!settingsDrawingBody){Component heading=Component.literal("添加 / 编辑资源来源");Runnable back=()->{sourceEditMode=false;init();};pageHeader(g,mx,my,heading,Component.empty(),back);renderSettingsViewport(g,mx,my,layout.contentLeft,layout.top+40,layout.contentWidth,height-layout.top-12,offset->renderSourceEditReference(g,mx,my+offset,pt));return;}

        int x=this.layout.contentLeft,w=this.layout.contentWidth,fx=x+16,fw=Math.min(530,w-32);pageHeader(g,mx,my,Component.literal("添加 / 编辑资源来源"),Component.literal("资源站 / 来源管理"),()->{sourceEditMode=false;init();});
        drawText(g,Component.literal("名称"),fx,70);renderReferenceField(g,mx,my,pt,sourceNameBox,fx,87,fw,24);drawMuted(g,Component.literal("目录中显示的名称"),fx,132);drawText(g,Component.literal("来源类型"),fx,146);String url=siteEditBox==null?"":siteEditBox.getValue();renderTextButton(g,mx,my,fx,163,fw,24,Component.literal(url.contains("github.com")?"GitHub":"Index JSON"),()->setStatus(Component.literal("来源类型由地址自动识别")));drawText(g,Component.literal("地址"),fx,222);renderReferenceField(g,mx,my,pt,siteEditBox,fx,239,fw,24);drawMuted(g,Component.literal("填写完整可访问的资源目录地址"),fx,284);
        int by=Math.max(this.height-this.layout.top-34,318);renderTextButton(g,mx,my,x+w-184,by,76,22,Component.translatable("gui.cancel"),()->{sourceEditMode=false;init();});renderPrimaryButton(g,mx,my,x+w-100,by,100,22,Component.literal("保存来源"),()->{STATE.siteEditText=siteEditBox.getValue();saveSite();ResourceSourceLabels.put(STATE.siteEditText,sourceNameDraft);sourceEditMode=false;init();});
    }


    private void renderDeveloperReference(GuiGraphicsExtractor g,int mx,int my,SettingsPanelLayout.Columns c){
        int x=c.detailX(),w=c.detailWidth(),y=c.top();var group=ModelPanelState.SettingGroup.DEVELOPER;
        renderSettingRow(g,mx,my,x,y,w,bool(group,"gui.sparkle_morpher.model_panel.setting.show_model_id_first",GeneralConfig.SHOW_MODEL_ID_FIRST));
        drawText(g,Component.literal("面板状态"),x+8,y+settingRowHeight()+5);drawMuted(g,Component.literal("高级操作单独确认"),x+8,y+settingRowHeight()+17);
        int bx=x+w-160,by=y+settingRowHeight()+2;renderTextButton(g,mx,my,bx,by,150,22,Component.literal("导出 / 重置…"),()->developerStateMenu=!developerStateMenu);
        SettingRow[] rows={bool(group,"gui.sparkle_morpher.model_panel.setting.resource_monitor_log",GeneralConfig.RESOURCE_STATION_MONITOR_LOG),bool(group,"gui.sparkle_morpher.model_panel.setting.animation_debug_log",GeneralConfig.ANIMATION_DEBUG_LOG),bool(group,"gui.sparkle_morpher.model_panel.setting.model_memory_profiler",GeneralConfig.MODEL_MEMORY_PROFILER),bool(group,"gui.sparkle_morpher.model_panel.setting.animation_frame_profiler",GeneralConfig.ANIMATION_FRAME_PROFILER)};
        for(int i=0;i<rows.length;i++)renderSettingRow(g,mx,my,x,y+64+i*settingRowHeight(),w,rows[i]);
        renderTextButton(g,mx,my,x+w-160,height-layout.top-34,150,22,Component.literal("更多开发选项 ›"),()->{developerAdvanced=true;STATE.settingsScroll=0;});
        if(developerStateMenu){
            int count=ConfigPolicies.developerStateWriteback()?3:2,py=by+26;roundedSurface(g,bx,py,150,count*26+8,3,0xFC292929,0x33FFFFFF);
            renderTextButton(g,mx,my,bx+4,py+4,142,22,Component.literal("导出到剪贴板"),()->{developerStateMenu=false;dumpPanelState();});
            renderTextButton(g,mx,my,bx+4,py+30,142,22,Component.literal("重置面板状态…"),()->{developerStateMenu=false;confirmationTitle=Component.literal("重置面板状态？");confirmationBody=Component.literal("清除已保存的面板选择、筛选与滚动位置。");confirmationAction=Component.literal("重置");pendingConfirmation=this::resetPanelState;});
            if(count==3)renderTextButton(g,mx,my,bx+4,py+56,142,22,Component.literal("从剪贴板导入…"),()->{developerStateMenu=false;confirmationTitle=Component.literal("导入面板状态？");confirmationBody=Component.literal("使用剪贴板中的面板设置覆盖当前状态。");confirmationAction=Component.literal("导入");pendingConfirmation=this::applyStateFromClipboard;});
        }
    }

    private void renderConfirmation(GuiGraphicsExtractor g,int mx,int my){
        if(pendingConfirmation==null)return;this.hits.clear();fill(g,0,0,width,height,0x99000000);int w=Math.min(432,width-32),h=202,x=(width-w)/2,y=(height-h)/2-19;roundedSurface(g,x,y,w,h,5,0xFC292929,0x44FFFFFF);drawTitle(g,confirmationTitle,x+20,y+22);int yy=y+58;for(String line:confirmationBody.getString().split("\n")){drawMuted(g,Component.literal(trim(line,w-40)),x+20,yy);yy+=16;}
        renderTextButton(g,mx,my,x+w-214,y+164,88,22,Component.translatable("gui.cancel"),()->pendingConfirmation=null);renderPrimaryButton(g,mx,my,x+w-116,y+164,100,22,confirmationAction,()->{var action=pendingConfirmation;pendingConfirmation=null;action.run();});
    }

    private void renderPerformanceReference(GuiGraphicsExtractor g,int mx,int my){
        var c=settingsColumns();int x=c.detailX(),w=c.detailWidth(),y=c.top(),rh=settingRowHeight(),bh=uiButtonHeight();
        var mode=rendererModeRow(ModelPanelState.SettingGroup.PERFORMANCE).segmented();int controlW=Math.min(280,w*2/3),half=(controlW-4)/2,cx=x+w-controlW-8;
        drawText(g,Component.literal("渲染模式"),x+8,UiElementLayout.centeredY(y,rh,font.lineHeight));
        renderSegmentedOption(g,cx,y+4,half,bh,mode.left(),mode.leftSelected(),mode.leftAction());renderSegmentedOption(g,cx+half+4,y+4,half,bh,mode.right(),!mode.leftSelected(),mode.rightAction());
        y+=rh;for(var line:wrap("GPU 与兼容模式互斥；无法使用 GPU 时退回兼容模式。",w-16,2)){drawMuted(g,Component.literal(line),x+8,y+3);y+=font.lineHeight+2;}y+=5;
        renderSettingRow(g,mx,my,x,y,w,javaVectorRendererRow(ModelPanelState.SettingGroup.PERFORMANCE));y+=rh;
        String[] labels={"模型缓存","Cloud 处理与上传"};Runnable[] actions={()->selectSettingsPage("cache"),()->openCloudSettings(CloudAccountPage.PERFORMANCE)};
        for(int i=0;i<2;i++){drawText(g,Component.literal(labels[i]),x+8,UiElementLayout.centeredY(y,rh,font.lineHeight));renderTextButton(g,mx,my,x+w-108,y+4,100,bh,Component.literal("配置 ›"),actions[i]);y+=rh;}
        renderTextButton(g,mx,my,x+8,y+4,Math.min(150,w-16),bh,Component.literal(performanceAdvanced?"收起高级选项":"高级渲染选项 ›"),()->performanceAdvanced=!performanceAdvanced);y+=rh;
        if(performanceAdvanced)for(var row:settingsRows()){if(row.segmented()!=null||row.labelKey().contains("java_vector"))continue;renderSettingRow(g,mx,my,x,y,w,row);y+=rh;}
    }

    private void renderPeerConnectionReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        boolean probe=peerSettingsSection==PeerSettingsSection.PROBE,ready=minecraft.player!=null&&minecraft.level!=null&&!com.micaftic.morpher.client.PrivacyMode.isActive(),running=com.micaftic.morpher.client.peer.ClientPeerRuntime.running();String invite=com.micaftic.morpher.client.peer.ClientPeerRuntime.invitation();int x=layout.contentLeft,w=layout.contentWidth;
        pageHeader(g,mx,my,Component.literal(probe?"访客加入 / 探测":"高级：局域网手动连接"),Component.literal("P2P / 高级连接"),()->selectPeerSection(PeerSettingsSection.ROOM));
        if(probe){drawText(g,settingsText("peer.invitation_help"),x,70);renderReferenceField(g,mx,my,pt,peerInvitationBox,x,87,w,24);peerInvitationBox.visible=true;drawText(g,settingsText("peer.local_route"),x,150);renderReferenceField(g,mx,my,pt,peerLocalRouteBox,x,167,w,24);peerLocalRouteBox.visible=true;networkAction(g,mx,my,x,240,130,settingsText("peer.probe"),!peerInvitationDraft.isBlank(),()->com.micaftic.morpher.client.peer.ClientPeerRuntime.probeInvitation(peerInvitationDraft.trim(),peerLocalRouteDraft));networkAction(g,mx,my,x+138,240,100,settingsText("peer.join"),ready&&!peerInvitationDraft.isBlank(),this::joinPeerInvitation);networkAction(g,mx,my,x+246,240,160,settingsText("peer.clipboard_probe"),true,()->{peerInvitationDraft=minecraft.keyboardHandler.getClipboard();peerInvitationBox.setValue(peerInvitationDraft);com.micaftic.morpher.client.peer.ClientPeerRuntime.probeInvitation(peerInvitationDraft.trim(),peerLocalRouteDraft);});networkLine(g,com.micaftic.morpher.client.peer.ClientPeerRuntime.probeStatusComponent(),x,300,w);networkLine(g,settingsText("peer.probe_boundary"),x,332,w);}
        else{drawText(g,settingsText("peer.endpoint"),x,70);renderReferenceField(g,mx,my,pt,peerEndpointBox,x,87,w,24);peerEndpointBox.visible=true;networkAction(g,mx,my,x,148,160,settingsText("peer.next_adapter"),true,()->{peerEndpointDraft=com.micaftic.morpher.client.peer.ClientPeerRuntime.nextSuggestedEndpoint(peerEndpointDraft);peerEndpointBox.setValue(peerEndpointDraft);});networkAction(g,mx,my,x+168,148,110,settingsText("peer.host"),ready&&!running,()->com.micaftic.morpher.client.peer.ClientPeerRuntime.host(peerEndpointDraft.trim()));networkAction(g,mx,my,x+286,148,110,settingsText("peer.copy"),invite!=null,()->minecraft.keyboardHandler.setClipboard(invite));drawText(g,settingsText("peer.invitation_help"),x,200);renderReferenceField(g,mx,my,pt,peerInvitationBox,x,221,w,24);peerInvitationBox.visible=true;networkAction(g,mx,my,x,285,110,settingsText("peer.join"),ready&&!peerInvitationDraft.isBlank(),this::joinPeerInvitation);networkAction(g,mx,my,x+118,285,180,settingsText("peer.clipboard_join"),ready,()->{peerInvitationDraft=minecraft.keyboardHandler.getClipboard();peerInvitationBox.setValue(peerInvitationDraft);joinPeerInvitation();});networkLine(g,com.micaftic.morpher.client.peer.ClientPeerRuntime.statusComponent(),x,340,w);}
    }

    private void renderEmptyState(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,Component title,Component detail,Component actionLabel,Runnable action){
        int pw=Math.min(w-24,360),ph=Math.min(h-20,200),px=x+(w-pw)/2,py=y+(h-ph)/2;roundedSurface(g,px,py,pw,ph,4,0x0AFFFFFF,0x22FFFFFF);drawTitle(g,Component.literal(trim(title.getString(),pw-24)),px+12,py+18);int yy=py+52;for(var line:wrap(detail.getString(),pw-24,4)){drawMuted(g,Component.literal(line),px+12,yy);yy+=15;}renderPrimaryButton(g,mx,my,px+12,py+ph-34,pw-24,22,actionLabel,action);
    }

    private void renderReferenceField(GuiGraphicsExtractor g,int mx,int my,float pt,EditBox field,int x,int y,int w,int h){
        settingsExtent(y+h);roundedSurface(g,x,y,w,h,3,0x0DFFFFFF,0x24FFFFFF);
        if(field!=null){
            int offset=settingsDrawingBody?settingsDrawingOffset:0;
            field.visible=true;field.active=true;field.setX(x+6);field.setY(y+(h-12)/2-offset);field.setWidth(Math.max(1,w-12));field.setHeight(12);field.setBordered(false);
            if(settingsDrawingBody)settingsViewportFields.add(field);
            g.pose().pushMatrix();g.pose().translate(0,offset);field.extractWidgetRenderState(g,mx,my-offset,pt);g.pose().popMatrix();
        }
    }
    private void renderPrimaryButton(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,Component label,Runnable action){
        roundedFill(g,x,y,w,h,UiElementLayout.BUTTON_RADIUS,inside(mx,my,x,y,w,h)?0xFFEE6370:RED);drawCentered(g,Component.literal(trim(label.getString(),w-8)),x+w/2,UiElementLayout.centeredY(y,h,font.lineHeight),TEXT);hit(x,y,w,h,label,action);
    }
    private void renderAccountReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        int x=layout.contentLeft,w=layout.contentWidth,bh=uiButtonHeight(),gap=8,y=layout.top+42;
        pageHeader(g,mx,my,cloudText("title"),Component.empty(),this::backFromCloud);
        var registry=CloudManagementScreen.management().registry();var selected=registry.selected();
        String id=selected.map(CloudInstanceRegistry.CloudInstanceProfile::instanceId).orElse("");
        boolean connected=CloudClientRuntime.state(id)!=null;
        int manageW=Math.min(124,w/3),switchW=w-manageW-gap;
        renderTextButton(g,mx,my,x,y,switchW,bh,cloudText("switch_instance").copy().append(": ").append(selected.map(CloudManagementScreen::displayName).orElse("Cloud")),()->sourceTabMenu=!sourceTabMenu);
        renderTextButton(g,mx,my,x+switchW+gap,y,manageW,bh,cloudText("instance_management"),()->setCloudAccountPage(CloudAccountPage.INSTANCES));
        int menuY=y+bh+4;y+=bh+10;
        roundedSurface(g,x,y,w,42,4,0x10FFFFFF,0x20FFFFFF);
        drawText(g,Component.literal(trim(connected?CloudManagementScreen.management().accountId(id):cloudText("disconnected").getString(),w-20)),x+10,y+8);
        drawMuted(g,Component.literal(trim(selected.map(CloudManagementScreen::displayName).orElse("Cloud")+" · "+cloudText(connected?"connected":"disconnected").getString(),w-20)),x+10,y+25);y+=50;
        int half=(w-gap)/2;
        renderPrimaryButton(g,mx,my,x,y,half,bh,Component.literal(cloudFormBusy?"处理中…":"一键登录 + 绑定"),()->{
            if(cloudFormBusy)return;
            try{if(connected)bindCloudIdentity(null);else runCloudAccount(CloudManagementScreen.connectOfficialAccount(),"quick_success");}
            catch(RuntimeException failure){cloudPanelStatus=Component.literal(CloudManagementScreen.errorText(failure));}
        });
        int manualW=(w-half-gap*2)/2;
        renderTextButton(g,mx,my,x+half+gap,y,manualW,bh,Component.literal("手动登录"),()->openCloudLoginForm(false));
        renderTextButton(g,mx,my,x+half+gap*2+manualW,y,w-half-manualW-gap*2,bh,Component.literal("注册"),()->openCloudLoginForm(true));y+=bh+12;
        int rh=settingRowHeight(),controlW=Math.min(144,w/2);
        drawText(g,Component.literal("正版 / 第三方身份"),x+8,UiElementLayout.centeredY(y,rh,font.lineHeight));
        networkAction(g,mx,my,x+w-controlW,y+(rh-bh)/2,controlW,Component.literal("绑定当前游戏身份"),connected&&!cloudFormBusy,()->bindCloudIdentity(null));y+=rh;
        String[] labels={"identity_panel.title","scopes","identity_panel.admin_title","targets_acl","performance","offline.title"};
        Runnable[] actions={()->openCloudIdentityPage(CloudAccountPage.IDENTITY),()->{setCloudAccountPage(CloudAccountPage.SCOPES);refreshCloudScopes();},()->openCloudIdentityPage(CloudAccountPage.APPROVAL),()->InputUtil.setScreen(new CloudTargetManagementScreen(this,CloudManagementScreen.management())),()->setCloudAccountPage(CloudAccountPage.PERFORMANCE),this::openOfflineIdentityPage};
        for(int i=0;i<labels.length;i++){drawText(g,Component.literal(trim(cloudText(labels[i]).getString(),w-controlW-20)),x+8,UiElementLayout.centeredY(y,rh,font.lineHeight));renderTextButton(g,mx,my,x+w-controlW,y+(rh-bh)/2,controlW,bh,Component.literal(i==0?"身份与认证提供方 ›":"配置 ›"),connected?actions[i]:()->openCloudLoginForm(false));y+=rh;}
        networkAction(g,mx,my,x+8,y+6,Math.min(144,w-16),cloudText("logout"),connected&&!cloudFormBusy,()->{CloudManagementScreen.management().logout();resetCloudPage();init();});
        if(sourceTabMenu){int menuW=switchW,yy=menuY;roundedSurface(g,x,yy,menuW,registry.profiles().size()*24+4,3,0xFF292929,0x44FFFFFF);for(var profile:registry.profiles()){var p=profile;renderTextButton(g,mx,my,x+2,yy+2,menuW-4,22,Component.literal(CloudManagementScreen.displayName(profile)),()->{sourceTabMenu=false;selectCloudInstance(p);init();});yy+=24;}}
    }

    private void openOfflineIdentityPage(){setCloudAccountPage(CloudAccountPage.OFFLINE);}
    private void renderOfflineReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        int x=layout.contentLeft+8,w=Math.max(24,layout.contentWidth-16),y=layout.top+48,bh=uiButtonHeight();
        drawText(g,cloudText("offline.help"),x,y);y+=28;
        var runtime=CloudClientRuntime.state();
        drawMuted(g,Component.literal(trim(runtime==null?"Cloud":CloudManagementScreen.management().accountId(runtime.instance().instanceId()),w)),x,y);y+=28;
        drawMuted(g,Component.literal(trim(com.micaftic.morpher.cloud.client.CloudPlayerModelSync.status(),w)),x,y);y+=28;
        networkAction(g,mx,my,x,y,Math.min(180,w),cloudText("offline.retry"),runtime!=null&&!cloudFormBusy,()->com.micaftic.morpher.cloud.client.CloudPlayerModelSync.refreshOfflineIdentity());
        settingsExtent(y+bh+24);
    }

    private void bindCloudIdentity(String providerId){
        if(cloudFormBusy)return;
        var context=CloudManagementScreen.accountContext();long generation=cloudFormGeneration;
        cloudFormBusy=true;cloudPanelStatus=cloudText("working");
        try{
            var request=providerId==null?CloudManagementScreen.bindCurrentGameAccount():CloudManagementScreen.bindProvider(providerId);
            request.whenComplete((result,failure)->Minecraft.getInstance().execute(()->{
                try{context.check();}catch(java.util.concurrent.CancellationException stale){return;}
                if(generation!=cloudFormGeneration)return;
                cloudFormBusy=false;
                cloudPanelStatus=failure==null?Component.literal("当前游戏身份已绑定"):Component.literal(CloudManagementScreen.errorText(failure));
                if(failure==null&&cloudIdentityFlow!=null)cloudIdentityFlow.refresh();
                init();
            }));
        }catch(RuntimeException failure){cloudFormBusy=false;cloudPanelStatus=Component.literal(CloudManagementScreen.errorText(failure));}
    }
    private void renderInstancesReference(GuiGraphicsExtractor g,int mx,int my,float pt){
        var a=FinalUiLayout.scene("instances",this.width,this.height);int x=this.layout.contentLeft;
        pageHeader(g,mx,my,cloudText("instances"),Component.empty(),this::backFromCloud);var sr=a.control(0);renderReferenceField(g,mx,my,pt,instanceSearchBox,sr.x(),sr.y(),sr.w(),sr.h());
        var registry=CloudManagementScreen.management().registry();var profiles=registry.profiles().stream().filter(p->(CloudManagementScreen.displayName(p)+" "+p.instance().origin()).toLowerCase(Locale.ROOT).contains(instanceSearchDraft.toLowerCase(Locale.ROOT))).toList();
        var inspected=registry.find(inspectedInstanceId).orElseGet(()->registry.selected().orElse(null));
        IconGlyph[] icons={IconGlyph.PLUS,IconGlyph.REFRESH,IconGlyph.EDIT,IconGlyph.DELETE,IconGlyph.CHECK,IconGlyph.SITES,IconGlyph.USER,IconGlyph.GLOBE};
        Runnable[] actions={this::openCloudInstanceForm,()->{if(inspected!=null)selectCloudInstance(inspected);init();},()->{if(inspected!=null){openCloudInstanceForm();editingCloudInstance=true;cloudAddressDraft=inspected.instance().origin().toString();cloudNameDraft=CloudManagementScreen.displayName(inspected);init();}},()->{if(inspected!=null&&!CloudInstanceRegistry.isBuiltinOfficial(inspected)){selectCloudInstance(inspected);removeCloudInstance();}},()->{if(inspected!=null){selectCloudInstance(inspected);init();}},()->{if(inspected!=null){STATE.closedCloudTabs.remove(inspected.instanceId());switchCloudInstance(inspected);STATE.activeTab=ModelPanelState.Tab.MODEL;init();}},()->{if(inspected!=null){selectCloudInstance(inspected);setCloudAccountPage(CloudAccountPage.ACCOUNT);}},()->{if(inspected!=null){selectCloudInstance(inspected);setCloudAccountPage(CloudAccountPage.SCOPES);refreshCloudScopes();}}};
        String[] keys={"add_instance","refresh_scopes","instance_management","remove_instance","selected","instances","account","scopes"};
        for(int i=0;i<8;i++){var b=a.control(i+1);toolbarButton(g,mx,my,b.x(),b.y(),icons[i],cloudText(keys[i]),false,actions[i]);}
        var lr=a.area("listRect");int rowH=this.width<560?43:66,visible=Math.max(1,(this.height-lr.y()-45)/(rowH+6));cloudAccountScroll=clamp(cloudAccountScroll,0,Math.max(0,profiles.size()-visible));
        for(int i=cloudAccountScroll;i<Math.min(profiles.size(),cloudAccountScroll+visible);i++){var profile=profiles.get(i);int yy=lr.y()+(i-cloudAccountScroll)*(rowH+6);boolean chosen=profile.equals(inspected),active=registry.selected().map(p->p.instanceId().equals(profile.instanceId())).orElse(false);roundedSurface(g,lr.x(),yy,lr.w(),rowH,4,chosen?0x18FFFFFF:0x08FFFFFF,chosen?0x88DC4F5D:0x16FFFFFF);drawText(g,Component.literal((active?"◉ ":"○ ")+trim(CloudManagementScreen.displayName(profile),lr.w()-24)),lr.x()+8,yy+9);drawMuted(g,Component.literal(trim(CloudManagementScreen.management().accountId(profile.instanceId())+" · "+cloudText(CloudClientRuntime.state(profile.instanceId())!=null?"connected":"disconnected").getString(),lr.w()-24)),lr.x()+14,yy+rowH-17);hit(lr.x(),yy,lr.w(),rowH,Component.literal(profile.instance().origin().toString()),()->inspectedInstanceId=profile.instanceId());}
        if(inspected!=null){var dr=a.area("detailRect");roundedSurface(g,dr.x(),dr.y(),dr.w(),dr.h(),4,0x0CFFFFFF,0x20FFFFFF);int dx=dr.x()+12,yy=dr.y()+14;drawTitle(g,Component.literal(CloudManagementScreen.displayName(inspected)),dx,yy);drawMuted(g,Component.literal(trim(inspected.instance().origin().toString(),dr.w()-24)),dx,yy+30);String[] values={inspected.instanceId(),registry.selected().map(CloudManagementScreen::displayName).orElse(""),cloudText(CloudClientRuntime.state(inspected.instanceId())==null?"disconnected":"connected").getString(),STATE.closedCloudTabs.contains(inspected.instanceId())?"关闭":"打开"};String[] labels={"实例 ID","当前选择","登录状态","模型标签"};for(int i=0;i<4;i++){drawMuted(g,Component.literal(labels[i]),dx,yy+(this.width<560?52:64)+i*(this.width<560?22:36));drawText(g,Component.literal(trim(values[i],dr.w()/2)),dx+dr.w()/3,yy+(this.width<560?52:64)+i*(this.width<560?22:36));}}
    }
    private void renderCloudPerformanceReference(GuiGraphicsExtractor g,int mx,int my){
        pageHeader(g,mx,my,settingsText("cloud.performance"),cloudText("account_intro"),this::backFromCloud);
        int x=this.layout.contentLeft,w=this.layout.contentWidth,y=this.layout.top+58;
        drawText(g,settingsText("cloud.performance.preset"),x+10,y+12);
        var presets=com.micaftic.morpher.cloud.client.CloudClientPerformance.Preset.values();var current=com.micaftic.morpher.cloud.client.CloudClientPerformance.preset();
        renderTextButton(g,mx,my,x+w-160,y+7,150,22,settingsText("cloud.performance.preset."+current.name().toLowerCase(Locale.ROOT)),()->com.micaftic.morpher.cloud.client.CloudClientPerformance.select(presets[(current.ordinal()+1)%presets.length]));
        renderSettingRow(g,mx,my,x,y+47,w,cloudConcurrencyRow(false));renderSettingRow(g,mx,my,x,y+94,w,cloudConcurrencyRow(true));
        networkLine(g,settingsText("cloud.performance.custom_help"),x+10,y+170,w-20);
    }

    private void renderReferenceTab(GuiGraphicsExtractor g,int mx,int my,int x,int y,int w,int h,Component label,boolean selected,Runnable action){
        roundedSurface(g,x,y,w,h,3,selected?0x1CFFFFFF:0x0CFFFFFF,0x18FFFFFF);
        if(selected)fill(g,x+10,y+h-1,w-20,1,RED);
        drawCentered(g,Component.literal(trim(label.getString(),w-12)),x+w/2,UiElementLayout.centeredY(y,h,font.lineHeight),TEXT);hit(x,y,w,h,label,action);
    }
    private void referenceButton(GuiGraphicsExtractor g,int mx,int my,FinalUiLayout.Rect r,Component label,Runnable action){renderTextButton(g,mx,my,r.x(),r.y(),r.w(),r.h(),label,action);}
    private void pageHeader(GuiGraphicsExtractor g,int mx,int my,Component title,Component subtitle,Runnable back){
        if(settingsDrawingBody)return;
        int x=this.layout.contentLeft,y=this.layout.top+6,right=x+this.layout.contentWidth,buttonH=uiButtonHeight();
        if(back!=null){toolbarButton(g,mx,my,x,y,IconGlyph.BACK,Component.translatable("gui.back"),false,back);x+=buttonH+8;}
        var text=UiElementLayout.header(y,buttonH,font.lineHeight,!subtitle.getString().isBlank());
        int available=Math.max(1,right-x-(back==null?buttonH+10:6));drawTitle(g,Component.literal(trim(title.getString(),(int)(available/1.25f))),x,text.titleY());
        if(!subtitle.getString().isBlank())drawMuted(g,Component.literal(trim(subtitle.getString(),available)),x,text.subtitleY());
        if(back==null)toolbarButton(g,mx,my,right-buttonH,y,IconGlyph.CLOSE,Component.translatable("gui.sparkle_morpher.model_panel.close"),false,this::onClose);

    }
    private Component appearanceText(Component text){
        if(appearance.font.equals("minecraft:default"))return text;
        String[] id=appearance.font.split(":",2);
        return text.copy().withStyle(style->style.withFont(new net.minecraft.network.chat.FontDescription.Resource(com.micaftic.morpher.core.api.resource.ResourceApi.nativeId(id[0],id[1]))));
    }
    private boolean handleUiKey(int key,int modifiers){
        if(key==258&&!hits.isEmpty()){setFocused(null);keyboardHit=Math.floorMod(keyboardHit+((modifiers&1)!=0?-1:1),hits.size());return true;}
        if((key==257||key==335||key==32)&&keyboardHit>=0&&keyboardHit<hits.size()){hits.get(keyboardHit).action().run();return true;}
        return false;
    }
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
        if(handleUiKey(event.key(),event.modifiers()))return true;
        return super.keyPressed(event);
    }
}
