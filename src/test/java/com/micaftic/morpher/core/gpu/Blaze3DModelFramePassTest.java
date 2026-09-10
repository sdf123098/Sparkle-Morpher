package com.micaftic.morpher.core.gpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * in-pipeline GPU 绘制通道的开关契约。
 *
 * <p>本通道默认必须<b>关闭</b>：关闭时 {@code tryDefer} 恒 false，调用方走既有提交/回放路径，
 * 行为与改动前完全一致。只有显式 {@code "true"} 才启用——这条不变量保证新增路径不会因为
 * 属性缺失/拼写错误而意外生效。</p>
 */
class Blaze3DModelFramePassTest {

    @Test
    void onlyExplicitTrueEnablesTheChannel() {
        assertTrue(Blaze3DModelFramePass.isEnabledValue("true"));
        assertTrue(Blaze3DModelFramePass.isEnabledValue("TRUE"));
        assertTrue(Blaze3DModelFramePass.isEnabledValue("True"));
    }

    @Test
    void absentOrAnyOtherValueKeepsItOff() {
        assertFalse(Blaze3DModelFramePass.isEnabledValue(null), "unset property must stay disabled");
        assertFalse(Blaze3DModelFramePass.isEnabledValue(""), "empty must stay disabled");
        assertFalse(Blaze3DModelFramePass.isEnabledValue("false"));
        assertFalse(Blaze3DModelFramePass.isEnabledValue("1"));
        assertFalse(Blaze3DModelFramePass.isEnabledValue("yes"));
        assertFalse(Blaze3DModelFramePass.isEnabledValue(" true"), "no trimming: stays disabled");
    }

    @Test
    void deferIsInertWhileDisabled() {
        // 默认（未设属性）下不得接管任何绘制，且 begin/end 必须可安全调用。
        assertFalse(Blaze3DModelFramePass.isEnabled());
        assertFalse(Blaze3DModelFramePass.tryDefer(
                null, null, null, null, 0, 0, 0, 0, 1f, 1f, 1f, 1f, null, false));
        Blaze3DModelFramePass.beginFrame();
        Blaze3DModelFramePass.endFrame();
        // appendPass 在禁用/空列表时必须是 no-op（传 null 也不得抛）。
        Blaze3DModelFramePass.appendPass(null, null);
    }
}
