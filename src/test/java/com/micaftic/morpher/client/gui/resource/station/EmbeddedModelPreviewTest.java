package com.micaftic.morpher.client.gui.resource.station;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddedModelPreviewTest {
    private static RawYsmModel.RawImage image(String name, int width, int height, int color) throws Exception {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        var raw = new RawYsmModel.RawImage();
        raw.name = name; raw.width = width; raw.height = height; raw.format = 2; raw.data = output.toByteArray();
        return raw;
    }

    @Test void extractsAuthoredBackgroundAndBoundsRetainedThumbnail() throws Exception {
        var raw = new RawYsmModel();
        raw.properties.guiBackground = "custom-background";
        raw.properties.backgroundImages.add(image("custom-background", 800, 1200, 0xFFFF0000));
        var preview = ImageIO.read(new ByteArrayInputStream(EmbeddedModelPreview.extract(raw)));
        assertEquals(512, preview.getHeight());
        assertTrue(preview.getWidth() <= 512);
    }

    @Test void explicitModelPreviewWinsOverDecorativeBackground() throws Exception {
        var raw = new RawYsmModel();
        raw.properties.backgroundImages.add(image("gui_background", 4, 4, 0xFFFF0000));
        raw.properties.backgroundImages.add(image("preview.png", 4, 4, 0xFF00FF00));
        var preview = ImageIO.read(new ByteArrayInputStream(EmbeddedModelPreview.extract(raw)));
        assertEquals(0xFF00FF00, preview.getRGB(0, 0));
    }

    @Test void bodyTextureAndAuthorAvatarAreNotMistakenForAModelPreview() {
        var raw = new RawYsmModel();
        raw.mainEntity.textures.put("body", new RawYsmModel.RawTexture());
        raw.metadata.extraAvatars.add(new RawYsmModel.RawImage());
        assertNull(EmbeddedModelPreview.extract(raw));
    }

    @Test void rejectsOversizedRawRastersBeforeAllocating() {
        var raw = new RawYsmModel();
        var image = new RawYsmModel.RawImage();
        image.name = "gui_background"; image.width = 100_000; image.height = 100_000;
        image.format = -1; image.data = new byte[4]; raw.properties.backgroundImages.add(image);
        assertNull(EmbeddedModelPreview.extract(raw));
    }

    @Test void rejectsOversizedSourceBeforeParsing() {
        assertThrows(IllegalArgumentException.class,
                () -> EmbeddedModelPreview.extract(new byte[EmbeddedModelPreview.MAX_MODEL_BYTES + 1], "huge.ysm"));
    }

    @Test void rejectsSmallArchivesWhoseCumulativeExpansionExceedsThePreviewBudget() throws Exception {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        byte[] zeros = new byte[1024 * 1024];
        try (ZipOutputStream zip = new ZipOutputStream(archive)) {
            for (int entry = 0; entry < 2; entry++) {
                zip.putNextEntry(new ZipEntry("unused-" + entry + ".bin"));
                for (int block = 0; block < 17; block++) zip.write(zeros);
                zip.closeEntry();
            }
        }
        assertTrue(archive.size() < EmbeddedModelPreview.MAX_MODEL_BYTES);
        Exception failure = assertThrows(Exception.class,
                () -> EmbeddedModelPreview.extract(archive.toByteArray(), "expanded.zip"));
        assertTrue(failure.getMessage().contains("Preview archive"));
    }

    @Test void normalizesExternalPngToABoundedPng() throws Exception {
        byte[] source = image("cover", 800, 1200, 0xFFFF0000).data;
        var result = ImageIO.read(new ByteArrayInputStream(EmbeddedModelPreview.normalizeImage(source)));
        assertEquals(512, result.getHeight());
        assertTrue(result.getWidth() <= 512);
    }

    @Test void preservesExternalJpegSupportAndRejectsInvalidBytes() throws Exception {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(EmbeddedModelPreview.normalizeImage(output.toByteArray()))));
        assertNull(EmbeddedModelPreview.normalizeImage(new byte[]{1, 2, 3}));
        assertNull(EmbeddedModelPreview.normalizeImage(null));
    }
}
