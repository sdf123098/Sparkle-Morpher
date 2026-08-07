package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.render.Blaze3D26_2Capability;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Experimental holder for the 26.2 Blaze3D/Vulkan model renderer.
 */
public final class Blaze3DRenderPath {
    private static final AtomicBoolean warnedIncompleteDrawPath = new AtomicBoolean(false);
    private static final AtomicBoolean warnedRuntimeFailure = new AtomicBoolean(false);
    private static final Map<GeoModel, Blaze3DModelMesh> meshCache = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Matrix4f IDENTITY_MATRIX = new Matrix4f();
    private static final Vector3f overlayScratch = new Vector3f();

    private Blaze3DRenderPath() {
    }

    public static boolean isExperimentalEnabled() {
        return GeneralConfig.safeGet(GeneralConfig.ENABLE_BLAZE3D_VULKAN_GPU_RENDERER, false);
    }

    public static boolean hasStableGraphicsApi() {
        Blaze3D26_2Capability.Report report = Blaze3D26_2Capability.report();
        return report.stableGraphicsApiPresent()
                && report.createBufferPresent()
                && report.precompilePipelinePresent()
                && report.createRenderPassPresent()
                && report.drawIndexedPresent();
    }

    public static boolean tryRender(
            GeoModel model,
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
        if (!isExperimentalEnabled()) {
            return false;
        }
        if (translucentTexture) {
            return false;
        }
        if (!hasStableGraphicsApi()) {
            if (warnedIncompleteDrawPath.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D render path unavailable: stable 26.2 graphics API probe failed");
            }
            return false;
        }

        try {
            RenderSystem.assertOnRenderThread();
            Minecraft mc = Minecraft.getInstance();
            GpuDevice device = RenderSystem.getDevice();
            if (mc == null || device == null || model.bakedBones == null || model.bakedBones.isEmpty()) {
                return false;
            }

            Blaze3DModelMesh mesh = getOrBuildMesh(model);
            if (mesh == null) {
                return false;
            }

            int drawCount = mesh.indexDrawCount(renderPartMask);
            if (drawCount <= 0 && (renderPartMask == 0 || renderPartMask == 3 || mesh.partMask3Count <= 0)) {
                GpuDebugLog.warn("Blaze3D render path fallback: drawCount={} partMask={} meshIndices={} pm1={} pm2={} pm3={}",
                        drawCount, renderPartMask, mesh.indexCount, mesh.partMask1Count, mesh.partMask2Count, mesh.partMask3Count);
                return false;
            }

            ByteBuffer boneBuf = mesh.perFrameBoneBuffer;
            if (!Blaze3DBoneMatrices.write(model, pose.pose(), pose.normal(), boneParams, stateBuffer, packedLight, boneBuf)) {
                GpuDebugLog.warn("Blaze3D render path fallback: bone matrix update failed bones={} boneParamsLen={}",
                        model.bakedBones.size(), boneParams == null ? -1 : boneParams.length);
                return false;
            }

            CommandEncoder encoder = device.createCommandEncoder();
            encoder.writeToBuffer(mesh.boneMatrixSlice(), boneBuf);

            AbstractTexture modelTexture = mc.getTextureManager().getTexture(textureLocation);
            if (modelTexture == null || modelTexture.getTextureView() == null || modelTexture.getSampler() == null) {
                return false;
            }

            OverlayTexture overlayTexture = mc.gameRenderer.overlayTexture();
            GpuTextureView overlayTextureView = overlayTexture == null ? null : overlayTexture.getTextureView();
            GpuTextureView lightmapTextureView = mc.gameRenderer.lightmap();
            GpuSampler clampSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            if (overlayTextureView == null || lightmapTextureView == null || clampSampler == null) {
                return false;
            }

            RenderTarget target = mc.gameRenderer.mainRenderTarget();
            if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) {
                return false;
            }

            overlayScratch.set((float) packedOverlay, 0.0f, 0.0f);
            var dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
                    IDENTITY_MATRIX,
                    new Vector4f(r, g, b, a),
                    overlayScratch,
                    IDENTITY_MATRIX
            );

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "sparkle_morpher_blaze3d_model",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(Blaze3DBoneSkinPipeline.PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.setUniform("BoneMatrices", mesh.boneMatrixSlice());
                pass.bindTexture("Sampler0", modelTexture.getTextureView(), modelTexture.getSampler());
                pass.bindTexture("Sampler1", overlayTextureView, clampSampler);
                pass.bindTexture("Sampler2", lightmapTextureView, clampSampler);
                pass.setVertexBuffer(0, mesh.vertexSlice());
                pass.setIndexBuffer(mesh.indexBuffer, IndexType.INT);
                drawMeshParts(pass, mesh, renderPartMask);
            }

            return true;
        } catch (Throwable t) {
            if (warnedRuntimeFailure.compareAndSet(false, true)) {
                GpuDebugLog.warn("Blaze3D render path failed for texture={}: {}: {}; falling back",
                        textureLocation, t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
            }
            return false;
        }
    }

    private static Blaze3DModelMesh getOrBuildMesh(GeoModel model) {
        Blaze3DModelMesh mesh = meshCache.get(model);
        if (mesh != null) {
            return mesh;
        }
        mesh = Blaze3DModelMeshBuilder.build(model);
        if (mesh != null) {
            meshCache.put(model, mesh);
        }
        return mesh;
    }

    private static void drawMeshParts(RenderPass pass, Blaze3DModelMesh mesh, int renderPartMask) {
        drawMeshPart(pass, mesh.indexOffsetBytes(renderPartMask), mesh.indexDrawCount(renderPartMask));
        if ((renderPartMask == 1 || renderPartMask == 2) && mesh.partMask3Count > 0) {
            drawMeshPart(pass, mesh.partMask3Start * Integer.BYTES, mesh.partMask3Count);
        }
    }

    private static void drawMeshPart(RenderPass pass, int offsetBytes, int drawCount) {
        if (drawCount > 0) {
            pass.drawIndexed(drawCount, 1, offsetBytes / Integer.BYTES, 0, 0);
        }
    }
}
