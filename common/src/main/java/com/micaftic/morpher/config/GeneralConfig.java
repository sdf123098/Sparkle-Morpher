package com.micaftic.morpher.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import com.micaftic.morpher.core.render.SmRenderBackendMode;

public class GeneralConfig {

    public static ModConfigSpec.BooleanValue DISCLAIMER_SHOW;

    public static ModConfigSpec.BooleanValue PRINT_ANIMATION_ROULETTE_MSG;

    public static ModConfigSpec.BooleanValue DISABLE_SELF_MODEL;

    public static ModConfigSpec.BooleanValue DISABLE_OTHER_MODEL;

    public static ModConfigSpec.BooleanValue DISABLE_SELF_HANDS;

    public static ModConfigSpec.BooleanValue DISABLE_PROJECTILE_MODEL;

    public static ModConfigSpec.BooleanValue DISABLE_VEHICLE_MODEL;

    public static ModConfigSpec.BooleanValue DISABLE_EXTERNAL_FP_ANIM;

    public static ModConfigSpec.BooleanValue USE_COMPATIBILITY_RENDERER;

    public static ModConfigSpec.DoubleValue SOUND_VOLUME;

    public static ModConfigSpec.BooleanValue SHOW_MODEL_ID_FIRST;

    public static ModConfigSpec.BooleanValue SOPHISTICATEDBACKPACK;

    public static ModConfigSpec.BooleanValue PARCOOL;

    public static ModConfigSpec.BooleanValue USE_GPU_RENDERER;

    public static ModConfigSpec.BooleanValue USE_NATIVE_SIMD_RENDERER;

    public static ModConfigSpec.BooleanValue EXPERIMENTAL_JAVA_VECTOR_RENDERER;

    public static ModConfigSpec.EnumValue<SmRenderBackendMode> GRAPHICS_BACKEND_MODE;

    public static ModConfigSpec.BooleanValue DISABLE_RAW_OPENGL_ON_NON_OPENGL;

    public static ModConfigSpec.BooleanValue ENABLE_OPENGL_LEGACY_GPU_RENDERER;

    public static ModConfigSpec.BooleanValue ENABLE_OPENGL_GUI_BLUR;

    public static ModConfigSpec.BooleanValue MODEL_MEMORY_PROFILER;

    public static ModConfigSpec.BooleanValue MODEL_IMPORT_PERFORMANCE_LOG;

    public static ModConfigSpec.BooleanValue ANIMATION_FRAME_PROFILER;

    public static ModConfigSpec.BooleanValue ANIMATION_DEBUG_LOG;

    public static ModConfigSpec.BooleanValue INPUT_STATE_DEBUG_LOG;

    public static ModConfigSpec.BooleanValue EXPERIMENTAL_FALLBACK_ELYTRA_WITHOUT_LOCATOR;

    public static ModConfigSpec.BooleanValue EXPERIMENTAL_ENABLE_ELYTRA_FOR_DEFAULT_AND_MISC_MODELS;

    public static ModConfigSpec.BooleanValue WARN_REPEATED_ANIMATION_EVALUATION;

    public static ModConfigSpec.BooleanValue RELEASE_TEXTURE_BYTES_AFTER_UPLOAD;

    public static ModConfigSpec.BooleanValue RESOURCE_STATION_MONITOR_LOG;

    public static ModConfigSpec.BooleanValue NETWORK_ONLINE_DEBUG_LOG;

    public static ModConfigSpec.BooleanValue GPU_DEBUG_LOG;

    public static ModConfigSpec.BooleanValue GPU_DEBUG_VERBOSE_LOG;

    public static ModConfigSpec.BooleanValue VULKAN_EXPERIMENTAL_CAPABILITY_PROBE;

    public static ModConfigSpec.IntValue MAX_CACHED_GPU_MODELS;

    public static ModConfigSpec.IntValue UNUSED_MODEL_TTL_SECONDS;

    public static ModConfigSpec.IntValue AUDIO_CACHE_MAX_BYTES;

    public static ModConfigSpec.BooleanValue DISABLE_MODEL_GLOW_IN_SHADERPACK;

    public static ModConfigSpec.BooleanValue ANIMATION_DISTANCE_LOD;

    public static ModConfigSpec.EnumValue<RouletteContentMode> ROULETTE_CONTENT_MODE;

    public enum RouletteContentMode {
        ORIGINAL,
        CUSTOM
    }

    public static boolean safeGet(ModConfigSpec.BooleanValue value) {
        return safeGet(value, false);
    }

    public static boolean safeGet(ModConfigSpec.BooleanValue value, boolean fallback) {
        try { return value == null ? fallback : value.get(); } catch (IllegalStateException e) { return fallback; }
    }

    public static <T extends Enum<T>> T safeGet(ModConfigSpec.EnumValue<T> value, T fallback) {
        try { return value == null ? fallback : value.get(); } catch (IllegalStateException e) { return fallback; }
    }

    public static ModConfigSpec buildSpec() {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        defineGeneral(builder);
        ExtraPlayerRenderConfig.define(builder);
        LoadingStateConfig.define(builder);
        return builder.build();
    }

    public static void defineGeneral(ModConfigSpec.Builder builder) {
        builder.push("general");
        builder.comment("Whether to display disclaimer GUI");
        DISCLAIMER_SHOW = builder.define("DisclaimerShow", false);
        builder.comment("Whether to print animation roulette play message");
        PRINT_ANIMATION_ROULETTE_MSG = builder.define("PrintAnimationRouletteMsg", false);
        builder.comment("Prevents rendering of self player's model");
        DISABLE_SELF_MODEL = builder.define("DisableSelfModel", false);
        builder.comment("Prevents rendering of other player's model");
        DISABLE_OTHER_MODEL = builder.define("DisableOtherModel", false);
        builder.comment("Prevents rendering of self player's hand");
        DISABLE_SELF_HANDS = builder.define("DisableSelfHands", false);
        builder.comment("Prevents rendering of projectile model");
        DISABLE_PROJECTILE_MODEL = builder.define("DisableProjectileModel", false);
        builder.comment("Prevents rendering of vehicle model");
        DISABLE_VEHICLE_MODEL = builder.define("DisableVehicleModel", false);
        builder.comment("Disable first person animation from other mods.");
        DISABLE_EXTERNAL_FP_ANIM = builder.define("DisableExternalFirstPersonAnim", false);
        builder.comment("If rendering errors occur, try turning on this.");
        USE_COMPATIBILITY_RENDERER = builder.define("UseCompatibilityRenderer", true);
        builder.comment("Legacy direct OpenGL GPU renderer master switch. This remains off by default and is additionally gated by GraphicsBackendMode and EnableOpenGlLegacyGpuRenderer.");
        USE_GPU_RENDERER = builder.define("UseGpuRenderer", false);
        GRAPHICS_BACKEND_MODE = builder.comment("Graphics backend policy. AUTO uses Minecraft's detected device, VANILLA_PIPELINE_ONLY disables raw OpenGL paths, OPENGL_LEGACY_COMPAT permits legacy OpenGL acceleration only on OpenGL, DISABLED_GPU_ACCELERATION disables raw OpenGL acceleration.")
                .defineEnum("GraphicsBackendMode", SmRenderBackendMode.AUTO);
        DISABLE_RAW_OPENGL_ON_NON_OPENGL = builder.comment("Prevent raw OpenGL probes/renderers when Minecraft is not using an OpenGL backend.")
                .define("DisableRawOpenGlOnNonOpenGl", true);
        ENABLE_OPENGL_LEGACY_GPU_RENDERER = builder.comment("Allow the legacy raw OpenGL model renderer when the detected backend is OpenGL and UseGpuRenderer is also enabled.")
                .define("EnableOpenGlLegacyGpuRenderer", false);
        ENABLE_OPENGL_GUI_BLUR = builder.comment("Allow raw OpenGL GUI blur/shader effects on OpenGL. Disabled by default for Vulkan compatibility.")
                .define("EnableOpenGlGuiBlur", false);
        builder.comment("Use OpenYSM native SIMD model rendering. Disabled by default on 26.1.2 because the current native renderer has different bone visibility semantics than the Java path.");
        USE_NATIVE_SIMD_RENDERER = builder.define("UseNativeSimdRenderer", false);
        builder.comment("Render ysmGlow bones with normal entity lighting while a shader pack is active.");
        DISABLE_MODEL_GLOW_IN_SHADERPACK = builder.define("DisableModelGlowInShaderpack", true);
        ROULETTE_CONTENT_MODE = builder.defineEnum("RouletteContentMode", RouletteContentMode.ORIGINAL);
        builder.comment("The amount of volume when the animation is played.");
        SOUND_VOLUME = builder.defineInRange("SoundVolume", 100.0d, 0.0d, 100.0d);
        builder.comment("Whether to display model ID first in the model selection screen, instead of the model name filled in by the model author.");
        SHOW_MODEL_ID_FIRST = builder.define("ShowModelIdFirst", false);
        builder.pop();
        builder.push("Integration");
        SOPHISTICATEDBACKPACK = builder.define("SophisticatedBackpack", true);
        PARCOOL = builder.define("Parcool", true);
        builder.pop();
        builder.push("ExperimentalTesting");
        builder.comment("Log model loading memory checkpoints. Intended for diagnostics only.");
        MODEL_MEMORY_PROFILER = builder.define("ModelMemoryProfiler", false);
        builder.comment("Print [SM][Perf] timing logs for model import, upload, and reload checkpoints. Intended for diagnostics only.");
        MODEL_IMPORT_PERFORMANCE_LOG = builder.define("ModelImportPerformanceLog", false);
        builder.comment("Collect animation timing/evaluation diagnostics. Intended for diagnostics only.");
        ANIMATION_FRAME_PROFILER = builder.define("AnimationFrameProfiler", false);
        builder.comment("Print one [SM-ANIM] line for each animation evaluation when AnimationFrameProfiler is enabled.");
        ANIMATION_DEBUG_LOG = builder.define("AnimationDebugLog", false);
        builder.comment("Print [SM-INPUT] diagnostics for attack/use mouse clicks, key state, vanilla swing/use state, and local animation pulses.");
        INPUT_STATE_DEBUG_LOG = builder.define("InputStateDebugLog", false);
        builder.comment("Allow fallback elytra rendering for models without ElytraLocator. Experimental and may not align perfectly.");
        EXPERIMENTAL_FALLBACK_ELYTRA_WITHOUT_LOCATOR = builder.define("ExperimentalFallbackElytraWithoutLocator", false);
        builder.comment("Re-enable elytra rendering for default and misc built-in models, plus models whose ElytraLocator is nested under an Elytra display bone.");
        EXPERIMENTAL_ENABLE_ELYTRA_FOR_DEFAULT_AND_MISC_MODELS = builder.define("ExperimentalEnableElytraForDefaultAndMiscModels", false);
        builder.comment("Warn when the same entity evaluates animation more than once in the same render frame.");
        WARN_REPEATED_ANIMATION_EVALUATION = builder.define("WarnRepeatedAnimationEvaluation", true);
        builder.comment("Reduce animation update rates for distant entities. Disabled by default for smoother animation.");
        ANIMATION_DISTANCE_LOD = builder.define("AnimationDistanceLod", false);
        builder.comment("Use the incubating Java Vector API for part of the Java fallback renderer. Experimental and default off; if the module is unavailable, Sparkle Morpher automatically falls back to scalar Java math.");
        EXPERIMENTAL_JAVA_VECTOR_RENDERER = builder.define("ExperimentalJavaVectorRenderer", false);
        builder.comment("Release original texture byte arrays after successful GPU upload. Disable if resource reloads need to re-decode outer textures.");
        RELEASE_TEXTURE_BYTES_AFTER_UPLOAD = builder.define("ReleaseTextureBytesAfterUpload", false);
        builder.comment("Print detailed [SM-RESOURCE] logs for resource station listing, HTTP, preview, and download diagnostics.");
        RESOURCE_STATION_MONITOR_LOG = builder.define("ResourceStationMonitorLog", false);
        builder.comment("Print detailed client/server online model sync diagnostics. Default off.");
        NETWORK_ONLINE_DEBUG_LOG = builder.define("NetworkOnlineDebugLog", false);
        builder.comment("Print [SM-GPU] diagnostics for GPU renderer path selection, mesh build, draw counts, and GL errors. Intended for diagnostics only.");
        GPU_DEBUG_LOG = builder.define("GpuDebugLog", false);
        builder.comment("Print verbose per-draw [SM-GPU] diagnostics. Requires GpuDebugLog and may be noisy.");
        GPU_DEBUG_VERBOSE_LOG = builder.define("GpuDebugVerboseLog", false);
        builder.comment("Run a 26.2-only Vulkan backend capability probe at client startup. Diagnostics only: no rendering, no compute dispatch, default off.");
        VULKAN_EXPERIMENTAL_CAPABILITY_PROBE = builder.define("VulkanExperimentalCapabilityProbe", false);
        builder.comment("Maximum client models allowed to keep GPU/native render caches. 0 disables LRU unloading.");
        MAX_CACHED_GPU_MODELS = builder.defineInRange("MaxCachedGpuModels", 0, 0, 512);
        builder.comment("Minimum idle time before an unused client model GPU/native cache can be unloaded by LRU.");
        UNUSED_MODEL_TTL_SECONDS = builder.defineInRange("UnusedModelTtlSeconds", 300, 30, 86400);
        builder.comment("Maximum decoded audio cache bytes per model. 0 disables decoded audio caching.");
        AUDIO_CACHE_MAX_BYTES = builder.defineInRange("AudioCacheMaxBytes", 64 * 1024 * 1024, 0, 512 * 1024 * 1024);
        builder.pop();
    }
}
