package com.micaftic.morpher.client.gui;

import java.io.ByteArrayInputStream;
import java.util.Set;
import javax.imageio.ImageIO;

/** Coordinates in the author's complete card canvas, including transparent name space. */
public record ModelCardArtwork(int canvasHeight, int artworkBottom, int figureBottom, int framedNameTop, int nameTextColor) {
    public static final ModelCardArtwork NONE = new ModelCardArtwork(0, 0, 0);
    private static final int LIGHT_TEXT = 0xFFF0F0F0;
    private static final int DARK_TEXT = 0xFF161616;
    private static final int MAX_ANALYSIS_EDGE = 512;

    public ModelCardArtwork(int canvasHeight, int artworkBottom, int figureBottom, int framedNameTop) {
        this(canvasHeight, artworkBottom, figureBottom, framedNameTop, LIGHT_TEXT);
    }

    public ModelCardArtwork(int canvasHeight, int artworkBottom, int figureBottom) {
        this(canvasHeight, artworkBottom, figureBottom, 0);
    }

    public static ModelCardArtwork read(byte[] png) {
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(png))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return NONE;
            var reader = readers.next();
            try {
                reader.setInput(input);
                int w = reader.getWidth(0), h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || (long) w * h > 16_777_216) return NONE;
                // Metadata is analyzed on demand by the UI. Bound the decoded raster before reading,
                // rather than decoding multi-megapixel author PNGs and resizing them afterwards.
                var parameters = reader.getDefaultReadParam();
                int sampling = Math.max(1, Math.max((w + MAX_ANALYSIS_EDGE - 1) / MAX_ANALYSIS_EDGE,
                        (h + MAX_ANALYSIS_EDGE - 1) / MAX_ANALYSIS_EDGE));
                parameters.setSourceSubsampling(sampling, sampling, 0, 0);
                var image = reader.read(0, parameters);
                w = image.getWidth();
                h = image.getHeight();
                int bottom = 0;
                int[] opaqueRows = new int[h];
                int[] innerOpaqueRows = new int[h];
                int innerLeft = w / 8, innerRight = w - innerLeft;
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    if (alpha > 16) bottom = y + 1;
                    if (alpha > 192) opaqueRows[y]++;
                    if (alpha > 192 && x >= innerLeft && x < innerRight) innerOpaqueRows[y]++;
                }
                if (bottom == 0) return NONE;
                int figure = bottom;
                // A frame's solid lower bar follows its transparent model window.
                boolean opening = false;
                for (int y = h / 4; y < bottom; y++) {
                    int opaque = opaqueRows[y];
                    if (opaque < w / 2) opening = true;
                    else if (opening && opaque >= Math.ceil(w * .8)) { figure = y; break; }
                }
                // Some authors paint a solid caption box under the open model window.
                // Its alpha reaches the canvas bottom, so transparent-padding detection alone misses it.
                int nameTop = 0;
                if (bottom == h && figure >= h / 2 && h - figure >= Math.max(8, h / 8)) {
                    boolean solidFooter = true;
                    for (int y = figure; y < h; y++) if (opaqueRows[y] < Math.ceil(w * .8)) {
                        solidFooter = false; break;
                    }
                    int afterDivider = figure + Math.max(2, h / 45);
                    if (solidFooter && h - afterDivider >= Math.max(8, h / 8)) nameTop = afterDivider;
                }
                // A substantial transparent tail is the author's existing name space, rather than
                // a painted caption above it. Only tolerate small decorative canvas margins here.
                if (nameTop == 0 && h - bottom <= Math.max(3, h / 16)) {
                    int footer = footerTop(innerOpaqueRows, innerRight - innerLeft, bottom, h);
                    if (footer > 0) {
                        figure = footer;
                        nameTop = footer + Math.max(2, h / 45);
                    }
                }
                int color = nameTop == 0 ? LIGHT_TEXT : captionTextColor(image, nameTop, bottom);
                return new ModelCardArtwork(h, bottom, figure, nameTop, color);
            } finally { reader.dispose(); }
        } catch (Exception invalid) { return NONE; }
    }

    private static int footerTop(int[] opaqueRows, int width, int bottom, int height) {
        int dense = Math.max(1, (int) Math.ceil(width * .7));
        int end = bottom - 1;
        while (end >= height / 2 && opaqueRows[end] < dense && bottom - end <= Math.max(2, height / 16)) end--;
        if (end < height / 2 || opaqueRows[end] < dense) return 0;
        int top = end, gap = 0;
        for (int y = end; y >= height / 2; y--) {
            if (opaqueRows[y] >= dense) { top = y; gap = 0; }
            else if (++gap > Math.max(1, height / 90)) break;
        }
        if (end - top + 1 < Math.max(8, height / 8)) return 0;
        int openRows = 0;
        for (int y = height / 4; y < top; y++) if (opaqueRows[y] < width / 2) openRows++;
        return openRows >= Math.max(2, height / 8) ? top : 0;
    }

    /** Median backdrop luminance ignores small logos/printed ornaments in the caption field. */
    private static int captionTextColor(java.awt.image.BufferedImage image, int top, int bottom) {
        int[] histogram = new int[256];
        int samples = 0, width = image.getWidth();
        int insetY = Math.max(1, (bottom - top) / 6);
        for (int y = top + insetY; y < bottom - insetY; y++) {
            for (int x = width / 8; x < width - width / 8; x++) {
                int rgba = image.getRGB(x, y);
                double alpha = (rgba >>> 24) / 255.0;
                double red = ((rgba >> 16 & 255) * alpha + 50 * (1 - alpha)) / 255.0;
                double green = ((rgba >> 8 & 255) * alpha + 50 * (1 - alpha)) / 255.0;
                double blue = ((rgba & 255) * alpha + 50 * (1 - alpha)) / 255.0;
                double luminance = .2126 * linear(red) + .7152 * linear(green) + .0722 * linear(blue);
                histogram[Math.min(255, (int) Math.round(luminance * 255))]++;
                samples++;
            }
        }
        if (samples == 0) return LIGHT_TEXT;
        int cumulative = 0;
        for (int bin = 0; bin < histogram.length; bin++) {
            cumulative += histogram[bin];
            if (cumulative * 2 >= samples) return bin / 255.0 >= .18 ? DARK_TEXT : LIGHT_TEXT;
        }
        return LIGHT_TEXT;
    }

    private static double linear(double srgb) {
        return srgb <= .04045 ? srgb / 12.92 : Math.pow((srgb + .055) / 1.055, 2.4);
    }

    /** All authored layers share this transform, independent of animation or footer detection. */
    public int canvasHeightForCard(int cardHeight, int coverHeight) {
        return canvasHeight > 0 ? cardHeight : coverHeight;
    }

    public boolean reservesNameArea() {
        return hasFramedNameArea() || canvasHeight > 0 && artworkBottom < canvasHeight && artworkBottom >= canvasHeight / 2;
    }

    public boolean hasFramedNameArea() {
        return framedNameTop > 0 && framedNameTop < canvasHeight;
    }

    public int coverHeight(int cardHeight, int fallbackNameHeight) {
        if (!reservesNameArea())
            return cardHeight - fallbackNameHeight;
        // Use the same integer canvas transform as the full-card texture blit.
        return Math.max(1, Math.min(cardHeight - 18,
                1 + Math.round((cardHeight - 2) * (hasFramedNameArea() ? framedNameTop : artworkBottom) / (float) canvasHeight)));
    }

    public int figureHeight(int cardHeight, int coverHeight) {
        return canvasHeight == 0 ? coverHeight : Math.max(20,
                Math.min(coverHeight, 1 + Math.round((cardHeight - 2) * figureBottom / (float) canvasHeight)));
    }

    public static String previewAnimation(String configured, Set<String> available) {
        return configured != null && !configured.isBlank() && available.contains(configured) ? configured : "";
    }
}
