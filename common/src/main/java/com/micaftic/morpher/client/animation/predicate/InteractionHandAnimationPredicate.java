package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionManager;
import com.micaftic.morpher.client.animation.condition.ConditionUse;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.client.input.InputStateKey;
import com.micaftic.morpher.client.model.ModelActionProfile;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import dev.spm.core.scene.CodedLoopMode;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
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
                        return playContinuousUseAnimation(event, str);
                    }
                }
                return playContinuousUseAnimation(event, "use_mainhand");
            }
            ConditionUse conditionUse2 = conditionManager.getUseOffhand();
            if (conditionUse2 != null) {
                String str2 = conditionUse2.doTest(livingEntity, usedHand);
                if (StringUtils.isNoneBlank(str2)) {
                    return playContinuousUseAnimation(event, str2);
                }
            }
            return playContinuousUseAnimation(event, "use_offhand");
        }
        return HostPlayState.Decision.STOP;
    }

    static HostPlayState.Decision playContinuousUseAnimation(HostActionEvent<? extends LivingAnimatable<?>> event, String animation) {
        return IAnimationPredicate.playContinuousAnimation(event, animation);
    }
}
