package com.micaftic.morpher.client.renderer.preview;

import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.core.compat.acceleratedrendering.AcceleratedRenderingCompat;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.LinkedHashMap;
import java.util.SequencedMap;

/** Local, transparent snapshots: a reused card submits a quad, never the model's mesh/animation. */
final class LegacyCardPreviewCache {
    private static final int MAX_PHYSICAL_SIZE = 256;
    private final PreviewTexturePool<Key, Image> images = new PreviewTexturePool<>(
            32, 24L * 1024 * 1024, image -> image.target.destroyBuffers());

    private record Key(Object source, int width, int height, int physicalWidth, int physicalHeight,
                       float originX, float originY, float scale, boolean fixed, boolean hide, float yaw) {}

    private static final class Image {
        final RenderTarget target;
        long revision;
        boolean ready;
        Image(int width, int height) {
            target = new TextureTarget(width, height, true, false);
            target.setClearColor(0, 0, 0, 0);
            target.setFilterMode(GL11.GL_LINEAR);
        }
    }

    boolean draw(GuiGraphics graphics, int left, int top, int right, int bottom,
                 float originX, float originY, float scale, float tick,
                 LivingAnimatable<?> entity, GeoReplacedEntityRenderer renderer,
                 boolean fixed, boolean hide, float yaw, Object identity, long revision, boolean refreshAllowed) {
        RenderSystem.assertOnRenderThread();
        if (identity == null || right <= left || bottom <= top || scale <= 0) return false;
        Minecraft minecraft = Minecraft.getInstance();
        int width = right - left;
        int height = bottom - top;
        double density = minecraft.getWindow().getGuiScale();
        int physicalWidth = Math.max(1, Math.min(MAX_PHYSICAL_SIZE, (int) Math.ceil(width * density)));
        int physicalHeight = Math.max(1, Math.min(MAX_PHYSICAL_SIZE, (int) Math.ceil(height * density)));
        Key key = new Key(identity, width, height, physicalWidth, physicalHeight,
                originX - left, originY - top, scale, fixed, hide, yaw);
        Image image = images.get(key);
        boolean needsRefresh = image == null || !image.ready || image.revision != revision;
        if (needsRefresh && refreshAllowed && entity != null && renderer != null) {
            graphics.flush();
            TargetState previous = new TargetState();
            boolean isolated = AcceleratedRenderingCompat.enterVanillaPipeline();
            boolean projectionSaved = false;
            try {
                if (image == null) {
                    image = new Image(physicalWidth, physicalHeight);
                    // RGBA8 + depth attachment. Both dimensions are capped before GPU allocation.
                    images.put(key, image, (long) physicalWidth * physicalHeight * 8);
                }
                image.ready = false;
                // The outer GUI scissor uses main-target coordinates and must not clip this local FBO.
                RenderSystem.disableScissor();
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
                image.target.clear(false);
                image.target.bindWrite(true);
                RenderSystem.backupProjectionMatrix();
                projectionSaved = true;
                RenderSystem.setProjectionMatrix(
                        new Matrix4f().setOrtho(0, width, height, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
                renderLocal(graphics, key, tick, entity, renderer);
                image.revision = revision;
                image.ready = true;
            } finally {
                AcceleratedRenderingCompat.exitVanillaPipeline(isolated);
                if (projectionSaved) RenderSystem.restoreProjectionMatrix();
                previous.restore();
            }
        }
        // A deferred refresh keeps the previous image, including when the caller skips entity setup.
        if (image == null || !image.ready) return false;
        composite(graphics, image.target, left, top, right, bottom);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderLocal(GuiGraphics graphics, Key key, float tick,
                                    LivingAnimatable<?> entity, GeoReplacedEntityRenderer renderer) {
        // Glint and item renderers can hold two consumers simultaneously: never share their builders.
        SequencedMap<RenderType, ByteBufferBuilder> builders = new LinkedHashMap<>() {
            @Override public ByteBufferBuilder get(Object type) {
                return computeIfAbsent((RenderType) type, ignored -> new ByteBufferBuilder(256));
            }
        };
        try (ByteBufferBuilder shared = new ByteBufferBuilder(256)) {
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediateWithBuffers(builders, shared);
            try {
                ModelPreviewRenderer.renderLivingEntityPreview(key.originX, key.originY, key.scale, tick,
                        entity, renderer, key.fixed, key.hide, key.yaw, buffers);
                buffers.endBatch();
                graphics.flush();
            } finally {
                builders.values().forEach(ByteBufferBuilder::close);
            }
        }
    }

    private static void composite(GuiGraphics graphics, RenderTarget target, int left, int top, int right, int bottom) {
        graphics.flush();
        graphics.enableScissor(left, top, right, bottom);
        var shader = RenderSystem.getShader();
        float[] color = RenderSystem.getShaderColor().clone();
        int texture = RenderSystem.getShaderTexture(0);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        try {
            RenderSystem.disableBlend();
            RenderSystem.enableBlend();
            // The transparent target already contains alpha-multiplied RGB from the model pass.
            // Multiplying by alpha a second time would darken translucent hair and author artwork.
            RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, target.getColorTextureId());
            RenderSystem.setShader(GameRenderer::getPositionTexShader);
            Matrix4f pose = graphics.pose().last().pose();
            BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            quad.addVertex(pose, left, bottom, 0).setUv(0, 0);
            quad.addVertex(pose, right, bottom, 0).setUv(1, 0);
            quad.addVertex(pose, right, top, 0).setUv(1, 1);
            quad.addVertex(pose, left, top, 0).setUv(0, 1);
            BufferUploader.drawWithShader(quad.buildOrThrow());
        } finally {
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
            RenderSystem.defaultBlendFunc();
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            graphics.disableScissor();
        }
    }

    void clear() {
        RenderSystem.assertOnRenderThread();
        TargetState previous = new TargetState();
        try { images.clear(); }
        finally { previous.restore(); }
    }

    /** FBO operations mutate GL viewport/binding and clear masks; restore the actual previous target. */
    private static final class TargetState {
        final int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        final int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        final int[] viewport = new int[4];
        final int[] scissor = new int[4];
        final boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        final boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        final boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        final boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        final boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        final byte[] colorWrite = new byte[4];
        final int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        final int destRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        final int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        final int destAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        final float[] shaderColor = RenderSystem.getShaderColor().clone();
        final net.minecraft.client.renderer.ShaderInstance shader = RenderSystem.getShader();
        final int texture0 = RenderSystem.getShaderTexture(0);
        final int texture1 = RenderSystem.getShaderTexture(1);
        final int texture2 = RenderSystem.getShaderTexture(2);
        TargetState() {
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            try (var mask = org.lwjgl.system.MemoryStack.stackPush()) {
                var buffer = mask.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, buffer);
                buffer.get(colorWrite);
            }
        }
        void restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.viewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            if (scissorEnabled) RenderSystem.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            else RenderSystem.disableScissor();
            RenderSystem.depthMask(depthWrite);
            RenderSystem.colorMask(colorWrite[0] != 0, colorWrite[1] != 0, colorWrite[2] != 0, colorWrite[3] != 0);
            if (depthEnabled) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cullEnabled) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha);
            if (blendEnabled) RenderSystem.enableBlend();
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShaderTexture(0, texture0);
            RenderSystem.setShaderTexture(1, texture1);
            RenderSystem.setShaderTexture(2, texture2);
            RenderSystem.setShader(() -> shader);
        }
    }
}
