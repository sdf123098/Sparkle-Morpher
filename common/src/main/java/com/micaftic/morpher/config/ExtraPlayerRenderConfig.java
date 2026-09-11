package com.micaftic.morpher.config;

import dev.architectury.platform.Platform;
import com.micaftic.morpher.client.renderer.preview.PaperDollLayout;
import net.minecraftforge.common.ForgeConfigSpec;

public class ExtraPlayerRenderConfig {

    public static ForgeConfigSpec.BooleanValue DISABLE_PLAYER_RENDER;

    public static ForgeConfigSpec.BooleanValue ENABLE_MODERN_HUD_RENDER;

    /** 经典 HUD 布局（键名保持 PlayerPosX/... 不变，兼容既有配置文件）。 */
    public static HudLayoutConfig CLASSIC_HUD_LAYOUT;

    /** 现代 HUD 布局（独立键名 ModernPlayerPosX/...，与经典 HUD 互不干扰）。 */
    public static HudLayoutConfig MODERN_HUD_LAYOUT;


    /** 经典 HUD 小人头部朝向模式（默认 STRAIGHT，等价历史行为）。 */
    public static ForgeConfigSpec.EnumValue<HeadMode> CLASSIC_HUD_HEAD_MODE;

    /** 经典 HUD 小人锚点（默认 TOP_LEFT，等价历史「绝对像素位置」）。 */
    public static ForgeConfigSpec.EnumValue<PaperDollLayout.Anchor> CLASSIC_HUD_ANCHOR;

    /** 第三人称（F5）时隐藏经典 HUD 小人。 */
    public static ForgeConfigSpec.BooleanValue CLASSIC_HUD_HIDE_IN_THIRD_PERSON;

    /** 经典 HUD 小人空闲自动隐藏秒数（0 = 不自动隐藏）。 */
    public static ForgeConfigSpec.IntValue CLASSIC_HUD_AUTO_HIDE_IDLE_SECONDS;

    /** 经典 HUD 小人仅在动作中显示（动作可见性；优先于 auto-hide）。 */
    public static ForgeConfigSpec.BooleanValue CLASSIC_HUD_SHOW_ON_ACTION_ONLY;

    /** 经典 HUD 小人骑乘时随载具 yaw 对齐。 */
    public static ForgeConfigSpec.BooleanValue CLASSIC_HUD_ALIGN_WITH_VEHICLE;

    /** 经典 HUD 小人头部朝向模式（路线图 22.2 head mode）。 */
    public enum HeadMode {
        STRAIGHT,
        FOLLOW
    }

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("extra_player_render");
        builder.comment("Legacy inverse switch for classic HUD rendering");
        DISABLE_PLAYER_RENDER = builder.define("DisablePlayerRender", false);
        builder.comment("Whether to enable the independent modern HUD renderer");
        ENABLE_MODERN_HUD_RENDER = builder.define("EnableModernHudRender", false);
        builder.comment("Classic HUD layout: position, continuous scale and yaw");
        CLASSIC_HUD_LAYOUT = HudLayoutConfig.define(builder, "", 10, 10, 40.0d, 0.0d);
        builder.comment("Modern HUD layout: position, continuous scale and yaw (independent of classic HUD)");
        MODERN_HUD_LAYOUT = HudLayoutConfig.define(builder, "Modern", 10, 10, 40.0d, 0.0d);
        builder.comment("Classic HUD doll head mode: STRAIGHT (head faces same way as body) or FOLLOW (head tracks the player's look direction)");
        CLASSIC_HUD_HEAD_MODE = builder.defineEnum("ClassicHudHeadMode", HeadMode.STRAIGHT);
        builder.comment("Classic HUD doll anchor corner; the X/Y layout values become offsets from this anchor (TOP_LEFT keeps legacy absolute positions)");
        CLASSIC_HUD_ANCHOR = builder.defineEnum("ClassicHudAnchor", PaperDollLayout.Anchor.TOP_LEFT);
        builder.comment("Hide the classic HUD doll in third-person view (F5)");
        CLASSIC_HUD_HIDE_IN_THIRD_PERSON = builder.define("ClassicHudHideInThirdPerson", false);
        builder.comment("Auto-hide the classic HUD doll after this many idle seconds (0 disables auto-hide)");
        CLASSIC_HUD_AUTO_HIDE_IDLE_SECONDS = builder.defineInRange("ClassicHudAutoHideIdleSeconds", 0, 0, 3600);
        builder.comment("Show the classic HUD doll only while the player is performing an action");
        CLASSIC_HUD_SHOW_ON_ACTION_ONLY = builder.define("ClassicHudShowOnActionOnly", false);
        builder.comment("Align the classic HUD doll with the ridden vehicle's yaw");
        CLASSIC_HUD_ALIGN_WITH_VEHICLE = builder.define("ClassicHudAlignWithVehicle", false);
        builder.pop();
    }
}
