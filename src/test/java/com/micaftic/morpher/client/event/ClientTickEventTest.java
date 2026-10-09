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
        Path eventPath = firstExisting(
                Path.of("common/src/main/java/com/micaftic/morpher/client/event/ClientTickEvent.java"),
                Path.of("src/neoforge/java/com/micaftic/morpher/client/event/ClientTickEvent.java"));
        String source = Files.readString(eventPath, StandardCharsets.UTF_8);

        assertEquals(1, occurrences(source,
                "runScheduledModelCacheTrim(tickCount, ClientModelManager::trimUnusedGpuCaches);"),
                "the normal client tick must use the periodic trim hook");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        for (int from = 0; (from = source.indexOf(needle, from)) >= 0; from += needle.length()) count++;
        return count;
    }

    private static Path firstExisting(Path... candidates) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            for (Path candidate : candidates) {
                Path resolved = directory.resolve(candidate);
                if (Files.isRegularFile(resolved)) return resolved;
            }
            directory = directory.getParent();
        }
        throw new IOException("ClientTickEvent source not found");
    }
}
