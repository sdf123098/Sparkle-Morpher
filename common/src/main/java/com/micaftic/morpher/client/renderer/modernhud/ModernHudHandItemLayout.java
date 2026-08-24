package com.micaftic.morpher.client.renderer.modernhud;

import com.micaftic.morpher.client.model.HandLocatorProfile;
import com.micaftic.morpher.geckolib3.core.processor.IBone;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Vector3f;

import java.util.List;

/**
 * Calculates the modern HUD screen position of a hand attachment.
 *
 * <p>The model and its animated bones are the same objects used by the body
 * snapshot. This class only reads the already evaluated locator hierarchy;
 * it never evaluates an animation controller a second time.</p>
 */
public final class ModernHudHandItemLayout {
    private ModernHudHandItemLayout() {
    }

    public static Vector3f locate(AnimatedGeoModel model, HandLocatorProfile profile,
                                  HumanoidArm arm, float originX, float originY,
                                  float scale, float yawOffset) {
        List<? extends IBone> locator = arm == HumanoidArm.LEFT
                ? model.leftHandBones()
                : model.rightHandBones();

        PoseStack poseStack = new PoseStack();
        poseStack.translate(originX, originY, 0.0f);
        poseStack.scale(-scale, scale, -scale);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f + 0.1f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f + yawOffset));

        if (locator != null && !locator.isEmpty()) {
            boolean hidden = profile != null && profile.usesEquipmentLocatorTransform()
                    ? RenderUtils.prepMatrixForEquipmentLocator(poseStack, locator)
                    : RenderUtils.prepMatrixForLocator(poseStack, locator);
            if (!hidden) {
                return poseStack.last().pose().transformPosition(0.0f, 0.0f, 0.0f, new Vector3f());
            }
        }

        // Models without an authored hand locator still keep the item in the
        // modern HUD instead of silently dropping it or switching HUD modes.
        float side = arm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        return new Vector3f(originX + side * scale * 0.35f, originY + scale * 0.95f, 0.0f);
    }
}

