package com.micaftic.morpher.client.renderer;

import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.client.entity.GeckoProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.NotNull;

public class ProjectileRenderer extends AbstractProjectileRenderer<Projectile, GeckoProjectileEntity> {
    @Override
    public EntityRenderState createRenderState() { return new EntityRenderState(); }

    public ProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public void render(Projectile projectile, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (Minecraft.getInstance().player == null || projectile.isInvisibleTo(Minecraft.getInstance().player)) {
            return;
        }
        ProjectileCapability.get(projectile).ifPresent(cap -> {
            cap.tickModel();
            render(cap, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        });
    }

    @NotNull
    public Identifier getTextureLocation(Projectile projectile) {
        return ProjectileCapability.get(projectile).map((cap) -> cap.getTextureLocation()).orElse(MissingTextureAtlasSprite.getLocation());
    }
}
