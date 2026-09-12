package com.micaftic.morpher.resource.bundle;

import com.micaftic.morpher.resource.pojo.RawYsmModel;

import java.awt.image.BufferedImage;

/**
 * 1.2.7 §24.1：从 {@code YSMClientMapper} 外提的贴图透明度分析职责（行为等价，纯搬运）。
 *
 * 按面 UV 区间对每张贴图采样 alpha，判定面是 INVISIBLE / OPAQUE / TRANSLUCENT，
 * 并记录哪些贴图含彩色半透明像素（供 translucent 渲染层使用）。
 */
public class TextureAlphaAnalyzer {

    private static final int TILE_SIZE = 8;

    private final BufferedImage[] images;
    private final boolean[] results;
    private final AlphaTileSummary[] alphaSummaries;

    public static final int STATE_INVISIBLE = 0;
    public static final int STATE_OPAQUE = 1;
    public static final int STATE_TRANSLUCENT = 2;

    public TextureAlphaAnalyzer(BufferedImage[] images, int expectedCount) {
        this.images = images;
        this.results = new boolean[Math.max(expectedCount, images.length)];
        this.alphaSummaries = new AlphaTileSummary[images.length];
        for (int i = 0; i < images.length; i++) {
            if (images[i] != null) {
                alphaSummaries[i] = new AlphaTileSummary(images[i]);
            }
        }
    }

    public boolean[] getResults() {
        return results;
    }

    public int scan(RawYsmModel.RawFace face) {
        float minU = face.u[0], maxU = face.u[0];
        float minV = face.v[0], maxV = face.v[0];
        for (int i = 1; i < 4; i++) {
            minU = Math.min(minU, face.u[i]);
            maxU = Math.max(maxU, face.u[i]);
            minV = Math.min(minV, face.v[i]);
            maxV = Math.max(maxV, face.v[i]);
        }

        boolean hasValidImage = false;
        boolean faceHasVisiblePixel = false;
        boolean faceHasTransparentPixel = false;

        for (int i = 0; i < images.length; i++) {
            if (images[i] == null) continue;
            hasValidImage = true;

            BufferedImage img = images[i];
            int imgW = img.getWidth();
            int imgH = img.getHeight();

            int startX = (int) Math.floor(minU * imgW + 0.01f);
            int endX = (int) Math.floor(maxU * imgW - 0.01f);
            if (endX < startX) endX = startX;

            int startY = (int) Math.floor(minV * imgH + 0.01f);
            int endY = (int) Math.floor(maxV * imgH - 0.01f);
            if (endY < startY) endY = startY;

            startX = Math.max(0, Math.min(startX, imgW - 1));
            endX = Math.max(0, Math.min(endX, imgW - 1));
            startY = Math.max(0, Math.min(startY, imgH - 1));
            endY = Math.max(0, Math.min(endY, imgH - 1));

            AlphaState alphaState = alphaSummaries[i] != null
                    ? alphaSummaries[i].scan(startX, startY, endX, endY)
                    : scanPixels(img, startX, startY, endX, endY);
            boolean imageHasVisiblePixel = alphaState.hasVisiblePixel;
            boolean imageHasTransparentPixel = alphaState.hasTransparentPixel;
            boolean imageHasColoredTranslucentPixel = alphaState.hasColoredTranslucentPixel;

            if (imageHasVisiblePixel) {
                faceHasVisiblePixel = true;

                if (imageHasTransparentPixel) {
                    faceHasTransparentPixel = true;
                }

                if (imageHasColoredTranslucentPixel) {
                    results[i] = true;
                }
            }
        }

        if (!hasValidImage) return STATE_OPAQUE;
        if (!faceHasVisiblePixel) return STATE_INVISIBLE;
        if (faceHasTransparentPixel) return STATE_TRANSLUCENT;
        return STATE_OPAQUE;
    }

    private static AlphaState scanPixels(BufferedImage img, int startX, int startY, int endX, int endY) {
        boolean hasVisiblePixel = false;
        boolean hasTransparentPixel = false;
        boolean hasColoredTranslucentPixel = false;

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                int alpha = (img.getRGB(x, y) >>> 24) & 0xFF;

                if (alpha > 0) {
                    hasVisiblePixel = true;

                    if (alpha < 255) {
                        hasColoredTranslucentPixel = true;
                    }
                }

                if (alpha < 255) {
                    hasTransparentPixel = true;
                }

                if (hasVisiblePixel && hasTransparentPixel && hasColoredTranslucentPixel) {
                    return new AlphaState(true, true, true);
                }
            }
        }

        return new AlphaState(hasVisiblePixel, hasTransparentPixel, hasColoredTranslucentPixel);
    }

    private record AlphaState(boolean hasVisiblePixel, boolean hasTransparentPixel, boolean hasColoredTranslucentPixel) {
    }

    private static final class AlphaTileSummary {
        private final BufferedImage image;
        private final int tileColumns;
        private final int tileRows;
        private final byte[] flags;

        AlphaTileSummary(BufferedImage image) {
            this.image = image;
            this.tileColumns = Math.max(1, (image.getWidth() + TILE_SIZE - 1) / TILE_SIZE);
            this.tileRows = Math.max(1, (image.getHeight() + TILE_SIZE - 1) / TILE_SIZE);
            this.flags = new byte[tileColumns * tileRows];
            build();
        }

        AlphaState scan(int startX, int startY, int endX, int endY) {
            int fullStartTileX = (startX + TILE_SIZE - 1) / TILE_SIZE;
            int fullEndTileX = (endX + 1) / TILE_SIZE - 1;
            int fullStartTileY = (startY + TILE_SIZE - 1) / TILE_SIZE;
            int fullEndTileY = (endY + 1) / TILE_SIZE - 1;
            int fullTileColumns = fullEndTileX - fullStartTileX + 1;
            int fullTileRows = fullEndTileY - fullStartTileY + 1;
            if (fullTileColumns <= 0 || fullTileRows <= 0 || fullTileColumns * fullTileRows <= 2) {
                return scanPixels(image, startX, startY, endX, endY);
            }

            boolean hasVisiblePixel = false;
            boolean hasTransparentPixel = false;
            boolean hasColoredTranslucentPixel = false;
            for (int ty = fullStartTileY; ty <= fullEndTileY; ty++) {
                int row = ty * tileColumns;
                for (int tx = fullStartTileX; tx <= fullEndTileX; tx++) {
                    byte flag = flags[row + tx];
                    hasVisiblePixel |= (flag & 1) != 0;
                    hasTransparentPixel |= (flag & 2) != 0;
                    hasColoredTranslucentPixel |= (flag & 4) != 0;
                    if (hasVisiblePixel && hasTransparentPixel && hasColoredTranslucentPixel) {
                        return new AlphaState(true, true, true);
                    }
                }
            }

            int fullStartX = fullStartTileX * TILE_SIZE;
            int fullEndX = Math.min(image.getWidth() - 1, (fullEndTileX + 1) * TILE_SIZE - 1);
            int fullStartY = fullStartTileY * TILE_SIZE;
            int fullEndY = Math.min(image.getHeight() - 1, (fullEndTileY + 1) * TILE_SIZE - 1);

            if (startY < fullStartY) {
                AlphaState edge = scanPixels(image, startX, startY, endX, fullStartY - 1);
                hasVisiblePixel |= edge.hasVisiblePixel;
                hasTransparentPixel |= edge.hasTransparentPixel;
                hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
            }
            if (endY > fullEndY) {
                AlphaState edge = scanPixels(image, startX, fullEndY + 1, endX, endY);
                hasVisiblePixel |= edge.hasVisiblePixel;
                hasTransparentPixel |= edge.hasTransparentPixel;
                hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
            }
            if (startX < fullStartX) {
                AlphaState edge = scanPixels(image, startX, fullStartY, fullStartX - 1, fullEndY);
                hasVisiblePixel |= edge.hasVisiblePixel;
                hasTransparentPixel |= edge.hasTransparentPixel;
                hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
            }
            if (endX > fullEndX) {
                AlphaState edge = scanPixels(image, fullEndX + 1, fullStartY, endX, fullEndY);
                hasVisiblePixel |= edge.hasVisiblePixel;
                hasTransparentPixel |= edge.hasTransparentPixel;
                hasColoredTranslucentPixel |= edge.hasColoredTranslucentPixel;
            }
            return new AlphaState(hasVisiblePixel, hasTransparentPixel, hasColoredTranslucentPixel);
        }

        private void build() {
            int width = image.getWidth();
            int height = image.getHeight();
            for (int ty = 0; ty < tileRows; ty++) {
                int startY = ty * TILE_SIZE;
                int endY = Math.min(height, startY + TILE_SIZE);
                for (int tx = 0; tx < tileColumns; tx++) {
                    int startX = tx * TILE_SIZE;
                    int endX = Math.min(width, startX + TILE_SIZE);
                    byte flag = 0;
                    for (int y = startY; y < endY; y++) {
                        for (int x = startX; x < endX; x++) {
                            int alpha = (image.getRGB(x, y) >>> 24) & 0xFF;
                            if (alpha > 0) {
                                flag |= 1;
                                if (alpha < 255) {
                                    flag |= 4;
                                }
                            }
                            if (alpha < 255) {
                                flag |= 2;
                            }
                            if ((flag & 7) == 7) {
                                break;
                            }
                        }
                        if ((flag & 7) == 7) {
                            break;
                        }
                    }
                    flags[ty * tileColumns + tx] = flag;
                }
            }
        }
    }
}
