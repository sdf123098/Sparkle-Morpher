package com.micaftic.morpher.client.renderer.modernhud;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.gpu.Blaze3DBoneMatrices;
import com.micaftic.morpher.core.gpu.Blaze3DModelMesh;
import com.micaftic.morpher.core.gpu.Blaze3DModelMeshBuilder;
import com.micaftic.morpher.core.gpu.Blaze3DBoneSkinPipeline;
import com.micaftic.morpher.core.render.Blaze3D26_1_2Capability;
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
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 现代 HUD 渲染实例（26.1.2 移植，阶段 2）。
 *
 * <p>按 modelId 缓存：26.1.2 Blaze3D 持久网格（{@link Blaze3DModelMeshBuilder}）、
 * 独立 HUD 骨骼缓冲 ring（26.1.2 无 RGBA32F texel buffer，骨骼矩阵走 std140 UBO，
 * 见 {@link Blaze3DBoneSkinPipeline}）、独立小型离屏 {@link TextureTarget}。
 *
 * <p>提交：CommandEncoder 直提（backend-neutral，OpenGL/Vulkan 通用），投影经
 * {@code RenderSystem.setProjectionMatrix(GpuBufferSlice, ORTHOGRAPHIC)} 写入自己的
 * HUD 正交矩阵；顶点/骨骼经 {@code Blaze3DBoneSkinPipeline} + DynamicTransforms。
 * 只接受 {@link PlayerPoseSnapshot}，零二次动画评估。
 *
 * <p>26.1.2 差异（相对 26.2 版）：RenderPass 无 RenderArea（viewport 即整个 FBO 纹理）、
 * createRenderPass 用 OptionalInt/OptionalDouble 清屏、setVertexBuffer 收 GpuBuffer
 * （26.2 收 GpuBufferSlice）、drawIndexed 4 参 (baseVertex, firstIndex, count, instanceCount)、
 * TextureTarget 4 参（无 GpuFormat）、前向-Z（DepthStencilState.DEFAULT = LESS_THAN_OR_EQUAL，
 * 与 1.21.1 同约定：清深度 1.0 + 标准正交矩阵，无 zZeroToOne）。
 *
 * <p>26.1.2 首版限制：Blaze3DBoneSkinPipeline 无 blend（ColorTargetState.DEFAULT），
 * translucent 纹理第二遍暂不绘制（fragment 丢弃 alpha<0.99，与 26.2 版一致）。
 */
public final class ModernHudRenderInstance {

    private static final int FBO_PADDING = 8;
    private static final int BONE_RING = 2;
    /** 经典 HUD 同款满亮 packed light（0xF000F0）。 */
    private static final int PACKED_LIGHT = 0xF000F0;
    private static final float FRONT_FACING_YAW = 180.0f;
    /**
     * FBO 观察空间 z。26.1.2 沿用 1.21.1 的 GUI 相机约定：本 FBO 投影
     * setOrtho(0, w, h, 0, 1000, 21000) 可见 z_eye ∈ [-21000, -1000]，相机取
     * vanilla GUI 位置 10000 - farPlane = -11000（1.21.1 实测验证）。
     */
    private static final float GUI_CAMERA_Z = -11000.0f;
    /** 前向-Z：清深度取"远"值 1.0（vanilla 26.1.2 清主目标同样用 1.0）。 */
    private static final double CLEAR_DEPTH = 1.0;

    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    private static final Vector3f overlayScratch = new Vector3f();
    private static final Vector4f WHITE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);
    private static final Vector4f TRANSPARENT_CLEAR = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    private static final Map<String, ModernHudRenderInstance> INSTANCES = new HashMap<>();
    private static final AtomicBoolean warnedRender = new AtomicBoolean(false);
    /** 一次性诊断标记（2026-08-15 排查 FBO 链路用，确认后移除）。 */
    private static final AtomicBoolean diagOnce = new AtomicBoolean(false);

    private final String modelId;
    private final GeoModel geoModel;
    /** 静息姿态模型空间 AABB（块单位）。静息时骨骼矩阵为单位阵（pivot 平移自抵消），顶点即模型空间坐标。 */
    private final float boundMinX, boundMinY, boundMaxX, boundMaxY;
    /** 人物锚点（模型原点 (0,0,0)）在 FBO 逻辑空间中的坐标；由 {@link #ensureFbo(float)} 随 scale 更新。 */
    private float fboAnchorX;
    private float fboAnchorY;

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
        // 参与 framing 的可见几何范围。特意剔除 glow 特效骨（YSM 中名字以 ysmGlow 开头的骨，
        // 由 YSMClientMapper 烘成 BakedBone.glow=true）：这些特效骨（如 ysmGlow_arrow11 顶部箭矢、
        // 发光装饰）在静息帧 scale=0 不可见，却会把静态 AABB 撑到远大于真实可见人物
        // （default 模型实测 5.737x7.191，而可见人物仅约 2 格高），导致 FBO——连带现代 HUD 观感——
        // 比布局编辑器（经典预览、按框剪裁）大一圈。
        // 仅从 framing 排除，仍保留它们参与网格渲染（发光瞳孔/动态箭矢等要继续显示）。
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;
        int glowExcluded = 0;
        String minXBone = null, maxXBone = null, minYBone = null, maxYBone = null;
        for (GeoModel.BakedBone bone : geoModel.bakedBones) {
            if (bone.glow) {
                glowExcluded++;
                continue;
            }
            for (GeoModel.BakedCube cube : bone.cubes) {
                for (GeoModel.BakedQuad quad : cube.quads) {
                    for (int v = 0; v < 4; v++) {
                        float vx = quad.x(v);
                        float vy = quad.y(v);
                        if (vx < minX) {
                            minX = vx;
                            minXBone = bone.name;
                        }
                        if (vx > maxX) {
                            maxX = vx;
                            maxXBone = bone.name;
                        }
                        if (vy < minY) {
                            minY = vy;
                            minYBone = bone.name;
                        }
                        if (vy > maxY) {
                            maxY = vy;
                            maxYBone = bone.name;
                        }
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
        // 一次性 AABB 诊断（2026-08-16 定位现代 HUD framing 异常放大用，确认后移除）：
        // 记录四个极值分别由哪个 bone 提供，帮助判断是正常模型尺寸还是特殊/隐藏骨骼撑大。
        YesSteveModel.LOGGER.info(
                "[MODERN-HUD-AABB] modelId={} minX={} [{}], maxX={} [{}], minY={} [{}], maxY={} [{}], size={}x{} (glowBonesExcluded={})",
                modelId,
                minX, minXBone,
                maxX, maxXBone,
                minY, minYBone,
                maxY, maxYBone,
                maxX - minX, maxY - minY, glowExcluded);
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
        // 26.1.2 门控：不能用 GpuCapability.isAvailable()（那是 raw GL 专属判定，Vulkan 后端下
        // 恒为 false）。现代 HUD 走 backend-neutral 的 Blaze3D CommandEncoder 管线，用
        // Blaze3D26_1_2Capability 的能力探测（GpuDevice/RenderPass API 存在性，GL/Vulkan 通用）。
        Blaze3D26_1_2Capability.Report report = Blaze3D26_1_2Capability.report();
        if (!report.stableGraphicsApiPresent() || !report.createBufferPresent()
                || !report.createRenderPassPresent() || !report.drawIndexedPresent()) {
            diag("26.1.2 graphics api probe failed");
            return false;
        }
        if (geoModel.bakedBones == null || geoModel.bakedBones.isEmpty()) {
            diag("no baked bones");
            return false;
        }
        if (geoModel.bakedBones.size() > Blaze3DBoneSkinPipeline.BONE_CAP) {
            diag("bone count exceeds UBO cap bones=" + geoModel.bakedBones.size()
                    + " cap=" + Blaze3DBoneSkinPipeline.BONE_CAP);
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
            // 骨骼参数；26.x fallback 评估不烘焙 → 缺失时看到背部（26.2 实测）。
            PoseStack poseStack = new PoseStack();
            poseStack.translate(x + boundMaxX * scale, y + boundMaxY * scale, GUI_CAMERA_Z);
            // GUI front-view coordinates need an X handedness correction. Keep the
            // negative Z used by the GUI camera, but do not leave the model left-right mirrored.
            poseStack.scale(-scale, scale, -scale);
            poseStack.mulPose(Axis.ZP.rotationDegrees(FRONT_FACING_YAW + 0.1f));
            // 26.1.2 的 fallback 评估已把 bodyRot=180 烘进骨骼参数（与 1.21.1 同，26.2 不烘焙），
            // 这里若再加 180 会 360°=背对，故只加 yawOffset。
            poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset));
            PoseStack.Pose pose = poseStack.last();

            boneBuf.clear();
            if (!Blaze3DBoneMatrices.write(geoModel, pose.pose(),
                    snapshot.matrixData(), snapshot.absPivotData(), PACKED_LIGHT, boneBuf)) {
                diag("bone matrix write failed");
                return false;
            }
            boneBuf.position(0);
            boneBuf.limit(geoModel.bakedBones.size() * 80);

            int boneSlice = nextBoneCursor();
            CommandEncoder encoder = device.createCommandEncoder();
            // 26.1.2 深度约定（前向-Z，DepthStencilState.DEFAULT = LESS_THAN_OR_EQUAL，
            // 与 1.21.1 同）：清深度 1.0（远值），正交矩阵标准 setOrtho（无 zZeroToOne）。
            encoder.writeToBuffer(boneSlices[boneSlice], boneBuf);
            encoder.writeToBuffer(projSlice, orthoBytes(fboLogicalWidth, fboLogicalHeight));

            // HUD 固定光照：用 1.21.1 现代 HUD 的显式双灯值（z 分量正负对称，正反面都能照到）。
            // 之前用 ENTITY_IN_UI：BBModel 正面（pose 后 +Z 法线）能照到，但 YSM 正面
            // （pose 后 -Z 法线）照不到 → 背亮前暗（26.2 实测 + 法线诊断确认法线本身无误）。
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
                // 26.1.2 writeTransform 返回 GpuBufferSlice（DynamicTransforms UBO），可直接 setUniform。
                var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                        IDENTITY_MATRIX, WHITE, overlayScratch, IDENTITY_MATRIX);

                try (RenderPass pass = encoder.createRenderPass(
                        () -> "sparkle_morpher_modern_hud",
                        fbo.getColorTextureView(),
                        OptionalInt.of(0),
                        fbo.getDepthTextureView(),
                        OptionalDouble.of(CLEAR_DEPTH))) {
                    // 26.1.2 RenderPass 无 RenderArea：viewport 覆盖整个 FBO 纹理（本 FBO 即所需尺寸）
                    pass.setPipeline(Blaze3DBoneSkinPipeline.PIPELINE);
                    RenderSystem.bindDefaultUniforms(pass);
                    pass.setUniform("DynamicTransforms", dynamicTransforms);
                    pass.setUniform("BoneMatrices", boneSlices[boneSlice]);
                    pass.bindTexture("Sampler0", modelTexture.getTextureView(), modelTexture.getSampler());
                    pass.bindTexture("Sampler1", overlayTextureView, clampSampler);
                    pass.bindTexture("Sampler2", lightmapTextureView, clampSampler);
                    pass.setVertexBuffer(0, mesh.vertexBuffer);
                    pass.setIndexBuffer(mesh.indexBuffer, VertexFormat.IndexType.INT);
                    int drawCount = mesh.indexDrawCount(0);
                    if (drawCount > 0) {
                        // 26.1.2 4 参：drawIndexed(baseVertex, firstIndex, count, instanceCount)
                        pass.drawIndexed(0, mesh.indexOffsetBytes(0) / Integer.BYTES, drawCount, 1);
                    }
                }
                // 一次性诊断：GL 错误码 + 关键状态 + FBO 像素回读（26.1.2 排查用，确认后移除）
                if (diagOnce.compareAndSet(false, true)) {
                    int err = GL11.glGetError();
                    YesSteveModel.LOGGER.info("[MODERN-HUD] diag: modelId={} bones={} writtenBytes={} meshV={} meshI={}"
                                    + " fbo={}x{} logical={}x{} drawCount={} glError=0x{}",
                            modelId, geoModel.bakedBones.size(), boneBuf.remaining(),
                            mesh.vertexCount, mesh.indexCount,
                            fboWidth, fboHeight, fboLogicalWidth, fboLogicalHeight, mesh.indexDrawCount(0),
                            Integer.toHexString(err));
                    try {
                        int w = fboWidth;
                        int h = fboHeight;
                        // GlDevice 非 public，反射读 GlTexture.id 用 glGetTexImage 回读
                        int texId = -1;
                        try {
                            Class<?> glTexClass = Class.forName("com.mojang.blaze3d.opengl.GlTexture");
                            java.lang.reflect.Field idField = glTexClass.getDeclaredField("id");
                            idField.setAccessible(true);
                            texId = idField.getInt(fbo.getColorTexture());
                        } catch (Throwable re) {
                            YesSteveModel.LOGGER.info("[MODERN-HUD] diag-fbo texId failed: {}", String.valueOf(re));
                        }
                        ByteBuffer px = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
                        if (texId >= 0) {
                            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
                            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
                            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
                        }
                        int alphaCount = 0;
                        int nonBlack = 0;
                        for (int i = 0; i < w * h; i++) {
                            int base = i * 4;
                            if ((px.get(base + 3) & 0xFF) > 0) {
                                alphaCount++;
                            }
                            if ((px.get(base) & 0xFF) > 8 || (px.get(base + 1) & 0xFF) > 8 || (px.get(base + 2) & 0xFF) > 8) {
                                nonBlack++;
                            }
                        }
                        YesSteveModel.LOGGER.info("[MODERN-HUD] diag-fbo: {}x{} total={} alpha>0={} nonBlack={}",
                                w, h, w * h, alphaCount, nonBlack);
                    } catch (Throwable t) {
                        YesSteveModel.LOGGER.info("[MODERN-HUD] diag-fbo failed: {}", String.valueOf(t));
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
        return hudX + scale * 0.5f;
    }

    /** Screen-space model origin used by the modern HUD attachment pass. */
    public float modelOriginY(float hudY, float scale) {
        return hudY + scale * 2.0f - 2.0f;
    }

    /** 人物锚点（模型原点）在 FBO 逻辑空间中的 X；合成阶段用它对齐用户配置的锚点。 */
    public float anchorX() {
        return fboAnchorX;
    }

    /** 人物锚点（模型原点）在 FBO 逻辑空间中的 Y；合成阶段用它对齐用户配置的锚点。 */
    public float anchorY() {
        return fboAnchorY;
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
        int boneCapBytes = Blaze3DBoneSkinPipeline.BONE_CAP * 80;
        for (int i = 0; i < BONE_RING; i++) {
            if (boneBuffers[i] == null) {
                GpuDevice device = RenderSystem.getDevice();
                if (device == null) {
                    return false;
                }
                // 26.1.2 骨骼走 std140 UBO：buffer 固定 cap 大小（shader 块固定），
                // 每帧只写入 boneCount*80 字节（writeToBuffer 拷 ByteBuffer.remaining）。
                boneBuffers[i] = device.createBuffer(
                        () -> "sparkle_morpher_modern_hud_bones",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        boneCapBytes);
                boneSlices[i] = new GpuBufferSlice(boneBuffers[i], 0, boneCapBytes);
            }
        }
        if (boneBuf == null) {
            boneBuf = ByteBuffer.allocateDirect(geoModel.bakedBones.size() * 80).order(ByteOrder.nativeOrder());
        }
        if (projBuffer == null) {
            GpuDevice device = RenderSystem.getDevice();
            if (device == null) {
                return false;
            }
            // Projection UBO = mat4 ProjMat（64B）
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
        var mainTarget = mc.getMainRenderTarget();
        if (screenW <= 0 || screenH <= 0 || mainTarget == null || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return false;
        }
        float pixelScaleX = mainTarget.width / (float) screenW;
        float pixelScaleY = mainTarget.height / (float) screenH;
        int fboWidth = Math.max(1, (int) Math.ceil(logicalWidth * pixelScaleX));
        int fboHeight = Math.max(1, (int) Math.ceil(logicalHeight * pixelScaleY));

        // 逻辑尺寸 / 锚点必须与 scale 同步：pixel 尺寸取整后可能不变而 scale 已变，
        // 若只在重建分支写入会导致合成时 logical 值过期（旧尺寸 + 旧锚点漂移）。
        this.fboLogicalWidth = logicalWidth;
        this.fboLogicalHeight = logicalHeight;
        // 人物锚点：渲染时模型 maxX/maxY 边被平移到 (FBO_PADDING, FBO_PADDING)，因此模型原点
        // (0,0,0) 在 FBO 逻辑空间落在 (FBO_PADDING + boundMaxX*scale, FBO_PADDING + boundMaxY*scale)。
        // 合成阶段用它把“人物锚点”对齐到用户配置的 GUI 位置，FBO 大小与位置语义彻底解耦。
        this.fboAnchorX = FBO_PADDING + boundMaxX * scale;
        this.fboAnchorY = FBO_PADDING + boundMaxY * scale;

        if (fbo == null || this.fboWidth != fboWidth || this.fboHeight != fboHeight) {
            if (fbo != null) {
                fbo.destroyBuffers();
            }
            // 26.1.2 TextureTarget 4 参（无 GpuFormat）：RGBA8 + depth 由 useDepth=true 决定
            fbo = new TextureTarget("sparkle_morpher_modern_hud", fboWidth, fboHeight, true);
            this.fboWidth = fboWidth;
            this.fboHeight = fboHeight;
        }
        return true;
    }

    private int nextBoneCursor() {
        int id = boneCursor;
        boneCursor = (boneCursor + 1) % BONE_RING;
        return id;
    }

    private static final float[] orthoScratch = new float[16];

    private static ByteBuffer orthoBytes(int logicalWidth, int logicalHeight) {
        // 观察空间 z 约定同 1.21.1：near=1000/far=21000 → 可见 z_eye ∈ [-21000, -1000]，
        // 相机取 -11000（GUI_CAMERA_Z 注释）。矩阵按列主序写入 GpuBufferSlice。
        // 26.1.2 前向-Z：标准 setOrtho（无 zZeroToOne），深度映射 near→近、far→远（清 1.0）。
        new Matrix4f().setOrtho(0.0f, logicalWidth, logicalHeight, 0.0f, 1000.0f, 21000.0f).get(orthoScratch);
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
