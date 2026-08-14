package com.micaftic.morpher.client.renderer.modernhud;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.event.ClientTickEvent;
import com.micaftic.morpher.client.render.RenderContext;
import com.micaftic.morpher.client.render.RenderPass;
import com.micaftic.morpher.client.renderer.ModelPreviewRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 现代 HUD 姿态快照存储（阶段 1，计划书 §4.1/阶段 1）。
 *
 * <p>世界帧动画评估完成点 {@link #publishFromWorld(PlayerCapability, float, ResourceLocation)}
 * 发布；现代 HUD 帧 {@link #consume()} 消费。发布/消费都不触发动画评估。
 *
 * <p>{@code worldEvalCount} 记录世界评估发布次数、{@code hudConsumeCount} 记录 HUD 消费次数，
 * 用于阶段 1 验收：现代 HUD 消费 snapshot 时动画评估计数不增加（两者之差 = 世界评估本身，
 * 由世界渲染驱动，与现代 HUD 无关）。
 *
 * <p>阶段 2 增补：本地玩家不可见/第一人称时世界不发布快照，由
 * {@link #buildFallbackSnapshot(LocalPlayer, float)} 自评估一次（等价经典 HUD 的一次
 * 评估成本；第一人称时世界未评估，这是首次评估而非二次评估）。
 */
public final class ModernHudPoseStore {

    private static final AtomicBoolean warnedPublish = new AtomicBoolean(false);

    private static volatile PlayerPoseSnapshot latest;
    private static volatile long generation;
    private static volatile long worldEvalCount;
    private static volatile long hudConsumeCount;

    private ModernHudPoseStore() {
    }

    private static void diagPublish(String reason) {
        if (warnedPublish.compareAndSet(false, true)) {
            YesSteveModel.LOGGER.warn("[MODERN-HUD] snapshot publish skipped: {}", reason);
        }
    }

    /**
     * 无世界快照时的自评估快照：本地玩家不可见/第一人称时，现代 HUD 用经典 HUD 同款
     * 上下文（OLD_HUD + beginOldHudRenderState 固定展示相机）执行一次动画评估并构建快照。
     * 返回 null 表示无法构建（模型未激活/未就绪），调用方回退经典 HUD。
     */
    @Nullable
    public static PlayerPoseSnapshot buildFallbackSnapshot(LocalPlayer player, float partialTick) {
        return PlayerCapability.get(player)
                .filter(cap -> cap.isModelActive() && cap.isModelReady())
                .map(cap -> buildFallback(cap, player, partialTick))
                .orElse(null);
    }

    private static PlayerPoseSnapshot buildFallback(PlayerCapability capability, LocalPlayer player, float partialTick) {
        AnimatedGeoModel model = capability.getCurrentModel();
        if (model == null) {
            return null;
        }
        RenderPass previous = RenderContext.enter(RenderPass.OLD_HUD);
        try {
            capability.beginOldHudRenderState(ModelPreviewRenderer.FRONT_FACING_YAW, partialTick);
            try {
                capability.processAnimation(partialTick);
            } finally {
                capability.endRenderState();
            }
        } finally {
            RenderContext.restore(previous);
        }
        if (model.getMatrixData() == null) {
            return null;
        }
        int tick = ClientTickEvent.getTickCount();
        float bodyRot = 0.0f;
        float netHeadYaw = 0.0f;
        float headPitch = 0.0f;
        if (capability.hasRenderState()) {
            bodyRot = capability.getRenderStateBodyRot();
            netHeadYaw = capability.getRenderStateNetHeadYaw();
            headPitch = capability.getRenderStateHeadPitch();
        } else {
            bodyRot = player.getYRot();
            netHeadYaw = player.getYHeadRot() - bodyRot;
            headPitch = player.getXRot();
        }
        ResourceLocation texture = capability.getTextureLocation();
        if (texture == null) {
            texture = MissingTextureAtlasSprite.getLocation();
        }
        return new PlayerPoseSnapshot(
                ((long) tick << 32) | Float.floatToRawIntBits(partialTick),
                tick,
                partialTick,
                capability.getModelId(),
                model,
                model.getMatrixData(),
                model.getAbsPivotData(),
                texture,
                bodyRot,
                netHeadYaw,
                headPitch,
                0,
                player.hurtTime > 0 || player.deathTime > 0,
                player.getBbWidth(),
                player.getBbHeight()
        );
    }

    /**
     * 世界帧动画评估完成点发布。仅接受：本地玩家、WORLD 渲染上下文（排除 GUI 预览与
     * 经典 HUD 的二次评估）、模型已激活且就绪。条件不满足时不发布（现代 HUD 无快照可
     * 消费，由调用方回退经典 HUD）。
     */
    public static void publishFromWorld(PlayerCapability capability, float partialTick, ResourceLocation texture) {
        if (!(capability.entity instanceof LocalPlayer localPlayer)) {
            diagPublish("not local player");
            return;
        }
        if (RenderContext.isGuiPreview() || RenderContext.isOldHud()) {
            diagPublish("non-world render context");
            return;
        }
        if (!capability.isModelActive() || !capability.isModelReady()) {
            diagPublish("model not active/ready");
            return;
        }
        AnimatedGeoModel model = capability.getCurrentModel();
        if (model == null || model.getMatrixData() == null) {
            diagPublish("current model or matrix data null");
            return;
        }
        if (texture == null) {
            // 事件未解析出纹理位置：用模型默认纹理，避免消费侧 getTexture(null) 崩溃
            texture = capability.getTextureLocation();
            if (texture == null) {
                diagPublish("texture null and no model default");
                return;
            }
        }

        int tick = ClientTickEvent.getTickCount();
        float bodyRot = 0.0f;
        float netHeadYaw = 0.0f;
        float headPitch = 0.0f;
        if (capability.hasRenderState()) {
            bodyRot = capability.getRenderStateBodyRot();
            netHeadYaw = capability.getRenderStateNetHeadYaw();
            headPitch = capability.getRenderStateHeadPitch();
        } else {
            // render state 未就绪的兜底（与 AnimatableEntity.processAnimationImpl 的实体直读一致）
            bodyRot = localPlayer.getYRot();
            netHeadYaw = localPlayer.getYHeadRot() - bodyRot;
            headPitch = localPlayer.getXRot();
        }

        PlayerPoseSnapshot snapshot = new PlayerPoseSnapshot(
                ((long) tick << 32) | Float.floatToRawIntBits(partialTick),
                tick,
                partialTick,
                capability.getModelId(),
                model,
                model.getMatrixData(),
                model.getAbsPivotData(),
                texture,
                bodyRot,
                netHeadYaw,
                headPitch,
                0,
                localPlayer.hurtTime > 0 || localPlayer.deathTime > 0,
                localPlayer.getBbWidth(),
                localPlayer.getBbHeight()
        );
        publish(snapshot);
    }

    public static void publish(PlayerPoseSnapshot snapshot) {
        latest = snapshot;
        generation++;
        worldEvalCount++;
    }

    /** 现代 HUD 消费最新快照（不触发任何动画评估）。无快照返回 null。 */
    @Nullable
    public static PlayerPoseSnapshot consume() {
        PlayerPoseSnapshot snapshot = latest;
        if (snapshot != null) {
            hudConsumeCount++;
        }
        return snapshot;
    }

    /** 模型切换 / 断线 / 资源重载时使快照失效，避免消费陈旧 generation。 */
    public static void invalidate() {
        latest = null;
    }

    public static long generation() {
        return generation;
    }

    /** 世界评估发布次数（阶段 1 验收用）。 */
    public static long worldEvalCount() {
        return worldEvalCount;
    }

    /** HUD 消费次数（阶段 1 验收用）。 */
    public static long hudConsumeCount() {
        return hudConsumeCount;
    }
}
