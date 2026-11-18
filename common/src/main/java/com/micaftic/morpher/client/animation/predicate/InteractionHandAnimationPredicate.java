package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.condition.ConditionUse;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import dev.spm.core.scene.CodedLoopMode;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

public class InteractionHandAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        LivingEntity livingEntity = (LivingEntity) ((LivingAnimatable) event.getAnimatable()).getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        ModelAssembly modelAssembly = event.getAnimatable().getModelAssembly();
        PlayerModelBundle animationBundle = modelAssembly == null ? null : modelAssembly.getAnimationBundle();
        if (animationBundle == null || animationBundle.getActionProfile() == ModelActionProfile.VANILLA_HUMANOID) {
            return HostPlayState.Decision.STOP;
        }
        int i = event.getAnimatable().getModelAssembly().getModelData().getFormatVersion();
        if (livingEntity.isUsingItem() && !livingEntity.isSleeping()) {
            if (livingEntity.getTicksUsingItem() == 1 && ((LivingAnimatable) event.getAnimatable()).getPositionTracker().markProcessed(2)) {
                event.getController().stopTransition();
            }
            ConditionManager conditionManager = event.getAnimatable().getModelConfig();
            if (livingEntity.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                ConditionUse conditionUse = conditionManager.getUseMainhand();
                if (conditionUse != null) {
                    String str = conditionUse.doTest(livingEntity, InteractionHand.MAIN_HAND);
                    if (StringUtils.isNoneBlank(str)) {
                        return IAnimationPredicate.playAnimationWithValid(event, str, CodedLoopMode.LOOP, i);
                    }
                }
                return IAnimationPredicate.playAnimationWithValid(event, "use_mainhand", CodedLoopMode.LOOP, i);
            }
            ConditionUse conditionUse2 = conditionManager.getUseOffhand();
            if (conditionUse2 != null) {
                String str2 = conditionUse2.doTest(livingEntity, InteractionHand.OFF_HAND);
                if (StringUtils.isNoneBlank(str2)) {
                    return IAnimationPredicate.playAnimationWithValid(event, str2, CodedLoopMode.LOOP, i);
                }
            }
            return IAnimationPredicate.playAnimationWithValid(event, "use_offhand", CodedLoopMode.LOOP, i);
        }
        return HostPlayState.Decision.STOP;
    }
}
