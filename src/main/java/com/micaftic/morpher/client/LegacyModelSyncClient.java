package com.micaftic.morpher.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.core.security.YsmCrypt;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SModelSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;

import java.nio.ByteBuffer;

/**
 * 客户端 legacy 同步协议（R7 剩余：Legacy sync 从 ClientModelManager 抽出）——
 * YSM 握手/密钥交换 step 状态机（syncStep 1 公钥 → 2 模型清单 → 3 缓存请求）与
 * 同步会话连接管理。
 *
 * <p>与 ClientModelManager 同包：握手包（handlePacket01）与发送入口在本类，
 * 模型清单/缓存包（handlePacket03/05，含后台 cache 校验）留在 CMM，经
 * {@code ClientModelManager.handlePacket03/handlePacket05}（包可见）调用。
 * 状态字段（serverKey/clientKey/currentCacheFolderName 等）供 CMM 的 cache 处理共享。
 */
public final class LegacyModelSyncClient {

    static int syncStep = 1;
    static byte[] key1;
    static byte[] lastKey;
    static byte[] serverKey;
    static byte[] clientKey;
    static String currentCacheFolderName;
    static volatile Connection serverConnection;

    private LegacyModelSyncClient() {
    }

    static void processServerData(ByteBuffer data) {
        if (data == null) {
            ClientModelManager.resetClientState();
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
                        ClientModelManager.handlePacket03(buf);
                    }
                }
            } else if (syncStep == 3) {
                decrypted = YsmCrypt.decrypt(packetBytes, key1);
                if (decrypted != null) {
                    try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                        ClientModelManager.handlePacket05(buf);
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
        ClientModelManager.onSyncProgress(-1); // Preparing GUI stage

        int garbageLen = 16 + ClientModelManager.SECURE_RANDOM.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        ClientModelManager.SECURE_RANDOM.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);
            outBuf.getRawBuf().writeByte(0x02);
            outBuf.getRawBuf().writeByte(0x00);

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), key1, true);
            lastKey = result.nextKey();

            sendModelFile(ByteBuffer.wrap(result.data()));
        }
    }

    static void sendModelFile(ByteBuffer byteBuffer) {
        if (PrivacyMode.isActive()) {
            return;
        }
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
        if (PrivacyMode.isActive()) {
            return;
        }
        if (connection == null) {
            YesSteveModel.LOGGER.warn("[SM] Ignoring model sync packet without a connection");
            return;
        }
        if (serverConnection != connection) {
            if (serverConnection != null) {
                YesSteveModel.LOGGER.info("[SM] Model sync connection changed; discarding the previous client sync session");
                ClientModelManager.resetClientState();
            }
            serverConnection = connection;
        }
        processServerData(byteBuffer);
    }

    /** 复位 legacy 同步会话状态（断线/换服/进入隐私模式时调用）。 */
    static void resetSyncState() {
        syncStep = 1;
        key1 = null;
        lastKey = null;
        serverKey = null;
        clientKey = null;
        currentCacheFolderName = null;
        serverConnection = null;
    }
}
