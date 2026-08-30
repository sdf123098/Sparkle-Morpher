package com.micaftic.morpher.core.compat.slashblade;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mods.flammpfeil.slashblade.capability.slashblade.BladeStateAccess;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.CarryType;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.BladeMotionManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import mods.flammpfeil.slashblade.client.renderer.util.MSAutoCloser;
import mods.flammpfeil.slashblade.event.client.UserPoseOverrider;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.TimeValueHelper;
import mods.flammpfeil.slashblade.util.VectorHelper;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import jp.nyatla.nymmd.MmdException;
import jp.nyatla.nymmd.MmdMotionPlayerGL2;
import jp.nyatla.nymmd.MmdPmdModelMc;
import jp.nyatla.nymmd.MmdVmdMotionMc;

/**
 * The only place allowed to reference SlashBlade classes. Callers must check
 * {@link SlashBladeModState#LOADED} first so this class is never classloaded
 * while the mod is absent.
 *
 * <p>Rendering mirrors SlashBlade's own {@code LayerMainBlade}: in third person
 * the blade is drawn by that player layer, whose transform is MMD-motion driven
 * relative to the entity origin, so the same math stays valid when the player
 * body is replaced by a morph model.</p>
 */
public final class SlashBladeBridge {

    private static final float MOTION_Y_OFFSET = 1.5f;
    private static final double MOTION_SCALE = 1.5 / 12.0;
    private static final double MODEL_SCALE_BASE = 0.0078125F; // 0.5^7

    private static final String BLADE_LUMINOUS = "blade_luminous";
    private static final String BLADE_DAMAGED_LUMINOUS = "blade_damaged_luminous";
    private static final String SHEATH_LUMINOUS = "sheath_luminous";

    private static final Quaternionf CARRY_ROTATION_PSO2 =
        new Quaternionf().rotateZYX(-0.122173F, 0, 0);
    private static final Quaternionf CARRY_ROTATION_KATANA =
        new Quaternionf().rotateZYX(3.1415927F, 1.570796f, 0.261799F);
    private static final Quaternionf CARRY_ROTATION_DEFAULT =
        new Quaternionf().rotateZYX(0F, 1.570796f, 0.261799F);
    private static final Quaternionf CARRY_ROTATION_NINJA =
        new Quaternionf().rotateZYX(-2.094395F, 0f, 3.1415927F);
    private static final Quaternionf CARRY_ROTATION_RNINJA =
        new Quaternionf().rotateZYX(-1.047198F, 0, 0);

    @Nullable
    private static MmdPmdModelMc bladeHolder;
    @Nullable
    private static MmdMotionPlayerGL2 motionPlayer;

    private final float[] boneMatrixBuf = new float[16];
    private final Matrix3f normalMatrixTmp = new Matrix3f();

    private SlashBladeBridge() {
    }

    public static boolean isSlashBladeItem(ItemStack itemStack) {
        return itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof ItemSlashBlade;
    }

    /**
     * SlashBlade combo state key ("slashblade:combo_a1" style) for the main hand
     * blade, or "" while no combat combo is active (idle/standby keep the
     * model's regular hold/swing animations in charge).
     */
    public static String getComboAnimationName(LivingEntity livingEntity) {
        if (livingEntity == null) {
            return "";
        }
        Optional<ISlashBladeState> state = BladeStateAccess.of(livingEntity.getMainHandItem());
        if (state.isEmpty()) {
            return "";
        }
        ResourceLocation combo = state.get().resolvCurrentComboState(livingEntity);
        if (combo == null
            || ComboStateRegistry.NONE.getId().equals(combo)
            || ComboStateRegistry.STANDBY.getId().equals(combo)) {
            return "";
        }
        return combo.toString();
    }

    @Nullable
    public static PlayState handleSlashBladeAnim(LivingEntity livingEntity, AnimationEvent<? extends LivingAnimatable<?>> event, String stateAnimation, ILoopType loopType) {
        String comboName = getComboAnimationName(livingEntity);
        if (comboName.isEmpty() || comboName.equals(stateAnimation)) {
            return null;
        }
        LivingAnimatable<?> animatable = event.getAnimatable();
        if (animatable == null || animatable.getAnimation(comboName) == null) {
            return null;
        }
        int formatVersion = animatable.getModelAssembly() != null && animatable.getModelAssembly().getModelData() != null
            ? animatable.getModelAssembly().getModelData().getFormatVersion()
            : 0;
        return IAnimationPredicate.playAnimationWithValid(event, comboName, ILoopType.EDefaultLoopTypes.PLAY_ONCE, formatVersion);
    }

    public static void registerBindings(CtrlBinding ctrlBinding) {
        ctrlBinding.livingEntityVar("slashblade_animation", ctx -> getComboAnimationName(ctx.entity()));
    }

    public static void renderMainHandBlade(LivingEntity livingEntity, ItemStack itemStack, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Optional<ISlashBladeState> stateOptional = BladeStateAccess.of(itemStack);
        if (stateOptional.isEmpty()) {
            return;
        }
        ISlashBladeState state = stateOptional.get();
        MmdMotionPlayerGL2 player = getMotionPlayer();
        if (player == null) {
            return;
        }

        Map.Entry<Integer, ResourceLocation> comboStateTicks = state.peekCurrentComboStateTicks(livingEntity);
        ComboState combo = Objects.requireNonNullElse(
            ComboStateRegistry.REGISTRY.get(comboStateTicks.getValue()),
            ComboStateRegistry.NONE.get());
        double time = TimeValueHelper.getMSecFromTicks(comboStateTicks.getKey() + partialTick);
        if (combo == ComboStateRegistry.NONE.get()) {
            ResourceLocation comboRoot = state.getComboRoot();
            combo = comboRoot != null && ComboStateRegistry.REGISTRY.get(comboRoot) != null
                ? ComboStateRegistry.REGISTRY.get(comboRoot)
                : ComboStateRegistry.STANDBY.get();
        }

        MmdVmdMotionMc motion = combo != null
            ? BladeMotionManager.getInstance().getMotion(combo.getMotionLoc())
            : null;
        if (motion == null && !player.hasVmdMotion()) {
            return;
        }

        double maxSeconds = 0;
        if (motion != null) {
            try {
                player.setVmd(motion);
                maxSeconds = TimeValueHelper.getMSecFromFrames(motion.getMaxFrame());
            } catch (MmdException e) {
                logMotionWarning(e);
                return;
            }
        }

        double start = combo != null ? TimeValueHelper.getMSecFromFrames(combo.getStartFrame()) : 0;
        double end = combo != null ? TimeValueHelper.getMSecFromFrames(combo.getEndFrame()) : 0;
        double span = Math.abs(end - start);
        span = Math.min(maxSeconds, span);
        if (combo != null && combo.getLoop()) {
            time = time % span;
        }
        time = Math.min(span, time);
        time = start + time;

        try {
            player.updateMotionBonesAndSkinning((float) time);
        } catch (MmdException e) {
            logMotionWarning(e);
            return;
        }

        ResourceLocation textureLocation = state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
        WavefrontObject obj = BladeModelManager.getInstance()
            .getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));

        SlashBladeBridge instance = INSTANCE;
        try (MSAutoCloser ignored = MSAutoCloser.pushMatrix(poseStack)) {
            float comboRot = UserPoseOverrider.getInterpolatedComboRotation(state, livingEntity, partialTick);
            if (comboRot != 0f) {
                poseStack.mulPose(Axis.YP.rotationDegrees(comboRot));
            }
            poseStack.translate(0, MOTION_Y_OFFSET, 0);
            poseStack.scale((float) MOTION_SCALE, (float) MOTION_SCALE, (float) MOTION_SCALE);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180));

            try (MSAutoCloser ignored1 = MSAutoCloser.pushMatrix(poseStack)) {
                instance.applyHardpoint(poseStack, player, "hardpointA");
                poseStack.scale(modelScale(), modelScale(), modelScale());
                String part = state.isBroken() ? "blade_damaged" : "blade";
                BladeRenderState.renderOverrided(itemStack, obj, part, textureLocation, poseStack, bufferSource, packedLight);
                BladeRenderState.renderOverridedLuminous(itemStack, obj,
                    state.isBroken() ? BLADE_DAMAGED_LUMINOUS : BLADE_LUMINOUS, textureLocation, poseStack, bufferSource, packedLight);
            }

            try (MSAutoCloser ignored1 = MSAutoCloser.pushMatrix(poseStack)) {
                instance.applyHardpoint(poseStack, player, "hardpointB");
                poseStack.scale(modelScale(), modelScale(), modelScale());
                BladeRenderState.renderOverrided(itemStack, obj, "sheath", textureLocation, poseStack, bufferSource, packedLight);
                BladeRenderState.renderOverridedLuminous(itemStack, obj, SHEATH_LUMINOUS, textureLocation, poseStack, bufferSource, packedLight);
                if (state.isCharged(livingEntity)) {
                    BladeRenderState.renderChargeEffect(itemStack, (float) livingEntity.tickCount + partialTick, obj, "effect",
                        ResourceLocation.parse("textures/entity/creeper/creeper_armor.png"), poseStack, bufferSource, packedLight);
                }
            }
        }
    }

    public static void renderWaistBlade(ItemStack itemStack, LivingEntity livingEntity, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Optional<ISlashBladeState> stateOptional = BladeStateAccess.of(itemStack);
        if (stateOptional.isEmpty()) {
            return;
        }
        ISlashBladeState state = stateOptional.get();
        ResourceLocation textureLocation = state.getTexture().orElse(DefaultResources.resourceDefaultTexture);
        WavefrontObject obj = BladeModelManager.getInstance()
            .getModel(state.getModel().orElse(DefaultResources.resourceDefaultModel));

        try (MSAutoCloser ignored = MSAutoCloser.pushMatrix(poseStack)) {
            poseStack.translate(0, 1.5f, 0);
            CarryType carryType = state.getCarryType();
            Minecraft minecraft = Minecraft.getInstance();
            switch (carryType) {
                case PSO2 -> {
                    poseStack.translate(1F, -1.125f, 0.20f);
                    poseStack.mulPose(CARRY_ROTATION_PSO2);
                    if (isFirstPersonSelf(livingEntity, minecraft)) {
                        return;
                    }
                }
                case KATANA -> {
                    poseStack.translate(0.25F, -0.875f, -0.55f);
                    poseStack.mulPose(CARRY_ROTATION_KATANA);
                }
                case DEFAULT -> {
                    poseStack.translate(0.25F, -0.875f, -0.55f);
                    poseStack.mulPose(CARRY_ROTATION_DEFAULT);
                }
                case NINJA -> {
                    poseStack.translate(-0.5F, -2f, 0.20f);
                    poseStack.mulPose(CARRY_ROTATION_NINJA);
                    if (isFirstPersonSelf(livingEntity, minecraft)) {
                        return;
                    }
                }
                case RNINJA -> {
                    poseStack.translate(0.5F, -2f, 0.20f);
                    poseStack.mulPose(CARRY_ROTATION_RNINJA);
                    if (isFirstPersonSelf(livingEntity, minecraft)) {
                        return;
                    }
                }
                default -> {
                    return;
                }
            }

            poseStack.scale((float) MOTION_SCALE, (float) MOTION_SCALE, (float) MOTION_SCALE);
            poseStack.scale(modelScale(), modelScale(), modelScale());

            try (MSAutoCloser ignored1 = MSAutoCloser.pushMatrix(poseStack)) {
                String part = state.isBroken() ? "blade_damaged" : "blade";
                BladeRenderState.renderOverrided(itemStack, obj, part, textureLocation, poseStack, bufferSource, packedLight);
                BladeRenderState.renderOverridedLuminous(itemStack, obj,
                    state.isBroken() ? BLADE_DAMAGED_LUMINOUS : BLADE_LUMINOUS, textureLocation, poseStack, bufferSource, packedLight);
                BladeRenderState.renderOverrided(itemStack, obj, "sheath", textureLocation, poseStack, bufferSource, packedLight);
                BladeRenderState.renderOverridedLuminous(itemStack, obj, SHEATH_LUMINOUS, textureLocation, poseStack, bufferSource, packedLight);
            }
        }
    }

    private static final SlashBladeBridge INSTANCE = new SlashBladeBridge();

    private void applyHardpoint(PoseStack poseStack, MmdMotionPlayerGL2 player, String hardpoint) {
        int idx = player.getBoneIndexByName(hardpoint);
        if (idx < 0) {
            return;
        }
        player._skinning_mat[idx].getValue(this.boneMatrixBuf);
        Matrix4f mat = VectorHelper.matrix4fFromArray(this.boneMatrixBuf);
        poseStack.scale(-1, 1, 1);
        PoseStack.Pose entry = poseStack.last();
        entry.pose().mul(mat);
        entry.normal().mul(this.normalMatrixTmp.set(mat).invert().transpose());
        poseStack.scale(-1, 1, 1);
    }

    private static float modelScale() {
        return (float) (MODEL_SCALE_BASE * (1.0f / MOTION_SCALE));
    }

    private static boolean isFirstPersonSelf(LivingEntity livingEntity, Minecraft minecraft) {
        return minecraft.options.getCameraType() == CameraType.FIRST_PERSON
            && livingEntity.equals(minecraft.player);
    }

    @Nullable
    private static MmdMotionPlayerGL2 getMotionPlayer() {
        if (motionPlayer == null) {
            motionPlayer = new MmdMotionPlayerGL2();
            try {
                if (bladeHolder == null) {
                    bladeHolder = new MmdPmdModelMc(ResourceLocation.fromNamespaceAndPath("slashblade", "model/bladeholder.pmd"));
                }
                motionPlayer.setPmd(bladeHolder);
            } catch (IOException | MmdException e) {
                logMotionWarning(e);
            }
        }
        return motionPlayer;
    }

    private static void logMotionWarning(Exception e) {
        com.micaftic.morpher.YesSteveModel.LOGGER.warn("[SM-SLASHBLADE] motion error", e);
    }
}
