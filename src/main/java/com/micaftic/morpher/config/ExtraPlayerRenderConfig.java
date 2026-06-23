package com.micaftic.morpher.config;

import com.micaftic.morpher.core.architectury.platform.Platform;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ExtraPlayerRenderConfig {

    public static ModConfigSpec.BooleanValue DISABLE_PLAYER_RENDER;

    public static ModConfigSpec.IntValue PLAYER_POS_X;

    public static ModConfigSpec.IntValue PLAYER_POS_Y;

    public static ModConfigSpec.DoubleValue PLAYER_SCALE;

    public static ModConfigSpec.DoubleValue PLAYER_YAW_OFFSET;

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("extra_player_render");
        builder.comment("Whether to display player");
        DISABLE_PLAYER_RENDER = builder.define("DisablePlayerRender", Platform.isModLoaded("figura"));
        builder.comment("Player position x in screen");
        PLAYER_POS_X = builder.defineInRange("PlayerPosX", 10, 0, Integer.MAX_VALUE);
        builder.comment("Player position y in screen");
        PLAYER_POS_Y = builder.defineInRange("PlayerPosY", 10, 0, Integer.MAX_VALUE);
        builder.comment("Player scale in screen");
        PLAYER_SCALE = builder.defineInRange("PlayerScale", 40.0d, 8.0d, 360.0d);
        builder.comment("Player yaw offset in screen");
        PLAYER_YAW_OFFSET = builder.defineInRange("PlayerYawOffset", 5.0d, Double.MIN_VALUE, Double.MAX_VALUE);
        builder.pop();
    }
}
