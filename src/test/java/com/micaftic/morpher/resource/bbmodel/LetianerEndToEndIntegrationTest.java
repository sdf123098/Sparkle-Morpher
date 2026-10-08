package com.micaftic.morpher.resource.bbmodel;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Optional real Figura ZIP integration test; configure -Dspm.letianer.fixture=<authorized fixture path>. */
class LetianerEndToEndIntegrationTest {
    @Test
    void convertsConfiguredFiguraFixtureEndToEnd() throws IOException {
        String configured = System.getProperty("spm.letianer.fixture");
        assumeTrue(configured != null && !configured.isBlank(),
                "Set -Dspm.letianer.fixture to an authorized Figura ZIP fixture to run this integration test");

        Path zip = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(zip), "Configured Figura ZIP fixture does not exist: " + zip);
        byte[] data = Files.readAllBytes(zip);
        ZipModelSniffer sniff = ZipModelSniffer.sniff(data, 0);
        assertEquals(ZipModelSniffer.Kind.FIGURA_AVATAR, sniff.kind);
        assertNotNull(sniff.bbmodelBytes);
        assertNotNull(sniff.avatarJsonBytes);

        String avatarName = ZipModelSniffer.parseAvatarName(sniff.avatarJsonBytes);
        assertFalse(avatarName.isBlank(), "Figura avatar name is missing");
        BBModelFile bbmodel = BBModelParser.parse(new String(sniff.bbmodelBytes, StandardCharsets.UTF_8));
        assertNotNull(bbmodel.meta);
        assertNotNull(bbmodel.resolution);
        assertFalse(bbmodel.elements.isEmpty(), "Figura BBModel has no elements");

        RawYsmModel raw = BBToRawConverter.convert(bbmodel, sniff.sideTextures);
        assertNotNull(raw.mainEntity);
        assertNotNull(raw.mainEntity.mainModel);
        assertTrue(raw.mainEntity.mainModel.bones.size() >= 6,
                "Expected at least six bones from the Figura avatar outliner");

        int totalFaces = 0;
        for (RawYsmModel.RawBone bone : raw.mainEntity.mainModel.bones) {
            for (RawYsmModel.RawCube cube : bone.cubes) totalFaces += cube.faces.size();
        }
        assertTrue(totalFaces >= 6000, "Expected at least 6000 converted faces, got " + totalFaces);
        assertFalse(raw.mainEntity.textures.isEmpty(), "No Figura side textures were converted");
        raw.mainEntity.textures.values().forEach(texture -> {
            assertNotNull(texture.data, "Converted texture data is missing: " + texture.name);
            assertTrue(texture.data.length > 0, "Converted texture data is empty: " + texture.name);
            assertTrue(texture.width > 0 && texture.height > 0,
                    "Converted texture dimensions are invalid: " + texture.name);
        });
        assertFalse(raw.mainEntity.animationFiles.isEmpty(), "No Figura animations were converted");
    }
}
