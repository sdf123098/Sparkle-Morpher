package com.micaftic.morpher.core.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.2.2 §8：Backend Capability Model 基础验收。
 *
 * <p>只验证 record 语义与能力蕴含关系，不断言具体布尔值（探测结果依赖
 * 运行环境/后端）。current() 在 26.2 loom 测试 classpath 下可安全调用
 * （Blaze3D 反射探测 + SmGraphicsBackendDetector 的 null-device 降级路径）。
 */
class SmGraphicsCapabilitiesTest {

    @Test
    void noneSentinelIsAllFalse() {
        assertFalse(SmGraphicsCapabilities.NONE.supportsPortablePipeline());
        assertFalse(SmGraphicsCapabilities.NONE.supportsGpuMesh());
        assertFalse(SmGraphicsCapabilities.NONE.supportsGpuSkinning());
        assertFalse(SmGraphicsCapabilities.NONE.supportsCustomShader());
        assertFalse(SmGraphicsCapabilities.NONE.supportsRawOpenGl());
        assertFalse(SmGraphicsCapabilities.NONE.supportsGpuTimestamp());
        assertFalse(SmGraphicsCapabilities.NONE.supportsAsyncUpload());
        assertFalse(SmGraphicsCapabilities.NONE.supportsStorageBuffer());
    }

    @Test
    void summaryIsStableAndDescriptive() {
        String s = SmGraphicsCapabilities.NONE.summary();
        assertNotNull(s);
        assertTrue(s.contains("portablePipeline="));
        assertTrue(s.contains("gpuSkinning="));
        assertTrue(s.contains("rawOpenGl="));
        assertTrue(s.contains("storageBuffer="));
    }

    @Test
    void currentIsCallableWithoutRenderingDevice() {
        // 无渲染线程/device 时不得抛异常（detector 降级为 UNKNOWN + rawOpenGl=false）。
        SmGraphicsCapabilities caps = SmGraphicsCapabilities.current();
        assertNotNull(caps);
    }

    @Test
    void capabilityImplicationsHold() {
        // 蕴含关系：portable pipeline 需要 mesh；GPU skinning 需要 portable + custom shader。
        SmGraphicsCapabilities caps = SmGraphicsCapabilities.current();
        if (caps.supportsPortablePipeline()) {
            assertTrue(caps.supportsGpuMesh());
            assertTrue(caps.supportsCustomShader());
        }
        if (caps.supportsGpuSkinning()) {
            assertTrue(caps.supportsPortablePipeline());
            assertTrue(caps.supportsCustomShader());
        }
        if (caps.supportsGpuMesh()) {
            assertTrue(caps.supportsStorageBuffer());
        }
    }

    @Test
    void reportCachingIsIdempotent() {
        Blaze3D26_2Capability.resetForTests();
        Blaze3D26_2Capability.Report a = Blaze3D26_2Capability.report();
        Blaze3D26_2Capability.Report b = Blaze3D26_2Capability.report();
        assertEquals(a, b);
        Blaze3D26_2Capability.resetForTests();
    }
}