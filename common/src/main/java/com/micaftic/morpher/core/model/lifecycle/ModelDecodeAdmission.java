package com.micaftic.morpher.core.model.lifecycle;

import java.util.concurrent.locks.ReentrantLock;

/** Bounds the expensive decoded-model peak independently of network concurrency. */
public final class ModelDecodeAdmission {
    private static final long MIB = 1024L * 1024;
    private final ReentrantLock decode = new ReentrantLock(true);

    public void run(Runnable task) {
        decode.lock();
        try { task.run(); }
        finally { decode.unlock(); }
    }

    /** Optional previews must leave space for the world as well as expanded source textures. */
    public static boolean previewFits(long sourceBytes, long maxHeapBytes, long availableHeapBytes) {
        if (sourceBytes < 0 || sourceBytes > Long.MAX_VALUE / 6) return false;
        long worldReserve = Math.max(64 * MIB, maxHeapBytes / 8);
        long decodePeak = Math.max(64 * MIB, sourceBytes * 6);
        return availableHeapBytes >= worldReserve && decodePeak <= availableHeapBytes - worldReserve;
    }

    public static boolean previewFits(long sourceBytes) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return previewFits(sourceBytes, runtime.maxMemory(), runtime.maxMemory() - used);
    }

    /** Use decoded dimensions, never compressed file size, to bound a raster allocation. */
    public static boolean textureFits(int width, int height, int rasterCopies,
            long maxHeapBytes, long availableHeapBytes) {
        if (width <= 0 || height <= 0 || rasterCopies <= 0) return false;
        long pixels = (long) width * height;
        // Java image rasters and native decoders use signed-int array indexing.
        if (pixels > Integer.MAX_VALUE - 8L) return false;
        long peak;
        try { peak = Math.multiplyExact(Math.multiplyExact(pixels, 4L), rasterCopies); }
        catch (ArithmeticException overflow) { return false; }
        long worldReserve = Math.max(64 * MIB, maxHeapBytes / 8);
        return availableHeapBytes >= worldReserve && peak <= availableHeapBytes - worldReserve;
    }

    public static boolean textureFits(int width, int height, int rasterCopies) {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return textureFits(width, height, rasterCopies, runtime.maxMemory(), runtime.maxMemory() - used);
    }
}
