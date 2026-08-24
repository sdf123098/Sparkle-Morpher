package com.micaftic.morpher.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class LoadingStateConfig {

    public static ModConfigSpec.BooleanValue DISABLE_LOADING_STATE_SCREEN;

    public static ModConfigSpec.EnumValue<Position> LOADING_STATE_POSITION;

    public static ModConfigSpec.IntValue LOADING_STATE_OFFSET_X;

    public static ModConfigSpec.IntValue LOADING_STATE_OFFSET_Y;

    public static ModConfigSpec.IntValue LOADING_STATE_AUTO_HIDE_SECONDS;

    public enum Position {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT
    }

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("loading_state_screen");
        builder.comment("Whether to disable loading state screen");
        DISABLE_LOADING_STATE_SCREEN = builder.define("DisableLoadingStateScreen", false);
        builder.comment("Loading state screen position");
        LOADING_STATE_POSITION = builder.defineEnum("LoadingStatePosition", Position.TOP_CENTER);
        builder.comment("Horizontal offset of the loading state popup from its anchor, in GUI pixels");
        LOADING_STATE_OFFSET_X = builder.defineInRange("LoadingStateOffsetX", 0, -10000, 10000);
        builder.comment("Vertical offset of the loading state popup from its anchor, in GUI pixels");
        LOADING_STATE_OFFSET_Y = builder.defineInRange("LoadingStateOffsetY", 0, -10000, 10000);
        builder.comment("Seconds before the loading state popup hides after success or failure");
        LOADING_STATE_AUTO_HIDE_SECONDS = builder.defineInRange("LoadingStateAutoHideSeconds", 4, 1, 30);
        builder.pop();
    }
}
