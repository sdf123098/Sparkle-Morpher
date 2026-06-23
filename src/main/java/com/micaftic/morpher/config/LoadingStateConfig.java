package com.micaftic.morpher.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public class LoadingStateConfig {

    public static ModConfigSpec.BooleanValue DISABLE_LOADING_STATE_SCREEN;

    public static ModConfigSpec.EnumValue<Position> LOADING_STATE_POSITION;

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
        builder.pop();
    }
}