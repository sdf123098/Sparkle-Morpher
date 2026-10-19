package com.micaftic.morpher.client.gui;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelCardArtworkTest {
    @Test void builtinFrameUsesWholeCanvasAndReservesItsAuthoredNameArea() throws Exception {
        try (var input = getClass().getResourceAsStream("/assets/sparkle_morpher/builtin/default/textures/gui/foreground.png")) {
            assertNotNull(input);
            var art = ModelCardArtwork.read(input.readAllBytes());
            assertEquals(new ModelCardArtwork(90, 70, 59), art);
            assertTrue(art.reservesNameArea());
            for (int height : new int[]{90, 118, 140}) {
                int cover = art.coverHeight(height, height / 3);
                assertEquals(1 + Math.round((height - 2) * 70 / 90f), cover);
                assertTrue(art.figureHeight(height, cover) < cover);
                assertTrue(height - cover >= 18);
            }
        }
    }
    @Test void invalidArtworkKeepsTheDefaultLayout() {
        var art = ModelCardArtwork.read(new byte[]{1, 2, 3});
        assertEquals(ModelCardArtwork.NONE, art);
        assertEquals(70, art.coverHeight(100, 30));
        assertEquals(70, art.figureHeight(100, 70));
        assertFalse(art.reservesNameArea());
    }
    @Test void artworkWithoutTransparentNameSpaceStaysInsideTheCover() {
        var art = new ModelCardArtwork(90, 90, 75);
        assertFalse(art.reservesNameArea());
        int cover = art.coverHeight(140, 40);
        assertEquals(100, cover);
        assertEquals(83, art.figureHeight(cover, cover));
    }
    @Test void onlyValidAuthoredAnimationOverridesTheStaticPreview() {
        assertEquals("card", ModelCardArtwork.previewAnimation("card", Set.of("idle", "card")));
        assertEquals("", ModelCardArtwork.previewAnimation("missing", Set.of("idle")));
        assertEquals("", ModelCardArtwork.previewAnimation(null, Set.of("idle")));
        assertEquals("", ModelCardArtwork.previewAnimation(" ", Set.of("idle")));
    }

    @Test void paintedCaptionPanelAndModelWindowShareOneCanvas() throws Exception {
        var art=ModelCardArtwork.read(framedCard(true));
        assertTrue(art.reservesNameArea(), "A painted footer reserves name space just like a transparent footer");
        for(int height:new int[]{90,118,140}) {
            assertEquals(1+Math.round((height-2)*70/90f),art.coverHeight(height,height/3));
            assertEquals(1+Math.round((height-2)*68/90f),art.figureHeight(height,art.coverHeight(height,height/3)));
        }
    }

    @Test void openFrameWithJustABottomStrokeDoesNotCreateAnExtraCaptionPanel() throws Exception {
        var art=ModelCardArtwork.read(framedCard(false));
        assertFalse(art.reservesNameArea());
        assertEquals(100,art.coverHeight(140,40));
    }

    @Test void fullCardArtworkUsesTheSameCanvasRegardlessOfItsFooterDetection() {
        var coverOnly = new ModelCardArtwork(90, 90, 75);
        var transparentFooter = new ModelCardArtwork(90, 70, 59);
        assertEquals(140, coverOnly.canvasHeightForCard(140, 120));
        assertEquals(140, transparentFooter.canvasHeightForCard(140, 120));
        assertEquals(120, ModelCardArtwork.NONE.canvasHeightForCard(140, 120));
    }

    @Test void paleAuthoredCaptionUsesDarkTextWhileDarkAndTransparentFootersStayLight() throws Exception {
        assertEquals(0xFF161616, ModelCardArtwork.read(decoratedCaption(0xFFF5EEE7)).nameTextColor());
        assertEquals(0xFFF0F0F0, ModelCardArtwork.read(decoratedCaption(0xFF161979)).nameTextColor());
        assertEquals(0xFFF0F0F0, ModelCardArtwork.read(decoratedCaption(0x00FFFFFF)).nameTextColor());
        assertEquals(0xFFF0F0F0, ModelCardArtwork.NONE.nameTextColor());
    }

    @Test void narrowCaptionWithTransparentCanvasMarginsKeepsItsAuthoredNameRegion() throws Exception {
        var art = ModelCardArtwork.read(decoratedCaption(0xFFF5EEE7));
        assertTrue(art.hasFramedNameArea());
        assertTrue(art.framedNameTop() >= 68 && art.framedNameTop() <= 72);
        assertEquals(1 + Math.round(138 * art.framedNameTop() / 90f), art.coverHeight(140, 20));
    }

    private static byte[] decoratedCaption(int fill) throws Exception {
        var image = new java.awt.image.BufferedImage(52, 90, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 2; y < 88; y++) for (int x = 3; x < 49; x++) {
            if (x < 5 || x >= 47 || y < 4 || y == 68 || y == 69) image.setRGB(x, y, 0xFFEE3366);
            else if (y >= 70) image.setRGB(x, y, fill);
        }
        // Small printed decorations must not turn the pale caption's text white.
        for (int y = 78; y < 82; y++) for (int x = 8; x < 44; x += 6) image.setRGB(x, y, 0xFFFF0055);
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        return png.toByteArray();
    }

    @Test void largeArtworkIsSubsampledBeforeItsMetadataRasterIsDecoded() throws Exception {
        var image = new java.awt.image.BufferedImage(1024, 1024, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 1024; y++) for (int x = 0; x < 1024; x++) {
            if (x < 32 || x >= 992 || y < 32 || y >= 800) image.setRGB(x, y, 0xFFF5EEE7);
        }
        var png = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", png);
        var art = ModelCardArtwork.read(png.toByteArray());
        assertEquals(512, art.canvasHeight(), "Metadata raster must remain bounded before pixel scanning");
        assertTrue(art.hasFramedNameArea());
        assertEquals(0xFF161616, art.nameTextColor());
    }

    private static byte[] framedCard(boolean paintedFooter) throws Exception {
        // Procedural fixture: 52x90 canvas, transparent model window, two-row divider,
        // and optional solid caption panel. No model-author artwork is redistributed.
        var image=new java.awt.image.BufferedImage(52,90,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<90;y++)for(int x=0;x<52;x++) {
            if(x<2||x>=50||y<2||y>=88||(y>=68&&y<70))image.setRGB(x,y,0xFF80D0FF);
            else if(paintedFooter&&y>=70)image.setRGB(x,y,0xFF161979);
        }
        var png=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",png);return png.toByteArray();
    }
}
