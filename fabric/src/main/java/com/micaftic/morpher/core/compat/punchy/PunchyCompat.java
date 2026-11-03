package com.micaftic.morpher.core.compat.punchy;

import dev.architectury.injectables.annotations.ExpectPlatform;

/**
 * Punchy! (Modrinth slug {@code punchy-fpa}) detection.
 *
 * <p>Punchy! ships without source, so this deliberately does not reference any Punchy class or a
 * single exact mod id. It scans the loader mod list for a mod whose id or display name contains
 * {@code punchy}, which covers {@code punchy}, {@code punchy-fpa} and the display name
 * {@code Punchy!}.
 *
 * <p>Detection only. No rendering behaviour changes here until
 * {@code FirstPersonRenderDiagnostics} shows which render path Punchy! actually takes.
 */
public final class PunchyCompat {

    public static final String MODRINTH_SLUG = "punchy-fpa";

    private PunchyCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    /** Loader-reported mod id, or {@code null} when Punchy! is absent. */
    @ExpectPlatform
    public static String modId() {
        throw new AssertionError();
    }

    /** Loader-reported version, or {@code null} when Punchy! is absent. */
    @ExpectPlatform
    public static String version() {
        throw new AssertionError();
    }
}
