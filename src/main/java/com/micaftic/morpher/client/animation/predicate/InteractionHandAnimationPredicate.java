package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.condition.ConditionUse;
import com.micaftic.morpher.client.animation.condition.InnerClassify;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.geckolib3.core.builder.ILoopType;
import com.micaftic.morpher.geckolib3.core.event.predicate.AnimationEvent;
import com.micaftic.morpher.geckolib3.core.enums.PlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.apache.commons.lang3.StringUtils;
import com.micaftic.morpher.core.api.item.WeaponActionBridge;
import com.micaftic.morpher.core.api.item.WeaponActionState;
import com.micaftic.morpher.core.api.item.WeaponKind;

public class InteractionHandAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {

    @Override
    public PlayState predicate(AnimationEvent<LivingAnimatable<?>> event, ExpressionEvaluator<?> evaluator) {
        LivingEntity livingEntity = (LivingEntity) ((LivingAnimatable) event.getAnimatable()).getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return PlayState.STOP;
        }
        if (event.getAnimatable().getModelAssembly().getAnimationBundle().getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return PlayState.STOP;
        }
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        InteractionHand usedHand = InputStateKey.getUsedItemHand(livingEntity);
        if (InputStateKey.isUsingItem(livingEntity, usedHand) && !livingEntity.isSleeping()) {
            if (InputStateKey.getTicksUsingItem(livingEntity) == 1 && ((LivingAnimatable) event.getAnimatable()).getPositionTracker().markProcessed(2)) {
                event.getController().stopTransition();
            }
            ConditionManager conditionManager = event.getAnimatable().getModelConfig();
            HumanoidArm usedArm = usedHand == InteractionHand.MAIN_HAND ? livingEntity.getMainArm() : livingEntity.getMainArm().getOpposite();
            if (usedArm == HumanoidArm.RIGHT) {
                ConditionUse conditionUse = conditionManager.getUseMainhand();
                if (conditionUse != null) {
                    String str = conditionUse.doTest(livingEntity, usedHand);
                    if (StringUtils.isNoneBlank(str)) {
                        return playUseAnimation(event, livingEntity, usedHand, str, i);
                    }
                }
                return playUseAnimation(event, livingEntity, usedHand, "use_mainhand", i);
            }
            ConditionUse conditionUse2 = conditionManager.getUseOffhand();
            if (conditionUse2 != null) {
                String str2 = conditionUse2.doTest(livingEntity, usedHand);
                if (StringUtils.isNoneBlank(str2)) {
                    return playUseAnimation(event, livingEntity, usedHand, str2, i);
                }
            }
            return playUseAnimation(event, livingEntity, usedHand, "use_offhand", i);
        }
        return PlayState.STOP;
    }

    private PlayState playUseAnimation(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, InteractionHand usedHand, String animation, int version) {
        ItemStack itemStack = livingEntity.getItemInHand(usedHand);
        if (isLanceUse(itemStack)) {
            WeaponActionState state = WeaponActionBridge.get(livingEntity, event.getPartialTick());
            if (isLanceLike(state.kind())) {
                Animation targetAnimation = event.getAnimatable().getAnimation(animation);
                ResolvedAnimation resolved = pickFirstAvailable(event, LanceAnimationTiming.selectChargeNames(state.lance()));
                if (resolved != null) {
                    animation = resolved.name();
                    targetAnimation = resolved.animation();
                }
                applyKineticChargeAnimationTickOverride(event, itemStack, state.lance().useTicks(), targetAnimation);
            }
        }
        return IAnimationPredicate.playAnimationWithValid(event, animation, ILoopType.EDefaultLoopTypes.LOOP, version);
    }

    private ResolvedAnimation pickFirstAvailable(AnimationEvent<LivingAnimatable<?>> event, String[] names) {
        LivingAnimatable<?> animatable = event.getAnimatable();
        for (String name : names) {
            Animation anim = animatable.getAnimation(name);
            if (anim != null && !anim.isEmpty()) {
                return new ResolvedAnimation(name, anim);
            }
        }
        return null;
    }

    private void applyKineticChargeAnimationTickOverride(AnimationEvent<LivingAnimatable<?>> event, ItemStack itemStack, float useTicks, Animation animation) {
        float animationTick = LanceAnimationTiming.sampleKineticChargeAnimationTick(itemStack, useTicks, animation);
        if (animationTick >= 0.0f) {
            event.getController().setAnimationTickOverride(animationTick);
        }
    }

    private boolean isLanceUse(ItemStack itemStack) {
        return itemStack.getUseAnimation() == ItemUseAnimation.SPEAR
                && isLanceLike(InnerClassify.getWeaponKind(itemStack));
    }

    private boolean isLanceLike(WeaponKind kind) {
        return kind == WeaponKind.LANCE || kind == WeaponKind.SPEAR;
    }

    private record ResolvedAnimation(String name, Animation animation) {
    }
}
