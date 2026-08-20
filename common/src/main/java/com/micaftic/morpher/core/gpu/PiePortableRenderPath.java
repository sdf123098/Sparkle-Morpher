package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.render.SmGraphicsCapabilities;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * R1.2.2 轮盘阶段 1：ring segment 的可移植（backend-neutral）提交路径。
 *
 * <p>替代两个旧路径：
 * <ul>
 *   <li>Vulkan 下 {@code Pie.drawFallback} 的逐行 {@code graphics.fill}（每扇区几十~几百 fill）；</li>
 *   <li>OpenGL 下 {@link PieShader} 的 Raw GL 覆盖矩形（长期目标是收敛到同一可移植路径）。</li>
 * </ul>
 *
 * <p>提交模型与 {@link Blaze3DRenderPath} 一致：CPU 预三角化网格（{@link PieMesh}，布局不变
 * 零重建）→ CommandEncoder → RenderPass（自定义 {@link PiePipeline}）→ 每扇区每层 1 次
 * drawIndexed。OpenGL / Vulkan 由 26.2 Blaze3D 统一执行。
 *
 * <p>uniform 生命周期（2026-08-14 修复）：26.2 {@code writeToBuffer} 异步引用 ByteBuffer，
 * 且每帧多个 draw 共享 uniform —— 颜色改为 64 槽 ring（每槽独立 GpuBuffer slice + 持久
 * ByteBuffer，避免释放后读垃圾与多层颜色互相覆盖）；投影改为每次 draw 写入（跟随轮盘
 * pose/layoutScale 动画，不缓存过期）。
 *
 * <p>experimental（config 开关），未启用或能力探测失败时返回 false，由 {@link Pie} 回退。
 */
public final class PiePortableRenderPath {
    private static final int COLOR_RING_SLOTS = 64;

    private static final AtomicBoolean warnedRuntimeFailure = new AtomicBoolean(false);
    private static final AtomicLong drawCount = new AtomicLong();
    private static final AtomicLong failCount = new AtomicLong();

    private static final Matrix4f mvpScratch = new Matrix4f();
    private static final Matrix4f poseScratch = new Matrix4f();

    private static GpuBuffer projBuffer;
    private static GpuBufferSlice projSlice;
    private static final ByteBuffer projBytes = MemoryUtil.memAlloc(64).order(ByteOrder.nativeOrder());

    private static GpuBuffer[] colorBuffers;
    private static GpuBufferSlice[] colorSlices;
    private static final ByteBuffer[] colorBytes = new ByteBuffer[COLOR_RING_SLOTS];
    private static int colorCursor;

    private PiePortableRenderPath() {
    }

    public static boolean isExperimentalEnabled() {
        return GeneralConfig.safeGet(GeneralConfig.ENABLE_BLAZE3D_ROULETTE_RENDERER, false);
    }

    public static boolean hasStableApi() {
        // §8 能力模型（RULE-GFX-5）：按能力选路径，而非按后端名字。
        SmGraphicsCapabilities caps = SmGraphicsCapabilities.current();
        return caps.supportsPortablePipeline()
                && caps.supportsCustomShader()
                && caps.supportsGpuMesh();
    }

    /** 诊断：本会话可移植路径累计成功提交次数（排除回退）。 */
    public static long drawCount() {
        return drawCount.get();
    }

    public static boolean tryDraw(GuiGraphicsExtractor graphics, float centerX, float centerY,
                                  float innerRadius, float outerRadius, float startAngle, float endAngle,
                                  int rgba, float feather) {
        // §9.5：Raw GL 不可用（如 Vulkan）时，即使实验开关关闭也必须走可移植路径，
        // 避免退回逐扫描线 fill；开关仅在 Raw GL 可用时决定是否优先 portable
        // （RULE-GFX-5：按能力 supportsRawOpenGl 而非后端名字）。
        SmGraphicsCapabilities caps = SmGraphicsCapabilities.current();
        if (!isExperimentalEnabled() && caps.supportsRawOpenGl()) {
            return false;
        }
        if (!caps.supportsPortablePipeline()
                || !caps.supportsCustomShader()
                || !caps.supportsGpuMesh()) {
            return false;
        }

        try {
            RenderSystem.assertOnRenderThread();
            Minecraft mc = Minecraft.getInstance();
            GpuDevice device = RenderSystem.getDevice();
            if (mc == null || device == null) {
                return false;
            }
            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
                return false;
            }

            PieMesh mesh = PieMesh.getOrCreate(device, centerX, centerY, innerRadius, outerRadius, startAngle, endAngle);
            if (mesh == null) {
                return false;
            }
            if (!ensureUniforms(device)) {
                return false;
            }

            // 颜色：与 PieShader 一致的 AARRGGBB → 0..1 floats
            float cr = ((rgba >> 16) & 0xFF) / 255.0f;
            float cg = ((rgba >> 8) & 0xFF) / 255.0f;
            float cb = (rgba & 0xFF) / 255.0f;
            float ca = ((rgba >> 24) & 0xFF) / 255.0f;

            // 每槽独立 slice + 持久 ByteBuffer：writeToBuffer 异步引用，后续 draw 不再覆盖
            int slot = colorCursor++ & (COLOR_RING_SLOTS - 1);
            ByteBuffer colorSlot = colorBytes[slot];
            colorSlot.clear();
            colorSlot.putFloat(cr).putFloat(cg).putFloat(cb).putFloat(ca);
            colorSlot.flip();

            // 投影每次写入：跟随当前 GUI pose（layoutScale 动画/缩放变化不缓存过期）
            projBytes.clear();
            writeProjection(projBytes, graphics, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            projBytes.flip();

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeToBuffer(projSlice, projBytes);
            encoder.writeToBuffer(colorSlices[slot], colorSlot);
            try (RenderPass pass = encoder.createRenderPass(
                    () -> "sparkle_morpher_pie",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(PiePipeline.PIPELINE);
                pass.setUniform("PieProjBlock", projSlice);
                pass.setUniform("PieColorBlock", colorSlices[slot]);
                pass.setVertexBuffer(0, mesh.vertexSlice());
                pass.setIndexBuffer(mesh.indexBuffer(), IndexType.INT);
                pass.drawIndexed(mesh.indexCount(), 1, 0, 0, 0);
            }
            drawCount.incrementAndGet();
            return true;
        } catch (Throwable t) {
            failCount.incrementAndGet();
            if (warnedRuntimeFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Pie portable render path failed: {}: {}; falling back. draws={}",
                        t.getClass().getSimpleName(), String.valueOf(t.getMessage()), drawCount.get());
            }
            return false;
        }
    }

    /** 懒创建 proj/color uniform 资源（渲染线程）。 */
    private static boolean ensureUniforms(GpuDevice device) {
        if (projBuffer == null) {
            projBuffer = device.createBuffer(
                    () -> "sparkle_morpher_pie_proj",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    64L
            );
            projSlice = projBuffer.slice();
        }
        if (colorBuffers == null) {
            colorBuffers = new GpuBuffer[COLOR_RING_SLOTS];
            colorSlices = new GpuBufferSlice[COLOR_RING_SLOTS];
            for (int i = 0; i < COLOR_RING_SLOTS; i++) {
                final int slotIndex = i;
                colorBuffers[i] = device.createBuffer(
                        () -> "sparkle_morpher_pie_color_" + slotIndex,
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        16L
                );
                colorSlices[i] = colorBuffers[i].slice();
                colorBytes[i] = MemoryUtil.memAlloc(16).order(ByteOrder.nativeOrder());
            }
        }
        return true;
    }

    private static void writeProjection(ByteBuffer out, GuiGraphicsExtractor graphics,
                                        float guiWidth, float guiHeight) {
        // 与 Pie.draw 的 Raw GL mvp 相同的 GUI 正交投影（y 向下）+ 活动 pose（layoutScale 等）
        mvpScratch.identity().setOrtho(0.0f, guiWidth, guiHeight, 0.0f, -1000.0f, 1000.0f);
        Matrix3x2fc pose = null;
        try {
            pose = graphics.pose();
        } catch (Throwable ignored) {
            // 提取器无 pose 时退回纯正交（与旧路径 isIdentity2D 分支等价）
        }
        if (pose != null && !isIdentity2D(pose)) {
            poseScratch.identity();
            poseScratch.m00(pose.m00()).m01(pose.m01()).m03(pose.m20());
            poseScratch.m10(pose.m10()).m11(pose.m11()).m13(pose.m21());
            mvpScratch.mul(poseScratch);
        }
        mvpScratch.get(out);
    }

    private static boolean isIdentity2D(Matrix3x2fc pose) {
        return pose.m00() == 1.0f && pose.m01() == 0.0f && pose.m10() == 0.0f
                && pose.m11() == 1.0f && pose.m20() == 0.0f && pose.m21() == 0.0f;
    }
}