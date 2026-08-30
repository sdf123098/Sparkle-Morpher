package com.micaftic.morpher.legacy.compat;

import com.micaftic.morpher.model.LegacyModelSyncProtocol;
import com.micaftic.morpher.network.protocol.LegacyModelProtocol;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Single compatibility boundary for the 1.2.x legacy model network.
 *
 * <p>New core code may call this adapter during the 1.3.x migration window,
 * but must not import legacy packets, registration or sync implementations.
 * This whole class is scheduled for removal with the rest of Legacy in 1.4.0.</p>
 */
public final class LegacyCompatNetwork {
    private LegacyCompatNetwork() {
    }

    public static void register() {
        LegacyModelProtocol.register();
    }

    public static void sendModelData(UUID playerId, ByteBuffer data) {
        LegacyModelSyncProtocol.nativeSendModelData(playerId, data);
    }

    public static void syncModels(UUID[] playerIds, String[] playerNames, String[] modelIds, Object callback) {
        LegacyModelSyncProtocol.nativeSyncModels(playerIds, playerNames, modelIds, callback);
    }

    public static void clearPlayerSyncState(UUID playerId) {
        LegacyModelSyncProtocol.clearPlayerSyncState(playerId);
    }
}
