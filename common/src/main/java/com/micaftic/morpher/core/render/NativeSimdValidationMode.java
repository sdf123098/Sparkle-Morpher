package com.micaftic.morpher.core.render;

/** Stable diagnostics contract consumed by render runtime code. */
public enum NativeSimdValidationMode {
    OFF,
    LOG_MISMATCH,
    STRICT_FALLBACK,
    CRASH_TEST
}
