package com.micaftic.morpher.client.model;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuiltinActionResourceIsolationTest {
    private static final String PRESET = "com.micaftic.morpher.client.model.BuiltinDefaultActionPreset";
    private static final String ROOT = "assets/sparkle_morpher/action_presets/ysm/animations/";
    private static final Set<String> SOURCES = Set.of("main", "arm", "extra", "tac", "carryon", "parcool",
            "swem", "slashblade", "tlm", "immersive_melodies", "irons_spell_books", "fp_arm");

    @Test
    void loadsCompletePresetWithoutAnyDefaultModelResources() throws Exception {
        try (var loader = isolatedResources(false)) {
            Class<?> preset = Class.forName(PRESET, true, loader);
            Map<?, ?> files = (Map<?, ?>) preset.getMethod("playerAnimations").invoke(null);
            assertEquals(SOURCES, files.keySet());
            for (Object file : files.values()) {
                Map<?, ?> animations = (Map<?, ?>) file.getClass().getMethod("getAnimations").invoke(file);
                assertFalse(animations.isEmpty());
            }
            Map<?, ?> main = (Map<?, ?>) preset.getMethod("mainAnimations").invoke(null);
            Map<?, ?> arms = (Map<?, ?>) preset.getMethod("armAnimations").invoke(null);
            assertTrue(main.containsKey("idle"));
            assertTrue(main.containsKey("walk"));
            assertFalse(arms.isEmpty());
            assertSame(files, preset.getMethod("playerAnimations").invoke(null));
            assertTrue(loader.legacyRequests.isEmpty(), loader.legacyRequests.toString());
        }
    }

    @Test
    void missingIndependentResourceNeverFallsBackToBundledModel() throws Exception {
        try (var loader = isolatedResources(true)) {
            Class<?> preset = Class.forName(PRESET, true, loader);
            Map<?, ?> files = (Map<?, ?>) preset.getMethod("playerAnimations").invoke(null);
            Set<String> expected = new LinkedHashSet<>(SOURCES);
            expected.remove("main");
            assertEquals(expected, files.keySet());
            assertTrue(loader.legacyRequests.isEmpty(), loader.legacyRequests.toString());
        }
    }

    private static IsolatedResources isolatedResources(boolean hideMain) {
        URL classes = BuiltinDefaultActionPreset.class.getProtectionDomain().getCodeSource().getLocation();
        return new IsolatedResources(new URL[]{classes}, hideMain);
    }

    private static final class IsolatedResources extends URLClassLoader {
        final List<String> legacyRequests = new ArrayList<>();
        private final boolean hideMain;

        IsolatedResources(URL[] urls, boolean hideMain) {
            // A fresh application loader also prevents a warm preset cache hiding dependencies.
            super(urls, BuiltinActionResourceIsolationTest.class.getClassLoader());
            this.hideMain = hideMain;
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!PRESET.equals(name)) {
                return super.loadClass(name, resolve);
            }
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                loaded = findClass(name);
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }

        @Override
        public URL getResource(String name) {
            if (name.startsWith("assets/sparkle_morpher/builtin/default/")
                    || name.startsWith("assets/sparkle_morpher/builtin/external_ysm/")) {
                legacyRequests.add(name);
                return null;
            }
            if (hideMain && name.equals(ROOT + "main.animation.json")) {
                return null;
            }
            return super.getResource(name);
        }
    }
}
