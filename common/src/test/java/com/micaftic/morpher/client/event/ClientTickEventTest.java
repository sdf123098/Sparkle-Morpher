package com.micaftic.morpher.client.event;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientTickEventTest {
    @Test
    void scheduledTickRunsTheModelTrimHook() {
        AtomicInteger trims = new AtomicInteger();

        ClientTickEvent.runScheduledModelCacheTrim(64, trims::incrementAndGet);

        assertEquals(1, trims.get());
    }

    @Test
    void unscheduledTickDoesNotRunTheModelTrimHook() {
        AtomicInteger trims = new AtomicInteger();

        ClientTickEvent.runScheduledModelCacheTrim(63, trims::incrementAndGet);

        assertEquals(0, trims.get());
    }

    @Test
    void eachSixtyFourthTickRunsOneTrim() {
        AtomicInteger trims = new AtomicInteger();

        ClientTickEvent.runScheduledModelCacheTrim(64, trims::incrementAndGet);
        ClientTickEvent.runScheduledModelCacheTrim(127, trims::incrementAndGet);
        ClientTickEvent.runScheduledModelCacheTrim(128, trims::incrementAndGet);

        assertEquals(2, trims.get());
    }

    @Test
    void normalClientTickDelegatesToTheTestedTrimHook() throws IOException {
        String source = Files.readString(findClientTickEvent(), StandardCharsets.UTF_8);

        assertEquals(1, occurrences(source,
                "runScheduledModelCacheTrim(tickCount, ClientModelManager::trimUnusedGpuCaches);"),
                "the normal client tick must use the periodic trim hook");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int from = 0; (from = source.indexOf(needle, from)) >= 0; from += needle.length()) count++;
        return count;
    }

    /**
     * Locates ClientTickEvent.java without hard-coding one physical layout: the class is
     * loader-divergent, so it lives in the fabric tree on the fabric loader and in the
     * neoforge tree on the neoforge loader, never in the shared common/ layer.
     */
    private static Path findClientTickEvent() throws IOException {
        Path relative = Path.of("com", "micaftic", "morpher", "client", "event", "ClientTickEvent.java");
        Path[] candidates = {
                Path.of("fabric", "src", "main", "java").resolve(relative),
                Path.of("neoforge", "src", "main", "java").resolve(relative),
                Path.of("common", "src", "main", "java").resolve(relative),
                Path.of("src", "main", "java").resolve(relative)
        };
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path resolved = base.resolve(candidate).normalize();
                if (Files.isRegularFile(resolved)) {
                    return resolved;
                }
            }
        }
        throw new IOException("ClientTickEvent source not found under any loader source root");
    }
}
