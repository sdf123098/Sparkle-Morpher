package com.micaftic.morpher.core.compat.gun.tacz.fabric;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.micaftic.morpher.core.compat.gun.tacz.ConditionTAC;
import com.micaftic.morpher.core.compat.gun.tacz.TacAnimationResolver;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangValue;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Optional gameplay bridge for TaCZ Refabricated 26.1.2, 26.2 and 26.3; rendering stays with TaCZ. */
public final class TacCompatImpl {
    private static final String MOD_ID = "tacz";

    private TacCompatImpl() {
    }

    public static boolean isLoaded() {
        try {
            return FabricLoader.getInstance().isModLoaded(MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void registerControllerFunctions(HostMolangSink binding) {
        if (!isLoaded()) {
            registerUnavailableFunctions(binding);
            return;
        }
        binding.var("tac_hold_gun", event -> MolangValue.bool(
                event.getAnimatable().getEntity() instanceof LivingEntity living && living.getMainHandItem().getItem() instanceof IGun));
        binding.var("tac_gun_type", event -> MolangValue.text(
                event.getAnimatable().getEntity() instanceof LivingEntity living ? getGunType(living.getMainHandItem()) : ""));
        binding.var("tac_gun_id", event -> MolangValue.text(
                event.getAnimatable().getEntity() instanceof LivingEntity living ? getGunId(living.getMainHandItem()) : ""));
        binding.var("tac_is_fire", event -> {
            IGunOperator op = operatorLiving(event);
            return MolangValue.bool(op != null && op.getSynShootCoolDown() > 0);
        });
        binding.var("tac_is_aim", event -> {
            IGunOperator op = operatorLiving(event);
            return MolangValue.bool(op != null && op.getSynAimingProgress() > 0.0F);
        });
        binding.var("tac_is_reload", event -> MolangValue.bool(isReloading(operatorLiving(event))));
        binding.var("tac_is_melee", event -> {
            IGunOperator op = operatorLiving(event);
            return MolangValue.bool(op != null && op.getSynMeleeCoolDown() > 0);
        });
        binding.var("tac_is_draw", event -> {
            IGunOperator op = operatorLiving(event);
            return MolangValue.bool(op != null && op.getSynDrawCoolDown() > 0);
        });
        binding.var("tac_fire_mode", event -> {
            if (!(event.getAnimatable().getEntity() instanceof LivingEntity living)) {
                return MolangValue.text("");
            }
            FireMode fireMode = IGun.getMainHandFireMode(living);
            return MolangValue.text(fireMode == FireMode.UNKNOWN ? "" : fireMode.name());
        });
    }

    private static @Nullable IGunOperator operatorLiving(HostActionEvent<?> event) {
        return event.getAnimatable().getEntity() instanceof LivingEntity living ? operator(living) : null;
    }

    /* Gun item rendering remains on the normal hand/locator path. */
    public static void applyItemTransform(ItemStack stack, ModelScenePose model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
    }

    public static @Nullable HostPlayState.Decision handleTaczAnimState(LivingEntity entity, HostActionEvent<? extends LivingAnimatable<?>> event, String animation, CodedLoopMode loopType) {
        if (!isLoaded() || IGun.getIGunOrNull(entity.getMainHandItem()) == null) {
            return null;
        }
        String taczAnimation = "tac:" + animation;
        if (event.getAnimatable().getAnimation(taczAnimation) != null) return setAnimation(event, taczAnimation, loopType);
        return event.getAnimatable().getAnimation(animation) == null ? null : setAnimation(event, animation, loopType);
    }

    public static @Nullable HostPlayState.Decision handleGunHoldAnimState(ItemStack stack, HostActionEvent<? extends LivingAnimatable<?>> event) {
        if (!isLoaded()) {
            return null;
        }
        String type = getGunType(stack);
        LivingEntity entity = event.getAnimatable().getEntity();
        if (type.isEmpty() || entity == null) {
            return type.isEmpty() ? null : HostPlayState.Decision.STOP;
        }
        IGunOperator operator = operator(entity);
        if (operator == null) return null;
        String prefix = TacAnimationResolver.holdPrefix(!entity.isSwimming() && entity.getPose() == Pose.SWIMMING,
                Math.abs(event.getLimbSwingAmount()) > 0.05D, operator.getSynAimingProgress() > 0.0F,
                entity.onGround() && entity.isSprinting());
        return playGunAnimation(event, type, prefix, CodedLoopMode.LOOP);
    }

    public static @Nullable HostPlayState.Decision handleGunActionAnimState(ItemStack stack, HostActionEvent<? extends LivingAnimatable<?>> event) {
        if (!isLoaded()) {
            return null;
        }
        String type = getGunType(stack);
        LivingEntity entity = event.getAnimatable().getEntity();
        if (type.isEmpty() || entity == null) {
            return type.isEmpty() ? null : HostPlayState.Decision.STOP;
        }
        IGunOperator operator = operator(entity);
        if (operator == null) return null;
        long shootCooldown = operator.getSynShootCoolDown();
        if (entity instanceof IClientPlayerGunOperator clientOperator) {
            shootCooldown = Math.max(shootCooldown, clientOperator.getClientShootCoolDown());
        }
        String prefix = TacAnimationResolver.actionPrefix(!entity.isSwimming() && entity.getPose() == Pose.SWIMMING,
                Math.abs(event.getLimbSwingAmount()) > 0.05D, operator.getSynAimingProgress() > 0.0F,
                isReloading(operator), operator.getSynMeleeCoolDown() > 0, shootCooldown);
        return prefix == null ? HostPlayState.Decision.CONTINUE : playGunAnimation(event, type, prefix, CodedLoopMode.PLAY_ONCE);
    }

    public static void handleGunSound(LivingEntity entity, ItemStack stack) {
    }

    public static void handleItemSound(ItemStack stack) {
    }

    public static Identifier getGunTexture(ItemStack stack) {
        if (!isLoaded()) {
            return null;
        }
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? null : gun.getGunId(stack);
    }

    private static void registerUnavailableFunctions(HostMolangSink binding) {
        binding.var("tac_hold_gun", event -> MolangValue.bool(false));
        binding.var("tac_gun_type", event -> MolangValue.text(""));
        binding.var("tac_gun_id", event -> MolangValue.text(""));
        binding.var("tac_is_fire", event -> MolangValue.bool(false));
        binding.var("tac_is_aim", event -> MolangValue.bool(false));
        binding.var("tac_is_reload", event -> MolangValue.bool(false));
        binding.var("tac_is_melee", event -> MolangValue.bool(false));
        binding.var("tac_is_draw", event -> MolangValue.bool(false));
        binding.var("tac_fire_mode", event -> MolangValue.text(""));
    }

    private static String getGunType(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return "";
        }
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
        return index.map(CommonGunIndex::getType).orElse("");
    }

    private static String getGunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? "" : gun.getGunId(stack).toString();
    }

    private static IGunOperator operator(LivingEntity entity) {
        return entity instanceof IGunOperator operator ? operator : null;
    }

    private static @Nullable HostPlayState.Decision playGunAnimation(HostActionEvent<? extends LivingAnimatable<?>> event, String type, String prefix, CodedLoopMode loopType) {
        ConditionTAC condition = event.getAnimatable().getModelConfig().getTAC();
        String custom = condition == null ? "" : condition.doTest(event.getAnimatable().getEntity().getMainHandItem(), prefix);
        String animation = TacAnimationResolver.select(prefix, type, custom,
                name -> event.getAnimatable().getAnimation(name) != null);
        return animation == null ? null : setAnimation(event, animation, loopType);
    }

    private static HostPlayState.Decision setAnimation(HostActionEvent<?> event, String animation, CodedLoopMode loopType) {
        event.getController().setAnimation(animation, loopType);
        return HostPlayState.Decision.CONTINUE;
    }

    private static boolean isReloading(IGunOperator operator) {
        return operator != null && operator.getSynReloadState() != null && operator.getSynReloadState().getCountDown() > 0;
    }

    /** Respect the kept draw/put-away stack and a real loaded viewmodel, when the port exposes its handoff API. */
    public static boolean ownsFirstPersonViewmodel(LivingEntity entity) {
        if (!isLoaded() || !(entity instanceof net.minecraft.client.player.LocalPlayer player)) return false;
        if (ViewmodelHook.ACTIVE != null) {
            try { return Boolean.TRUE.equals(ViewmodelHook.ACTIVE.invoke(null, player)); }
            catch (ReflectiveOperationException | LinkageError unavailable) { /* Older ports still expose IGun. */ }
        }
        return IGun.getIGunOrNull(player.getMainHandItem()) != null;
    }

    private static final class ViewmodelHook {
        private static final java.lang.reflect.Method ACTIVE = find();
        private static java.lang.reflect.Method find() {
            try {
                return Class.forName("com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat", false,
                        TacCompatImpl.class.getClassLoader()).getMethod("shouldUseTaczRenderer", net.minecraft.client.player.LocalPlayer.class);
            } catch (ReflectiveOperationException | LinkageError unavailable) { return null; }
        }
    }
}
