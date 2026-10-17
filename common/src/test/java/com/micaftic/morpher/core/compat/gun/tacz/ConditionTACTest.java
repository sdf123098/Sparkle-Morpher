package com.micaftic.morpher.core.compat.gun.tacz;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionTACTest {
    @Test
    void matchesNamespacedGunOnlyForRegisteredAction() {
        ConditionTAC condition = new ConditionTAC();
        condition.addTest("tac:hold$tacz:ak47");
        assertEquals("tac:hold$tacz:ak47", condition.doTest(Identifier.parse("tacz:ak47"), "tac:hold:"));
        assertEquals("", condition.doTest(Identifier.parse("tacz:ak47"), "tac:aim:"));
        assertEquals("", condition.doTest(Identifier.parse("tacz:glock_17"), "tac:hold:"));
    }

    @Test
    void ignoresMalformedIdsAndUnrelatedAnimations() {
        ConditionTAC condition = new ConditionTAC();
        assertDoesNotThrow(() -> {
            condition.addTest("tac:hold$TACZ:AK47");
            condition.addTest("tac:hold$tacz:bad:id");
            condition.addTest("tac:hold$");
            condition.addTest("idle$tacz:ak47");
        });
        assertEquals("", condition.doTest(Identifier.parse("tacz:ak47"), "tac:hold:"));
        assertEquals("", condition.doTest((Identifier) null, "tac:hold:"));
    }
}
