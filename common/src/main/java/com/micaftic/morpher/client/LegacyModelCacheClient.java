package com.micaftic.morpher.client;

import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.resource.models.ModelPackData;
import com.micaftic.morpher.resource.YSMClientMapper;
import com.micaftic.morpher.core.security.YSMClientCache;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.LegacySyncFlowControl;
import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.core.security.YSMByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客户端 legacy 模型缓存协议（R7 剩余③：Legacy cache 从 ClientModelManager 抽出）——
 * 服务器模型清单/缓存分片（handlePacket03/05）解析、后台缓存校验与落盘。
 *
 * <p>与 ClientModelManager 同包：模型解析（parseAndLoadModel）与加载管线（submitModelTask/
 * modelAssemblyMap 等）留在 CMM，经包可见成员调用。同步状态（syncStep/密钥）见
 * {@link LegacyModelSyncClient}。
 */
public final class LegacyModelCacheClient {

    private static final List<ModelHash> cachedModelHashes = new ArrayList<>();
    private static final AtomicLong inFlightBufferBytes = new AtomicLong();

    private LegacyModelCacheClient() {
    }

    /** Narrow entry points used only by the legacy-compat adapter. */
    public static void compatClearCachedModelHashes() {
        clearCachedModelHashes();
    }

    public static void compatReleaseAllInFlightBuffers() {
        releaseAllInFlightBuffers();
    }

    private record ModelHash(long hash1, long hash2) {
    }

    /**
     * 服务器清单中的单个模型及其对应的本地缓存文件。
     * 主线程只负责收集,文件内容校验全部在后台线程完成。
     */
    private record CacheVerificationEntry(ClientModelManager.ServerModelContext ctx, ModelHash hash, @Nullable File cachedFile) {
    }

    /** 清空已登记的服务端缓存哈希清单（断线/换服时）。 */
    static void clearCachedModelHashes() {
        cachedModelHashes.clear();
    }

    static void releaseAllInFlightBuffers() {
        for (ClientModelManager.ServerModelContext ctx : ClientModelManager.serverModels.values()) {
            synchronized (ctx) {
                releaseContextBuffer(ctx);
                ctx.transferTerminal = true;
            }
        }
    }

    static void handlePacket03(YSMByteBuf buf) throws Exception {
        buf.skipGarbageHeader();
        int type = buf.readVarInt(); // expect 3
        long folderHash = buf.readVarLong();
        LegacyModelSyncClient.currentCacheFolderName = Long.toHexString(folderHash);

        LegacyModelSyncClient.serverKey = new byte[56];
        buf.getRawBuf().readBytes(LegacyModelSyncClient.serverKey);

        LegacyModelSyncClient.clientKey = new byte[56];
        buf.getRawBuf().readBytes(LegacyModelSyncClient.clientKey);

        File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(LegacyModelSyncClient.currentCacheFolderName).toFile();
        if (!cacheDir.exists()) cacheDir.mkdirs();
        YSMClientCache.prepareCacheDirectory(cacheDir);

        // 仅列目录 + 文件名解密(轻量,无文件内容 IO);完整校验在后台线程完成
        Map<UUID, File> localCacheMap = YSMClientCache.buildCacheIndex(cacheDir, LegacyModelSyncClient.clientKey);
        List<CacheVerificationEntry> cacheEntries = new ArrayList<>();
        Set<UUID> expectedCacheModels = new HashSet<>();

        int unkSize = buf.readVarInt();
        ClientModelManager.onSyncProgress(unkSize);
        ClientModelManager.pendingModelsCount.set(0);
        ClientModelManager.failedSyncModelsCount.set(0);
        ClientModelManager.syncManifestProcessed = false;
        ClientModelManager.syncCompletionScheduled.set(false);

        Set<String> validServerModelIds = new HashSet<>();

        for (int i = 0; i < unkSize; i++) {
            long hash1 = buf.readVarLong();
            long hash2 = buf.readVarLong();
            ModelHash mHash = new ModelHash(hash1, hash2);
            cachedModelHashes.add(mHash);

            String modelId = buf.readString();
            boolean isAuth = buf.readVarInt() == 1;// isAuth
            int isCustomSkinModel = buf.readVarInt();// is default
            int version = buf.readVarInt();

            ClientModelManager.ServerModelContext ctx = new ClientModelManager.ServerModelContext(hash1, hash2, modelId, isAuth, isCustomSkinModel, version);
            ClientModelManager.serverModels.put(ctx.uuid, ctx);
            boolean firstCanonicalId = validServerModelIds.add(ctx.modelKey);
            expectedCacheModels.add(ctx.uuid);
            if (!firstCanonicalId) {
                YesSteveModel.LOGGER.warn("[SM] Ignoring duplicate server model id after case normalization: raw={} key={}", modelId, ctx.modelKey);
                ClientModelManager.markSyncModelProcessed();
                continue;
            }

            // 主线程只登记缓存路径与懒加载源(纯内存操作),绝不读取缓存文件内容。
            File cachedFile = localCacheMap.get(ctx.uuid);
            if (cachedFile != null) {
                ClientModelManager.cachedModelFiles.put(ctx.modelKey, cachedFile);
                ClientModelManager.registerRemoteLazySource(ctx.modelKey, cachedFile.toPath(), LegacyModelSyncClient.clientKey, ctx.isAuth);
            }
            cacheEntries.add(new CacheVerificationEntry(ctx, mHash, cachedFile));
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
            ClientModelManager.onModelPacksReceived(parsedPacks.toArray(new ModelPackData[0]));
        }

        // ---- 缓存校验全部移到后台线程:主线程不再对缓存文件做任何读盘+全文件哈希 ----
        // 原来的主线程同步校验(verifyFileContent = 全文件读取 + CityHash)会让进入服务器瞬间
        // 卡死(模型越多越大越明显)。现在主线程只解析包结构,校验/清理/决策在后台完成,
        // 结果回到主线程发送请求清单并收尾。
        final int taskGeneration = ClientModelManager.MODEL_TASK_GENERATION.get();
        ClientModelManager.submitModelTask(() -> {
            final List<ModelHash> modelsToRequest = new ArrayList<>();
            final List<String> previousModelIds = new ArrayList<>();
            final List<String> updatedModelIds = new ArrayList<>();
            final List<Boolean> isModelReadyList = new ArrayList<>();
            try {
                for (CacheVerificationEntry entry : cacheEntries) {
                    if (taskGeneration != ClientModelManager.MODEL_TASK_GENERATION.get()) {
                        return;
                    }
                    ClientModelManager.ServerModelContext ctx = entry.ctx;
                    File cachedFile = entry.cachedFile;
                    boolean isFileValid = cachedFile != null
                            // 传 LegacyModelSyncClient.clientKey 做解密+解压校验：仅校验 trailer 无法发现“trailer 合法但载荷是垃圾”的坏文件
                            // （服务端损坏数据经 transcode 后会重新计算合法 trailer），坏文件会被删除并重新请求。
                            && YSMClientCache.verifyFileContent(cachedFile, entry.hash.hash1, entry.hash.hash2, LegacyModelSyncClient.clientKey);
                    if (taskGeneration != ClientModelManager.MODEL_TASK_GENERATION.get()) {
                        return;
                    }

                    boolean alreadyInMemory = ClientModelManager.modelAssemblyMap != null && ClientModelManager.modelAssemblyMap.containsKey(ctx.modelKey);

                    if (isFileValid) {
                        YesSteveModel.LOGGER.info("[SM] Cache HIT & Validated: " + ctx.uuid);
                        if (alreadyInMemory) {
                            previousModelIds.add(ctx.modelKey);
                            updatedModelIds.add(ctx.modelKey);
                            isModelReadyList.add(ctx.isAuth);
                            ClientModelManager.markSyncModelProcessed();
                        } else if (ClientModelManager.isLazyModelLoading()) {
                            YesSteveModel.LOGGER.info("[SM] Deferred cached model until first use: {}", ctx.modelKey);
                            ClientModelManager.markSyncModelProcessed();
                        } else {
                            // 非懒加载模式:后台解析缓存文件
                            ClientModelManager.pendingModelsCount.incrementAndGet();
                            ClientModelManager.submitModelTask(() -> {
                                try {
                                    if (LegacyModelSyncClient.clientKey == null) return;
                                    byte[] fileBytes = Files.readAllBytes(cachedFile.toPath());
                                    ModelMemoryProfiler.logBytes("cache-read", ctx.modelId, fileBytes);
                                    byte[] decompressed = YsmCrypt.readInPlace(fileBytes, LegacyModelSyncClient.clientKey);
                                    ModelMemoryProfiler.logBytes("cache-decrypted", ctx.modelId, decompressed);
                                    fileBytes = null;
                                    ClientModelManager.parseAndLoadModel(decompressed, ctx.modelKey, ctx.isAuth);
                                    decompressed = null;
                                    ModelMemoryProfiler.log("cache-parsed", ctx.modelId);
                                } catch (Exception e) {
                                    YesSteveModel.LOGGER.error("[SM] Failed to parse and load cached model: " + ctx.modelId, e);
                                    YSMClientCache.deleteCacheFile(cachedFile);
                                } finally {
                                    ClientModelManager.finishPendingModelLoad();
                                }
                            });
                        }
                    } else {
                        YesSteveModel.LOGGER.info("[SM] Cache MISS or Invalid: " + ctx.uuid + " -> Requesting...");
                        if (cachedFile != null) {
                            YSMClientCache.deleteCacheFile(cachedFile);
                        }
                        modelsToRequest.add(entry.hash);
                        ClientModelManager.pendingModelsCount.incrementAndGet();
                    }
                    ClientModelManager.markSyncActivity();
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to verify cached models on background thread", e);
            }
            try {
                YSMClientCache.cleanupCacheDirectory(cacheDir, expectedCacheModels, LegacyModelSyncClient.clientKey);
            } catch (Exception e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to cleanup cache directory", e);
            }
            ClientModelManager.markSyncActivity();

            // 回到主线程:发送请求清单 + 清理过期模型 + 标记清单处理完成
            ((Executor) Minecraft.getInstance()).execute(() -> {
                if (taskGeneration != ClientModelManager.MODEL_TASK_GENERATION.get()) {
                    return;
                }
                try {
                List<String> modelsToRemove = new ArrayList<>();
                    if (ClientModelManager.modelAssemblyMap != null) {
                        for (String loadedId : ClientModelManager.modelAssemblyMap.keySet()) {
                            if ("default".equals(loadedId)) continue;

                            if (!validServerModelIds.contains(loadedId)) {
                                modelsToRemove.add(loadedId);
                            } else if (modelsToRequest.stream().anyMatch(h -> ClientModelManager.serverModels.containsKey(new UUID(h.hash1, h.hash2)) && ClientModelManager.serverModels.get(new UUID(h.hash1, h.hash2)).modelKey.equals(loadedId))) {
                                modelsToRemove.add(loadedId);
                            }
                        }
                    }

                    if (!modelsToRemove.isEmpty() || !previousModelIds.isEmpty()) {
                        boolean[] readyArr = new boolean[isModelReadyList.size()];
                        for (int j = 0; j < isModelReadyList.size(); j++) {
                            readyArr[j] = isModelReadyList.get(j);
                        }
                        ClientModelManager.onModelContextsUpdated(
                                modelsToRemove.isEmpty() ? null : modelsToRemove.toArray(new String[0]),
                                previousModelIds.isEmpty() ? null : previousModelIds.toArray(new String[0]),
                                updatedModelIds.isEmpty() ? null : updatedModelIds.toArray(new String[0]),
                                readyArr
                        );
                        YesSteveModel.LOGGER.info("[SM] Cleaned up {} outdated models and updated {} existing models during sync.", modelsToRemove.size(), previousModelIds.size());
                    }

                    LegacyModelSyncClient.syncStep = 3;
                    ClientModelManager.markSyncActivity();

                    int garbageLen = 16 + ClientModelManager.SECURE_RANDOM.nextInt(48);
                    byte[] garbage = new byte[garbageLen];
                    ClientModelManager.SECURE_RANDOM.nextBytes(garbage);

                    try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                        outBuf.writeGarbageHeader(garbageLen, garbage);
                        outBuf.getRawBuf().writeByte(0x04);

                        outBuf.writeVarInt(modelsToRequest.size());
                        for (ModelHash h : modelsToRequest) {
                            outBuf.writeVarLong(h.hash1);
                            outBuf.writeVarLong(h.hash2);
                        }

                        YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), LegacyModelSyncClient.key1, false);
                        LegacyModelSyncClient.sendModelFile(ByteBuffer.wrap(result.data()));
                    }

                ClientModelManager.syncManifestProcessed = true;
                ClientModelManager.scheduleSyncCompleteIfReady();
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("[SM] Failed to finish model sync on main thread", e);
                }
            });
        });
    }

    static void handlePacket05(YSMByteBuf buf) throws Exception {
        buf.skipGarbageHeader();
        int type = buf.readVarInt();
        if (type != 5 && type != 6) return;

        long hash1 = buf.readVarLong();
        long hash2 = buf.readVarLong();
        UUID uuid = new UUID(hash1, hash2);

        ClientModelManager.ServerModelContext ctx = ClientModelManager.serverModels.get(uuid);
        if (ctx == null) {
            YesSteveModel.LOGGER.warn("[SM] Received unexpected file chunk for model: " + uuid);
            return;
        }

        if (type == 6) {
            String reason = buf.readString();
            synchronized (ctx) {
                if (ctx.transferTerminal) return;
                ctx.transferTerminal = true;
                releaseContextBuffer(ctx);
            }
            YesSteveModel.LOGGER.warn("[SM] Server could not transfer model {}: {}", ctx.modelKey, reason);
            ClientModelManager.finishPendingModelFailure();
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
        byte[] completedBuffer = null;
        long completedReservation = 0L;
        synchronized (ctx) {
            if (ctx.transferTerminal) return;
            if (ctx.fileBuffer == null) {
                if (!LegacySyncFlowControl.tryReserve(inFlightBufferBytes, totalSize)) {
                    throw new IllegalStateException("SPM aggregate model receive budget exceeded");
                }
                ctx.fileBufferReserved = true;
                try {
                    ctx.fileBuffer = new byte[totalSize];
                } catch (OutOfMemoryError error) {
                    releaseContextBuffer(ctx);
                    throw error;
                }
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
            if (ctx.bytesReceived >= totalSize) {
                completedBuffer = ctx.fileBuffer;
                ctx.fileBuffer = null;
                ctx.transferTerminal = true;
                if (ctx.fileBufferReserved) {
                    completedReservation = ctx.totalSize;
                    ctx.fileBufferReserved = false;
                }
            }
        }
        ClientModelManager.markSyncActivity();

        if (completedBuffer != null) {
            byte[] fileBuffer = completedBuffer;

            boolean succeeded = false;
            try {
                    if (LegacyModelSyncClient.clientKey == null) return;
                    // 落盘前校验服务端原始缓存数据：服务端缓存文件损坏（旧版本非原子写）时，
                    // transcode 会把垃圾数据重新打包成带合法 trailer 的客户端缓存文件，
                    // 之后所有校验都通过但模型永远解析失败。此处拒绝缓存坏数据，下轮同步重请求。
                    if (!YsmCrypt.verifyServerCache(fileBuffer, hash1, hash2)) {
                        YesSteveModel.LOGGER.warn("[SM] Server sent corrupt cache data for model {} (hash mismatch); refusing to cache, will re-request on next sync", ctx.modelKey);
                        return;
                    }
                    String folder = LegacyModelSyncClient.currentCacheFolderName != null ? LegacyModelSyncClient.currentCacheFolderName : "default_cache";
                    File cacheDir = ServerModelManager.CACHE_CLIENT.resolve(folder).toFile();
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    byte[] cachedFileData = YsmCrypt.transcodeServerDataToClientCache(fileBuffer, LegacyModelSyncClient.serverKey, LegacyModelSyncClient.clientKey, hash1, hash2);
                    ModelMemoryProfiler.logBytes("download-transcoded-cache", ctx.modelId, cachedFileData);

                    String legitFileName = YSMClientCache.generateCacheFileName(hash1, hash2, LegacyModelSyncClient.clientKey);
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
                    ClientModelManager.cachedModelFiles.put(ctx.modelKey, outFile);
                    ClientModelManager.registerRemoteLazySource(ctx.modelKey, outFile.toPath(), LegacyModelSyncClient.clientKey, ctx.isAuth);
                    if (ClientModelManager.isLazyModelLoading()) {
                        YesSteveModel.LOGGER.info("[SM] Deferred downloaded model until first use: {}", ctx.modelKey);
                    } else {
                        byte[] decompressed = YsmCrypt.readInPlace(cachedFileData, LegacyModelSyncClient.clientKey);
                        ModelMemoryProfiler.logBytes("download-decrypted", ctx.modelId, decompressed);
                        cachedFileData = null;
                        ClientModelManager.parseAndLoadModel(decompressed, ctx.modelKey, ctx.isAuth);
                        decompressed = null;
                        ModelMemoryProfiler.log("download-parsed", ctx.modelId);
                    }
                    succeeded = true;
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to save/parse downloaded model: " + ctx.modelId, e);
            } finally {
                LegacySyncFlowControl.release(inFlightBufferBytes, completedReservation);
                if (succeeded) {
                    ClientModelManager.finishPendingModelLoad();
                } else {
                    ClientModelManager.finishPendingModelFailure();
                }
            }
        }
    }

    private static void releaseContextBuffer(ClientModelManager.ServerModelContext ctx) {
        ctx.fileBuffer = null;
        ctx.bytesReceived = 0;
        if (ctx.fileBufferReserved) {
            LegacySyncFlowControl.release(inFlightBufferBytes, ctx.totalSize);
            ctx.fileBufferReserved = false;
        }
        ctx.totalSize = 0;
    }


}
