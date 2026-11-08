package com.micaftic.morpher.core.api;

import com.micaftic.morpher.core.api.loader.LoaderKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** A merged compilation unit must invoke its actual loader bridge without a common-module transformer. */
final class FabricPlatformBridgeTest {
    @Test void startupPlatformIdentityIsExecutableWithoutArchitecturyTransformation() {
        assertEquals("Fabric", PlatformAPI.getPlatformName());
        assertEquals(LoaderKind.FABRIC, PlatformAPI.loaderKind());
    }
}
