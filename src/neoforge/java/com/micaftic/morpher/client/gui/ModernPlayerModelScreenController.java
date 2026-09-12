package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.client.PrivacyMode;
import com.micaftic.morpher.client.gui.resource.ModelRepoClient;
import com.micaftic.morpher.client.gui.resource.ModelRepoEntry;
import com.micaftic.morpher.client.gui.resource.ResourceDownloadManager;
import com.micaftic.morpher.client.gui.resource.ResourceStationConfig;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.ModelImportFilePicker;
import com.micaftic.morpher.client.upload.ModelUploadSession;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.LoadingStateConfig;
import com.micaftic.morpher.core.vector.VectorApiCapability;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import com.micaftic.morpher.network.message.C2SSetStarModelPacket;
import com.micaftic.morpher.util.LocalStarModelsStore;
import com.micaftic.morpher.util.SmExecutors;
import com.micaftic.morpher.util.ModelIdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 1.2.7 §24.7：从 {@code ModernPlayerModelScreen} 外提的「模型面板服务层」。
 *
 * <p>路线图要求 Screen 不再直接操作整个底层系统，而是走
 * {@code Screen → Service/Controller → 底层系统}。本类即那一层 Service：所有对
 * {@code ModelRepoClient} / {@code ResourceDownloadManager} / {@code ModelUploadSession} /
 * {@code ModelImportFilePicker} / {@code UploadManager} / {@code ClientModelManager} /
 * {@code ModelPanelFileActions} 以及配置读写的调用都收敛在这里。</p>
 *
 * <p><b>行为等价（§4.2 Facade First）</b>：本类只做搬运，不改变任何分支/文案/时序。
 * Screen 的对外可见行为、GUI 布局与文案一律不变；旧方法保留为薄委托，未拆完的部分
 * 见 {@code ModernPlayerModelScreen} 内注释。</p>
 */
public final class ModernPlayerModelScreenController {

    /** Screen 侧回调：Service 不直接持有 Screen 实例，只通过该接口回报。 */
    public interface Host {
        /** 普通状态栏消息（底部 footer）。 */
        void postStatus(Component component, ChatFormatting color);

        /** 资源站专用状态栏消息（与 {@link #postStatus} 同一槽位，但允许被普通消息覆盖）。 */
        void postResourceStatus(Component component, ChatFormatting color);

        /** 请求 Screen 重建控件（原 {@code init()}）。 */
        void requestRerender();
    }

    /** 一次导入动作的结果（Screen 据此设置文案，文案仍留在 Screen 侧）。 */
    public enum ApplyResult {
        /** 应用到了外部回调目标（女仆 / NPC 面板）。 */
        APPLIED_TO_TARGET,
        /** 应用到了本地玩家（直接 capability 或发包）。 */
        APPLIED_TO_PLAYER,
        /** 本地玩家不在线。 */
        NO_PLAYER,
        /** 玩家 capability 缺失。 */
        NO_CAPABILITY
    }

    /** 下载队列行的只读视图（Screen 不再直接持有 {@code ResourceDownloadManager.TaskSnapshot}）。 */
    public record TaskView(String name, float progress, int color) {
    }

    private static final ExecutorService RESOURCE_EXECUTOR = SmExecutors.pool(SmExecutors.Pool.MODEL_IO);

    private final ModelPanelState state;
    private final Host host;

    private ResourceStationConfig.State resourceConfig = ResourceStationConfig.load();
    private final List<ModelRepoEntry> resourceEntries = new ArrayList<>();
    private final Set<String> selectedResourceUrls = new LinkedHashSet<>();
    private final Queue<ModelImportFilePicker.PickedFile> pendingImports = new ArrayDeque<>();
    private boolean localImportInProgress;
    private int screenGeneration;

    public ModernPlayerModelScreenController(ModelPanelState state, Host host) {
        this.state = state;
        this.host = host;
    }

    // ================================================================
    // 资源站：站点配置（持久化读写）
    // ================================================================

    public ResourceStationConfig.State config() {
        return this.resourceConfig;
    }

    public List<String> siteUrls() {
        return this.resourceConfig.urls();
    }

    public String selectedSite() {
        return this.resourceConfig.selectedUrl();
    }

    public boolean mainlandChinaMode() {
        return this.resourceConfig.preferGithubAccelerator();
    }

    public int timeoutMs() {
        return this.resourceConfig.timeoutMs();
    }

    public int maxDownloadBytes() {
        return this.resourceConfig.maxDownloadBytes();
    }

    public List<String> githubAccelerators() {
        return this.resourceConfig.githubAccelerators();
    }

    /** 选中站点并落盘（原 {@code selectSite}）。 */
    public void selectSite(String url) {
        this.resourceConfig = new ResourceStationConfig.State(this.resourceConfig.urls(), url, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
    }

    /** 新增站点并落盘；返回是否真的写入了（空串直接忽略）。 */
    public boolean addSite(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        List<String> urls = new ArrayList<>(this.resourceConfig.urls());
        if (!urls.contains(url)) {
            urls.add(0, url);
        }
        this.resourceConfig = new ResourceStationConfig.State(urls, url, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        return true;
    }

    /**
     * 删除站点并落盘。
     *
     * @return 删除后生效的站点 url；无法删除（仅剩一个或不存在）时返回 {@code null}。
     */
    public String removeSite(String url) {
        List<String> urls = new ArrayList<>(this.resourceConfig.urls());
        if (urls.size() <= 1 || !urls.remove(url)) {
            return null;
        }
        String selected = urls.get(0);
        this.resourceConfig = new ResourceStationConfig.State(urls, selected, this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
        return selected;
    }

    /** 切换大陆模式并落盘。 */
    public void toggleMainlandChinaMode() {
        this.resourceConfig = new ResourceStationConfig.State(this.resourceConfig.urls(), this.resourceConfig.selectedUrl(), this.resourceConfig.timeoutMs(), this.resourceConfig.maxDownloadBytes(), !this.resourceConfig.preferGithubAccelerator(), this.resourceConfig.githubAccelerators());
        ResourceStationConfig.save(this.resourceConfig);
    }

    // ================================================================
    // 资源站：列表拉取（网络）与筛选
    // ================================================================

    /**
     * 拉取资源列表（原 {@code refreshResources}）。
     *
     * <p>异步 + 超时 + 代际/请求号双重失效检查，行为与拆分前逐行等价。</p>
     */
    public void refreshResources(boolean manual) {
        int requestId = ++this.state.resourceRequestId;
        int generation = this.screenGeneration;
        ResourceStationConfig.State config = this.resourceConfig;
        this.state.resourceLoading = true;
        this.state.resourceLoaded = false;
        this.state.resourceScroll = 0;
        this.state.selectedResourceUrl = "";
        this.resourceEntries.clear();
        if (this.state.activeTab == ModelPanelState.Tab.RESOURCE) {
            this.host.postResourceStatus(Component.translatable("gui.sparkle_morpher.resource_station.loading"), ChatFormatting.YELLOW);
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return ModelRepoClient.list(config.selectedUrl(), config);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, RESOURCE_EXECUTOR).orTimeout(Math.max(15_000L, config.timeoutMs() * 3L), TimeUnit.MILLISECONDS).whenComplete((result, error) ->
                ((Executor) Minecraft.getInstance()).execute(() -> {
                    if (generation != this.screenGeneration || requestId != this.state.resourceRequestId) {
                        return;
                    }
                    this.state.resourceLoading = false;
                    if (error != null) {
                        if (this.state.activeTab == ModelPanelState.Tab.RESOURCE) {
                            this.host.postResourceStatus(Component.translatable("gui.sparkle_morpher.resource_station.error", rootMessage(error)), ChatFormatting.RED);
                        }
                    } else {
                        this.resourceEntries.clear();
                        this.resourceEntries.addAll(result);
                        this.resourceEntries.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
                        this.state.resourceLoaded = true;
                        if (this.state.activeTab == ModelPanelState.Tab.RESOURCE) {
                            this.host.postResourceStatus(Component.translatable("gui.sparkle_morpher.resource_station.loaded", result.size()), manual ? ChatFormatting.GREEN : ChatFormatting.GRAY);
                            this.host.requestRerender();
                        }
                    }
                }));
    }

    /** 按搜索词过滤已拉取条目（原 {@code filteredResources}）。 */
    public List<ModelRepoEntry> filteredResources() {
        String query = this.state.resourceSearchText.trim().toLowerCase(Locale.ROOT);
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

    /** 当前选中的资源条目（原 {@code selectedResource}）。 */
    public ModelRepoEntry selectedResource() {
        return this.resourceEntries.stream().filter(e -> e.url().equals(this.state.selectedResourceUrl)).findFirst().orElse(null);
    }

    public int resourceEntryCount() {
        return this.resourceEntries.size();
    }

    /** 单击资源行（原 {@code clickResource}）。 */
    public void clickResource(ModelRepoEntry entry) {
        if (this.state.resourceMultiSelectMode) {
            if (!this.selectedResourceUrls.add(entry.url())) {
                this.selectedResourceUrls.remove(entry.url());
            }
            return;
        }
        this.state.selectedResourceUrl = entry.url();
    }

    public boolean isResourceSelected(ModelRepoEntry entry) {
        return entry.url().equals(this.state.selectedResourceUrl) || this.selectedResourceUrls.contains(entry.url());
    }

    /** 切换资源多选模式（原 {@code toggleResourceMultiSelect}）。 */
    public void toggleResourceMultiSelect() {
        this.state.resourceMultiSelectMode = !this.state.resourceMultiSelectMode;
        if (!this.state.resourceMultiSelectMode) {
            this.selectedResourceUrls.clear();
            this.state.selectedResourceUrl = "";
        }
    }

    // ================================================================
    // 资源站：下载队列
    // ================================================================

    /** 入队单个资源；返回是否入队成功（原 {@code enqueueResource} 的判定部分）。 */
    public boolean enqueueResource(ModelRepoEntry entry) {
        return ResourceDownloadManager.enqueue(entry, this.resourceConfig);
    }

    /** 入队选中项（无选中则整页）；返回新增数量（原 {@code enqueueSelectedResources} 的判定部分）。 */
    public int enqueueSelectedResources() {
        List<ModelRepoEntry> selected = this.resourceEntries.stream().filter(e -> this.selectedResourceUrls.contains(e.url())).toList();
        return ResourceDownloadManager.enqueueAll(selected.isEmpty() ? filteredResources() : selected, this.resourceConfig);
    }

    public boolean isQueued(ModelRepoEntry entry) {
        return ResourceDownloadManager.isQueued(entry);
    }

    /** 驱动下载队列（原 {@code tick()} 中的 {@code ResourceDownloadManager.tick()}）。 */
    public void tickDownloads() {
        ResourceDownloadManager.tick();
    }

    public void clearFinishedDownloads() {
        ResourceDownloadManager.clearFinished();
    }

    public void cancelCurrentDownload() {
        ResourceDownloadManager.cancelCurrent();
    }

    /** 队列视图：未完成在前、已完成最多 8 条（顺序与原渲染代码一致）。 */
    public List<TaskView> queueRows() {
        ResourceDownloadManager.Snapshot snapshot = ResourceDownloadManager.snapshot();
        List<TaskView> rows = new ArrayList<>();
        for (ResourceDownloadManager.TaskSnapshot task : snapshot.unfinishedTasks()) {
            rows.add(toView(task));
        }
        for (ResourceDownloadManager.TaskSnapshot task : snapshot.finishedTasks().stream().limit(8).toList()) {
            rows.add(toView(task));
        }
        return rows;
    }

    public Component queueStatus() {
        return ResourceDownloadManager.snapshot().status();
    }

    public ChatFormatting queueStatusColor() {
        return ResourceDownloadManager.snapshot().statusColor();
    }

    private static TaskView toView(ResourceDownloadManager.TaskSnapshot task) {
        return new TaskView(task.name(), task.progress(), taskStateColor(task.state()));
    }

    /** 任务状态 → 进度条颜色（原 Screen 内 {@code stateColor}，数值不变）。 */
    private static int taskStateColor(ResourceDownloadManager.TaskState state) {
        return switch (state) {
            case DONE -> 0xFF4CAF50;
            case FAILED -> 0xFFD23232;
            case CANCELLED -> 0xFF8F8F8F;
            default -> 0xFFE05252;
        };
    }

    // ================================================================
    // 导入：文件选择 / 拖放 / 本地导入 / 服务器上传
    // ================================================================

    /** 标记当前代际失效（原 {@code removed()} 中的 {@code screenGeneration++}）。 */
    public void invalidateGeneration() {
        this.screenGeneration++;
    }

    public int generation() {
        return this.screenGeneration;
    }

    /** 取消系统文件选择框（原 {@code removed()} 中的 cancelPicking）。 */
    public void cancelPicking() {
        ModelImportFilePicker.cancelPicking();
    }

    /** 打开 .ysm 文件选择框；返回错误（无错误返回 null）。 */
    public Component pickYsmFile() {
        return ModelImportFilePicker.pickYsmFile();
    }

    /** 轮询已完成的选择 + 错误，并推进队列（原 {@code pollImports}）。 */
    public void pollImports() {
        ModelImportFilePicker.PickedFile picked;
        while ((picked = ModelImportFilePicker.pollCompleted()) != null) {
            this.pendingImports.add(picked);
        }
        Component pickerError = ModelImportFilePicker.consumeLastError();
        if (!pickerError.getString().isEmpty()) {
            this.host.postStatus(pickerError, ChatFormatting.RED);
        }
        startNextImportIfIdle();
    }

    /** 处理拖放的单个路径（原 {@code enqueueImportPath}）。 */
    public void enqueueImportPath(Path path) {
        try {
            if (Files.isDirectory(path)) {
                this.pendingImports.add(ModelImportFilePicker.packDirectory(path));
            } else if (ModelImportFilePicker.isImportFileName(path.getFileName().toString())) {
                this.pendingImports.add(new ModelImportFilePicker.PickedFile(path.getFileName().toString(), Files.readAllBytes(path)));
            }
        } catch (IOException e) {
            this.host.postStatus(Component.translatable("gui.sparkle_morpher.import.error.read_file", e.getMessage()), ChatFormatting.RED);
        }
    }

    /** 空闲时推进下一个导入（原 {@code startNextImportIfIdle}）。 */
    public void startNextImportIfIdle() {
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
            this.host.postStatus(Component.translatable("gui.sparkle_morpher.import.error.model_id_from_filename", fileName), ChatFormatting.RED);
            return;
        }
        this.localImportInProgress = true;
        this.host.postStatus(Component.translatable("gui.sparkle_morpher.import.state.local_importing", modelId), ChatFormatting.YELLOW);
        ClientModelManager.importLocalModel(modelId, fileName, file.data(), error -> {
            this.localImportInProgress = false;
            if (error != null) {
                this.host.postStatus(error, ChatFormatting.RED);
                return;
            }
            if (ClientModelManager.isGltfFileName(fileName)) {
                this.host.postStatus(Component.translatable("gui.sparkle_morpher.import.state.local_imported_as", modelId), ChatFormatting.GREEN);
                return;
            }
            Component uploadError = ModelUploadSession.start(modelId, fileName, file.data());
            if (uploadError != null) {
                this.host.postStatus(Component.translatable("gui.sparkle_morpher.import.state.local_imported_as", modelId), ChatFormatting.GREEN);
            }
        });
    }

    public boolean localImportInProgress() {
        return this.localImportInProgress;
    }

    // ---- 上传会话只读视图（Screen 不再直接引用 ModelUploadSession） ----

    public boolean uploadSessionActive() {
        return ModelUploadSession.getInstance() != null;
    }

    public Component uploadSessionMessage() {
        ModelUploadSession session = ModelUploadSession.getInstance();
        return session == null ? Component.empty() : session.getMessage();
    }

    public float uploadSessionProgress() {
        ModelUploadSession session = ModelUploadSession.getInstance();
        return session == null ? 0f : session.getProgress();
    }

    public boolean uploadSessionFailed() {
        ModelUploadSession session = ModelUploadSession.getInstance();
        return session != null && session.getState() == ModelUploadSession.State.FAILED;
    }

    public String uploadSessionBytesText() {
        ModelUploadSession session = ModelUploadSession.getInstance();
        if (session == null) {
            return "";
        }
        return ModelUploadSession.formatBytes(session.getSentBytes()) + " / " + ModelUploadSession.formatBytes(session.getTotalBytes());
    }

    /** 资源条目大小文案（原 {@code resourceDetail} 中的 formatBytes 调用）。 */
    public String formatBytes(long bytes) {
        return ModelUploadSession.formatBytes((int) Math.min(Integer.MAX_VALUE, bytes));
    }

    // ================================================================
    // 模型目录：目录操作 / 重载 / 选择记忆
    // ================================================================

    public Component deleteModels(Collection<String> modelIds) {
        return ModelPanelFileActions.deleteModels(modelIds);
    }

    public Component moveModels(Collection<String> modelIds, String category) {
        return ModelPanelFileActions.moveModels(modelIds, category);
    }

    public Component createCategory(String category) {
        return ModelPanelFileActions.createCategory(category);
    }

    public Component deleteCategory(String category, boolean deleteModels) {
        return ModelPanelFileActions.deleteCategory(category, deleteModels);
    }

    public List<String> listCategories() {
        return ModelPanelFileActions.listCategories();
    }

    public void reloadLocalModels(Consumer<Component> callback) {
        ClientModelManager.reloadLocalModels(callback);
    }

    /** 打开本地模型目录，并按原行为回写 GRAY/RED 状态栏文案。 */
    public void openModelFolder() {
        try {
            Files.createDirectories(ServerModelManager.CUSTOM);
            Util.getPlatform().openFile(ServerModelManager.CUSTOM.toFile());
            this.host.postStatus(Component.literal(ServerModelManager.CUSTOM.toString()), ChatFormatting.GRAY);
        } catch (IOException e) {
            this.host.postStatus(Component.translatable("gui.sparkle_morpher.import.error.open_folder", e.getMessage()), ChatFormatting.RED);
        }
    }

    public boolean isAllowUpload() {
        return ClientModelManager.isAllowUpload();
    }

    public boolean isOysmServer() {
        return ClientModelManager.isOysmServer();
    }

    // ================================================================
    // 模型目录：只读查询（Screen 侧列表/预览用）
    // ================================================================

    public Set<String> modelPackPaths() {
        return ClientModelManager.getModelPackMap().keySet();
    }

    /** 模型包条目（含封面贴图）；不存在返回 null。 */
    public com.micaftic.morpher.resource.models.ModelPackData modelPack(String modelId) {
        return ClientModelManager.getModelPackMap().get(modelId);
    }

    public Set<String> availableModelIds() {
        return ClientModelManager.getAvailableModelIds();
    }

    public int availableModelCount() {
        return ClientModelManager.getAvailableModelIds().size();
    }

    public Map<String, ModelAssembly> modelAssemblyMap() {
        return ClientModelManager.getModelAssemblyMap();
    }

    public Optional<ModelAssembly> lookupAssembly(String modelId) {
        return ClientModelManager.getModelContext(modelId);
    }

    public ModelAssembly assemblyOrNull(String modelId) {
        return ClientModelManager.getModelContext(modelId).orElse(null);
    }

    public boolean isAuthModel(String modelId) {
        return ClientModelManager.isAuthModel(modelId);
    }

    public String lazyModelDisplayName(String modelId) {
        return ClientModelManager.getLazyModelDisplayName(modelId);
    }

    public boolean isLocalOnlyModel(String modelId) {
        return ClientModelManager.isLocalOnlyModel(modelId);
    }

    public void markModelUsed(String modelId) {
        ClientModelManager.markModelUsed(modelId);
    }

    public boolean isGpuCacheTrimmed(String modelId) {
        return ClientModelManager.isGpuCacheTrimmed(modelId);
    }

    public void rememberSelectedModel(String modelId, String textureId) {
        ClientModelManager.rememberSelectedModel(modelId, textureId);
    }

    // ================================================================
    // 应用选择：写玩家选择 / 发包切换模型
    // ================================================================

    /**
     * 应用模型 + 贴图（原 {@code applyModelAndTexture}），结果由 Screen 决定文案。
     *
     * @param selectionTarget     外部选择回调（女仆 / NPC 面板），可为 null
     * @param rememberPlayerSelection 是否写入玩家自己的模型选择
     */
    public ApplyResult applyModel(String modelId, String textureId, BiConsumer<String, String> selectionTarget, boolean rememberPlayerSelection) {
        if (selectionTarget != null) {
            if (rememberPlayerSelection) {
                ClientModelManager.rememberSelectedModel(modelId, textureId);
            }
            selectionTarget.accept(modelId, textureId);
            return ApplyResult.APPLIED_TO_TARGET;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return ApplyResult.NO_PLAYER;
        }
        Optional<PlayerCapability> capability = PlayerCapability.get(player);
        if (capability.isEmpty()) {
            return ApplyResult.NO_CAPABILITY;
        }
        capability.ifPresent(cap -> {
            if (rememberPlayerSelection) {
                ClientModelManager.rememberSelectedModel(modelId, textureId);
            }
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
        });
        return ApplyResult.APPLIED_TO_PLAYER;
    }

    /** 切换收藏（原 {@code toggleSelectedStar}）；无法操作时返回 false。 */
    public boolean toggleStar(String modelId) {
        if (modelId == null || modelId.isBlank() || Minecraft.getInstance().player == null) {
            return false;
        }
        return StarModelsCapability.get(Minecraft.getInstance().player).map(cap -> {
            if (cap.containsModel(modelId)) {
                cap.removeModel(modelId);
                LocalStarModelsStore.remove(modelId);
                NetworkHandler.sendToServer(C2SSetStarModelPacket.remove(modelId));
            } else {
                cap.addModel(modelId);
                LocalStarModelsStore.add(modelId);
                NetworkHandler.sendToServer(C2SSetStarModelPacket.add(modelId));
            }
            return true;
        }).orElse(false);
    }

    // ================================================================
    // 贴图资源：GPU 上传定位（原 Screen 内直接调用 UploadManager）
    // ================================================================

    /** 取出贴图可用的资源位置；无法定位时返回 null。 */
    public ResourceLocation textureLocation(AbstractTexture texture, int fallbackWidth) {
        IResourceLocatable locatable = UploadManager.getOrCreateLocatable(texture, true);
        if (locatable == null && texture instanceof OuterFileTexture) {
            locatable = UploadManager.getOrCreateLocatableWithSize(texture, true, Math.max(64, fallbackWidth));
        }
        return locatable == null ? null : locatable.getResourceLocationOrNull();
    }

    // ================================================================
    // 配置读写：设置页里的「业务性」变更
    // ================================================================

    public boolean privacyModeConfigured() {
        return PrivacyMode.isConfigured();
    }

    public void togglePrivacyMode() {
        boolean enabled = !PrivacyMode.isConfigured();
        GeneralConfig.PRIVACY_MODE.set(enabled);
        GeneralConfig.PRIVACY_MODE.save();
        PrivacyMode.onConfigChanged(enabled);
    }

    public boolean javaVectorRendererEnabled() {
        return safeBool(GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER);
    }

    public void toggleJavaVectorRenderer() {
        boolean next = !safeBool(GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER);
        GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER.set(next);
        GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER.save();
        VectorApiCapability.warnIfRequested(next);
    }

    public GeneralConfig.NativeSimdPolicy nativeSimdPolicy() {
        return GeneralConfig.safeGet(GeneralConfig.NATIVE_SIMD_POLICY, GeneralConfig.NativeSimdPolicy.AGGRESSIVE);
    }

    public void cycleNativeSimdPolicy() {
        GeneralConfig.NativeSimdPolicy current = nativeSimdPolicy();
        GeneralConfig.NativeSimdPolicy next = switch (current) {
            case OFF -> GeneralConfig.NativeSimdPolicy.SAFE;
            case SAFE -> GeneralConfig.NativeSimdPolicy.AGGRESSIVE;
            case AGGRESSIVE -> GeneralConfig.NativeSimdPolicy.OFF;
        };
        GeneralConfig.NATIVE_SIMD_POLICY.set(next);
        GeneralConfig.NATIVE_SIMD_POLICY.save();
    }

    public LoadingStateConfig.Position loadingPosition() {
        return GeneralConfig.safeGet(LoadingStateConfig.LOADING_STATE_POSITION, LoadingStateConfig.Position.TOP_CENTER);
    }

    /** 上一个加载界面位置（原 {@code loadingPositionRow} 的 decrement 分支）。 */
    public void stepLoadingPosition(boolean forward) {
        LoadingStateConfig.Position[] values = LoadingStateConfig.Position.values();
        LoadingStateConfig.Position selected = loadingPosition();
        LoadingStateConfig.Position next = forward
                ? values[(selected.ordinal() + 1) % values.length]
                : values[(selected.ordinal() - 1 + values.length) % values.length];
        LoadingStateConfig.LOADING_STATE_POSITION.set(next);
        LoadingStateConfig.LOADING_STATE_POSITION.save();
    }

    public boolean gpuRendererSelected() {
        boolean gpu = safeBool(GeneralConfig.USE_GPU_RENDERER);
        boolean compatibility = safeBool(GeneralConfig.USE_COMPATIBILITY_RENDERER);
        if (gpu == compatibility) {
            setRendererMode(gpu);
            compatibility = !gpu;
        }
        return gpu && !compatibility;
    }

    public void setRendererMode(boolean useGpuRenderer) {
        GeneralConfig.USE_COMPATIBILITY_RENDERER.set(!useGpuRenderer);
        GeneralConfig.USE_COMPATIBILITY_RENDERER.save();
        GeneralConfig.USE_GPU_RENDERER.set(useGpuRenderer);
        GeneralConfig.USE_GPU_RENDERER.save();
    }

    // ================================================================
    // 内部工具（从 Screen 搬运，逻辑不变）
    // ================================================================

    private static boolean safeBool(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (Exception e) {
            return false;
        }
    }

    /** 去掉导入文件名末尾的模型扩展名（原 Screen 内 {@code stripImportExtension}）。 */
    static String stripImportExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel", ".gltf", ".glb"}) {
            if (lower.endsWith(extension)) {
                return fileName.substring(0, fileName.length() - extension.length());
            }
        }
        return fileName;
    }

    /** 取异常根因消息（原 Screen 内 {@code rootMessage}）。 */
    static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
