package com.micaftic.morpher.core.compat.punchy;

import com.micaftic.morpher.YesSteveModel;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

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

    private static volatile boolean resolved;
    private static volatile String modId;
    private static volatile String version;

    private PunchyCompat() {
    }

    public static boolean isLoaded() {
        resolve();
        return modId != null;
    }

    /** Loader-reported mod id, or {@code null} when Punchy! is absent. */
    public static String modId() {
        resolve();
        return modId;
    }

    /** Loader-reported version, or {@code null} when Punchy! is absent. */
    public static String version() {
        resolve();
        return version;
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        synchronized (PunchyCompat.class) {
            if (resolved) {
                return;
            }
            String foundId = null;
            String foundVersion = null;
            try {
                for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                    String id = container.getMetadata().getId();
                    String name = container.getMetadata().getName();
                    if (PunchyModMatcher.matches(id, name)) {
                        foundId = id;
                        foundVersion = container.getMetadata().getVersion().getFriendlyString();
                        break;
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                // Never let detection break rendering; log instead of silently reporting absent.
                YesSteveModel.LOGGER.warn("Punchy! detection failed; assuming not installed", e);
            }
            modId = foundId;
            version = foundVersion;
            resolved = true;
        }
    }
}
