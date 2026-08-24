package com.micaftic.morpher.core.config;

import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.ServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.micaftic.morpher.core.render.SmRenderBackendMode;

import java.util.List;

/** Stable policy boundary over legacy NeoForge ConfigSpec values. */
public final class ConfigPolicies {
    private ConfigPolicies() { }
    public static Snapshot snapshot() { return new Snapshot(render(), memory(), diagnostics(), privacy(), network()); }
    public static RenderPolicy render() { return new RenderPolicy(bool(GeneralConfig.DISABLE_SELF_MODEL, false), bool(GeneralConfig.DISABLE_OTHER_MODEL, false), bool(GeneralConfig.DISABLE_SELF_HANDS, false), bool(GeneralConfig.DISABLE_PROJECTILE_MODEL, false), bool(GeneralConfig.DISABLE_VEHICLE_MODEL, false), bool(GeneralConfig.DISABLE_EXTERNAL_FP_ANIM, false), bool(GeneralConfig.USE_COMPATIBILITY_RENDERER, false), bool(GeneralConfig.USE_GPU_RENDERER, true), bool(GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK, true), bool(GeneralConfig.ANIMATION_DISTANCE_LOD, false)); }
    public static MemoryPolicy memory() { return new MemoryPolicy(integer(GeneralConfig.AUDIO_CACHE_MAX_BYTES, 64 * 1024 * 1024), integer(GeneralConfig.MAX_CACHED_GPU_MODELS, 0), bool(GeneralConfig.LAZY_MODEL_LOADING, true), integer(GeneralConfig.MAX_RESIDENT_CPU_MODELS, 64), integer(GeneralConfig.UNUSED_MODEL_TTL_SECONDS, 300)); }
    public static DiagnosticsPolicy diagnostics() { return new DiagnosticsPolicy(bool(GeneralConfig.ANIMATION_FRAME_PROFILER, false), bool(GeneralConfig.ANIMATION_DEBUG_LOG, false), bool(GeneralConfig.WARN_REPEATED_ANIMATION_EVALUATION, true), bool(GeneralConfig.RESOURCE_STATION_MONITOR_LOG, false), bool(GeneralConfig.NETWORK_ONLINE_DEBUG_LOG, false), bool(GeneralConfig.MODEL_MEMORY_PROFILER, false), bool(GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG, false), bool(GeneralConfig.INPUT_STATE_DEBUG_LOG, false)); }
    public static PrivacyPolicy privacy() { return new PrivacyPolicy(bool(GeneralConfig.PRIVACY_MODE, false)); }
    public static NetworkPolicy network() { return new NetworkPolicy(value(ServerConfig.DEFAULT_MODEL_ID, "default"), value(ServerConfig.DEFAULT_MODEL_TEXTURE, "default"), bool(ServerConfig.CAN_SWITCH_MODEL, true), bool(ServerConfig.ALLOW_MODEL_UPLOAD, true), integer(ServerConfig.MODEL_UPLOAD_MAX_MB, 128), integer(ServerConfig.MODEL_UPLOAD_CHUNKS_PER_TICK, 4), List.copyOf(value(ServerConfig.CLIENT_NOT_DISPLAY_MODELS, List.of())), integer(ServerConfig.THREAD_COUNT, 0), bool(ServerConfig.ENABLE_GLOBAL_BANDWIDTH_LIMIT, false), integer(ServerConfig.BANDWIDTH_LIMIT, 5), integer(ServerConfig.PLAYER_SYNC_TIMEOUT, 0), bool(ServerConfig.LOW_BANDWIDTH_USAGE, false), integer(ServerConfig.ACCEPT_SOUND_FX, 0)); }
    public static GraphicsPolicy graphics() { return new GraphicsPolicy(value(GeneralConfig.GRAPHICS_BACKEND_MODE, SmRenderBackendMode.AUTO), bool(GeneralConfig.ENABLE_OPENGL_LEGACY_GPU_RENDERER, false), bool(GeneralConfig.DISABLE_RAW_OPENGL_ON_NON_OPENGL, true), bool(GeneralConfig.ENABLE_OPENGL_GUI_BLUR, false), value(GeneralConfig.NATIVE_SIMD_POLICY, GeneralConfig.NativeSimdPolicy.AGGRESSIVE), value(GeneralConfig.NATIVE_SIMD_VALIDATION_MODE, GeneralConfig.NativeSimdValidationMode.OFF), bool(GeneralConfig.EXPERIMENTAL_JAVA_VECTOR_RENDERER, false), bool(GeneralConfig.NATIVE_SIMD_COMPATIBILITY_LOG, false), bool(GeneralConfig.GPU_DEBUG_LOG, false), bool(GeneralConfig.GPU_DEBUG_VERBOSE_LOG, false)); }
    public static AudioPolicy audio() { return new AudioPolicy(decimal(GeneralConfig.SOUND_VOLUME, 100.0)); }
    public static FeaturePolicy features() { return new FeaturePolicy(bool(GeneralConfig.EXPERIMENTAL_FALLBACK_ELYTRA_WITHOUT_LOCATOR, false), bool(GeneralConfig.EXPERIMENTAL_ENABLE_ELYTRA_FOR_DEFAULT_AND_MISC_MODELS, false), false, bool(GeneralConfig.ENABLE_WORLD_RENDERER_HOOK, true), false); }
    private static boolean bool(ModConfigSpec.BooleanValue value, boolean fallback) { try { return value == null ? fallback : value.get(); } catch (Exception ignored) { return fallback; } }
    private static int integer(ModConfigSpec.IntValue value, int fallback) { try { return value == null ? fallback : value.get(); } catch (Exception ignored) { return fallback; } }
    private static double decimal(ModConfigSpec.DoubleValue value, double fallback) { try { return value == null ? fallback : value.get(); } catch (Exception ignored) { return fallback; } }
    private static <T> T value(ModConfigSpec.ConfigValue<T> value, T fallback) { try { T result = value == null ? null : value.get(); return result == null ? fallback : result; } catch (Exception ignored) { return fallback; } }
    public record Snapshot(RenderPolicy render, MemoryPolicy memory, DiagnosticsPolicy diagnostics, PrivacyPolicy privacy, NetworkPolicy network) { }
    public record RenderPolicy(boolean disableSelfModel, boolean disableOtherModel, boolean disableSelfHands, boolean disableProjectileModel, boolean disableVehicleModel, boolean disableExternalFirstPersonAnimation, boolean useCompatibilityRenderer, boolean useGpuRenderer, boolean disableModelGlowInShaderpack, boolean animationDistanceLod) { }
    public record MemoryPolicy(int audioCacheMaxBytes, int maxCachedGpuModels, boolean lazyModelLoading, int maxResidentCpuModels, int unusedModelTtlSeconds) { }
    public record DiagnosticsPolicy(boolean animationFrameProfiler, boolean animationDebugLog, boolean warnRepeatedAnimationEvaluation, boolean resourceStationMonitorLog, boolean networkOnlineDebugLog, boolean modelMemoryProfiler, boolean modelImportPerformanceLog, boolean inputStateDebugLog) { }
    public record PrivacyPolicy(boolean enabled) { }
    public record NetworkPolicy(String defaultModelId, String defaultModelTexture, boolean canSwitchModel, boolean allowModelUpload, int modelUploadMaxMiB, int modelUploadChunksPerTick, List<String> clientNotDisplayModels, int threadCount, boolean globalBandwidthLimit, int bandwidthLimitMbps, int playerSyncTimeoutSeconds, boolean lowBandwidthUsage, int acceptSoundFx) { }
    public record GraphicsPolicy(SmRenderBackendMode backendMode, boolean openGlLegacyGpuRenderer, boolean disableRawOpenGlOnNonOpenGl, boolean openGlGuiBlur, GeneralConfig.NativeSimdPolicy nativeSimdPolicy, GeneralConfig.NativeSimdValidationMode nativeSimdValidationMode, boolean experimentalJavaVectorRenderer, boolean nativeSimdCompatibilityLog, boolean gpuDebugLog, boolean gpuDebugVerboseLog) { }
    public record AudioPolicy(double soundVolumePercent) { }
    public record FeaturePolicy(boolean experimentalFallbackElytraWithoutLocator, boolean experimentalEnableElytraForDefaultAndMiscModels, boolean vulkanExperimentalCapabilityProbe, boolean worldRendererHook, boolean animationRouletteDebugLog) { }
}
