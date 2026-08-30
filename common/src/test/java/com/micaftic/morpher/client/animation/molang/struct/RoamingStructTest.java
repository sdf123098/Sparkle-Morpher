package com.micaftic.morpher.client.animation.molang.struct;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RoamingStructTest {

    @Test
    void acceptsMissingServerVariablesForLocalSettingsRestore() {
        RoamingStruct target = new RoamingStruct(42, null);

        assertNotNull(target);
    }
}
