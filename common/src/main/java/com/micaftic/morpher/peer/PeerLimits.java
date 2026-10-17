package com.micaftic.morpher.peer;

/** V1 hard ceilings; admission and resource budgets can negotiate lower values. */
public final class PeerLimits {
    public static final int PROTOCOL_MAJOR = 1, PROTOCOL_MINOR = 2;
    public static final int CONTROL_BYTES = 64 * 1024, CHUNK_BYTES = 256 * 1024;
    public static final int MANIFEST_BYTES = 1024 * 1024, RESOURCE_COUNT = 4096;
    public static final int PATH_BYTES = 512, INVITE_BYTES = 8192, ENDPOINT_COUNT = 8;
    public static final int CAPACITY_CEILING = 32, HANDSHAKE_COUNT = 8;
    public static final long RESOURCE_BYTES = 64L * 1024 * 1024;
    public static final long SOURCE_BYTES = 128L * 1024 * 1024;
    public static final long EXPANDED_BYTES = 256L * 1024 * 1024;
    private PeerLimits() {}
}
