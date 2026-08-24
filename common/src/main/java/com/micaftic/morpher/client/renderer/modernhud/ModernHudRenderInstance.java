package com.micaftic.morpher.client.renderer.modernhud;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.renderer.ExtraPlayerRenderProfiler;
import com.micaftic.morpher.core.gpu.BoneMatrixComputer;
import com.micaftic.morpher.core.gpu.BoneSkinShader;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.gpu.GpuMesh;
import com.micaftic.morpher.core.gpu.GpuMeshBuilder;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 现代 HUD 渲染实例（阶段 2，计划书 §4.2/§5 阶段 2）。
 *
 * <p>按本地玩家 + 模型 generation 缓存：持久 GPU mesh（复用 {@link GpuMeshBuilder}）、
 * 独立 HUD 骨骼 SSBO ring（避免世界 draw 与 HUD draw 共享缓冲互相覆盖）、独立小型
 * RenderTarget（只分配 HUD 物理像素区域，RGBA8 + depth）。
 *
 * <p>只接受 {@link PlayerPoseSnapshot}（世界帧动画评估结果），不经过
 * {@code GeoReplacedEntityRenderer} / {@code MultiBufferSource} —— 骨骼矩阵由
 * {@link BoneMatrixComputer} 计算（与世界渲染同一算法），提交为专用 GPU 路径。
 */
public final class ModernHudRenderInstance {

    private static final int FBO_PADDING = 8;
    private static final int BONE_SSBO_RING = 2;
    /** 经典 HUD 同款满亮 packed light（0xF000F0）。 */
    private static final int PACKED_LIGHT = 0xF000F0;
    private static final float FRONT_FACING_YAW = 180.0f;
    /**
     * FBO 观察空间 z。JOML setOrtho(l,r,b,t,near,far) 的可见范围是 z_eye ∈ [-far, -near]
     * （2026-08-14 实测验证）：本 FBO 投影 near=1000/far=21000 → 可见 [-21000, -1000]。
     * vanilla GUI 相机约定（1.21.1 GameRenderer.render）：modelview translation
     * (0, 0, 10000 - guiFarPlane)，guiFarPlane=21000 → z_eye=-11000。经典 HUD 的
     * GpuRenderPath 用 u_proj=proj*RenderSystem.modelView 隐式继承该平移，因此经典路径
     * poseStack z=0 也能可见；本路径 u_proj 只含自身正交投影，必须在 poseStack 显式给出
     * 负 z。历史教训：F4 曾误用 +16000（符号反）→ 全部顶点被 near 平面裁剪 → FBO 全透明
     * 且 tryRender 仍返回 true（无任何 GL 错误），表现为 submitted 计数正常但画面无模型。
     */
    private static final float GUI_CAMERA_Z = -11000.0f;

    private static final Map<String, ModernHudRenderInstance> INSTANCES = new HashMap<>();
    private static final AtomicBoolean warnedRender = new AtomicBoolean(false);

    private final String modelId;
    private final AnimatedGeoModel model;
    private final GeoModel geoModel;
    /** 静息姿态模型空间 AABB（块单位）。静息时骨骼矩阵为单位阵（pivot 平移自抵消），顶点即模型空间坐标。 */
    private final float boundMinX, boundMinY, boundMaxX, boundMaxY;

    private GpuMesh mesh;
    private final int[] boneSsbos = new int[BONE_SSBO_RING];
    private int boneSsboCursor;
    private ByteBuffer boneBuffer;
    private TextureTarget fbo;
    private int fboLogicalWidth;
    private int fboLogicalHeight;
    private int fboWidth;
    private int fboHeight;
    private boolean released;

    private ModernHudRenderInstance(String modelId, AnimatedGeoModel model) {
        this.modelId = modelId;
        this.model = model;
        this.geoModel = model.getGeoModel();
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
    public static ModernHudRenderInstance getOrCreate(String modelId, AnimatedGeoModel model) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(modelId, id -> new ModernHudRenderInstance(id, model));
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
     * 把玩家模型主体渲染进独立 HUD FBO。成功后调用方应把 {@link #textureId()} 合成到 GUI。
     * 不触发任何动画评估（骨骼来自 snapshot）。
     */
    public boolean tryRender(PlayerPoseSnapshot snapshot, float x, float y, float scale, float yawOffset,
                             float partialTick) {
        if (released) {
            diag("instance released");
            return false;
        }
        if (!GpuCapability.isAvailable()) {
            diag("gpu unavailable: " + GpuCapability.getReason());
            return false;
        }
        if (!BoneSkinShader.ensureCompiled()) {
            diag("bone skin shader not compiled");
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
        if (!ensureFbo(scale)) {
            diag("fbo ensure failed");
            return false;
        }
        if (boneBuffer == null) {
            boneBuffer = MemoryUtil.memAlloc(geoModel.bakedBones.size() * 144).order(ByteOrder.nativeOrder());
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTargetGuard guard = bindFbo(mc);
        boolean profile = ExtraPlayerRenderProfiler.enabled();
        try {
            long clearStart = profile ? System.nanoTime() : 0L;
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.depthMask(true);
            fbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            fbo.clear(false);
            fbo.bindWrite(true);
            if (profile) {
                ExtraPlayerRenderProfiler.recordClear(System.nanoTime() - clearStart);
            }

            RenderSystem.backupProjectionMatrix();
            RenderSystem.setProjectionMatrix(
                    new Matrix4f().setOrtho(0.0f, fboLogicalWidth, fboLogicalHeight, 0.0f, 1000.0f, 21000.0f),
                    com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);

            long boneStart = profile ? System.nanoTime() : 0L;
            PoseStack poseStack = new PoseStack();
            // 观察空间 z = -11000：setOrtho(near=1000, far=21000) 可见 z_eye ∈ [-21000, -1000]，
            // 取 vanilla GUI 相机约定位置 10000 - farPlane = -11000（1.21.1 GameRenderer.render
            // 的 modelview translation(0,0,10000-guiFarPlane)，经典 HUD 经 u_proj=proj*modelView
            // 隐式继承同一位置）。2026-08-14 实测：误用 +16000 时所有顶点在 near 平面外被裁剪，
            // FBO 全透明且无任何 GL 错误（见 GUI_CAMERA_Z 注释）。
            // framing 用模型真实静息 AABB：maxX/maxY 边对齐逻辑坐标 (x, y)（Rz180 镜像后
            // logical = translate - model*scale）；vanilla 2 块高模型下与旧常量等价。
            poseStack.translate(x + boundMaxX * scale, y + boundMaxY * scale, GUI_CAMERA_Z);
            // GUI front-view coordinates need an X handedness correction. Keep the
            // negative Z used by the GUI camera, but do not leave the model left-right mirrored.
            poseStack.scale(-scale, scale, -scale);
            poseStack.mulPose(Axis.ZP.rotationDegrees(FRONT_FACING_YAW + 0.1f));
            poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset));
            PoseStack.Pose pose = poseStack.last();

            boneBuffer.clear();
            if (!BoneMatrixComputer.compute(geoModel, pose.pose(), pose.normal(),
                    snapshot.matrixData(), snapshot.absPivotData(), PACKED_LIGHT, boneBuffer)) {
                return false;
            }
            boneBuffer.position(0);
            boneBuffer.limit(geoModel.bakedBones.size() * 144);
            if (profile) {
                ExtraPlayerRenderProfiler.recordBoneMatrices(System.nanoTime() - boneStart);
            }

            long ssboStart = profile ? System.nanoTime() : 0L;
            int boneSsbo = nextBoneSsbo();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boneSsbo);
            // Re-spec STREAM_DRAW storage：orphan 上一帧仍在被消费的 backing store，避免与
            // 世界/HUD 其他 draw 的隐式同步等待（与 GpuRenderPath 一致）。
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, boneBuffer, GL15.GL_STREAM_DRAW);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BoneSkinShader.ssbo, boneSsbo);
            if (profile) {
                ExtraPlayerRenderProfiler.recordSsboUpload(System.nanoTime() - ssboStart);
            }

            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();

            AbstractTexture modelTex;
            ResourceLocation textureLocation = snapshot.texture();
            if (textureLocation == null) {
                // 发布层已用模型默认纹理兜底；此处为最终防御，绝不 getTexture(null)
                textureLocation = MissingTextureAtlasSprite.getLocation();
                diag("texture null, using missing texture");
            }
            modelTex = mc.getTextureManager().getTexture(textureLocation);
            if (modelTex == null) {
                return false;
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 2);
            mc.gameRenderer.lightTexture().turnOnLightLayer();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + 1);
            mc.gameRenderer.overlayTexture().setupOverlayColor();
            GlStateManager._bindTexture(RenderSystem.getShaderTexture(1));
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(modelTex.getId());

            float fogStart = RenderSystem.getShaderFogStart();
            float fogEnd = RenderSystem.getShaderFogEnd();
            float[] fogColor = RenderSystem.getShaderFogColor();
            int fogShape = RenderSystem.getShaderFogShape().getIndex();

            GlStateManager._glUseProgram(BoneSkinShader.program());
            if (BoneSkinShader.locProj() >= 0) {
                GL20.glUniformMatrix4fv(BoneSkinShader.locProj(), false, projScratch(RenderSystem.getProjectionMatrix()));
            }
            if (BoneSkinShader.locColor() >= 0) {
                GL20.glUniform4f(BoneSkinShader.locColor(), 1.0f, 1.0f, 1.0f, 1.0f);
            }
            if (BoneSkinShader.locOverlay() >= 0) {
                // 必须用 NO_OVERLAY 而不是裸 0：overlay 纹理 (0,0) 槽不是透明 no-op，
                // 裸 0 会把整个模型染成该槽颜色（2026-08-14 实测发红）。与经典路径
                // packOverlayCoords 语义一致。
                GL20.glUniform1i(BoneSkinShader.locOverlay(),
                        snapshot.hurtOverlay()
                                ? OverlayTexture.pack(OverlayTexture.u(1.0f), OverlayTexture.v(true))
                                : OverlayTexture.NO_OVERLAY);
            }
            if (BoneSkinShader.locFogStart() >= 0) {
                GL20.glUniform1f(BoneSkinShader.locFogStart(), fogStart);
            }
            if (BoneSkinShader.locFogEnd() >= 0) {
                GL20.glUniform1f(BoneSkinShader.locFogEnd(), fogEnd);
            }
            if (BoneSkinShader.locFogColor() >= 0) {
                GL20.glUniform4f(BoneSkinShader.locFogColor(), fogColor[0], fogColor[1], fogColor[2], fogColor[3]);
            }
            if (BoneSkinShader.locFogShape() >= 0) {
                GL20.glUniform1i(BoneSkinShader.locFogShape(), fogShape);
            }

            Lighting.setupForEntityInInventory();
            // HUD 固定 inventory 灯光（与 GpuRenderPath.refreshLights 的默认一致），
            // 不依赖世界 RenderSystem light directions（HUD 渲染上下文隔离）。
            float[] light0 = new float[]{0.2f, 1.0f, -0.7f};
            float[] light1 = new float[]{-0.2f, 1.0f, 0.7f};
            if (BoneSkinShader.locLight0() >= 0) {
                GL20.glUniform3f(BoneSkinShader.locLight0(), light0[0], light0[1], light0[2]);
            }
            if (BoneSkinShader.locLight1() >= 0) {
                GL20.glUniform3f(BoneSkinShader.locLight1(), light1[0], light1[1], light1[2]);
            }

            long drawStart = profile ? System.nanoTime() : 0L;
            GlStateManager._glBindVertexArray(mesh.vao);
            // alphaMode 必须先于 draw 设置（不透明=1，translucent=2）；顺序颠倒会用残留值
            if (BoneSkinShader.locAlphaMode() >= 0) {
                GL20.glUniform1i(BoneSkinShader.locAlphaMode(), 1);
            }
            drawMeshParts(mesh, 0);
            if (geoModel.isTranslucentTexture(0)) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                if (BoneSkinShader.locAlphaMode() >= 0) {
                    GL20.glUniform1i(BoneSkinShader.locAlphaMode(), 2);
                }
                drawMeshParts(mesh, 0);
                RenderSystem.disableBlend();
            }
            if (profile) {
                ExtraPlayerRenderProfiler.recordDrawSubmission(System.nanoTime() - drawStart);
            }

            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BoneSkinShader.ssbo, 0);
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
            GlStateManager._glUseProgram(0);
            BufferUploader.invalidate();
            GlStateManager._glBindVertexArray(0);
            mc.gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            return true;
        } finally {
            RenderSystem.restoreProjectionMatrix();
            guard.restore(mc);
        }
    }

    public int textureId() {
        return fbo == null ? -1 : fbo.getColorTextureId();
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
        mesh = GpuMeshBuilder.build(geoModel);
        return mesh != null;
    }

    private boolean ensureFbo(float scale) {
        // 按模型真实静息尺寸 framing（带头发/饰品 >2 块的高模型不会被 FBO 顶边截断）；
        // vanilla 2 块高模型下退化为旧的 scale × 2*scale。
        int logicalWidth = Math.max(1, Math.round((boundMaxX - boundMinX) * scale)) + FBO_PADDING * 2;
        int logicalHeight = Math.max(1, Math.round((boundMaxY - boundMinY) * scale)) + FBO_PADDING * 2;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (screenW <= 0 || screenH <= 0 || mc.getMainRenderTarget().viewWidth <= 0 || mc.getMainRenderTarget().viewHeight <= 0) {
            return false;
        }
        float pixelScaleX = mc.getMainRenderTarget().viewWidth / (float) screenW;
        float pixelScaleY = mc.getMainRenderTarget().viewHeight / (float) screenH;
        int fboWidth = Math.max(1, (int) Math.ceil(logicalWidth * pixelScaleX));
        int fboHeight = Math.max(1, (int) Math.ceil(logicalHeight * pixelScaleY));

        if (fbo == null || this.fboWidth != fboWidth || this.fboHeight != fboHeight) {
            if (fbo != null) {
                fbo.destroyBuffers();
            }
            fbo = new TextureTarget(fboWidth, fboHeight, true, false);
            fbo.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            this.fboWidth = fboWidth;
            this.fboHeight = fboHeight;
            this.fboLogicalWidth = logicalWidth;
            this.fboLogicalHeight = logicalHeight;
        }
        return true;
    }

    private RenderTargetGuard bindFbo(Minecraft mc) {
        return new RenderTargetGuard(mc);
    }

    private int nextBoneSsbo() {
        int id = boneSsbos[boneSsboCursor];
        if (id == 0) {
            id = GL15.glGenBuffers();
            boneSsbos[boneSsboCursor] = id;
        }
        boneSsboCursor = (boneSsboCursor + 1) % boneSsbos.length;
        return id;
    }

    private static void drawMeshParts(GpuMesh mesh, int renderPartMask) {
        drawMeshPart(mesh.indexOffsetBytes(renderPartMask), mesh.indexDrawCount(renderPartMask));
        if ((renderPartMask == 1 || renderPartMask == 2) && mesh.partMask3Count > 0) {
            drawMeshPart(mesh.partMask3Start * Integer.BYTES, mesh.partMask3Count);
        }
    }

    private static void drawMeshPart(int offsetBytes, int drawCount) {
        if (drawCount > 0) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, drawCount, GL11.GL_UNSIGNED_INT, offsetBytes);
        }
    }

    private static final float[] projScratch = new float[16];

    private static float[] projScratch(Matrix4f projection) {
        projection.get(projScratch);
        return projScratch;
    }

    private void release() {
        released = true;
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        for (int i = 0; i < boneSsbos.length; i++) {
            if (boneSsbos[i] != 0) {
                GL15.glDeleteBuffers(boneSsbos[i]);
                boneSsbos[i] = 0;
            }
        }
        if (fbo != null) {
            fbo.destroyBuffers();
            fbo = null;
        }
        if (boneBuffer != null) {
            MemoryUtil.memFree(boneBuffer);
            boneBuffer = null;
        }
    }

    /** 绑定主目标备份 + 恢复（HUD FBO 绘制期间主目标状态隔离）。 */
    private static final class RenderTargetGuard {
        private final Minecraft mc;
        private boolean restored;

        RenderTargetGuard(Minecraft mc) {
            this.mc = mc;
        }

        void restore(Minecraft mc) {
            if (restored) {
                return;
            }
            restored = true;
            // FBO 解绑后恢复主目标绑定与视口（同经典 HUD 的 mainRenderTarget.bindWrite(true)）
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
