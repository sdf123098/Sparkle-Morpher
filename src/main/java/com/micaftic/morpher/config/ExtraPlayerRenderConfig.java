package com.micaftic.morpher.config;

import com.micaftic.morpher.core.architectury.platform.Platform;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ExtraPlayerRenderConfig {

    public static ModConfigSpec.BooleanValue DISABLE_PLAYER_RENDER;

    public static ModConfigSpec.BooleanValue ENABLE_MODERN_HUD_RENDER;

    /** 经典 HUD 布局（键名保持 PlayerPosX/... 不变，兼容既有配置文件）。 */
    public static HudLayoutConfig CLASSIC_HUD_LAYOUT;

    /** 现代 HUD 布局（独立键名 ModernPlayerPosX/...，与经典 HUD 互不干扰）。 */
    public static HudLayoutConfig MODERN_HUD_LAYOUT;

    public static void define(ModConfigSpec.Builder builder) {
        builder.push("extra_player_render");
        builder.comment("Legacy inverse switch for classic HUD rendering");
        DISABLE_PLAYER_RENDER = builder.define("DisablePlayerRender", Platform.isModLoaded("figura"));
        builder.comment("Whether to enable the independent modern HUD renderer");
        ENABLE_MODERN_HUD_RENDER = builder.define("EnableModernHudRender", true);
        builder.comment("Classic HUD layout: position, continuous scale and yaw");
        CLASSIC_HUD_LAYOUT = HudLayoutConfig.define(builder, "", 10, 10, 40.0d, 5.0d);
        builder.comment("Modern HUD layout: position, continuous scale and yaw (independent of classic HUD)");
        MODERN_HUD_LAYOUT = HudLayoutConfig.define(builder, "Modern", 10, 10, 40.0d, 5.0d);
        builder.pop();
    }
}
