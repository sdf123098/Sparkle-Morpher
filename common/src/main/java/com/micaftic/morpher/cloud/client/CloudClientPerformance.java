package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.config.GeneralConfig;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Client-side Cloud limits, independent of the selected server's own admission policy. */
public final class CloudClientPerformance {
    public enum Preset {
        UNLIMITED(0, 0), LOW(2, 1), MEDIUM(4, 2), HIGH(8, 4), CUSTOM(0, 0);
        private final int uploads, processing;
        Preset(int uploads, int processing) { this.uploads = uploads; this.processing = processing; }
        public int uploads(int custom) { return this == CUSTOM ? custom : uploads; }
        public int processing(int custom) { return this == CUSTOM ? custom : processing; }
    }

    private static final CloudConcurrencyQueue UPLOADS = new CloudConcurrencyQueue();
    private static final CloudConcurrencyQueue PROCESSING = new CloudConcurrencyQueue();
    private CloudClientPerformance() {}

    public static Preset preset() {
        try { return GeneralConfig.CLOUD_PERFORMANCE_PRESET == null ? Preset.UNLIMITED : GeneralConfig.CLOUD_PERFORMANCE_PRESET.get(); }
        catch (IllegalStateException unavailable) { return Preset.UNLIMITED; }
    }
    public static int uploads() { return preset().uploads(custom(true)); }
    public static int processing() { return preset().processing(custom(false)); }
    private static int custom(boolean upload) {
        var value = upload ? GeneralConfig.CLOUD_UPLOAD_CONCURRENCY : GeneralConfig.CLOUD_PROCESSING_CONCURRENCY;
        try { return value == null ? 0 : Math.max(0, Math.min(64, value.get())); }
        catch (IllegalStateException unavailable) { return 0; }
    }

    public static void select(Preset preset) {
        GeneralConfig.CLOUD_PERFORMANCE_PRESET.set(java.util.Objects.requireNonNull(preset));
        GeneralConfig.CLOUD_PERFORMANCE_PRESET.save();
        refresh();
    }
    public static void adjust(boolean upload, int delta) {
        if (preset() != Preset.CUSTOM) return;
        var value = upload ? GeneralConfig.CLOUD_UPLOAD_CONCURRENCY : GeneralConfig.CLOUD_PROCESSING_CONCURRENCY;
        value.set(Math.max(0, Math.min(64, custom(upload) + delta)));
        value.save();
        refresh();
    }
    private static void refresh() { UPLOADS.limit(uploads()); PROCESSING.limit(processing()); }
    public static <T> CompletableFuture<T> upload(Supplier<CompletableFuture<T>> operation) {
        refresh();
        return UPLOADS.submit(operation);
    }
    public static <T> CompletableFuture<T> process(Supplier<CompletableFuture<T>> operation) {
        refresh();
        return PROCESSING.submit(operation);
    }
}
