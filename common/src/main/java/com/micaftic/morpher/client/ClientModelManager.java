package com.micaftic.morpher.client;

import com.micaftic.morpher.RuntimeAccelerationLoader;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.animation.BedrockAnimationMapping;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.gui.IGuiWidget;
import com.micaftic.morpher.client.gui.GuiWidgetRegistry;
import com.micaftic.morpher.client.entity.EntityRenderCache;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.ModelAssemblyFactory;
import com.micaftic.morpher.core.api.network.state.LegacySpmHandshakeState;
import com.micaftic.morpher.core.gpu.GpuRenderPath;
import com.micaftic.morpher.core.model.ModelRef;
import com.micaftic.morpher.core.model.ModelSourceType;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog;
import com.micaftic.morpher.core.model.selection.EntityModelResolver;
import com.micaftic.morpher.core.model.selection.ModelRevisionGuard;
import com.micaftic.morpher.core.model.selection.ModelSelectionState;
import com.micaftic.morpher.core.model.ModelRetention;
import com.micaftic.morpher.core.storage.LocalModelImportStore;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.client.model.ProjectileModelBundle;
import com.micaftic.morpher.client.model.VehicleModelBundle;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.compat.ClientRenderCompatibilityRegistry;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SModelSyncPayload;
import com.micaftic.morpher.network.message.C2SRequestSwitchModelPacket;
import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.resource.YSMClientMapper;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.resource.models.ModelPackData;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.DigestUtil;
import com.micaftic.morpher.util.FileTypeUtil;
import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.micaftic.morpher.util.LocalModelSelectionStore;
import com.micaftic.morpher.util.YSMThreadPool;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.core.security.YSMClientCache;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.core.legacy.YesModelUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Environment(EnvType.CLIENT)
public class ClientModelManager {
    static final AtomicInteger pendingModelsCount = new AtomicInteger(0);
    static final AtomicBoolean syncCompletionScheduled = new AtomicBoolean(false);
    static volatile boolean syncManifestProcessed = false;

    // ---- 同步超时看门狗 ----
    // 记录最近一次同步活动（开始同步 / 收到分片 / 完成一个模型）的时间戳。
    // 当同步过程中长时间没有任何进度时（例如服务端因缓存文件被清理而跳过了
    // 某个被请求模型的分片，或客户端在重连竞态下漏掉了 decrement），
    // pendingModelsCount 会永远归不了零、onSyncComplete 永不触发，导致加载弹窗
    // 卡死。看门狗在超时后强制结束同步，避免 UI 永久停在某一进度。
    private static volatile long lastSyncActivityMillis = 0L;
    private static final long SYNC_WATCHDOG_TIMEOUT_MILLIS = 20000L;

    private static final int MODEL_PARSE_THREAD_COUNT = 2;
    private static final int MODEL_PARSE_QUEUE_CAPACITY = 8;
    private static final int MODEL_PARSE_MEMORY_BUDGET_MIB = 256;
    private static final AtomicInteger MODEL_PARSE_THREAD_IDS = new AtomicInteger(1);
    private static final Semaphore MODEL_PARSE_SLOTS = new Semaphore(MODEL_PARSE_THREAD_COUNT + MODEL_PARSE_QUEUE_CAPACITY, true);
    private static final Semaphore MODEL_PARSE_MEMORY = new Semaphore(MODEL_PARSE_MEMORY_BUDGET_MIB, true);
    static final AtomicInteger MODEL_TASK_GENERATION = new AtomicInteger(0);
    private static final ThreadPoolExecutor modelTaskDispatcher = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(256), r -> {
        Thread t = new Thread(r, "SM-Model-Dispatch");
        t.setDaemon(true);
        return t;
    });
    private static final ThreadPoolExecutor modelPhraseExecutor = new ThreadPoolExecutor(MODEL_PARSE_THREAD_COUNT, MODEL_PARSE_THREAD_COUNT, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MODEL_PARSE_QUEUE_CAPACITY),
            r -> {
                Thread t = new Thread(r, "SM-Model-Parse-" + MODEL_PARSE_THREAD_IDS.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
    );

    static void submitModelTask(Runnable task) {
        int generation = MODEL_TASK_GENERATION.get();
        modelTaskDispatcher.execute(() -> {
            boolean acquired = false;
            try {
                MODEL_PARSE_SLOTS.acquire();
                acquired = true;
                if (generation != MODEL_TASK_GENERATION.get()) {
                    MODEL_PARSE_SLOTS.release();
                    acquired = false;
                    return;
                }
                modelPhraseExecutor.execute(() -> {
                    try {
                        if (generation == MODEL_TASK_GENERATION.get()) {
                            task.run();
                        }
                    } finally {
                        MODEL_PARSE_SLOTS.release();
                    }
                });
                acquired = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RejectedExecutionException e) {
                YesSteveModel.LOGGER.warn("[SM] Model parser rejected a queued task", e);
            } finally {
                if (acquired) {
                    MODEL_PARSE_SLOTS.release();
                }
            }
        });
    }

    private static final long MAX_LOCAL_MODEL_FILE_BYTES = LocalModelCatalog.DEFAULT_MAX_FILE_BYTES;

    static final Map<UUID, ServerModelContext> serverModels = new ConcurrentHashMap<>();

    static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    private static volatile ModelAssembly localModelContext;
    private static volatile Runnable pendingModelCallback;
    private static IResourceLocatable defaultTexture;
    private static volatile boolean defaultModelLoadAttempted;

    static volatile Map<String, ModelAssembly> modelAssemblyMap = Object2ReferenceMaps.emptyMap();
    private static volatile Map<String, ModelPackData> modelPackMap = new Object2ReferenceOpenHashMap<>();
    private static final Set<String> localOnlyModelIds = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Path> localModelSourcePaths = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> modelLastUsedAt = new ConcurrentHashMap<>();
    private static final Set<String> gpuCacheTrimmedModels = ConcurrentHashMap.newKeySet();
    static final Map<String, File> cachedModelFiles = new ConcurrentHashMap<>();
    private static final Set<String> cpuReloadInFlight = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, LocalModelCatalog.Entry> lazyModelSources = new ConcurrentHashMap<>();
    private static final Set<ModelAssembly> deferredAssemblyReleases = ConcurrentHashMap.newKeySet();
    private static volatile Boolean lastLazyModelLoading;
    private static volatile long lastModelTrimMillis;

    // ---- 模型处理失败日志去重 ----
    // 同一模型短时间反复失败（如损坏模型被反复重载）时只打印首条完整堆栈，
    // 之后每 100 次输出一次计数摘要，避免日志刷屏；模型成功处理后清除计数。
    private static final ConcurrentHashMap<String, long[]> MODEL_PROCESS_FAILURES = new ConcurrentHashMap<>();
    /**
     * R6：实体模型决策器（候选优先级 + revision 竞态防护）。
     * 选择变化（rememberSelectedModel）推进 revision，作废在途异步结果；
     * 异步模型加载完成时以 shouldApply(generation) 校验再 apply。
     */
    public static final EntityModelResolver MODEL_RESOLVER = new EntityModelResolver(new ModelRevisionGuard());
    /**
     * R7.3：模型选择状态（selected / localOnly 双轨），从本类 4 个 volatile 字段抽取。
     */
    public static final ModelSelectionState MODEL_SELECTION = new ModelSelectionState();
    /**
     * R7.1：本地模型导入持久化（原子写 + 路径沙箱 + sibling 清理），
     * 从 ClientModelManager 抽取为独立可测组件。
     */
    private static final LocalModelImportStore LOCAL_IMPORT_STORE = new LocalModelImportStore(ServerModelManager.CUSTOM);
    private static final LocalModelCatalog LOCAL_MODEL_CATALOG = new LocalModelCatalog();
    private static final long MODEL_PROCESS_FAILURE_SUPPRESS_MILLIS = 5L * 60L * 1000L;

    private static final ConcurrentLinkedQueue<Pair<ModelAssembly, String>> pendingModelQueue = new ConcurrentLinkedQueue<>();
    private static final SyncStatus syncState = new SyncStatus();

    public enum SyncState {
        WAITING, LOADING, IDLE, PREPARING, SYNCING
    }

    public static class ServerModelContext {
        public final UUID uuid;
        public final long hash1;
        public final long hash2;
        public final String modelId;
        public final String modelKey;
        public final boolean isAuth;
        public final int isCustomSkinModel;
        public final int version;
        /** R5.1 试点：服务器下发模型的来源语义（LEGACY_SERVER）；modelId 为空时 null。 */
        public final ModelRef ref;

        public byte[] fileBuffer;
        public int totalSize;
        public int bytesReceived;

        public ServerModelContext(long hash1, long hash2, String modelId, boolean isAuth, int isCustomSkinModel, int version) {
            this.uuid = new UUID(hash1, hash2);
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.modelId = modelId;
            this.modelKey = LocalModelCatalog.canonicalKey(modelId);
            this.isAuth = isAuth;
            this.isCustomSkinModel = isCustomSkinModel;
            this.version = version;
            this.ref = (modelId != null && !modelId.isEmpty())
                    ? ModelRef.of(ModelSourceType.LEGACY_SERVER, modelId)
                    : null;
        }
    }

    public static void loadDefaultModel() {
        if (localModelContext != null || defaultModelLoadAttempted) {
            return;
        }
        defaultModelLoadAttempted = true;
        YesSteveModel.LOGGER.info("[SM] Loading builtin default model...");
        try {
            String resourcePath = "/assets/sparkle_morpher/builtin/default";
            // 生产环境（jar / NeoForge 模块类加载器）下对"目录"做 getResource 经常解析不到
            // （目录不是可枚举的 classpath 条目），因此改为探测目录内的真实文件 ysm.json，
            // 再从它的 URL 推导出目录路径。
            String probeFile = "/ysm.json";
            URL probeUrl = YesSteveModel.class.getResource(resourcePath + probeFile);
            if (probeUrl == null) {
                YesSteveModel.LOGGER.warn("[SM] Builtin default model not found in classpath: " + resourcePath
                        + " (client will rely on server-provided models)");
                return;
            }
            URI uri = probeUrl.toURI();
            if ("union".equals(uri.getScheme())) {
                // NeoForge 模块类加载器（HMCL 外部启动，jar 放 mods/）返回 union: URL：
                // union:/path/to/sparkle-morpher-*.jar%23<idx>!/assets/.../ysm.json
                // 将其转换为标准 jar:file: URL，后续统一走 jar 分支处理。
                String urlStr = probeUrl.toString();
                String inner = urlStr.substring("union:".length());
                int bang = inner.indexOf('!');
                if (bang <= 0) {
                    YesSteveModel.LOGGER.warn("[SM] Malformed union URL: " + probeUrl
                            + " (client will rely on server-provided models)");
                    return;
                }
                String filePart = inner.substring(0, bang).replaceAll("%23\\d+$", "");
                uri = URI.create("jar:file:" + filePart + inner.substring(bang));
            }
            Path defaultPath;
            FileSystem jarFs = null;
            if ("jar".equals(uri.getScheme())) {
                // jar:file:/.../sparkle-morpher-*.jar!/assets/.../default/ysm.json -> 去掉文件名得到目录 URI
                URI dirUri = URI.create(uri.toString().substring(0, uri.toString().length() - probeFile.length()));
                try {
                    jarFs = FileSystems.getFileSystem(dirUri);
                } catch (FileSystemNotFoundException e) {
                    jarFs = FileSystems.newFileSystem(dirUri, Collections.emptyMap());
                }
                defaultPath = jarFs.getPath(resourcePath);
            } else if ("file".equals(uri.getScheme())) {
                defaultPath = Paths.get(uri).getParent();
            } else {
                YesSteveModel.LOGGER.warn("[SM] Unsupported builtin default model resource URL scheme: " + probeUrl
                        + " (client will rely on server-provided models)");
                return;
            }

            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(defaultPath)) {
                RawYsmModel rawModel = deserializer.deserialize();

                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, "default");

                onModelDataReceived(parsedBundle, "default", true, false);
                YesSteveModel.LOGGER.info("[SM] Successfully pushed Default Model to render queue.");
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to dispatch Default Model", e);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to load builtin default model", e);
        }
    }


    static void parseAndLoadModel(byte[] decompressed, String modelId, boolean isAuth) {
        modelId = LocalModelCatalog.canonicalKey(modelId);
        int memoryPermits = Math.max(1, Math.min(MODEL_PARSE_MEMORY_BUDGET_MIB,
                (decompressed.length + 1024 * 1024 - 1) / (1024 * 1024)));
        try {
            MODEL_PARSE_MEMORY.acquire(memoryPermits);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
//            if (true) return;
            // IR

            ModelMemoryProfiler.logBytes("binary-parse-start", modelId, decompressed);
            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decompressed, 32)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                YSMByteBuf reader = deserializer.getReader();

                // 读取版本号
                rawModel.footer.version = reader.readVarInt();// 65535 或 32

                rawModel.footer.unkInt1 = reader.readVarInt(); // 待分析
                if (rawModel.footer.unkInt1 != 0) {
                    rawModel.footer.rand = reader.readString();
                }

                rawModel.footer.time = reader.readVarLong();

                if (rawModel.footer.unkInt1 != 0) {
                    rawModel.footer.extra = reader.readString();
                    rawModel.footer.unkInt2 = reader.readVarInt();
                }

                // 组装到客户端模型
                ModelMemoryProfiler.log("client-map-start", modelId);
                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
                ModelMemoryProfiler.log("client-map-finished", modelId);
                onModelDataReceived(parsedBundle, modelId, false, isAuth);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to parse and load model: " + modelId, e);
        } finally {
            MODEL_PARSE_MEMORY.release(memoryPermits);
        }
    }

    private static OrderedStringMap<String, OuterFileTexture> toOrderedTextureMap(Map<String, OuterFileTexture> textures) {
        if (textures == null || textures.isEmpty()) {
            return new OrderedStringMap<>(new String[0], new OuterFileTexture[0]);
        }
        return new OrderedStringMap<>(
                textures.keySet().toArray(new String[0]),
                textures.values().toArray(new OuterFileTexture[0])
        );
    }

    static void resetClientState() {
        MODEL_TASK_GENERATION.incrementAndGet();
        modelTaskDispatcher.getQueue().clear();
        LegacyModelSyncClient.resetSyncState();

        int discardedModelTasks = modelPhraseExecutor.getQueue().size();
        modelPhraseExecutor.getQueue().clear();
        MODEL_PARSE_SLOTS.release(discardedModelTasks);

        pendingModelsCount.set(0);
        syncManifestProcessed = false;
        syncCompletionScheduled.set(false);
        LegacyModelCacheClient.clearCachedModelHashes();

        serverModels.clear();
        cachedModelFiles.clear();
        cpuReloadInFlight.clear();
        lazyModelSources.entrySet().removeIf(entry -> entry.getValue().remote);

        // 断线/换服：释放过期服务端模型装配（含纹理源 byte[] 与 GPU/native 资源），
        // 否则它们会被 modelAssemblyMap 强引用跨会话累积（主要内存泄漏源）。
        // 保留 localModelContext（默认模型）与本地导入模型：reloadLocalModels 会重建后者。
        // R7.3：保留/释放判定集中到 ModelRetention（纯函数，单测覆盖）。
        if (!modelAssemblyMap.isEmpty()) {
            ModelRetention.Split<ModelAssembly> retention = ModelRetention.partition(
                    new ArrayList<>(modelAssemblyMap.entrySet()), localOnlyModelIds::contains, localModelContext);
            Object2ReferenceOpenHashMap<String, ModelAssembly> survivors = new Object2ReferenceOpenHashMap<>();
            for (Map.Entry<String, ModelAssembly> entry : retention.survivors()) {
                survivors.put(entry.getKey(), entry.getValue());
            }
            modelAssemblyMap = survivors;
            for (ModelAssembly asm : retention.toRelease()) {
                releaseModelAssembly(asm);
            }
        }

        Map<String, ModelPackData> oldPreviews = modelPackMap;
        if (oldPreviews != null && !oldPreviews.isEmpty()) {
            for (ModelPackData preview : oldPreviews.values()) {
                if (preview.getTexture() != null) {
                    ResourceLocation loc = FileTypeUtil.getPackIconLocation(preview.getPath());
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().getTextureManager().release(loc);
                    });
                }
            }
        }

        modelPackMap = new Object2ReferenceOpenHashMap<>();
        if (localModelContext != null) {
            pendingModelCallback = null;
        } else if (pendingModelCallback == null) {
            defaultModelLoadAttempted = false;
        }
        pendingModelQueue.clear();
        localModelSourcePaths.clear();
        modelLastUsedAt.clear();
        gpuCacheTrimmedModels.clear();
        lastModelTrimMillis = 0L;

        forEachGuiWidget(l -> {
            try {
                l.onSyncBegin();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public static SyncStatus getSyncStatus() {
        return syncState;
    }

    public static Map<String, ModelAssembly> getModelAssemblyMap() {
        return modelAssemblyMap;
    }

    public static Map<String, ModelPackData> getModelPackMap() {
        return modelPackMap;
    }

    public static Optional<ModelAssembly> getModelContext(String str) {
        String modelKey = LocalModelCatalog.canonicalKey(str);
        ModelAssembly assembly = modelAssemblyMap.get(modelKey);
        if (assembly instanceof LazyModelAssembly) {
            scheduleCachedModelReload(modelKey);
            return Optional.empty();
        }
        if (assembly != null) {
            touchModel(modelKey);
        }
        if ((assembly == null && lazyModelSources.containsKey(modelKey))
                || (assembly != null && !assembly.isRuntimeResident())) {
            scheduleCachedModelReload(modelKey);
            return Optional.empty();
       }
       return Optional.ofNullable(assembly);
    }

    public static boolean isModelLoadPending(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        if (modelKey == null) {
            return false;
        }
        if (cpuReloadInFlight.contains(modelKey)) {
            return true;
        }
        ModelAssembly assembly = modelAssemblyMap.get(modelKey);
        return lazyModelSources.containsKey(modelKey)
                && (assembly == null || assembly instanceof LazyModelAssembly || !assembly.isRuntimeResident());
    }

    private static void scheduleCachedModelReload(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        if (modelKey == null || !cpuReloadInFlight.add(modelKey)) return;
        LocalModelCatalog.Entry source = lazyModelSources.get(modelKey);
        if (source == null) {
            cpuReloadInFlight.remove(modelKey);
            return;
        }
        submitModelTask(() -> {
            try {
                if (lazyModelSources.get(modelKey) != source) return;
                if (source.remote) {
                    if (source.cacheKey == null) return;
                    byte[] fileBytes = Files.readAllBytes(source.path);
                    byte[] decompressed = YsmCrypt.readInPlace(fileBytes, source.cacheKey);
                    if (lazyModelSources.get(modelKey) != source) return;
                    parseAndLoadModel(decompressed, modelKey, source.auth);
                } else {
                    loadLocalModelSource(modelKey, source);
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to reload resident model: {}", modelKey, e);
            } finally {
                cpuReloadInFlight.remove(modelKey);
            }
        });
    }

    public static Set<String> getAvailableModelIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(modelAssemblyMap.keySet());
        ids.addAll(lazyModelSources.keySet());
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Display name for a model that exists only in the lazy catalog (not yet fully loaded).
     * Prefer sniffed/cached metadata name, then {@link ServerModelInfo} metadata, else {@code null}.
     */
    @Nullable
    public static String getLazyModelDisplayName(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        if (modelKey == null) return null;
        LocalModelCatalog.Entry source = lazyModelSources.get(modelKey);
        if (source == null) return null;
        if (StringUtils.isNotBlank(source.displayName)) {
            return source.displayName;
        }
        String fromInfo = LocalModelCatalog.displayNameFromInfo(source.modelInfo);
        if (StringUtils.isNotBlank(fromInfo)) {
            source.displayName = fromInfo;
            return fromInfo;
        }
        return null;
    }


    static void registerRemoteLazySource(String modelId, Path path, byte[] key, boolean isAuth) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        if (modelKey == null || path == null || key == null) return;
        // 服务器已公布同名模型，本次会话中它不再是“仅本地”模型。
        localOnlyModelIds.remove(modelKey);
        localModelSourcePaths.remove(modelKey);
        LocalModelCatalog.Entry previous = lazyModelSources.get(modelKey);
        ServerModelInfo modelInfo = previous == null ? null : previous.modelInfo;
        String prevName = previous == null ? null : previous.displayName;
        if (prevName == null) {
            prevName = LocalModelCatalog.displayNameFromInfo(modelInfo);
        }
        lazyModelSources.put(modelKey, new LocalModelCatalog.Entry(path, key, true, isAuth, 0L, modelInfo, prevName));
    }

    public static boolean isAuthModel(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        if (modelKey == null) return false;
        LocalModelCatalog.Entry source = lazyModelSources.get(modelKey);
        return (source != null && source.auth) || serverModels.values().stream()
                .anyMatch(value -> modelKey.equals(value.modelKey) && value.isAuth);
    }

    public static boolean canUploadToServer() {
        return NetworkHandler.isClientConnected()
                && LegacySpmHandshakeState.isOysmServer()
                && LegacySpmHandshakeState.isAllowUpload();
    }

    public static boolean isLocalOnlyModel(String modelId) {
        return modelId != null && localOnlyModelIds.contains(LocalModelCatalog.canonicalKey(modelId));
    }

    public static Optional<Path> getLocalModelSourcePath(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        Path path = localModelSourcePaths.get(LocalModelCatalog.canonicalKey(modelId));
        if (path == null || !Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    public static Map<String, Path> snapshotLocalCustomSources() {
        LinkedHashMap<String, Path> out = new LinkedHashMap<>();
        for (String id : localOnlyModelIds) {
            Path p = localModelSourcePaths.get(id);
            if (p != null && Files.exists(p)) {
                out.put(id, p);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    public static boolean isSelectedLocalOnlyModel(String modelId) {
        return modelId != null && sameRuntimeModelId(modelId, MODEL_SELECTION.selectedModelId()) && isLocalOnlyModel(modelId);
    }

    public static void rememberSelectedModel(String modelId, String textureId) {
        // R6：选择变化推进 revision——作废所有在途异步模型结果（竞态防护）
        MODEL_RESOLVER.request();
        // R7.3：双轨选择状态（selected / localOnly）集中到 ModelSelectionState
        MODEL_SELECTION.remember(modelId, textureId, isLocalOnlyModel(modelId),
                sameRuntimeModelId(modelId, MODEL_SELECTION.localOnlyModelId()));
        // 持久化模型选择到本地文件，以便在无模组服务器上自动恢复
        LocalModelSelectionStore.save(modelId, textureId);
    }

    /**
     * 恢复客户端本地持久化的模型选择。
     * <p>
     * 优先使用内存中的 selectedModelId/selectedTextureId，
     * 如果内存中无有效选择（非仅本地模型场景），则从 LocalModelSelectionStore 文件读取。
     * <p>
     * 只恢复在本地 modelAssemblyMap 中仍然可用的模型。
     * 在无模组服务器上，这意味着仅本地导入的模型可以被恢复；
     * 在 BungeeCord 子服务器切换场景下（没有触发 resetSync），服务器同步的模型也可能仍在缓存中。
     */
    @Environment(EnvType.CLIENT)
    public static void restorePersistedModelSelection() {
        // 1. 先尝试内存中的选择
        String modelId = MODEL_SELECTION.selectedModelId();
        String textureId = MODEL_SELECTION.selectedTextureId();

        // 2. 如果内存中的选择不是仅本地模型（在断开YSM服务器后可能已不可用），尝试从文件恢复
        if (modelId == null || (!isLocalOnlyModel(modelId) && !containsRuntimeModel(modelId))) {
            Pair<String, String> persisted = LocalModelSelectionStore.load();
            if (persisted != null) {
                modelId = persisted.getLeft();
                textureId = persisted.getRight();
            }
        }

        // 3. 没有有效选择则跳过
        if (modelId == null || modelId.equals("default") || modelId.isBlank()) {
            return;
        }

        // 4. 模型必须仍在本地缓存中可用
        if (!isLocalOnlyModel(modelId) && !containsRuntimeModel(modelId)) {
            return;
        }

        // 目录已经确认该模型可用，先恢复内存选择状态，避免同步完成时读取到 null。
        rememberSelectedModel(modelId, textureId);

        // 5. 拷贝为 final 变量供 lambda 使用
        final String finalModelId = modelId;
        final String finalTextureId = textureId;

        // 6. 在渲染线程上应用
        Minecraft.getInstance().execute(() -> {
            // 再次检查，防止在 execute 延迟期间选择已改变
            if (!sameRuntimeModelId(finalModelId, MODEL_SELECTION.selectedModelId()) && !isLocalOnlyModel(finalModelId) && !containsRuntimeModel(finalModelId)) {
                // 内存中的选择已经变了，且持久化的模型也不再可用，放弃恢复
                return;
            }
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            PlayerCapability.get(player).ifPresent(cap -> {
                if (!finalModelId.equals(cap.getModelId())) {
                    cap.initModelWithTexture(finalModelId, finalTextureId);
                    rememberSelectedModel(finalModelId, finalTextureId);
                }
            });
        });
    }

    /**
     * 在无模组服务器上，每 tick 检查本地玩家是否需要恢复模型选择。
     * <p>
     * 条件：当前不在 YSM 连接上（即服务器没有安装 YSM 模组），
     * 且本地玩家的模型被重置为 "default"，但之前有持久化的非 default 选择。
     * <p>
     * 此方法设计为只触发一次恢复：恢复后 modelId 不再是 "default"，条件不再满足。
     */
    @Environment(EnvType.CLIENT)
    public static void restorePersistedModelSelectionOnVanillaServer() {
        // 仅在无模组服务器上执行（YSM 连接未建立 = 服务器没有 YSM 模组）
        if (NetworkHandler.isClientConnected()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PlayerCapability.get(player).ifPresent(cap -> {
            // 只在模型被重置为 default 时触发恢复
            if (!"default".equals(cap.getModelId())) {
                return;
            }
            Pair<String, String> persisted = LocalModelSelectionStore.load();
            if (persisted == null) {
                return;
            }
            String modelId = persisted.getLeft();
            String textureId = persisted.getRight();
            // 模型必须在本地缓存中可用
            if (!isLocalOnlyModel(modelId) && !containsRuntimeModel(modelId)) {
                return;
            }
            cap.initModelWithTexture(modelId, textureId);
            MODEL_SELECTION.rememberPlain(modelId, textureId);
        });
    }

    /**
     * @deprecated 使用 {@link #restorePersistedModelSelection()} 替代
     */
    @Deprecated
    @Environment(EnvType.CLIENT)
    public static void restoreSelectedLocalOnlyModel() {
        restorePersistedModelSelection();
    }

    public static void onUploadedModelAvailable(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        onUploadedModelImported(modelId);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerCapability.get(player).ifPresent(cap -> {
                if (sameRuntimeModelId(modelId, cap.getModelId())) {
                    String textureId = cap.getCurrentTextureName();
                    rememberSelectedModel(modelId, textureId);
                    NetworkHandler.sendToServer(new C2SRequestSwitchModelPacket(modelId, textureId));
                }
            });
        }
    }

    public static void onUploadedModelImported(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        localOnlyModelIds.remove(modelKey);
        localModelSourcePaths.remove(modelKey);
    }

    public static void resendSelectedServerModel() {
        String modelId = MODEL_SELECTION.selectedModelId();
        String textureId = MODEL_SELECTION.selectedTextureId();
        if (modelId == null || modelId.isBlank() || textureId == null || isLocalOnlyModel(modelId) || !NetworkHandler.isClientConnected()) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            String currentModelId = MODEL_SELECTION.selectedModelId();
            String currentTextureId = MODEL_SELECTION.selectedTextureId();
            if (!sameRuntimeModelId(modelId, currentModelId) || currentTextureId == null || isLocalOnlyModel(modelId) || !containsRuntimeModel(modelId)) {
                return;
            }
            NetworkHandler.sendToServer(new C2SRequestSwitchModelPacket(modelId, currentTextureId));
        });
    }

    public static void removeLocalModels(Collection<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            List<Pair<String, ModelAssembly>> removed = new ArrayList<>();
            for (String modelId : modelIds) {
                String modelKey = LocalModelCatalog.canonicalKey(modelId);
                localOnlyModelIds.remove(modelKey);
                localModelSourcePaths.remove(modelKey);
                lazyModelSources.computeIfPresent(modelKey, (key, source) -> source.remote ? source : null);
                cpuReloadInFlight.remove(modelKey);
                if (sameRuntimeModelId(modelId, MODEL_SELECTION.localOnlyModelId())) {
                    MODEL_SELECTION.clearLocalOnly();
                }
                if (sameRuntimeModelId(modelId, MODEL_SELECTION.selectedModelId())) {
                    MODEL_SELECTION.clear();
                }
                modelLastUsedAt.remove(modelKey);
                gpuCacheTrimmedModels.remove(modelKey);
                ModelAssembly assembly = map.remove(modelKey);
                if (assembly != null) {
                    removed.add(Pair.of(modelKey, assembly));
                }
            }
            modelAssemblyMap = map;
            for (Pair<String, ModelAssembly> pair : removed) {
                releaseModelAssembly(pair.getLeft(), pair.getRight());
            }
            if (!removed.isEmpty()) {
                forEachGuiWidget(guiWidget -> guiWidget.onModelsLoaded(map));
            }
        });
    }

    /**
     * 释放所有"服务器同步"而来的模型装配，仅保留仅本地导入模型（localOnlyModelIds）、
     * 内建 default 模型以及当前本地模型上下文。会释放这些装配持有的纹理、音频、
     * 原生模型句柄（nativeModelHandle）以及 GPU 网格，使客户端断开连接后内存回落到基线，
     * 避免上一台服务器同步的模型在返回主菜单/进入无模组服务器后仍然常驻。
     * <p>
     * 重新加入模组服务器时会通过同步流程重新拉取模型；加入无模组服务器时本地模型仍可用。
     */
    public static void releaseServerSyncedModels(String reason) {
        Minecraft.getInstance().execute(() -> {
            Map<String, ModelAssembly> current = modelAssemblyMap;
            if (current == null || current.isEmpty()) {
                return;
            }
            Object2ReferenceOpenHashMap<String, ModelAssembly> retained = new Object2ReferenceOpenHashMap<>();
            List<Pair<String, ModelAssembly>> released = new ArrayList<>();
            for (Map.Entry<String, ModelAssembly> entry : current.entrySet()) {
                String modelId = entry.getKey();
                ModelAssembly assembly = entry.getValue();
                if ("default".equals(modelId) || localOnlyModelIds.contains(modelId) || assembly == localModelContext) {
                    retained.put(modelId, assembly);
                } else {
                    released.add(Pair.of(modelId, assembly));
                }
            }
            if (released.isEmpty()) {
                return;
            }
            modelAssemblyMap = retained;
            for (Pair<String, ModelAssembly> pair : released) {
                modelLastUsedAt.remove(pair.getLeft());
                gpuCacheTrimmedModels.remove(pair.getLeft());
                releaseModelAssembly(pair.getLeft(), pair.getRight());
            }
            ModelMemoryProfiler.log("server-models-released reason=" + reason + " count=" + released.size(), null);
            forEachGuiWidget(guiWidget -> guiWidget.onModelsLoaded(retained));
        });
    }

    public static void importLocalModel(String modelId, String fileName, byte[] data, @Nullable Consumer<Component> callback) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        byte[] importData = data;
        submitModelTask(() -> {
            Component error = null;
            try {
                ModelMemoryProfiler.logBytes("local-import-read", modelKey, importData);
                RawYsmModel rawModel = parseImportModel(fileName, importData);
                ModelMemoryProfiler.log("local-import-parsed", modelKey);
                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelKey);
                ModelMemoryProfiler.log("local-import-mapped", modelKey);
                localOnlyModelIds.add(modelKey);
                touchModel(modelKey);
                runPendingModelCallback();
                if (!processModelData(parsedBundle, modelKey, false, false)) {
                    localOnlyModelIds.remove(modelKey);
                    throw new IllegalStateException("Failed to build local model");
                }
                Path persisted = LOCAL_IMPORT_STORE.persist(modelKey, fileName, importData);
                localModelSourcePaths.put(modelKey, persisted.toAbsolutePath().normalize());
                if (persisted != null) {
                    LocalModelCatalog.Entry previousSource = lazyModelSources.get(modelKey);
                    ServerModelInfo prevInfo = previousSource == null ? null : previousSource.modelInfo;
                    String prevName = previousSource == null ? null : previousSource.displayName;
                    if (prevName == null) {
                        prevName = LocalModelCatalog.displayNameFromInfo(prevInfo);
                    }
                    if (prevName == null) {
                        prevName = LocalModelCatalog.sniffName(persisted);
                    }
                    lazyModelSources.put(modelKey, new LocalModelCatalog.Entry(persisted, null, false, false,
                            LocalModelCatalog.fingerprint(persisted), prevInfo, prevName));
                }
                Minecraft.getInstance().execute(ClientModelManager::flushPendingModels);
                YesSteveModel.LOGGER.info("[SM] Imported local model: {}", modelKey);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to import local model: {}", modelKey, e);
                error = Component.translatable("gui.sparkle_morpher.import.error.local_import_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (callback != null) {
                Component result = error;
                Minecraft.getInstance().execute(() -> callback.accept(result));
            }
        });
    }

    public static void reloadLocalModels(@Nullable Consumer<Component> callback) {
        submitModelTask(() -> {
            Component error = null;
            try {
                localModelSourcePaths.clear();
                LinkedHashMap<String, LocalModelCatalog.Entry> catalog = new LinkedHashMap<>();
                scanLocalModelSources(ServerModelManager.BUILT, false, catalog);
                scanLocalModelSources(ServerModelManager.CUSTOM, false, catalog);
                scanLocalModelSources(ServerModelManager.AUTH, true, catalog);
                applyLocalModelCatalog(catalog);
                if (!isLazyModelLoading()) {
                    for (Map.Entry<String, LocalModelCatalog.Entry> entry : catalog.entrySet()) {
                        if (!modelAssemblyMap.containsKey(entry.getKey())
                                || !modelAssemblyMap.get(entry.getKey()).isRuntimeResident()) {
                            loadLocalModelSource(entry.getKey(), entry.getValue());
                        }
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    flushPendingModels();
                    ClientRenderCompatibilityRegistry.flush();
                    forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(modelAssemblyMap));
                });
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to reload local model folders", e);
                error = Component.translatable("gui.sparkle_morpher.import.error.local_reload_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (callback != null) {
                Component result = error;
                Minecraft.getInstance().execute(() -> callback.accept(result));
            }
        });
    }

    public static ModelAssembly getLocalModelContext() {
        runPendingModelCallback();
        flushPendingModels();

        ModelAssembly model = localModelContext;
        if (model != null) {
            touchAssembly(model);
            return model;
        }

        // 触发预加载
        loadDefaultModel();
        model = localModelContext;
        if (model != null) {
            touchAssembly(model);
            return model;
        }

        Map<String, ModelAssembly> reg = modelAssemblyMap;
        if (reg != null && !reg.isEmpty()) {
            model = reg.get("default");
            if (model == null) {
                for (ModelAssembly v : reg.values()) {
                    if (v != null) {
                        model = v;
                        break;
                    }
                }
            }
            if (model != null) {
                localModelContext = model;
                touchAssembly(model);
                return model;
            }
        }
        return null;
    }

    public static ResourceLocation getDefaultTexture() {
        return defaultTexture.getResourceLocation().get();
    }

    // R7 剩余：GUI observers 迁至 GuiWidgetRegistry（注册/遍历委托）

    public static <T extends IGuiWidget> T registerGuiWidget(T t) {
        return GuiWidgetRegistry.register(t);
    }

    public static void unregisterGuiWidget(IGuiWidget guiWidget) {
        GuiWidgetRegistry.unregister(guiWidget);
    }

    private static void forEachGuiWidget(Consumer<IGuiWidget> consumer) {
        GuiWidgetRegistry.forEach(consumer);
    }


    public static synchronized void resetSync() {
        // R9.2：oySm/allowUpload 与握手标志统一由 resetClientHandshake（→ LegacySpmHandshakeState.resetClientSession）复位
        LegacyModelSyncClient.processServerData(null);
        NetworkHandler.resetClientHandshake();
        Minecraft.getInstance().execute(() -> {
            syncState.setState(SyncState.WAITING);
        });
    }

    public static void enterPrivacyMode() {
        LegacyModelSyncClient.processServerData(null);
        NetworkHandler.resetClientHandshake();
        Minecraft.getInstance().execute(() -> {
            syncState.setState(SyncState.LOADING);
            forEachGuiWidget(IGuiWidget::onSyncBegin);
        });
        reloadLocalModels(error -> {
            syncState.setState(SyncState.IDLE);
            restorePersistedModelSelection();
            forEachGuiWidget(guiWidget -> {
                guiWidget.onModelsLoaded(modelAssemblyMap);
                guiWidget.onSyncComplete();
            });
        });
    }

    public static boolean isAllowUpload() {
        return LegacySpmHandshakeState.isAllowUpload();
    }

    public static boolean isOysmServer() {
        return LegacySpmHandshakeState.isOysmServer();
    }

    // R7 剩余：Legacy sync 状态机/握手协议迁至 LegacyModelSyncClient（startSync 委托）

    public static synchronized void startSync(Connection connection, ByteBuffer byteBuffer) {
        LegacyModelSyncClient.startSync(connection, byteBuffer);
    }


    public static void onSyncConnected() {
        if (Minecraft.getInstance().isLocalServer()) {
            syncState.setState(SyncState.LOADING);
        } else {
            syncState.setState(SyncState.IDLE);
        }
        forEachGuiWidget(IGuiWidget::onSyncBegin);
    }

    /**
     * 在加入服务器一段时间后，如果 YSM 握手仍未完成（服务器没有安装本模组），
     * 将同步状态从 WAITING 重置为 IDLE，避免加载状态 UI 一直卡在“等待中”。
     */
    public static void markVanillaServerIfNoHandshake() {
        if (NetworkHandler.isClientConnected()) {
            return;
        }
        if (syncState.getCurrentState() == SyncState.WAITING) {
            syncState.setState(SyncState.IDLE);
        }
    }

    static void onSyncProgress(int totalModels) {
        if (totalModels == -1) {
            Minecraft.getInstance().execute(() -> {
                syncState.setState(SyncState.PREPARING);
                forEachGuiWidget(IGuiWidget::onSyncError);
            });
        } else {
            Minecraft.getInstance().execute(() -> {
                if (totalModels > 0) {
                    syncState.startSyncing(totalModels);
                } else {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> guiWidget.onSyncProgress(totalModels, 0));
            });
        }
    }

    static void onModelPacksReceived(ModelPackData[] packDataArr) {
        Object2ReferenceOpenHashMap<String, ModelPackData> newPackMap = new Object2ReferenceOpenHashMap<>();

        for (ModelPackData packData : packDataArr) {
            if (StringUtils.isBlank(packData.getName())) {
                packData = new ModelPackData(packData.getPath(), FileTypeUtil.getFinalPathSegment(packData.getPath()), packData.getDescription(), packData.getTexture(), packData.getTranslations());
            }
            newPackMap.put(packData.getPath(), packData);
            OuterFileTexture iconTexture = packData.getTexture();
            if (iconTexture != null) {
                ResourceLocation location2 = FileTypeUtil.getPackIconLocation(packData.getPath());
                Minecraft.getInstance().submit(() -> {
                    Minecraft.getInstance().getTextureManager().register(location2, iconTexture);
                });
            }
        }

        for (ModelPackData packData : modelPackMap.values()) {
            if (!newPackMap.containsKey(packData.getPath()) && packData.getTexture() != null) {
                ResourceLocation location = FileTypeUtil.getPackIconLocation(packData.getPath());
                Minecraft.getInstance().submit(() -> Minecraft.getInstance().getTextureManager().release(location));
            }
        }
        modelPackMap = newPackMap;
    }

    static void onModelContextsUpdated(String[] removedModelIds, String[] previousModelIds, String[] updatedModelIds, boolean[] isModelReady) {
        Minecraft.getInstance().execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            if (removedModelIds != null) {
                ArrayList<Pair<String, ModelAssembly>> removed = new ArrayList<>(removedModelIds.length);
                for (String str : removedModelIds) {
                    String modelKey = LocalModelCatalog.canonicalKey(str);
                    if (localOnlyModelIds.contains(modelKey)) {
                        continue;
                    }
                    modelLastUsedAt.remove(str);
                    gpuCacheTrimmedModels.remove(str);
                    if (sameRuntimeModelId(str, MODEL_SELECTION.localOnlyModelId())) {
                        MODEL_SELECTION.clearLocalOnly();
                    }
                    if (sameRuntimeModelId(str, MODEL_SELECTION.selectedModelId())) {
                        MODEL_SELECTION.clear();
                    }
                    ModelAssembly assembly = map.remove(modelKey);
                    if (assembly != null) {
                        removed.add(Pair.of(modelKey, assembly));
                    }
                }
                Minecraft.getInstance().execute(() -> {
                    for (Pair<String, ModelAssembly> pair : removed) {
                        releaseModelAssembly(pair.getLeft(), pair.getRight());
                    }
                });
            }
            if (previousModelIds != null) {
                ModelAssembly[] modelAssemblies = new ModelAssembly[previousModelIds.length];
                for (int i = 0; i < previousModelIds.length; i++) {
                    String previousKey = LocalModelCatalog.canonicalKey(previousModelIds[i]);
                    localOnlyModelIds.remove(previousKey);
                    if (sameRuntimeModelId(previousModelIds[i], MODEL_SELECTION.localOnlyModelId())) {
                        MODEL_SELECTION.clearLocalOnly();
                    }
                    if (sameRuntimeModelId(previousModelIds[i], MODEL_SELECTION.selectedModelId())) {
                        MODEL_SELECTION.setSelectedId(updatedModelIds[i]);
                    }
                    modelAssemblies[i] = map.remove(previousKey);
                }
                for (int i = 0; i < modelAssemblies.length; i++) {
                    ModelAssembly modelAssembly = modelAssemblies[i];
                    if (modelAssembly != null) {
                        modelAssembly.getTextureRegistry().setAuthModel(isModelReady[i]);
                        map.put(LocalModelCatalog.canonicalKey(updatedModelIds[i]), modelAssembly);
                    }
                }
            }
            modelAssemblyMap = map;
            if ((removedModelIds != null && removedModelIds.length > 0) || (previousModelIds != null && previousModelIds.length > 0)) {
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onModelsLoaded(map);
                });
            }
        });
    }

    static void onModelDataReceived(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) throws Exception {
        if (isPrimary) {
            pendingModelCallback = () -> {
                processModelData(parsedBundle, modelId, true, false);
            };
        } else {
            runPendingModelCallback();
            localOnlyModelIds.remove(LocalModelCatalog.canonicalKey(modelId));
            processModelData(parsedBundle, modelId, false, isAuth);
        }
    }

    public static void runPendingModelCallback() {
        Runnable runnable = pendingModelCallback;
        if (runnable != null) {
            synchronized (runnable) {
                Runnable runnable2 = pendingModelCallback;
                if (runnable2 != null) {
                    runnable2.run();
                    pendingModelCallback = null;
                }
            }
        }
    }

    private static void logModelProcessFailure(@Nullable String modelId, Throwable error) {
        String key = modelId == null ? "(unknown)" : modelId;
        long now = System.currentTimeMillis();
        long[] state = MODEL_PROCESS_FAILURES.computeIfAbsent(key, k -> new long[2]);
        long count;
        synchronized (state) {
            if (state[1] != 0L && now - state[1] > MODEL_PROCESS_FAILURE_SUPPRESS_MILLIS) {
                state[0] = 0L; // 时间窗口过期：重新计数并允许再次打印完整堆栈
            }
            state[1] = now;
            count = ++state[0];
        }
        if (count == 1) {
            YesSteveModel.LOGGER.error("Failed to process model: {}", key, error);
        } else if (count % 100 == 0) {
            YesSteveModel.LOGGER.error("Failed to process model: {} - repeated {} times since first failure, stack trace suppressed", key, count);
        }
    }

    public static boolean processModelData(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) {
        modelId = LocalModelCatalog.canonicalKey(modelId);
        if (parsedBundle != null) {
            try {
                ModelMemoryProfiler.log("assembly-build-start", modelId);
                ModelAssembly runtimeModel = ModelAssemblyFactory.buildAssembly(parsedBundle, isPrimary, isAuth);
                if (modelId != null) {
                    MODEL_PROCESS_FAILURES.remove(modelId);
                }
                ModelMemoryProfiler.log("assembly-build-finished", modelId);
                ResourceLifecycleStats.onModelAssemblyLoaded(modelId);
                pendingModelQueue.add(Pair.of(runtimeModel, modelId));
                touchModel(modelId);
                if (isPrimary) {
                    localModelContext = runtimeModel;

                    Minecraft.getInstance().execute(() -> {
                        defaultTexture = UploadManager.getOrCreateLocatable(runtimeModel.getAnimationBundle().getTextures().getValueAt(0), true);
                    });
                    return true;
                }
            } catch (Exception e) {
                if (isPrimary) throw new RuntimeException(e);
                logModelProcessFailure(modelId, e);
                return false;
            }
        }
        return parsedBundle != null;
    }

    static void markSyncModelProcessed() {
        Minecraft.getInstance().execute(() -> {
            if (syncState.currentState != SyncState.SYNCING) {
                return;
            }
            markSyncActivity();
            syncState.syncedModels = Math.min(syncState.totalModels, syncState.syncedModels + 1);
            int processed = syncState.syncedModels;
            forEachGuiWidget(guiWidget -> guiWidget.onSyncProgress(syncState.getTotalModels(), processed));
        });
    }

    private static RawYsmModel parseImportModel(String fileName, byte[] data) throws Exception {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ysm")) {
            return parseYsmImport(data, fileName);
        }
        if (lower.endsWith(".zip")) {
            return parseZipImport(data);
        }
        if (lower.endsWith(".bbmodel")) {
            return parseBbModelImport(data, fileName);
        }
        if (lower.endsWith(".geo.json") || lower.endsWith("geometry.json")) {
            return parseBedrockGeoImport(data, fileName);
        }
        throw new IllegalArgumentException("Unsupported model import type: " + fileName);
    }

    private static RawYsmModel parseYsmImport(byte[] data, String source) throws Exception {
        int ysmCryptoVersion = YesModelUtils.getYsmCryptoVersion(data);
        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(YesModelUtils.input(data))) {
                return deserializer.deserialize();
            }
        }
        try {
            byte[] decrypted = YsmCrypt.decryptYsmFile(data);
            try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
                RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                deserializer.parseYSMFooter(rawModel);
                return rawModel;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid model: " + source, e);
        }
    }

    private static RawYsmModel parseZipImport(byte[] data) throws Exception {
        // 先嗅探 zip 内容：YSM 包走老路径，Figura/纯 bbmodel 包直接走 bbmodel 解析
        com.micaftic.morpher.resource.bbmodel.ZipModelSniffer sniff =
                com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.sniff(data, 64L * 1024L * 1024L);

        switch (sniff.kind) {
            case FIGURA_AVATAR:
            case PLAIN_BBMODEL: {
                String avatarName = sniff.kind == com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.Kind.FIGURA_AVATAR
                        ? com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.parseAvatarName(sniff.avatarJsonBytes)
                        : null;
                YesSteveModel.LOGGER.info(
                        "[SM] Detected {} zip (bbmodel={}, textures={}{})",
                        sniff.kind == com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.Kind.FIGURA_AVATAR ? "Figura avatar" : "bbmodel",
                        sniff.bbmodelPath, sniff.sideTextures.size(),
                        avatarName == null ? "" : ", avatar.name=" + avatarName);
                String json = new String(sniff.bbmodelBytes, java.nio.charset.StandardCharsets.UTF_8);
                com.micaftic.morpher.resource.bbmodel.BBModelFile bbmodel =
                        com.micaftic.morpher.resource.bbmodel.BBModelParser.parse(json);
                RawYsmModel rawModel = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.convert(bbmodel, sniff.sideTextures);
                rawModel.properties.sha256 = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheSha256(data);
                return rawModel;
            }
            case YSM_FOLDER:
            case UNKNOWN:
            default:
                break;
        }

        if (sniff.kind == com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.Kind.BEDROCK_PACK) {
            return parseBedrockPackImport(sniff);
        }

        // 落到这里：YSM_FOLDER 或 UNKNOWN（让 YSMFolderDeserializer 处理 / 报错）
        Path temp = Files.createTempFile("ysm-local-import-", ".zip");
        try {
            Files.write(temp, data);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(temp)) {
                return deserializer.deserialize();
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to remove temporary local import archive {}", temp, e);
            }
        }
    }

    private static RawYsmModel parseBedrockGeoImport(byte[] data, String fileName) throws Exception {
        String identifier = bedrockIdentifierFromFileName(fileName);
        YesSteveModel.LOGGER.info("[SM] Importing Bedrock geometry file {} (identifier={})", fileName, identifier);
        RawYsmModel.RawGeometry geometry = YSMFolderDeserializer.parseBedrockGeometry(data, identifier);
        if (geometry == null || geometry.bones.isEmpty()) {
            throw new IllegalArgumentException("Invalid Bedrock geometry: " + fileName);
        }
        RawYsmModel raw = assembleBedrockModel(geometry, null, null);
        normalizeBedrockCase(raw);
        raw.properties.sha256 = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheSha256(data);
        return raw;
    }

    private static RawYsmModel parseBedrockPackImport(com.micaftic.morpher.resource.bbmodel.ZipModelSniffer sniff) throws Exception {
        String identifier = bedrockIdentifierFromFileName(sniff.bedrockGeoPath);
        YesSteveModel.LOGGER.info("[SM] Detected Bedrock pack (geo={}, textures={}, animations={})",
                sniff.bedrockGeoPath, sniff.sideTextures.size(), sniff.bedrockAnimations.size());
        RawYsmModel.RawGeometry geometry = YSMFolderDeserializer.parseBedrockGeometry(sniff.bedrockGeoBytes, identifier);
        if (geometry == null || geometry.bones.isEmpty()) {
            throw new IllegalArgumentException("Invalid Bedrock pack (no usable geometry): " + sniff.bedrockGeoPath);
        }

        Map<String, RawYsmModel.RawTexture> textures = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : sniff.sideTextures.entrySet()) {
            RawYsmModel.RawTexture texture = YSMFolderDeserializer.parseBedrockTexture(entry.getValue(), entry.getKey());
            if (texture.data != null) {
                textures.put(entry.getKey(), texture);
            }
        }

        Map<String, RawYsmModel.RawAnimationFile> animationFiles = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<String, byte[]> entry : sniff.bedrockAnimations.entrySet()) {
            try {
                RawYsmModel.RawAnimationFile animationFile = YSMFolderDeserializer.parseAnimationFile(entry.getValue());
                if (!animationFile.animations.isEmpty()) {
                    animationFiles.put("bedrock-anim-" + (index++), animationFile);
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to parse Bedrock animation {}: {}", entry.getKey(), e.toString());
            }
        }

        animationFiles = BedrockAnimationMapping.remapToActions(animationFiles);
        RawYsmModel raw = assembleBedrockModel(geometry, textures, animationFiles);
        normalizeBedrockCase(raw);
        raw.properties.sha256 = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheSha256(sniff.bedrockGeoBytes);
        return raw;
    }

    /** 组装 Bedrock 直读模型：几何 + 可选纹理 + 可选动画，属性对齐 bbmodel 导入（scale=1）。 */
    private static RawYsmModel assembleBedrockModel(RawYsmModel.RawGeometry geometry,
                                                    Map<String, RawYsmModel.RawTexture> textures,
                                                    Map<String, RawYsmModel.RawAnimationFile> animationFiles) {
        RawYsmModel raw = new RawYsmModel();
        raw.modelId = (geometry.identifier == null || geometry.identifier.isEmpty()) ? "bedrock" : geometry.identifier;
        raw.formatVersion = 65535;
        raw.metadata = new RawYsmModel.RawMetadata();
        raw.properties = new RawYsmModel.RawProperties();
        raw.properties.widthScale = 1.0f;
        raw.properties.heightScale = 1.0f;
        raw.properties.defaultTexture = "default";

        geometry.modelType = 1;
        RawYsmModel.RawMainEntity mainEntity = new RawYsmModel.RawMainEntity();
        mainEntity.mainModel = geometry;
        if (textures != null) {
            mainEntity.textures.putAll(textures);
            if (!textures.isEmpty()) {
                raw.properties.defaultTexture = textures.keySet().iterator().next();
            }
        }
        if (animationFiles != null) {
            mainEntity.animationFiles.putAll(animationFiles);
        }
        raw.mainEntity = mainEntity;
        raw.footer = new RawYsmModel.RawFooter();
        return raw;
    }

    /**
     * Bedrock 导入边界大小写归一：骨名与动画骨名统一小写。
     * 基岩版动画用小写（leftarm）、几何用驼峰（leftArm），
     * 这里在入口处把两者归一为小写以便动画绑定；
     * 仅在 Bedrock 入口生效，不影响 YSM/内置模型路径。
     */
    private static void normalizeBedrockCase(RawYsmModel raw) {
        if (raw == null || raw.mainEntity == null) {
            return;
        }
        Map<String, String> rename = new HashMap<>();
        RawYsmModel.RawGeometry geometry = raw.mainEntity.mainModel;
        if (geometry != null && geometry.bones != null) {
            for (RawYsmModel.RawBone bone : geometry.bones) {
                if (bone.name == null || bone.name.isEmpty()) continue;
                String lower = bone.name.toLowerCase(Locale.ROOT);
                if (!lower.equals(bone.name)) {
                    rename.put(bone.name, lower);
                }
            }
            for (RawYsmModel.RawBone bone : geometry.bones) {
                if (bone.name != null) {
                    bone.name = rename.getOrDefault(bone.name, bone.name);
                }
                if (bone.parentName != null) {
                    bone.parentName = rename.getOrDefault(bone.parentName, bone.parentName);
                }
            }
        }
        for (RawYsmModel.RawAnimationFile animationFile : raw.mainEntity.animationFiles.values()) {
            if (animationFile == null || animationFile.animations == null) continue;
            for (RawYsmModel.RawAnimation animation : animationFile.animations.values()) {
                if (animation == null || animation.boneAnimations == null) continue;
                for (RawYsmModel.RawBoneAnimation boneAnimation : animation.boneAnimations) {
                    if (boneAnimation != null && boneAnimation.boneName != null) {
                        boneAnimation.boneName = boneAnimation.boneName.toLowerCase(Locale.ROOT);
                    }
                }
            }
        }
    }

    /** 从文件名推导 Bedrock 几何 identifier：去掉 .geo.json / geometry.json 后缀后的文件名。 */
    private static String bedrockIdentifierFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        String lower = fileName.toLowerCase(Locale.ROOT);
        String base;
        if (lower.endsWith(".geo.json")) {
            base = fileName.substring(0, fileName.length() - ".geo.json".length());
        } else if (lower.endsWith("geometry.json")) {
            base = fileName.substring(0, fileName.length() - "geometry.json".length());
        } else {
            base = fileName;
        }
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.trim();
        return base.isEmpty() ? null : base;
    }

    private static RawYsmModel parseBbModelImport(byte[] data, String source) throws Exception {
        try {
            String json = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            com.micaftic.morpher.resource.bbmodel.BBModelFile bbmodel = com.micaftic.morpher.resource.bbmodel.BBModelParser.parse(json);
            RawYsmModel rawModel = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.convert(bbmodel);
            rawModel.properties.sha256 = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheSha256(data);
            return rawModel;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid bbmodel file: " + source, e);
        }
    }

    private static void scanLocalModelSources(Path baseDir, boolean isAuth,
                                              Map<String, LocalModelCatalog.Entry> catalog) throws IOException {
        LocalModelCatalog.ScanResult result = LOCAL_MODEL_CATALOG.scan(baseDir, isAuth,
                YSMFolderDeserializer::isModelFolder, lazyModelSources, catalog);
        if (!samePath(baseDir, ServerModelManager.CUSTOM)) {
            return;
        }
        for (Map.Entry<String, Path> source : result.sources().entrySet()) {
            localModelSourcePaths.put(source.getKey(), source.getValue());
        }
    }

    private static void applyLocalModelCatalog(Map<String, LocalModelCatalog.Entry> catalog) {
        LocalModelCatalog.Diff diff = LocalModelCatalog.diff(lazyModelSources, catalog);
        Set<String> validIds = new HashSet<>(catalog.keySet());
        Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
        ArrayList<Pair<String, ModelAssembly>> staleAssemblies = new ArrayList<>();
        for (String staleId : diff.staleIds()) {
            ModelAssembly stale = map.remove(staleId);
            if (stale != null) {
                staleAssemblies.add(Pair.of(staleId, stale));
            }
            modelLastUsedAt.remove(staleId);
            gpuCacheTrimmedModels.remove(staleId);
        }

        lazyModelSources.entrySet().removeIf(entry -> !entry.getValue().remote);
        lazyModelSources.putAll(diff.catalog());
        localOnlyModelIds.clear();
        localOnlyModelIds.addAll(validIds);
        modelAssemblyMap = map;

        if (!staleAssemblies.isEmpty()) {
            ((Executor) Minecraft.getInstance()).execute(() -> staleAssemblies.forEach(pair ->
                    releaseModelAssembly(pair.getLeft(), pair.getRight())));
        }
    }

    private static void loadLocalModelSource(String modelId, LocalModelCatalog.Entry source) throws Exception {
        if (source.remote) return;
        RawYsmModel rawModel;
        if (Files.isDirectory(source.path)) {
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(source.path)) {
                rawModel = deserializer.deserialize();
            }
        } else {
            long size = Files.size(source.path);
            if (size > MAX_LOCAL_MODEL_FILE_BYTES) {
                throw new IOException("Local model file too large (" + size + " bytes), skipped: " + source.path);
            }
            byte[] data = Files.readAllBytes(source.path);
            rawModel = parseImportModel(source.path.getFileName().toString(), data);
        }
        loadLocalModel(modelId, rawModel, source.auth);
    }

    private static void loadLocalModel(String modelId, RawYsmModel rawModel, boolean isAuth) throws Exception {
        modelId = LocalModelCatalog.canonicalKey(modelId);
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
        localOnlyModelIds.add(modelId);
        runPendingModelCallback();
        if (!processModelData(parsedBundle, modelId, false, isAuth)) {
            localOnlyModelIds.remove(modelId);
            throw new IllegalStateException("Failed to build local model");
        }
    }

    private static boolean containsRuntimeModel(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        return modelKey != null && (modelAssemblyMap.containsKey(modelKey) || lazyModelSources.containsKey(modelKey));
    }

    private static boolean sameRuntimeModelId(String first, String second) {
        return Objects.equals(LocalModelCatalog.canonicalKey(first), LocalModelCatalog.canonicalKey(second));
    }

    private static boolean samePath(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }

    static void markSyncActivity() {
        lastSyncActivityMillis = System.currentTimeMillis();
    }

    static void finishPendingModelLoad() {
        pendingModelsCount.updateAndGet(value -> Math.max(0, value - 1));
        markSyncModelProcessed();
        scheduleSyncCompleteIfReady();
    }

    static void scheduleSyncCompleteIfReady() {
        if (!syncManifestProcessed || pendingModelsCount.get() != 0
                || !syncCompletionScheduled.compareAndSet(false, true)) {
            return;
        }
        submitModelTask(() -> {
            YesSteveModel.LOGGER.info("[SM] All server models loaded; handshake complete!");
            onSyncComplete();
        });
    }

    /**
     * 由客户端每帧（经 {@code ModelSyncStateOverlay#render}）调用。
     * 若同步处于进行中（还有待下载模型或仍在 SYNCING）且超过
     * {@link #SYNC_WATCHDOG_TIMEOUT_MILLIS} 没有任何进度，则强制结束同步，
     * 防止加载弹窗永久卡在某一进度。
     */
    public static void tickSyncWatchdog() {
        long last = lastSyncActivityMillis;
        if (last == 0L) {
            return;
        }
        boolean active = pendingModelsCount.get() > 0 || syncState.getCurrentState() == SyncState.SYNCING;
        if (!active) {
            lastSyncActivityMillis = 0L;
            return;
        }
        if (System.currentTimeMillis() - last > SYNC_WATCHDOG_TIMEOUT_MILLIS) {
            lastSyncActivityMillis = 0L;
            int dropped = pendingModelsCount.getAndSet(0);
            YesSteveModel.LOGGER.warn(
                    "[SM] 模型同步超时（{}ms 无进度），强制结束以避免加载弹窗卡死；有 {} 个模型分片未收齐。",
                    SYNC_WATCHDOG_TIMEOUT_MILLIS, dropped);
            onSyncComplete();
        }
    }

    private static void onSyncComplete() {
        LegacyModelSyncClient.syncStep = 1;
        LegacyModelCacheClient.clearCachedModelHashes();
        lastSyncActivityMillis = 0L;
        syncManifestProcessed = false;

        Minecraft.getInstance().execute(() -> {
            flushPendingModels();
            ClientRenderCompatibilityRegistry.flush();
            syncState.finishSuccess();
            // 远端懒加载目录到此才完整，补做一次持久化选择恢复。
            restorePersistedModelSelection();
            resendSelectedServerModel();
            forEachGuiWidget(IGuiWidget::onSyncComplete);
        });
    }

    public static void setAllowUpload(boolean allowUpload) {
        LegacySpmHandshakeState.setAllowUpload(allowUpload);
    }

    public static void setOysmServer(boolean isOysmServer) {
        LegacySpmHandshakeState.setOysmServer(isOysmServer);
    }

    private static void onSyncError(@Nullable Object obj) {
        lastSyncActivityMillis = 0L;
        Minecraft.getInstance().execute(() -> {
            syncState.finishFailure(obj instanceof Component component ? component : null);
            forEachGuiWidget(guiWidget -> {
                guiWidget.onSyncMessage(obj == null ? null : (Component) obj);
            });
            if (obj instanceof Component component) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(component);
                }
                YesSteveModel.LOGGER.error(component.getString(256));
            }
        });
    }

    public static void flushPendingModels() {
        if (pendingModelQueue.isEmpty())
            return;

        Object2ReferenceOpenHashMap<String, ModelAssembly> object2ReferenceOpenHashMap = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
        while (true) {
            Pair<ModelAssembly, String> pairPoll = pendingModelQueue.poll();
            if (pairPoll != null) {
                String modelKey = LocalModelCatalog.canonicalKey(pairPoll.getRight());
                ModelAssembly previous = object2ReferenceOpenHashMap.put(modelKey, pairPoll.getLeft());
                LocalModelCatalog.Entry source = lazyModelSources.get(modelKey);
                if (source != null && !(pairPoll.getLeft() instanceof LazyModelAssembly)) {
                    source.modelInfo = pairPoll.getLeft().getModelData();
                    if (source.modelInfo != null) {
                        String name = LocalModelCatalog.displayNameFromInfo(source.modelInfo);
                        if (StringUtils.isNotBlank(name)) {
                            source.displayName = name;
                        }
                    }
                }
                touchModel(modelKey);
                gpuCacheTrimmedModels.remove(modelKey);
                if (previous != null && previous != pairPoll.getLeft()) {
                    releaseModelAssembly(modelKey, previous);
                }
           } else {
               modelAssemblyMap = object2ReferenceOpenHashMap;
                trimUnusedCpuModels();
               forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(object2ReferenceOpenHashMap));
               return;
           }
       }
   }

   private static void releaseAssemblyTextures(ModelAssembly assembly) {
       for (AbstractTexture tex : assembly.getTextures()) {
            if (tex == null) {
                continue;
            }
           UploadManager.removeTexture(tex);
            if (tex instanceof OuterFileTexture outerFileTexture) {
                outerFileTexture.closeAndReleaseSource();
            } else {
                tex.close();
            }
        }
    }

    private static void releaseModelAssembly(ModelAssembly assembly) {
        releaseModelAssembly(null, assembly);
    }

    private static void releaseModelAssembly(String modelId, ModelAssembly assembly) {
        if (assembly == null || assembly instanceof LazyModelAssembly) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> releaseModelAssembly(modelId, assembly));
            return;
       }
        synchronized (assembly) {
        if (EntityRenderCache.isModelAssemblyInUse(assembly)) {
            deferredAssemblyReleases.add(assembly);
            return;
        }
        deferredAssemblyReleases.remove(assembly);
       AudioStreamCache.clearForModel(assembly);
        releaseAssemblyTextures(assembly);
        if (assembly.getProjectileModels() != null) {
            for (Map.Entry<ResourceLocation, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                releaseModelCache(entry.getValue().getModel());
            }
        }
        if (assembly.getVehicleModels() != null) {
            for (Map.Entry<ResourceLocation, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
                releaseModelCache(entry.getValue().getModel());
            }
        }
        if (assembly.getAnimationBundle() != null) {
            releaseModelCache(assembly.getAnimationBundle().getMainModel());
            releaseModelCache(assembly.getAnimationBundle().getArmModel());
        }
       if (assembly.getExpressionCache() != null) {
           for (AudioTrackData trackData : assembly.getExpressionCache().getSoundEffects().values()) {
               if (trackData != null) trackData.close();
           }
       }
        assembly.unloadRuntime();
       ResourceLifecycleStats.onModelAssemblyEvicted(modelId);
        ModelMemoryProfiler.log("assembly-released", modelId);
        }
    }

    private static void releaseModelCache(GeoModel model) {
        if (model == null) {
            return;
        }
        if (RuntimeAccelerationLoader.isLoaded()) {
            model.freeNativeCache();
        } else {
            model.freeGpuCache();
        }
    }

    public static void trimUnusedGpuCaches() {
        updateModelLoadingMode();
        drainDeferredAssemblyReleases();
        // R10.2：孤儿 GPU mesh 兜底回收（owner 弱引用失效/异常替换路径残留），渲染线程执行。
        GpuRenderPath.sweepOrphanedMeshes("periodic trim");
        long checkNow = System.currentTimeMillis();
        if (checkNow - lastModelTrimMillis < 1_000L) {
            return;
        }
        lastModelTrimMillis = checkNow;
        trimUnusedCpuModels();
       int maxCachedGpuModels = GeneralConfig.safeInt(GeneralConfig.MAX_CACHED_GPU_MODELS, 0);
        if (maxCachedGpuModels <= 0) {
           return;
       }
       Minecraft minecraft = Minecraft.getInstance();
        long residentGpuModels = modelAssemblyMap.values().stream()
                .filter(Objects::nonNull)
                .filter(ModelAssembly::isRuntimeResident)
                .count();
        if (residentGpuModels <= maxCachedGpuModels) {
           return;
       }

        long now = System.currentTimeMillis();
        long ttlMillis = GeneralConfig.safeInt(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300) * 1000L;
        Set<String> protectedModels = collectProtectedModelIds(minecraft);
        ModelMemoryProfiler.log("lru-check", null);
       modelAssemblyMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isRuntimeResident())
               .filter(entry -> canTrimGpuCache(entry.getKey(), protectedModels, now, ttlMillis))
               .sorted(Comparator.comparingLong(entry -> modelLastUsedAt.getOrDefault(entry.getKey(), 0L)))
                .limit(Math.max(1L, residentGpuModels - maxCachedGpuModels))
               .forEach(entry -> trimGpuCache(entry.getKey(), entry.getValue()));
    }

    private static void trimUnusedCpuModels() {
        if (!isLazyModelLoading()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (modelAssemblyMap.isEmpty()) return;
        long now = System.currentTimeMillis();
        long ttlMillis = GeneralConfig.safeInt(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300) * 1000L;
        Set<String> protectedModels = collectProtectedModelIds(minecraft);
        List<Map.Entry<String, ModelAssembly>> residents = modelAssemblyMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isRuntimeResident())
                .filter(entry -> !"default".equals(entry.getKey()) && lazyModelSources.containsKey(entry.getKey()))
                .toList();
        long idleCount = residents.stream()
                .filter(entry -> !protectedModels.contains(entry.getKey()))
                .filter(entry -> now - modelLastUsedAt.getOrDefault(entry.getKey(), now) >= ttlMillis)
                .count();
        long overLimit = Math.max(0, residents.size() - GeneralConfig.safeInt(GeneralConfig.MAX_RESIDENT_CPU_MODELS, 64));
        long trimCount = Math.max(idleCount, overLimit);
        if (trimCount <= 0) return;
        List<Map.Entry<String, ModelAssembly>> victims = residents.stream()
                .filter(entry -> !protectedModels.contains(entry.getKey()))
                .filter(entry -> {
                    long idleMillis = now - modelLastUsedAt.getOrDefault(entry.getKey(), now);
                    return idleMillis >= ttlMillis || (overLimit > 0 && idleMillis >= 1_000L);
                })
                .sorted(Comparator.comparingLong(entry -> modelLastUsedAt.getOrDefault(entry.getKey(), 0L)))
                .limit(trimCount)
                .toList();
        if (victims.isEmpty()) return;

        Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
        ArrayList<Pair<String, ModelAssembly>> released = new ArrayList<>();
        for (Map.Entry<String, ModelAssembly> entry : victims) {
            LocalModelCatalog.Entry source = lazyModelSources.get(entry.getKey());
            if (source == null) continue;
            source.modelInfo = entry.getValue().getModelData();
            if (source.modelInfo == null) continue;
            String name = LocalModelCatalog.displayNameFromInfo(source.modelInfo);
            if (StringUtils.isNotBlank(name)) {
                source.displayName = name;
            }
            map.put(entry.getKey(), new LazyModelAssembly(entry.getKey(), source));
            gpuCacheTrimmedModels.remove(entry.getKey());
            released.add(Pair.of(entry.getKey(), entry.getValue()));
        }
        if (released.isEmpty()) return;
        modelAssemblyMap = map;
        forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(map));
        released.forEach(pair -> releaseModelAssembly(pair.getLeft(), pair.getRight()));
    }

    private static void unloadModelRuntime(String modelId, ModelAssembly assembly) {
        if (assembly == null || !assembly.isRuntimeResident()) return;
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> unloadModelRuntime(modelId, assembly));
            return;
        }
        synchronized (assembly) {
        if (EntityRenderCache.isModelAssemblyInUse(assembly)) return;
        AudioStreamCache.clearForModel(assembly);
        if (assembly.getProjectileModels() != null) {
            for (ProjectileModelBundle bundle : assembly.getProjectileModels().values()) releaseModelCache(bundle.getModel());
        }
        if (assembly.getVehicleModels() != null) {
            for (VehicleModelBundle bundle : assembly.getVehicleModels().values()) releaseModelCache(bundle.getModel());
        }
        if (assembly.getAnimationBundle() != null) {
            releaseModelCache(assembly.getAnimationBundle().getMainModel());
            releaseModelCache(assembly.getAnimationBundle().getArmModel());
        }
       if (assembly.getExpressionCache() != null) {
           for (AudioTrackData trackData : assembly.getExpressionCache().getSoundEffects().values()) if (trackData != null) trackData.close();
       }
        releaseAssemblyTextures(assembly);
       assembly.unloadRuntime();
        gpuCacheTrimmedModels.remove(modelId);
        ModelMemoryProfiler.log("cpu-model-unloaded", modelId);
        }
    }

    private static boolean canTrimGpuCache(String modelId, Set<String> protectedModels, long now, long ttlMillis) {
        modelId = LocalModelCatalog.canonicalKey(modelId);
        if (modelId == null || "default".equals(modelId) || protectedModels.contains(modelId) || gpuCacheTrimmedModels.contains(modelId)) {
            return false;
        }
        long lastUsed = modelLastUsedAt.getOrDefault(modelId, 0L);
        return lastUsed > 0L && now - lastUsed >= ttlMillis;
    }

    private static Set<String> collectProtectedModelIds(Minecraft minecraft) {
        Set<String> protectedModels = new HashSet<>();
        protectedModels.add("default");
        if (localModelContext != null) {
            for (Map.Entry<String, ModelAssembly> entry : modelAssemblyMap.entrySet()) {
                if (entry.getValue() == localModelContext) {
                    protectedModels.add(entry.getKey());
                    touchModel(entry.getKey());
                    break;
                }
            }
        }
        if (MODEL_SELECTION.selectedModelId() != null && !MODEL_SELECTION.selectedModelId().isBlank()) {
            protectedModels.add(LocalModelCatalog.canonicalKey(MODEL_SELECTION.selectedModelId()));
            touchModel(MODEL_SELECTION.selectedModelId());
        }
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                ModelInfoCapability.get(player).ifPresent(cap -> {
                    String modelId = cap.getModelId();
                    if (modelId != null && !modelId.isBlank()) {
                        protectedModels.add(LocalModelCatalog.canonicalKey(modelId));
                        touchModel(modelId);
                    }
                });
            }
        }
        return protectedModels;
    }

    private static void drainDeferredAssemblyReleases() {
        if (deferredAssemblyReleases.isEmpty()) return;
        for (ModelAssembly assembly : new ArrayList<>(deferredAssemblyReleases)) {
            if (!EntityRenderCache.isModelAssemblyInUse(assembly)
                    && deferredAssemblyReleases.remove(assembly)) {
                releaseModelAssembly(assembly);
            }
        }
    }

    private static void trimGpuCache(String modelId, ModelAssembly assembly) {
        if (assembly == null || !assembly.isRuntimeResident() || !gpuCacheTrimmedModels.add(modelId)) {
           return;
       }
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> trimGpuCache(modelId, assembly));
            return;
        }
        int releasedMeshes = 0;
        if (assembly.getProjectileModels() != null) {
            for (Map.Entry<ResourceLocation, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                if (entry.getValue().getModel().freeGpuCache()) {
                    releasedMeshes++;
                }
           }
       }
        if (assembly.getVehicleModels() != null) {
            for (Map.Entry<ResourceLocation, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
                if (entry.getValue().getModel().freeGpuCache()) {
                    releasedMeshes++;
                }
           }
       }
        if (assembly.getAnimationBundle() != null) {
            if (assembly.getAnimationBundle().getMainModel().freeGpuCache()) {
                releasedMeshes++;
            }
            if (assembly.getAnimationBundle().getArmModel().freeGpuCache()) {
                releasedMeshes++;
            }
       }
        if (releasedMeshes > 0) {
            ModelMemoryProfiler.log("gpu-cache-trimmed meshes=" + releasedMeshes, modelId);
        }
    }

    private static void touchModel(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        if (modelKey != null && !modelKey.isBlank()) {
            modelLastUsedAt.put(modelKey, System.currentTimeMillis());
            gpuCacheTrimmedModels.remove(modelKey);
        }
    }

    static boolean isLazyModelLoading() {
        return GeneralConfig.safeGet(GeneralConfig.LAZY_MODEL_LOADING, true);
    }

    public static void updateModelLoadingMode() {
        boolean enabled = isLazyModelLoading();
        Boolean previous = lastLazyModelLoading;
        if (previous != null && previous == enabled) return;
        lastLazyModelLoading = enabled;
        lastModelTrimMillis = 0L;
        if (previous == null || enabled) return;

        for (String modelId : new ArrayList<>(lazyModelSources.keySet())) {
            ModelAssembly assembly = modelAssemblyMap.get(modelId);
            if (assembly == null || !assembly.isRuntimeResident()) {
                scheduleCachedModelReload(modelId);
            }
        }
    }

    public static void markModelUsed(String modelId) {
        touchModel(modelId);
    }

    public static boolean isGpuCacheTrimmed(String modelId) {
        String modelKey = LocalModelCatalog.canonicalKey(modelId);
        return modelKey != null && gpuCacheTrimmedModels.contains(modelKey);
    }

    private static void touchAssembly(ModelAssembly assembly) {
        if (assembly == null) {
            return;
        }
        for (Map.Entry<String, ModelAssembly> entry : modelAssemblyMap.entrySet()) {
            if (entry.getValue() == assembly) {
                touchModel(entry.getKey());
                return;
            }
        }
    }

    private static final class LazyModelAssembly extends ModelAssembly {
        private final String modelId;
        private final LocalModelCatalog.Entry source;
        private final ModelDisplayAssets displayAssets;
        private final ModelResourceBundle metadataResources;

        private LazyModelAssembly(String modelId, LocalModelCatalog.Entry source) {
            super(null, Map.of(), Map.of(), createLazyResourceBundle(), source.modelInfo,
                    new ModelDisplayAssets(source.modelInfo.getModelProperties().getDefaultTexture(),
                            source.auth, Map.of(), Map.of()), List.of());
            this.modelId = modelId;
            this.source = source;
            this.displayAssets = super.getTextureRegistry();
            this.metadataResources = super.getExpressionCache();
        }

        private static ModelResourceBundle createLazyResourceBundle() {
            return new ModelResourceBundle(Map.of(), new Object2ReferenceOpenHashMap<>(),
                    new Object2ReferenceOpenHashMap<>(), Map.of());
        }

        @Nullable
        private ModelAssembly loadedAssembly() {
            ModelAssembly current = modelAssemblyMap.get(modelId);
            return current != null && current != this && !(current instanceof LazyModelAssembly)
                    && current.isRuntimeResident() ? current : null;
        }

        @Nullable
        private ModelAssembly requestAndGetFallback() {
            scheduleCachedModelReload(modelId);
            ModelAssembly loaded = loadedAssembly();
            return loaded == null ? localModelContext : loaded;
        }

        @Override
        public PlayerModelBundle getAnimationBundle() {
            ModelAssembly assembly = requestAndGetFallback();
            return assembly == null ? null : assembly.getAnimationBundle();
        }

        @Override
        public ModelResourceBundle getExpressionCache() {
            ModelAssembly assembly = loadedAssembly();
            return assembly == null ? metadataResources : assembly.getExpressionCache();
        }

        @Override
        public Map<ResourceLocation, ProjectileModelBundle> getProjectileModels() {
            ModelAssembly assembly = requestAndGetFallback();
            return assembly == null ? Map.of() : assembly.getProjectileModels();
        }

        @Override
        public Map<ResourceLocation, VehicleModelBundle> getVehicleModels() {
            ModelAssembly assembly = requestAndGetFallback();
            return assembly == null ? Map.of() : assembly.getVehicleModels();
        }

        @Override
        public ServerModelInfo getModelData() {
            ModelAssembly assembly = loadedAssembly();
            return assembly == null ? source.modelInfo : assembly.getModelData();
        }

        @Override
        public ModelDisplayAssets getTextureRegistry() {
            ModelAssembly assembly = loadedAssembly();
            return assembly == null ? displayAssets : assembly.getTextureRegistry();
        }

        @Override
        public List<AbstractTexture> getTextures() {
            ModelAssembly assembly = loadedAssembly();
            return assembly == null ? List.of() : assembly.getTextures();
        }
    }

    public static int getPendingModelCount() {
        return pendingModelQueue.size();
    }

    public static class SyncStatus {
        private SyncState currentState = SyncState.WAITING;

        private int totalModels = -1;

        private int syncedModels = -1;

        private long terminalSinceMillis = 0L;

        @Nullable
        private Component message = null;

        public SyncState getCurrentState() {
            return this.currentState;
        }

        public int getSyncedModels() {
            return this.syncedModels;
        }

        public int getTotalModels() {
            return this.totalModels;
        }

        public long getTerminalSinceMillis() {
            return this.terminalSinceMillis;
        }

        @Nullable
        public Component getMessage() {
            return this.message;
        }

        public void setState(SyncState syncState) {
            this.currentState = syncState;
            this.totalModels = -1;
            this.syncedModels = -1;
            this.terminalSinceMillis = 0L;
            this.message = null;
        }

        public void startSyncing(int totalModels) {
            this.currentState = SyncState.SYNCING;
            this.totalModels = totalModels;
            this.syncedModels = 0;
            this.terminalSinceMillis = 0L;
            this.message = null;
        }

        public void finishSuccess() {
            this.currentState = SyncState.IDLE;
            if (this.totalModels < 0) {
                this.totalModels = Math.max(0, this.syncedModels);
            }
            this.syncedModels = this.totalModels;
            this.terminalSinceMillis = System.currentTimeMillis();
            this.message = null;
        }

        public void finishFailure(@Nullable Component message) {
            this.currentState = SyncState.IDLE;
            this.terminalSinceMillis = System.currentTimeMillis();
            this.message = message;
        }
    }

    public static void exportAllCachedModels(@Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                if (LegacyModelSyncClient.clientKey == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("未连接到服务器或尚未完成握手同步，无法获取客户端解密密钥。"), "", "", 0));
                    }
                    return;
                }

                String folder = LegacyModelSyncClient.currentCacheFolderName != null ? LegacyModelSyncClient.currentCacheFolderName : "default_cache";
                File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();

                if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("尚未生成任何缓存或缓存文件夹不存在: " + folder), "", "", 0));
                    }
                    return;
                }

                File[] files = cacheDir.listFiles();
                if (files == null || files.length == 0) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("缓存文件夹中没有任何模型可供导出。"), "", "", 0));
                    }
                    return;
                }

                int successCount = 0;
                for (File file : files) {
                    if (!file.isFile()) continue;

                    try {
                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        byte[] clearText = YsmCrypt.readInPlace(fileBytes, LegacyModelSyncClient.clientKey);

                        int coreDataLength;
                        String exportName = file.getName(); // Fallback name

                        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(clearText, 32)) {
                            RawYsmModel rawModel = deserializer.deserializeKeepOpen();
                            coreDataLength = deserializer.getReader().getRawBuf().readerIndex();

                            if (rawModel.metadata != null && rawModel.metadata.name != null && !rawModel.metadata.name.trim().isEmpty()) {
                                exportName = rawModel.metadata.name.trim();
                            } else if (rawModel.properties != null && rawModel.properties.sha256 != null && !rawModel.properties.sha256.isEmpty()) {
                                exportName = rawModel.properties.sha256;
                            }
                        }

                        exportName = exportName.replaceAll("[\\\\/:*?\"<>|]", "_");

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeDword(32);

                            outBuf.getRawBuf().writeBytes(clearText, 0, coreDataLength);

                            outBuf.writeVarInt(32); // Version
                            outBuf.writeVarInt(1);

                            byte[] randBytes = new byte[8];
                            SECURE_RANDOM.nextBytes(randBytes);
                            StringBuilder sb = new StringBuilder(16);
                            for (byte b : randBytes) {
                                sb.append(String.format("%02x", b));
                            }
                            outBuf.writeString(sb.toString()); // rand hash

                            outBuf.writeVarLong(java.time.Instant.now().getEpochSecond()); // time
                            outBuf.writeString(extra != null ? extra : ""); // extra info
                            outBuf.writeVarInt(0); // padding

                            byte[] rawBytes = new byte[outBuf.getRawBuf().readableBytes()];
                            outBuf.getRawBuf().readBytes(rawBytes);

                            byte[] finalEncrypted = YsmCrypt.encryptYsmFile(rawBytes);

                            Path exportPath = ServerModelManager.EXPORT.resolve(exportName + ".ysm");
                            Files.createDirectories(exportPath.getParent());
                            Files.write(exportPath, finalEncrypted);

                            successCount++;
                            YesSteveModel.LOGGER.info("[SM] Successfully exported cached model to: " + exportPath);
                        }
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("[SM] Failed to export cached model: " + file.getName(), e);
                    }
                }

                if (callback != null) {
                    String displayPath = Paths.get("export").toString();
                    if (successCount > 0) {
                        callback.accept(new ExportResult(true, null, displayPath, "", 0));
                    } else {
                        callback.accept(new ExportResult(false, Component.literal("导出完成，但没有成功导出任何模型。可能是缓存已损坏。"), "", "", 0));
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Error during batch export", e);
                if (callback != null) {
                    callback.accept(new ExportResult(false, Component.literal("批量导出过程发生严重错误: " + e.getMessage()), "", "", 0));
                }
            }
        });
    }
}
