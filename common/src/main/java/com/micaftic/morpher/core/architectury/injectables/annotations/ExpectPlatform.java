package com.micaftic.morpher.core.architectury.injectables.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Stub for com.micaftic.morpher.core.architectury.injectables.annotations.ExpectPlatform.
 * Used to mark methods that have platform-specific implementations.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExpectPlatform {
}
