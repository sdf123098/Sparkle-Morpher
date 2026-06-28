package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.geckolib3.core.controller.BoneTransformProvider;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.core.molang.value.IValue;
import com.micaftic.morpher.geckolib3.core.processor.AnimationProcessor;
import com.micaftic.morpher.geckolib3.core.snapshot.BoneTopLevelSnapshot;
import com.micaftic.morpher.geckolib3.core.util.TransitionVector3f;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class ImportedVanillaPoseController implements IAnimationController<CustomPlayerEntity> {

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

    private final String name;
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

    public ImportedVanillaPoseController(String name) {
        this(name, false);
    }

    public ImportedVanillaPoseController(String name, boolean fallbackOnly) {
        this.name = name;
        this.fallbackOnly = fallbackOnly;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getCurrentAnimation() {
        return "[vanilla_pose] " + this.currentPose;
    }

    @Override
    public void init(List<BoneTopLevelSnapshot> bones, Object2ReferenceMap<String, List<IValue>> expressions) {
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
    public void process(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<AnimationContext<?>> evaluator, boolean active) {
        if (!active) {
            return;
        }
        Player player = event.getAnimatable().getEntity();
        if (player == null) {
            return;
        }
        AnimationProcessor<?> processor = event.getAnimatable().getEvaluationContext();
        PoseValues pose = calculatePose(player, event);
        this.currentPose = pose.name;
        this.head.setRotation(pose.headX, pose.headY, pose.headZ, processor);
        this.body.setRotation(pose.bodyX, pose.bodyY, pose.bodyZ, processor);
        this.leftArm.setRotation(pose.leftArmX, pose.leftArmY, pose.leftArmZ, processor);
        this.rightArm.setRotation(pose.rightArmX, pose.rightArmY, pose.rightArmZ, processor);
        this.leftForeArm.setRotation(pose.leftForeArmX, pose.leftForeArmY, pose.leftForeArmZ, processor);
        this.rightForeArm.setRotation(pose.rightForeArmX, pose.rightForeArmY, pose.rightForeArmZ, processor);
        this.leftHand.setRotation(pose.leftHandX, pose.leftHandY, pose.leftHandZ, processor);
        this.rightHand.setRotation(pose.rightHandX, pose.rightHandY, pose.rightHandZ, processor);
        this.leftLeg.setRotation(pose.leftLegX, pose.leftLegY, pose.leftLegZ, processor);
        this.rightLeg.setRotation(pose.rightLegX, pose.rightLegY, pose.rightLegZ, processor);
        this.leftLowerLeg.setRotation(pose.leftLowerLegX, pose.leftLowerLegY, pose.leftLowerLegZ, processor);
        this.rightLowerLeg.setRotation(pose.rightLowerLegX, pose.rightLowerLegY, pose.rightLowerLegZ, processor);
        this.leftFoot.setRotation(pose.leftFootX, pose.leftFootY, pose.leftFootZ, processor);
        this.rightFoot.setRotation(pose.rightFootX, pose.rightFootY, pose.rightFootZ, processor);
    }

    @Override
    public void forEachTransform(Consumer<BoneTransformProvider> consumer) {
        this.head.accept(consumer, this.fallbackOnly);
        this.body.accept(consumer, this.fallbackOnly);
        this.leftArm.accept(consumer, this.fallbackOnly);
        this.rightArm.accept(consumer, this.fallbackOnly);
        this.leftForeArm.accept(consumer, this.fallbackOnly);
        this.rightForeArm.accept(consumer, this.fallbackOnly);
        this.leftHand.accept(consumer, this.fallbackOnly);
        this.rightHand.accept(consumer, this.fallbackOnly);
        this.leftLeg.accept(consumer, this.fallbackOnly);
        this.rightLeg.accept(consumer, this.fallbackOnly);
        this.leftLowerLeg.accept(consumer, this.fallbackOnly);
        this.rightLowerLeg.accept(consumer, this.fallbackOnly);
        this.leftFoot.accept(consumer, this.fallbackOnly);
        this.rightFoot.accept(consumer, this.fallbackOnly);
    }

    @Override
    public void reset() {
        this.currentPose = "idle";
    }

    private static PoseValues calculatePose(Player player, AnimationEvent<CustomPlayerEntity> event) {
        VanillaHumanoidPoseSampler.PoseSample vanillaPose = VanillaHumanoidPoseSampler.sample(player, event);
        if (vanillaPose != null) {
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

    private static void applyHeldItemPose(Player player, AnimationEvent<CustomPlayerEntity> event, PoseValues pose) {
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
        if (isToolOrWeapon(itemType)) {
            addArm(pose, arm, -0.25f, 0.0f, arm == HumanoidArm.RIGHT ? 0.08f : -0.08f);
        }
    }

    private static void applyUsePose(Player player, AnimationEvent<CustomPlayerEntity> event, PoseValues pose, InteractionHand hand) {
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
        if ("mace".equals(itemType)) {
            pose.name = "use_mace";
            setArm(pose, arm, 2.25f, arm == HumanoidArm.RIGHT ? -0.25f : 0.25f, arm == HumanoidArm.RIGHT ? 0.35f : -0.35f);
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

    private static void applyGenericUsePose(Player player, AnimationEvent<CustomPlayerEntity> event, PoseValues pose, HumanoidArm arm) {
        float ticks = InputStateKey.getTicksUsingItem(player, event.getPartialTick());
        float progress = Mth.clamp(ticks / 10.0f, 0.0f, 1.0f);
        float reach = player.isUsingItem() ? 1.0f : Mth.sin(progress * (float) Math.PI);
        applyGenericSwingPose(pose, arm, reach, Mth.sin(progress * (float) Math.PI));
    }

    private static void applySwingPose(Player player, AnimationEvent<CustomPlayerEntity> event, PoseValues pose) {
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
        pose.name = "swing";
        applyGenericSwingPose(pose, arm, swing, recovery);
    }

    private static void applyGenericSwingPose(PoseValues pose, HumanoidArm arm, float swing, float recovery) {
        addArm(pose, arm,
                GENERIC_SWING_X * swing + GENERIC_SWING_RECOVERY_X * recovery,
                arm == HumanoidArm.RIGHT ? GENERIC_SWING_Y * swing : -GENERIC_SWING_Y * swing,
                arm == HumanoidArm.RIGHT ? GENERIC_SWING_Z * swing : -GENERIC_SWING_Z * swing);
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
            return player.getAttackAnim(partialTick);
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

    private static final class PartTransformProvider implements BoneTransformProvider {
        private final int[] candidateIds;
        private final TransitionVector3f rotation = new TransitionVector3f();
        private BoneTopLevelSnapshot target;
        private AnimationProcessor<?> processor;

        private PartTransformProvider(String[] names) {
            this.candidateIds = new int[names.length];
            for (int i = 0; i < names.length; i++) {
                this.candidateIds[i] = StringPool.computeIfAbsent(names[i]);
            }
        }

        private void bind(List<BoneTopLevelSnapshot> bones) {
            this.target = null;
            for (int candidateId : this.candidateIds) {
                for (BoneTopLevelSnapshot bone : bones) {
                    if (bone.boneId == candidateId || normalize(bone.bone.getName()).equals(StringPool.getString(candidateId))) {
                        this.target = bone;
                        return;
                    }
                }
            }
        }

        private void setRotation(float x, float y, float z, AnimationProcessor<?> processor) {
            this.rotation.set(x, y, z);
            this.rotation.percentCompleted = 0.0f;
            this.processor = processor;
        }

        private void accept(Consumer<BoneTransformProvider> consumer, boolean fallbackOnly) {
            if (this.target != null) {
                consumer.accept(fallbackOnly ? new FallbackTransformProvider(this) : this);
            }
        }

        @Override
        public BoneTopLevelSnapshot getBoneTarget() {
            return this.target;
        }

        @Override
        public TransitionVector3f getRotation(ExpressionEvaluator<AnimationContext<?>> evaluator) {
            return this.rotation;
        }

        @Override
        @Nullable
        public TransitionVector3f getPosition(ExpressionEvaluator<AnimationContext<?>> evaluator) {
            return null;
        }

        @Override
        @Nullable
        public TransitionVector3f getScale(ExpressionEvaluator<AnimationContext<?>> evaluator) {
            return null;
        }
    }

    private static final class FallbackTransformProvider implements BoneTransformProvider {
        private final PartTransformProvider delegate;

        private FallbackTransformProvider(PartTransformProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public BoneTopLevelSnapshot getBoneTarget() {
            return this.delegate.getBoneTarget();
        }

        @Override
        @Nullable
        public TransitionVector3f getRotation(ExpressionEvaluator<AnimationContext<?>> evaluator) {
            BoneTopLevelSnapshot target = this.delegate.getBoneTarget();
            if (target == null || this.delegate.processor == null || this.delegate.processor.isRotationActive(target.boneId)) {
                return null;
            }
            return this.delegate.getRotation(evaluator);
        }

        @Override
        @Nullable
        public TransitionVector3f getPosition(ExpressionEvaluator<AnimationContext<?>> evaluator) {
            return null;
        }

        @Override
        @Nullable
        public TransitionVector3f getScale(ExpressionEvaluator<AnimationContext<?>> evaluator) {
            return null;
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
