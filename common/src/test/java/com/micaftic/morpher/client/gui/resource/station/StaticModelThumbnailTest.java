package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class StaticModelThumbnailTest {
    private static RawYsmModel model(boolean transparentFront) {
        var model = new RawYsmModel();
        var texture = new RawYsmModel.RawTexture();
        texture.width = 2; texture.height = 1; texture.imageFormat = -1;
        texture.data = new byte[]{(byte) 255, 0, 0, transparentFront ? 0 : (byte) 255, 0, 0, (byte) 255, (byte) 255};
        model.mainEntity.textures.put("default", texture);
        model.mainEntity.mainModel = new RawYsmModel.RawGeometry();
        var bone = new RawYsmModel.RawBone(); bone.name = "root";
        var cube = new RawYsmModel.RawCube();
        cube.faces.add(face(-.1f, .1f)); cube.faces.add(face(.1f, .75f));
        bone.cubes.add(cube); model.mainEntity.mainModel.bones.add(bone);
        return model;
    }

    private static RawYsmModel.RawFace face(float z, float u) {
        var face = new RawYsmModel.RawFace();
        face.positions = new float[][]{{-1, -1, z}, {1, -1, z}, {1, 1, z}, {-1, 1, z}};
        face.u = new float[]{u, u, u, u};
        return face;
    }

    @Test void rendersTexturedGeometryWithDepthInsteadOfShowingTheSkinAtlas() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(StaticModelThumbnail.render(model(false))));
        assertEquals(256, image.getWidth()); assertEquals(384, image.getHeight());
        assertEquals(0xFFFF0000, image.getRGB(128, 192));
        assertEquals(0, image.getRGB(0, 0));
    }

    @Test void transparentFrontFaceDoesNotOccludeTheModelBehindIt() throws Exception {
        var image = ImageIO.read(new ByteArrayInputStream(StaticModelThumbnail.render(model(true))));
        assertEquals(0xFF0000FF, image.getRGB(128, 192));
    }

    @Test void detectsCyclicBonesWithoutRecursingForever() {
        var model = model(false);
        model.mainEntity.mainModel.bones.getFirst().parentName = "root";
        assertThrows(IllegalArgumentException.class, () -> StaticModelThumbnail.render(model));
    }

    @Test void rejectsNonFiniteGeometry() {
        var model = model(false);
        model.mainEntity.mainModel.bones.getFirst().cubes.getFirst().faces.getFirst().positions[0][0] = Float.NaN;
        assertNull(StaticModelThumbnail.render(model));
    }

    @Test void rejectsActualImageDimensionsEvenWhenModelMetadataClaimsASmallTexture() throws Exception {
        BufferedImage oversized = new BufferedImage(2300, 2300, BufferedImage.TYPE_INT_ARGB);
        var graphics = oversized.createGraphics();
        try { graphics.setColor(java.awt.Color.RED); graphics.fillRect(0, 0, 2300, 2300); }
        finally { graphics.dispose(); }
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(oversized, "png", encoded);
        oversized.flush();
        var model = model(false);
        var texture = model.mainEntity.textures.get("default");
        texture.width = texture.height = 1;
        texture.imageFormat = 2;
        texture.data = encoded.toByteArray();
        assertNull(StaticModelThumbnail.render(model));
    }
}
