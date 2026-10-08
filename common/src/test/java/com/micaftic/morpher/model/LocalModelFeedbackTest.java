package com.micaftic.morpher.model;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LocalModelFeedbackTest {
    @Test void freezes_evaluated_values_and_rejects_non_finite_results() {
        var source = new HashMap<>(Map.of("variable.pose", 2f));
        var result = new LocalModelFeedback(42, source, 7);
        source.put("variable.pose", 9f);
        assertEquals(2f, result.stringValues().get("variable.pose"));
        assertThrows(UnsupportedOperationException.class, () -> result.stringValues().clear());
        assertThrows(IllegalArgumentException.class,
            () -> new LocalModelFeedback(42, Map.of("variable.pose", Float.NaN), 7));
    }
}
