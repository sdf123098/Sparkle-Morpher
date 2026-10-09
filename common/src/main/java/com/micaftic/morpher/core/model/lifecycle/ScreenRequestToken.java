package com.micaftic.morpher.core.model.lifecycle;

/** Identifies a request that may update a particular screen generation. */
public record ScreenRequestToken(int requestId, long screenGeneration) {
    public boolean isCurrent(int currentRequestId, long currentScreenGeneration) {
        return this.requestId == currentRequestId && this.screenGeneration == currentScreenGeneration;
    }
}
