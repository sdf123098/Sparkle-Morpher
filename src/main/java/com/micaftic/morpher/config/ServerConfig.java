package com.micaftic.morpher.config;

import com.google.common.collect.Lists;

import java.util.List;

public class ServerConfig {

    public static net.neoforged.neoforge.common.ModConfigSpec.IntValue THREAD_COUNT;

    public static net.neoforged.neoforge.common.ModConfigSpec.IntValue BANDWIDTH_LIMIT;

    public static net.neoforged.neoforge.common.ModConfigSpec.IntValue PLAYER_SYNC_TIMEOUT;

    public static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue LOW_BANDWIDTH_USAGE;

    public static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue CAN_SWITCH_MODEL;

    public static net.neoforged.neoforge.common.ModConfigSpec.BooleanValue ALLOW_MODEL_UPLOAD;

    public static net.neoforged.neoforge.common.ModConfigSpec.IntValue MODEL_UPLOAD_MAX_MB;

    public static net.neoforged.neoforge.common.ModConfigSpec.IntValue MODEL_UPLOAD_CHUNKS_PER_TICK;

    public static net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> DEFAULT_MODEL_ID;

    public static net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<String> DEFAULT_MODEL_TEXTURE;

    public static net.neoforged.neoforge.common.ModConfigSpec.IntValue ACCEPT_SOUND_FX;

    public static net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<List<String>> CLIENT_NOT_DISPLAY_MODELS;

    public static net.neoforged.neoforge.common.ModConfigSpec buildSpec() {
        net.neoforged.neoforge.common.ModConfigSpec.Builder builder = new net.neoforged.neoforge.common.ModConfigSpec.Builder();
        defineOptions(builder);
        return builder.build();
    }

    private static void defineOptions(net.neoforged.neoforge.common.ModConfigSpec.Builder builder) {
        builder.comment("The default model ID when a player first enters the game");
        DEFAULT_MODEL_ID = builder.define("DefaultModelId", "default");
        builder.comment("The default model texture when a player first enters the game");
        DEFAULT_MODEL_TEXTURE = builder.define("DefaultModelTexture", "default");
        builder.comment("Whether or not players are allowed to switch models");
        CAN_SWITCH_MODEL = builder.define("CanSwitchModel", true);
        builder.comment("Whether clients are allowed to upload .ysm/.zip files into the server custom model folder");
        ALLOW_MODEL_UPLOAD = builder.define("AllowModelUpload", true);
        builder.comment("Maximum size of a single uploaded model file, in MiB");
        MODEL_UPLOAD_MAX_MB = builder.defineInRange("ModelUploadMaxMiB", 128, 1, 512);
        builder.comment("How many upload chunks a client may send per tick");
        MODEL_UPLOAD_CHUNKS_PER_TICK = builder.defineInRange("ModelUploadChunksPerTick", 4, 1, 32);
        builder.comment("Models that are not displayed on the client model selection screen");
        builder.comment("Example: [\"default\", \"misc_3_default_boy\", \"misc_1_alex\", \"misc_2_steve\", \"wine_fox_1_taisho_maid\", \"wine_fox_7_jk\"]");
        CLIENT_NOT_DISPLAY_MODELS = builder.define("ClientNotDisplayModels", Lists.newArrayList());
        builder.push("server_scheduler");
        builder.comment("Concurrent level for processing models. Value 0 means AUTO.");
        THREAD_COUNT = builder.defineInRange("ThreadCount", 0, 0, Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
        builder.comment("Bandwidth limitation during distributing models to players.(In Mbps)");
        BANDWIDTH_LIMIT = builder.defineInRange("BandwidthLimit", 5, 1, 999);
        builder.comment("Timeout for players to respond to synchronization. Value not greater than 10 means AUTO.(In seconds)");
        PLAYER_SYNC_TIMEOUT = builder.defineInRange("PlayerSyncTimeout", 0, 0, 120);
        builder.comment("Suppress network synchronization of partial features to reduce bandwidth usage");
        builder.comment("Only effective when there are tons of players");
        LOW_BANDWIDTH_USAGE = builder.define("LowBandwidthUsage", false);
        builder.comment("Skip sound effect processing to reduce server bandwidth and client memory usage");
        builder.comment("0: Accept all sounds (Default)");
        builder.comment("1: Accept short sounds only (Shorter than 4s and smaller than 40KB)");
        builder.comment("2: Reject all sounds (Not recommended)");
        builder.comment("Note: Takes effect after model reloading. Increasing this option does not cause model resynchronization, whereas decreasing it does.");
        ACCEPT_SOUND_FX = builder.defineInRange("AcceptSoundFX", 0, 0, 2);
        builder.pop();
    }
}
