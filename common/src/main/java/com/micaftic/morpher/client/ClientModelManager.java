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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.*;
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

    private static final ThreadPoolExecutor modelPhraseExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r, "SM-Model-Parse-Thread");
                t.setDaemon(true);
                return t;
            }
    );

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
                System.out.println(Arrays.toString(decrypted));
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

        Map<UUID, File> localCacheMap = YSMClientCache.buildCacheIndex(cacheDir, clientKey);
        List<ModelHash> modelsToRequest = new ArrayList<>();

        int unkSize = buf.readVarInt();
        onSyncProgress(unkSize);

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
            validServerModelIds.add(modelId);

            File cachedFile = localCacheMap.get(ctx.uuid);
            boolean isFileValid = YSMClientCache.verifyFileContent(cachedFile, hash1, hash2, clientKey);

            boolean alreadyInMemory = modelAssemblyMap != null && modelAssemblyMap.containsKey(modelId);

            if (isFileValid) {
                YesSteveModel.LOGGER.info("[SM] Cache HIT & Validated: " + ctx.uuid);
                if (alreadyInMemory) {
                    previousModelIds.add(modelId);
                    updatedModelIds.add(modelId);
                    isModelReadyList.add(isAuth);
                } else {
                    // 閸涙垝鑵戠紓鎾崇摠
                    modelPhraseExecutor.submit(() -> {
                        if (clientKey == null) return;
                        try {
                            byte[] fileBytes = Files.readAllBytes(cachedFile.toPath());
                            ModelMemoryProfiler.logBytes("cache-read", modelId, fileBytes);
                            byte[] decompressed = YsmCrypt.read(fileBytes, clientKey);
                            ModelMemoryProfiler.logBytes("cache-decrypted", modelId, decompressed);
                            fileBytes = null;
                            parseAndLoadModel(decompressed, modelId, isAuth);
                            decompressed = null;
                            ModelMemoryProfiler.log("cache-parsed", modelId);
                        } catch (Exception e) {
                            YesSteveModel.LOGGER.error("[SM] Failed to parse and load cached model: " + modelId, e);
                        }
                    });
                }
            } else {
                YesSteveModel.LOGGER.info("[SM] Cache MISS or Invalid: " + ctx.uuid + " -> Requesting...");
                modelsToRequest.add(mHash);
            }
        }

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
                } else if (modelsToRequest.stream().anyMatch(h -> serverModels.containsKey(new UUID(h.hash1, h.hash2)) && serverModels.get(new UUID(h.hash1, h.hash2)).modelId.equals(loadedId))) {
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
        pendingModelsCount.set(modelsToRequest.size());

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

        if (pendingModelsCount.get() == 0) {
            modelPhraseExecutor.submit(() -> {
                YesSteveModel.LOGGER.info("[SM] All models loaded from local cache. Handshake complete!");
                onSyncComplete();
            });
        }
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

        // Initialize buffer on first reception
        if (ctx.fileBuffer == null) {
            ctx.fileBuffer = new byte[totalSize];
            ctx.totalSize = totalSize;
            ctx.bytesReceived = 0;
        }

        buf.getRawBuf().readBytes(ctx.fileBuffer, chunkOffset, chunkLength);
        ctx.bytesReceived += chunkLength;

        if (ctx.bytesReceived >= totalSize) {
            byte[] fileBuffer = ctx.fileBuffer;
            ctx.fileBuffer = null;

            modelPhraseExecutor.submit(() -> {
                if (clientKey == null) return;
                try {
                    String folder = currentCacheFolderName != null ? currentCacheFolderName : "default_cache";
                    File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    byte[] cachedFileData = YsmCrypt.transcodeServerDataToClientCache(fileBuffer, serverKey, clientKey, hash1, hash2);
                    ModelMemoryProfiler.logBytes("download-transcoded-cache", ctx.modelId, cachedFileData);

                    String legitFileName = YSMClientCache.generateCacheFileName(hash1, hash2, clientKey);
                    File outFile = new File(cacheDir, legitFileName);

                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        fos.write(cachedFileData);
                    }

                    YesSteveModel.LOGGER.info("[SM] Downloaded & Cached: " + outFile.getAbsolutePath());
                    byte[] decompressed = YsmCrypt.read(cachedFileData, clientKey);
                    ModelMemoryProfiler.logBytes("download-decrypted", ctx.modelId, decompressed);
                    cachedFileData = null;

                    parseAndLoadModel(decompressed, ctx.modelId, ctx.isAuth);
                    decompressed = null;
                    ModelMemoryProfiler.log("download-parsed", ctx.modelId);
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[SM] Failed to save/parse downloaded model: " + ctx.modelId, e);
                } finally {
                    if (pendingModelsCount.decrementAndGet() <= 0) {
                        YesSteveModel.LOGGER.info("[SM] All missing models downloaded and loaded successfully!");
                        onSyncComplete();
                    }
                }
            });
        }
    }


    private static void parseAndLoadModel(byte[] decompressed, String modelId, boolean isAuth) {
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
        syncStep = 1;
        key1 = null;
        lastKey = null;
        serverKey = null;
        clientKey = null;

        modelPhraseExecutor.getQueue().clear();

        currentCacheFolderName = null;
        pendingModelsCount.set(0);
        cachedModelHashes.clear();

        serverModels.clear();

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
        pendingModelCallback = null;
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
        ModelAssembly assembly = modelAssemblyMap.get(str);
        if (assembly != null) {
            touchModel(str);
        }
        return Optional.ofNullable(assembly);
    }

    public static boolean isLocalOnlyModel(String modelId) {
        return modelId != null && localOnlyModelIds.contains(modelId);
    }

    public static Optional<Path> getLocalModelSourcePath(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        Path path = localModelSourcePaths.get(modelId);
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
        return modelId != null && modelId.equals(selectedModelId) && isLocalOnlyModel(modelId);
    }

    public static void rememberSelectedModel(String modelId, String textureId) {
        selectedModelId = modelId;
        selectedTextureId = textureId;
        if (isLocalOnlyModel(modelId)) {
            selectedLocalOnlyModelId = modelId;
            selectedLocalOnlyTextureId = textureId;
        } else if (modelId != null && modelId.equals(selectedLocalOnlyModelId)) {
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
        if (modelId == null || (!isLocalOnlyModel(modelId) && !modelAssemblyMap.containsKey(modelId))) {
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
        if (!isLocalOnlyModel(modelId) && !modelAssemblyMap.containsKey(modelId)) {
            return;
        }

        // 5. 拷贝为 final 变量供 lambda 使用
        final String finalModelId = modelId;
        final String finalTextureId = textureId;

        // 6. 在渲染线程上应用
        ((Executor) Minecraft.getInstance()).execute(() -> {
            // 再次检查，防止在 execute 延迟期间选择已改变
            if (!finalModelId.equals(selectedModelId) && !isLocalOnlyModel(finalModelId) && !modelAssemblyMap.containsKey(finalModelId)) {
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
            if (!isLocalOnlyModel(modelId) && !modelAssemblyMap.containsKey(modelId)) {
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
        localOnlyModelIds.remove(modelId);
        localModelSourcePaths.remove(modelId);
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            PlayerCapability.get(player).ifPresent(cap -> {
                if (modelId.equals(cap.getModelId())) {
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
            if (!modelId.equals(currentModelId) || currentTextureId == null || isLocalOnlyModel(modelId) || !modelAssemblyMap.containsKey(modelId)) {
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
                localOnlyModelIds.remove(modelId);
                localModelSourcePaths.remove(modelId);
                if (modelId.equals(selectedLocalOnlyModelId)) {
                    clearSelectedLocalOnlyModel();
                }
                if (modelId.equals(selectedModelId)) {
                    clearSelectedModel();
                }
                modelLastUsedAt.remove(modelId);
                gpuCacheTrimmedModels.remove(modelId);
                ModelAssembly assembly = map.remove(modelId);
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
        byte[] importData = data;
        modelPhraseExecutor.submit(() -> {
            Component error = null;
            try {
                ModelMemoryProfiler.logBytes("local-import-read", modelId, importData);
                RawYsmModel rawModel = parseImportModel(fileName, importData);
                ModelMemoryProfiler.log("local-import-parsed", modelId);
                ClientModelInfo parsedBundle = YSMClientMapper.buildParsedBundle(rawModel, modelId);
                ModelMemoryProfiler.log("local-import-mapped", modelId);
                localOnlyModelIds.add(modelId);
                touchModel(modelId);
                runPendingModelCallback();
                if (!processModelData(parsedBundle, modelId, false, false)) {
                    localOnlyModelIds.remove(modelId);
                    throw new IllegalStateException("Failed to build local model");
                }
                Path persisted = persistImportedModel(modelId, fileName, importData);
                rememberLocalModelSource(ServerModelManager.CUSTOM, modelId, persisted);
                YesSteveModel.LOGGER.info("[SM] Imported local model: {}", modelId);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to import local model: {}", modelId, e);
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
        modelPhraseExecutor.submit(() -> {
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

    public static void resetSync() {
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
        if (!connection.isConnected()) {
            return;
        }
        try {
            connection.send(NetworkHandler.toServerboundPacket(new C2SModelSyncPayload(byteBuffer)));
        } catch (Exception e2) {
            e2.printStackTrace();
        }
    }

    public static void startSync(Connection connection, ByteBuffer byteBuffer) {
        serverConnection = connection;
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
                    if (localOnlyModelIds.contains(str)) {
                        continue;
                    }
                    if (str.equals(selectedLocalOnlyModelId)) {
                        clearSelectedLocalOnlyModel();
                    }
                    if (str.equals(selectedModelId)) {
                        clearSelectedModel();
                    }
                    ModelAssembly assembly = map.remove(str);
                    if (assembly != null) {
                        removed.add(Pair.of(str, assembly));
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
                    localOnlyModelIds.remove(previousModelIds[i]);
                    if (previousModelIds[i].equals(selectedLocalOnlyModelId)) {
                        clearSelectedLocalOnlyModel();
                    }
                    if (previousModelIds[i].equals(selectedModelId)) {
                        selectedModelId = updatedModelIds[i];
                    }
                    modelAssemblies[i] = map.remove(previousModelIds[i]);
                }
                for (int i = 0; i < modelAssemblies.length; i++) {
                    ModelAssembly modelAssembly = modelAssemblies[i];
                    if (modelAssembly != null) {
                        modelAssembly.getTextureRegistry().setAuthModel(isModelReady[i]);
                        map.put(updatedModelIds[i], modelAssembly);
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
            localOnlyModelIds.remove(modelId);
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
        return stripImportExtension(modelId.replace('\\', '/').toLowerCase(Locale.ROOT).replaceAll("/+", "/"));
    }

    private static void rememberLocalModelSource(Path baseDir, String modelId, Path source) {
        if (modelId == null || modelId.isBlank() || source == null) {
            return;
        }
        if (!samePath(baseDir, ServerModelManager.CUSTOM)) {
            return;
        }
        localModelSourcePaths.put(modelId, source.toAbsolutePath().normalize());
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
                syncState.syncedModels++;
                int loaded = syncState.syncedModels;
                if (loaded == syncState.totalModels) {
                    syncState.setState(SyncState.IDLE);
                }
                forEachGuiWidget(guiWidget -> {
                    guiWidget.onSyncProgress(syncState.getTotalModels(), loaded);
                });
            }
        });
        return parsedBundle != null;
    }

    private static void onSyncComplete() {
        NetworkOnlineDebugLog.info("onSyncComplete: selectedModelId={} selectedTextureId={} assemblyMapSize={}",
                selectedModelId, selectedTextureId, modelAssemblyMap.size());
        syncStep = 1;
        serverModels.clear();
        cachedModelHashes.clear();

        ((Executor) Minecraft.getInstance()).execute(() -> {
            syncState.setState(SyncState.IDLE);
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
        ((Executor) Minecraft.getInstance()).execute(() -> {
            syncState.setState(SyncState.IDLE);
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
                ModelAssembly previous = object2ReferenceOpenHashMap.put(pairPoll.getRight(), pairPoll.getLeft());
                touchModel(pairPoll.getRight());
                gpuCacheTrimmedModels.remove(pairPoll.getRight());
                if (previous != null && previous != pairPoll.getLeft()) {
                    releaseModelAssembly(pairPoll.getRight(), previous);
                }
            } else {
                modelAssemblyMap = object2ReferenceOpenHashMap;
                forEachGuiWidget(guiWidget -> guiWidget.onModelsUpdated(object2ReferenceOpenHashMap));
                return;
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
        for (AbstractTexture tex : assembly.getTextures()) {
            UploadManager.removeTexture(tex);
            if (tex instanceof OuterFileTexture outerFileTexture) {
                outerFileTexture.closeAndReleaseSource();
            } else {
                tex.close();
            }
        }
        for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
            entry.getValue().getModel().freeNativeCache();
        }
        for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
            entry.getValue().getModel().freeNativeCache();
        }
        assembly.getAnimationBundle().getMainModel().freeNativeCache();
        assembly.getAnimationBundle().getArmModel().freeNativeCache();
        for (AudioTrackData trackData : assembly.getExpressionCache().getSoundEffects().values()) {
            if (trackData != null) {
                trackData.close();
            }
        }
        ModelMemoryProfiler.log("assembly-released", modelId);
    }

    public static void trimUnusedGpuCaches() {
        int maxCachedGpuModels = safeInt(GeneralConfig.MAX_CACHED_GPU_MODELS, 0);
        if (maxCachedGpuModels <= 0 || modelAssemblyMap.size() <= maxCachedGpuModels) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (InputUtil.getCurrentScreen() != null) {
            return;
        }
        long now = System.currentTimeMillis();
        long ttlMillis = safeInt(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300) * 1000L;
        Set<String> protectedModels = collectProtectedModelIds(minecraft);
        modelAssemblyMap.entrySet().stream()
                .filter(entry -> canTrimGpuCache(entry.getKey(), protectedModels, now, ttlMillis))
                .sorted(Comparator.comparingLong(entry -> modelLastUsedAt.getOrDefault(entry.getKey(), 0L)))
                .limit(Math.max(1, modelAssemblyMap.size() - maxCachedGpuModels))
                .forEach(entry -> trimGpuCache(entry.getKey(), entry.getValue()));
    }

    private static boolean canTrimGpuCache(String modelId, Set<String> protectedModels, long now, long ttlMillis) {
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
                        protectedModels.add(modelId);
                        touchModel(modelId);
                    }
                });
            }
        }
        return protectedModels;
    }

    private static void trimGpuCache(String modelId, ModelAssembly assembly) {
        if (assembly == null || !gpuCacheTrimmedModels.add(modelId)) {
            return;
        }
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> trimGpuCache(modelId, assembly));
            return;
        }
        int releasedMeshes = 0;
        for (Map.Entry<Identifier, ProjectileModelBundle> entry : assembly.getProjectileModels().entrySet()) {
            if (entry.getValue().getModel().freeGpuCache()) {
                releasedMeshes++;
            }
        }
        for (Map.Entry<Identifier, VehicleModelBundle> entry : assembly.getVehicleModels().entrySet()) {
            if (entry.getValue().getModel().freeGpuCache()) {
                releasedMeshes++;
            }
        }
        if (assembly.getAnimationBundle().getMainModel().freeGpuCache()) {
            releasedMeshes++;
        }
        if (assembly.getAnimationBundle().getArmModel().freeGpuCache()) {
            releasedMeshes++;
        }
        if (releasedMeshes > 0) {
            ModelMemoryProfiler.log("gpu-cache-trimmed meshes=" + releasedMeshes
                    + " liveMeshes=" + ResourceLifecycleStats.gpuMeshLiveCount()
                    + " liveBytes=" + ResourceLifecycleStats.gpuMeshLiveBytesEstimate(), modelId);
        }
    }

    private static void touchModel(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            modelLastUsedAt.put(modelId, System.currentTimeMillis());
            gpuCacheTrimmedModels.remove(modelId);
        }
    }

    public static void markModelUsed(String modelId) {
        touchModel(modelId);
    }

    public static boolean isGpuCacheTrimmed(String modelId) {
        return modelId != null && gpuCacheTrimmedModels.contains(modelId);
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

        public SyncState getCurrentState() {
            return this.currentState;
        }

        public int getSyncedModels() {
            return this.syncedModels;
        }

        public int getTotalModels() {
            return this.totalModels;
        }

        public void setState(SyncState syncState) {
            System.out.println("Sync state: " + syncState);
            this.currentState = syncState;
            this.totalModels = -1;
            this.syncedModels = -1;
        }

        public void startSyncing(int totalModels) {
            this.currentState = SyncState.SYNCING;
            this.totalModels = totalModels;
            this.syncedModels = 0;
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
