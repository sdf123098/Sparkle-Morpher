package com.micaftic.morpher.client.renderer.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * GUI 预览渲染桥（路线图 §22.1 拆分，1.2.6 Slice B）。
 *
 * <p>从 {@code ModelPreviewRenderer} 外提 GUI 预览的排队/消费机制：MC 26.x 的 GUI 实体
 * 走 PIP 延迟提交，{@code GuiEntityRendererMixin} 在 vanilla 解析排队
 * {@link EntityRenderState} 之前回调本桥把它替换成自定义预览绘制。</p>
 *
 * <p>本类只负责「按一次性 render state 身份排队一个绘制请求、消费时执行」，
 * 不关心预览的具体绘制内容（由 {@link GuiModelRenderer} 提供）。
 * 排队表按 identity 语义，故用 {@link IdentityHashMap}；跨线程访问由同步包装保证，
 * 与迁移前 {@code ModelPreviewRenderer.GUI_PREVIEWS} 语义一致。</p>
 *
 * <p>同时承载 GUI 预览桥的可用性门控（原
 * {@code ModelPreviewRenderer.isDirectGuiPreviewSupported()}）与
 * {@code InventoryScreen} 预览实体/partialTick 的帧内握手。</p>
 */
public final class PreviewRenderBridge {

    /** 排队中的预览绘制请求（延迟到 PIP 提交阶段执行）。 */
    @FunctionalInterface
    public interface QueuedPreview {
        void render(PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource);
    }

    // MC 26.x renders GUI entities through the PIP submit pipeline. The custom
    // preview bridge is only safe when we can intercept that renderer before
    // vanilla resolves the queued EntityRenderState.
    private static final boolean DIRECT_GUI_PREVIEWS_SUPPORTED = isGuiPreviewBridgeMixinEnabledByConfig();

    private static final Map<EntityRenderState, QueuedPreview> QUEUED =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** 26.2 GUI 预览（InventoryScreen.extractEntityInInventoryFollowsMouse）当前渲染的实体。 */
    private static Entity guiPreviewEntity;
    private static float guiPreviewPartialTick;

    private PreviewRenderBridge() {
    }

    /** GUI 预览桥是否可用（mixin 未被配置禁用）。 */
    public static boolean isDirectGuiPreviewSupported() {
        return DIRECT_GUI_PREVIEWS_SUPPORTED;
    }

    private static boolean isGuiPreviewBridgeMixinEnabledByConfig() {
        String property = System.getProperty("sparkle_morpher.mixin.GuiEntityRendererMixin");
        if (property == null) {
            property = System.getProperty("ysm.mixin.GuiEntityRendererMixin");
        }
        if (property != null && property.equalsIgnoreCase("false")) {
            return false;
        }
        String disabledMixins = System.getProperty("sparkle_morpher.disableMixins");
        if (disabledMixins == null) {
            disabledMixins = System.getProperty("ysm.disableMixins");
        }
        if (disabledMixins == null) {
            disabledMixins = System.getenv("SPARKLE_MORPHER_DISABLE_MIXINS");
        }
        if (disabledMixins == null) {
            disabledMixins = System.getenv("YSM_DISABLE_MIXINS");
        }
        if (disabledMixins == null) {
            return true;
        }
        for (String disabledMixin : disabledMixins.split(",")) {
            String normalized = disabledMixin.trim();
            if (normalized.equalsIgnoreCase("GuiEntityRendererMixin") || normalized.equalsIgnoreCase("com.micaftic.morpher.mixin.client.GuiEntityRendererMixin")) {
                return false;
            }
        }
        return true;
    }

    /** 为某次 render state 排队一个预览绘制请求（消费一次后移除）。 */
    public static void enqueue(EntityRenderState state, QueuedPreview preview) {
        QUEUED.put(state, preview);
    }

    /**
     * 消费并执行排队于该 render state 的预览请求。
     *
     * @return 命中并执行返回 {@code true}；无对应请求返回 {@code false}（调用方回退 vanilla）。
     */
    public static boolean renderQueued(EntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector, MultiBufferSource.BufferSource bufferSource) {
        QueuedPreview request = QUEUED.remove(renderState);
        if (request == null) {
            return false;
        }
        request.render(poseStack, collector, bufferSource);
        return true;
    }

    public static void setGuiPreviewEntity(@Nullable Entity entity, float partialTick) {
        guiPreviewEntity = entity;
        guiPreviewPartialTick = partialTick;
    }

    @Nullable
    public static Entity getAndClearGuiPreviewEntity() {
        Entity entity = guiPreviewEntity;
        guiPreviewEntity = null;
        return entity;
    }

    public static float getGuiPreviewPartialTick() {
        return guiPreviewPartialTick;
    }
}
