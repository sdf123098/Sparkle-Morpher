package com.micaftic.morpher.core.gpu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * in-pipeline GPU 绘制通道的开关契约。
 *
 * <p>开关来自模组配置 {@code EnableBlaze3DInPipelineDraw}（游戏内可切换，无需 JVM 参数），
 * 默认关闭。关闭时 {@code tryDefer} 恒 false，调用方走既有提交/回放路径，行为与引入前一致。</p>
 */
class Blaze3DModelFramePassTest {

    @Test
    void enabledByDefaultOff() {
        // 配置默认 false：本通道默认不得接管任何绘制（保证行为与引入前一致）。
        // isEnabled() 读模组配置；未注册/不可读时按关闭处理。
        assertFalse(Blaze3DModelFramePass.isEnabled());
    }

    @Test
    void deferIsInertWhileDisabled() {
        // 关闭状态下 tryDefer 恒 false，且三个入口都必须可安全调用（传 null 也不得抛）。
        assertFalse(Blaze3DModelFramePass.tryDefer(
                null, null, null, null, 0, 0, 0, 0, 1f, 1f, 1f, 1f, null, false));
        Blaze3DModelFramePass.beginFrame();
        Blaze3DModelFramePass.endFrame();
        Blaze3DModelFramePass.appendPass(null, null);
        Blaze3DModelFramePass.appendPass(null, null);
    }
}
