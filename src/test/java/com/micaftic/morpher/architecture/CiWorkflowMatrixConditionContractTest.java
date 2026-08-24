package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CiWorkflowMatrixConditionContractTest {
    @Test
    void matrixRunsEveryBranchOnPush() throws IOException {
        String source = readRepoFile(Path.of(".github", "workflows", "ci.yml"));
        String condition = "github.event_name != 'push' || matrix.branch == github.ref_name";

        assertFalse(source.lines().anyMatch(line -> line.equals("    if: ${{ " + condition + " }}")));
        assertFalse(source.lines().anyMatch(line -> line.equals("        if: ${{ " + condition + " }}")));
        assertTrue(source.lines().anyMatch(line -> line.equals("          ref: ${{ matrix.branch }}")));
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
