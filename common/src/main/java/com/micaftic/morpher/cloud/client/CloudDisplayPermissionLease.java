package com.micaftic.morpher.cloud.client;

/** Main-thread permission lease; cached bytes cannot grant permission. */
public final class CloudDisplayPermissionLease {
    private static final long TTL = 30_000_000_000L;
    private static final long REFRESH = 15_000_000_000L;
    private static final long RETRY = 5_000_000_000L;
    private boolean authorized;
    private boolean pending;
    private long expires;
    private long next;
    private boolean scheduled;

    public boolean valid(long now) { return authorized && now - expires < 0; }
    public boolean start(long now) {
        if (pending || scheduled && now - next < 0) return false;
        pending = true;
        return true;
    }
    public void complete(boolean allowed, long now) {
        pending = false;
        authorized = allowed;
        expires = now + TTL;
        next = now + (allowed ? REFRESH : RETRY);
        scheduled = true;
    }
}
