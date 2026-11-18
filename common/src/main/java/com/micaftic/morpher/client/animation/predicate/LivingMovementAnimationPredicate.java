package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.RidingAnimationPlan;
import com.micaftic.morpher.client.animation.condition.ConditionChair;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.condition.ConditionVehicle;
import com.micaftic.morpher.client.compat.touhoulittlemaid.TouhouLittleMaidClientCompat;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.carryon.CarryOnCompat;
import com.micaftic.morpher.core.compat.swem.SWEMCompat;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LivingMovementAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        return Objects.requireNonNullElse(renderRidingAnimation(event), HostPlayState.Decision.STOP);
    }

    @Nullable
    public HostPlayState.Decision renderRidingAnimation(HostActionEvent<LivingAnimatable<?>> event) {
        Entity vehicle;
        ConditionChair conditionChair;
        LivingEntity livingEntity = event.getAnimatable().getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable) || (vehicle = livingEntity.getVehicle()) == null || !vehicle.isAlive()) {
            return null;
        }
        String str = SWEMCompat.getHorseGaitName(livingEntity);
        if (StringUtils.isNoneBlank(str)) {
            return IAnimationPredicate.playAnimationWithLoop(event, str, CodedLoopMode.LOOP);
        }
        ConditionManager conditionManager = event.getAnimatable().getModelConfig();
        if (TouhouLittleMaidCompat.isLoaded() && (conditionChair = conditionManager.getChair()) != null) {
            String str2 = conditionChair.doTest(livingEntity);
            if (StringUtils.isNoneBlank(str2)) {
                return IAnimationPredicate.playAnimationWithLoop(event, str2, CodedLoopMode.LOOP);
            }
        }
        ConditionVehicle conditionVehicle = conditionManager.getVehicle();
        if (conditionVehicle != null) {
            String str3 = conditionVehicle.doTest(livingEntity);
            if (StringUtils.isNoneBlank(str3)) {
                return IAnimationPredicate.playAnimationWithLoop(event, str3, CodedLoopMode.LOOP);
            }
        }
        if (vehicle instanceof Pig) {
            return playPlanned(event, RidingAnimationPlan.Kind.PIG);
        }
        if (vehicle instanceof AbstractBoat) {
            return playPlanned(event, RidingAnimationPlan.Kind.BOAT);
        }
        if (vehicle instanceof AbstractMinecart) {
            return playPlanned(event, RidingAnimationPlan.Kind.SEATED);
        }
        boolean z = (livingEntity instanceof Player var8) && CarryOnCompat.isPlayerCarrying(var8);
        boolean z2 = TouhouLittleMaidCompat.isMaidEntity(livingEntity) && (livingEntity.getVehicle() instanceof Player);
        if (z || z2) {
            return IAnimationPredicate.playAnimationWithLoop(event, "carryon:princess", CodedLoopMode.LOOP);
        }
        HostPlayState.Decision playState = TouhouLittleMaidClientCompat.handleMaidInteraction(event, livingEntity, vehicle);
        if (playState != null) {
            return playState;
        }
        if (vehicle instanceof HappyGhast) {
            return playPlanned(event, RidingAnimationPlan.Kind.SEATED);
        }
        if (vehicle instanceof LivingEntity) {
            return playPlanned(event, RidingAnimationPlan.Kind.STRADDLE);
        }
        return playPlanned(event, RidingAnimationPlan.Kind.SEATED);
    }

    private HostPlayState.Decision playPlanned(HostActionEvent<LivingAnimatable<?>> event, RidingAnimationPlan.Kind kind) {
        var assembly = event.getAnimatable().getModelAssembly();
        var bundle = assembly == null ? null : assembly.getAnimationBundle();
        if (bundle == null) return HostPlayState.Decision.STOP;
        String animation = bundle.getRidingAnimationPlan().selection(kind).animation();
        return animation == null ? HostPlayState.Decision.STOP
                : IAnimationPredicate.playAnimationWithLoop(event, animation, CodedLoopMode.LOOP);
    }
}
