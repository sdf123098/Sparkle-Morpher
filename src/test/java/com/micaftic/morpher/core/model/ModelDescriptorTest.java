package com.micaftic.morpher.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * R5.2 ModelDescriptor 测试：不可变描述、null 归一化、equals/hashCode。
 */
class ModelDescriptorTest {

    @Test
    void descriptor_holdsIdentityAndLightMetadata() {
        ModelRef ref = ModelRef.of(ModelSourceType.LOCAL, "cirno");
        ModelDescriptor descriptor = new ModelDescriptor(ref, "琪露诺", "YSM_NATIVE");
        assertEquals(ref, descriptor.ref());
        assertEquals("琪露诺", descriptor.displayName());
        assertEquals("YSM_NATIVE", descriptor.format());
    }

    @Test
    void descriptor_normalizesNullToAbsent() {
        ModelDescriptor descriptor = new ModelDescriptor(ModelRef.of(ModelSourceType.LOCAL, "cirno"), null, null);
        assertNull(descriptor.displayName());
        assertNull(descriptor.format());
    }

    @Test
    void descriptor_equalsHashCode() {
        ModelDescriptor a = new ModelDescriptor(ModelRef.of(ModelSourceType.LOCAL, "cirno"), "A", "YSM_NATIVE");
        ModelDescriptor b = new ModelDescriptor(ModelRef.of(ModelSourceType.LOCAL, "cirno"), "A", "YSM_NATIVE");
        ModelDescriptor c = new ModelDescriptor(ModelRef.of(ModelSourceType.LEGACY_SERVER, "cirno"), "A", "YSM_NATIVE");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
