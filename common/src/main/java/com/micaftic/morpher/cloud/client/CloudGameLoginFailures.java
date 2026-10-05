package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

/** A verified but unlinked identity can proceed even if a different issuer is unavailable. */
public final class CloudGameLoginFailures {
    private boolean verifiedUnlinked;
    private Throwable unavailable;

    public void record(CloudHttpException failure) {
        if (failure.errorCode() == CloudErrorCode.IDENTITY_NOT_LINKED) verifiedUnlinked = true;
        if (failure.errorCode() == CloudErrorCode.IDENTITY_PROVIDER_UNAVAILABLE && unavailable == null) unavailable = failure;
    }

    public Throwable unavailable() { return verifiedUnlinked ? null : unavailable; }
}
