package com.micaftic.morpher.core.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanBackendRoutingTest {

    @Test
    void Blaze3DPathIsVulkanOnly() {
        assertTrue(RenderBackendDecision.isVulkanBlaze3DBackend(SmGraphicsBackend.VULKAN));
        assertFalse(RenderBackendDecision.isVulkanBlaze3DBackend(SmGraphicsBackend.OPENGL));
        assertFalse(RenderBackendDecision.isVulkanBlaze3DBackend(SmGraphicsBackend.UNKNOWN));
    }
}
