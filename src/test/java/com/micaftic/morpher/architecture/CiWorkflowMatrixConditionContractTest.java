package com.micaftic.morpher.architecture;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiWorkflowMatrixConditionContractTest {
    @Test
    void pushBuildsExactCommitAndScheduledMatrixCoversAllBranchesAndTests() throws IOException {
        String source = readRepoFile(Path.of(".github", "workflows", "ci.yml"));
        assertTrue(source.contains("schedule:"));
        assertTrue(source.contains("workflow_dispatch:"));
        assertTrue(source.contains("if event in ('push', 'pull_request'):"));
        assertTrue(source.contains("rows[0]['ref'] = os.environ['COMMIT']"));
        assertTrue(source.contains("row['ref'] = row['branch']"));
        assertTrue(source.contains("ref: ${{ matrix.ref }}"));
        var rows = JsonParser.parseString(readRepoFile(Path.of(".github", "ci-matrix.json"))).getAsJsonArray();
        var expected = java.util.Set.of("main", "fa26.1.2", "fa26.2", "fa26.3",
                "neo1.21.1", "neo26.1.2", "neo26.2", "neo26.3");
        var actual = new java.util.HashSet<String>();
        for (var value : rows) {
            var row = value.getAsJsonObject();
            String branch = row.get("branch").getAsString();
            assertTrue(actual.add(branch), "Duplicate matrix branch: " + branch);
            org.junit.jupiter.api.Assertions.assertEquals("build", row.get("gradle_task").getAsString(),
                    "Run the root build so shared tests are included");
            org.junit.jupiter.api.Assertions.assertEquals(branch.equals("main") || branch.equals("neo1.21.1")
                    ? "21.0.12" : "25.0.4", row.get("java").getAsString());
        }
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
        assertFalse(source.contains("continue-on-error: true"));
    }

    private static String readRepoFile(Path relativePath) throws IOException {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            directory = directory.getParent();
        }
        throw new IOException("Repository file not found: " + relativePath);
    }
}
