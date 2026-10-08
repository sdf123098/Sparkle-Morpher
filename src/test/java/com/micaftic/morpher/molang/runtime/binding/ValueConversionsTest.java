package com.micaftic.morpher.molang.runtime.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueConversionsTest {
    @Test
    void nonFiniteNumbersBecomeZeroBeforeTheyReachAnimationVariables() {
        assertEquals(0.0f, ValueConversions.asFloat(Float.POSITIVE_INFINITY));
        assertEquals(0.0f, ValueConversions.asFloat(Float.NEGATIVE_INFINITY));
        assertEquals(0.0d, ValueConversions.asDouble(Double.POSITIVE_INFINITY));
        assertEquals(0.0d, ValueConversions.asDouble(Double.NaN));
    }
}
