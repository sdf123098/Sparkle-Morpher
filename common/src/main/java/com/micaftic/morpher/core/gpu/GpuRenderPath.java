package com.micaftic.morpher.core.gpu;

import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.renderer.WorldRenderState;
import com.micaftic.morpher.core.render.SmGraphicsBackendDetector;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executor;

public final class GpuRenderPath {
    private static final float[] projScratch = new float[16];
    private static final Matrix4f projMVScratch = new Matrix4f();
    private static final Vector3f[] currentLights = new Vector3f[2];
    private static final GpuMeshRegistry<GpuMesh> MESH_REGISTRY = new GpuMeshRegistry<>();
    private static final float[] fogColorScratch = new float[4];
    private static final Vector3f light0Scratch = new Vector3f(0.2f, 1.0f, -0.7f).normalize();
    private static final Vector3f light1Scratch = new Vector3f(-0.2f, 1.0f, 0.7f).normalize();
    private static final RenderStateCache stateCache = new RenderStateCache();

    public static boolean tryRender(
            BuiltModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            float[] stateBuffer,
            int renderPartMask,
            int packedLight,
            int packedOverlay,
            float r, float g, float b, float a,
            Identifier textureLocation,
            boolean translucentTexture
    ) {
        long frameId = GpuDebugLog.nextFrame();
        if (translucentTexture) {
            GpuDebugLog.verbose("frame={} fallback: translucent texture={}", frameId, textureLocation);
            return false;
        }
        if (!SmGraphicsBackendDetector.isRawOpenGlAllowed()) {
            disposeAllMeshes("raw OpenGL disabled: " + SmGraphicsBackendDetector.currentBackend());
            GpuDebugLog.warn("frame={} fallback: raw OpenGL disabled backend={} reason={}", frameId,
                    SmGraphicsBackendDetector.currentBackend(), SmGraphicsBackendDetector.reason());
            return false;
        }
        if (!GpuCapability.isAvailable()) {
            GpuDebugLog.warn("frame={} fallback: GPU unavailable reason={}", frameId, GpuCapability.getReason());
            return false;
        }
        if (!BoneSkinShader.ensureCompiled()) {
            GpuDebugLog.warn("frame={} fallback: BoneSkinShader compile unavailable", frameId);
            return false;
        }
        if (model.bakedBones == null || model.bakedBones.isEmpty()) {
            GpuDebugLog.verbose("frame={} fallback: no baked bones texture={}", frameId, textureLocation);
            return false;
        }
        stateCache.invalidate();

        if (model.gpuMeshHandle == 0) {
            GpuDebugLog.info("frame={} building mesh bones={} texture={}", frameId, model.bakedBones.size(), textureLocation);
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) {
                GpuDebugLog.warn("frame={} fallback: mesh build returned null texture={}", frameId, textureLocation);
                return false;
            }
            model.gpuMeshHandle = encodeMeshRef(model, mesh);
        }
        GpuMesh mesh = decodeMeshRef(model.gpuMeshHandle);
        if (mesh == null) {
            GpuDebugLog.warn("frame={} fallback: mesh ref missing ref={} texture={}", frameId, model.gpuMeshHandle, textureLocation);
            model.gpuMeshHandle = 0;
            GpuMesh rebuilt = GpuMeshBuilder.build(model);
            if (rebuilt == null) {
                return false;
            }
            model.gpuMeshHandle = encodeMeshRef(model, rebuilt);
            mesh = rebuilt;
        }

        int drawCount = mesh.indexDrawCount(renderPartMask);
        if (drawCount <= 0 && (renderPartMask == 0 || renderPartMask == 3 || mesh.partMask3Count <= 0)) {
            GpuDebugLog.warn("frame={} fallback: drawCount={} partMask={} meshIndices={} pm1={} pm2={} pm3={}",
                    frameId, drawCount, renderPartMask, mesh.indexCount, mesh.partMask1Count, mesh.partMask2Count, mesh.partMask3Count);
            return false;
        }

        Matrix4f rootPose = pose.pose();
        Matrix3f rootNormal = pose.normal();
        Matrix4f projMat = projMVScratch;
        if (!WorldRenderState.getProjectionMatrix(projMat)) {
            GpuDebugLog.warn("frame={} fallback: missing world projection matrix texture={}", frameId, textureLocation);
            return false;
        }
        projMat.get(projScratch);

        ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
        boneBuf.clear();

        if (!computeBoneMatrices(model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, boneBuf)) {
            GpuDebugLog.warn("frame={} fallback: Java bone matrices failed bones={} boneParamsLen={}",
                    frameId, model.bakedBones.size(), boneParams == null ? -1 : boneParams.length);
            return false;
        }
        boneBuf.position(0);
        boneBuf.limit(mesh.boneCount * 144);

        Minecraft mc = Minecraft.getInstance();
        AbstractTexture modelTex = mc.getTextureManager().getTexture(textureLocation);
        TextureBinding modelTexture = resolveTextureBinding(modelTex);
        int modelSamplerId = resolveSamplerId(modelTex != null ? modelTex.getSampler() : null);
        TextureBinding overlayTexture = resolveOverlayTexture(mc.gameRenderer.overlayTexture());
        int clampSamplerId = resolveClampSamplerId();
        TextureBinding lightmapTexture = resolveLightmapTexture(mc);
        if (!modelTexture.isValid() || modelSamplerId == 0 || !overlayTexture.isValid() || clampSamplerId == 0 || !lightmapTexture.isValid()) {
            GpuDebugLog.warn("frame={} fallback: invalid texture binding modelTex={} modelSampler={} overlayTex={} clampSampler={} lightmapTex={} texture={}",
                    frameId, modelTexture.isValid(), modelSamplerId, overlayTexture.isValid(), clampSamplerId, lightmapTexture.isValid(), textureLocation);
            return false;
        }

        GlStateManager._disableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
        GlStateManager._disableBlend(0);

        stateCache.activeTexture(GL13.GL_TEXTURE0 + 2);
        bindTextureView(lightmapTexture);
        stateCache.bindSampler(2, clampSamplerId);

        stateCache.activeTexture(GL13.GL_TEXTURE0 + 1);
        bindTextureView(overlayTexture);
        stateCache.bindSampler(1, clampSamplerId);

        stateCache.activeTexture(GL13.GL_TEXTURE0);
        bindTextureView(modelTexture);
        stateCache.bindSampler(0, modelSamplerId);

        int boneSsbo = mesh.nextBoneSsbo();
        stateCache.bindSsbo(boneSsbo);
        GL15.glBufferData(GL43.GL_SHADER_STORAGE_BUFFER, boneBuf, GL15.GL_STREAM_DRAW);
        stateCache.bindSsboBase(BoneSkinShader.ssbo, boneSsbo);

        float fogStart = 0f;
        float fogEnd = 1f;
        float[] fogColor = fogColorScratch;
        int fogShape = 0; // MC 26.x: getShaderFogShape() return type changed

        stateCache.useProgram(BoneSkinShader.program());
        if (BoneSkinShader.locProj() >= 0) GL20.glUniformMatrix4fv(BoneSkinShader.locProj(), false, projScratch);
        if (BoneSkinShader.locColor() >= 0) GL20.glUniform4f(BoneSkinShader.locColor(), r, g, b, a);
        if (BoneSkinShader.locOverlay() >= 0) GL20.glUniform1i(BoneSkinShader.locOverlay(), packedOverlay);
        if (BoneSkinShader.locFogStart() >= 0) GL20.glUniform1f(BoneSkinShader.locFogStart(), fogStart);
        if (BoneSkinShader.locFogEnd() >= 0) GL20.glUniform1f(BoneSkinShader.locFogEnd(), fogEnd);

        if (BoneSkinShader.locFogColor() >= 0)
            GL20.glUniform4f(BoneSkinShader.locFogColor(), fogColor[0], fogColor[1], fogColor[2], fogColor[3]);

        if (BoneSkinShader.locFogShape() >= 0) GL20.glUniform1i(BoneSkinShader.locFogShape(), fogShape);

        refreshLights();

        if (BoneSkinShader.locLight0() >= 0)
            GL20.glUniform3f(BoneSkinShader.locLight0(), currentLights[0].x, currentLights[0].y, currentLights[0].z);
        if (BoneSkinShader.locLight1() >= 0)
            GL20.glUniform3f(BoneSkinShader.locLight1(), currentLights[1].x, currentLights[1].y, currentLights[1].z);

        GlStateManager._glBindVertexArray(mesh.vao);

        int alphaMode = 1;
        if (BoneSkinShader.locAlphaMode() >= 0) GL20.glUniform1i(BoneSkinShader.locAlphaMode(), alphaMode);
        GpuDebugLog.verbose("frame={} draw texture={} translucent={} alphaMode={} partMask={} drawCount={} part3Extra={} vertices={} indices={} proj00={} proj11={} proj22={} root30={} root31={} root32={}",
                frameId, textureLocation, translucentTexture, alphaMode, renderPartMask, drawCount,
                (renderPartMask == 1 || renderPartMask == 2) ? mesh.partMask3Count : 0,
                mesh.vertexCount, mesh.indexCount,
                projScratch[0], projScratch[5], projScratch[10],
                rootPose.m30(), rootPose.m31(), rootPose.m32());
        drawMeshParts(mesh, renderPartMask);
        GpuDebugLog.glError("draw frame=" + frameId);

        stateCache.bindSsboBase(BoneSkinShader.ssbo, 0);
        stateCache.bindSsbo(0);
        stateCache.useProgram(0);

        GlStateManager._glBindVertexArray(0);
        restoreRenderState();
        GpuDebugLog.glError("restore frame=" + frameId);

        return true;
    }

    private static void restoreRenderState() {
        stateCache.restoreTextureParameters();
        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(1, 0);
        GL33.glBindSampler(2, 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._disableBlend(0);
        GlStateManager._enableCull();
        GlStateManager._enableDepthTest();
        GlStateManager._depthMask(true);
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

    private static void refreshLights() {
        currentLights[0] = light0Scratch;
        currentLights[1] = light1Scratch;
    }

    private static boolean computeBoneMatrices(
            BuiltModel model,
            Matrix4f rootPose,
            Matrix3f rootNormal,
            float[] boneParams,
            float[] stateBuffer,
            int packedLight,
            ByteBuffer out
    ) {
        return SpmCoreBoneMatrixWriter.writeEvaluated(
                model, rootPose, rootNormal, boneParams, stateBuffer, packedLight, out, false
        );
    }

    private static TextureBinding resolveTextureBinding(AbstractTexture texture) {
        if (texture == null) {
            return TextureBinding.EMPTY;
        }
        try {
            return resolveTextureBinding(texture.getTextureView());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static TextureBinding resolveTextureBinding(GpuTextureView textureView) {
        if (!(textureView instanceof GlTextureView glTextureView)) {
            return TextureBinding.EMPTY;
        }
        try {
            GlTexture glTexture = glTextureView.texture();
            return new TextureBinding(glTexture.glId(), textureView.baseMipLevel(), textureView.mipLevels());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static void bindTextureView(TextureBinding texture) {
        stateCache.bindTexture(texture);
    }

    private static int resolveSamplerId(GpuSampler sampler) {
        if (!(sampler instanceof GlSampler glSampler)) {
            return 0;
        }
        try {
            return glSampler.getId();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static TextureBinding resolveOverlayTexture(OverlayTexture overlayTexture) {
        if (overlayTexture == null) {
            return TextureBinding.EMPTY;
        }
        try {
            return resolveTextureBinding(overlayTexture.getTextureView());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static TextureBinding resolveLightmapTexture(Minecraft mc) {
        try {
            return resolveTextureBinding(mc.gameRenderer.lightmap());
        } catch (RuntimeException ignored) {
            return TextureBinding.EMPTY;
        }
    }

    private static int resolveClampSamplerId() {
        try {
            return resolveSamplerId(RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static void disposeMesh(BuiltModel model) {
        if (model.gpuMeshHandle == 0) return;
        GpuMesh mesh = MESH_REGISTRY.remove(model.gpuMeshHandle);
        if (mesh != null) mesh.dispose();
        model.gpuMeshHandle = 0;
    }

    /**
     * R10.2：释放指定模型（BuiltModel，即租约 owner）名下的全部 GPU mesh。
     * 与 {@link #disposeMesh} 等价（每个 BuiltModel 至多一个 mesh），但语义按 owner 释放，
     * 供 RenderBackend.release 走统一的租约 revoke 路径。
     */
    public static void disposeOwner(BuiltModel model) {
        if (model.gpuMeshHandle != 0) {
            disposeMesh(model);
            return;
        }
        for (GpuMesh mesh : MESH_REGISTRY.releaseOwner(model)) {
            mesh.dispose();
        }
    }

    /**
     * R10.2：回收孤儿 GPU mesh（owner 弱引用已失效 / 无 owner 的残留），GC 兜底防泄漏。
     * 正常路径由模型装配释放显式 disposeMesh，本方法只兜底异常/替换路径。
     */
    public static void sweepOrphanedMeshes(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> sweepOrphanedMeshes(reason));
            return;
        }
        int releasedMeshes = 0;
        long releasedBytes = 0L;
        for (GpuMesh mesh : MESH_REGISTRY.sweepOrphans()) {
            if (mesh != null) {
                releasedMeshes++;
                releasedBytes += mesh.estimatedBytes;
                mesh.dispose();
            }
        }
        if (releasedMeshes > 0) {
            GpuDebugLog.info("disposed orphan GPU meshes reason={} releasedMeshes={} estimatedReleasedBytes={}",
                    reason, releasedMeshes, releasedBytes);
        }
    }

    public static void disposeAllMeshes(String reason) {
        if (!RenderSystem.isOnRenderThread()) {
            ((Executor) Minecraft.getInstance()).execute(() -> disposeAllMeshes(reason));
            return;
        }
        int releasedMeshes = 0;
        long releasedBytes = 0L;
        for (GpuMesh mesh : MESH_REGISTRY.clearAll()) {
            if (mesh != null) {
                releasedMeshes++;
                releasedBytes += mesh.estimatedBytes;
                mesh.dispose();
            }
        }
        if (releasedMeshes > 0) {
            GpuDebugLog.info("disposed all GPU meshes reason={} releasedMeshes={} estimatedReleasedBytes={}",
                    reason, releasedMeshes, releasedBytes);
        }
    }

    public static GpuMesh getOrBuildMesh(BuiltModel model) {
        if (model.gpuMeshHandle == 0) {
            GpuMesh mesh = GpuMeshBuilder.build(model);
            if (mesh == null) return null;
            model.gpuMeshHandle = encodeMeshRef(model, mesh);
        }
        return decodeMeshRef(model.gpuMeshHandle);
    }

    private static long encodeMeshRef(BuiltModel owner, GpuMesh mesh) {
        return MESH_REGISTRY.register(owner, mesh);
    }

    private static GpuMesh decodeMeshRef(long ref) {
        return MESH_REGISTRY.get(ref);
    }

    private static final class RenderStateCache {
        private final int[] textureIds = new int[8];
        private final int[] samplerIds = new int[8];
        private final int[] ssboBases = new int[8];
        private final int[] savedTextureIds = new int[16];
        private final int[] savedTextureBaseMip = new int[16];
        private final int[] savedTextureMaxMip = new int[16];
        private int activeTexture = -1;
        private int ssbo = -1;
        private int program = -1;
        private int savedTextureCount = 0;

        void invalidate() {
            Arrays.fill(textureIds, Integer.MIN_VALUE);
            Arrays.fill(samplerIds, Integer.MIN_VALUE);
            Arrays.fill(ssboBases, Integer.MIN_VALUE);
            activeTexture = -1;
            ssbo = -1;
            program = -1;
            savedTextureCount = 0;
        }

        void useProgram(int id) {
            if (program == id) {
                return;
            }
            GlStateManager._glUseProgram(id);
            program = id;
        }

        void bindSsbo(int id) {
            if (ssbo == id) {
                return;
            }
            GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, id);
            ssbo = id;
        }

        void bindSsboBase(int index, int id) {
            if (index >= 0 && index < ssboBases.length && ssboBases[index] == id) {
                return;
            }
            GL43.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, id);
            if (index >= 0 && index < ssboBases.length) {
                ssboBases[index] = id;
            }
        }

        void bindTexture(TextureBinding texture) {
            int unit = Math.max(0, activeTexture - GL13.GL_TEXTURE0);
            if (unit >= textureIds.length) {
                GlStateManager._bindTexture(texture.id);
                applyTextureView(texture);
                return;
            }
            if (textureIds[unit] != texture.id) {
                GlStateManager._bindTexture(texture.id);
                textureIds[unit] = texture.id;
            }
            applyTextureView(texture);
        }

        void activeTexture(int texture) {
            if (activeTexture == texture) {
                return;
            }
            GlStateManager._activeTexture(texture);
            activeTexture = texture;
        }

        void bindSampler(int unit, int sampler) {
            if (unit >= 0 && unit < samplerIds.length && samplerIds[unit] == sampler) {
                return;
            }
            GL33.glBindSampler(unit, sampler);
            if (unit >= 0 && unit < samplerIds.length) {
                samplerIds[unit] = sampler;
            }
        }

        private void applyTextureView(TextureBinding texture) {
            rememberTextureParameters(texture.id);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, texture.baseMipLevel);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, texture.maxMipLevel());
        }

        private void rememberTextureParameters(int textureId) {
            if (textureId == 0) {
                return;
            }
            for (int i = 0; i < savedTextureCount; i++) {
                if (savedTextureIds[i] == textureId) {
                    return;
                }
            }
            if (savedTextureCount >= savedTextureIds.length) {
                return;
            }
            savedTextureIds[savedTextureCount] = textureId;
            savedTextureBaseMip[savedTextureCount] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL);
            savedTextureMaxMip[savedTextureCount] = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL);
            savedTextureCount++;
        }

        void restoreTextureParameters() {
            if (savedTextureCount <= 0) {
                return;
            }
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            for (int i = 0; i < savedTextureCount; i++) {
                GlStateManager._bindTexture(savedTextureIds[i]);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, savedTextureBaseMip[i]);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, savedTextureMaxMip[i]);
                savedTextureIds[i] = 0;
            }
            savedTextureCount = 0;
        }
    }

    private static final class TextureBinding {
        private static final TextureBinding EMPTY = new TextureBinding(0, 0, 0);

        final int id;
        final int baseMipLevel;
        final int mipLevels;

        TextureBinding(int id, int baseMipLevel, int mipLevels) {
            this.id = id;
            this.baseMipLevel = baseMipLevel;
            this.mipLevels = mipLevels;
        }

        boolean isValid() {
            return id != 0 && mipLevels > 0;
        }

        int maxMipLevel() {
            return baseMipLevel + mipLevels - 1;
        }
    }
}
