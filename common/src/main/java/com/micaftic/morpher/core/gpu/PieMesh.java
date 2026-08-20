package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * R1.2.2 轮盘阶段 1：ring segment 的 CPU 预三角化网格 + 静态 GPU 缓冲。
 *
 * <p>几何只由 (cx, cy, inner, outer, start, end) 决定 —— 布局不变（轮盘项数量/半径/
 * 角度/UI Scale 不变）则复用缓存，每帧零重建、零上传。hover/selected/alpha 等
 * 动态量不进本网格，由调用方（{@link PiePortableRenderPath}）经 uniform 更新。
 *
 * <p>顶点仅 Position(RG32_FLOAT)，颜色走 per-slice uniform —— 与
 * {@link Blaze3DModelMeshBuilder} 的静态缓冲模式一致，OpenGL/Vulkan 通用。
 */
public final class PieMesh {
    /** 每扇区弧分段数：48 段 × 2 三角形 = 96 三角形/扇区，肉眼无锯齿。 */
    public static final int SEGMENTS = 48;
    private static final int MAX_CACHE_ENTRIES = 64;
    private static final int VERTEX_FLOATS_PER_SEGMENT = 4 * 2;
    private static final int INDICES_PER_SEGMENT = 6;

    private static final Map<PieMeshKey, PieMesh> CACHE = new HashMap<>();

    private final GpuBuffer vertexBuffer;
    private final GpuBuffer indexBuffer;
    private final GpuBufferSlice vertexSlice;
    private final int vertexCount;
    private final int indexCount;

    private PieMesh(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, int vertexCount, int indexCount) {
        this.vertexBuffer = vertexBuffer;
        this.indexBuffer = indexBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.vertexSlice = vertexBuffer.slice();
    }

    /**
     * 取（或构建）匹配几何的网格。必须在渲染线程调用（内部上传 GPU 缓冲）。
     */
    public static PieMesh getOrCreate(GpuDevice device, float cx, float cy, float inner, float outer,
                                      float start, float end) {
        float clampedInner = Math.max(0.0f, inner);
        PieMeshKey key = PieMeshKey.of(cx, cy, clampedInner, outer, start, end);
        synchronized (CACHE) {
            PieMesh mesh = CACHE.get(key);
            if (mesh != null) {
                return mesh;
            }
            mesh = build(device, cx, cy, clampedInner, outer, start, end);
            if (mesh != null) {
                if (CACHE.size() >= MAX_CACHE_ENTRIES) {
                    CACHE.clear();
                }
                CACHE.put(key, mesh);
            }
            return mesh;
        }
    }

    public GpuBufferSlice vertexSlice() {
        return vertexSlice;
    }

    public GpuBuffer indexBuffer() {
        return indexBuffer;
    }

    public int indexCount() {
        return indexCount;
    }

    public int vertexCount() {
        return vertexCount;
    }

    /**
     * 环形扇区三角化：沿角度均匀细分 SEGMENTS 段，每段为外弧两点 + 内弧两点组成的
     * 梯形（两个三角形）。inner == 0 时内弧顶点重合于圆心，三角形自然退化为扇形。
     */
    private static PieMesh build(GpuDevice device, float cx, float cy, float inner, float outer,
                                 float start, float end) {
        int segments = SEGMENTS;
        int vertexCount = segments * 4;
        int indexCount = segments * INDICES_PER_SEGMENT;
        long vertexBytes = (long) vertexCount * 2L * Float.BYTES;
        long indexBytes = (long) indexCount * Integer.BYTES;

        ByteBuffer vertices = MemoryUtil.memAlloc(Math.toIntExact(vertexBytes)).order(ByteOrder.nativeOrder());
        ByteBuffer indices = MemoryUtil.memAlloc(Math.toIntExact(indexBytes)).order(ByteOrder.nativeOrder());
        ResourceLifecycleStats.onDirectBufferAllocated(null, vertexBytes);
        ResourceLifecycleStats.onDirectBufferAllocated(null, indexBytes);

        try {
            float span = end - start;
            float delta = span / segments;
            for (int i = 0; i < segments; i++) {
                float a0 = start + i * delta;
                float a1 = a0 + delta;
                float cos0 = (float) Math.cos(a0);
                float sin0 = (float) Math.sin(a0);
                float cos1 = (float) Math.cos(a1);
                float sin1 = (float) Math.sin(a1);
                int base = i * 4;
                // v0 = outer(a0), v1 = outer(a1), v2 = inner(a0), v3 = inner(a1)
                vertices.putFloat(cx + outer * cos0).putFloat(cy + outer * sin0);
                vertices.putFloat(cx + outer * cos1).putFloat(cy + outer * sin1);
                vertices.putFloat(cx + inner * cos0).putFloat(cy + inner * sin0);
                vertices.putFloat(cx + inner * cos1).putFloat(cy + inner * sin1);

                int ibase = i * INDICES_PER_SEGMENT;
                indices.putInt(base).putInt(base + 1).putInt(base + 2);
                indices.putInt(base + 1).putInt(base + 3).putInt(base + 2);
            }
            vertices.flip();
            indices.flip();

            GpuBuffer vertexBuffer = null;
            GpuBuffer indexBuffer = null;
            try {
                vertexBuffer = device.createBuffer(
                        () -> "sparkle_morpher_pie_vertices",
                        GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                        vertices
                );
                indexBuffer = device.createBuffer(
                        () -> "sparkle_morpher_pie_indices",
                        GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST,
                        indices
                );
            } catch (Throwable t) {
                closeQuietly(vertexBuffer);
                closeQuietly(indexBuffer);
                GpuDebugLog.warn("PieMesh build failed: {}: {}",
                        t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
                return null;
            }
            GpuDebugLog.verbose("PieMesh built vertices={} indices={} inner={} outer={} start={} end={}",
                    vertexCount, indexCount, inner, outer, start, end);
            return new PieMesh(vertexBuffer, indexBuffer, vertexCount, indexCount);
        } finally {
            ResourceLifecycleStats.onDirectBufferFreed(null, vertexBytes);
            ResourceLifecycleStats.onDirectBufferFreed(null, indexBytes);
            MemoryUtil.memFree(vertices);
            MemoryUtil.memFree(indices);
        }
    }

    private static void closeQuietly(GpuBuffer buffer) {
        if (buffer == null) {
            return;
        }
        try {
            buffer.close();
        } catch (Throwable ignored) {
        }
    }

    /** 几何 key：与 {@code Pie.FallbackGeomKey} 同思路，float bit 精确匹配。 */
    private record PieMeshKey(int cxBits, int cyBits, int innerBits, int outerBits, int startBits, int endBits) {
        static PieMeshKey of(float cx, float cy, float inner, float outer, float start, float end) {
            return new PieMeshKey(Float.floatToIntBits(cx), Float.floatToIntBits(cy),
                    Float.floatToIntBits(inner), Float.floatToIntBits(outer),
                    Float.floatToIntBits(start), Float.floatToIntBits(end));
        }
    }
}
