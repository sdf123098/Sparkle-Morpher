package com.micaftic.morpher.core.api.version;

/** Version adapter selected by this source branch. */
public final class VersionAdapters {
    private static final MinecraftVersionAdapter CURRENT = new MinecraftVersionAdapter() {
        public String minecraftVersion() { return "26.1.2"; }
        public boolean supportsSubmitNodeCollector() { return true; }
        public boolean supportsBlaze3dGpuPipeline() { return false; }
        public boolean supportsGuiGraphicsExtractor() { return true; }
        public boolean supports(VersionAdapterSurface surface) { return true; }
    };
    private VersionAdapters() { }
    public static MinecraftVersionAdapter current() { return CURRENT; }

    public static VersionAdapterCapabilities capabilities() { return VersionAdapterCapabilities.from(CURRENT); }
}
