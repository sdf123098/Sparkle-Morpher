package com.micaftic.morpher.architecture;

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

        assertTrue(ci.contains("diagnostic_branch:"), "CI must use the baseline's diagnostic branch identity");
        assertTrue(ci.contains("compile_task:"), "CI must target the effective JavaCompile task");
        assertTrue(ci.contains("java: '21.0.12'"), "1.21 branches must pin the baseline JDK patch");
        assertTrue(ci.contains("java: '25.0.4'"), "26.x branches must pin the baseline JDK patch");
        assertTrue(ci.contains("Compile and compare native javac baseline"));
        assertTrue(ci.contains("Compile and compare CurseForge javac baseline"));
        assertTrue(ci.contains("--rerun-tasks --no-build-cache"), "diagnostic sampling must force javac to run");
        assertTrue(ci.contains("-Dist native"));
        assertTrue(ci.contains("-Dist curseforge"));
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
