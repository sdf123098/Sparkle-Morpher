package com.micaftic.morpher.architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LocalMaidSelectionContractTest {
    @Test
    void localMaidSelectionIsDispatchedBeforeCloudLoginAndPrivacyChecks() throws Exception {
        String source = source();
        int method = source.indexOf("public static void applySelection(");
        int local = source.indexOf("LocalMaidModelSync.select(", method);
        int login = source.indexOf("runtime == null", method);
        int privacy = source.indexOf("NetworkAccess.allowsCloud()", method);
        assertTrue(local > method && local < login && local < privacy,
                "Local maid models must be applied without a Cloud session or network access");
    }

    @Test
    void savedLocalMaidChoicesAreRestoredWhileCloudIsLoggedOut() throws Exception {
        String source = source();
        int method = source.indexOf("public static void tick()");
        int local = source.indexOf("LocalMaidModelSync.tick(", method);
        int login = source.indexOf("runtime == null", method);
        assertTrue(local > method && local < login,
                "Restoring a local maid model must run before the no-Cloud early return");
    }

    @Test
    void builtinDefaultPlayerModelActivatesTheMaidModelInsteadOfResettingIt() throws Exception {
        String relative = "fabric/src/main/java/com/micaftic/morpher/core/compat/touhoulittlemaid/MaidCapability.java";
        String source = read(relative);
        int start = source.indexOf("public void applyLocalState(");
        int end = source.indexOf("\n    }", start);
        String apply = source.substring(start, end);
        assertTrue(apply.contains("initModelWithTexture(modelId, textureId)"));
        assertFalse(apply.contains("resetModel()"),
                "The default card is a built-in player model, not a request for the native maid renderer");
    }

    private static String source() throws Exception {
        return read("common/src/main/java/com/micaftic/morpher/cloud/client/CloudEntityModelSync.java");
    }

    private static String read(String relative) throws Exception {
        for (Path base = Path.of("").toAbsolutePath(); base != null; base = base.getParent()) {
            for (String path : new String[]{relative, relative.replace("fabric/src/main/java/", "common/src/main/java/")}) {
                Path candidate = base.resolve(path);
                if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            }
        }
        throw new IllegalStateException("CloudEntityModelSync source not found");
    }
}
