package com.micaftic.morpher.architecture;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiWorkflowMatrixConditionContractTest {
    @Test
    void pushBuildsExactCommitAndScheduledMatrixCoversAllBranchesAndTests() throws IOException {
        String source = RepoSourceLookup.readFirstExisting(Path.of(".github", "workflows", "ci.yml"));
        assertTrue(source.contains("schedule:"));
        assertTrue(source.contains("workflow_dispatch:"));
        assertTrue(source.contains("if event in ('push', 'pull_request'):"));
        assertTrue(source.contains("row['ref'] = os.environ['COMMIT']"));
        assertTrue(source.contains("row['ref'] = row['branch']"));
        assertTrue(source.contains("ref: ${{ matrix.ref }}"));

        var rows = JsonParser.parseString(
                RepoSourceLookup.readFirstExisting(Path.of(".github", "ci-matrix.json"))).getAsJsonArray();
        // Symmetric layout: one MC version per repository, so each branch maps to both
        // loader rows (fabric + neoforge). A branch is "covered" when both build roots exist.
        Set<String> branches = new HashSet<>();
        Set<String> buildRoots = new HashSet<>();
        var rowsPerBranch = new java.util.HashMap<String, Set<String>>();
        for (var value : rows) {
            var row = value.getAsJsonObject();
            String branch = row.get("branch").getAsString();
            String buildRoot = row.get("build_root").getAsString();
            branches.add(branch);
            buildRoots.add(buildRoot);
            rowsPerBranch.computeIfAbsent(branch, key -> new HashSet<>()).add(buildRoot);
            assertEquals("build", row.get("gradle_task").getAsString(),
                    "Run the root build so shared tests are included");
        }
        assertEquals(Set.of("fabric", "neoforge"), buildRoots,
                "Every matrix row must name its loader build root");
        // One MC version per repository: each registered branch must build BOTH loaders,
        // and only once per loader.
        for (var entry : rowsPerBranch.entrySet()) {
            assertEquals(Set.of("fabric", "neoforge"), entry.getValue(),
                    "Branch " + entry.getKey() + " must build both loaders exactly once");
        }
        // Each repository tracks exactly one MC version. It lives on `main` in the
        // 26.2 repository and on a version-named branch (e.g. `26.3`) elsewhere, so
        // the assertion is about the count, not about a hard-coded branch name.
        assertEquals(1, branches.size(),
                "Each repository holds a single MC version, so exactly one branch is registered, found " + branches);
        String registeredBranch = branches.iterator().next();
        assertFalse(registeredBranch.isBlank(), "The registered branch name must not be blank");
        assertTrue(registeredBranch.equals("main") || registeredBranch.matches("\\d+(\\.\\d+)*"),
                "The registered branch must be `main` or a MC version name, found " + registeredBranch);

        for (var value : rows) {
            var row = value.getAsJsonObject();
            String branch = row.get("branch").getAsString();
            String expectedJava = "1.21.1".equals(branch) ? "21.0.12" : "25.0.4";
            assertEquals(expectedJava, row.get("java").getAsString(),
                    "Java version must follow the MC version of the branch");
            String compileTask = row.get("compile_task").getAsString();
            assertTrue(compileTask.startsWith(":fabric:") || compileTask.startsWith(":neoforge:"),
                    "compile_task must target one loader subproject, found " + compileTask);
            String buildRoot = row.get("build_root").getAsString();
            assertTrue(compileTask.startsWith(":" + buildRoot + ":"),
                    "compile_task must match build_root for " + branch);
        }
        assertFalse(source.contains("continue-on-error: true"));
    }

    @Test
    void everyBranchRowReportsDiagnosticsIntoItsBuildRoot() throws IOException {
        String source = RepoSourceLookup.readFirstExisting(Path.of(".github", "workflows", "ci.yml"));
        // Both loaders are modules of ONE Gradle build, so every matrix row writes its
        // diagnostics to the same repo-root directory; the row is identified by the
        // artifact name instead of a per-loader build root path.
        assertTrue(source.contains("build/ci-diagnostics/"),
                "diagnostics must be uploaded from the repository build directory");
        assertTrue(source.contains("name: ${{ matrix.diagnostic_branch }}-diagnostics"),
                "diagnostics artifacts must stay per-loader named");
        assertTrue(source.contains("BUILD_ROOT: ${{ matrix.build_root }}"),
                "ci-build.sh needs BUILD_ROOT to select the loader subproject");
    }
}
