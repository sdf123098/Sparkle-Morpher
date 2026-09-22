package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import com.micaftic.morpher.cloud.client.CloudAssetClient;
import com.micaftic.morpher.cloud.client.CloudAuthClient;
import com.micaftic.morpher.cloud.client.CloudHttpClient;
import com.micaftic.morpher.cloud.client.CloudSession;
import com.micaftic.morpher.core.api.network.state.CloudState;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

import java.util.Objects;

/** Process-local Cloud client wiring; credentials remain in memory only. */
public final class CloudUploadRuntime {
    private static volatile ModelUploadTransport transport;

    private CloudUploadRuntime() {
    }

    public static synchronized void configure(CloudInstanceConfig instance, CloudSession session) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(session, "session");
        CloudHttpClient http = new CloudAuthClient(new CloudHttpClient(instance)).authenticated(session);
        transport = new CloudUploadTransport(new CloudAssetClient(http));
        http.discoverInstance();
    }

    public static ModelUploadTransport transport() {
        return transport;
    }

    public static boolean isConfigured() {
        return transport != null && CloudState.isAvailable();
    }

    public static synchronized void clear() {
        transport = null;
        CloudState.reset();
    }
}
