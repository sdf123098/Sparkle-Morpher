package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.core.compat.touhoulittlemaid.MaidCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import com.micaftic.morpher.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public final class MaidEntityRenderer extends GeoReplacedEntityRenderer<LivingEntity, MaidCapability> {
    public MaidEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    public static boolean tryRender(Entity entity, float entityYaw, float partialTick, PoseStack poseStack,
                                    MultiBufferSource bufferSource, int packedLight) {
        if (!(entity instanceof LivingEntity) || !TouhouMaidCompat.isMaidEntity(entity)) {
            return true;
        }
        return MaidCapability.get(entity).map(capability -> {
            capability.syncOfficialYsmState();
            capability.tickModel();
            if (!capability.isModelActive() || !capability.isModelReady()) {
                return true;
            }
            RendererManager.getMaidRenderer().renderEntity(capability,
                    CustomVehicleRenderer.getBodyRotation(entity, entityYaw, partialTick),
                    partialTick, poseStack, bufferSource, packedLight);
            return false;
        }).orElse(true);
    }

    @NotNull
    public Identifier getTextureLocation(LivingEntity entity) {
        return MaidCapability.get(entity).map(MaidCapability::getTextureLocation)
                .orElse(MissingTextureAtlasSprite.getLocation());
    }

    @Override
    @NotNull
    public Identifier getTextureLocation(LivingEntityRenderState renderState) {
        return MissingTextureAtlasSprite.getLocation();
    }

    @Override
    public void setupRotations(LivingEntity entity, PoseStack poseStack, float ageInTicks,
                               float rotationYaw, float partialTicks) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
        if (entity.getVehicle() instanceof net.minecraft.world.entity.player.Player) {
            poseStack.translate(-0.05d, 0.19d, 0.24d);
        }
    }
}
