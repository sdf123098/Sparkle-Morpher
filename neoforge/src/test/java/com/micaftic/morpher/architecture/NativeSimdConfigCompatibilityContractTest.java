package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeSimdConfigCompatibilityContractTest {
    @Test
    void onePointTwoOneOneNativeSimdDependenciesArePresent() throws IOException {
        // GeneralConfig is a loader-divergent class: it lives in the neoforge tree on the
        // neoforge loader and in the fabric tree on the fabric loader, never in common/.
        String config = RepoSourceLookup.readSource(
                "com/micaftic/morpher/config/GeneralConfig.java");
        assertTrue(config.contains("enum NativeSimdPolicy"));
        assertTrue(config.contains("NATIVE_SIMD_POLICY"));
        assertTrue(config.contains("safeGet(net.neoforged.neoforge.common.ModConfigSpec.EnumValue"));

        String log = RepoSourceLookup.readSource("com/micaftic/morpher/core/gpu/GpuDebugLog.java");
        assertTrue(log.contains("static void error"));
    }
}
