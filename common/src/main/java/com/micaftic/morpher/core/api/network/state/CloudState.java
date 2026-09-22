package com.micaftic.morpher.core.api.network.state;

/** Shared Cloud connection state; it never selects the legacy Minecraft channel. */
public final class CloudState {

    private static volatile CloudConnectionSnapshot snapshot =
            new CloudConnectionSnapshot(CloudConnectionStatus.DISCONNECTED, CloudErrorCode.NONE, null, 0);

    private CloudState() {
    }

    public static boolean isAvailable() {
        return snapshot.isAvailable();
    }

    public static CloudConnectionSnapshot snapshot() {
        return snapshot;
    }

    public static synchronized void setStatus(
            CloudConnectionStatus status,
            CloudErrorCode error,
            String instanceId
    ) {
        snapshot = new CloudConnectionSnapshot(status, error, instanceId, snapshot.generation() + 1);
    }

    public static void setTransportAvailable(boolean value) {
        CloudConnectionSnapshot current = snapshot;
        setStatus(
                value ? CloudConnectionStatus.READY : CloudConnectionStatus.DISCONNECTED,
                CloudErrorCode.NONE,
                value ? current.instanceId() : null
        );
    }

    public static void reset() {
        setStatus(CloudConnectionStatus.DISCONNECTED, CloudErrorCode.NONE, null);
    }
}
