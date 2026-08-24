package com.micaftic.morpher.core.gpu;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Map;

public final class Pie {
    public static final float tau = (float) (Math.PI * 2.0);

    // Scanline fallback geometry is static per (center, radii, angles): only the
    // color changes per frame (hover states). Cache the computed runs so the
    // per-frame cost drops from O(area) math + thousands of fills to a plain
    // replay of cached pixel runs.
    private static final int MAX_FALLBACK_GEOMETRY_CACHE = 64;
    private static final Map<FallbackGeomKey, int[]> FALLBACK_GEOMETRY_CACHE = new HashMap<>();

    private record FallbackGeomKey(int cxBits, int cyBits, int innerBits, int outerBits, int startBits, int endBits) {
        static FallbackGeomKey of(float cx, float cy, float inner, float outer, float start, float end) {
            return new FallbackGeomKey(Float.floatToIntBits(cx), Float.floatToIntBits(cy),
                    Float.floatToIntBits(inner), Float.floatToIntBits(outer),
                    Float.floatToIntBits(start), Float.floatToIntBits(end));
        }
    }

    public static void draw(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        draw(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba, 1.0f);
    }

    public static void draw(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba, float feather) {
        // 26.x Screen rendering extracts GUI commands before drawing them. Raw
        // OpenGL submitted here is outside that command stream and is cleared
        // before the final GUI pass, so record the wheel with extractor fills.
        drawFallback(graphics, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle, rgba);
    }

    private static void drawFallback(GuiGraphicsExtractor graphics, float centerX, float centerY, float innerRadius, float outerRadius, float startAngle, float endAngle, int rgba) {
        float inner = Math.max(0.0f, innerRadius);
        FallbackGeomKey key = FallbackGeomKey.of(centerX, centerY, inner, outerRadius, startAngle, endAngle);
        int[] runs = FALLBACK_GEOMETRY_CACHE.get(key);
        if (runs == null) {
            runs = computeFallbackRuns(centerX, centerY, inner, outerRadius, startAngle, endAngle);
            if (FALLBACK_GEOMETRY_CACHE.size() >= MAX_FALLBACK_GEOMETRY_CACHE) {
                FALLBACK_GEOMETRY_CACHE.clear();
            }
            FALLBACK_GEOMETRY_CACHE.put(key, runs);
        }
        for (int i = 0; i < runs.length; i += 3) {
            int y = runs[i];
            graphics.fill(runs[i + 1], y, runs[i + 2] + 1, y + 1, rgba);
        }
    }

    /**
     * Scanline analytic geometry: on a fixed row, coverage of a ring segment can
     * only change at intersections with the outer circle, the inner circle and
     * the two angle-boundary rays. Collect those x candidates (&lt;= 6), sort and
     * dedupe, then a mid-point test per adjacent pair yields at most 3 fill runs
     * per row -- O(rows) instead of O(area) with atan2 per pixel. Backend-agnostic.
     * Runs are emitted as flattened [y, x0, x1] triples and cached per geometry.
     */
    private static int[] computeFallbackRuns(float centerX, float centerY, float inner, float outerRadius, float startAngle, float endAngle) {
        float outerSq = outerRadius * outerRadius;
        float innerSq = inner * inner;
        float span = endAngle - startAngle;
        boolean fullCircle = Math.abs(span) >= tau - 0.001f;
        float normStart = fullCircle ? 0.0f : normalize(startAngle);
        float normEnd = fullCircle ? 0.0f : normalize(endAngle);
        boolean ccw = span >= 0.0f;
        int minX = (int) Math.floor(centerX - outerRadius);
        int maxX = (int) Math.ceil(centerX + outerRadius);
        int minY = (int) Math.floor(centerY - outerRadius);
        int maxY = (int) Math.ceil(centerY + outerRadius);

        int[] runs = new int[96];
        int size = 0;
        float[] xs = new float[6];
        for (int y = minY; y < maxY; y++) {
            float py = y + 0.5f;
            float dy = py - centerY;
            float dySq = dy * dy;
            if (dySq > outerSq) {
                continue;
            }
            int count = 0;
            float halfOuter = (float) Math.sqrt(Math.max(0.0f, outerSq - dySq));
            xs[count++] = centerX - halfOuter;
            xs[count++] = centerX + halfOuter;
            if (innerSq > 0.0f && dySq < innerSq) {
                float halfInner = (float) Math.sqrt(Math.max(0.0f, innerSq - dySq));
                xs[count++] = centerX - halfInner;
                xs[count++] = centerX + halfInner;
            }
            if (!fullCircle) {
                xs[count++] = centerX + dy * cot(normStart);
                xs[count++] = centerX + dy * cot(normEnd);
            }
            java.util.Arrays.sort(xs, 0, count);
            int unique = 0;
            for (int i = 0; i < count; i++) {
                if (unique == 0 || xs[i] - xs[unique - 1] > 0.001f) {
                    xs[unique++] = xs[i];
                }
            }
            for (int i = 0; i + 1 < unique; i++) {
                float xm = (xs[i] + xs[i + 1]) * 0.5f;
                if (!contains(xm, py, centerX, centerY, innerSq, outerSq, normStart, normEnd, ccw, fullCircle)) {
                    continue;
                }
                int x0 = (int) Math.ceil(xs[i] - 0.5f);
                int x1 = (int) Math.floor(xs[i + 1] - 0.5f);
                if (x0 < minX) {
                    x0 = minX;
                }
                if (x1 >= maxX) {
                    x1 = maxX - 1;
                }
                if (x1 >= x0) {
                    if (size + 3 > runs.length) {
                        runs = java.util.Arrays.copyOf(runs, runs.length * 2);
                    }
                    runs[size++] = y;
                    runs[size++] = x0;
                    runs[size++] = x1;
                }
            }
        }
        return size == runs.length ? runs : java.util.Arrays.copyOf(runs, size);
    }



    private static float cot(float angle) {
        float sin = (float) Math.sin(angle);
        if (Math.abs(sin) < 1.0E-4f) {
            return sin >= 0.0f ? 1.0E7f : -1.0E7f;
        }
        return (float) Math.cos(angle) / sin;
    }

    private static boolean contains(float x, float y, float centerX, float centerY, float innerSq, float outerSq, float normStart, float normEnd, boolean ccw, boolean fullCircle) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distSq = dx * dx + dy * dy;
        if (distSq > outerSq || distSq < innerSq) {
            return false;
        }
        if (fullCircle) {
            return true;
        }
        float angle = normalize((float) Math.atan2(dy, dx));
        if (ccw) {
            return normStart <= normEnd ? angle >= normStart && angle <= normEnd : angle >= normStart || angle <= normEnd;
        }
        return normEnd <= normStart ? angle <= normStart && angle >= normEnd : angle <= normStart || angle >= normEnd;
    }

    private static float normalize(float angle) {
        angle %= tau;
        return angle < 0.0f ? angle + tau : angle;
    }
}
