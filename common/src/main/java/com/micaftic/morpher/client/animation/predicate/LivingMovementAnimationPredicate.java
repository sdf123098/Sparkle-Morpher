package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.core.compat.carryon.CarryOnCompat;
import com.micaftic.morpher.core.compat.swem.SWEMCompat;
import com.micaftic.morpher.client.animation.condition.ConditionChair;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.client.animation.condition.ConditionVehicle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class LivingMovementAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public PlayState predicate(AnimationEvent<LivingAnimatable<?>> event, ExpressionEvaluator<?> evaluator) {
        return Objects.requireNonNullElse(renderRidingAnimation(event), PlayState.STOP);
    }

    @Nullable
    public PlayState renderRidingAnimation(AnimationEvent<LivingAnimatable<?>> event) {
        Entity vehicle;
        ConditionChair conditionChair;
        LivingEntity livingEntity = event.getAnimatable().getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable) || (vehicle = livingEntity.getVehicle()) == null || !vehicle.isAlive()) {
            return null;
        }
        String str = SWEMCompat.getHorseGaitName(livingEntity);
        if (StringUtils.isNoneBlank(str)) {
            return IAnimationPredicate.playAnimationWithLoop(event, str, ILoopType.EDefaultLoopTypes.LOOP);
        }
        ConditionManager conditionManager = event.getAnimatable().getModelConfig();
        if (TouhouLittleMaidCompat.isLoaded() && (conditionChair = conditionManager.getChair()) != null) {
            String str2 = conditionChair.doTest(livingEntity);
            if (StringUtils.isNoneBlank(str2)) {
                return IAnimationPredicate.playAnimationWithLoop(event, str2, ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        ConditionVehicle conditionVehicle = conditionManager.getVehicle();
        if (conditionVehicle != null) {
            String str3 = conditionVehicle.doTest(livingEntity);
            if (StringUtils.isNoneBlank(str3)) {
                return IAnimationPredicate.playAnimationWithLoop(event, str3, ILoopType.EDefaultLoopTypes.LOOP);
            }
        }
        if (vehicle instanceof Pig) {
            return IAnimationPredicate.playAnimationWithLoop(event, "ride_pig", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (vehicle instanceof Saddleable) {
            return IAnimationPredicate.playAnimationWithLoop(event, "ride", ILoopType.EDefaultLoopTypes.LOOP);
        }
        if (vehicle instanceof Boat) {
            return IAnimationPredicate.playAnimationWithLoop(event, "boat", ILoopType.EDefaultLoopTypes.LOOP);
        }
        boolean z = (livingEntity instanceof Player var8) && CarryOnCompat.isPlayerCarrying(var8);
        boolean z2 = TouhouLittleMaidCompat.isMaidEntity(livingEntity) && (livingEntity.getVehicle() instanceof Player);
        if (z || z2) {
            return IAnimationPredicate.playAnimationWithLoop(event, "carryon:princess", ILoopType.EDefaultLoopTypes.LOOP);
        }
        PlayState playState = TouhouLittleMaidCompat.handleMaidInteraction(event, livingEntity, vehicle);
        if (playState != null) {
            return playState;
        }
        return IAnimationPredicate.playAnimationWithLoop(event, "sit", ILoopType.EDefaultLoopTypes.LOOP);
    }
}