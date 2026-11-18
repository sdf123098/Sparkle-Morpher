package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.client.render.ModelBoneView;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;

public final class SpmRenderMath {

    private SpmRenderMath() {
    }

    public static void translateMatrixToBone(PoseStack poseStack, ModelBoneView bone) {
        poseStack.translate((-bone.getPositionX()) / 16f, bone.getPositionY() / 16f, bone.getPositionZ() / 16f);
    }

    public static void rotateMatrixAroundBone(PoseStack poseStack, ModelBoneView bone) {
        if (bone.getRotationZ() != 0.0F || bone.getRotationY() != 0.0F || bone.getRotationX() != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotateZYX(bone.getRotationZ(), bone.getRotationY(), bone.getRotationX()));
        }
    }

    public static boolean scaleMatrixForBone(PoseStack poseStack, ModelBoneView bone) {
        float scaleX = bone.getScaleX();
        float scaleY = bone.getScaleY();
        float scaleZ = bone.getScaleZ();
        poseStack.scale(scaleX, scaleY, scaleZ);
        return isHiddenScale(bone);
    }

    private static boolean isHiddenScale(ModelBoneView bone) {
        return bone.getScaleX() == 0.0f && bone.getScaleY() == 0.0f && bone.getScaleZ() == 0.0f;
    }

    public static void translateToPivotPoint(PoseStack poseStack, ModelBoneView bone) {
        poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
    }

    public static void translateAwayFromPivotPoint(PoseStack poseStack, ModelBoneView bone) {
        poseStack.translate(-bone.getPivotX() / 16f, -bone.getPivotY() / 16f, -bone.getPivotZ() / 16f);
    }

    public static void translateAndRotateMatrixForBone(PoseStack poseStack, ModelBoneView bone) {
        translateToPivotPoint(poseStack, bone);
        rotateMatrixAroundBone(poseStack, bone);
    }

    public static boolean prepMatrixForBone(PoseStack poseStack, ModelBoneView bone) {
        translateMatrixToBone(poseStack, bone);
        translateToPivotPoint(poseStack, bone);
        rotateMatrixAroundBone(poseStack, bone);
        boolean hidden = scaleMatrixForBone(poseStack, bone);
        translateAwayFromPivotPoint(poseStack, bone);
        return hidden;
    }

    private static void prepMatrixForEquipmentBone(PoseStack poseStack, ModelBoneView bone) {
        translateMatrixToBone(poseStack, bone);
        translateToPivotPoint(poseStack, bone);
        rotateMatrixAroundBone(poseStack, bone);
        translateAwayFromPivotPoint(poseStack, bone);
    }

    public static boolean prepMatrixForLocator(PoseStack poseStack, List<? extends ModelBoneView> locatorHierarchy) {
        return prepMatrixForLocator(poseStack, locatorHierarchy, false);
    }

    public static boolean prepMatrixForLocatorIgnoringHiddenLastScale(PoseStack poseStack, List<? extends ModelBoneView> locatorHierarchy) {
        return prepMatrixForLocator(poseStack, locatorHierarchy, true);
    }

    private static boolean prepMatrixForLocator(PoseStack poseStack, List<? extends ModelBoneView> locatorHierarchy, boolean ignoreHiddenLastScale) {
        boolean scaleCheck = false;
        for (int i = 0; i < locatorHierarchy.size() - 1; i++) {
            if (prepMatrixForBone(poseStack, locatorHierarchy.get(i))) {
                scaleCheck = true;
            }
        }
        ModelBoneView lastBone = locatorHierarchy.get(locatorHierarchy.size() - 1);
        translateMatrixToBone(poseStack, lastBone);
        translateToPivotPoint(poseStack, lastBone);
        rotateMatrixAroundBone(poseStack, lastBone);
        if (!ignoreHiddenLastScale || !isHiddenScale(lastBone)) {
            if (scaleMatrixForBone(poseStack, lastBone)) {
                scaleCheck = true;
            }
        }
        return scaleCheck;
    }

    public static boolean prepMatrixForEquipmentLocator(PoseStack poseStack, List<? extends ModelBoneView> locatorHierarchy) {
        boolean hidden = false;
        for (int i = 0; i < locatorHierarchy.size() - 1; i++) {
            ModelBoneView bone = locatorHierarchy.get(i);
            if (isHiddenScale(bone)) {
                hidden = true;
            }
            prepMatrixForEquipmentBone(poseStack, bone);
        }
        ModelBoneView lastBone = locatorHierarchy.get(locatorHierarchy.size() - 1);
        if (isHiddenScale(lastBone)) {
            hidden = true;
        }
        translateMatrixToBone(poseStack, lastBone);
        translateToPivotPoint(poseStack, lastBone);
        rotateMatrixAroundBone(poseStack, lastBone);
        return hidden;
    }

    public static Matrix4f invertAndMultiplyMatrices(Matrix4f baseMatrix, Matrix4f inputMatrix) {
        inputMatrix = new Matrix4f(inputMatrix);
        inputMatrix.invert();
        inputMatrix.mul(baseMatrix);
        return inputMatrix;
    }
}
