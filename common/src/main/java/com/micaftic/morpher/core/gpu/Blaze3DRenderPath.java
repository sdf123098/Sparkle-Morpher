package com.micaftic.morpher.core.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.core.acceleration.AccelerationCapability;
import com.micaftic.morpher.core.render.Blaze3D26_2Capability;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
    private static final float[] rootPoseScratch = new float[16];
    private static final float[] rootNormalScratch = new float[9];
    private static final AtomicBoolean reportedNativeStorageDraw = new AtomicBoolean();
    private static final AtomicBoolean warnedNativeStorageFallback = new AtomicBoolean();

    private Blaze3DRenderPath() {
    }

    public static boolean isExperimentalEnabled() {
        var graphics = ConfigPolicies.graphics();
        return graphics.blaze3dVulkanGpuRenderer()
                && graphics.backendMode() != com.micaftic.morpher.core.render.SmRenderBackendMode.VANILLA_PIPELINE_ONLY
                && graphics.backendMode() != com.micaftic.morpher.core.render.SmRenderBackendMode.DISABLED_GPU_ACCELERATION;
    }

    public static boolean hasStableGraphicsApi() {
        Blaze3D26_2Capability.Report report = Blaze3D26_2Capability.report();
        return report.stableGraphicsApiPresent()
                && report.createBufferPresent()
                && report.precompilePipelinePresent()
                && report.createRenderPassPresent()
                && report.drawIndexedPresent();
    }

    /** Prepare/upload a separate bone snapshot before Minecraft starts executing feature passes. */
    public static PreparedDraw prepare(GeoModel model, PoseStack.Pose pose, float[] boneParams,
            float[] stateBuffer, int renderPartMask, int packedLight, int packedOverlay,
            float r, float g, float b, float a, Identifier texture, boolean translucent,
            com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass bonePass, boolean outline) {
        if (!isExperimentalEnabled() || model == null || texture == null) return null;
        VulkanBoneBufferPool.Lease lease = null;
        try {
            RenderSystem.assertOnRenderThread();
            Blaze3DModelMesh mesh = getOrBuildMesh(model);
            if (mesh == null) return null;
            ByteBuffer bones = mesh.perFrameBoneBuffer;
            boolean nativeComputed = computeBoneMatricesNativeVulkan(model, pose, boneParams, packedLight, bones);
            if (nativeComputed) {
                com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.computeStateBufferForNative(
                        model, boneParams, stateBuffer, pose);
            } else {
                if (warnedNativeStorageFallback.compareAndSet(false, true)) {
                    com.micaftic.morpher.YesSteveModel.LOGGER.warn("[SM-VULKAN-SSBO] Native SIMD bone computation unavailable; Java bone fallback is active and does not count as a complete SIMD/GPU chain");
                }
                if (!Blaze3DBoneMatrices.write(model, pose.pose(), pose.normal(), boneParams, stateBuffer, packedLight, bones)) return null;
            }
            if (bonePass != com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass.ALL) {
                for (int i = 0; i < model.bakedBones.size(); i++) {
                    boolean glow = model.bakedBones.get(i).glow;
                    if (glow != (bonePass == com.elfmcys.yesstevemodel.geckolib3.geo.ModelRendererBridge.BoneRenderPass.GLOW)) {
                        bones.putFloat(i * 144 + 132, 1.0f);
                    }
                }
            }
            Minecraft mc = Minecraft.getInstance();
            AbstractTexture modelTexture = mc.getTextureManager().getTexture(texture);
            var overlayTexture = mc.gameRenderer.overlayTexture();
            var lightmap = mc.gameRenderer.lightmap();
            if (modelTexture == null || modelTexture.getTextureView() == null || modelTexture.getSampler() == null
                    || overlayTexture == null || overlayTexture.getTextureView() == null || lightmap == null) return null;
            lease = mesh.boneStoragePool.acquire();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(lease.buffer.slice(), bones);
            return new PreparedDraw(mesh, lease, new Vector4f(r, g, b, a), packedOverlay, modelTexture.getTextureView(), modelTexture.getSampler(),
                    overlayTexture.getTextureView(), lightmap, renderPartMask,
                    outline ? Blaze3DBoneSkinPipeline.OUTLINE_PIPELINE : translucent
                            ? Blaze3DBoneSkinPipeline.TRANSLUCENT_PIPELINE : Blaze3DBoneSkinPipeline.PIPELINE,
                    RenderSystem.getScissorStateForRenderTypeDraws(), nativeComputed);
        } catch (Throwable failure) {
            if (lease != null) lease.retire();
            com.micaftic.morpher.YesSteveModel.LOGGER.error("[SM-VULKAN-SSBO] prepare failed; native/GPU chain was not accepted", failure);
            return null;
        }
    }

    public static final class PreparedDraw implements AutoCloseable {
        private final Blaze3DModelMesh mesh;
        private final VulkanBoneBufferPool.Lease bones;
        private com.mojang.blaze3d.buffers.GpuBufferSlice dynamic;
        private final Vector4f color;
        private final int packedOverlay;
        private final GpuTextureView texture, overlay, lightmap;
        private final GpuSampler sampler;
        private final int partMask;
        private final RenderPipeline pipeline;
        private final com.mojang.blaze3d.systems.ScissorState scissor;
        private final boolean nativeComputed;

        private PreparedDraw(Blaze3DModelMesh mesh, VulkanBoneBufferPool.Lease bones,
                Vector4f color, int packedOverlay, GpuTextureView texture, GpuSampler sampler,
                GpuTextureView overlay, GpuTextureView lightmap, int partMask, RenderPipeline pipeline,
                com.mojang.blaze3d.systems.ScissorState scissor, boolean nativeComputed) {
            this.mesh = mesh; this.bones = bones; this.color = color; this.packedOverlay = packedOverlay; this.texture = texture;
            this.sampler = sampler; this.overlay = overlay; this.lightmap = lightmap;
            this.partMask = partMask; this.pipeline = pipeline; this.scissor = scissor; this.nativeComputed = nativeComputed;
        }

        public void prepareView() {
            dynamic = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(),
                    color, new Vector3f((float) packedOverlay, 0, 0), IDENTITY_MATRIX);
        }

        public void draw(RenderTarget target) {
            GpuTextureView color = RenderSystem.outputColorTextureOverride != null
                    ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
            GpuTextureView depth = !target.useDepth ? null : RenderSystem.outputDepthTextureOverride != null
                    ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
            try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "sparkle_morpher_vulkan_ssbo_feature", color, Optional.empty(), depth, OptionalDouble.empty())) {
                drawInto(pass);
            }
        }

        public void drawInto(RenderPass pass) {
            pass.setPipeline(pipeline);
            if (scissor.enabled()) pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
            else pass.disableScissor();
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dynamic);
            pass.setUniform(VulkanBoneStorage.NAME, bones.buffer.slice());
            var clamp = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            pass.bindTexture("Sampler0", texture, sampler);
            pass.bindTexture("Sampler1", overlay, clamp);
            pass.bindTexture("Sampler2", lightmap, clamp);
            pass.setVertexBuffer(0, mesh.vertexSlice());
            pass.setIndexBuffer(mesh.indexBuffer, IndexType.INT);
            drawMeshParts(pass, mesh, partMask);
            bones.nativeComputed = nativeComputed;
            if (partMask != 0) VulkanBoneStorage.drawPhase("part_mask_" + partMask);
            VulkanBoneStorage.drawPhase(pipeline == Blaze3DBoneSkinPipeline.OUTLINE_PIPELINE ? "outline"
                    : pipeline == Blaze3DBoneSkinPipeline.TRANSLUCENT_PIPELINE ? "translucent" : "opaque");
            if (nativeComputed) VulkanBoneStorage.nativeDrawSubmitted();
            if (nativeComputed && reportedNativeStorageDraw.compareAndSet(false, true)) {
                com.micaftic.morpher.YesSteveModel.LOGGER.info(
                        "[SM-VULKAN-SSBO] native SIMD -> storage buffer -> GPU skinning draw submitted; GPU completion and screen pixels require validation");
            }
        }

        @Override public void close() { bones.retire(); }
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

            // Precompile both skinning pipelines once, off the first-draw hot path.
            Blaze3DBoneSkinPipeline.precompile(device);

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
            if (!computeBoneMatricesNativeVulkan(model, pose, boneParams, packedLight, boneBuf)
                    && !Blaze3DBoneMatrices.write(model, pose.pose(), pose.normal(), boneParams, stateBuffer, packedLight, boneBuf)) {
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
                    RenderSystem.getModelViewMatrixCopy(),
                    new Vector4f(r, g, b, a),
                    overlayScratch,
                    IDENTITY_MATRIX
            );

            // Translucent models use the entity-translucent blend pipeline; the mesh and
            // bone-buffer path are identical, only the blend state/shader differ.
            RenderPipeline pipeline = translucentTexture
                    ? Blaze3DBoneSkinPipeline.TRANSLUCENT_PIPELINE
                    : Blaze3DBoneSkinPipeline.PIPELINE;

            try (RenderPass pass = encoder.createRenderPass(
                    () -> translucentTexture
                            ? "sparkle_morpher_blaze3d_model_translucent"
                            : "sparkle_morpher_blaze3d_model",
                    target.getColorTextureView(),
                    Optional.empty(),
                    target.getDepthTextureView(),
                    OptionalDouble.empty()
            )) {
                pass.setPipeline(pipeline);
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", dynamicTransforms);
                pass.setUniform(VulkanBoneStorage.NAME, mesh.boneMatrixSlice());
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

    /**
     * 释放指定模型持有的 Blaze3D GPU mesh（租约 revoke），与
     * {@link OpenGlGpuRenderBackend#release(GeoModel)} 对称。WeakHashMap 仅能回收 key，
     * 不会关闭其 GpuBuffer / 直接内存，故模型装配释放时必须显式调用。
     */
    public static void disposeOwner(GeoModel model) {
        if (model == null) {
            return;
        }
        Blaze3DModelMesh mesh = meshCache.remove(model);
        if (mesh != null) {
            mesh.close();
        }
    }

    private static boolean computeBoneMatricesNativeVulkan(
            GeoModel model,
            PoseStack.Pose pose,
            float[] boneParams,
            int packedLight,
            ByteBuffer out
    ) {
        int boneCount = model.bakedBones == null ? 0 : model.bakedBones.size();
        if (boneCount <= 0 || boneParams == null || boneParams.length < boneCount * 12
                || ConfigPolicies.graphics().nativeSimdPolicy()
                == com.micaftic.morpher.core.render.NativeSimdPolicy.OFF
                || !AccelerationCapability.canRenderSimd()
                || !model.ensureVulkanNativeGpuMesh()) {
            return false;
        }

        pose.pose().get(rootPoseScratch);
        pose.normal().get(rootNormalScratch);
        out.clear();
        NativeSimdGpuCompute.markVulkanUnwritten(out, boneCount);
        try {
            GeoModel.nComputeBoneMatricesVulkan(
                    model.vulkanNativeGpuMeshHandle,
                    rootPoseScratch,
                    rootNormalScratch,
                    boneParams,
                    packedLight,
                    out
            );
            if (!NativeSimdGpuCompute.hasCompleteVulkanWrite(out, boneCount)) {
                GpuDebugLog.warn("Blaze3D native Vulkan bone buffer incomplete bones={} handle={}",
                        boneCount, model.vulkanNativeGpuMeshHandle);
                return false;
            }
            out.position(0);
            out.limit(boneCount * NativeSimdGpuCompute.BONE_STRIDE_BYTES);
            return true;
        } catch (Throwable t) {
            GpuDebugLog.warn("Blaze3D native Vulkan bone computation failed: {}", t.toString());
            return false;
        }
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
