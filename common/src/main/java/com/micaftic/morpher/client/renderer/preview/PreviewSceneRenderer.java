package com.micaftic.morpher.client.renderer.preview;

import com.micaftic.morpher.client.animation.AnimationTracker;
import com.micaftic.morpher.geckolib3.core.AnimatableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.concurrent.ExecutionException;

/**
 * 预览场景家具渲染（路线图 §22.1 拆分，1.2.6 Slice A）。
 *
 * <p>从 {@code ModelPreviewRenderer} 外提：GUI 预览里的床、地面方块与植物装饰，
 * 以及载具动画占位。这些内容只依赖 {@link PoseStack} / {@link VertexConsumer}，
 * 不读写实体状态，属纯绘制，因此独立成类以缩小原大类职责。</p>
 *
 * <p>行为等价搬迁：方法体、调色板常量与调用顺序均与迁移前一致。</p>
 */
public final class PreviewSceneRenderer {

    private static final float GRASS_R = 0.26f;
    private static final float GRASS_G = 0.55f;
    private static final float GRASS_B = 0.22f;
    private static final float DIRT_R = 0.32f;
    private static final float DIRT_G = 0.22f;
    private static final float DIRT_B = 0.13f;
    private static final float BED_RED_R = 0.72f;
    private static final float BED_RED_G = 0.08f;
    private static final float BED_RED_B = 0.07f;
    private static final float BED_WHITE_R = 0.92f;
    private static final float BED_WHITE_G = 0.88f;
    private static final float BED_WHITE_B = 0.78f;

    private PreviewSceneRenderer() {
    }

    public static void renderBedPreview(float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        renderSimpleBed(poseStack, bufferSource);
    }

    public static void renderBedPreview(PoseStack poseStack, float yaw, MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        renderSimpleBed(poseStack, bufferSource);
        poseStack.popPose();
    }

    public static void renderGroundPreview(float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);
        renderSimpleGround(poseStack, bufferSource);
    }

    public static void renderGroundPreview(PoseStack poseStack, float yaw, MultiBufferSource.BufferSource bufferSource) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);
        renderSimpleGround(poseStack, bufferSource);
        poseStack.popPose();
    }

    private static void renderSimpleGround(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                float shade = ((x + z) & 1) == 0 ? 1.0f : 0.88f;
                addTopQuad(buffer, pose, x, z, x + 1.0f, z + 1.0f, GRASS_R * shade, GRASS_G * shade, GRASS_B * shade);
                addSideQuad(buffer, pose, x, z, x + 1.0f, z + 1.0f, DIRT_R, DIRT_G, DIRT_B);
            }
        }
        addPlantCross(buffer, pose, 0.55f, 1.45f, 1.0f, 0.28f, 0.64f, 0.18f);
        addPlantCross(buffer, pose, 1.85f, 2.2f, 1.0f, 0.75f, 0.08f, 0.08f);
    }

    private static void renderSimpleBed(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderTypes.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        addCuboid(buffer, pose, 0.0f, 0.0f, 0.0f, 1.0f, 0.28f, 1.0f, BED_RED_R, BED_RED_G, BED_RED_B);
        addCuboid(buffer, pose, 0.0f, 0.29f, 0.0f, 1.0f, 0.34f, 0.35f, BED_WHITE_R, BED_WHITE_G, BED_WHITE_B);
    }

    private static void addTopQuad(VertexConsumer buffer, PoseStack.Pose pose, float x1, float z1, float x2, float z2, float r, float g, float b) {
        addQuad(buffer, pose, x1, 1.0f, z1, x2, 1.0f, z1, x2, 1.0f, z2, x1, 1.0f, z2, r, g, b, 0.0f, 1.0f, 0.0f);
    }

    private static void addSideQuad(VertexConsumer buffer, PoseStack.Pose pose, float x1, float z1, float x2, float z2, float r, float g, float b) {
        addQuad(buffer, pose, x1, 0.85f, z1, x2, 0.85f, z1, x2, 1.0f, z1, x1, 1.0f, z1, r, g, b, 0.0f, 0.0f, -1.0f);
        addQuad(buffer, pose, x2, 0.85f, z1, x2, 0.85f, z2, x2, 1.0f, z2, x2, 1.0f, z1, r, g, b, 1.0f, 0.0f, 0.0f);
    }

    private static void addPlantCross(VertexConsumer buffer, PoseStack.Pose pose, float x, float z, float y, float r, float g, float b) {
        addQuad(buffer, pose, x - 0.12f, y, z, x + 0.12f, y, z, x + 0.12f, y + 0.45f, z, x - 0.12f, y + 0.45f, z, r, g, b, 0.0f, 0.0f, 1.0f);
        addQuad(buffer, pose, x, y, z - 0.12f, x, y, z + 0.12f, x, y + 0.45f, z + 0.12f, x, y + 0.45f, z - 0.12f, r, g, b, 1.0f, 0.0f, 0.0f);
    }

    private static void addCuboid(VertexConsumer buffer, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b) {
        addQuad(buffer, pose, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, 0.0f, 1.0f, 0.0f);
        addQuad(buffer, pose, x1, y1, z2, x2, y1, z2, x2, y1, z1, x1, y1, z1, r * 0.55f, g * 0.55f, b * 0.55f, 0.0f, -1.0f, 0.0f);
        addQuad(buffer, pose, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r * 0.72f, g * 0.72f, b * 0.72f, -1.0f, 0.0f, 0.0f);
        addQuad(buffer, pose, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, r * 0.72f, g * 0.72f, b * 0.72f, 1.0f, 0.0f, 0.0f);
        addQuad(buffer, pose, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r * 0.65f, g * 0.65f, b * 0.65f, 0.0f, 0.0f, -1.0f);
        addQuad(buffer, pose, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r * 0.65f, g * 0.65f, b * 0.65f, 0.0f, 0.0f, 1.0f);
    }

    private static void addQuad(VertexConsumer buffer, PoseStack.Pose pose,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                float x3, float y3, float z3,
                                float x4, float y4, float z4,
                                float r, float g, float b,
                                float nx, float ny, float nz) {
        buffer.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose.pose(), x3, y3, z3).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
        buffer.addVertex(pose.pose(), x4, y4, z4).setColor(r, g, b, 1.0f).setNormal(pose, nx, ny, nz);
    }

    public static void renderVehicleForAnimation(float yaw, AnimatableEntity animatableEntity, AnimationTracker animationTracker, float partialTick, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource) throws ExecutionException {
        if (animationTracker == null) {
            return;
        }
        Entity entity = animatableEntity.getEntity();

        if (animationTracker.isCurrentAnimation("ride")) {
            // MC 26.x: EntityType.create needs EntitySpawnReason, BOAT removed
            // renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.HORSE), () -> EntityType.HORSE.create(entity.level())), partialTick);
        } else if (animationTracker.isCurrentAnimation("ride_pig")) {
            // renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.PIG), () -> EntityType.PIG.create(entity.level())), partialTick);
        } else if (animationTracker.isCurrentAnimation("boat")) {
            // renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.BOAT), () -> EntityType.BOAT.create(entity.level())), partialTick);
        }
    }

    private static void renderVehicleEntity(float yaw, Entity riderEntity, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource, Entity vehicleEntity, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        Vec3 passengerAttachment = vehicleEntity.getPassengerRidingPosition(riderEntity).subtract(vehicleEntity.position());
        // MC 26.x: EntityRenderDispatcher.render() signature changed
        // entityRenderDispatcher.render(vehicleEntity, 0.0d, passengerAttachment.y(), 0.0d, 0.0f, partialTick, poseStack, bufferSource, 15728880);
        poseStack.popPose();
    }
}
