package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.util.SmExecutors;
import com.micaftic.morpher.client.entity.PlayerPreviewEntity;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.client.renderer.RendererManager;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.LoadingStateConfig;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.core.gui.UnifiedRouletteScreen;
import com.micaftic.morpher.core.gpu.BlurStack;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import com.micaftic.morpher.network.message.C2SSetStarModelPacket;
import com.micaftic.morpher.resource.models.AuthorInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.resource.models.ModelPackData;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;

public class ModernPlayerModelScreen extends Screen {
    private static final ModelPanelState STATE = new ModelPanelState();
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
    private static final ExecutorService RESOURCE_EXECUTOR = SmExecutors.pool(SmExecutors.Pool.MODEL_IO);

    // 卡片网格(封面卡片模式)相关常量。
    private static final int NAME_BAND = 34;             // 卡片底部名字条高度
    private static final int CARD_MIN_W = 150;           // 进入卡片模式的网格最小宽度
    private static final int CARD_MIN_H = 168;           // 进入卡片模式的网格最小高度
    private static final int CARD_TARGET_W = 140;        // 期望卡片宽(参与列数推导)
    private static final int CARD_MAX_W = 216;           // 卡片最大宽
    private static final int CARD_MIN_CELL_W = 124;      // 卡片最小宽
    private static final int CARD_MAX_CELL_H = 208;      // 卡片最大高
    private static final int PRELOAD_BUDGET = 4;         // 每 tick 最多新启动的预载数
    private static final int PRELOAD_GIVE_UP_TICKS = 400; // 预载观察超时(约20秒),超时未落地判为失败
    private static final int WHEEL_STEP_MAX = 28;        // 模型网格单次滚轮的像素步长上限

    // 目录卡片页相关常量。
    private static final int CAT_BASE = 0xFF434242;        // 卡片底
    private static final int CAT_BASE_HOVER = 0xFF505755;  // 悬停
    private static final int CAT_SELECT = 0xFF58636E;      // 选中
    private static final int CAT_FOLDER = 0xFF9B51E0;      // 资源包/文件夹 = 紫色卡
    private static final int CAT_FOLDER_HOVER = 0xFFB17BEA;
    private static final int CAT_NAME = 0xFFF3EFE0;        // 卡名(奶油色)
    private static final int CAT_STAR_BORDER = 0xFFF3EFE0; // 悬停奶油描边
    private static final int CAT_DIM = 0x9F222222;         // 需授权压暗
    private static final int CAT_MARGIN = 6;
    private static final int CAT_PAD = 4;                  // 卡间距
    private static final int CAT_MAX_COLS = 8;             // 一页最多 8 列
    /**
     * 卡片小人镜头(fa26.1.2):row0=关闭预览旋转(正面),row1=正常旋转。
     * {scale基准系数, 脚底到封面底的 y 锚点像素, 已弃用额外 yaw, 已弃用额外 pitch}。
     */
    private static final float[][] YSM_CAM = {
            {31.1f, 0.9f, 0.5f, -0.7f},
            {32.5f, 4.4f, -0.3f, 0.0f}
    };
    private static final int CAT_MAX_ROWS = 3;             // 一页最多 3 行(实时 3D 卡,留性能余量)
    private static final int CAT_MIN_CELL_W = 64;          // 卡片最小宽(保证可读)
    private static final int CAT_MAX_CELL_W = 170;         // 卡片最大宽
    private static final int CAT_MIN_W = 140;              // 进入卡片页的最小网格宽
    private static final int CAT_MIN_H = 120;              // 进入卡片页的最小网格高
    private static final float CAT_ASPECT = 1.73f;         // 竖卡比例 52:90

    /** 封面贴图实际像素尺寸缓存(仅渲染线程读写;key 用弱引用避免阻止贴图回收)。 */
    private static final WeakHashMap<AbstractTexture, int[]> COVER_DIMS = new WeakHashMap<>();

    private final List<Hit> hits = new ArrayList<>();
    private final List<ModelRepoEntry> resourceEntries = new ArrayList<>();
    private final Set<String> selectedModelIds = new LinkedHashSet<>();
    private final Set<String> selectedResourceUrls = new LinkedHashSet<>();
    private final Queue<ModelImportFilePicker.PickedFile> pendingImports = new ArrayDeque<>();
    private final PlayerPreviewEntity previewEntity = new PlayerPreviewEntity();
    /** 目录卡页每张卡的实时 3D 预览实体(id → figure)。 */
    private final Map<String, PlayerPreviewEntity> cardPreviewEntities = new LinkedHashMap<>(16);
    /** 各卡预览实体当前绑定的贴图 id(换贴图时重建 figure)。 */
    private final Map<String, String> cardPreviewTextureIds = new LinkedHashMap<>(16);
    /** 卡片小人 hover/idle 动画编排(fa26.1.2 port)。 */
    private final YsmCardAnimator cardAnimator = new YsmCardAnimator();
    private final BiConsumer<String, String> modelSelectionTarget;
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
    private boolean draggingResourceScroll;
    private String previewModelId = "";
    private String previewTextureId = "";
    private String pendingModelApplyId;
    /** 可视卡封面预载:本次打开已尝试但未能常驻(缺源/解析失败/损坏)的模型 id。 */
    private final Set<String> preloadFailed = new HashSet<>();
    /** 可视卡封面预载:已发起后台加载、正在等待落地的模型 id。 */
    private final Set<String> preloadWatching = new HashSet<>();
    /** 预载开始 tick 记录:用于超时把“永不落地”的 id 移入 preloadFailed。 */
    private final Map<String, Integer> preloadWatchStart = new LinkedHashMap<>();
    private int preloadTicker;

    // 模型网格“稳定排序”:指纹不变(集合/路径/筛选/搜索/星标未变)时复用上一次的排序结果。
    // 后台预载把模型从“目录项”变成“常驻”时,其名字来源会从懒加载的嗅探名/路径升级为真名;
    // 若每帧按名字重排,整排卡片就会在加载完成瞬间来回乱跳。指纹只跟“有哪些模型”绑定、与名字无关,
    // 所以加载期名字刷新只会原位更新卡片文字,不会让卡片挪动位置。
    private String modelListFingerprint = "";
    private List<String> modelListOrder = List.of();

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
        this((BiConsumer<String, String>) null);
    }

    public ModernPlayerModelScreen(BiConsumer<String, String> modelSelectionTarget) {
        super(Component.translatable("key.sparkle_morpher.player_model.desc"));
        this.modelSelectionTarget = modelSelectionTarget;
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
        if ((++this.preloadTicker & 3) == 0) {
            preloadVisibleCovers();
        }
        if (this.pendingModelApplyId != null) {
            String pendingId = this.pendingModelApplyId;
            ClientModelManager.getModelContext(pendingId).ifPresent(assembly -> {
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
            enqueueImportPath(path);
        }
        startNextImportIfIdle();
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
            renderVerticalTab(g, mouseX, mouseY, ModelPanelState.Tab.SETTINGS, railX, y + (th + 4) * 2, railW, th, IconGlyph.SETTINGS, Component.translatable("gui.sparkle_morpher.model_panel.settings"));
            return;
        }
        int tabWidth = this.layout.width / 3;
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.MODEL, this.layout.left, tabWidth, IconGlyph.MODEL, Component.translatable("gui.sparkle_morpher.model_panel.model"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.RESOURCE, this.layout.left + tabWidth, tabWidth, IconGlyph.RESOURCE, Component.translatable("gui.sparkle_morpher.resource_station.title"));
        renderTab(g, mouseX, mouseY, ModelPanelState.Tab.SETTINGS, this.layout.left + tabWidth * 2, this.layout.width - tabWidth * 2, IconGlyph.SETTINGS, Component.translatable("gui.sparkle_morpher.model_panel.settings"));
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

    /** 右详情栏:内容区够宽够高才画在右侧;否则详情退回底部条。旧二值 compact 会整体丢掉右栏,这里拆成独立档位。 */
    private boolean modelDetailsRight() {
        return this.layout.contentWidth >= 520 && this.layout.contentHeight >= 200;
    }

    /** 左摘要栏:在右栏基础上再多要一行宽/高;不足则标题/筛选/动作改为列表列顶部的内联行。 */
    private boolean modelHeaderWide() {
        return modelDetailsRight() && this.layout.contentWidth >= 720 && this.layout.contentHeight >= 280;
    }

    private int modelLeftW() {
        if (!modelHeaderWide()) {
            return 0;
        }
        int free = this.layout.contentWidth - modelRightW() - modelListMinW() - 46;
        return clamp(this.layout.contentWidth / 6, 140, Math.max(140, Math.min(220, free)));
    }

    private int modelRightW() {
        if (!modelDetailsRight()) {
            return 0;
        }
        int cap = Math.max(150, Math.min(250, this.layout.contentWidth - 240));
        return clamp(this.layout.contentWidth / 4, 150, cap);
    }

    private int modelListX() {
        if (modelHeaderWide()) {
            return modelLeftX() + modelLeftW() + 12;
        }
        return this.layout.contentLeft + 8;
    }

    private int modelListW() {
        int left = modelLeftW();
        int right = modelRightW();
        int used = 16 + (left > 0 ? left + 12 : 0) + (right > 0 ? right + 12 : 0);
        return Math.max(modelListMinW(), this.layout.contentWidth - used);
    }

    private int modelListMinW() {
        return this.layout.contentWidth < 560 ? 110 : 180;
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

    private void renderModelTab(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean sideDetails = modelDetailsRight();
        int x = modelLeftX();
        int y = this.layout.contentTop + 8;
        int leftW = modelLeftW();
        int rightW = modelRightW();
        int listX = modelListX();
        int listW = modelListW();
        int detailX = listX + listW + 10;
        int contentBottom = this.layout.footerTop - 6;

        if (!modelHeaderWide()) {
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
            this.modelSearchBox.render(g, mouseX, mouseY, partialTick);
        }
        int pathY = modelPathY();
        renderPathBar(g, listX, pathY, listW);
        int gridY = pathY + 20;
        int actionsBandY = contentBottom - 28;
        int gridH = modelListGridHeight();
        renderModelGrid(g, mouseX, mouseY, listX, gridY, listW, gridH, partialTick);
        int stripH = compactDetailStripH();
        if (stripH > 0) {
            renderCompactDetail(g, mouseX, mouseY, listX, gridY + gridH + 3, listW, stripH, partialTick);
        }
        renderModelBottomActions(g, mouseX, mouseY, listX, actionsBandY, listW, gridH);
        if (sideDetails) {
            renderModelDetails(g, mouseX, mouseY, detailX, y, rightW, contentBottom - y, partialTick);
        }
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
        int count = ClientModelManager.getAvailableModelIds().size();
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

    /**
     * 模型网格。面积足够时走「目录卡片页」(52:90 竖卡,列×行随可用面积自适应,卡主体为实时 3D 小人);
     * 面积不足则退回文字行格(统一两行,副标题保留)。卡片判定只看网格区实际尺寸,与整窗档位无关。
     */
    private void renderModelGrid(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        glassPanel(g, x, y, w, h);
        List<ModelEntry> entries = collectModelEntries();
        if (entries.isEmpty()) {
            drawCentered(g, Component.translatable("gui.sparkle_morpher.model_panel.no_models"), x + w / 2, y + h / 2 - 4, MUTED);
            return;
        }
        if (fitsCatalog(w, h)) {
            renderCatalogGrid(g, mouseX, mouseY, entries, x, y, w, h, partialTick);
            return;
        }
        Set<String> starredModels = starModels();
        ModelListMetrics metrics = modelListMetrics(w, h, entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, metrics.maxScroll());
        int cols = metrics.cols();
        int cellW = metrics.cellW();
        int cellH = metrics.cellH();
        int contentRows = metrics.contentRows();
        int scroll = STATE.modelScroll;
        int startRow = Math.min(contentRows - 1, Math.max(0, scroll / Math.max(1, cellH)));
        g.enableScissor(x, y, x + w, y + h);
        try {
            for (int row = startRow; row < contentRows; row++) {
                int rowTop = y + row * cellH - scroll;
                if (rowTop >= y + h) {
                    break;
                }
                if (rowTop + cellH <= y) {
                    continue;
                }
                for (int col = 0; col < cols; col++) {
                    int index = row * cols + col;
                    if (index >= entries.size()) {
                        break;
                    }
                    ModelEntry entry = entries.get(index);
                    int cx = x + col * cellW + 3;
                    int cy = rowTop + 3;
                    int cw = cellW - 6;
                    int ch = cellH - 6;
                    renderLegacyCell(g, mouseX, mouseY, entry, cx, cy, cw, ch, y, y + h, metrics.dense(), starredModels);
                }
            }
        } finally {
            g.disableScissor();
        }
    }

    private static boolean fitsCatalog(int w, int h) {
        if (w < CAT_MIN_W || h < CAT_MIN_H) {
            return false;
        }
        // 结构探测:即便过了最小宽高,也要能至少放 1 列 1 行才采用目录页。
        CatalogMetrics m = catalogMetrics(w, h, 2);
        return m.cols() >= 1 && m.rows() >= 1;
    }

    /** 目录页是否生效(与 renderModelGrid 同源:按当前列表区实际尺寸,不看 compact 整窗标记)。 */
    private boolean modelCardsActive() {
        return this.layout != null
                && STATE.secondaryPanel == ModelPanelState.SecondaryPanel.NONE
                && fitsCatalog(modelListW(), currentModelGridH());
    }

    /**
     * 目录页排版:宽推列、高推行(列×行随面积自适应,最多 CAT_MAX_COLS×CAT_MAX_ROWS),
     * 卡宽取宽/高两方向都放得下的值,STATE.modelScroll 在目录模式下表示页号。
     */
    private static CatalogMetrics catalogMetrics(int w, int h, int entryCount) {
        int cols = clamp((w - 2 * CAT_MARGIN + CAT_PAD) / (CAT_MIN_CELL_W + CAT_PAD), 1, CAT_MAX_COLS);
        int rows = clamp((h - 2 * CAT_MARGIN + CAT_PAD) / ((int) (CAT_MIN_CELL_W * CAT_ASPECT) + CAT_PAD), 1, CAT_MAX_ROWS);
        int wFit = (w - 2 * CAT_MARGIN - (cols - 1) * CAT_PAD) / cols;
        int hFit = (int) ((h - 2 * CAT_MARGIN - (rows - 1) * CAT_PAD) / (rows * CAT_ASPECT));
        int cellW = clamp(Math.min(wFit, hFit), CAT_MIN_CELL_W, CAT_MAX_CELL_W);
        cols = clamp((w - 2 * CAT_MARGIN + CAT_PAD) / (cellW + CAT_PAD), 1, cols);
        int cellH = Math.max(1, (int) Math.floor(cellW * CAT_ASPECT));
        rows = clamp((h - 2 * CAT_MARGIN + CAT_PAD) / (cellH + CAT_PAD), 1, rows);
        int capacity = cols * rows;
        int totalPages = entryCount == 0 ? 1 : (entryCount + capacity - 1) / capacity;
        return new CatalogMetrics(cols, rows, cellW, cellH, capacity, totalPages);
    }

    /** 目录卡片页网格:列×行随面积自适应,整块居中,每张卡 = 实时 3D 小人 + 名字条。 */
    private void renderCatalogGrid(GuiGraphics g, int mouseX, int mouseY, List<ModelEntry> entries, int x, int y, int w, int h, float partialTick) {
        CatalogMetrics m = catalogMetrics(w, h, entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, m.totalPages() - 1);
        int start = STATE.modelScroll * m.capacity();
        int blockW = m.cols() * m.cellW() + (m.cols() - 1) * CAT_PAD;
        int blockH = m.rows() * m.cellH() + (m.rows() - 1) * CAT_PAD;
        int x0 = x + Math.max(CAT_MARGIN, (w - blockW) / 2);
        int y0 = y + Math.max(CAT_MARGIN, (h - blockH) / 2);
        Set<String> starred = starModels();
        Set<String> pageIds = new HashSet<>();
        outer:
        for (int row = 0; row < m.rows(); row++) {
            for (int col = 0; col < m.cols(); col++) {
                int index = start + row * m.cols() + col;
                if (index >= entries.size()) {
                    break outer;
                }
                ModelEntry entry = entries.get(index);
                if (!entry.folder()) {
                    pageIds.add(entry.modelId());
                }
                int cx = x0 + col * (m.cellW() + CAT_PAD);
                int cy = y0 + row * (m.cellH() + CAT_PAD);
                renderCatalogCard(g, mouseX, mouseY, entry, cx, cy, m.cellW(), m.cellH(), starred, partialTick);
            }
        }
        evictCardPreviews(pageIds);
    }

    /** 单张竖卡:实时 3D 小人 / 紫色文件夹卡 / 懒模型加载小圆点。 */
    private void renderCatalogCard(GuiGraphics g, int mouseX, int mouseY, ModelEntry entry, int cx, int cy, int cw, int ch, Set<String> starredModels, float partialTick) {
        boolean folder = entry.folder();
        boolean selected = entry.modelId().equals(STATE.selectedModelId) || this.selectedModelIds.contains(entry.modelId());
        boolean multiSelected = !entry.modelId().equals(STATE.selectedModelId) && this.selectedModelIds.contains(entry.modelId());
        boolean hover = inside(mouseX, mouseY, cx, cy, cw, ch);
        boolean starred = !folder && starredModels.contains(entry.modelId());
        boolean locked = !folder && entry.locked();
        int nameH = clamp((int) (ch * 0.24f), 20, 46);
        int coverH = Math.max(1, ch - nameH);

        if (folder) {
            fill(g, cx, cy, cw, ch, hover ? CAT_FOLDER_HOVER : CAT_FOLDER);
        } else {
            fill(g, cx, cy, cw, ch, selected ? CAT_SELECT : hover ? CAT_BASE_HOVER : CAT_BASE);
        }

        if (folder) {
            // fa26.1.2: 文件夹卡若已有常驻 model-pack,在其上图铺封面贴图;否则纯紫 + 文件夹图标。
            ModelPackData pack = ClientModelManager.getModelPackMap().get(entry.modelId());
            AbstractTexture packIcon = pack == null ? null : pack.getTexture();
            if (packIcon != null) {
                drawCoverImage(g, packIcon, cx + 2, cy + 2, cw - 4, coverH - 2);
            }
            int size = Math.min(30, Math.min(cw - 10, coverH - 10));
            drawIconScaled(g, IconGlyph.FOLDER, cx + Math.max(0, (cw - size) / 2), cy + Math.max(2, (coverH - size) / 2), size);
        } else if (!locked) {
            ModelAssembly asm = residentAssembly(entry.modelId());
            if (asm == null) {
                drawCatalogLoading(g, cx, cy, cw, coverH);
            } else if (asm.isGltf()) {
                int size = Math.min(30, Math.min(cw - 8, coverH - 10));
                drawIconScaled(g, IconGlyph.MODEL, cx + Math.max(0, (cw - size) / 2), cy + Math.max(2, (coverH - size) / 2), size);
            } else {
                AbstractTexture cover = coverTextureOf(asm);
                if (cover != null && drawCoverImage(g, cover, cx + 2, cy + 2, cw - 4, coverH - 2)) {
                    fill(g, cx, cy, cw, coverH, 0x8C000000); // 封面压暗当底,凸显上面的实时小人
                }
                String textureId = selectedTextureOrDefault(asm);
                PlayerPreviewEntity cardEntity = cardPreviewEntityFor(entry.modelId(), textureId);
                this.cardAnimator.update(entry.modelId(), cardEntity, hover, System.currentTimeMillis());
                renderCatalogCardFigure(g, entry.modelId(), asm, cx, cy, cw, coverH, partialTick);
                // fa26.1.2: 封面 gui_foreground 前景叠在小人上方(图框成卡)。
                ModelDisplayAssets displayAssets = asm.getTextureRegistry();
                AbstractTexture foreground = displayAssets == null ? null : displayAssets.getGuiForeground();
                if (foreground != null) {
                    drawCoverImage(g, foreground, cx, cy, cw, coverH);
                }
            }
        }

        // 名字条:压在 3D 之后,保证不被小人遮挡
        fill(g, cx, cy + coverH, cw, nameH, 0xC6000000);
        drawCatalogName(g, entry, cx, cy + coverH, cw, nameH, folder);

        if (locked) {
            fill(g, cx, cy, cw, ch, CAT_DIM);
            drawIcon(g, IconGlyph.LOCK, cx + (cw - 18) / 2, cy + Math.max(2, (coverH - 18) / 2));
        } else if (!folder) {
            if (multiSelected) {
                drawIcon(g, IconGlyph.CHECK, cx + cw - 18, cy + 3);
            } else if (starred) {
                drawIcon(g, IconGlyph.STAR, cx + cw - 18, cy + 3);
            }
        }

        border(g, cx, cy, cw, ch, selected ? RED : hover ? CAT_STAR_BORDER : 0x55FFFFFF);
        hit(cx, cy, cw, ch, Component.literal(entry.title()), () -> clickModelEntry(entry));
    }

    /** 卡片底部名字条。名字够高(≥28)且有条目副标题时排两行,否则单行居中。 */
    private void drawCatalogName(GuiGraphics g, ModelEntry entry, int bx, int by, int cw, int nameH, boolean folder) {
        int w = Math.max(6, cw - 6);
        String title = trim(entry.title(), w);
        String sub = entry.subtitle();
        boolean twoLine = !folder && nameH >= 28 && sub != null && !sub.isEmpty() && !"folder".equals(sub);
        int tx = bx + 3;
        if (twoLine) {
            g.drawString(this.font, Component.literal(title), tx, by + 3, CAT_NAME, false);
            g.drawString(this.font, Component.literal(trim(sub, w)), tx, by + nameH - this.font.lineHeight - 3, 0xFF9A9A9A, false);
        } else {
            g.drawString(this.font, Component.literal(title), tx, by + Math.max(2, (nameH - this.font.lineHeight) / 2), CAT_NAME, false);
        }
    }

    /** 懒模型未常驻:在卡片上部画加载提示小圆点。 */
    private void drawCatalogLoading(GuiGraphics g, int cx, int cy, int cw, int coverH) {
        int dots = (this.preloadTicker / 3) % 4;
        String s = "." + ".".repeat(dots);
        g.drawString(this.font, Component.literal(s), cx + (cw - this.font.width(s)) / 2, cy + Math.max(6, (coverH - this.font.lineHeight) / 2), CAT_NAME, false);
    }

    /** 卡片里该模型的实时 3D 小人(与右栏详情同一渲染管线、静止正面;带封面时已垫压暗底)。 */
    private void renderCatalogCardFigure(GuiGraphics g, String modelId, ModelAssembly asm, int cx, int cy, int cw, int coverH, float partialTick) {
        if (cw < 14 || coverH < 22) {
            return;
        }
        try {
            String textureId = selectedTextureOrDefault(asm);
            ClientModelManager.markModelUsed(modelId);
            PlayerPreviewEntity entity = cardPreviewEntityFor(modelId, textureId);
            if (entity == null || !entity.isModelReady()) {
                return;
            }
            // fa26.1.2: 依模型 disable_rotation 决定朝向与缩放;main 无 extraYaw/extraPitch,微调角度并入 previewYaw。
            boolean disableRotation;
            try {
                var props = asm.getModelData().getModelProperties();
                disableRotation = props != null && props.isDisablePreviewRotation();
            } catch (Exception ignored) {
                disableRotation = false;
            }
            float[] cam = YSM_CAM[disableRotation ? 0 : 1];
            float scale = clamp(coverH * (cam[0] / 70.0f), 16.0f, 170.0f);
            float yaw = disableRotation ? ModelPreviewRenderer.FRONT_FACING_YAW : ModelPreviewRenderer.FRONT_FACING_YAW - 24.0f;
            ModelPreviewRenderer.renderLivingEntityPreview(cx + cw / 2.0f, cy + coverH - cam[1], scale, partialTick, entity,
                    RendererManager.getPlayerRenderer(), disableRotation, true, yaw);
        } catch (Exception ignored) {
            int size = Math.min(26, Math.min(cw - 8, coverH - 10));
            drawIconScaled(g, IconGlyph.MODEL, cx + Math.max(0, (cw - size) / 2), cy + Math.max(2, (coverH - size) / 2), size);
        }
    }

    /** 取/建某模型的卡片预览实体;换贴图时重建。失败返回 null(调用方回退图标)。 */
    private PlayerPreviewEntity cardPreviewEntityFor(String modelId, String textureId) {
        PlayerPreviewEntity existing = this.cardPreviewEntities.get(modelId);
        if (existing != null) {
            if (!textureId.equals(this.cardPreviewTextureIds.get(modelId))) {
                try {
                    existing.initModelWithTexture(modelId, textureId);
                    this.cardPreviewTextureIds.put(modelId, textureId);
                } catch (Exception ignored) {
                }
            }
            return existing;
        }
        PlayerPreviewEntity created = new PlayerPreviewEntity();
        try {
            created.initModelWithTexture(modelId, textureId);
        } catch (Exception e) {
            return null;
        }
        this.cardPreviewEntities.put(modelId, created);
        this.cardPreviewTextureIds.put(modelId, textureId);
        return created;
    }

    /** 只保留当前页上的卡片预览实体,翻页即释放上一页(防止实体/资源堆积)。 */
    private void evictCardPreviews(Set<String> keep) {
        if (this.cardPreviewEntities.size() > keep.size()) {
            for (String old : this.cardPreviewEntities.keySet()) {
                if (!keep.contains(old)) {
                    this.cardAnimator.forget(old);
                }
            }
            this.cardPreviewEntities.keySet().retainAll(keep);
            this.cardPreviewTextureIds.keySet().retainAll(keep);
        }
    }

    /** 旧式(紧凑/小窗)文字小格,保持原样外观。 */
    private void renderLegacyCell(GuiGraphics g, int mouseX, int mouseY, ModelEntry entry, int cx, int cy, int cw, int ch, int clipTop, int clipBottom, boolean dense, Set<String> starredModels) {
        boolean selected = entry.modelId().equals(STATE.selectedModelId) || this.selectedModelIds.contains(entry.modelId());
        int[] hitBox = clampCellToViewport(cx, cy, cw, ch, clipTop, clipBottom);
        boolean hover = hitBox != null && inside(mouseX, mouseY, hitBox[0], hitBox[1], hitBox[2], hitBox[3]);
        boolean starred = !entry.folder() && starredModels.contains(entry.modelId());
        fill(g, cx, cy, cw, ch, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : 0x3E30363B);
        border(g, cx, cy, cw, ch, selected ? RED : 0x33FFFFFF);
        int iconY = dense ? cy + Math.max(0, (ch - 16) / 2) : cy + 3;
        drawIcon(g, entry.folder() ? IconGlyph.FOLDER : entry.locked() ? IconGlyph.LOCK : IconGlyph.MODEL, cx + 4, iconY);
        if (dense) {
            if (starred) {
                drawIcon(g, IconGlyph.STAR, cx + cw - 18, iconY);
            }
            int titleW = starred ? cw - 44 : cw - 26;
            drawText(g, Component.literal(trim(entry.title(), titleW)), cx + 22, cy + (ch - this.font.lineHeight) / 2 + 1);
        } else {
            if (starred) {
                drawIcon(g, IconGlyph.STAR, cx + cw - 16, cy + ch - 16);
            }
            drawText(g, Component.literal(trim(entry.title(), cw - 28)), cx + 22, cy + 6);
            drawMuted(g, Component.literal(trim(entry.subtitle(), cw - 12)), cx + 6, cy + 22);
        }
        if (hitBox != null) {
            hit(hitBox[0], hitBox[1], hitBox[2], hitBox[3], Component.literal(entry.title()), () -> clickModelEntry(entry));
        }
    }

    /** 卡片格:上方封面/占位图,底部压名条。封面=文件夹 ysm 内嵌的 gui_background/gui_foreground。 */
    private void renderCardCell(GuiGraphics g, int mouseX, int mouseY, ModelEntry entry, int cx, int cy, int cw, int ch, int clipTop, int clipBottom, Set<String> starredModels) {
        boolean selected = entry.modelId().equals(STATE.selectedModelId) || this.selectedModelIds.contains(entry.modelId());
        boolean multiSelected = !entry.modelId().equals(STATE.selectedModelId) && this.selectedModelIds.contains(entry.modelId());
        int[] hitBox = clampCellToViewport(cx, cy, cw, ch, clipTop, clipBottom);
        boolean hover = hitBox != null && inside(mouseX, mouseY, hitBox[0], hitBox[1], hitBox[2], hitBox[3]);
        boolean starred = !entry.folder() && starredModels.contains(entry.modelId());
        fill(g, cx, cy, cw, ch, selected ? 0xEF5E7784 : hover ? 0xEF3F4A54 : 0xEF171A1E);
        int coverH = Math.max(1, ch - NAME_BAND);
        if (entry.folder()) {
            int size = Math.min(48, Math.min(cw - 18, coverH - 26));
            drawIconScaled(g, IconGlyph.FOLDER, cx + Math.max(0, (cw - size) / 2), cy + Math.max(4, (coverH - size) / 2), size);
        } else {
            ModelAssembly asm = residentAssembly(entry.modelId());
            AbstractTexture coverTex = coverTextureOf(asm);
            boolean coverDrawn = coverTex != null && drawCoverImage(g, coverTex, cx + 2, cy + 2, cw - 4, Math.max(1, coverH - 2));
            if (!coverDrawn) {
                int size = Math.min(42, Math.min(cw - 20, coverH - 30));
                drawIconScaled(g, IconGlyph.MODEL, cx + Math.max(0, (cw - size) / 2), cy + Math.max(4, (coverH - size) / 2), size);
                if (asm == null && !entry.locked()) {
                    drawCentered(g, Component.translatable("gui.sparkle_morpher.model_panel.model.on_demand"), cx + cw / 2, cy + coverH - 13, MUTED);
                }
            }
        }
        if (entry.locked()) {
            fill(g, cx + 1, cy + 1, cw - 2, coverH - 2, 0x99000000);
            drawIcon(g, IconGlyph.LOCK, cx + (cw - 18) / 2, cy + Math.max(2, (coverH - 18) / 2));
        }
        if (multiSelected) {
            drawIcon(g, IconGlyph.CHECK, cx + cw - 19, cy + 3);
        } else if (starred) {
            drawIcon(g, IconGlyph.STAR, cx + cw - 19, cy + 3);
        }
        int bandY = cy + coverH;
        if (ch > NAME_BAND) {
            fill(g, cx + 1, bandY, cw - 2, ch - coverH - 1, 0xB0000000);
        }
        drawText(g, Component.literal(trim(entry.title(), Math.max(8, cw - 10))), cx + 5, bandY + 3);
        if (!entry.folder() && NAME_BAND >= 29) {
            String sub = entry.subtitle();
            if (sub != null && !sub.isEmpty()) {
                drawMuted(g, Component.literal(trim(sub, Math.max(8, cw - 10))), cx + 5, bandY + 14);
            }
        }
        border(g, cx, cy, cw, ch, selected ? RED : hover ? 0x99FFFFFF : 0x374D5A66);
        if (hitBox != null) {
            hit(hitBox[0], hitBox[1], hitBox[2], hitBox[3], Component.literal(entry.title()), () -> clickModelEntry(entry));
        }
    }

    /** 把格子点击/悬停区夹到网格视口内,避免像素滚动时半可见首/末行把可点区伸出面板外。返回 null 表示完全不可见。 */
    private static int[] clampCellToViewport(int cx, int cy, int cw, int ch, int clipTop, int clipBottom) {
        int top = Math.max(cy, clipTop);
        int bottom = Math.min(cy + ch, clipBottom);
        if (bottom <= top) {
            return null;
        }
        return new int[]{cx, top, cw, bottom - top};
    }

    private ModelListMetrics modelListMetrics(int w, int h, int entryCount) {
        boolean dense = false; // 兜底文字行格统一两行,保证副标题(作者/纹理数)始终可见
        boolean cards = false; // 目录卡片页由 renderCatalogGrid 独占;此处恒为文字行格
        int cellW;
        int cellH;
        int cols;
        if (cards) {
            int targetCols = Math.max(1, w / CARD_TARGET_W);
            cellW = clamp(w / targetCols, CARD_MIN_CELL_W, CARD_MAX_W);
            cols = Math.max(1, w / cellW);
            cellH = Math.max(CARD_MIN_H - 12, Math.min(CARD_MAX_CELL_H, cellW + 26));
        } else {
            int targetW = dense ? 104 : 116;
            int minW = dense ? 86 : 92;
            int maxW = dense ? 132 : 150;
            cellW = Math.max(minW, Math.min(maxW, w / Math.max(1, w / targetW)));
            cols = Math.max(1, w / cellW);
            cellH = dense ? DENSE_MODEL_ROW : h < 110 ? 42 : 50;
        }
        int contentRows = entryCount == 0 ? 0 : (entryCount + cols - 1) / cols;
        int maxScroll = Math.max(0, contentRows * cellH - h);
        return new ModelListMetrics(cellW, cellH, cols, contentRows, maxScroll, dense, cards);
    }

    private List<ModelEntry> visibleModelEntries() {
        List<ModelEntry> entries = collectModelEntries();
        if (entries.isEmpty()) {
            return List.of();
        }
        if (modelCardsActive()) {
            CatalogMetrics m = catalogMetrics(modelListW(), currentModelGridH(), entries.size());
            int page = clamp(STATE.modelScroll, 0, m.totalPages() - 1);
            int start = page * m.capacity();
            int end = Math.min(entries.size(), start + m.capacity());
            return start >= end ? List.of() : entries.subList(start, end);
        }
        ModelListMetrics metrics = modelListMetrics(modelListW(), currentModelGridH(), entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, metrics.maxScroll());
        int[] range = visibleIndexRange(metrics, entries.size());
        return range[1] <= range[0] ? List.of() : entries.subList(range[0], range[1]);
    }

    /** 当前滚动下至少部分可见的条目下标区间 [first, endExclusive)。 */
    private int[] visibleIndexRange(ModelListMetrics metrics, int count) {
        if (count == 0 || metrics.cellH() <= 0) {
            return new int[]{0, 0};
        }
        int cols = metrics.cols();
        int contentRows = metrics.contentRows();
        int startRow = clamp(STATE.modelScroll / metrics.cellH(), 0, Math.max(0, contentRows - 1));
        int viewportH = currentModelGridH();
        int rowsVisible = Math.max(1, (viewportH + metrics.cellH() - 1) / metrics.cellH());
        int first = startRow * cols;
        int last = Math.min(count, first + rowsVisible * cols);
        return new int[]{first, last};
    }

    /** 模型网格每格滚轮滚动像素量;卡片较高时限制步长避免跳太快。 */
    private int modelWheelStep() {
        int cellH = DENSE_MODEL_ROW;
        if (this.layout != null) {
            List<ModelEntry> entries = collectModelEntries();
            if (!entries.isEmpty()) {
                ModelListMetrics metrics = modelListMetrics(modelListW(), currentModelGridH(), entries.size());
                cellH = metrics.cellH();
            }
        }
        return Math.max(8, Math.min(WHEEL_STEP_MAX, cellH));
    }

    /** 常驻(运行时已解析)assembly;懒/占位 assembly 返回 null。 */
    private ModelAssembly residentAssembly(String modelId) {
        if (modelId == null) {
            return null;
        }
        ModelAssembly asm = ClientModelManager.getModelAssemblyMap().get(modelId);
        return asm != null && asm.isRuntimeResident() ? asm : null;
    }

    /** 文件夹 ysm 内嵌 GUI 封面:优先 gui_background,缺则退回 gui_foreground。 */
    private AbstractTexture coverTextureOf(ModelAssembly asm) {
        if (asm == null) {
            return null;
        }
        ModelDisplayAssets assets = asm.getTextureRegistry();
        if (assets == null) {
            return null;
        }
        AbstractTexture background = assets.getGuiBackground();
        return background != null ? background : assets.getGuiForeground();
    }

    /** 把封面等宽高比 fit 进 (x,y,w,h),居中不拉伸。无法获得资源/尺寸时返回 false。 */
    private boolean drawCoverImage(GuiGraphics g, AbstractTexture tex, int x, int y, int w, int h) {
        if (tex == null || w <= 0 || h <= 0) {
            return false;
        }
        int[] dims = coverDimensions(tex);
        if (dims == null) {
            return false;
        }
        IResourceLocatable locatable = UploadManager.getOrCreateLocatable(tex, true);
        if (locatable == null) {
            return false;
        }
        ResourceLocation loc = locatable.getResourceLocationOrNull();
        if (loc == null) {
            return false;
        }
        int texW = dims[0];
        int texH = dims[1];
        double scale = Math.min((double) w / texW, (double) h / texH);
        int dw = Math.max(1, (int) Math.floor(texW * scale));
        int dh = Math.max(1, (int) Math.floor(texH * scale));
        g.blit(loc, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, 0.0f, 0.0f, texW, texH, texW, texH);
        return true;
    }

    /** 封面尺寸(像素)。gui 图一定是 OuterFileTexture(构建链 toPng),直接读 PNG IHDR。 */
    private int[] coverDimensions(AbstractTexture tex) {
        if (!(tex instanceof OuterFileTexture outer)) {
            return null;
        }
        synchronized (COVER_DIMS) {
            int[] dims = COVER_DIMS.get(tex);
            if (dims == null) {
                dims = pngHeaderSize(outer.getResourceData());
                if (dims != null) {
                    COVER_DIMS.put(tex, dims);
                }
            }
            return dims;
        }
    }

    private static int[] pngHeaderSize(byte[] data) {
        if (data == null || data.length < 24) {
            return null;
        }
        // PNG 签名 8 字节 + IHDR 块(length 4 + "IHDR" 4),宽高自偏移 16 起大端。
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

    /** 图标纹理按 size 等比放大绘制(源是 16px 格)。 */
    private void drawIconScaled(GuiGraphics g, IconGlyph icon, int x, int y, int size) {
        if (size <= 0) {
            return;
        }
        g.blit(MODEL_PANEL_ICONS, x, y, size, size, icon.u, icon.v, 16, 16, 128, 64);
    }

    private int currentModelGridH() {
        return modelListGridHeight();
    }

    /** 列表列纵向几何的单一来源:renderModelTab 与各 metrics 消费者共用,避免两处公式漂移。 */
    private int modelPathY() {
        int y = this.layout.contentTop + 8;
        if (modelHeaderWide()) {
            return y + 30;
        }
        return modelListW() < 260 ? y + 68 : y + 48;
    }

    private int modelListGridHeight() {
        int bottom = this.layout.footerTop - 6;
        int stripH = modelDetailsRight() ? 0 : compactDetailStripH(); // 详情在右栏时不再占用底部条空间
        int reserve = stripH > 0 ? stripH + 3 : 0;
        int minH = (modelHeaderWide() || modelDetailsRight()) ? 50 : 34;
        return Math.max(minH, bottom - 28 - 4 - reserve - modelPathY() - 20);
    }

    private void renderModelBottomActions(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int gridH) {
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
        renderModelPageControls(g, mouseX, mouseY, x, y, w, gridH);
    }

    private void renderModelPageControls(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int gridH) {
        List<ModelEntry> entries = collectModelEntries();
        if (entries.isEmpty()) {
            return;
        }
        if (fitsCatalog(w, gridH)) {
            renderCatalogPageControls(g, mouseX, mouseY, entries, x, y, w, gridH);
            return;
        }
        ModelListMetrics metrics = modelListMetrics(w, gridH, entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, metrics.maxScroll());
        int[] range = visibleIndexRange(metrics, entries.size());
        int start = range[0];
        int end = Math.min(entries.size(), range[1]);
        Component countRange = Component.literal((start + 1) + "-" + end + "/" + entries.size());
        int nextX = x + w - MODEL_PAGE_BUTTON_WIDTH - 6;
        int prevX = nextX - MODEL_PAGE_BUTTON_WIDTH - 4;
        int labelX = Math.max(x + 82, prevX - this.font.width(countRange) - 8);
        drawMuted(g, countRange, labelX, y + 8);
        int pageStep = Math.max(1, gridH - metrics.cellH() / 2);
        renderTextButton(g, mouseX, mouseY, prevX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.pre_page"), () -> {
            STATE.modelScroll = Math.max(0, STATE.modelScroll - pageStep);
        });
        renderTextButton(g, mouseX, mouseY, nextX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.next_page"), () -> {
            STATE.modelScroll = Math.min(metrics.maxScroll(), STATE.modelScroll + pageStep);
        });
    }

    /** 目录卡片页翻页控件:STATE.modelScroll 此时即页号,整页前进/后退。 */
    private void renderCatalogPageControls(GuiGraphics g, int mouseX, int mouseY, List<ModelEntry> entries, int x, int y, int w, int gridH) {
        CatalogMetrics m = catalogMetrics(w, gridH, entries.size());
        int page = clamp(STATE.modelScroll, 0, m.totalPages() - 1);
        STATE.modelScroll = page;
        int start = page * m.capacity();
        int end = Math.min(entries.size(), start + m.capacity());
        Component countRange = Component.literal((start + 1) + "-" + end + "/" + entries.size());
        int nextX = x + w - MODEL_PAGE_BUTTON_WIDTH - 6;
        int prevX = nextX - MODEL_PAGE_BUTTON_WIDTH - 4;
        int labelX = Math.max(x + 82, prevX - this.font.width(countRange) - 8);
        drawMuted(g, countRange, labelX, y + 8);
        renderTextButton(g, mouseX, mouseY, prevX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.pre_page"), () -> {
            if (page > 0) {
                STATE.modelScroll = page - 1;
            }
        });
        renderTextButton(g, mouseX, mouseY, nextX, y + 3, MODEL_PAGE_BUTTON_WIDTH, MODEL_PAGE_BUTTON_HEIGHT, Component.translatable("gui.sparkle_morpher.next_page"), () -> {
            if (page < m.totalPages() - 1) {
                STATE.modelScroll = page + 1;
            }
        });
    }

    /**
     * 目录页懒模型自动预载:后台把「当前页 + 下一页」的未常驻模型逐个载入常驻,
     * 落地后卡片小人/封面随后续渲染自然出现。只对目录页生效(文字行格不做预载)。
     */
    private void preloadVisibleCovers() {
        if (STATE.activeTab != ModelPanelState.Tab.MODEL
                || STATE.secondaryPanel != ModelPanelState.SecondaryPanel.NONE
                || this.layout == null
                || !modelCardsActive()) {
            return;
        }
        List<ModelEntry> entries = collectModelEntries();
        if (entries.isEmpty()) {
            return;
        }
        CatalogMetrics m = catalogMetrics(modelListW(), currentModelGridH(), entries.size());
        STATE.modelScroll = clamp(STATE.modelScroll, 0, m.totalPages() - 1);
        int budget = PRELOAD_BUDGET;
        for (int p = 0; p < 2 && budget > 0; p++) {
            int page = clamp(STATE.modelScroll + p, 0, m.totalPages() - 1);
            int start = page * m.capacity();
            int end = Math.min(entries.size(), start + m.capacity());
            for (int i = start; i < end && budget > 0; i++) {
                ModelEntry entry = entries.get(i);
                if (entry.folder()) {
                    continue;
                }
                String id = entry.modelId();
                if (residentAssembly(id) != null) {
                    this.preloadFailed.remove(id);
                    this.preloadWatching.remove(id);
                    this.preloadWatchStart.remove(id);
                    continue;
                }
                if (this.preloadFailed.contains(id)) {
                    continue;
                }
                if (this.preloadWatching.contains(id)) {
                    Integer started = this.preloadWatchStart.get(id);
                    if (started != null && this.preloadTicker - started >= PRELOAD_GIVE_UP_TICKS
                            && !ClientModelManager.isModelLoadPending(id)) {
                        // 长时间未落地且不再有加载任务:判为失败,本屏幕内不再尝试。
                        this.preloadWatching.remove(id);
                        this.preloadWatchStart.remove(id);
                        this.preloadFailed.add(id);
                    }
                    continue;
                }
                this.preloadWatching.add(id);
                this.preloadWatchStart.put(id, this.preloadTicker);
                budget--;
                ClientModelManager.getModelContext(id);
            }
        }
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
        boolean wasTrimmed = ClientModelManager.isGpuCacheTrimmed(modelId);
        ClientModelManager.markModelUsed(modelId);
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
            boolean selected = entry.url().equals(STATE.selectedResourceUrl) || this.selectedResourceUrls.contains(entry.url());
            boolean hover = inside(mouseX, mouseY, x + 3, rowY + 2, ww - 6, ROW - 4);
            fill(g, x + 3, rowY + 2, ww - 6, ROW - 4, selected ? PANEL_ACTIVE : hover ? PANEL_HOVER : (i & 1) == 0 ? 0x3E30363B : 0x3630363B);
            g.drawString(this.font, trim(entry.name(), ww - 78), x + 8, rowY + 5, TEXT, false);
            g.drawString(this.font, trim(resourceDetail(entry), ww - 100), x + 8, rowY + 15, MUTED, false);
            hit(x + 3, rowY + 2, ww - 38, ROW - 4, Component.literal(entry.name()), () -> clickResource(entry));
            renderIconButton(g, mouseX, mouseY, x + ww - 28, rowY + 4, ResourceDownloadManager.isQueued(entry) ? IconGlyph.QUEUE : IconGlyph.DOWNLOAD, Component.translatable("gui.sparkle_morpher.model_panel.download"), () -> enqueueResource(entry));
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

    private void renderTaskRow(GuiGraphics g, int x, int y, int w, ResourceDownloadManager.TaskSnapshot task) {
        fill(g, x, y, w, 20, GLASS_DARK);
        drawText(g, Component.literal(trim(task.name(), w - 58)), x + 4, y + 3);
        int barX = x + 4;
        int barY = y + 14;
        int fillW = (int) ((w - 8) * clamp(task.progress(), 0f, 1f));
        fill(g, barX, barY, w - 8, 3, 0xAA101010);
        fill(g, barX, barY, fillW, 3, stateColor(task.state()));
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
            case DEBUG -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.debug");
            case MISC -> Component.translatable("gui.sparkle_morpher.model_panel.setting_group.misc");
        };
    }

    private void renderSettingRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, SettingRow row) {
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

    private void renderFooter(GuiGraphics g) {
        fill(g, this.layout.left, this.layout.footerTop, this.layout.width, 1, 0x55303030);
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        Component line = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? snapshot.status() : this.status;
        ChatFormatting color = this.status.getString().isBlank() && STATE.activeTab == ModelPanelState.Tab.RESOURCE ? snapshot.statusColor() : this.statusColor;
        int c = color.getColor() == null ? MUTED : 0xFF000000 | color.getColor();
        g.drawString(this.font, trim(line.getString(), this.layout.width - 20), this.layout.left + 10, this.layout.footerTop + 8, c, false);
    }

    private void renderTooltip(GuiGraphics g, int mouseX, int mouseY) {
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
                g.drawString(this.font, trim(text, tw - 8), tx + 5, ty + 5, 0xFFFFFFFF, false);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && beginResourceScrollDrag(mouseX, mouseY)) {
            return true;
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
                if (modelCardsActive()) {
                    List<ModelEntry> pageEntries = collectModelEntries();
                    int pages = pageEntries.isEmpty() ? 1 : catalogMetrics(modelListW(), currentModelGridH(), pageEntries.size()).totalPages();
                    STATE.modelScroll = clamp(STATE.modelScroll + delta, 0, pages - 1);
                } else {
                    STATE.modelScroll = Math.max(0, STATE.modelScroll + delta * modelWheelStep());
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
            for (String pack : ClientModelManager.getModelPackMap().keySet()) {
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
            for (String modelId : ClientModelManager.getAvailableModelIds()) {
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
            out.add(ModelEntry.model(modelId, listTitle(modelId, assembly), modelSubtitle(modelId, assembly), locked));
        }
        for (String modelId : ClientModelManager.getAvailableModelIds()) {
            if (ClientModelManager.getModelAssemblyMap().containsKey(modelId)) continue;
            if (!searching && !isDirectModel(STATE.currentPath, modelId)) continue;
            boolean authModel = ClientModelManager.isAuthModel(modelId);
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
        return applyStableModelOrder(out, stars);
    }

    /**
     * 模型条目的稳定排序键:文件夹与模型分开记名,避免同名字符串被两种条目同时占用。
     */
    private static String modelEntryOrderKey(ModelEntry entry) {
        return (entry.folder() ? "F/" : "M/") + entry.modelId();
    }

    /**
     * 排序指纹:只依赖“当前模型集合 + 浏览上下文”,与条目名字/常驻加载状态无关。
     * 集合/路径/筛选/搜索/星标任一变化 → 指纹变化 → 重新全量排序;否则复用上一次排序。
     */
    private String modelListFingerprintOf(List<ModelEntry> entries, Set<String> stars) {
        StringBuilder sb = new StringBuilder(96 + entries.size() * 24);
        sb.append(STATE.currentPath).append('\0');
        sb.append(STATE.modelFilter).append('\0');
        sb.append(STATE.modelSearchText.trim()).append('\0');
        List<String> keys = new ArrayList<>(entries.size());
        for (ModelEntry entry : entries) {
            String key = modelEntryOrderKey(entry);
            if (!entry.folder() && stars.contains(entry.modelId())) {
                key = "*" + key; // 星标集合也参与指纹:列表内点星/取消后需要重排
            }
            keys.add(key);
        }
        keys.sort(String::compareTo);
        for (String key : keys) {
            sb.append(key).append('\1');
        }
        return sb.toString();
    }

    /**
     * 集合未变时复用上次的稳定顺序,只为仍存在的条目刷新文字(title/subtitle/locked),
     * 新出现的条目(指纹变化必走全量重建,此分支仅作兜底)按当前顺序追加到末尾。
     */
    private List<ModelEntry> applyStableModelOrder(List<ModelEntry> unsorted, Set<String> stars) {
        String fingerprint = modelListFingerprintOf(unsorted, stars);
        if (fingerprint.equals(this.modelListFingerprint) && !this.modelListOrder.isEmpty()) {
            Map<String, ModelEntry> byKey = new HashMap<>(Math.max(16, unsorted.size() * 2));
            for (ModelEntry entry : unsorted) {
                byKey.put(modelEntryOrderKey(entry), entry);
            }
            List<ModelEntry> ordered = new ArrayList<>(unsorted.size());
            for (String key : this.modelListOrder) {
                ModelEntry entry = byKey.remove(key);
                if (entry != null) {
                    ordered.add(entry);
                }
            }
            for (ModelEntry entry : unsorted) {
                if (byKey.containsKey(modelEntryOrderKey(entry))) {
                    ordered.add(entry);
                }
            }
            return ordered;
        }
        List<ModelEntry> sorted = new ArrayList<>(unsorted);
        sorted.sort(Comparator
                .<ModelEntry, Boolean>comparing(entry -> !stars.contains(entry.modelId()))
                .thenComparing(Comparator.<ModelEntry, Boolean>comparing(ModelEntry::folder).reversed())
                .thenComparing(entry -> entry.title().toLowerCase(Locale.ROOT)));
        this.modelListFingerprint = fingerprint;
        this.modelListOrder = new ArrayList<>(sorted.size());
        for (ModelEntry entry : sorted) {
            this.modelListOrder.add(modelEntryOrderKey(entry));
        }
        return sorted;
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
        ModelAssembly assembly = ClientModelManager.getModelContext(entry.modelId()).orElse(null);
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

    private void applyModelAndTexture(String modelId, String textureId, ModelAssembly assembly) {
        if (this.modelSelectionTarget != null) {
            ClientModelManager.rememberSelectedModel(modelId, textureId);
            this.modelSelectionTarget.accept(modelId, textureId);
            setStatus(Component.translatable("gui.sparkle_morpher.model_panel.applied_model", modelId), ChatFormatting.GREEN);
            return;
        }
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
                LocalStarModelsStore.remove(STATE.selectedModelId);
                NetworkHandler.sendToServer(C2SSetStarModelPacket.remove(STATE.selectedModelId));
            } else {
                cap.addModel(STATE.selectedModelId);
                LocalStarModelsStore.add(STATE.selectedModelId);
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
            String text = entry.name() + " "
                    + ModelRepoClient.safeModelId(entry) + " "
                    + entry.fileName() + " "
                    + entry.description() + " "
                    + entry.author() + " "
                    + entry.tags() + " "
                    + entry.url() + " "
                    + entry.githubOwner() + " "
                    + entry.githubRepo() + " "
                    + entry.githubBranch() + " "
                    + entry.githubPath();
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
        this.resourceConfig = new ResourceStationConfig.State(this.resourceConfig.urls(), url, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
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
        this.resourceConfig = new ResourceStationConfig.State(urls, url, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
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
        this.resourceConfig = new ResourceStationConfig.State(urls, selected, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        STATE.siteEditText = selected;
        refreshResources(false);
        init();
    }

    private void toggleResourceMode() {
        this.resourceConfig = new ResourceStationConfig.State(this.resourceConfig.urls(), this.resourceConfig.selectedUrl(), this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), !this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
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
        setFocused(null);
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
            Util.getPlatform().openFile(ServerModelManager.CUSTOM.toFile());
            setStatus(Component.literal(ServerModelManager.CUSTOM.toString()), ChatFormatting.GRAY);
        } catch (IOException e) {
            setStatus(Component.translatable("gui.sparkle_morpher.import.error.open_folder", e.getMessage()), ChatFormatting.RED);
        }
    }

    private void openCustomFolderUpload() {
        Minecraft.getInstance().setScreen(new CustomFolderUploadScreen(this));
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
            if (ClientModelManager.isGltfFileName(fileName)) {
                setStatus(Component.translatable("gui.sparkle_morpher.import.state.local_imported_as", modelId), ChatFormatting.GREEN);
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
            if (modelAssembly != null && !modelAssembly.isGltf() && modelAssembly.getModelData() != null
                    && !modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                minecraft.setScreen(new UnifiedRouletteScreen(modelId, modelAssembly, cap));
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
        rows.add(rendererModeRow(ModelPanelState.SettingGroup.PERFORMANCE));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.java_vector_renderer", GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER));
        rows.add(bool(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.lazy_model_loading", GeneralConfig.LAZY_MODEL_LOADING));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.gpu_cache_limit", GeneralConfig.MAX_CACHED_GPU_MODELS, 0, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.cpu_cache_limit", GeneralConfig.MAX_RESIDENT_CPU_MODELS, 1, 512, 1, ""));
        rows.add(intRow(ModelPanelState.SettingGroup.CACHE, "gui.sparkle_morpher.model_panel.setting.unused_model_ttl", GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 30, 86400, 30, "s"));
        rows.add(bool(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.enable_global_bandwidth_limit", ServerConfig.ENABLE_GLOBAL_BANDWIDTH_LIMIT));
        rows.add(intRow(ModelPanelState.SettingGroup.PERFORMANCE, "gui.sparkle_morpher.model_panel.setting.bandwidth_limit", ServerConfig.BANDWIDTH_LIMIT, 1, 999, 10, "Mbps"));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.resource_monitor_log", GeneralConfig.RESOURCE_STATION_MONITOR_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.model_memory_profiler", GeneralConfig.MODEL_MEMORY_PROFILER));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.import_performance_log", GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.animation_frame_profiler", GeneralConfig.ANIMATION_FRAME_PROFILER));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.animation_debug_log", GeneralConfig.ANIMATION_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.DEBUG, "gui.sparkle_morpher.model_panel.setting.input_debug_log", GeneralConfig.INPUT_STATE_DEBUG_LOG));
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.show_model_id_first", GeneralConfig.SHOW_MODEL_ID_FIRST));
        rows.add(bool(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_disabled", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN));
        rows.add(loadingPositionRow(ModelPanelState.SettingGroup.MISC));
        rows.add(intRow(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_offset_x", LoadingStateConfig.LOADING_STATE_OFFSET_X, -10000, 10000, 10, "px"));
        rows.add(intRow(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_offset_y", LoadingStateConfig.LOADING_STATE_OFFSET_Y, -10000, 10000, 10, "px"));
        rows.add(intRow(ModelPanelState.SettingGroup.MISC, "gui.sparkle_morpher.model_panel.setting.loading_state_auto_hide", LoadingStateConfig.LOADING_STATE_AUTO_HIDE_SECONDS, 1, 30, 1, "s"));
        return rows.stream().filter(row -> row.group() == STATE.settingGroup).toList();
    }

    private SettingRow bool(ModelPanelState.SettingGroup group, String labelKey, ForgeConfigSpec.BooleanValue value) {
        boolean current = safeBool(value);
        return new SettingRow(group, labelKey, current, "", () -> {
            value.set(!safeBool(value));
            value.save();
        }, null, null, null);
    }

    private SettingRow invertedBool(ModelPanelState.SettingGroup group, String labelKey, ForgeConfigSpec.BooleanValue value) {
        boolean current = !safeBool(value);
        return new SettingRow(group, labelKey, current, "", () -> {
            value.set(!safeBool(value));
            value.save();
        }, null, null, null);
    }

    private SettingRow actionRow(ModelPanelState.SettingGroup group, String labelKey, Runnable action) {
        return new SettingRow(group, labelKey, null,
                Component.translatable("gui.sparkle_morpher.model_panel.setting.configure").getString(),
                action, null, null, null);
    }

    private SettingRow privacyModeRow(ModelPanelState.SettingGroup group) {
        boolean current = PrivacyMode.isConfigured();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.privacy_mode", current, "", () -> {
            boolean enabled = !PrivacyMode.isConfigured();
            GeneralConfig.PRIVACY_MODE.set(enabled);
            GeneralConfig.PRIVACY_MODE.save();
            PrivacyMode.onConfigChanged(enabled);
        }, null, null, null);
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
                }, null);
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

    private SettingRow loadingPositionRow(ModelPanelState.SettingGroup group) {
        LoadingStateConfig.Position current;
        try {
            current = LoadingStateConfig.LOADING_STATE_POSITION.get();
        } catch (Exception e) {
            current = LoadingStateConfig.Position.TOP_CENTER;
        }
        final LoadingStateConfig.Position selected = current;
        final LoadingStateConfig.Position[] values = LoadingStateConfig.Position.values();
        String valueText = Component.translatable("gui.sparkle_morpher.config.loading_state_position.value." + selected.name().toLowerCase(Locale.ROOT)).getString();
        return new SettingRow(group, "gui.sparkle_morpher.model_panel.setting.loading_state_position", null, valueText, null,
                () -> {
                    LoadingStateConfig.Position prev = values[(selected.ordinal() - 1 + values.length) % values.length];
                    LoadingStateConfig.LOADING_STATE_POSITION.set(prev);
                    LoadingStateConfig.LOADING_STATE_POSITION.save();
                },
                () -> {
                    LoadingStateConfig.Position next = values[(selected.ordinal() + 1) % values.length];
                    LoadingStateConfig.LOADING_STATE_POSITION.set(next);
                    LoadingStateConfig.LOADING_STATE_POSITION.save();
                }, null);
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
        return this.resourceEntries.stream().filter(e -> e.url().equals(STATE.selectedResourceUrl)).findFirst().orElse(null);
    }

    private String selectedTextureOrDefault(ModelAssembly assembly) {
        List<String> names = assembly.getTextureNames();
        if (STATE.selectedTextureId != null && !STATE.selectedTextureId.isBlank() && names.contains(STATE.selectedTextureId)) {
            return STATE.selectedTextureId;
        }
        return names.isEmpty() ? "" : names.get(0);
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
        return this.resourceConfig.preferGithubAccelerator()
                ? Component.translatable("gui.sparkle_morpher.resource_station.mode.mainland")
                : Component.translatable("gui.sparkle_morpher.resource_station.mode.native");
    }

    private boolean isMainlandResourceMode() {
        return this.resourceConfig.preferGithubAccelerator();
    }

    /**
     * 卡片名字的单一来源:真正常驻(有运行态几何/表达式缓存)的模型显示实时本地化名;
     * 尚未真正加载的(LazyModelAssembly 占位 / 纯目录项)用目录缓存名——嗅探到的 metadata 名
     * 或服务器解析后写回 source.displayName 的真名,都没有才退回 modelId。
     *
     * <p>这样同一模型在“目录项→常驻”两个状态下名字不各算一套:占位被真正模型替换时,
     * 卡片只是原位把名字升级成真名,不会因为换了一套名字源而被重新排序乱跳。</p>
     */
    private String listTitle(String modelId, ModelAssembly assembly) {
        if (assembly != null && assembly.isRuntimeResident()) {
            String live = displayName(modelId, assembly);
            if (!modelId.equals(live)) {
                return live;
            }
        }
        return lazyModelDisplayName(modelId);
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
        String sniffed = ClientModelManager.getLazyModelDisplayName(modelId);
        return StringUtils.isBlank(sniffed) ? modelId : sniffed;
    }

    private String modelSubtitle(String modelId, ModelAssembly assembly) {
        List<String> parts = new ArrayList<>();
        if (ClientModelManager.isLocalOnlyModel(modelId)) {
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
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel", ".gltf", ".glb"}) {
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

    private int compactDetailStripH() {
        if (modelDetailsRight()) {
            return 0;
        }
        boolean hasSel = !STATE.selectedModelId.isBlank();
        if (hasSel) {
            return STATE.compactPreviewState == 2 ? 18 : 112; // 选中即自动展开,除非用户手动收起
        }
        return STATE.compactPreviewState == 1 ? 112 : 18;
    }

    private void renderCompactDetail(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, float partialTick) {
        int barH = 18;
        fill(g, x, y, w, h, GLASS_DARK);
        border(g, x, y, w, h, 0x33FFFFFF);
        boolean expanded = h > 18;
        renderIconButton(g, mouseX, mouseY, x + 1, y, expanded ? IconGlyph.MINUS : IconGlyph.PLUS,
                Component.translatable("gui.sparkle_morpher.model_panel.details"), () -> STATE.compactPreviewState = expanded ? 2 : 1);
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
                Component.translatable("gui.sparkle_morpher.model_panel.info"), () -> STATE.compactPreviewState = 1);
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

    private record ModelListMetrics(int cellW, int cellH, int cols, int contentRows, int maxScroll, boolean dense, boolean cards) {
    }

    private record CatalogMetrics(int cols, int rows, int cellW, int cellH, int capacity, int totalPages) {
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
