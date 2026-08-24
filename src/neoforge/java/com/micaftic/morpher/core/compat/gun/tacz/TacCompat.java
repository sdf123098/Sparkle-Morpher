package com.micaftic.morpher.core.compat.gun.tacz;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.geckolib3.geo.animated.AnimatedGeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.neoforged.fml.ModList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Optional;

/** Compatibility with MUKSC's TaCZ 1.21.1 NeoForge port. */
public final class TacCompat {
    private static final String MOD_ID = "tacz";

    private TacCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void registerControllerFunctions(CtrlBinding binding) {
        if (!isLoaded()) {
            registerUnavailableFunctions(binding);
            return;
        }
        binding.livingEntityVar("tac_hold_gun", ctx -> IGun.mainhandHoldGun(ctx.entity()));
        binding.livingEntityVar("tac_gun_type", ctx -> getGunType(ctx.entity().getMainHandItem()));
        binding.livingEntityVar("tac_gun_id", ctx -> getGunId(ctx.entity().getMainHandItem()));
        binding.livingEntityVar("tac_is_fire", ctx -> operator(ctx.entity()).getSynShootCoolDown() > 0);
        binding.livingEntityVar("tac_is_aim", ctx -> operator(ctx.entity()).getSynAimingProgress() > 0.0F);
        binding.livingEntityVar("tac_is_reload", ctx -> operator(ctx.entity()).getSynReloadState().getCountDown() > 0);
        binding.livingEntityVar("tac_is_melee", ctx -> operator(ctx.entity()).getSynMeleeCoolDown() > 0);
        binding.livingEntityVar("tac_is_draw", ctx -> operator(ctx.entity()).getSynDrawCoolDown() > 0);
        binding.livingEntityVar("tac_fire_mode", ctx -> {
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
        return event.getAnimatable().getAnimation(taczAnimation) != null
                ? setAnimation(event, taczAnimation, loopType)
                : setAnimation(event, animation, loopType);
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
        if (!entity.isSwimming() && entity.getPose() == Pose.SWIMMING) {
            return playGunAnimation(event, type, Math.abs(event.getLimbSwingAmount()) > 0.05D ? "tac:climb:" : "tac:climbing:", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (operator.getSynAimingProgress() > 0.0F) {
            return playGunAnimation(event, type, "tac:aim:", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (entity.onGround() && entity.isSprinting()) {
            return playGunAnimation(event, type, "tac:run:", ILoopType.EDefaultLoopTypes.LOOP);
        }
        return playGunAnimation(event, type, "tac:hold:", ILoopType.EDefaultLoopTypes.LOOP);
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
        long shootCooldown = operator.getSynShootCoolDown();
        if (entity instanceof IClientPlayerGunOperator clientOperator) {
            shootCooldown = Math.max(shootCooldown, clientOperator.getClientShootCoolDown());
        }
        if (operator.getSynReloadState().getCountDown() > 0) {
            return playGunAnimation(event, type, "tac:reload:", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }
        if (operator.getSynMeleeCoolDown() > 0) {
            return playGunAnimation(event, type, "tac:melee:", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }
        if (shootCooldown <= 0) {
            return PlayState.CONTINUE;
        }
        if (!entity.isSwimming() && entity.getPose() == Pose.SWIMMING && Math.abs(event.getLimbSwingAmount()) <= 0.05D) {
            return playGunAnimation(event, type, "tac:climbing:fire:", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
        }
        return playGunAnimation(event, type, operator.getSynAimingProgress() > 0.0F ? "tac:aim:fire:" : "tac:hold:fire:", ILoopType.EDefaultLoopTypes.PLAY_ONCE);
    }

    public static void handleGunSound(LivingEntity entity, ItemStack stack) {
    }

    public static void handleItemSound(ItemStack stack) {
    }

    public static ResourceLocation getGunTexture(ItemStack stack) {
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
        return IGunOperator.fromLivingEntity(entity);
    }

    private static PlayState playGunAnimation(AnimationEvent<? extends LivingAnimatable<?>> event, String type, String prefix, ILoopType loopType) {
        ConditionTAC condition = event.getAnimatable().getModelConfig().getTAC();
        if (condition != null) {
            String custom = condition.doTest(event.getAnimatable().getEntity().getMainHandItem(), prefix);
            if (StringUtils.isNotBlank(custom)) {
                return setAnimation(event, custom, loopType);
            }
        }
        if (isGunType(type, GunTabType.PISTOL)) {
            return setAnimation(event, prefix + "pistol", loopType);
        }
        if (isGunType(type, GunTabType.RPG)) {
            return setAnimation(event, prefix + "rpg", loopType);
        }
        return setAnimation(event, prefix + "rifle", loopType);
    }

    private static PlayState setAnimation(AnimationEvent<?> event, String animation, ILoopType loopType) {
        event.getController().setAnimation(animation, loopType);
        return PlayState.CONTINUE;
    }

    private static boolean isGunType(String type, GunTabType tab) {
        return type.equals(tab.name().toLowerCase(Locale.ENGLISH));
    }
}
