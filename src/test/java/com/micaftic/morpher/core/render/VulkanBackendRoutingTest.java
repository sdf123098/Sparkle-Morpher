package com.micaftic.morpher.core.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RULE-GFX-5：Blaze3D portable 路径按「能力」选择，而不是按后端名字
 * （旧的 {@code backend == VULKAN} 判断已移除）。
 */
class VulkanBackendRoutingTest {

    private static SmGraphicsCapabilities caps(boolean portable, boolean rawOpenGl) {
        return new SmGraphicsCapabilities(
                portable, /*gpuMesh*/ portable, /*gpuSkinning*/ portable, /*customShader*/ portable,
                rawOpenGl, /*timestamp*/ false, /*asyncUpload*/ false, /*storageBuffer*/ portable);
    }

    @Test
    void usesPortablePathWhenRawOpenGlUnavailable() {
        // Vulkan 类设备：支持 portable、无法用 raw GL → 走 Blaze3D。
        assertTrue(RenderBackendDecision.supportsBlaze3DPortablePath(caps(true, false), true));
    }

    @Test
    void keepsRawOpenGlFastPathWhenAvailable() {
        // OpenGL 设备：raw GL 更快，保持既有快路径，不切到 Blaze3D。
        assertFalse(RenderBackendDecision.supportsBlaze3DPortablePath(caps(true, true), true));
    }

    @Test
    void requiresPortablePipelineCapability() {
        // 无 portable 能力（旧版本 MC / 探测失败）时不得启用。
        assertFalse(RenderBackendDecision.supportsBlaze3DPortablePath(caps(false, false), true));
    }

    @Test
    void requiresBlaze3DVersionSupport() {
        // 该 MC 版本不提供 Blaze3D GPU 管线时不得启用。
        assertFalse(RenderBackendDecision.supportsBlaze3DPortablePath(caps(true, false), false));
    }
}
