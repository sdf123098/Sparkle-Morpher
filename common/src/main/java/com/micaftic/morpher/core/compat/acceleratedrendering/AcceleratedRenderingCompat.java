package com.micaftic.morpher.core.compat.acceleratedrendering;

import com.micaftic.morpher.YesSteveModel;

import java.lang.reflect.Method;

/**
 * Optional compatibility boundary for Accelerated Rendering.
 *
 * Its ImmediatelyFast bridge can bind an otherwise private BufferSource to a global accelerated
 * provider. Geometry intended for a temporary FBO may then be submitted after the main target is
 * rebound. Old HUD rendering must use the vanilla pipelines while its FBO is active.
 */
public final class AcceleratedRenderingCompat {
    private static final String[] FEATURE_CLASSES = {
            "com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature",
            "com.github.argon4w.acceleratedrendering.features.items.AcceleratedItemRenderingFeature",
            "com.github.argon4w.acceleratedrendering.features.text.AcceleratedTextRenderingFeature"
    };

    private static boolean resolved;
    private static Method[] useVanillaPipeline;
    private static Method[] resetPipeline;
    private static boolean warned;

    private AcceleratedRenderingCompat() {
    }

    /** Returns true only when all three optional pipelines were switched successfully. */
    public static boolean enterVanillaPipeline() {
        resolve();
        if (useVanillaPipeline == null) {
            return false;
        }
        try {
            for (Method method : useVanillaPipeline) {
                method.invoke(null);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("enter", exception);
            resetBestEffort();
            return false;
        }
    }

    public static void exitVanillaPipeline(boolean entered) {
        if (entered) {
            resetBestEffort();
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        Method[] useMethods = new Method[FEATURE_CLASSES.length];
        Method[] resetMethods = new Method[FEATURE_CLASSES.length];
        try {
            ClassLoader loader = AcceleratedRenderingCompat.class.getClassLoader();
            for (int i = 0; i < FEATURE_CLASSES.length; i++) {
                Class<?> feature = Class.forName(FEATURE_CLASSES[i], false, loader);
                useMethods[i] = feature.getMethod("useVanillaPipeline");
                resetMethods[i] = feature.getMethod("resetPipeline");
            }
            useVanillaPipeline = useMethods;
            resetPipeline = resetMethods;
            YesSteveModel.LOGGER.info("[SM] Enabled Accelerated Rendering isolation for old HUD FBO");
        } catch (ClassNotFoundException exception) {
            // Optional mod is not installed. This is the normal path for a standalone SPM instance.
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("resolve", exception);
        }
    }

    private static void resetBestEffort() {
        if (resetPipeline == null) {
            return;
        }
        for (Method method : resetPipeline) {
            try {
                method.invoke(null);
            } catch (ReflectiveOperationException | LinkageError exception) {
                warnOnce("exit", exception);
            }
        }
    }

    private static void warnOnce(String phase, Throwable exception) {
        if (!warned) {
            warned = true;
            YesSteveModel.LOGGER.warn("[SM] Accelerated Rendering old HUD isolation failed during {}", phase, exception);
        }
    }
}
