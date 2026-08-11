package com.micaftic.morpher.model;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.architectury.utils.GameInstance;
import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.mixin.ConnectionAccessor;
import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import com.micaftic.morpher.model.format.ServerModelData;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.S2CModelSyncPayload;
import com.micaftic.morpher.util.YSMThreadPool;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Legacy 模型同步协议（R8 遗留③：从 ServerModelManager 拆出）——
 * YSM 加密握手 + 模型清单/缓存分片下发 的 step 状态机（step 1 pong → 2 模型清单 → 3 缓存请求）。
 *
 * <p>与 ServerModelManager 同包：直接访问其包内可见的目录/包/限流/缓存重建依赖，
 * 保持行为与旧版本一致。发送 helper（sendModelData/sendPacketToPlayer/sendPacketReliably）
 * 一并迁入本类。
 */
public final class LegacyModelSyncProtocol {

    private static final Map<UUID, PlayerSyncState> syncStates = new ConcurrentHashMap<>();

    private LegacyModelSyncProtocol() {
    }

    static class PlayerSyncState {
        byte[] clientKey = new byte[56];
        byte[] key1;
        byte[] clientNextKey;
        int step = 0;
        List<ServerModelData> allowedModels = new ArrayList<>();
        Connection connection;

        // TODO: 未来可基于UUID持久化，这里目前每次加入生成固定clientKey
        PlayerSyncState() {new Random(114514).nextBytes(clientKey);}
    }

    public static void nativeSyncModels(UUID[] uuids, String[] playerNames, String[] modelIds, Object callback) {
        ServerModelManager.initRateLimit();
        YSMThreadPool.submitSync(() -> {
            try {
                MinecraftServer currentServer = GameInstance.getServer();
                if (currentServer == null) return;

                for (UUID uuid : uuids) {
                    PlayerSyncState candidate = new PlayerSyncState();
                    candidate.allowedModels.addAll(ServerModelManager.CATALOG.all().values());
                    candidate.step = 1;
                    candidate.connection = getPlayerConnection(uuid);
                    PlayerSyncState state = syncStates.compute(uuid, (key, existing) -> {
                        if (existing != null && existing.step > 0 && existing.step < 3
                                && candidate.connection != null && existing.connection == candidate.connection) {
                            return existing;
                        }
                        return candidate;
                    });
                    if (state != candidate) {
                        YesSteveModel.LOGGER.debug("[SM] Model sync already in progress for {}; duplicate start ignored", uuid);
                        continue;
                    }

                    // HandshakePing
//                    byte[] garbage = new byte[16 + SECURE_RANDOM_S.nextInt(48)];
//                    SECURE_RANDOM_S.nextBytes(garbage);
//                    byte[] payload = new byte[2 + garbage.length + 1];
//                    payload[0] = (byte)(garbage.length & 0xFF);
//                    payload[1] = (byte)((garbage.length >> 8) & 0xFF);
//                    System.arraycopy(garbage, 0, payload, 2, garbage.length);
//                    payload[2 + garbage.length] = 0x01;
//
//                    var result = YsmCrypt.encrypt(payload, K0_SERVER, true);
//                    state.key1 = result.nextKey();
//
//                    sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
                    int garbageLen = 16 + ServerModelManager.theRandom.nextInt(48);
                    byte[] garbage = new byte[garbageLen];
                    ServerModelManager.theRandom.nextBytes(garbage);

                    try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                        outBuf.writeGarbageHeader(garbageLen, garbage);
                        outBuf.writeByte((byte) 0x01);
                        YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), YsmCrypt.publicKey, true);
                        state.key1 = result.nextKey();

                        sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
                    }
                }
//                if (callback != null) onAuthDataReceived(null, callback);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Sync initiation failed", e);
            }
        });
    }

    public static void nativeSendModelData(UUID uuid, @Nullable ByteBuffer data) {
        if (data != null && !data.hasRemaining() && data.position() > 0) {
            data.flip();
        }

        if (data == null || data.remaining() == 0) {
            syncStates.remove(uuid);
            return;
        }

        PlayerSyncState state = syncStates.get(uuid);
        if (state == null) return;

        try {
            byte[] packetBytes = new byte[data.remaining()];
            data.get(packetBytes);
            System.out.println("Server Handle packet, step=" + state.step + ", length=" + packetBytes.length);

            if (state.step == 1) {
                // 等待Pong
                byte[] decrypted = YsmCrypt.decrypt(packetBytes, state.key1);
                if (decrypted == null || decrypted.length < 56) return;

                // 客戶端生成的密鑰
                state.clientNextKey = Arrays.copyOfRange(decrypted, decrypted.length - 56, decrypted.length);
                byte[] payload = Arrays.copyOfRange(decrypted, 0, decrypted.length - 56);

                try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(payload))) {
                    buf.skipGarbageHeader();
                    if (buf.getRawBuf().readByte() != 0x02) return;
                }

                // 發送可用模型
                state.step = 2;
                sendPacket03(uuid, state);
            } else if (state.step == 2) {
                byte[] decrypted = YsmCrypt.decrypt(packetBytes, state.key1);
                if (decrypted == null) return;

                try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                    buf.skipGarbageHeader();
                    if (buf.getRawBuf().readByte() != 0x04) return;

                    int numRequests = buf.readVarInt();
                    List<long[]> requestedHashes = new ArrayList<>();
                    for (int i = 0; i < numRequests; i++) {
                        requestedHashes.add(new long[]{buf.readVarLong(), buf.readVarLong()});
                    }
                    state.step = 3;
                    sendPacket05(uuid, state, requestedHashes);
                }
            }
        } catch (Exception e) {
            syncStates.remove(uuid, state);
            YesSteveModel.LOGGER.warn("[SM] Discarded invalid model sync session for {} at step {}: {}",
                    uuid, state.step, e.getMessage());
        }
    }

    private static void sendPacket03(UUID uuid, PlayerSyncState state) {
        int garbageLen = 16 + ServerModelManager.theRandom.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        ServerModelManager.theRandom.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);

            outBuf.writeVarInt(3); // Type
            outBuf.writeVarLong(0L); // 這個決定了cache資料夾的名稱

            outBuf.getRawBuf().writeBytes(ServerModelManager.serverKey);
            outBuf.getRawBuf().writeBytes(state.clientKey);

            outBuf.writeVarInt(state.allowedModels.size());
            for (ServerModelData model : state.allowedModels) {
                String sha256 = model.getLoadedModelData().getModelHash();
                long[] hashes = YsmCrypt.calculateModelHashes(sha256, ServerModelManager.serverKey);
                outBuf.writeVarLong(hashes[0]);
                outBuf.writeVarLong(hashes[1]);
                outBuf.writeString(model.getModelId());
                outBuf.writeVarInt(model.isAuth() ? 1 : 0);
                outBuf.writeVarInt(model.isCustomSkinModel() ? 1 : 0);
                outBuf.writeVarInt(32); // format
            }

            outBuf.writeVarInt(ServerModelManager.packs.size());
            for (ServerPackData pack : ServerModelManager.packs.values()) {
                outBuf.writeString(pack.folderPath);

                // 寫入圖標資訊
                if (pack.iconData != null) {
                    outBuf.writeVarInt(1);
                    outBuf.writeByteArray(pack.iconData);
                    outBuf.writeVarInt(pack.iconWidth);
                    outBuf.writeVarInt(pack.iconHeight);
                    outBuf.writeVarInt(pack.iconFormat);
                    outBuf.writeVarInt(1); // unkImageData
                } else {
                    outBuf.writeVarInt(0);
                }

                // 寫入基礎資訊
                if (pack.name != null || pack.description != null) {
                    outBuf.writeVarInt(1);
                    outBuf.writeString(pack.name != null ? pack.name : "");
                    outBuf.writeString(pack.description != null ? pack.description : "");
                } else {
                    outBuf.writeVarInt(0);
                }

                // 寫入語言本地化
                if (pack.lang != null && !pack.lang.isEmpty()) {
                    outBuf.writeVarInt(pack.lang.size());
                    for (Map.Entry<String, Map<String, String>> langEntry : pack.lang.entrySet()) {
                        outBuf.writeString(langEntry.getKey());
                        outBuf.writeVarInt(langEntry.getValue().size());
                        for (Map.Entry<String, String> kv : langEntry.getValue().entrySet()) {
                            outBuf.writeString(kv.getKey());
                            outBuf.writeString(kv.getValue());
                        }
                    }
                } else {
                    outBuf.writeVarInt(0);
                }
            }

            outBuf.writeVarInt(0);  // \0

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.clientNextKey, false);
            sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendPacket05(UUID uuid, PlayerSyncState state, List<long[]> requestedHashes) {
        YSMThreadPool.submitSync(() -> {
            try {
                ServerModelManager.threadLimiter.acquire();

                PendingTransfer transfer = new PendingTransfer();

                for (long[] hashes : requestedHashes) {
                    long hash1 = hashes[0];
                    long hash2 = hashes[1];
                    String fileName = String.format("%016x%016x", hash1, hash2);
                    Path file = ServerModelManager.CACHE_SERVER.resolve(fileName);

                    // 发送前校验缓存完整性：旧版本非原子写/读写竞争可能留下损坏文件，
                    // 直接发送会让客户端缓存坏数据并永久加载失败。损坏/缺失时尝试从源模型即时重建。
                    byte[] fileData = null;
                    if (Files.exists(file)) {
                        fileData = Files.readAllBytes(file);
                        if (!YsmCrypt.verifyServerCache(fileData, hash1, hash2)) {
                            YesSteveModel.LOGGER.warn("[SM] Corrupt server cache file {}; deleting and rebuilding from source model", fileName);
                            try { Files.deleteIfExists(file); } catch (Exception ignored) {}
                            fileData = null;
                        }
                    }
                    if (fileData == null) {
                        if (ServerModelManager.rebuildServerCacheByHashes(hash1, hash2) && Files.exists(file)) {
                            fileData = Files.readAllBytes(file);
                            if (!YsmCrypt.verifyServerCache(fileData, hash1, hash2)) fileData = null;
                        }
                        if (fileData == null) {
                            YesSteveModel.LOGGER.warn("[SM] Model cache missing or corrupt for {}; skipping transfer, client will re-request on next sync", fileName);
                            continue;
                        }
                    }

                    int totalSize = fileData.length;
                    int maxChunkSize = 30720;
                    int chunkCount = (totalSize + maxChunkSize - 1) / maxChunkSize;
                    int chunkSize = (totalSize + chunkCount - 1) / chunkCount;

                    int offset = 0;

                    while (offset < totalSize) {
                        if (!isCurrentSyncSession(uuid, state)) {
                            YesSteveModel.LOGGER.debug("[SM] Stopped stale model sync transfer for {}", uuid);
                            return;
                        }
                        int length = Math.min(chunkSize, totalSize - offset);

                        int garbageLen = 16 + ServerModelManager.theRandom.nextInt(48);
                        byte[] garbage = new byte[garbageLen];
                        ServerModelManager.theRandom.nextBytes(garbage);

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeGarbageHeader(garbageLen, garbage);
                            outBuf.writeVarInt(5); // Type
                            outBuf.writeVarLong(hash1);
                            outBuf.writeVarLong(hash2);
                            outBuf.writeVarInt(totalSize);
                            outBuf.writeVarInt(offset);
                            outBuf.writeVarInt(length);
                            outBuf.getRawBuf().writeBytes(fileData, offset, length);
                            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.key1, false);

                            ServerModelManager.acquireGlobalBandwidth(result.data().length);


                            // Stream chunks
                            boolean success = sendModelData(uuid, ByteBuffer.wrap(result.data()), transfer);
                            if (success) {
                                offset += length;
                            } else {
                                if (!isCurrentSyncSession(uuid, state)) return;
                                try { Thread.sleep(5); } catch (InterruptedException e) {}
                            }
                        }
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("Failed to send model chunks to " + uuid, e);
            } finally {
                ServerModelManager.threadLimiter.release();
            }
        });
    }

    public static void clearPlayerSyncState(UUID uuid) {
        if (uuid != null) {
            syncStates.remove(uuid);
        }
    }

    private static boolean isCurrentSyncSession(UUID uuid, PlayerSyncState state) {
        if (uuid == null || state == null || syncStates.get(uuid) != state || state.connection == null) {
            return false;
        }
        Connection connection = getPlayerConnection(uuid);
        return connection == state.connection && connection.isConnected();
    }

    private static Connection getPlayerConnection(UUID uuid) {
        ServerPlayer player;
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer == null || (player = currentServer.getPlayerList().getPlayer(uuid)) == null) {
            return null;
        }
        ServerGamePacketListenerImpl serverGamePacketListenerImpl = player.connection;
        if (!serverGamePacketListenerImpl.isAcceptingMessages() || !serverGamePacketListenerImpl.getClass().equals(ServerGamePacketListenerImpl.class)) {
            return null;
        }
        return ((ServerCommonPacketListenerImplAccessor) serverGamePacketListenerImpl).ysm$getConnection();
    }

    private static boolean sendModelData(UUID uuid, ByteBuffer byteBuffer, PendingTransfer pendingTransfer) {
        Connection connection = getPlayerConnection(uuid);
        if (connection != null) {
            return sendPacketReliably(connection, NetworkHandler.toClientboundPacket(new S2CModelSyncPayload(byteBuffer)), pendingTransfer);
        }
        return false;
    }

    private static Object createModelPacket(ByteBuffer byteBuffer) {
        return NetworkHandler.toClientboundPacket(new S2CModelSyncPayload(byteBuffer));
    }

    private static boolean sendPacketToPlayer(UUID uuid, Object obj, PendingTransfer pendingTransfer) {
        Connection connection = getPlayerConnection(uuid);
        if (connection != null) {
            return sendPacketReliably(connection, obj, pendingTransfer);
        }
        return false;
    }

    private static boolean sendPacketReliably(Connection connection, Object obj, PendingTransfer pendingTransfer) {
        if (!pendingTransfer.hasStarted) {
            pendingTransfer.hasStarted = true;
            pendingTransfer.pendingBytes = ((ConnectionAccessor) connection).ysm$getChannel().unsafe().outboundBuffer().totalPendingWriteBytes() + 65536;
        }

        while (connection.isConnected()) {
            if (((ConnectionAccessor) connection).ysm$getChannel().unsafe().outboundBuffer().size() > pendingTransfer.pendingBytes) {
                if (!YSMThreadPool.sleepMillis(10)) {
                    return false;
                }
            } else {
                try {
                    connection.send((Packet<?>) obj);
                    return true;
                } catch (Throwable th) {
                    th.printStackTrace();
                    return false;
                }
            }
        }
        return false;
    }

    private static class PendingTransfer {
        public long pendingBytes;

        public boolean hasStarted = false;

        private PendingTransfer() {
        }
    }
}
