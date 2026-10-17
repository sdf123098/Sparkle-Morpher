package com.micaftic.morpher.core.compat.gun.tacz.fabric;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.gun.tacz.ConditionTAC;
import com.micaftic.morpher.core.compat.gun.tacz.TacAnimationResolver;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Optional gameplay bridge for TaCZ Refabricated 26.1.2, 26.2 and 26.3; rendering stays with TaCZ. */
public final class TacCompatImpl {
    private static final String MOD_ID = "tacz";

    private TacCompatImpl() {
    }

    public static boolean isLoaded() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    public static void registerControllerFunctions(CtrlBinding binding) {
        if (!isLoaded()) {
            registerUnavailableFunctions(binding);
            return;
        }
        binding.livingEntityVar("tac_hold_gun", ctx -> ctx.entity() != null && IGun.mainhandHoldGun(ctx.entity()));
        binding.livingEntityVar("tac_gun_type", ctx -> ctx.entity() == null ? StringPool.EMPTY : getGunType(ctx.entity().getMainHandItem()));
        binding.livingEntityVar("tac_gun_id", ctx -> ctx.entity() == null ? StringPool.EMPTY : getGunId(ctx.entity().getMainHandItem()));
        binding.livingEntityVar("tac_is_fire", ctx -> operator(ctx.entity()) != null && operator(ctx.entity()).getSynShootCoolDown() > 0);
        binding.livingEntityVar("tac_is_aim", ctx -> operator(ctx.entity()) != null && operator(ctx.entity()).getSynAimingProgress() > 0.0F);
        binding.livingEntityVar("tac_is_reload", ctx -> isReloading(operator(ctx.entity())));
        binding.livingEntityVar("tac_is_melee", ctx -> operator(ctx.entity()) != null && operator(ctx.entity()).getSynMeleeCoolDown() > 0);
        binding.livingEntityVar("tac_is_draw", ctx -> operator(ctx.entity()) != null && operator(ctx.entity()).getSynDrawCoolDown() > 0);
        binding.livingEntityVar("tac_fire_mode", ctx -> {
            if (ctx.entity() == null) return StringPool.EMPTY;
            FireMode fireMode = IGun.getMainHandFireMode(ctx.entity());
            return fireMode == FireMode.UNKNOWN ? StringPool.EMPTY : fireMode.name();
        });
    }

    /* Gun item rendering remains on the normal hand/locator path. */
    public static void applyItemTransform(ItemStack stack, AnimatedGeoModel model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
    }

    public static PlayState handleTaczAnimState(LivingEntity entity, AnimationEvent<? extends LivingAnimatable<?>> event, String animation, ILoopType loopType) {
        if (!isLoaded() || IGun.getIGunOrNull(entity.getMainHandItem()) == null) {
            return null;
        }
        String taczAnimation = "tac:" + animation;
        if (event.getAnimatable().getAnimation(taczAnimation) != null) return setAnimation(event, taczAnimation, loopType);
        return event.getAnimatable().getAnimation(animation) == null ? null : setAnimation(event, animation, loopType);
    }

    public static PlayState handleGunHoldAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) {
        if (!isLoaded()) {
            return null;
        }
        String type = getGunType(stack);
        LivingEntity entity = event.getAnimatable().getEntity();
        if (type.isEmpty() || entity == null) {
            return type.isEmpty() ? null : PlayState.STOP;
        }
        IGunOperator operator = operator(entity);
        if (operator == null) return null;
        String prefix = TacAnimationResolver.holdPrefix(!entity.isSwimming() && entity.getPose() == Pose.SWIMMING,
                Math.abs(event.getLimbSwingAmount()) > 0.05D, operator.getSynAimingProgress() > 0.0F,
                entity.onGround() && entity.isSprinting());
        return playGunAnimation(event, type, prefix, ILoopType.EDefaultLoopTypes.LOOP);
    }

    public static PlayState handleGunActionAnimState(ItemStack stack, AnimationEvent<? extends LivingAnimatable<?>> event) {
        if (!isLoaded()) {
            return null;
        }
        String type = getGunType(stack);
        LivingEntity entity = event.getAnimatable().getEntity();
        if (type.isEmpty() || entity == null) {
            return type.isEmpty() ? null : PlayState.STOP;
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
        return prefix == null ? PlayState.CONTINUE : playGunAnimation(event, type, prefix, ILoopType.EDefaultLoopTypes.PLAY_ONCE);
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

    private static void registerUnavailableFunctions(CtrlBinding binding) {
        binding.livingEntityVar("tac_hold_gun", ctx -> false);
        binding.livingEntityVar("tac_gun_type", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("tac_gun_id", ctx -> StringPool.EMPTY);
        binding.livingEntityVar("tac_is_fire", ctx -> false);
        binding.livingEntityVar("tac_is_aim", ctx -> false);
        binding.livingEntityVar("tac_is_reload", ctx -> false);
        binding.livingEntityVar("tac_is_melee", ctx -> false);
        binding.livingEntityVar("tac_is_draw", ctx -> false);
        binding.livingEntityVar("tac_fire_mode", ctx -> StringPool.EMPTY);
    }

    private static String getGunType(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return StringPool.EMPTY;
        }
        Optional<CommonGunIndex> index = TimelessAPI.getCommonGunIndex(gun.getGunId(stack));
        return index.map(CommonGunIndex::getType).orElse(StringPool.EMPTY);
    }

    private static String getGunId(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        return gun == null ? StringPool.EMPTY : gun.getGunId(stack).toString();
    }

    private static IGunOperator operator(LivingEntity entity) {
        return entity instanceof IGunOperator operator ? operator : null;
    }

    private static PlayState playGunAnimation(AnimationEvent<? extends LivingAnimatable<?>> event, String type, String prefix, ILoopType loopType) {
        ConditionTAC condition = event.getAnimatable().getModelConfig().getTAC();
        String custom = condition == null ? "" : condition.doTest(event.getAnimatable().getEntity().getMainHandItem(), prefix);
        String animation = TacAnimationResolver.select(prefix, type, custom,
                name -> event.getAnimatable().getAnimation(name) != null);
        return animation == null ? null : setAnimation(event, animation, loopType);
    }

    private static PlayState setAnimation(AnimationEvent<?> event, String animation, ILoopType loopType) {
        event.getController().setAnimation(animation, loopType);
        return PlayState.CONTINUE;
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
