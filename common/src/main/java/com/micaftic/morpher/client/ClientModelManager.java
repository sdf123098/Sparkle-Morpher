package com.micaftic.morpher.client;

import com.micaftic.morpher.RuntimeAccelerationLoader;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.gui.IGuiWidget;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.ModelAssemblyFactory;
import com.micaftic.morpher.client.model.ProjectileModelBundle;
import com.micaftic.morpher.client.model.VehicleModelBundle;
import com.micaftic.morpher.client.texture.OuterFileTexture;
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
import com.micaftic.morpher.resource.models.ModelPackData;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.DigestUtil;
import com.micaftic.morpher.util.FileTypeUtil;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.util.LocalModelSelectionStore;
import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.util.NetworkOnlineDebugLog;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.micaftic.morpher.util.YSMThreadPool;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.mojang.blaze3d.systems.RenderSystem;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.micaftic.morpher.mixin.client.MinecraftAccessor;
import java.util.concurrent.Executor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.message.StringFormattedMessage;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.legacy.YesModelUtils;
import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.core.security.YSMClientCache;
import com.micaftic.morpher.core.security.YsmCrypt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ClientModelManager {
    private static int syncStep = 1;
    private static byte[] key1;
    private static byte[] lastKey;
    private static byte[] serverKey;
    private static byte[] clientKey;
    private static String currentCacheFolderName;
    private static final AtomicInteger pendingModelsCount = new AtomicInteger(0);
    private static final AtomicBoolean syncCompletionScheduled = new AtomicBoolean(false);
    private static volatile boolean syncManifestProcessed = false;

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
    private static final AtomicInteger MODEL_TASK_GENERATION = new AtomicInteger(0);
    private static final ThreadPoolExecutor modelTaskDispatcher = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), r -> {
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

    private static void submitModelTask(Runnable task) {
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

    private static final Map<UUID, ServerModelContext> serverModels = new ConcurrentHashMap<>();

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    private static volatile ModelAssembly localModelContext;
    private static volatile Runnable pendingModelCallback;
    private static IResourceLocatable defaultTexture;
    private static volatile boolean defaultModelLoadAttempted;
    private static volatile Connection serverConnection;

    private static volatile Map<String, ModelAssembly> modelAssemblyMap = Object2ReferenceMaps.emptyMap();
    private static volatile Map<String, ModelPackData> modelPackMap = new Object2ReferenceOpenHashMap<>();
    private static final ConcurrentHashMap<String, Long> modelLastUsedAt = new ConcurrentHashMap<>();
    private static final Set<String> gpuCacheTrimmedModels = ConcurrentHashMap.newKeySet();
    private static final Map<String, File> cachedModelFiles = new ConcurrentHashMap<>();
    private static final Set<String> cpuReloadInFlight = ConcurrentHashMap.newKeySet();

    private static final ConcurrentLinkedQueue<Pair<ModelAssembly, String>> pendingModelQueue = new ConcurrentLinkedQueue<>();
    private static final WeakHashMap<IGuiWidget, Object> guiWidgets = new WeakHashMap<>();
    private static final Set<String> localOnlyModelIds = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<String, Path> localModelSourcePaths = new ConcurrentHashMap<>();
    private static final SyncStatus syncState = new SyncStatus();
    private static volatile boolean isOysmServer = false;
    private static volatile boolean allowUpload = false;
    private static volatile String selectedModelId;
    private static volatile String selectedTextureId;
    private static volatile String selectedLocalOnlyModelId;
    private static volatile String selectedLocalOnlyTextureId;

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

        public byte[] fileBuffer;
        public int totalSize;
        public int bytesReceived;

        public ServerModelContext(long hash1, long hash2, String modelId, boolean isAuth, int isCustomSkinModel, int version) {
            this.uuid = new UUID(hash1, hash2);
            this.hash1 = hash1;
            this.hash2 = hash2;
            this.modelId = modelId;
            this.modelKey = canonicalRuntimeModelKey(modelId);
            this.isAuth = isAuth;
            this.isCustomSkinModel = isCustomSkinModel;
            this.version = version;
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
            URL resourceUrl = YesSteveModel.class.getResource(resourcePath);
            if (resourceUrl == null) {
                YesSteveModel.LOGGER.error("[SM] Builtin default model not found in classpath: " + resourcePath);
                return;
            }
            URI uri = resourceUrl.toURI();
            Path defaultPath;
            FileSystem jarFs = null;
            if ("jar".equals(uri.getScheme())) {
                try {
                    jarFs = FileSystems.getFileSystem(uri);
                } catch (FileSystemNotFoundException e) {
                    jarFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                }
                defaultPath = jarFs.getPath(resourcePath);
            } else {
                defaultPath = Paths.get(uri);
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

    private static void processServerData(ByteBuffer data) {
        if (data == null) {
            resetClientState();
            return;
        }
        try {
            if (!data.hasRemaining() && data.position() > 0) {
                data.flip();
            }
            if (!data.hasRemaining()) return;

            byte[] packetBytes = new byte[data.remaining()];
            data.get(packetBytes);

            byte[] decrypted;
            if (syncStep == 1) {
                decrypted = YsmCrypt.decrypt(packetBytes, YsmCrypt.publicKey);
                if (decrypted != null) handlePacket01(decrypted);
            } else if (syncStep == 2) {
                decrypted = YsmCrypt.decrypt(packetBytes, lastKey);
                if (decrypted != null) {
                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                        handlePacket03(buf);
                    }
                }
            } else if (syncStep == 3) {
                decrypted = YsmCrypt.decrypt(packetBytes, key1);
                if (decrypted != null) {
                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                        handlePacket05(buf);
                    }
                }
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Sync Error at step " + syncStep, e);
        }
    }

    private static void handlePacket01(byte[] decryptedBuffer) throws Exception {
        key1 = new byte[56];
        System.arraycopy(decryptedBuffer, decryptedBuffer.length - 56, key1, 0, 56);
        syncStep = 2;

        YesSteveModel.LOGGER.info("[SM] Exchanged Key1. Preparing to send Packet 02.");
        onSyncProgress(-1); // Preparing GUI stage

        int garbageLen = 16 + SECURE_RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        SECURE_RANDOM.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.getRawBuf().writeByte(0x02);
            outBuf.getRawBuf().writeByte(0x00);

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, true);
            lastKey = result.nextKey();

            sendModelFile(ByteBuffer.wrap(result.data()));
        }
    }

    private record ModelHash(long hash1, long hash2) {
    }

    private static final List<ModelHash> cachedModelHashes = new ArrayList<>();

    private static void handlePacket03(YSMByteBuf buf) throws Exception {
        buf.skipGarbageHeader();
        int type = buf.readVarInt(); // expect 3
        long folderHash = buf.readVarLong();
        currentCacheFolderName = Long.toHexString(folderHash);

        serverKey = new byte[56];
        buf.getRawBuf().readBytes(serverKey);

        clientKey = new byte[56];
        buf.getRawBuf().readBytes(clientKey);

        File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(currentCacheFolderName).toFile();
        if (!cacheDir.exists()) cacheDir.mkdirs();
        YSMClientCache.prepareCacheDirectory(cacheDir);

        Map<UUID, File> localCacheMap = YSMClientCache.buildCacheIndex(cacheDir, clientKey);
        List<ModelHash> modelsToRequest = new ArrayList<>();
        Set<UUID> expectedCacheModels = new HashSet<>();

        int unkSize = buf.readVarInt();
        onSyncProgress(unkSize);
        pendingModelsCount.set(0);
        syncManifestProcessed = false;
        syncCompletionScheduled.set(false);

        Set<String> validServerModelIds = new HashSet<>();
        List<String> previousModelIds = new ArrayList<>();
        List<String> updatedModelIds = new ArrayList<>();
        List<Boolean> isModelReadyList = new ArrayList<>();

        for (int i = 0; i < unkSize; i++) {
            long hash1 = buf.readVarLong();
            long hash2 = buf.readVarLong();
            ModelHash mHash = new ModelHash(hash1, hash2);
            cachedModelHashes.add(mHash);

            String modelId = buf.readString();
            boolean isAuth = buf.readVarInt() == 1;// isAuth
            int isCustomSkinModel = buf.readVarInt();// is default
//            System.out.println("Received model hash: " + mHash + ", id: " + modelId + ", unk1: " + isAuth + ", unk2: " + isCustomSkinModel);
            int version = buf.readVarInt(); // 鐎甸€涚艾閺傚洣娆㈡径瑙勬弓閸旂姴鐦戦惃鍕侀崹瀣剁礉娑?5535

            ServerModelContext ctx = new ServerModelContext(hash1, hash2, modelId, isAuth, isCustomSkinModel, version);
            serverModels.put(ctx.uuid, ctx);
            boolean firstCanonicalId = validServerModelIds.add(ctx.modelKey);
            expectedCacheModels.add(ctx.uuid);
            if (!firstCanonicalId) {
                YesSteveModel.LOGGER.warn("[SM] Ignoring duplicate server model id after case normalization: raw={} key={}", modelId, ctx.modelKey);
                continue;
            }

            File cachedFile = localCacheMap.get(ctx.uuid);
            if (cachedFile != null) cachedModelFiles.put(ctx.modelKey, cachedFile);
            boolean isFileValid = YSMClientCache.verifyFileContent(cachedFile, hash1, hash2);

            boolean alreadyInMemory = modelAssemblyMap != null && modelAssemblyMap.containsKey(ctx.modelKey);

            if (isFileValid) {
                YesSteveModel.LOGGER.info("[SM] Cache HIT & Validated: " + ctx.uuid);
                if (alreadyInMemory) {
                    previousModelIds.add(ctx.modelKey);
                    updatedModelIds.add(ctx.modelKey);
                    isModelReadyList.add(isAuth);
                } else {
                    // 閸涙垝鑵戠紓鎾崇摠
                    pendingModelsCount.incrementAndGet();
                    submitModelTask(() -> {
                        try {
                            if (clientKey == null) return;
                            byte[] fileBytes = Files.readAllBytes(cachedFile.toPath());
                            ModelMemoryProfiler.logBytes("cache-read", modelId, fileBytes);
                            byte[] decompressed = YsmCrypt.read(fileBytes, clientKey);
                            ModelMemoryProfiler.logBytes("cache-decrypted", modelId, decompressed);
                            fileBytes = null;
                            parseAndLoadModel(decompressed, ctx.modelKey, isAuth);
                            decompressed = null;
                            ModelMemoryProfiler.log("cache-parsed", modelId);
                        } catch (Exception e) {
                            YesSteveModel.LOGGER.error("[SM] Failed to parse and load cached model: " + modelId, e);
                            YSMClientCache.deleteCacheFile(cachedFile);
                        } finally {
                            finishPendingModelLoad();
                        }
                    });
                }
            } else {
                YesSteveModel.LOGGER.info("[SM] Cache MISS or Invalid: " + ctx.uuid + " -> Requesting...");
                YSMClientCache.deleteCacheFile(cachedFile);
                modelsToRequest.add(mHash);
                pendingModelsCount.incrementAndGet();
            }
        }

        YSMClientCache.cleanupCacheDirectory(cacheDir, expectedCacheModels, clientKey);

        int unkSize2 = buf.readVarInt();
        List<ModelPackData> parsedPacks = new ArrayList<>();

        for (int i = 0; i < unkSize2; i++) {
            String folderPath = buf.readString();

            OuterFileTexture iconTexture = null;
            if (buf.readVarInt() != 0) {
                byte[] textureData = buf.readByteArray();
                int textureWidth = buf.readVarInt();
                int textureHeight = buf.readVarInt();
                int imageFormat = buf.readVarInt();
                int unkImageData = buf.readVarInt();

                byte[] png = YSMClientMapper.toPng(textureData, imageFormat, textureWidth, textureHeight);

                iconTexture = new OuterFileTexture(png);
            }

            String folderName = "";
            String folderDesc = "";
            int hasYSMPackInfo = buf.readVarInt();
            if (hasYSMPackInfo != 0) {
                folderName = buf.readString();
                folderDesc = buf.readString();
            }

            Map<String, Map<String, String>> languageData = new HashMap<>();
            int languageSize = buf.readVarInt();
            for (int j = 0; j < languageSize; j++) {
                String languageType = buf.readString();
                int translateKeySize = buf.readVarInt();
                Map<String, String> translationMap = new HashMap<>();
                for (int k = 0; k < translateKeySize; k++) {
                    translationMap.put(buf.readString(), buf.readString());
                }
                languageData.put(languageType, translationMap);
            }
            parsedPacks.add(new ModelPackData(folderPath, folderName, folderDesc, iconTexture, languageData));
        }

        if (!parsedPacks.isEmpty()) {
            onModelPacksReceived(parsedPacks.toArray(new ModelPackData[0]));
        }

        List<String> modelsToRemove = new ArrayList<>();
        if (modelAssemblyMap != null) {
            for (String loadedId : modelAssemblyMap.keySet()) {
                if ("default".equals(loadedId)) continue;

                if (!validServerModelIds.contains(loadedId)) {
                    modelsToRemove.add(loadedId);
                } else if (modelsToRequest.stream().anyMatch(h -> serverModels.containsKey(new UUID(h.hash1, h.hash2)) && serverModels.get(new UUID(h.hash1, h.hash2)).modelKey.equals(loadedId))) {
                    modelsToRemove.add(loadedId);
                }
            }
        }

        if (!modelsToRemove.isEmpty() || !previousModelIds.isEmpty()) {
            boolean[] readyArr = new boolean[isModelReadyList.size()];
            for (int j = 0; j < isModelReadyList.size(); j++) {
                readyArr[j] = isModelReadyList.get(j);
            }

            onModelContextsUpdated(
                    modelsToRemove.isEmpty() ? null : modelsToRemove.toArray(new String[0]),
                    previousModelIds.isEmpty() ? null : previousModelIds.toArray(new String[0]),
                    updatedModelIds.isEmpty() ? null : updatedModelIds.toArray(new String[0]),
                    readyArr
            );
            YesSteveModel.LOGGER.info("[SM] Cleaned up {} outdated models and updated {} existing models during sync.", modelsToRemove.size(), previousModelIds.size());
        }

        syncStep = 3;
        markSyncActivity();

        int garbageLen = 16 + SECURE_RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        SECURE_RANDOM.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.getRawBuf().writeByte(0x04);

            outBuf.writeVarInt(modelsToRequest.size());
            for (ModelHash h : modelsToRequest) {
                outBuf.writeVarLong(h.hash1);
                outBuf.writeVarLong(h.hash2);
            }

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, false);
            sendModelFile(ByteBuffer.wrap(result.data()));
        }

        syncManifestProcessed = true;
        scheduleSyncCompleteIfReady();
    }

    private static void handlePacket05(YSMByteBuf buf) throws Exception {
        buf.skipGarbageHeader();
        int type = buf.readVarInt();
        if (type != 5) return;

        long hash1 = buf.readVarLong();
        long hash2 = buf.readVarLong();
        UUID uuid = new UUID(hash1, hash2);

        ServerModelContext ctx = serverModels.get(uuid);
        if (ctx == null) {
            YesSteveModel.LOGGER.warn("[SM] Received unexpected file chunk for model: " + uuid);
            return;
        }

        int totalSize = buf.readVarInt();
        int chunkOffset = buf.readVarInt();
        int chunkLength = buf.readVarInt();

        if (totalSize <= 0 || totalSize > 512 * 1024 * 1024 || chunkLength <= 0
                || chunkOffset < 0 || chunkOffset + chunkLength > totalSize
                || buf.getRawBuf().readableBytes() < chunkLength) {
            throw new IllegalArgumentException("Invalid model chunk bounds");
        }

        // Initialize buffer on first reception
        if (ctx.fileBuffer == null) {
            ctx.fileBuffer = new byte[totalSize];
            ctx.totalSize = totalSize;
            ctx.bytesReceived = 0;
        } else if (ctx.totalSize != totalSize) {
            throw new IllegalArgumentException("Model chunk total size changed");
        }
        if (chunkOffset != ctx.bytesReceived) {
            throw new IllegalArgumentException("Out-of-order model chunk: expected " + ctx.bytesReceived + ", got " + chunkOffset);
        }

        buf.getRawBuf().readBytes(ctx.fileBuffer, chunkOffset, chunkLength);
        ctx.bytesReceived += chunkLength;
        markSyncActivity();

        if (ctx.bytesReceived >= totalSize) {
            byte[] fileBuffer = ctx.fileBuffer;
            ctx.fileBuffer = null;

            submitModelTask(() -> {
                try {
                    if (clientKey == null) return;
                    String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                    File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    byte[] cachedFileData = YsmCrypt.transcodeServerDataToClientCache(fileBuffer, serverKey, clientKey, hash1, hash2);
                    ModelMemoryProfiler.logBytes("download-transcoded-cache", ctx.modelId, cachedFileData);

                    String legitFileName = YSMClientCache.generateCacheFileName(hash1, hash2, clientKey);
                    File outFile = new File(cacheDir, legitFileName);

                    Path tempFile = Files.createTempFile(cacheDir.toPath(), legitFileName, ".tmp");
                    try {
                        Files.write(tempFile, cachedFileData);
                        try {
                            Files.move(tempFile, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(tempFile, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally {
                        Files.deleteIfExists(tempFile);
                    }

                    YesSteveModel.LOGGER.info("[SM] Downloaded & Cached: " + outFile.getAbsolutePath());
                    byte[] decompressed = YsmCrypt.read(cachedFileData, clientKey);
                    ModelMemoryProfiler.logBytes("download-decrypted", ctx.modelId, decompressed);
                    cachedFileData = null;

                    cachedModelFiles.put(ctx.modelKey, outFile);
                    parseAndLoadModel(decompressed, ctx.modelKey, ctx.isAuth);
                    decompressed = null;
                    ModelMemoryProfiler.log("download-parsed", ctx.modelId);
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[SM] Failed to save/parse downloaded model: " + ctx.modelId, e);
                } finally {
                    finishPendingModelLoad();
                }
            });
        }
    }


    private static void parseAndLoadModel(byte[] decompressed, String modelId, boolean isAuth) {
        modelId = canonicalRuntimeModelKey(modelId);
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

                // Read version number
                rawModel.footer.version = reader.readVarInt(); // 65535 or 32

                rawModel.footer.unkInt1 = reader.readVarInt(); // Analyze
                if (rawModel.footer.unkInt1 != 0) {
                    rawModel.footer.rand = reader.readString();
                }

                rawModel.footer.time = reader.readVarLong();

                if (rawModel.footer.unkInt1 != 0) {
                    rawModel.footer.extra = reader.readString();
                    rawModel.footer.unkInt2 = reader.readVarInt();
                }

                // Assemble to client model
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

    private static void resetClientState() {
        MODEL_TASK_GENERATION.incrementAndGet();
        modelTaskDispatcher.getQueue().clear();
        serverConnection = null;
        syncStep = 1;
        key1 = null;
        lastKey = null;
        serverKey = null;
        clientKey = null;

        int discardedModelTasks = modelPhraseExecutor.getQueue().size();
        modelPhraseExecutor.getQueue().clear();
        MODEL_PARSE_SLOTS.release(discardedModelTasks);

        currentCacheFolderName = null;
        pendingModelsCount.set(0);
        syncManifestProcessed = false;
        syncCompletionScheduled.set(false);
        cachedModelHashes.clear();

        serverModels.clear();
        cachedModelFiles.clear();
        cpuReloadInFlight.clear();

        // 断线/换服：释放过期服务端模型装配（含纹理源 byte[] 与 GPU/native 资源），
        // 否则它们会被 modelAssemblyMap 强引用跨会话累积（主要内存泄漏源）。
        // 保留 localModelContext（默认模型）与本地导入模型：reloadLocalModels 会重建后者。
        if (!modelAssemblyMap.isEmpty()) {
            Object2ReferenceOpenHashMap<String, ModelAssembly> snapshot = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            Object2ReferenceOpenHashMap<String, ModelAssembly> survivors = new Object2ReferenceOpenHashMap<>();
            ArrayList<ModelAssembly> toRelease = new ArrayList<>();
            for (Map.Entry<String, ModelAssembly> entry : snapshot.entrySet()) {
                String id = entry.getKey();
                ModelAssembly asm = entry.getValue();
                if (asm == localModelContext || localOnlyModelIds.contains(id) || "default".equals(id)) {
                    survivors.put(id, asm);
                } else {
                    toRelease.add(asm);
                }
            }
            modelAssemblyMap = survivors;
            for (ModelAssembly asm : toRelease) {
                releaseModelAssembly(asm);
            }
        }

        Map<String, ModelPackData> oldPreviews = modelPackMap;
        if (oldPreviews != null && !oldPreviews.isEmpty()) {
            for (ModelPackData preview : oldPreviews.values()) {
                if (preview.getTexture() != null) {
                    Identifier loc = FileTypeUtil.getPackIconLocation(preview.getPath());
                    ((Executor) Minecraft.getInstance()).execute(() -> {
                        ((MinecraftAccessor) Minecraft.getInstance()).ysm$getTextureManager().release(loc);
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
        localOnlyModelIds.clear();
        localModelSourcePaths.clear();
        modelLastUsedAt.clear();
        gpuCacheTrimmedModels.clear();

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
       String modelKey = canonicalRuntimeModelKey(str);
       ModelAssembly assembly = modelAssemblyMap.get(modelKey);
        if (assembly != null) {
            touchModel(modelKey);
        }
       if (assembly != null && !assembly.isRuntimeResident()) {
           scheduleCachedModelReload(modelKey);
           return Optional.empty();
       }
       return Optional.ofNullable(assembly);
    }

    private static void scheduleCachedModelReload(String modelId) {
        String modelKey = canonicalRuntimeModelKey(modelId);
        if (modelKey == null || !cpuReloadInFlight.add(modelKey)) return;
        File cachedFile = cachedModelFiles.get(modelKey);
        ServerModelContext context = serverModels.values().stream()
                .filter(value -> modelKey.equals(value.modelKey)).findFirst().orElse(null);
        if (cachedFile == null || context == null || clientKey == null) {
            cpuReloadInFlight.remove(modelKey);
            return;
        }
        submitModelTask(() -> {
            try {
                byte[] fileBytes = Files.readAllBytes(cachedFile.toPath());
                byte[] decompressed = YsmCrypt.read(fileBytes, clientKey);
                parseAndLoadModel(decompressed, modelKey, context.isAuth);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to reload resident model: {}", modelKey, e);
            } finally {
                cpuReloadInFlight.remove(modelKey);
            }
        });
    }

    public static boolean isLocalOnlyModel(String modelId) {
        return modelId != null && localOnlyModelIds.contains(canonicalRuntimeModelKey(modelId));
    }

    public static Optional<Path> getLocalModelSourcePath(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        Path path = localModelSourcePaths.get(canonicalRuntimeModelKey(modelId));
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
        return modelId != null && sameRuntimeModelId(modelId, selectedModelId) && isLocalOnlyModel(modelId);
    }

    public static void rememberSelectedModel(String modelId, String textureId) {
        selectedModelId = modelId;
        selectedTextureId = textureId;
        if (isLocalOnlyModel(modelId)) {
            selectedLocalOnlyModelId = modelId;
            selectedLocalOnlyTextureId = textureId;
        } else if (modelId != null && sameRuntimeModelId(modelId, selectedLocalOnlyModelId)) {
            clearSelectedLocalOnlyModel();
        }
        // 持久化模型选择到本地文件，以便在无模组服务器上自动恢复
        LocalModelSelectionStore.save(modelId, textureId);
    }

    /**
     * 恢复本地玩家之前选择的模型。
     * <p>
     * 优先从内存中恢复，如果内存中的选择在断开 YSM 服务器后可能已不可用，
     * 则从本地文件恢复持久化的选择。
     */
    public static void restorePersistedModelSelection() {
        // 1. 先尝试内存中的选择
        String modelId = selectedModelId;
        String textureId = selectedTextureId;

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

        // 5. 拷贝为 final 变量供 lambda 使用
        final String finalModelId = modelId;
        final String finalTextureId = textureId;

        // 6. 在渲染线程上应用
        ((Executor) Minecraft.getInstance()).execute(() -> {
            // 再次检查，防止在 execute 延迟期间选择已改变
            if (!sameRuntimeModelId(finalModelId, selectedModelId) && !isLocalOnlyModel(finalModelId) && !containsRuntimeModel(finalModelId)) {
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
                }
            });
        });
    }

    /**
     * 在无模组服务器（vanilla server）上，通过 tick 事件持续检测并恢复持久化的模型选择。
     * <p>
     * 当玩家加入 vanilla 服务器时，模型会被重置为 default。
     * 此方法在每个 tick 中检查：如果当前模型是 default 且有持久化的选择，
     * 则自动恢复之前的模型。
     */
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
            selectedModelId = modelId;
            selectedTextureId = textureId;
        });
    }

    /**
     * @deprecated 使用 {@link #restorePersistedModelSelection()} 替代
     */
    @Deprecated
    public static void restoreSelectedLocalOnlyModel() {
        restorePersistedModelSelection();
    }

    public static void onUploadedModelAvailable(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        String modelKey = canonicalRuntimeModelKey(modelId);
        localOnlyModelIds.remove(modelKey);
        localModelSourcePaths.remove(modelKey);
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
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

    public static void resendSelectedServerModel() {
        String modelId = selectedModelId;
        String textureId = selectedTextureId;
        NetworkOnlineDebugLog.info("resendSelected: modelId={} textureId={} connected={}", modelId, textureId, NetworkHandler.isClientConnected());
        if (modelId == null || modelId.isBlank() || textureId == null || isLocalOnlyModel(modelId) || !NetworkHandler.isClientConnected()) {
            NetworkOnlineDebugLog.info("resendSelected: EARLY_RETURN (null/blank/localOnly/disconnected)");
            return;
        }
        ((Executor) Minecraft.getInstance()).execute(() -> {
            String currentModelId = selectedModelId;
            String currentTextureId = selectedTextureId;
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
        ((Executor) Minecraft.getInstance()).execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            ArrayList<ModelAssembly> removed = new ArrayList<>();
            for (String modelId : modelIds) {
                String modelKey = canonicalRuntimeModelKey(modelId);
                localOnlyModelIds.remove(modelKey);
                localModelSourcePaths.remove(modelKey);
                if (sameRuntimeModelId(modelId, selectedLocalOnlyModelId)) {
                    clearSelectedLocalOnlyModel();
                }
                if (sameRuntimeModelId(modelId, selectedModelId)) {
                    clearSelectedModel();
                }
                modelLastUsedAt.remove(modelKey);
                gpuCacheTrimmedModels.remove(modelKey);
                ModelAssembly assembly = map.remove(modelKey);
                if (assembly != null) {
                    removed.add(assembly);
                }
            }
            modelAssemblyMap = map;
            for (ModelAssembly assembly : removed) {
                releaseModelAssembly(assembly);
            }
            if (!removed.isEmpty()) {
                forEachGuiWidget(guiWidget -> guiWidget.onModelsLoaded(map));
            }
        });
    }

    public static void importLocalModel(String modelId, String fileName, byte[] data, @Nullable Consumer<Component> callback) {
        String modelKey = canonicalRuntimeModelKey(modelId);
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
                Path persisted = persistImportedModel(modelKey, fileName, importData);
                rememberLocalModelSource(ServerModelManager.CUSTOM, modelKey, persisted);
                YesSteveModel.LOGGER.info("[SM] Imported local model: {}", modelKey);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to import local model: {}", modelKey, e);
                error = Component.translatable("gui.sparkle_morpher.import.error.local_import_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (callback != null) {
                Component result = error;
                ((Executor) Minecraft.getInstance()).execute(() -> callback.accept(result));
            }
        });
    }

    private static Path persistImportedModel(String modelId, String fileName, byte[] data) throws IOException {
        if (modelId == null || modelId.isBlank() || data == null) {
            return null;
        }
        String extension = importExtension(fileName);
        if (extension.isBlank()) {
            extension = ".ysm";
        }
        Path target = ServerModelManager.CUSTOM.resolve(modelId + extension).normalize();
        if (!isInside(ServerModelManager.CUSTOM, target)) {
            throw new IOException("Invalid import target: " + modelId);
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temp, data);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            removeSiblingImportFiles(modelId, target);
            return target;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static String importExtension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            if (lower.endsWith(extension)) {
                return extension;
            }
        }
        return "";
    }

    private static void removeSiblingImportFiles(String modelId, Path keepTarget) throws IOException {
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            Path sibling = ServerModelManager.CUSTOM.resolve(modelId + extension).normalize();
            if (isInside(ServerModelManager.CUSTOM, sibling) && !sibling.toAbsolutePath().normalize().equals(keepTarget.toAbsolutePath().normalize())) {
                Files.deleteIfExists(sibling);
            }
        }
    }

    private static boolean isInside(Path root, Path path) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();
        return absolutePath.startsWith(absoluteRoot);
    }

    public static void reloadLocalModels(@Nullable Consumer<Component> callback) {
        reloadLocalModels(callback, true);
    }

    public static void reloadLocalModels(@Nullable Consumer<Component> callback, boolean restoreSelection) {
        submitModelTask(() -> {
            Component error = null;
            try {
                localModelSourcePaths.clear();
                loadDirectoryModels(ServerModelManager.BUILT);
                loadDirectoryModels(ServerModelManager.CUSTOM);
                loadDirectoryModels(ServerModelManager.AUTH);
                if (restoreSelection) {
                    restorePersistedModelSelection();
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to reload local model folders", e);
                error = Component.translatable("gui.sparkle_morpher.import.error.local_reload_failed", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            if (callback != null) {
                Component result = error;
                ((Executor) Minecraft.getInstance()).execute(() -> callback.accept(result));
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

        // 鐟欙箑褰傛０鍕鏉?        loadDefaultModel();
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

    public static Identifier getDefaultTexture() {
        return defaultTexture.getResourceLocation().get();
    }

    public static <T extends IGuiWidget> T registerGuiWidget(T t) {
        guiWidgets.put(t, null);
        return t;
    }

    public static void unregisterGuiWidget(IGuiWidget guiWidget) {
        guiWidgets.remove(guiWidget, null);
    }

    private static void forEachGuiWidget(Consumer<IGuiWidget> consumer) {
        Iterator<IGuiWidget> it = guiWidgets.keySet().iterator();
        while (it.hasNext()) {
            try {
                consumer.accept(it.next());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
    }

    public static synchronized void resetSync() {
        isOysmServer = false;
        allowUpload = false;
        processServerData(null);
        NetworkHandler.resetClientHandshake();
        ((Executor) Minecraft.getInstance()).execute(() -> {
            syncState.setState(SyncState.WAITING);
        });
    }

    public static boolean isAllowUpload() {
        return allowUpload;
    }

    public static boolean isOysmServer() {
        return isOysmServer;
    }

    private static void sendModelFile(ByteBuffer byteBuffer) {
        if (Minecraft.getInstance().player != null) {
            try {
                NetworkHandler.sendToServer(new C2SModelSyncPayload(byteBuffer));
                return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        Connection connection = serverConnection;
        if (connection == null || !connection.isConnected()) {
            return;
        }
        try {
            connection.send(NetworkHandler.toServerboundPacket(new C2SModelSyncPayload(byteBuffer)));
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public static synchronized void startSync(Connection connection, ByteBuffer byteBuffer) {
        if (connection == null) {
            YesSteveModel.LOGGER.warn("[SM] Ignoring model sync packet without a connection");
            return;
        }
        if (serverConnection != connection) {
            if (serverConnection != null) {
                YesSteveModel.LOGGER.info("[SM] Model sync connection changed; discarding the previous client sync session");
                resetClientState();
            }
            serverConnection = connection;
        }
        processServerData(byteBuffer);
    }

    public static void onSyncConnected() {
        if (((MinecraftAccessor) Minecraft.getInstance()).ysm$isLocalServer()) {
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

    private static void onSyncProgress(int totalModels) {
        if (totalModels == -1) {
            ((Executor) Minecraft.getInstance()).execute(() -> {
                syncState.setState(SyncState.PREPARING);
                forEachGuiWidget(IGuiWidget::onSyncError);
            });
        } else {
            ((Executor) Minecraft.getInstance()).execute(() -> {
                if (totalModels > 0) {
                    syncState.startSyncing(totalModels);
                } else {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> guiWidget.onSyncProgress(totalModels, 0));
            });
        }
    }

    private static void onModelPacksReceived(ModelPackData[] packDataArr) {
        Object2ReferenceOpenHashMap<String, ModelPackData> newPackMap = new Object2ReferenceOpenHashMap<>();

        for (ModelPackData packData : packDataArr) {
            if (StringUtils.isBlank(packData.getName())) {
                packData = new ModelPackData(packData.getPath(), FileTypeUtil.getFinalPathSegment(packData.getPath()), packData.getDescription(), packData.getTexture(), packData.getTranslations());
            }
            newPackMap.put(packData.getPath(), packData);
                OuterFileTexture iconTexture = packData.getTexture();
                if (iconTexture != null) {
                    Identifier location2 = FileTypeUtil.getPackIconLocation(packData.getPath());
                    ((Executor) Minecraft.getInstance()).execute(() -> {
                        iconTexture.doLoad();
                        ((MinecraftAccessor) Minecraft.getInstance()).ysm$getTextureManager().register(location2, iconTexture);
                    });
                }
            }

        for (ModelPackData packData : modelPackMap.values()) {
            if (!newPackMap.containsKey(packData.getPath()) && packData.getTexture() != null) {
                Identifier location = FileTypeUtil.getPackIconLocation(packData.getPath());
                ((Executor) Minecraft.getInstance()).execute(() -> ((MinecraftAccessor) Minecraft.getInstance()).ysm$getTextureManager().release(location));
            }
        }
        modelPackMap = newPackMap;
    }

    private static void onModelContextsUpdated(String[] removedModelIds, String[] previousModelIds, String[] updatedModelIds, boolean[] isModelReady) {
        ((Executor) Minecraft.getInstance()).execute(() -> {
            Object2ReferenceOpenHashMap<String, ModelAssembly> map = new Object2ReferenceOpenHashMap<>(modelAssemblyMap);
            if (removedModelIds != null) {
                ArrayList<Pair<String, ModelAssembly>> removed = new ArrayList<>(removedModelIds.length);
                for (String str : removedModelIds) {
                    String modelKey = canonicalRuntimeModelKey(str);
                    if (localOnlyModelIds.contains(modelKey)) {
                        continue;
                    }
                    if (sameRuntimeModelId(str, selectedLocalOnlyModelId)) {
                        clearSelectedLocalOnlyModel();
                    }
                    if (sameRuntimeModelId(str, selectedModelId)) {
                        clearSelectedModel();
                    }
                    ModelAssembly assembly = map.remove(modelKey);
                    if (assembly != null) {
                        removed.add(Pair.of(modelKey, assembly));
                    }
                }
                ((Executor) Minecraft.getInstance()).execute(() -> {
                    for (Pair<String, ModelAssembly> entry : removed) {
                        releaseModelAssembly(entry.getLeft(), entry.getRight());
                    }
                });
            }
            if (previousModelIds != null) {
                ModelAssembly[] modelAssemblies = new ModelAssembly[previousModelIds.length];
                for (int i = 0; i < previousModelIds.length; i++) {
                    String previousKey = canonicalRuntimeModelKey(previousModelIds[i]);
                    localOnlyModelIds.remove(previousKey);
                    if (sameRuntimeModelId(previousModelIds[i], selectedLocalOnlyModelId)) {
                        clearSelectedLocalOnlyModel();
                    }
                    if (sameRuntimeModelId(previousModelIds[i], selectedModelId)) {
                        selectedModelId = updatedModelIds[i];
                    }
                    modelAssemblies[i] = map.remove(previousKey);
                }
                for (int i = 0; i < modelAssemblies.length; i++) {
                    ModelAssembly modelAssembly = modelAssemblies[i];
                    if (modelAssembly != null) {
                        modelAssembly.getTextureRegistry().setAuthModel(isModelReady[i]);
                        map.put(canonicalRuntimeModelKey(updatedModelIds[i]), modelAssembly);
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

    private static void clearSelectedLocalOnlyModel() {
        selectedLocalOnlyModelId = null;
        selectedLocalOnlyTextureId = null;
    }

    private static void clearSelectedModel() {
        selectedModelId = null;
        selectedTextureId = null;
        clearSelectedLocalOnlyModel();
    }

    private static void onModelDataReceived(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) throws Exception {
        if (isPrimary) {
            pendingModelCallback = () -> {
                processModelData(parsedBundle, modelId, true, false);
            };
        } else {
            localOnlyModelIds.remove(canonicalRuntimeModelKey(modelId));
            runPendingModelCallback();
            processModelData(parsedBundle, modelId, false, isAuth);
        }
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

    private static boolean loadDirectoryModels(Path baseDir) throws IOException {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return false;
        }
        boolean[] loadedAny = new boolean[]{false};
        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(baseDir)) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    if (YSMFolderDeserializer.isModelFolder(dir)) {
                        String modelId = normalizeLocalModelId(baseDir.relativize(dir).toString());
                        loadLocalModelFolder(modelId, dir);
                        rememberLocalModelSource(baseDir, modelId, dir);
                        loadedAny[0] = true;
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[SM] Failed to load local model folder: {}", dir, e);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".ysm") && !lower.endsWith(".zip") && !lower.endsWith(".bbmodel")) {
                    return FileVisitResult.CONTINUE;
                }
                try {
                    String modelId = stripImportExtension(normalizeLocalModelId(baseDir.relativize(file).toString()));
                    byte[] data = Files.readAllBytes(file);
                    ModelMemoryProfiler.logBytes("local-folder-read", modelId, data);
                    RawYsmModel rawModel = parseImportModel(fileName, data);
                    data = null;
                    loadLocalModel(modelId, rawModel);
                    rememberLocalModelSource(baseDir, modelId, file);
                    loadedAny[0] = true;
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[SM] Failed to load local model file: {}", file, e);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return loadedAny[0];
    }

    private static void loadLocalModelFolder(String modelId, Path dir) throws Exception {
        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(dir)) {
            loadLocalModel(modelId, deserializer.deserialize());
        }
    }

    private static void loadLocalModel(String modelId, RawYsmModel rawModel) throws Exception {
        modelId = canonicalRuntimeModelKey(modelId);
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
        localOnlyModelIds.add(modelId);
        touchModel(modelId);
        runPendingModelCallback();
        if (!processModelData(parsedBundle, modelId, false, false)) {
            localOnlyModelIds.remove(modelId);
            throw new IllegalStateException("Failed to build local model");
        }
    }

    private static String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".ysm", ".zip", ".bbmodel"}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    private static String normalizeLocalModelId(String modelId) {
        return stripImportExtension(canonicalRuntimeModelKey(modelId));
    }

    private static String canonicalRuntimeModelKey(String modelId) {
        if (modelId == null) return null;
        return modelId.trim().replace('\\', '/').replaceAll("/+", "/").toLowerCase(Locale.ROOT);
    }

    private static boolean containsRuntimeModel(String modelId) {
        String modelKey = canonicalRuntimeModelKey(modelId);
        return modelKey != null && modelAssemblyMap.containsKey(modelKey);
    }

    private static boolean sameRuntimeModelId(String first, String second) {
        return Objects.equals(canonicalRuntimeModelKey(first), canonicalRuntimeModelKey(second));
    }

    private static void rememberLocalModelSource(Path baseDir, String modelId, Path source) {
        if (modelId == null || modelId.isBlank() || source == null) {
            return;
        }
        if (!samePath(baseDir, ServerModelManager.CUSTOM)) {
            return;
        }
        localModelSourcePaths.put(canonicalRuntimeModelKey(modelId), source.toAbsolutePath().normalize());
    }

    private static boolean samePath(Path a, Path b) {
        if (a == null || b == null) {
            return false;
        }
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
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

    public static boolean processModelData(@Nullable ClientModelInfo parsedBundle, String modelId, boolean isPrimary, boolean isAuth) {
        modelId = canonicalRuntimeModelKey(modelId);
        if (parsedBundle != null) {
            try {
                ModelMemoryProfiler.log("assembly-build-start", modelId);
                ModelAssembly runtimeModel = ModelAssemblyFactory.buildAssembly(parsedBundle, isPrimary, isAuth);
                ModelMemoryProfiler.log("assembly-build-finished", modelId);
                ResourceLifecycleStats.onModelAssemblyLoaded(modelId);
                pendingModelQueue.add(Pair.of(runtimeModel, modelId));
                touchModel(modelId);
                if (isPrimary) {
                    localModelContext = runtimeModel;

                    ((Executor) Minecraft.getInstance()).execute(() -> {
                        defaultTexture = UploadManager.getOrCreateLocatable(runtimeModel.getAnimationBundle().getTextures().getValueAt(0), true);
                    });
                    return true;
                }
            } catch (Exception e) {
                if (isPrimary) throw e;
                YesSteveModel.LOGGER.error(
                        new StringFormattedMessage("Failed to process {}", modelId), e);
                return false;
            }
        }
        ((Executor) Minecraft.getInstance()).execute(() -> {
            if (syncState.currentState == SyncState.SYNCING) {
                markSyncActivity();
                syncState.syncedModels++;
                int loaded = syncState.syncedModels;
                if (loaded == syncState.totalModels) {
                    syncState.finishSuccess();
                }
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onSyncProgress(syncState.getTotalModels(), loaded);
                });
            }
        });
        return parsedBundle != null;
    }

    private static void markSyncActivity() {
        lastSyncActivityMillis = System.currentTimeMillis();
    }

    private static void finishPendingModelLoad() {
        pendingModelsCount.updateAndGet(value -> Math.max(0, value - 1));
        markSyncActivity();
        scheduleSyncCompleteIfReady();
    }

    private static void scheduleSyncCompleteIfReady() {
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
        NetworkOnlineDebugLog.info("onSyncComplete: selectedModelId={} selectedTextureId={} assemblyMapSize={}",
                selectedModelId, selectedTextureId, modelAssemblyMap.size());
        syncStep = 1;
        cachedModelHashes.clear();
        lastSyncActivityMillis = 0L;
        syncManifestProcessed = false;

        ((Executor) Minecraft.getInstance()).execute(() -> {
            flushPendingModels();
            syncState.finishSuccess();
            NetworkOnlineDebugLog.info("onSyncComplete: calling resendSelectedServerModel");
            resendSelectedServerModel();
            forEachGuiWidget(IGuiWidget::onSyncComplete);
        });
    }

    public static void setAllowUpload(boolean allowUpload) {
        ClientModelManager.allowUpload = allowUpload;
    }

    public static void setOysmServer(boolean isOysmServer) {
        ClientModelManager.isOysmServer = isOysmServer;
    }

    private static void onSyncError(@Nullable Object obj) {
        lastSyncActivityMillis = 0L;
        ((Executor) Minecraft.getInstance()).execute(() -> {
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
                String modelKey = canonicalRuntimeModelKey(pairPoll.getRight());
                ModelAssembly previous = object2ReferenceOpenHashMap.put(modelKey, pairPoll.getLeft());
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
        if (assembly == null) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> releaseModelAssembly(modelId, assembly));
            return;
        }
        ResourceLifecycleStats.onModelAssemblyEvicted(modelId);
        AudioStreamCache.clearForModel(assembly);
        releaseAssemblyTextures(assembly);
        if (assembly.getProjectileModels() != null) {
            for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                if (entry.getValue().getModel() != null) entry.getValue().getModel().freeNativeCache();
            }
        }
        if (assembly.getVehicleModels() != null) {
            for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
                if (entry.getValue().getModel() != null) entry.getValue().getModel().freeNativeCache();
            }
        }
        if (assembly.getAnimationBundle() != null) {
            if (assembly.getAnimationBundle().getMainModel() != null) assembly.getAnimationBundle().getMainModel().freeNativeCache();
            if (assembly.getAnimationBundle().getArmModel() != null) assembly.getAnimationBundle().getArmModel().freeNativeCache();
        }
        if (assembly.getExpressionCache() != null) {
            for (AudioTrackData trackData : assembly.getExpressionCache().getSoundEffects().values()) {
                if (trackData != null) trackData.close();
            }
        }
        assembly.unloadRuntime();
        ModelMemoryProfiler.log("assembly-released", modelId);
    }

    public static void trimUnusedGpuCaches() {
        trimUnusedCpuModels();
        int maxCachedGpuModels = safeInt(GeneralConfig.MAX_CACHED_GPU_MODELS, 0);
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
        long ttlMillis = safeInt(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300) * 1000L;
        Set<String> protectedModels = collectProtectedModelIds(minecraft);
        modelAssemblyMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isRuntimeResident())
                .filter(entry -> canTrimGpuCache(entry.getKey(), protectedModels, now, ttlMillis))
                .sorted(Comparator.comparingLong(entry -> modelLastUsedAt.getOrDefault(entry.getKey(), 0L)))
                .limit(Math.max(1L, residentGpuModels - maxCachedGpuModels))
                .forEach(entry -> trimGpuCache(entry.getKey(), entry.getValue()));
    }

    private static void trimUnusedCpuModels() {
        Minecraft minecraft = Minecraft.getInstance();
        if (modelAssemblyMap.isEmpty()) return;
        long now = System.currentTimeMillis();
        long ttlMillis = safeInt(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300) * 1000L;
        Set<String> protectedModels = collectProtectedModelIds(minecraft);
        List<Map.Entry<String, ModelAssembly>> residents = modelAssemblyMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue().isRuntimeResident())
                .filter(entry -> !"default".equals(entry.getKey()) && !localOnlyModelIds.contains(entry.getKey()))
                .toList();
        long idleCount = residents.stream()
                .filter(entry -> !protectedModels.contains(entry.getKey()))
                .filter(entry -> now - modelLastUsedAt.getOrDefault(entry.getKey(), now) >= ttlMillis)
                .count();
        long overLimit = Math.max(0, residents.size() - safeInt(GeneralConfig.MAX_RESIDENT_CPU_MODELS, 24));
        long trimCount = Math.max(idleCount, overLimit);
        if (trimCount <= 0) return;
        residents.stream()
               .filter(entry -> !protectedModels.contains(entry.getKey()))
                .filter(entry -> {
                    long idleMillis = now - modelLastUsedAt.getOrDefault(entry.getKey(), now);
                    return idleMillis >= ttlMillis || (overLimit > 0 && idleMillis >= 1_000L);
                })
               .sorted(Comparator.comparingLong(entry -> modelLastUsedAt.getOrDefault(entry.getKey(), 0L)))
                .limit(trimCount)
                .forEach(entry -> unloadModelRuntime(entry.getKey(), entry.getValue()));
    }

    private static void unloadModelRuntime(String modelId, ModelAssembly assembly) {
        if (assembly == null || !assembly.isRuntimeResident()) return;
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(() -> unloadModelRuntime(modelId, assembly));
            return;
        }
        AudioStreamCache.clearForModel(assembly);
        if (assembly.getProjectileModels() != null) {
            for (ProjectileModelBundle bundle : assembly.getProjectileModels().values()) if (bundle.getModel() != null) bundle.getModel().freeNativeCache();
        }
        if (assembly.getVehicleModels() != null) {
            for (VehicleModelBundle bundle : assembly.getVehicleModels().values()) if (bundle.getModel() != null) bundle.getModel().freeNativeCache();
        }
        if (assembly.getAnimationBundle() != null) {
            if (assembly.getAnimationBundle().getMainModel() != null) assembly.getAnimationBundle().getMainModel().freeNativeCache();
            if (assembly.getAnimationBundle().getArmModel() != null) assembly.getAnimationBundle().getArmModel().freeNativeCache();
        }
        if (assembly.getExpressionCache() != null) {
            for (AudioTrackData trackData : assembly.getExpressionCache().getSoundEffects().values()) if (trackData != null) trackData.close();
        }
        releaseAssemblyTextures(assembly);
        assembly.unloadRuntime();
        gpuCacheTrimmedModels.remove(modelId);
        ModelMemoryProfiler.log("cpu-model-unloaded", modelId);
    }

    private static boolean canTrimGpuCache(String modelId, Set<String> protectedModels, long now, long ttlMillis) {
        modelId = canonicalRuntimeModelKey(modelId);
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
            touchAssembly(localModelContext);
        }
        if (minecraft.level != null) {
            for (Player player : minecraft.level.players()) {
                ModelInfoCapability.get(player).ifPresent(cap -> {
                    String modelId = cap.getModelId();
                    if (modelId != null && !modelId.isBlank()) {
                        protectedModels.add(canonicalRuntimeModelKey(modelId));
                        touchModel(modelId);
                    }
                });
            }
        }
        return protectedModels;
    }

    private static void trimGpuCache(String modelId, ModelAssembly assembly) {
        if (assembly == null || !assembly.isRuntimeResident() || !gpuCacheTrimmedModels.add(modelId)) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> trimGpuCache(modelId, assembly));
            return;
        }
        int releasedMeshes = 0;
        if (assembly.getProjectileModels() != null) {
            for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
                if (entry.getValue().getModel().freeGpuCache()) {
                    releasedMeshes++;
                }
            }
        }
        if (assembly.getVehicleModels() != null) {
            for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
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
            ModelMemoryProfiler.log("gpu-cache-trimmed meshes=" + releasedMeshes
                    + " liveMeshes=" + ResourceLifecycleStats.gpuMeshLiveCount()
                    + " liveBytes=" + ResourceLifecycleStats.gpuMeshLiveBytesEstimate(), modelId);
        }
    }

    private static void touchModel(String modelId) {
        String modelKey = canonicalRuntimeModelKey(modelId);
        if (modelKey != null && !modelKey.isBlank()) {
            modelLastUsedAt.put(modelKey, System.currentTimeMillis());
            gpuCacheTrimmedModels.remove(modelKey);
        }
    }

    public static void markModelUsed(String modelId) {
        touchModel(modelId);
    }

    public static boolean isGpuCacheTrimmed(String modelId) {
        String modelKey = canonicalRuntimeModelKey(modelId);
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

    private static int safeInt(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value == null ? fallback : value.get();
        } catch (IllegalStateException e) {
            return fallback;
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
            if (this.syncedModels < 0) {
                this.syncedModels = this.totalModels;
            }
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
                if (clientKey == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("(unavailable)"), "", "", 0));
                    }
                    return;
                }

                String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();

                if (!cacheDir.exists() || !cacheDir.isDirectory()) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("鐏忔碍婀悽鐔稿灇娴犺缍嶇紓鎾崇摠閹存牜绱︾€涙ɑ鏋冩禒璺恒仚娑撳秴鐡ㄩ崷? " + folder), "", "", 0));
                    }
                    return;
                }

                File[] files = cacheDir.listFiles();
                if (files == null || files.length == 0) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("(unavailable)"), "", "", 0));
                    }
                    return;
                }

                int successCount = 0;
                for (File file : files) {
                    if (!file.isFile()) continue;

                    try {
                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        byte[] clearText = YsmCrypt.read(fileBytes, clientKey);

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
                        callback.accept(new ExportResult(false, Component.literal("(unavailable)"), "", "", 0));
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Error during batch export", e);
                if (callback != null) {
                    callback.accept(new ExportResult(false, Component.literal("閹靛綊鍣虹€电厧鍤潻鍥┾柤閸欐垹鏁撴稉銉╁櫢闁挎瑨顕? " + e.getMessage()), "", "", 0));
                }
            }
        });
    }
}
