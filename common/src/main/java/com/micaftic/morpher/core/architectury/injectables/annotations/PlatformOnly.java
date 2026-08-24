package com.micaftic.morpher.core.architectury.injectables.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stub for com.micaftic.morpher.core.architectury.injectables.annotations.PlatformOnly.
 * Used to mark elements that are only available on specific platforms.
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PlatformOnly {
    String[] value() default {};
}
