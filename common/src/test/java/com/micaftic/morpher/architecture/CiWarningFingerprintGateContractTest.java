package com.micaftic.morpher.architecture;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CiWarningFingerprintGateContractTest {
    @Test
    void ciPinsTheDiagnosticInputsAndChecksBothDistributionCompiles() throws IOException {
        Path workflow = findFromWorkspace(Path.of(".github/workflows/ci.yml"));
        String ci = Files.readString(workflow, StandardCharsets.UTF_8);
        Path repoRoot = workflow.getParent().getParent().getParent();

        String build = Files.readString(repoRoot.resolve("scripts/ci-build.sh"));
        assertTrue(ci.contains("COMPILE_TASK: ${{ matrix.compile_task }}"));
        assertTrue(ci.contains("DIAGNOSTIC_BRANCH: ${{ matrix.diagnostic_branch }}"));
        assertTrue(build.contains("for dist in native curseforge"), "Both distributions must be built and tested");
        assertTrue(build.contains("--no-build-cache"), "A restored cache must not bypass compiler diagnostics");
        assertTrue(build.contains("--warning-mode fail"), "Gradle deprecations must fail CI");
        assertTrue(build.contains("-Dist \"$dist\""));
        assertTrue(build.contains("check-javac-warning-fingerprints.ps1"));
        var baselines = JsonParser.parseString(Files.readString(repoRoot.resolve(
                "gradle/diagnostic-warning-baselines.json"))).getAsJsonObject().getAsJsonArray("baselines");
        org.junit.jupiter.api.Assertions.assertEquals(16, baselines.size());
        for (var baseline : baselines) {
            org.junit.jupiter.api.Assertions.assertEquals(0,
                    baseline.getAsJsonObject().getAsJsonArray("fingerprints").size(),
                    "Resolved source warnings must not remain allowed in CI");
        }
        assertTrue(Files.isRegularFile(repoRoot.resolve("scripts/check-javac-warning-fingerprints.ps1")));
        assertTrue(Files.isRegularFile(repoRoot.resolve("gradle/diagnostic-warning-baselines.json")));
    }

    private static Path findFromWorkspace(Path relative) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            Path candidate = base.resolve(relative).normalize();
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("CI workflow not found from test workspace");
    }
}
