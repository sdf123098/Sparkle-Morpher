package com.micaftic.morpher.client.renderer.preview;

/**
 * 经典 HUD 小人可见性策略（路线图 §22.2 auto-hide / F5 hide / 动作可见性，1.2.6）。
 *
 * <p>纯决策、无 Minecraft 依赖，便于单测。规则优先级：</p>
 * <ol>
 *   <li><b>F5 hide</b>：{@code hideInThirdPerson} 开启且当前非第一人称 → 隐藏；</li>
 *   <li><b>动作可见性</b>：{@code showOnActionOnly} 开启 → 仅在动作中显示；</li>
 *   <li><b>auto-hide</b>：{@code autoHideIdleSeconds > 0} 且空闲超过该秒数 → 隐藏；</li>
 *   <li>否则显示。</li>
 * </ol>
 *
 * <p>默认配置（全部关闭 / 0）恒返回显示，等价历史行为。</p>
 */
public final class PaperDollVisibilityPolicy {

    /** 可见性配置（来自配置文件，已解析为普通值）。 */
    public record Config(boolean hideInThirdPerson, boolean showOnActionOnly, double autoHideIdleSeconds) {
        public static Config alwaysVisible() {
            return new Config(false, false, 0.0d);
        }
    }

    /** 当前帧可见性输入。 */
    public record Inputs(boolean firstPersonCamera, boolean inAction, double idleSeconds) {
    }

    private PaperDollVisibilityPolicy() {
    }

    /** 依据配置与当前帧状态决定是否绘制小人。 */
    public static boolean shouldRender(Config config, Inputs inputs) {
        if (config == null || inputs == null) {
            return true;
        }
        if (config.hideInThirdPerson() && !inputs.firstPersonCamera()) {
            return false;
        }
        if (config.showOnActionOnly()) {
            return inputs.inAction();
        }
        if (config.autoHideIdleSeconds() > 0.0d && !inputs.inAction() && inputs.idleSeconds() >= config.autoHideIdleSeconds()) {
            return false;
        }
        return true;
    }
}
