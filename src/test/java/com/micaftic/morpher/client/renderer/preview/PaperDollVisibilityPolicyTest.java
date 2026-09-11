package com.micaftic.morpher.client.renderer.preview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.2.6 §22.2 可见性策略契约：auto-hide / F5 hide / 动作可见性（MC-free）。
 *
 * <p>关键回归：默认配置恒显示（等价历史行为）。</p>
 */
class PaperDollVisibilityPolicyTest {

    private static final PaperDollVisibilityPolicy.Config DEFAULT = PaperDollVisibilityPolicy.Config.alwaysVisible();

    @Test
    void defaultConfigAlwaysRenders() {
        assertTrue(PaperDollVisibilityPolicy.shouldRender(DEFAULT,
                new PaperDollVisibilityPolicy.Inputs(true, false, 9999.0d)));
        assertTrue(PaperDollVisibilityPolicy.shouldRender(DEFAULT,
                new PaperDollVisibilityPolicy.Inputs(false, false, 9999.0d)));
    }

    @Test
    void nullInputsRenderDefensively() {
        assertTrue(PaperDollVisibilityPolicy.shouldRender(DEFAULT, null));
        assertTrue(PaperDollVisibilityPolicy.shouldRender(null, null));
    }

    @Test
    void hideInThirdPersonHidesOnlyOutsideFirstPerson() {
        PaperDollVisibilityPolicy.Config config = new PaperDollVisibilityPolicy.Config(true, false, 0.0d);
        assertFalse(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(false, false, 0.0d)), "third-person must hide");
        assertTrue(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, false, 0.0d)), "first-person must show");
    }

    @Test
    void showOnActionOnlyRequiresAction() {
        PaperDollVisibilityPolicy.Config config = new PaperDollVisibilityPolicy.Config(false, true, 0.0d);
        assertFalse(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, false, 0.0d)));
        assertTrue(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, true, 0.0d)));
    }

    @Test
    void autoHideOnlyAfterIdleThreshold() {
        PaperDollVisibilityPolicy.Config config = new PaperDollVisibilityPolicy.Config(false, false, 5.0d);
        assertTrue(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, false, 4.9d)), "below threshold stays visible");
        assertFalse(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, false, 5.0d)), "at threshold hides");
        assertTrue(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, true, 100.0d)), "action keeps it visible");
    }

    @Test
    void actionOnlyTakesPrecedenceOverAutoHide() {
        PaperDollVisibilityPolicy.Config config = new PaperDollVisibilityPolicy.Config(false, true, 1.0d);
        // 动作可见性要求「有动作」；空闲再久也无所谓。
        assertFalse(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, false, 100.0d)));
        assertTrue(PaperDollVisibilityPolicy.shouldRender(config,
                new PaperDollVisibilityPolicy.Inputs(true, true, 100.0d)));
    }
}
