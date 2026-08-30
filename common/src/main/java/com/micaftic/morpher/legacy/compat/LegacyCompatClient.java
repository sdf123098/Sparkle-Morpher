package com.micaftic.morpher.legacy.compat;

import com.micaftic.morpher.client.LegacyModelCacheClient;
import com.micaftic.morpher.client.LegacyModelSyncClient;
import net.minecraft.network.Connection;

import java.nio.ByteBuffer;

/**
 * Compatibility-owned client sync facade. New client workflow code uses this
 * facade instead of the legacy packet/cache state machines.
 */
public final class LegacyCompatClient {
    private LegacyCompatClient() {
    }

    public static void resetSyncState() {
        LegacyModelSyncClient.compatResetSyncState();
    }

    public static void resetStep() {
        LegacyModelSyncClient.compatResetStep();
    }

    public static void clearCachedModelHashes() {
        LegacyModelCacheClient.compatClearCachedModelHashes();
    }

    public static void releaseAllInFlightBuffers() {
        LegacyModelCacheClient.compatReleaseAllInFlightBuffers();
    }

    public static void processServerData(ByteBuffer data) {
        LegacyModelSyncClient.compatProcessServerData(data);
    }

    public static void enqueueSync(Connection connection, ByteBuffer data) {
        LegacyModelSyncClient.compatEnqueueSync(connection, data);
    }

    public static byte[] clientKey() {
        return LegacyModelSyncClient.compatClientKey();
    }

    public static String currentCacheFolderName() {
        return LegacyModelSyncClient.compatCurrentCacheFolderName();
    }
}

