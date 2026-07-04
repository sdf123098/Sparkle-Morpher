package com.micaftic.morpher.client.renderer.layer;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.geckolib3.core.processor.IBone;
import com.micaftic.morpher.geckolib3.geo.GeoLayerRenderer;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.micaftic.morpher.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class CustomPlayerElytraLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private static final String ELYTRA_BONE_NAME = "Elytra";

    private static final String ELYTRA_LOCATOR_BONE_NAME = "ElytraLocator";

    private static final String WING_BONE_NAME = "Wing";

    private static final String LEFT_WING_BONE_NAME = "LeftWing";

    private static final String RIGHT_WING_BONE_NAME = "RightWing";

    private static final Identifier WINGS_LOCATION = Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");

    private final net.minecraft.client.model.object.equipment.ElytraModel elytraModel;

    public CustomPlayerElytraLayer(EntityRendererProvider.Context context) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        net.minecraft.client.model.object.equipment.ElytraModel rawModel = new net.minecraft.client.model.object.equipment.ElytraModel(context.bakeLayer(ModelLayers.ELYTRA));
        this.elytraModel = rawModel;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        AnimatedGeoModel animatedGeoModel = entityLivingBaseIn.getCurrentModel();
        ElytraRenderMode renderMode = getElytraRenderMode(entityLivingBaseIn, animatedGeoModel);
        if (renderMode == ElytraRenderMode.NONE) {
            return;
        }
        LivingEntity entity = entityLivingBaseIn.getEntity();
        Identifier cloakTextureLocation = resolveElytraTexture(entity);
        poseStack.pushPose();
        boolean hidden = false;
        if (renderMode == ElytraRenderMode.LOCATOR) {
            hidden = renderLocatorElytra(poseStack, animatedGeoModel);
        } else {
            renderFallbackElytra(poseStack);
        }
        if (!hidden) {
            this.elytraModel.setupAnim(createElytraState(entity, partialTick, ageInTicks));
            this.elytraModel.renderToBuffer(poseStack, bufferSource.getBuffer(RenderTypes.armorCutoutNoCull(cloakTextureLocation)), packedLightIn, OverlayTexture.NO_OVERLAY, -1);
        }
        poseStack.popPose();
    }

    private HumanoidRenderState createElytraState(LivingEntity entity, float partialTick, float ageInTicks) {
        HumanoidRenderState state = new HumanoidRenderState();
        state.ageInTicks = ageInTicks;
        state.isBaby = entity.isBaby();
        state.isCrouching = entity.isCrouching();
        state.isFallFlying = entity.isFallFlying();
        state.elytraRotX = entity.elytraAnimationState.getRotX(partialTick);
        state.elytraRotY = entity.elytraAnimationState.getRotY(partialTick);
        state.elytraRotZ = entity.elytraAnimationState.getRotZ(partialTick);
        return state;
    }

    private boolean renderLocatorElytra(PoseStack poseStack, AnimatedGeoModel model) {
        boolean hidden = RenderUtils.prepMatrixForEquipmentLocator(poseStack, model.elytraBones());
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
        return hidden;
    }

    /**
     * 无 {@code ElytraLocator} 骨骼的导入模型（如 bbmodel）走这里：没有可用的定位骨骼链，
     * 只能按原版人形比例手动近似背部挂点。这里复刻 LOCATOR 路径的最终朝向
     * （{@code prepMatrixForEquipmentLocator} 结束时位于挂点 + 旋转 180°），
     * 手动上移 1.5 格到颈部/上背并略微后移，再旋转 180°。
     * 注意：新版 {@code ElytraModel} 已是正确尺寸，无需旧版的 2x 缩放。
     */
    private void renderFallbackElytra(PoseStack poseStack) {
        poseStack.translate(0.0f, 1.5f, 0.125f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
    }

    public static boolean shouldSuppressVanillaWings(CustomPlayerEntity customPlayer) {
        AnimatedGeoModel animatedGeoModel = customPlayer == null ? null : customPlayer.getCurrentModel();
        if (!hasEquippedElytra(customPlayer)) {
            return false;
        }
        if (hasModelOwnedElytraVisual(animatedGeoModel)) {
            return true;
        }
        return getElytraRenderMode(customPlayer, animatedGeoModel) != ElytraRenderMode.NONE;
    }

    private static ElytraRenderMode getElytraRenderMode(CustomPlayerEntity customPlayer, AnimatedGeoModel animatedGeoModel) {
        if (!hasEquippedElytra(customPlayer)) {
            return ElytraRenderMode.NONE;
        }
        if (animatedGeoModel == null) {
            return ElytraRenderMode.NONE;
        }
        if (hasModelOwnedElytraVisual(animatedGeoModel)) {
            return ElytraRenderMode.NONE;
        }
        if (isCompatibilityElytraBlocked(customPlayer.getModelId(), animatedGeoModel)) {
            return ElytraRenderMode.NONE;
        }
        if (!animatedGeoModel.elytraBones().isEmpty()) {
            return ElytraRenderMode.LOCATOR;
        }
        if (isImportedPlayerModel(customPlayer)) {
            return ElytraRenderMode.FALLBACK;
        }
        if (GeneralConfig.safeGet(GeneralConfig.EXPERIMENTAL_FALLBACK_ELYTRA_WITHOUT_LOCATOR)) {
            return ElytraRenderMode.FALLBACK;
        }
        return ElytraRenderMode.NONE;
    }

    private static boolean isImportedPlayerModel(CustomPlayerEntity customPlayer) {
        return customPlayer != null
                && customPlayer.getModelAssembly() != null
                && customPlayer.getModelAssembly().getAnimationBundle().isImportedPlayerModel();
    }

    private static boolean hasEquippedElytra(CustomPlayerEntity customPlayer) {
        if (customPlayer == null || !customPlayer.isModelReady()) {
            return false;
        }
        LivingEntity entity = customPlayer.getEntity();
        ItemStack stack = CosmeticArmorHelper.getElytraItem(entity);
        return !stack.isEmpty();
    }

    private static Identifier resolveElytraTexture(LivingEntity entity) {
        Identifier cloakTextureLocation = WINGS_LOCATION;
        if (entity instanceof AbstractClientPlayer abstractClientPlayer) {
            if (abstractClientPlayer.getSkin().elytra() != null) {
                cloakTextureLocation = abstractClientPlayer.getSkin().elytra().texturePath();
            } else if (abstractClientPlayer.getSkin().cape() != null) {
                cloakTextureLocation = abstractClientPlayer.getSkin().cape().texturePath();
            }
        }
        return cloakTextureLocation;
    }

    private static boolean isCompatibilityElytraBlocked(String modelId, AnimatedGeoModel animatedGeoModel) {
        if (GeneralConfig.safeGet(GeneralConfig.EXPERIMENTAL_ENABLE_ELYTRA_FOR_DEFAULT_AND_MISC_MODELS)) {
            return false;
        }
        return isDefaultOrMiscModel(modelId) || hasNestedElytraLocator(animatedGeoModel);
    }

    private static boolean isDefaultOrMiscModel(String modelId) {
        return "default".equals(modelId);
    }

    private static boolean hasNestedElytraLocator(AnimatedGeoModel animatedGeoModel) {
        if (animatedGeoModel == null) {
            return false;
        }
        java.util.List<IBone> elytraBones = animatedGeoModel.elytraBones();
        if (elytraBones.size() < 2) {
            return false;
        }
        IBone locatorBone = elytraBones.get(elytraBones.size() - 1);
        if (!ELYTRA_LOCATOR_BONE_NAME.equals(locatorBone.getName())) {
            return false;
        }
        for (int i = 0; i < elytraBones.size() - 1; i++) {
            if (ELYTRA_BONE_NAME.equals(elytraBones.get(i).getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasModelOwnedElytraVisual(AnimatedGeoModel animatedGeoModel) {
        return hasNamedWingBones(animatedGeoModel);
    }

    private static boolean hasNamedWingBones(AnimatedGeoModel animatedGeoModel) {
        if (animatedGeoModel == null) {
            return false;
        }
        for (IBone bone : animatedGeoModel.bones().values()) {
            String name = bone == null ? null : bone.getName();
            if (WING_BONE_NAME.equalsIgnoreCase(name)
                    || LEFT_WING_BONE_NAME.equalsIgnoreCase(name)
                    || RIGHT_WING_BONE_NAME.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private enum ElytraRenderMode {
        NONE,
        LOCATOR,
        FALLBACK
    }
}
