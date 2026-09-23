package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.cloud.client.CloudClientRuntime;
import com.micaftic.morpher.cloud.client.CloudSession;
import com.micaftic.morpher.core.api.network.state.CloudState;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

import java.nio.file.Path;
import java.util.Objects;

/** Process-local Cloud client wiring; credentials remain in memory only. */
public final class CloudUploadRuntime {
    private static volatile ModelUploadTransport transport;

    private CloudUploadRuntime() {
    }

    public static synchronized void configure(CloudInstanceConfig instance, CloudSession session) {
        configure(instance, session, CloudClientRuntime.defaultCacheRoot(), "2.0.0");
    }

    public static synchronized void configure(
            CloudInstanceConfig instance,
            CloudSession session,
            Path cacheRoot,
            String clientVersion
    ) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(session, "session");
        CloudClientRuntime.configure(instance, session, cacheRoot, clientVersion);
        transport = CloudClientRuntime.uploadTransport();
    }

    public static ModelUploadTransport transport() {
        return CloudClientRuntime.isConfigured() ? transport : null;
    }

    public static boolean isConfigured() {
        return transport != null && CloudState.isAvailable();
    }

    public static synchronized void clear() {
        transport = null;
        CloudClientRuntime.clear();
    }
}
