package com.micaftic.morpher.client.renderer.modernhud;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.gpu.Blaze3DBoneMatrices;
import com.micaftic.morpher.core.gpu.Blaze3DModelMesh;
import com.micaftic.morpher.core.gpu.Blaze3DModelMeshBuilder;
import com.micaftic.morpher.core.gpu.Blaze3DBoneSkinPipeline;
import com.micaftic.morpher.core.render.Blaze3D26_2Capability;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 现代 HUD 渲染实例（26.2 移植，阶段 2）。
 *
 * <p>按 modelId 缓存：26.2 Blaze3D 持久网格（{@link Blaze3DModelMeshBuilder}）、
 * 独立 HUD 骨骼缓冲 ring（避免与世界 draw 共用 {@code Blaze3DModelMesh.boneMatrixSlice}）、
 * 独立小型离屏 {@link TextureTarget}（RGBA8 + depth，vanilla 26.2 GpuTexture 化）。
 *
 * <p>提交：CommandEncoder 直提（backend-neutral，OpenGL/Vulkan 通用），投影经
 * {@code RenderSystem.setProjectionMatrix(GpuBufferSlice, ORTHOGRAPHIC)} 写入自己的
 * HUD 正交矩阵（26.2 无 1.21.1 的 setOrtho(Matrix4f) 重载）；顶点/骨骼经
 * {@code Blaze3DBoneSkinPipeline} + DynamicTransforms。只接受 {@link PlayerPoseSnapshot}，
 * 零二次动画评估。
 *
 * <p>26.2 首版限制：Blaze3DBoneSkinPipeline 无 blend（ColorTargetState.DEFAULT），
 * translucent 纹理第二遍暂不绘制（fragment 丢弃 alpha<0.99，与 Blaze3DRenderPath 一致）。
 */
public final class ModernHudRenderInstance {

    private static final int FBO_PADDING = 8;
    private static final int BONE_RING = 2;
    /** 经典 HUD 同款满亮 packed light（0xF000F0）。 */
    private static final int PACKED_LIGHT = 0xF000F0;
    private static final float FRONT_FACING_YAW = 180.0f;
    /**
     * FBO 观察空间 z。26.2 沿用 1.21.1 的 GUI 相机约定：本 FBO 投影
     * setOrtho(0, w, h, 0, 1000, 21000) 可见 z_eye ∈ [-21000, -1000]，相机取
     * vanilla GUI 位置 10000 - farPlane = -11000（1.21.1 实测验证）。
     */
    private static final float GUI_CAMERA_Z = -11000.0f;

    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    private static final Vector3f overlayScratch = new Vector3f();
    private static final Vector4f WHITE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private static final Vector4f TRANSPARENT_CLEAR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    private static final Map<String, ModernHudRenderInstance> INSTANCES = new HashMap<>();
    private static final AtomicBoolean warnedRender = new AtomicBoolean(false);

    private final String modelId;
    private final GeoModel geoModel;
    /** 静息姿态模型空间 AABB（块单位）。静息时骨骼矩阵为单位阵（pivot 平移自抵消），顶点即模型空间坐标。 */
    private final float boundMinX, boundMinY, boundMaxX, boundMaxY;

    private Blaze3DModelMesh mesh;
    private final GpuBuffer[] boneBuffers = new GpuBuffer[BONE_RING];
    private final GpuBufferSlice[] boneSlices = new GpuBufferSlice[BONE_RING];
    private int boneCursor;
    private ByteBuffer boneBuf;
    private GpuBuffer projBuffer;
    private GpuBufferSlice projSlice;
    private GpuBuffer lightBuffer;
    private GpuBufferSlice lightSlice;
    private TextureTarget fbo;
    private int fboLogicalWidth;
    private int fboLogicalHeight;
    private int fboWidth;
    private int fboHeight;
    private boolean released;

    private ModernHudRenderInstance(String modelId, GeoModel geoModel) {
        this.modelId = modelId;
        this.geoModel = geoModel;
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        for (GeoModel.BakedBone bone : geoModel.bakedBones) {
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    for (int v = 0; v < 4; v++) {
                        float vx = quad.x(v);
                        float vy = quad.y(v);
                        minX = Math.min(minX, vx);
                        maxX = Math.max(maxX, vx);
                        minY = Math.min(minY, vy);
                        maxY = Math.max(maxY, vy);
                    }
                }
            }
        }
        if (Float.isInfinite(minX) || maxX - minX < 1e-4f || maxY - minY < 1e-4f) {
            // 无几何兜底：vanilla 玩家 2 块高、0.5 块宽的旧 framing
            minX = -0.5f;
            maxX = 0.5f;
            minY = 0.0f;
            maxY = 2.0f;
        }
        this.boundMinX = minX;
        this.boundMinY = minY;
        this.boundMaxX = maxX;
        this.boundMaxY = maxY;
    }

    /** 按 modelId 取（或建）渲染实例；模型切换/资源重载时调用 {@link #release(String)}。 */
    public static ModernHudRenderInstance getOrCreate(String modelId, GeoModel geoModel) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(modelId, id -> new ModernHudRenderInstance(id, geoModel));
        }
    }

    public static void release(String modelId) {
        synchronized (INSTANCES) {
            ModernHudRenderInstance instance = INSTANCES.remove(modelId);
            if (instance != null) {
                instance.release();
            }
        }
    }

    /** 模型切换 / 断线 / 资源重载时统一释放全部实例。 */
    public static void releaseAll() {
        synchronized (INSTANCES) {
            for (ModernHudRenderInstance instance : INSTANCES.values()) {
                instance.release();
            }
            INSTANCES.clear();
        }
    }

    /**
     * 把玩家模型主体渲染进独立 HUD FBO。成功后调用方应把 {@link #fboColorView()} 合成到 GUI。
     * 不触发任何动画评估（骨骼来自 snapshot）。
     */
    public boolean tryRender(PlayerPoseSnapshot snapshot, float x, float y, float scale, float yawOffset,
                             float partialTick) {
        if (released) {
            diag("instance released");
            return false;
        }
        // 26.2 门控：不能用 GpuCapability.isAvailable()（那是 raw GL 专属判定，Vulkan 后端下
        // 恒为 false）。现代 HUD 走 backend-neutral 的 Blaze3D CommandEncoder 管线，用
        // Blaze3D26_2Capability 的能力探测（GpuDevice/RenderPass API 存在性，GL/Vulkan 通用）。
        Blaze3D26_2Capability.Report report = Blaze3D26_2Capability.report();
        if (!report.stableGraphicsApiPresent() || !report.createBufferPresent()
                || !report.createRenderPassPresent() || !report.drawIndexedPresent()) {
            diag("26.2 graphics api probe failed");
            return false;
        }
        if (geoModel.bakedBones == null || geoModel.bakedBones.isEmpty()) {
            diag("no baked bones");
            return false;
        }
        if (!ensureMesh()) {
            diag("mesh build failed");
            return false;
        }
        if (!ensureBuffers()) {
            diag("buffer ensure failed");
            return false;
        }
        if (!ensureFbo(scale)) {
            diag("fbo ensure failed");
            return false;
        }

        try {
            Minecraft mc = Minecraft.getInstance();
            GpuDevice device = RenderSystem.getDevice();
            if (mc == null || device == null) {
                diag("device unavailable");
                return false;
            }

            // framing 用模型真实静息 AABB：maxX/maxY 边对齐逻辑坐标 (x, y)（Rz180 镜像后
            // logical = translate - model*scale）；vanilla 2 块高模型下与旧常量等价。
            // yaw 项含 180°（与经典 HUD renderPlayerOverlay 的 180.0f+yawOffset 一致）：
            // 让模型正面朝向观看者。1.21.1 上该 180 由 beginOldHudRenderState(180) 烘焙进
            // 骨骼参数；26.2 fallback 评估不烘焙 → 缺失时看到背部（2026-08-15 实测）。
            PoseStack poseStack = new PoseStack();
            poseStack.translate(x + boundMaxX * scale, y + boundMaxY * scale, GUI_CAMERA_Z);
            // GUI front-view coordinates need an X handedness correction. Keep the
            // negative Z used by the GUI camera, but do not leave the model left-right mirrored.
            poseStack.scale(-scale, scale, -scale);
            poseStack.mulPose(Axis.ZP.rotationDegrees(FRONT_FACING_YAW + 0.1f));
            poseStack.mulPose(Axis.YP.rotationDegrees(FRONT_FACING_YAW + yawOffset));
            PoseStack.Pose pose = poseStack.last();

            boneBuf.clear();
            if (!Blaze3DBoneMatrices.write(geoModel, pose.pose(), pose.normal(),
                    snapshot.matrixData(), snapshot.absPivotData(), PACKED_LIGHT, boneBuf)) {
                diag("bone matrix write failed");
                return false;
            }
            boneBuf.position(0);
            boneBuf.limit(geoModel.bakedBones.size() * 144);

            int boneSlice = nextBoneCursor();
            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeToBuffer(boneSlices[boneSlice], boneBuf);
            // 26.2 深度约定（2026-08-15 实测定位）：DepthStencilState.DEFAULT 的深度比较是
            // GREATER_THAN_OR_EQUAL（反转-Z）。1.21.1 是前向-Z（清 1.0 正确）；26.2 清 1.0
            // 等于把整个模型放在"近平面之后" → 深度测试全失败 → FBO 只有透明清屏 → 模型透明。
            // 修复：清深度取设备约定（zZeroToOne → 0.0；GL NDC[-1,1] → -1.0），正交矩阵带
            // zZeroToOne 标志（Vulkan 近→1 远→0；GL 近→-1 远→+1）。
            boolean zZeroToOne = device.getDeviceInfo().isZZeroToOne();
            double clearDepth = zZeroToOne ? 0.0 : -1.0;
            encoder.writeToBuffer(projSlice, orthoBytes(fboLogicalWidth, fboLogicalHeight, zZeroToOne));

            // HUD 固定光照：用 1.21.1 现代 HUD 的显式双灯值（z 分量正负对称，正反面都能照到）。
            // 之前用 ENTITY_IN_UI：BBModel 正面（pose 后 +Z 法线）能照到，但 YSM 正面
            // （pose 后 -Z 法线）照不到 → 背亮前暗（2026-08-15 实测 + 法线诊断确认法线本身无误）。
            encoder.writeToBuffer(lightSlice, lightBytes());
            RenderSystem.setShaderLights(lightSlice);

            RenderSystem.backupProjectionMatrix();
            try {
                RenderSystem.setProjectionMatrix(projSlice, ProjectionType.ORTHOGRAPHIC);

                AbstractTexture modelTexture = mc.getTextureManager().getTexture(snapshot.texture());
                if (modelTexture == null || modelTexture.getTextureView() == null || modelTexture.getSampler() == null) {
                    diag("model texture view unavailable");
                    return false;
                }
                GpuTextureView overlayTextureView = mc.gameRenderer.overlayTexture().getTextureView();
                GpuTextureView lightmapTextureView = mc.gameRenderer.lightmap();
                GpuSampler clampSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
                if (overlayTextureView == null || lightmapTextureView == null || clampSampler == null) {
                    diag("overlay/lightmap/sampler unavailable");
                    return false;
                }

                // 受击红闪与经典路径一致：非受伤 NO_OVERLAY，受伤 pack(u=1, v=true)
                int packedOverlay = snapshot.hurtOverlay()
                        ? OverlayTexture.pack(OverlayTexture.u(1.0f), OverlayTexture.v(true))
                        : OverlayTexture.NO_OVERLAY;
                overlayScratch.set((float) packedOverlay, 0.0f, 0.0f);
                var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                        IDENTITY_MATRIX, WHITE, overlayScratch, IDENTITY_MATRIX);

                try (RenderPass pass = encoder.createRenderPass(
                        () -> "sparkle_morpher_modern_hud",
                        fbo.getColorTextureView(),
                        Optional.of(TRANSPARENT_CLEAR),
                        fbo.getDepthTextureView(),
                        OptionalDouble.of(clearDepth),
                        new RenderPass.RenderArea(0, 0, fboWidth, fboHeight)
                )) {
                    // 26.2（fabric-loom）RenderPass 无 setViewport：viewport 由 RenderArea 决定
                    pass.setPipeline(Blaze3DBoneSkinPipeline.PIPELINE);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dynamicTransforms);
                    pass.setUniform("BoneMatrices", boneSlices[boneSlice]);
                    pass.bindTexture("Sampler0", modelTexture.getTextureView(), modelTexture.getSampler());
                    pass.bindTexture("Sampler1", overlayTextureView, clampSampler);
                    pass.bindTexture("Sampler2", lightmapTextureView, clampSampler);
                    pass.setVertexBuffer(0, mesh.vertexSlice());
                    pass.setIndexBuffer(mesh.indexBuffer, IndexType.INT);
                    int drawCount = mesh.indexDrawCount(0);
                    if (drawCount > 0) {
                        pass.drawIndexed(drawCount, 1, mesh.indexOffsetBytes(0) / Integer.BYTES, 0, 0);
                    }
                }
                return true;
            } finally {
                RenderSystem.restoreProjectionMatrix();
            }
        } catch (Throwable t) {
            if (warnedRender.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.warn("[MODERN-HUD] instance.tryRender failed modelId={}", modelId, t);
            }
            return false;
        }
    }

    /** GUI 合成用：离屏 FBO 颜色纹理视图。 */
    public GpuTextureView fboColorView() {
        return fbo == null ? null : fbo.getColorTextureView();
    }

    public int fboLogicalWidth() {
        return fboLogicalWidth;
    }

    public int fboLogicalHeight() {
        return fboLogicalHeight;
    }

    /** Screen-space model origin used by the modern HUD attachment pass. */
    public float modelOriginX(float hudX, float scale) {
        return hudX + boundMaxX * scale;
    }

    /** Screen-space model origin used by the modern HUD attachment pass. */
    public float modelOriginY(float hudY, float scale) {
        return hudY + boundMaxY * scale;
    }

    private static void diag(String reason) {
        if (warnedRender.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.warn("[MODERN-HUD] instance.tryRender failed: {}", reason);
        }
    }

    private boolean ensureMesh() {
        if (mesh != null) {
            return true;
        }
        mesh = Blaze3DModelMeshBuilder.build(geoModel);
        return mesh != null;
    }

    private boolean ensureBuffers() {
        int boneBytes = geoModel.bakedBones.size() * 144;
        for (int i = 0; i < BONE_RING; i++) {
            if (boneBuffers[i] == null) {
                GpuDevice device = RenderSystem.getDevice();
                if (device == null) {
                    return false;
                }
                boneBuffers[i] = device.createBuffer(
                        () -> "sparkle_morpher_modern_hud_bones",
                        GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST,
                        boneBytes);
                boneSlices[i] = new GpuBufferSlice(boneBuffers[i], 0, boneBytes);
            }
        }
        if (boneBuf == null) {
            boneBuf = ByteBuffer.allocateDirect(boneBytes).order(ByteOrder.nativeOrder());
        }
        if (projBuffer == null) {
            GpuDevice device = RenderSystem.getDevice();
            if (device == null) {
                return false;
            }
            projBuffer = device.createBuffer(
                    () -> "sparkle_morpher_modern_hud_proj",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    64);
            projSlice = new GpuBufferSlice(projBuffer, 0, 64);
        }
        if (lightBuffer == null) {
            GpuDevice device = RenderSystem.getDevice();
            if (device == null) {
                return false;
            }
            // std140 Lighting UBO：两个 vec3（各 16B 步长）= 32B
            lightBuffer = device.createBuffer(
                    () -> "sparkle_morpher_modern_hud_light",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    32);
            lightSlice = new GpuBufferSlice(lightBuffer, 0, 32);
        }
        return true;
    }

    /** 1.21.1 现代 HUD 同款固定双灯（std140：L0 vec3 @0，L1 vec3 @16）。z 分量正负对称。 */
    private static ByteBuffer lightBytes() {
        ByteBuffer out = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder());
        out.putFloat(0.2f).putFloat(1.0f).putFloat(-0.7f).putFloat(0.0f);
        out.putFloat(-0.2f).putFloat(1.0f).putFloat(0.7f).putFloat(0.0f);
        out.flip();
        return out;
    }

    private boolean ensureFbo(float scale) {
        // 按模型真实静息尺寸 framing（带头发/饰品 >2 块的高模型不会被 FBO 顶边截断）；
        // vanilla 2 块高模型下退化为旧的 scale × 2*scale。
        int logicalWidth = Math.max(1, Math.round((boundMaxX - boundMinX) * scale)) + FBO_PADDING * 2;
        int logicalHeight = Math.max(1, Math.round((boundMaxY - boundMinY) * scale)) + FBO_PADDING * 2;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        var mainTarget = mc.gameRenderer.mainRenderTarget();
        if (screenW <= 0 || screenH <= 0 || mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }
        float pixelScaleX = mainTarget.width / (float) screenW;
        float pixelScaleY = mainTarget.height / (float) screenH;
        int fboWidth = Math.max(1, (int) Math.ceil(logicalWidth * pixelScaleX));
        int fboHeight = Math.max(1, (int) Math.ceil(logicalHeight * pixelScaleY));

        if (fbo == null || this.fboWidth != fboWidth || this.fboHeight != fboHeight) {
            if (fbo != null) {
                fbo.destroyBuffers();
            }
            fbo = new TextureTarget("sparkle_morpher_modern_hud", fboWidth, fboHeight, true, GpuFormat.RGBA8_UNORM);
            this.fboWidth = fboWidth;
            this.fboHeight = fboHeight;
            this.fboLogicalWidth = logicalWidth;
            this.fboLogicalHeight = logicalHeight;
        }
        return true;
    }

    private int nextBoneCursor() {
        int id = boneCursor;
        boneCursor = (boneCursor + 1) % BONE_RING;
        return id;
    }

    private static final float[] orthoScratch = new float[16];

    private static ByteBuffer orthoBytes(int logicalWidth, int logicalHeight, boolean zZeroToOne) {
        // 观察空间 z 约定同 1.21.1：near=1000/far=21000 → 可见 z_eye ∈ [-21000, -1000]，
        // 相机取 -11000（GUI_CAMERA_Z 注释）。矩阵按列主序写入 GpuBufferSlice。
        // zZeroToOne 随设备（Vulkan=true）：JOML setOrtho 布尔重载使近平面→最大深度（反转-Z）。
        new Matrix4f().setOrtho(0.0f, logicalWidth, logicalHeight, 0.0f, 1000.0f, 21000.0f, zZeroToOne).get(orthoScratch);
        ByteBuffer out = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
        for (float f : orthoScratch) {
            out.putFloat(f);
        }
        out.flip();
        return out;
    }

    private void release() {
        released = true;
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        for (int i = 0; i < boneBuffers.length; i++) {
            if (boneBuffers[i] != null) {
                boneBuffers[i].close();
                boneBuffers[i] = null;
                boneSlices[i] = null;
            }
        }
        if (projBuffer != null) {
            projBuffer.close();
            projBuffer = null;
            projSlice = null;
        }
        if (lightBuffer != null) {
            lightBuffer.close();
            lightBuffer = null;
            lightSlice = null;
        }
        if (fbo != null) {
            fbo.destroyBuffers();
            fbo = null;
        }
    }
}
