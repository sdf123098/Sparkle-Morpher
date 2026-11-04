package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSimdConfigCompatibilityContractTest {
    @Test
    void onePointTwoOneOneNativeSimdDependenciesArePresent() throws IOException {
        // GeneralConfig is a loader-divergent class: it lives in the fabric tree on the
        // fabric loader and in the neoforge tree on the neoforge loader, never in common/.
        String config = RepoSourceLookup.readSource(
                "com/micaftic/morpher/config/GeneralConfig.java");
        assertTrue(config.contains("enum NativeSimdPolicy"));
        assertTrue(config.contains("NATIVE_SIMD_POLICY"));
        assertTrue(config.contains("safeGet(ForgeConfigSpec.EnumValue"));

        String log = RepoSourceLookup.readSource("com/micaftic/morpher/core/gpu/GpuDebugLog.java");
        assertTrue(log.contains("static void error"));
    }
}
