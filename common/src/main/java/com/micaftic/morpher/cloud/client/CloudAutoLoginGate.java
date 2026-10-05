package com.micaftic.morpher.cloud.client;

import java.util.Objects;

/** Serializes automatic login and permits retries without polling the authentication server every tick. */
public final class CloudAutoLoginGate {
    private String previousKey;
    private long nextAttemptMillis;
    private boolean inFlight;

    public synchronized boolean begin(String key, long nowMillis) {
        Objects.requireNonNull(key, "key");
        if (inFlight || (key.equals(previousKey) && nowMillis < nextAttemptMillis)) return false;
        previousKey = key;
        nextAttemptMillis = nowMillis + 60_000L;
        inFlight = true;
        return true;
    }

    public synchronized void finish() { inFlight = false; }
}
