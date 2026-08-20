package com.micaftic.morpher.core.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.2.2 §8：Backend Capability Model 基础验收（1.21.1 常量集版）。
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
    void constantCapabilitiesMatch1211Surface() {
        SmGraphicsCapabilities caps = SmGraphicsCapabilities.current();
        assertFalse(caps.supportsPortablePipeline());
        assertTrue(caps.supportsGpuMesh());
        assertTrue(caps.supportsGpuSkinning());
        assertTrue(caps.supportsCustomShader());
        assertTrue(caps.supportsRawOpenGl());
        assertFalse(caps.supportsGpuTimestamp());
        assertFalse(caps.supportsAsyncUpload());
        assertTrue(caps.supportsStorageBuffer());
    }

    @Test
    void capabilityImplicationsHold() {
        SmGraphicsCapabilities caps = SmGraphicsCapabilities.current();
        if (caps.supportsPortablePipeline()) {
            assertTrue(caps.supportsGpuMesh());
            assertTrue(caps.supportsCustomShader());
        }
        if (caps.supportsGpuSkinning()) {
            assertTrue(caps.supportsCustomShader());
        }
        if (caps.supportsGpuMesh()) {
            assertTrue(caps.supportsStorageBuffer());
        }
    }
}

