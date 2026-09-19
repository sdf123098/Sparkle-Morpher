package com.micaftic.morpher.fakeplayer;

import com.micaftic.morpher.core.target.TargetCapability;
import com.micaftic.morpher.core.target.TargetCapabilities;
import com.micaftic.morpher.core.target.TargetType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FakePlayerTargetRulesTest {
    @Test
    void providerNamesClassifyOnlyKnownFakePlayerImplementations() {
        assertTrue(FakePlayerTargetRules.looksLikeFakeProvider("carpet.fake.FakePlayer"));
        assertTrue(FakePlayerTargetRules.looksLikeFakeProvider("rollinggate.SiliconedollConnection"));
        assertTrue(FakePlayerTargetRules.looksLikeFakeProvider("fake_client_connection"));
        assertFalse(FakePlayerTargetRules.looksLikeFakeProvider("net.minecraft.server.level.ServerPlayer"));
        assertFalse(FakePlayerTargetRules.looksLikeFakeProvider("touhou_little_maid.entity.EntityMaid"));
    }

    @Test
    void targetCapabilitiesAreExplicitAndUnknownTargetsHaveNone() {
        assertEquals(TargetType.UNKNOWN, FakePlayerTargetRules.targetType(null));
        assertTrue(TargetCapabilities.supports(TargetType.FAKE_PLAYER, TargetCapability.MODEL));
        assertTrue(TargetCapabilities.supports(TargetType.MAID, TargetCapability.PARTS));
        assertFalse(TargetCapabilities.supports(TargetType.UNKNOWN, TargetCapability.MODEL));
        assertTrue(TargetCapabilities.mutableCopy(TargetType.PLAYER).contains(TargetCapability.TRANSFORM));
    }
}

