package com.micaftic.morpher.cloud.client;

/** Validation failures carry a stable reason so each UI can localize them. */
public final class CloudValidationException extends IllegalArgumentException {
    public enum Reason { INVALID_ACCOUNT_ID, INVALID_PASSWORD }

    private final Reason reason;

    public CloudValidationException(Reason reason) {
        super(reason == Reason.INVALID_ACCOUNT_ID ? "Invalid Cloud account ID" : "Invalid Cloud password");
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
