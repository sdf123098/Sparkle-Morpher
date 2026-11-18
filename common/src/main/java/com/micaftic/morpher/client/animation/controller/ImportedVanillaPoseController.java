package com.micaftic.morpher.client.animation.controller;

import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.render.BoneIds;
import com.micaftic.morpher.client.render.ModelBoneView;
import com.micaftic.morpher.client.render.ModelScenePose;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

public class ImportedVanillaPoseController extends HostControllerSlot<CustomPlayerEntity> {

    private static final String[] HEAD_NAMES = {"head", "allhead", "vanillahead"};
    private static final String[] BODY_NAMES = {"body", "torso", "chest", "upperbody", "vanillabody"};
    private static final String[] LEFT_ARM_NAMES = {"leftarm", "leftupperarm", "leftshoulder", "vanillaleftarm"};
    private static final String[] RIGHT_ARM_NAMES = {"rightarm", "rightupperarm", "rightshoulder", "vanillarightarm"};
    private static final String[] LEFT_FOREARM_NAMES = {"leftforearm", "leftlowerarm", "leftelbow"};
    private static final String[] RIGHT_FOREARM_NAMES = {"rightforearm", "rightlowerarm", "rightelbow"};
    private static final String[] LEFT_HAND_NAMES = {"lefthand", "leftwrist", "leftpalm"};
    private static final String[] RIGHT_HAND_NAMES = {"righthand", "rightwrist", "rightpalm"};
    private static final String[] LEFT_LEG_NAMES = {"leftleg", "leftupperleg", "leftthigh", "vanillaleftleg"};
    private static final String[] RIGHT_LEG_NAMES = {"rightleg", "rightupperleg", "rightthigh", "vanillarightleg"};
    private static final String[] LEFT_LOWER_LEG_NAMES = {"leftlowerleg", "leftshin", "leftcalf"};
    private static final String[] RIGHT_LOWER_LEG_NAMES = {"rightlowerleg", "rightshin", "rightcalf"};
    private static final String[] LEFT_FOOT_NAMES = {"leftfoot", "leftboot"};
    private static final String[] RIGHT_FOOT_NAMES = {"rightfoot", "rightboot"};
    private static final float GENERIC_SWING_X = 1.25f;
    private static final float GENERIC_SWING_Y = 0.35f;
    private static final float GENERIC_SWING_Z = 0.25f;
    private static final float GENERIC_SWING_RECOVERY_X = 0.35f;

    // ==================== 矛（原版 *_spear）姿态参数 ====================
    // 仅 bbmodel / figura（VANILLA_HUMANOID）会走本控制器；YSM 与 YSM zip 不经过这里。
    // 单位：弧度。手臂约定 X=0 垂下、+π/2 前平举、+π 竖直朝上；右臂 +Z 外张、-Y 内摆。
    // 为通用性只用 LeftArm/RightArm 两根主臂骨（无小臂/上臂细分骨的模型也适用）。
    // 以下为起始估算值，需进游戏按观感校准。
    // 空闲手持：持矛手自然持握（与普通武器一致、不上抬）
    private static final float SPEAR_HOLD_ARM_X = -0.25f;
    private static final float SPEAR_HOLD_ARM_Z = 0.08f;
    // 冲锋/使用：手臂维持自然持握、不上抬，仅上身略前倾（矛尖平→竖→垂由物品挂点变换负责）
    private static final float SPEAR_USE_BODY_X = 0.12f;
    // 戳刺：在自然持握基础上做小幅前刺（不整臂上抬）+ 上身前倾
    private static final float SPEAR_THRUST_ARM_X = 0.70f;
    private static final float SPEAR_THRUST_BODY_X = 0.15f;

    private final PartTransformProvider head = new PartTransformProvider(HEAD_NAMES);
    private final PartTransformProvider body = new PartTransformProvider(BODY_NAMES);
    private final PartTransformProvider leftArm = new PartTransformProvider(LEFT_ARM_NAMES);
    private final PartTransformProvider rightArm = new PartTransformProvider(RIGHT_ARM_NAMES);
    private final PartTransformProvider leftForeArm = new PartTransformProvider(LEFT_FOREARM_NAMES);
    private final PartTransformProvider rightForeArm = new PartTransformProvider(RIGHT_FOREARM_NAMES);
    private final PartTransformProvider leftHand = new PartTransformProvider(LEFT_HAND_NAMES);
    private final PartTransformProvider rightHand = new PartTransformProvider(RIGHT_HAND_NAMES);
    private final PartTransformProvider leftLeg = new PartTransformProvider(LEFT_LEG_NAMES);
    private final PartTransformProvider rightLeg = new PartTransformProvider(RIGHT_LEG_NAMES);
    private final PartTransformProvider leftLowerLeg = new PartTransformProvider(LEFT_LOWER_LEG_NAMES);
    private final PartTransformProvider rightLowerLeg = new PartTransformProvider(RIGHT_LOWER_LEG_NAMES);
    private final PartTransformProvider leftFoot = new PartTransformProvider(LEFT_FOOT_NAMES);
    private final PartTransformProvider rightFoot = new PartTransformProvider(RIGHT_FOOT_NAMES);
    private final boolean fallbackOnly;
    private String currentPose = "idle";

    public ImportedVanillaPoseController(CustomPlayerEntity entity, String name) {
        this(entity, name, false);
    }

    public ImportedVanillaPoseController(CustomPlayerEntity entity, String name, boolean fallbackOnly) {
        super(entity, name, 0.0f, (event, animatable) -> HostPlayState.Decision.STOP);
        this.fallbackOnly = fallbackOnly;
    }

    @Override
    public String getCurrentAnimation() {
        return "[vanilla_pose] " + this.currentPose;
    }

    private void bindBones(List<ModelBoneView> bones) {
        this.head.bind(bones);
        this.body.bind(bones);
        this.leftArm.bind(bones);
        this.rightArm.bind(bones);
        this.leftForeArm.bind(bones);
        this.rightForeArm.bind(bones);
        this.leftHand.bind(bones);
        this.rightHand.bind(bones);
        this.leftLeg.bind(bones);
        this.rightLeg.bind(bones);
        this.leftLowerLeg.bind(bones);
        this.rightLowerLeg.bind(bones);
        this.leftFoot.bind(bones);
        this.rightFoot.bind(bones);
    }

    @Override
    public void applyProceduralPose(HostActionEvent<CustomPlayerEntity> event, ModelScenePose scenePose) {
        Player player = event.getAnimatable().getEntity();
        if (player == null) {
            return;
        }
        bindBones(scenePose.getOrderedBones());
        PoseValues pose = calculatePose(player, event);
        this.currentPose = pose.name;
        if (!this.fallbackOnly) {
            this.head.apply(pose.headX, pose.headY, pose.headZ, scenePose, false);
        }
        this.body.apply(pose.bodyX, pose.bodyY, pose.bodyZ, scenePose, this.fallbackOnly);
        this.leftArm.apply(pose.leftArmX, pose.leftArmY, pose.leftArmZ, scenePose, this.fallbackOnly);
        this.rightArm.apply(pose.rightArmX, pose.rightArmY, pose.rightArmZ, scenePose, this.fallbackOnly);
        this.leftForeArm.apply(pose.leftForeArmX, pose.leftForeArmY, pose.leftForeArmZ, scenePose, this.fallbackOnly);
        this.rightForeArm.apply(pose.rightForeArmX, pose.rightForeArmY, pose.rightForeArmZ, scenePose, this.fallbackOnly);
        this.leftHand.apply(pose.leftHandX, pose.leftHandY, pose.leftHandZ, scenePose, this.fallbackOnly);
        this.rightHand.apply(pose.rightHandX, pose.rightHandY, pose.rightHandZ, scenePose, this.fallbackOnly);
        this.leftLeg.apply(pose.leftLegX, pose.leftLegY, pose.leftLegZ, scenePose, this.fallbackOnly);
        this.rightLeg.apply(pose.rightLegX, pose.rightLegY, pose.rightLegZ, scenePose, this.fallbackOnly);
        this.leftLowerLeg.apply(pose.leftLowerLegX, pose.leftLowerLegY, pose.leftLowerLegZ, scenePose, this.fallbackOnly);
        this.rightLowerLeg.apply(pose.rightLowerLegX, pose.rightLowerLegY, pose.rightLowerLegZ, scenePose, this.fallbackOnly);
        this.leftFoot.apply(pose.leftFootX, pose.leftFootY, pose.leftFootZ, scenePose, this.fallbackOnly);
        this.rightFoot.apply(pose.rightFootX, pose.rightFootY, pose.rightFootZ, scenePose, this.fallbackOnly);
    }

    @Override
    public void reset() {
        super.reset();
        this.currentPose = "idle";
    }

    private static PoseValues calculatePose(Player player, HostActionEvent<CustomPlayerEntity> event) {
        VanillaHumanoidPoseSampler.PoseSample vanillaPose = VanillaHumanoidPoseSampler.sample(player, event);
        if (vanillaPose != null && !shouldUseFallbackHandPose(player)) {
            return fromVanillaPose(vanillaPose);
        }
        PoseValues pose = new PoseValues();
        pose.name = "idle";
        pose.headX = (float) Math.toRadians(event.getModelData().headPitch);
        pose.headY = (float) Math.toRadians(event.getModelData().netHeadYaw);

        float limbSwing = event.getLimbSwing();
        float limbSwingAmount = Mth.clamp(event.getLimbSwingAmount(), 0.0f, 1.0f);
        boolean moving = limbSwingAmount > 0.05f;

        if (player.isSwimming()) {
            pose.name = "swim";
            pose.bodyX = (float) Math.toRadians(-10.0f);
            pose.leftArmX = Mth.cos(limbSwing * 0.333f + (float) Math.PI) * 1.1f * limbSwingAmount;
            pose.rightArmX = Mth.cos(limbSwing * 0.333f) * 1.1f * limbSwingAmount;
            pose.leftLegX = Mth.cos(limbSwing * 0.333f) * 0.9f * limbSwingAmount;
            pose.rightLegX = Mth.cos(limbSwing * 0.333f + (float) Math.PI) * 0.9f * limbSwingAmount;
            applyHeldItemPose(player, event, pose);
            return pose;
        }
        if (event.getModelData().isSitting || player.isPassenger()) {
            pose.name = "sit";
            pose.leftArmX = -0.62831855f;
            pose.rightArmX = -0.62831855f;
            pose.leftLegX = -1.4137167f;
            pose.leftLegY = 0.31415927f;
            pose.leftLegZ = 0.07853982f;
            pose.rightLegX = -1.4137167f;
            pose.rightLegY = -0.31415927f;
            pose.rightLegZ = -0.07853982f;
            applyHeldItemPose(player, event, pose);
            return pose;
        }
        if (player.isFallFlying() || player.getPose() == Pose.FALL_FLYING) {
            pose.name = "elytra_fly";
            pose.leftArmX = 0.15f;
            pose.rightArmX = 0.15f;
            pose.leftArmZ = -0.25f;
            pose.rightArmZ = 0.25f;
            applyVanillaLegPose(pose, vanillaPose, limbSwing, limbSwingAmount);
            applyHeldItemPose(player, event, pose);
            return pose;
        }

        pose.leftArmX = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 2.0f * limbSwingAmount * 0.5f;
        pose.rightArmX = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbSwingAmount * 0.5f;
        pose.leftLegX = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        pose.rightLegX = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;

        if (player.getPose() == Pose.CROUCHING) {
            pose.name = moving ? "sneak" : "sneaking";
            pose.bodyX = 0.5f;
            pose.leftArmX += 0.4f;
            pose.rightArmX += 0.4f;
        } else if (player.getAbilities().flying) {
            pose.name = "fly";
            pose.leftArmX = 0.0f;
            pose.rightArmX = 0.0f;
            pose.leftLegX = 0.0f;
            pose.rightLegX = 0.0f;
        } else if (!player.onGround() && !player.isInWater()) {
            pose.name = "jump";
        } else if (player.isSprinting() && moving) {
            pose.name = "run";
            pose.leftArmX *= 1.25f;
            pose.rightArmX *= 1.25f;
            pose.leftLegX *= 1.15f;
            pose.rightLegX *= 1.15f;
        } else if (moving) {
            pose.name = "walk";
        }

        float idleArmSway = Mth.sin(event.getCurrentTick() * 0.067f) * 0.05f;
        pose.leftArmZ += -0.05f + idleArmSway;
        pose.rightArmZ += 0.05f - idleArmSway;
        pose.leftArmX += Mth.sin(event.getCurrentTick() * 0.067f) * 0.05f;
        pose.rightArmX -= Mth.sin(event.getCurrentTick() * 0.067f) * 0.05f;
        applyHeldItemPose(player, event, pose);
        return pose;
    }

    private static boolean shouldUseFallbackHandPose(Player player) {
        if (player.isUsingItem()
                || InputStateKey.isUsingItem(player, InteractionHand.MAIN_HAND)
                || InputStateKey.isUsingItem(player, InteractionHand.OFF_HAND)
                || InputStateKey.isSwinging(player, InteractionHand.MAIN_HAND)
                || InputStateKey.isSwinging(player, InteractionHand.OFF_HAND)) {
            return true;
        }
        return !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
    }

    private static PoseValues fromVanillaPose(VanillaHumanoidPoseSampler.PoseSample vanillaPose) {
        PoseValues pose = new PoseValues();
        pose.name = "vanilla";
        pose.headX = vanillaPose.head.xRot;
        pose.headY = vanillaPose.head.yRot;
        pose.headZ = vanillaPose.head.zRot;
        pose.bodyX = vanillaPose.body.xRot;
        pose.bodyY = vanillaPose.body.yRot;
        pose.bodyZ = vanillaPose.body.zRot;
        pose.leftArmX = vanillaPose.leftArm.xRot;
        pose.leftArmY = vanillaPose.leftArm.yRot;
        pose.leftArmZ = vanillaPose.leftArm.zRot;
        pose.rightArmX = vanillaPose.rightArm.xRot;
        pose.rightArmY = vanillaPose.rightArm.yRot;
        pose.rightArmZ = vanillaPose.rightArm.zRot;
        pose.leftLegX = vanillaPose.leftLeg.xRot;
        pose.leftLegY = vanillaPose.leftLeg.yRot;
        pose.leftLegZ = vanillaPose.leftLeg.zRot;
        pose.rightLegX = vanillaPose.rightLeg.xRot;
        pose.rightLegY = vanillaPose.rightLeg.yRot;
        pose.rightLegZ = vanillaPose.rightLeg.zRot;
        return pose;
    }

    /**
     * 鞘翅飞行时腿部遵循原版机制：优先直接采用原版 {@code PlayerModel.setupAnim} 计算出的腿部旋转
     * （与空手路径完全同源，基于行走速度 {@code limbSwingAmount} 摆动，站定时归零）；
     * 仅当原版采样不可用（反射失败）时，退回到原版同款的行走摆动公式。
     * 这样无论手上是否持物（如烟花），腿部表现都与空手时一致，而不是被写死为固定角度。
     */
    private static void applyVanillaLegPose(PoseValues pose, VanillaHumanoidPoseSampler.PoseSample vanillaPose,
                                            float limbSwing, float limbSwingAmount) {
        if (vanillaPose != null) {
            pose.leftLegX = vanillaPose.leftLeg.xRot;
            pose.leftLegY = vanillaPose.leftLeg.yRot;
            pose.leftLegZ = vanillaPose.leftLeg.zRot;
            pose.rightLegX = vanillaPose.rightLeg.xRot;
            pose.rightLegY = vanillaPose.rightLeg.yRot;
            pose.rightLegZ = vanillaPose.rightLeg.zRot;
            return;
        }
        pose.leftLegX = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        pose.rightLegX = Mth.cos(limbSwing * 0.6662f + (float) Math.PI) * 1.4f * limbSwingAmount;
    }

    private static void applyHeldItemPose(Player player, HostActionEvent<CustomPlayerEntity> event, PoseValues pose) {
        applyPassiveHoldPose(player, pose, InteractionHand.MAIN_HAND);
        applyPassiveHoldPose(player, pose, InteractionHand.OFF_HAND);
        InteractionHand useHand = getActiveUseHand(player);
        if (useHand != null) {
            applyUsePose(player, event, pose, useHand);
            return;
        }
        applySwingPose(player, event, pose);
    }

    @Nullable
    private static InteractionHand getActiveUseHand(Player player) {
        if (player.isUsingItem()) {
            return player.getUsedItemHand();
        }
        if (InputStateKey.isUsingItem(player, InteractionHand.OFF_HAND)) {
            return InteractionHand.OFF_HAND;
        }
        if (shouldIgnoreSyntheticMainHandUse(player)) {
            return null;
        }
        if (InputStateKey.isUsingItem(player, InteractionHand.MAIN_HAND)) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static boolean shouldIgnoreSyntheticMainHandUse(Player player) {
        if (!InputStateKey.isLocalPlayerEntity(player) || !InputStateKey.isUsingItem(player, InteractionHand.MAIN_HAND)) {
            return false;
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.isEmpty()) {
            return false;
        }
        return isToolOrWeapon(InnerClassify.getImportedItemType(player.getMainHandItem()));
    }

    private static void applyPassiveHoldPose(Player player, PoseValues pose, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        String itemType = InnerClassify.getImportedItemType(stack);
        HumanoidArm arm = armForHand(player, hand);
        if ("bow".equals(itemType)) {
            setArm(pose, arm, -0.2f, 0.0f, 0.0f);
            return;
        }
        if ("crossbow".equals(itemType) && stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            setArm(pose, arm, -0.85f, arm == HumanoidArm.RIGHT ? -0.25f : 0.25f, arm == HumanoidArm.RIGHT ? 0.1f : -0.1f);
            return;
        }
        if ("shield".equals(itemType)) {
            setArm(pose, arm, -0.65f, arm == HumanoidArm.RIGHT ? -0.25f : 0.25f, 0.0f);
            return;
        }
        if ("spear".equals(itemType)) {
            applySpearHoldPose(pose, arm);
            return;
        }
        if (isToolOrWeapon(itemType)) {
            addArm(pose, arm, -0.25f, 0.0f, arm == HumanoidArm.RIGHT ? 0.08f : -0.08f);
        }
    }

    private static void applyUsePose(Player player, HostActionEvent<CustomPlayerEntity> event, PoseValues pose, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            return;
        }
        String itemType = InnerClassify.getImportedItemType(stack);
        HumanoidArm arm = armForHand(player, hand);
        HumanoidArm other = arm.getOpposite();
        if ("bow".equals(itemType)) {
            pose.name = "use_bow";
            setArm(pose, arm, 1.5708f + pose.headX, pose.headY + (arm == HumanoidArm.RIGHT ? -0.1f : 0.1f), arm == HumanoidArm.RIGHT ? 0.1f : -0.1f);
            setArm(pose, other, 1.4708f + pose.headX, pose.headY + (other == HumanoidArm.RIGHT ? -0.4f : 0.4f), other == HumanoidArm.RIGHT ? 0.25f : -0.25f);
            return;
        }
        if ("crossbow".equals(itemType)) {
            pose.name = "use_crossbow";
            float pull = Mth.clamp(InputStateKey.getTicksUsingItem(player, event.getPartialTick()) / 20.0f, 0.0f, 1.0f);
            setArm(pose, arm, 1.2f + pose.headX, pose.headY + (arm == HumanoidArm.RIGHT ? -0.35f : 0.35f), arm == HumanoidArm.RIGHT ? 0.15f : -0.15f);
            setArm(pose, other, 0.95f + pose.headX, pose.headY + (other == HumanoidArm.RIGHT ? -0.65f : 0.65f) * pull, other == HumanoidArm.RIGHT ? 0.25f : -0.25f);
            return;
        }
        if ("shield".equals(itemType)) {
            pose.name = "block";
            setArm(pose, arm, 1.15f, arm == HumanoidArm.RIGHT ? -0.35f : 0.35f, 0.0f);
            return;
        }
        if ("spear".equals(itemType)) {
            pose.name = "use_spear";
            applySpearUsePose(pose, arm);
            return;
        }
        if ("trident".equals(itemType)) {
            pose.name = "use_trident";
            setArm(pose, arm, 3.1416f + pose.headX, pose.headY + (arm == HumanoidArm.RIGHT ? -0.1f : 0.1f), 0.0f);
            addForeArm(pose, arm, 0.1f, 0.0f, 0.0f);
            return;
        }
        if ("lance".equals(itemType)) {
            pose.name = "use_lance";
            setArm(pose, arm, 2.35f, arm == HumanoidArm.RIGHT ? -0.35f : 0.35f, arm == HumanoidArm.RIGHT ? 0.05f : -0.05f);
            addForeArm(pose, arm, 0.15f, 0.0f, 0.0f);
            return;
        }
        if (isToolOrWeapon(itemType)) {
            pose.name = "use_item";
            applyGenericUsePose(player, event, pose, arm);
            return;
        }
        pose.name = "use_item";
        applyGenericUsePose(player, event, pose, arm);
    }

    private static void applyGenericUsePose(Player player, HostActionEvent<CustomPlayerEntity> event, PoseValues pose, HumanoidArm arm) {
        float ticks = InputStateKey.getTicksUsingItem(player, event.getPartialTick());
        float progress = Mth.clamp(ticks / 10.0f, 0.0f, 1.0f);
        float reach = player.isUsingItem() ? 1.0f : Mth.sin(progress * (float) Math.PI);
        applyGenericSwingPose(pose, arm, reach, Mth.sin(progress * (float) Math.PI));
    }

    private static void applySwingPose(Player player, HostActionEvent<CustomPlayerEntity> event, PoseValues pose) {
        InteractionHand hand = getActiveSwingingHand(player);
        if (hand == null) {
            return;
        }
        float attack = getSwingProgress(player, hand, event.getPartialTick());
        if (attack <= 0.0f) {
            return;
        }
        HumanoidArm arm = armForHand(player, hand);
        float swing = Mth.sin(Mth.sqrt(attack) * (float) Math.PI);
        float recovery = Mth.sin(attack * (float) Math.PI);
        if ("spear".equals(InnerClassify.getImportedItemType(player.getItemInHand(hand)))) {
            applySpearThrustPose(pose, arm, swing);
            return;
        }
        pose.name = "swing";
        applyGenericSwingPose(pose, arm, swing, recovery);
    }

    private static void applyGenericSwingPose(PoseValues pose, HumanoidArm arm, float swing, float recovery) {
        addArm(pose, arm,
                GENERIC_SWING_X * swing + GENERIC_SWING_RECOVERY_X * recovery,
                arm == HumanoidArm.RIGHT ? GENERIC_SWING_Y * swing : -GENERIC_SWING_Y * swing,
                arm == HumanoidArm.RIGHT ? GENERIC_SWING_Z * swing : -GENERIC_SWING_Z * swing);
    }

    // 空闲手持：持矛手自然持握、手臂不上抬（保留行走摆动），与普通武器一致。只用主臂骨，通用。
    private static void applySpearHoldPose(PoseValues pose, HumanoidArm arm) {
        addArm(pose, arm, SPEAR_HOLD_ARM_X, 0.0f, arm == HumanoidArm.RIGHT ? SPEAR_HOLD_ARM_Z : -SPEAR_HOLD_ARM_Z);
    }

    // 冲锋/使用：手臂维持自然持握、不上抬；矛尖平→竖→垂由 CustomPlayerItemInHandLayer 的矛使用变换负责。
    private static void applySpearUsePose(PoseValues pose, HumanoidArm arm) {
        setArm(pose, arm, SPEAR_HOLD_ARM_X, 0.0f, arm == HumanoidArm.RIGHT ? SPEAR_HOLD_ARM_Z : -SPEAR_HOLD_ARM_Z);
        pose.bodyX += SPEAR_USE_BODY_X;
    }

    // 戳刺：在自然持握基础上做小幅前刺，不整臂上抬。
    private static void applySpearThrustPose(PoseValues pose, HumanoidArm arm, float swing) {
        float ext = Mth.clamp(swing, 0.0f, 1.0f);
        pose.name = "thrust_spear";
        setArm(pose, arm, SPEAR_HOLD_ARM_X + SPEAR_THRUST_ARM_X * ext, 0.0f,
                arm == HumanoidArm.RIGHT ? SPEAR_HOLD_ARM_Z : -SPEAR_HOLD_ARM_Z);
        pose.bodyX += SPEAR_THRUST_BODY_X * ext;
    }

    @Nullable
    private static InteractionHand getActiveSwingingHand(Player player) {
        InteractionHand hand = InputStateKey.getSwingingHand(player);
        if (InputStateKey.isSwinging(player, hand)) {
            return hand;
        }
        if (InputStateKey.isSwinging(player, InteractionHand.OFF_HAND)) {
            return InteractionHand.OFF_HAND;
        }
        if (InputStateKey.isSwinging(player, InteractionHand.MAIN_HAND)) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    private static float getSwingProgress(Player player, InteractionHand hand, float partialTick) {
        if (InputStateKey.isLocalPlayerEntity(player) && hand == InteractionHand.MAIN_HAND) {
            return InputStateKey.getAttackProgress(player, partialTick);
        }
        float attack = InputStateKey.getAttackProgress(player, partialTick);
        if (attack > 0.0f && (InputStateKey.getSwingingHand(player) == hand
                || (!InputStateKey.isLocalPlayerEntity(player) && hand == InteractionHand.MAIN_HAND))) {
            return attack;
        }
        if (InputStateKey.isSwinging(player, hand)) {
            return Mth.clamp(InputStateKey.getSwingTicks(player, partialTick) / 6.0f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    private static boolean isToolOrWeapon(String itemType) {
        return "sword".equals(itemType)
                || "axe".equals(itemType)
                || "pickaxe".equals(itemType)
                || "shovel".equals(itemType)
                || "hoe".equals(itemType)
                || "trident".equals(itemType)
                || "lance".equals(itemType)
                || "mace".equals(itemType);
    }

    private static HumanoidArm armForHand(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    private static void setArm(PoseValues pose, HumanoidArm arm, float x, float y, float z) {
        if (arm == HumanoidArm.LEFT) {
            pose.leftArmX = x;
            pose.leftArmY = y;
            pose.leftArmZ = z;
        } else {
            pose.rightArmX = x;
            pose.rightArmY = y;
            pose.rightArmZ = z;
        }
    }

    private static void addArm(PoseValues pose, HumanoidArm arm, float x, float y, float z) {
        if (arm == HumanoidArm.LEFT) {
            pose.leftArmX += x;
            pose.leftArmY += y;
            pose.leftArmZ += z;
        } else {
            pose.rightArmX += x;
            pose.rightArmY += y;
            pose.rightArmZ += z;
        }
    }

    private static void addForeArm(PoseValues pose, HumanoidArm arm, float x, float y, float z) {
        if (arm == HumanoidArm.LEFT) {
            pose.leftForeArmX += x;
            pose.leftForeArmY += y;
            pose.leftForeArmZ += z;
        } else {
            pose.rightForeArmX += x;
            pose.rightForeArmY += y;
            pose.rightForeArmZ += z;
        }
    }

    private static void addBodyYaw(PoseValues pose, float y) {
        pose.bodyY += y;
    }

    private static final class PartTransformProvider {
        private final int[] candidateIds;
        private final String[] candidateNames;
        private @Nullable ModelBoneView target;

        private PartTransformProvider(String[] names) {
            this.candidateNames = names;
            this.candidateIds = new int[names.length];
            for (int i = 0; i < names.length; i++) {
                this.candidateIds[i] = BoneIds.id(names[i]);
            }
        }

        private void bind(List<ModelBoneView> bones) {
            this.target = null;
            for (int i = 0; i < this.candidateIds.length; i++) {
                int candidateId = this.candidateIds[i];
                String candidateName = this.candidateNames[i];
                for (ModelBoneView bone : bones) {
                    if (bone.getBoneId() == candidateId || normalize(bone.getName()).equals(candidateName)) {
                        this.target = bone;
                        return;
                    }
                }
            }
        }

        private void apply(float x, float y, float z, ModelScenePose scenePose, boolean fallbackOnly) {
            ModelBoneView bone = this.target;
            if (bone == null) {
                return;
            }
            if (fallbackOnly && scenePose.isRotationDriven(bone.getName())) {
                return;
            }
            Vector3f init = bone.getInitialRotation();
            bone.setRotation(init.x + x, init.y + y, init.z + z);
        }
    }

    private static String normalize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = Character.toLowerCase(name.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static final class PoseValues {
        private String name;
        private float headX;
        private float headY;
        private float headZ;
        private float bodyX;
        private float bodyY;
        private float bodyZ;
        private float leftArmX;
        private float leftArmY;
        private float leftArmZ;
        private float leftForeArmX;
        private float leftForeArmY;
        private float leftForeArmZ;
        private float leftHandX;
        private float leftHandY;
        private float leftHandZ;
        private float rightArmX;
        private float rightArmY;
        private float rightArmZ;
        private float rightForeArmX;
        private float rightForeArmY;
        private float rightForeArmZ;
        private float rightHandX;
        private float rightHandY;
        private float rightHandZ;
        private float leftLegX;
        private float leftLegY;
        private float leftLegZ;
        private float leftLowerLegX;
        private float leftLowerLegY;
        private float leftLowerLegZ;
        private float leftFootX;
        private float leftFootY;
        private float leftFootZ;
        private float rightLegX;
        private float rightLegY;
        private float rightLegZ;
        private float rightLowerLegX;
        private float rightLowerLegY;
        private float rightLowerLegZ;
        private float rightFootX;
        private float rightFootY;
        private float rightFootZ;
    }
}
