package com.micaftic.morpher.network;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Byte-based flow-control helpers shared by the legacy SPM model transport.
 * Keeping the arithmetic here prevents message counts from being compared
 * with byte thresholds and gives the client one aggregate receive budget.
 */
public final class LegacySyncFlowControl {
    public static final long SERVER_BURST_BYTES = 64L * 1024L;
    public static final long CLIENT_IN_FLIGHT_BYTES = 512L * 1024L * 1024L;

    private LegacySyncFlowControl() {
    }

    public static long addSaturated(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    public static boolean shouldWaitForDrain(long pendingBytes, long allowedPendingBytes) {
        return pendingBytes > allowedPendingBytes;
    }

    public static boolean tryReserve(AtomicLong reservedBytes, long bytes) {
        if (bytes <= 0L || bytes > CLIENT_IN_FLIGHT_BYTES) {
            return false;
        }
        while (true) {
            long current = reservedBytes.get();
            if (current > CLIENT_IN_FLIGHT_BYTES - bytes) {
                return false;
            }
            if (reservedBytes.compareAndSet(current, current + bytes)) {
                return true;
            }
        }
    }

    public static void release(AtomicLong reservedBytes, long bytes) {
        if (bytes <= 0L) {
            return;
        }
        reservedBytes.updateAndGet(current -> Math.max(0L, current - bytes));
    }
}
