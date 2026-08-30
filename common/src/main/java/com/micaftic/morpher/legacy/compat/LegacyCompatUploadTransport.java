package com.micaftic.morpher.legacy.compat;

import com.micaftic.morpher.client.upload.LegacyServerUploadTransport;
import com.micaftic.morpher.core.api.network.upload.ModelUploadTransport;

/** Compatibility-owned upload transport; replaceable by the Cloud transport in 1.3.x. */
public final class LegacyCompatUploadTransport {
    public static final ModelUploadTransport INSTANCE = LegacyServerUploadTransport.INSTANCE;

    private LegacyCompatUploadTransport() {
    }
}
