package com.micaftic.morpher.architecture;

import com.micaftic.morpher.core.api.camera.CameraApi;
import com.micaftic.morpher.core.api.resource.ResourceApi;
import com.micaftic.morpher.core.api.version.VersionAdapterSurface;
import com.micaftic.morpher.core.api.version.VersionAdapters;
import com.micaftic.morpher.core.api.version.MinecraftVersionAdapter;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionAdapterContractTest {
    @Test void exposesEveryStableApiSurface() { var capabilities = VersionAdapters.capabilities(); for (VersionAdapterSurface surface : VersionAdapterSurface.values()) assertTrue(capabilities.supports(surface), "Unsupported stable surface: " + surface); }
    @Test void keepsResourceAndCameraValuesVersionNeutral() { assertEquals("sparkle_morpher:model", ResourceApi.parse("sparkle_morpher:model").asString()); assertTrue(CameraApi.snapshot(90.0f, 20.0f, true).firstPerson()); }
    @Test void stableSurfaceCapabilityMustBeDeclaredByEachVersionAdapter() throws NoSuchMethodException { var supports = MinecraftVersionAdapter.class.getDeclaredMethod("supports", VersionAdapterSurface.class); assertTrue(Modifier.isAbstract(supports.getModifiers()), "Version adapters must explicitly declare every stable surface"); }
}
