package com.micaftic.morpher.core.compat.gun.tacz;

import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.render.ModelScenePose;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.spm.core.scene.CodedLoopMode;
import dev.spm.core.scene.MolangValue;
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

    public static void registerControllerFunctions(HostMolangSink binding) {
        if (!isLoaded()) {
            registerUnavailableFunctions(binding);
            return;
        }
        binding.var("tac_hold_gun", event -> MolangValue.bool(event.getAnimatable().getEntity() instanceof LivingEntity living && living.getMainHandItem().getItem() instanceof IGun));
        binding.var("tac_gun_type", event -> MolangValue.text(event.getAnimatable().getEntity() instanceof LivingEntity living ? getGunType(living.getMainHandItem()) : ""));
        binding.var("tac_gun_id", event -> MolangValue.text(event.getAnimatable().getEntity() instanceof LivingEntity living ? getGunId(living.getMainHandItem()) : ""));
        binding.var("tac_is_fire", event -> MolangValue.bool(event.getAnimatable().getEntity() instanceof LivingEntity living && operator(living).getSynShootCoolDown() > 0));
        binding.var("tac_is_aim", event -> MolangValue.bool(event.getAnimatable().getEntity() instanceof LivingEntity living && operator(living).getSynAimingProgress() > 0.0F));
        binding.var("tac_is_reload", event -> MolangValue.bool(event.getAnimatable().getEntity() instanceof LivingEntity living && operator(living).getSynReloadState().getCountDown() > 0));
        binding.var("tac_is_melee", event -> MolangValue.bool(event.getAnimatable().getEntity() instanceof LivingEntity living && operator(living).getSynMeleeCoolDown() > 0));
        binding.var("tac_is_draw", event -> MolangValue.bool(event.getAnimatable().getEntity() instanceof LivingEntity living && operator(living).getSynDrawCoolDown() > 0));
        binding.var("tac_fire_mode", event -> {
            if (!(event.getAnimatable().getEntity() instanceof LivingEntity living)) {
                return MolangValue.text("");
            }
            FireMode fireMode = IGun.getMainHandFireMode(living);
            return MolangValue.text(fireMode == FireMode.UNKNOWN ? "" : fireMode.name());
        });
    }

    /* Gun item rendering remains on the normal hand/locator path. */
    public static void applyItemTransform(ItemStack stack, ModelScenePose model, LivingEntity entity, PoseStack poseStack, int packedLightIn, float partialTicks) {
    }

    public static HostPlayState.Decision handleTaczAnimState(LivingEntity entity, HostActionEvent<? extends LivingAnimatable<?>> event, String animation, CodedLoopMode loopType) {
        if (!isLoaded() || IGun.getIGunOrNull(entity.getMainHandItem()) == null) {
            return null;
        }
        String taczAnimation = "tac:" + animation;
        return event.getAnimatable().getAnimation(taczAnimation) != null
                ? setAnimation(event, taczAnimation, loopType)
                : setAnimation(event, animation, loopType);
    }

    public static HostPlayState.Decision handleGunHoldAnimState(ItemStack stack, HostActionEvent<? extends LivingAnimatable<?>> event) {
        if (!isLoaded()) {
            return null;
        }
        String type = getGunType(stack);
        LivingEntity entity = event.getAnimatable().getEntity();
        if (type.isEmpty() || entity == null) {
            return type.isEmpty() ? null : HostPlayState.Decision.STOP;
        }
        IGunOperator operator = operator(entity);
        if (!entity.isSwimming() && entity.getPose() == Pose.SWIMMING) {
            return playGunAnimation(event, type, Math.abs(event.getLimbSwingAmount()) > 0.05D ? "tac:climb:" : "tac:climbing:", CodedLoopMode.LOOP);
        }
        if (operator.getSynAimingProgress() > 0.0F) {
            return playGunAnimation(event, type, "tac:aim:", CodedLoopMode.LOOP);
        }
        if (entity.onGround() && entity.isSprinting()) {
            return playGunAnimation(event, type, "tac:run:", CodedLoopMode.LOOP);
        }
        return playGunAnimation(event, type, "tac:hold:", CodedLoopMode.LOOP);
    }

    public static HostPlayState.Decision handleGunActionAnimState(ItemStack stack, HostActionEvent<? extends LivingAnimatable<?>> event) {
        if (!isLoaded()) {
            return null;
        }
        String type = getGunType(stack);
        LivingEntity entity = event.getAnimatable().getEntity();
        if (type.isEmpty() || entity == null) {
            return type.isEmpty() ? null : HostPlayState.Decision.STOP;
        }
        IGunOperator operator = operator(entity);
        long shootCooldown = operator.getSynShootCoolDown();
        if (entity instanceof IClientPlayerGunOperator clientOperator) {
            shootCooldown = Math.max(shootCooldown, clientOperator.getClientShootCoolDown());
        }
        if (operator.getSynReloadState().getCountDown() > 0) {
            return playGunAnimation(event, type, "tac:reload:", CodedLoopMode.PLAY_ONCE);
        }
        if (operator.getSynMeleeCoolDown() > 0) {
            return playGunAnimation(event, type, "tac:melee:", CodedLoopMode.PLAY_ONCE);
        }
        if (shootCooldown <= 0) {
            return HostPlayState.Decision.CONTINUE;
        }
        if (!entity.isSwimming() && entity.getPose() == Pose.SWIMMING && Math.abs(event.getLimbSwingAmount()) <= 0.05D) {
            return playGunAnimation(event, type, "tac:climbing:fire:", CodedLoopMode.PLAY_ONCE);
        }
        return playGunAnimation(event, type, operator.getSynAimingProgress() > 0.0F ? "tac:aim:fire:" : "tac:hold:fire:", CodedLoopMode.PLAY_ONCE);
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
        return IGunOperator.fromLivingEntity(entity);
    }

    private static HostPlayState.Decision playGunAnimation(HostActionEvent<? extends LivingAnimatable<?>> event, String type, String prefix, CodedLoopMode loopType) {
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

    private static HostPlayState.Decision setAnimation(HostActionEvent<?> event, String animation, CodedLoopMode loopType) {
        event.getController().setAnimation(animation, loopType);
        return HostPlayState.Decision.CONTINUE;
    }

    private static boolean isGunType(String type, GunTabType tab) {
        return type.equals(tab.name().toLowerCase(Locale.ENGLISH));
    }
}
