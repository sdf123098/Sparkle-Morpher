package com.micaftic.morpher.cloud.client;

import java.util.Objects;

/** One restore per loaded capability/binding/model, with transient-failure backoff. */
final class FakePlayerRouletteRestoreGate {
    private Object token;
    private boolean pending, finished;
    private long retryAt;
    boolean begin(Object candidate, long now) {
        if (!Objects.equals(token, candidate)) { token = candidate; pending = finished = false; retryAt = 0; }
        if (pending || finished || now < retryAt) return false;
        pending = true;
        return true;
    }
    boolean complete(Object candidate, boolean success, boolean denied, long now) {
        if (!Objects.equals(token, candidate) || !pending) return false;
        pending = false; finished = success || denied; retryAt = now + 5_000_000_000L;
        return true;
    }
}
