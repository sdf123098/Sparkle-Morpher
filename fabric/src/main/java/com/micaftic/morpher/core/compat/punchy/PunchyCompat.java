package com.micaftic.morpher.core.compat.punchy;


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

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.punchy.fabric.PunchyCompatImpl.isLoaded();
    }

    /** Loader-reported mod id, or {@code null} when Punchy! is absent. */
    public static String modId() {
        return com.micaftic.morpher.core.compat.punchy.fabric.PunchyCompatImpl.modId();
    }

    /** Loader-reported version, or {@code null} when Punchy! is absent. */
    public static String version() {
        return com.micaftic.morpher.core.compat.punchy.fabric.PunchyCompatImpl.version();
    }
}
