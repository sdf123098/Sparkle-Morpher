package com.micaftic.morpher.client.compat.punchy;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.diagnostics.FirstPersonRenderDiagnostics;
import com.micaftic.morpher.client.event.ReplacePlayerHandRenderEvent;
import com.micaftic.morpher.core.compat.punchy.PunchyBridgeVersions;
import com.micaftic.morpher.core.compat.punchy.PunchyCompat;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * [FP-COMPAT] Punchy! arm ownership bridge.
 *
 * <p>Punchy! cancels vanilla {@code ItemInHandRenderer.submitArmWithItem}, so SPM's
 * {@code PlayerRenderer.renderRightHand/renderLeftHand} hook is unreachable while Punchy! draws
 * first-person arms. This bridge runs at the head of Punchy!'s own
 * {@code PunchyArmRenderer.renderArm}, where Punchy! has already computed the arm pose and applied
 * its camera/bob transforms to the pose stack. It replicates the one transform Punchy! applies to
 * the arm model part before drawing it ({@code ModelPart.translateAndRotate}) and hands the same
 * pose stack to the regular SPM first-person arm path.
 *
 * <p>Punchy!'s model part and sleeve are then skipped for that arm, its armor-arm pass is
 * suppressed, and its item rendering is left untouched. Any failure, an inactive SPM model or an
 * unsupported Punchy! build returns {@code false} so Punchy! draws its own arm as if the bridge
 * did not exist.
 */
public final class PunchyArmBridge {

    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<>();
    private static final ThreadLocal<HumanoidArm> REPLACED_ARM = new ThreadLocal<>();

    private PunchyArmBridge() {
    }

    private record Frame(Player player, HumanoidArm arm, MultiBufferSource bufferSource) {
    }

    public static boolean enabled() {
        return ConfigPolicies.punchyArmBridge()
                && PunchyCompat.isLoaded()
                && PunchyBridgeVersions.isSupported(PunchyCompat.version());
    }

    public static void beginArm(Player player, HumanoidArm arm, MultiBufferSource bufferSource) {
        FRAME.set(new Frame(player, arm, bufferSource));
        REPLACED_ARM.remove();
    }

    /**
     * Draws SPM's arm in the pose space Punchy! prepared for its own arm mesh.
     *
     * @return {@code true} when SPM geometry was submitted, in which case the caller must not draw
     * Punchy!'s model part
     */
    public static boolean tryReplaceArm(ModelPart part, PoseStack poseStack, int packedLight) {
        Frame frame = FRAME.get();
        if (frame == null) {
            return false;
        }
        boolean active = enabled();
        FirstPersonRenderDiagnostics.reportBridge(frame.arm(), active, active ? "active" : disabledReason());
        if (!active || !part.visible) {
            return false;
        }
        boolean rendered = false;
        try {
            poseStack.pushPose();
            try {
                part.translateAndRotate(poseStack);
                rendered = ReplacePlayerHandRenderEvent.onRenderArm(
                        frame.player(), frame.arm(), poseStack, frame.bufferSource(), packedLight);
            } finally {
                poseStack.popPose();
            }
        } catch (Throwable t) {
            YesSteveModel.LOGGER.warn("[FP-COMPAT] Punchy arm bridge failed for {}, keeping Punchy's arm",
                    frame.arm(), t);
            rendered = false;
        }
        if (rendered) {
            REPLACED_ARM.set(frame.arm());
        }
        return rendered;
    }

    /** Consumed once by the armor-arm pass that immediately follows a bridged render. */
    public static boolean consumeArmorSuppression(HumanoidArm arm) {
        if (REPLACED_ARM.get() != arm) {
            return false;
        }
        REPLACED_ARM.remove();
        return true;
    }

    private static String disabledReason() {
        if (!PunchyCompat.isLoaded()) {
            return "punchy_absent";
        }
        String version = PunchyCompat.version();
        if (!PunchyBridgeVersions.isSupported(version)) {
            return "unsupported_punchy_" + (version == null ? "unknown" : version);
        }
        if (!ConfigPolicies.punchyArmBridge()) {
            return "config_disabled";
        }
        return "unknown";
    }
}
