package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class VanillaHumanoidPoseSampler {

    private VanillaHumanoidPoseSampler() {
    }

    public static PoseSample sample(Player player, AnimationEvent<?> event) {
        try {
            Object model = createPlayerModel();
            if (model == null) {
                return null;
            }
            if (setupRenderStateModel(model, player, event) || setupLegacyModel(model, player, event)) {
                return readPose(model);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return null;
    }

    private static Object createPlayerModel() throws ReflectiveOperationException {
        Object entityModels = Minecraft.getInstance().getEntityModels();
        Class<?> layersClass = Class.forName("net.minecraft.client.model.geom.ModelLayers");
        Object layer = getStaticField(layersClass, "PLAYER_SLIM");
        Method bakeLayer = findMethod(entityModels.getClass(), "bakeLayer", 1);
        Object root = bakeLayer.invoke(entityModels, layer);
        Class<?> modelClass = classOrNull("net.minecraft.client.model.player.PlayerModel");
        if (modelClass == null) {
            modelClass = classOrNull("net.minecraft.client.model.PlayerModel");
        }
        if (modelClass == null) {
            return null;
        }
        for (Constructor<?> constructor : modelClass.getConstructors()) {
            Class<?>[] params = constructor.getParameterTypes();
            if (params.length == 2 && params[1] == boolean.class) {
                return constructor.newInstance(root, true);
            }
        }
        return null;
    }

    private static boolean setupRenderStateModel(Object model, Player player, AnimationEvent<?> event) throws ReflectiveOperationException {
        Class<?> stateClass = classOrNull("net.minecraft.client.renderer.entity.state.AvatarRenderState");
        if (stateClass == null) {
            return false;
        }
        Object state = stateClass.getConstructor().newInstance();
        setField(state, "ageInTicks", event.getCurrentTick());
        setField(state, "walkAnimationPos", event.getLimbSwing());
        setField(state, "walkAnimationSpeed", Mth.clamp(event.getLimbSwingAmount(), 0.0f, 1.0f));
        setField(state, "xRot", event.getModelData().headPitch);
        setField(state, "yRot", event.getModelData().netHeadYaw);
        setField(state, "pose", player.getPose());
        setField(state, "isCrouching", player.getPose() == Pose.CROUCHING);
        setField(state, "isPassenger", event.getModelData().isSitting || player.isPassenger());
        setField(state, "isVisuallySwimming", player.isSwimming());
        setField(state, "isFallFlying", player.isFallFlying());
        setField(state, "isUsingItem", player.isUsingItem());
        setField(state, "useItemHand", player.getUsedItemHand());
        setField(state, "ticksUsingItem", (float) player.getTicksUsingItem());
        setField(state, "mainArm", player.getMainArm());
        setField(state, "attackArm", getAttackArm(player));
        setField(state, "attackTime", player.getAttackAnim(event.getPartialTick()));
        setField(state, "rightHandItemStack", itemForArm(player, HumanoidArm.RIGHT));
        setField(state, "leftHandItemStack", itemForArm(player, HumanoidArm.LEFT));
        setField(state, "rightArmPose", armPose(player, HumanoidArm.RIGHT));
        setField(state, "leftArmPose", armPose(player, HumanoidArm.LEFT));
        Method setupAnim = findMethod(model.getClass(), "setupAnim", 1);
        setupAnim.invoke(model, state);
        return true;
    }

    private static boolean setupLegacyModel(Object model, Player player, AnimationEvent<?> event) throws ReflectiveOperationException {
        setField(model, "attackTime", player.getAttackAnim(event.getPartialTick()));
        setField(model, "riding", event.getModelData().isSitting || player.isPassenger());
        setField(model, "young", player.isBaby());
        Method setupAnim = findMethod(model.getClass(), "setupAnim", 6);
        setupAnim.invoke(model, player, event.getLimbSwing(), event.getLimbSwingAmount(), event.getCurrentTick(),
                event.getModelData().netHeadYaw, event.getModelData().headPitch);
        return true;
    }

    private static PoseSample readPose(Object model) throws ReflectiveOperationException {
        PoseSample sample = new PoseSample();
        readPart(model, "head", sample.head);
        readPart(model, "body", sample.body);
        readPart(model, "leftArm", sample.leftArm);
        readPart(model, "rightArm", sample.rightArm);
        readPart(model, "leftLeg", sample.leftLeg);
        readPart(model, "rightLeg", sample.rightLeg);
        return sample;
    }

    private static void readPart(Object model, String fieldName, PartPose out) throws ReflectiveOperationException {
        Object part = getField(model, fieldName);
        out.xRot = getFloat(part, "xRot");
        out.yRot = getFloat(part, "yRot");
        out.zRot = getFloat(part, "zRot");
    }

    private static HumanoidArm getAttackArm(Player player) {
        InteractionHand hand = player.swingingArm == null ? InteractionHand.MAIN_HAND : player.swingingArm;
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    private static ItemStack itemForArm(Player player, HumanoidArm arm) {
        return arm == player.getMainArm() ? player.getMainHandItem() : player.getOffhandItem();
    }

    private static Object armPose(Player player, HumanoidArm arm) {
        ItemStack stack = itemForArm(player, arm);
        if (stack.isEmpty()) {
            return enumValue("EMPTY");
        }
        if (player.isUsingItem() && itemForArm(player, arm) == player.getUseItem()) {
            return enumValue(switch (stack.getUseAnimation()) {
                case BLOCK -> "BLOCK";
                case BOW -> "BOW_AND_ARROW";
                case CROSSBOW -> "CROSSBOW_CHARGE";
                case SPEAR -> "THROW_SPEAR";
                default -> "ITEM";
            });
        }
        return enumValue("ITEM");
    }

    private static Object enumValue(String name) {
        Class<?> enumClass = classOrNull("net.minecraft.client.model.HumanoidModel$ArmPose");
        if (enumClass == null) {
            return null;
        }
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return enumClass.getEnumConstants()[0];
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(name);
    }

    private static Object getStaticField(Class<?> type, String name) throws ReflectiveOperationException {
        Field field = type.getField(name);
        return field.get(null);
    }

    private static Object getField(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static float getFloat(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        field.setAccessible(true);
        return field.getFloat(target);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    public static final class PoseSample {
        public final PartPose head = new PartPose();
        public final PartPose body = new PartPose();
        public final PartPose leftArm = new PartPose();
        public final PartPose rightArm = new PartPose();
        public final PartPose leftLeg = new PartPose();
        public final PartPose rightLeg = new PartPose();
    }

    public static final class PartPose {
        public float xRot;
        public float yRot;
        public float zRot;
    }
}
