package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.core.api.network.state.CloudErrorCode;

/** Typed failure returned by the Cloud HTTP boundary. */
public final class CloudHttpException extends RuntimeException {

    private final int statusCode;
    private final CloudErrorCode errorCode;

    public CloudHttpException(int statusCode, CloudErrorCode errorCode, String message) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public CloudErrorCode errorCode() {
        return errorCode;
    }
}

