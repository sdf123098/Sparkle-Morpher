package com.micaftic.morpher.client.renderer.modernhud;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.gpu.BoneMatrixComputer;
import com.micaftic.morpher.core.gpu.BoneSkinShader;
import com.micaftic.morpher.core.gpu.GpuCapability;
import com.micaftic.morpher.core.gpu.GpuMesh;
import com.micaftic.morpher.core.gpu.GpuMeshBuilder;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL43;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modern HUD model renderer for Fabric 26.1.2.
 *
 * <p>This deliberately reuses the 1.21.1 OpenGL path: explicit VAO attributes,
 * 144-byte-per-bone SSBO records and the shared {@link BoneMatrixComputer}.
 * The broken 80-byte std140 UBO and legacy VertexFormatElement path is not used.
 */
public final class ModernHudRenderInstance {
    private static final int FBO_PADDING = 8;
    private static final int PACKED_LIGHT = 0xF000F0;
    private static final float GUI_CAMERA_Z = -11000.0f;
    private static final float FRONT_FACING_YAW = 180.0f;
    private static final Map<String, ModernHudRenderInstance> INSTANCES = new HashMap<>();
    private static final AtomicBoolean warnedRender = new AtomicBoolean(false);
    private static final float[] PROJECTION = new float[16];

    private final String modelId;
    private final GeoModel geoModel;
    private final float boundMinX, boundMinY, boundMaxX, boundMaxY;
    private float fboAnchorX;
    private float fboAnchorY;
    private GpuMesh mesh;
    private ByteBuffer boneBuffer;
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
                        minX = Math.min(minX, quad.x(v));
                        maxX = Math.max(maxX, quad.x(v));
                        minY = Math.min(minY, quad.y(v));
                        maxY = Math.max(maxY, quad.y(v));
                    }
                }
            }
        }
        if (Float.isInfinite(minX) || maxX - minX < 1e-4f || maxY - minY < 1e-4f) {
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

    public static ModernHudRenderInstance getOrCreate(String modelId, GeoModel geoModel) {
        synchronized (INSTANCES) {
            return INSTANCES.computeIfAbsent(modelId, id -> new ModernHudRenderInstance(id, geoModel));
        }
    }

    public static void release(String modelId) {
        synchronized (INSTANCES) {
            ModernHudRenderInstance instance = INSTANCES.remove(modelId);
            if (instance != null) instance.release();
        }
    }

    public static void releaseAll() {
        synchronized (INSTANCES) {
            for (ModernHudRenderInstance instance : INSTANCES.values()) instance.release();
            INSTANCES.clear();
        }
    }

    public boolean tryRender(PlayerPoseSnapshot snapshot, float x, float y, float scale,
                             float yawOffset, float partialTick) {
        if (released) return fail("instance released");
        if (!SmGraphicsBackendDetector.isRawOpenGlAllowed()) {
            return fail("raw OpenGL unavailable: " + SmGraphicsBackendDetector.reason());
        }
        if (!GpuCapability.isAvailable()) return fail("GPU unavailable: " + GpuCapability.getReason());
        if (!BoneSkinShader.ensureCompiled()) return fail("bone skin shader unavailable");
        if (geoModel.bakedBones == null || geoModel.bakedBones.isEmpty()) return fail("no baked bones");
        if (!ensureMesh() || !ensureFbo(scale)) return fail("mesh or FBO unavailable");

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return fail("Minecraft unavailable");
        boolean stateChanged = false;
        try {
            GpuDevice device = RenderSystem.getDevice();
            if (device == null) return fail("GPU device unavailable");
            CommandEncoder encoder = device.createCommandEncoder();
            try (RenderPass targetPass = encoder.createRenderPass(
                    () -> "sparkle_morpher_modern_hud_opengl",
                    fbo.getColorTextureView(), OptionalInt.of(0),
                    fbo.getDepthTextureView(), OptionalDouble.of(1.0))) {

            Matrix4f projection = new Matrix4f().setOrtho(
                    0.0f, fboLogicalWidth, fboLogicalHeight, 0.0f, 1000.0f, 21000.0f);
            projection.get(PROJECTION);

            PoseStack poseStack = new PoseStack();
            poseStack.translate(x + boundMaxX * scale, y + boundMaxY * scale, GUI_CAMERA_Z);
            poseStack.scale(-scale, scale, -scale);
            poseStack.mulPose(Axis.ZP.rotationDegrees(FRONT_FACING_YAW + 0.1f));
            poseStack.mulPose(Axis.YP.rotationDegrees(yawOffset));

            if (boneBuffer == null) {
                boneBuffer = MemoryUtil.memAlloc(mesh.boneCount * 144).order(ByteOrder.nativeOrder());
            }
            if (!BoneMatrixComputer.compute(geoModel, poseStack.last().pose(), poseStack.last().normal(),
                    snapshot.matrixData(), snapshot.absPivotData(), PACKED_LIGHT, boneBuffer)) {
                return fail("bone matrix computation failed");
            }

            AbstractTexture modelTexture = snapshot.texture() == null ? null
                    : mc.getTextureManager().getTexture(snapshot.texture());
            if (modelTexture == null) {
                modelTexture = mc.getTextureManager().getTexture(MissingTextureAtlasSprite.getLocation());
            }
            GpuTextureView modelView = modelTexture == null ? null : modelTexture.getTextureView();
            GpuTextureView overlayView = mc.gameRenderer.overlayTexture().getTextureView();
            GpuTextureView lightmapView = mc.gameRenderer.lightmap();
            int modelTextureId = textureId(modelView);
            int overlayId = textureId(overlayView);
            int lightmapId = textureId(lightmapView);
            int modelSampler = samplerId(modelTexture == null ? null : modelTexture.getSampler());
            int clampSampler = samplerId(RenderSystem.getSamplerCache().getClampToEdge(
                    com.mojang.blaze3d.textures.FilterMode.NEAREST));
            if (modelTextureId == 0 || overlayId == 0 || lightmapId == 0
                    || modelSampler == 0 || clampSampler == 0) {
                return fail("OpenGL texture binding unavailable");
            }

            GlStateManager._disableCull();
            GlStateManager._enableDepthTest();
            GlStateManager._depthMask(true);
            GlStateManager._disableBlend();
            stateChanged = true;

            bindTexture(2, lightmapId, clampSampler);
            bindTexture(1, overlayId, clampSampler);
            bindTexture(0, modelTextureId, modelSampler);

            int boneSsbo = mesh.nextBoneSsbo();
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, boneSsbo);
            GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, boneBuffer, GL15.GL_STREAM_DRAW);
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BoneSkinShader.ssbo, boneSsbo);

            GlStateManager._glUseProgram(BoneSkinShader.program());
            if (BoneSkinShader.locProj() >= 0) GL20.glUniformMatrix4fv(BoneSkinShader.locProj(), false, PROJECTION);
            if (BoneSkinShader.locColor() >= 0) GL20.glUniform4f(BoneSkinShader.locColor(), 1, 1, 1, 1);
            if (BoneSkinShader.locOverlay() >= 0) {
                GL20.glUniform1i(BoneSkinShader.locOverlay(), snapshot.hurtOverlay()
                        ? OverlayTexture.pack(OverlayTexture.u(1.0f), OverlayTexture.v(true))
                        : OverlayTexture.NO_OVERLAY);
            }
            if (BoneSkinShader.locFogStart() >= 0) GL20.glUniform1f(BoneSkinShader.locFogStart(), 0.0f);
            if (BoneSkinShader.locFogEnd() >= 0) GL20.glUniform1f(BoneSkinShader.locFogEnd(), 1.0f);
            if (BoneSkinShader.locFogColor() >= 0) GL20.glUniform4f(BoneSkinShader.locFogColor(), 0, 0, 0, 0);
            if (BoneSkinShader.locFogShape() >= 0) GL20.glUniform1i(BoneSkinShader.locFogShape(), 0);
            if (BoneSkinShader.locLight0() >= 0) GL20.glUniform3f(BoneSkinShader.locLight0(), 0.2f, 1.0f, -0.7f);
            if (BoneSkinShader.locLight1() >= 0) GL20.glUniform3f(BoneSkinShader.locLight1(), -0.2f, 1.0f, 0.7f);

            GlStateManager._glBindVertexArray(mesh.vao);
            if (BoneSkinShader.locAlphaMode() >= 0) GL20.glUniform1i(BoneSkinShader.locAlphaMode(), 1);
            drawMesh(mesh);
            if (geoModel.isTranslucentTexture(0)) {
                GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ZERO);
                if (BoneSkinShader.locAlphaMode() >= 0) GL20.glUniform1i(BoneSkinShader.locAlphaMode(), 2);
                drawMesh(mesh);
                GlStateManager._disableBlend();
            }
            return true;
            }
        } catch (Throwable t) {
            if (warnedRender.compareAndSet(false, true)) {
                YesSteveModel.LOGGER.warn("[MODERN-HUD] OpenGL render failed modelId={}", modelId, t);
            }
            return false;
        } finally {
            if (stateChanged) {
                GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, BoneSkinShader.ssbo, 0);
                GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
                GlStateManager._glUseProgram(0);
                BufferUploader.invalidate();
                GlStateManager._glBindVertexArray(0);
                GL33.glBindSampler(0, 0);
                GL33.glBindSampler(1, 0);
                GL33.glBindSampler(2, 0);
                GlStateManager._enableCull();
                GlStateManager._enableDepthTest();
                GlStateManager._depthMask(true);
                GlStateManager._disableBlend();
            }
        }
    }

    public GpuTextureView fboColorView() { return fbo == null ? null : fbo.getColorTextureView(); }
    public int fboLogicalWidth() { return fboLogicalWidth; }
    public int fboLogicalHeight() { return fboLogicalHeight; }
    public float modelOriginX(float hudX, float scale) { return hudX + scale * 0.5f; }
    public float modelOriginY(float hudY, float scale) { return hudY + scale * 2.0f - 2.0f; }
    public float anchorX() { return fboAnchorX; }
    public float anchorY() { return fboAnchorY; }

    private boolean ensureMesh() {
        if (mesh != null) return true;
        mesh = GpuMeshBuilder.build(geoModel);
        return mesh != null;
    }

    private boolean ensureFbo(float scale) {
        int logicalWidth = Math.max(1, Math.round((boundMaxX - boundMinX) * scale)) + FBO_PADDING * 2;
        int logicalHeight = Math.max(1, Math.round((boundMaxY - boundMinY) * scale)) + FBO_PADDING * 2;
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        var main = mc.getMainRenderTarget();
        if (screenW <= 0 || screenH <= 0 || main == null || main.width <= 0 || main.height <= 0) return false;
        float pixelScaleX = main.width / (float) screenW;
        float pixelScaleY = main.height / (float) screenH;
        int width = Math.max(1, (int) Math.ceil(logicalWidth * pixelScaleX));
        int height = Math.max(1, (int) Math.ceil(logicalHeight * pixelScaleY));
        fboLogicalWidth = logicalWidth;
        fboLogicalHeight = logicalHeight;
        fboAnchorX = FBO_PADDING + boundMaxX * scale;
        fboAnchorY = FBO_PADDING + boundMaxY * scale;
        if (fbo == null || fboWidth != width || fboHeight != height) {
            if (fbo != null) fbo.destroyBuffers();
            fbo = new TextureTarget("sparkle_morpher_modern_hud", width, height, true);
            fboWidth = width;
            fboHeight = height;
        }
        return true;
    }

    private static void bindTexture(int unit, int textureId, int samplerId) {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(textureId);
        GL33.glBindSampler(unit, samplerId);
    }

    private static int textureId(GpuTextureView view) {
        if (!(view instanceof GlTextureView glView)) return 0;
        try {
            return glView.texture().glId();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int samplerId(GpuSampler sampler) {
        if (!(sampler instanceof GlSampler glSampler)) return 0;
        try {
            return glSampler.getId();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void drawMesh(GpuMesh mesh) {
        if (mesh.indexCount > 0) {
            GL11.glDrawElements(GL11.GL_TRIANGLES, mesh.indexCount, GL11.GL_UNSIGNED_INT, 0L);
        }
    }

    private static boolean fail(String reason) {
        if (warnedRender.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.warn("[MODERN-HUD] render skipped: {}", reason);
        }
        return false;
    }

    private void release() {
        released = true;
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (boneBuffer != null) {
            MemoryUtil.memFree(boneBuffer);
            boneBuffer = null;
        }
        if (fbo != null) {
            fbo.destroyBuffers();
            fbo = null;
        }
    }

}
