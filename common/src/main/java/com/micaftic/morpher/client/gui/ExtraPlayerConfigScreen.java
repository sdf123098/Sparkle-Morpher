package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.config.ExtraPlayerRenderConfig;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.LoadingStateConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.gui.Option;
import com.micaftic.morpher.core.gui.OptionGroup;
import com.micaftic.morpher.core.gui.OptionScreen;
import com.micaftic.morpher.core.gui.components.BooleanOptionRow;
import com.micaftic.morpher.core.gui.components.EnumOptionRow;
import com.micaftic.morpher.core.gui.components.RadioOptionRow;
import com.micaftic.morpher.core.gui.components.SliderOptionRow;

import java.util.List;

public class ExtraPlayerConfigScreen extends OptionScreen {

    public ExtraPlayerConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("gui.sparkle_morpher.settings.title"), parent);
    }

    @Override
    protected void registerGroups() {
        OptionGroup general = new OptionGroup("general")
                .add(new SliderOptionRow(0, 0, 0, 22, Option.ofDouble("sound_volume", GeneralConfig.SOUND_VOLUME), 0.0d, 100.0d, 1.0d, "%"))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_self_model", GeneralConfig.DISABLE_SELF_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_other_model", GeneralConfig.DISABLE_OTHER_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_self_hands", GeneralConfig.DISABLE_SELF_HANDS)));

        OptionGroup rendering = new OptionGroup("rendering")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_player_render", ExtraPlayerRenderConfig.DISABLE_PLAYER_RENDER)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_projectile_model", GeneralConfig.DISABLE_PROJECTILE_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_vehicle_model", GeneralConfig.DISABLE_VEHICLE_MODEL)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_external_first_person_anim", GeneralConfig.DISABLE_EXTERNAL_FP_ANIM)));

        OptionGroup performance = new OptionGroup("performance")
                .add(new RadioOptionRow(0, 0, 0, 22, rendererOption(), rendererLabels()))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_model_glow_in_shaderpack", GeneralConfig.DISABLE_MODEL_GLOW_IN_SHADERPACK)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("release_texture_bytes_after_upload", GeneralConfig.RELEASE_TEXTURE_BYTES_AFTER_UPLOAD)))
                .add(new SliderOptionRow(0, 0, 0, 22, intOption("max_cached_gpu_models", GeneralConfig.MAX_CACHED_GPU_MODELS), 0.0d, 512.0d, 1.0d, ""))
                .add(new SliderOptionRow(0, 0, 0, 22, intOption("unused_model_ttl_seconds", GeneralConfig.UNUSED_MODEL_TTL_SECONDS), 30.0d, 86400.0d, 30.0d, "s"));

        OptionGroup debug = new OptionGroup("debug")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("model_memory_profiler", GeneralConfig.MODEL_MEMORY_PROFILER)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("model_import_performance_log", GeneralConfig.MODEL_IMPORT_PERFORMANCE_LOG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("animation_frame_profiler", GeneralConfig.ANIMATION_FRAME_PROFILER)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("animation_debug_log", GeneralConfig.ANIMATION_DEBUG_LOG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("input_state_debug_log", GeneralConfig.INPUT_STATE_DEBUG_LOG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("warn_repeated_animation_evaluation", GeneralConfig.WARN_REPEATED_ANIMATION_EVALUATION)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("animation_distance_lod", GeneralConfig.ANIMATION_DISTANCE_LOD)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("network_online_debug_log", GeneralConfig.NETWORK_ONLINE_DEBUG_LOG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("resource_station_monitor_log", GeneralConfig.RESOURCE_STATION_MONITOR_LOG)));

        OptionGroup misc = new OptionGroup("misc")
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("print_animation_roulette_msg", GeneralConfig.PRINT_ANIMATION_ROULETTE_MSG)))
                .add(new BooleanOptionRow(0, 0, 0, 22, Option.ofBoolean("disable_loading_state_screen", LoadingStateConfig.DISABLE_LOADING_STATE_SCREEN)))
                .add(new EnumOptionRow<>(0, 0, 0, 22, Option.ofEnum("loading_state_position", LoadingStateConfig.LOADING_STATE_POSITION), LoadingStateConfig.Position.values()));

        groups.add(general);
        groups.add(rendering);
        groups.add(performance);
        groups.add(debug);
        groups.add(misc);
    }

    private static Option<Double> intOption(String key, ForgeConfigSpec.IntValue cfg) {
        return new Option<>(key, () -> cfg.get().doubleValue(), value -> {
            cfg.set(value == null ? 0 : value.intValue());
            cfg.save();
        });
    }

    private static Option<Integer> rendererOption() {
        return new Option<>("renderer", ExtraPlayerConfigScreen::getRendererState, ExtraPlayerConfigScreen::setRendererState);
    }

    private static int getRendererState() {
        return !GeneralConfig.USE_COMPATIBILITY_RENDERER.get() && GeneralConfig.USE_GPU_RENDERER.get() ? 1 : 0;
    }

    private static void setRendererState(Integer value) {
        boolean useGpuRenderer = value != null && value == 1;
        GeneralConfig.USE_COMPATIBILITY_RENDERER.set(!useGpuRenderer);
        GeneralConfig.USE_COMPATIBILITY_RENDERER.save();
        GeneralConfig.USE_GPU_RENDERER.set(useGpuRenderer);
        GeneralConfig.USE_GPU_RENDERER.save();
    }

    private static List<String> rendererLabels() {
        return List.of(
                Component.translatable("gui.sparkle_morpher.config.renderer.compatibility").getString(),
                Component.translatable("gui.sparkle_morpher.config.renderer.gpu").getString()
        );
    }
}
