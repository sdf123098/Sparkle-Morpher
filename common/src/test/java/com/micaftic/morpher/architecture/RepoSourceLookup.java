package com.micaftic.morpher.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves repository files by logical location instead of by physical path.
 *
 * <p>The project keeps one MC version per repository with a symmetric loader layout:
 * {@code common/} holds the shared layer, {@code fabric/} and {@code neoforge/} hold the
 * loader-specific trees. A source file may therefore live in common/ on one branch and in
 * a loader tree on another (the "divergent class" set), so contract tests must not
 * hard-code one physical path. Callers pass ordered candidates and this helper tries, for
 * each ancestor of the working directory, every candidate plus the equivalent path under
 * each known source root.
 */
final class RepoSourceLookup {

    /** Source roots in preference order, relative to the repository root. */
    private static final List<String> SOURCE_ROOTS = List.of(
            "common/src/main/java",
            "fabric/src/main/java",
            "neoforge/src/main/java",
            "src/main/java");

    /** Resource roots in preference order, relative to the repository root. */
    private static final List<String> RESOURCE_ROOTS = List.of(
            "common/src/main/resources",
            "fabric/src/main/resources",
            "neoforge/src/main/resources",
            "src/main/resources");

    private RepoSourceLookup() {
    }

    /**
     * Reads a Java source file identified by its package path, e.g.
     * {@code com/micaftic/morpher/config/ExtraPlayerRenderConfig.java}.
     */
    static String readSource(String packagePath) throws IOException {
        return readFirstExisting(resolve(packagePath));
    }

    /** Reads a resource identified by its path under a resources root, e.g. {@code assets/.../en_us.json}. */
    static String readResource(String resourcePath) throws IOException {
        return readFirstExisting(resolveResource(resourcePath));
    }

    /** Returns the first existing candidate for a Java source, or throws. */
    static Path resolve(String packagePath) throws IOException {
        return firstExisting(expand(SOURCE_ROOTS, packagePath));
    }

    /** Returns the first existing candidate for a resource, or throws. */
    static Path resolveResource(String resourcePath) throws IOException {
        return firstExisting(expand(RESOURCE_ROOTS, resourcePath));
    }

    /** True when the given Java source exists under any known source root. */
    static boolean sourceExists(String packagePath) {
        for (Path candidate : expand(SOURCE_ROOTS, packagePath)) {
            if (Files.isRegularFile(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** True when the given resource exists under any known resource root. */
    static boolean resourceExists(String resourcePath) {
        for (Path candidate : expand(RESOURCE_ROOTS, resourcePath)) {
            if (Files.isRegularFile(candidate)) {
                return true;
            }
        }
        return false;
    }

    static String readFirstExisting(Path... candidates) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path resolved = base.resolve(candidate).normalize();
                if (Files.isRegularFile(resolved)) {
                    return Files.readString(resolved, StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("Required source file not found among: " + describe(candidates));
    }

    static Path firstExisting(Path... candidates) throws IOException {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (Path candidate : candidates) {
                Path resolved = base.resolve(candidate).normalize();
                if (Files.isRegularFile(resolved)) {
                    return resolved;
                }
            }
        }
        throw new IOException("Required source file not found among: " + describe(candidates));
    }

    private static Path[] expand(List<String> roots, String relativePath) {
        List<Path> candidates = new ArrayList<>();
        for (String root : roots) {
            candidates.add(Path.of(root, relativePath));
        }
        return candidates.toArray(Path[]::new);
    }

    private static String describe(Path[] candidates) {
        StringBuilder text = new StringBuilder();
        for (Path candidate : candidates) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(candidate);
        }
        return text.toString();
    }
}
