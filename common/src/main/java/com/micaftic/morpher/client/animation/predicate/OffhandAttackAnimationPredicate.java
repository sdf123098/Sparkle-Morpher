package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionPassenger;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

public class OffhandAttackAnimationPredicate implements IAnimationPredicate<LivingAnimatable<?>> {
    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        LivingEntity livingEntity = event.getAnimatable().getEntity();
        if (livingEntity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        Entity firstPassenger = livingEntity.getFirstPassenger();
        if (firstPassenger == null || !firstPassenger.isAlive()) {
            return HostPlayState.Decision.STOP;
        }
        ConditionPassenger conditionPassenger = event.getAnimatable().getModelConfig().getPassenger();
        if (conditionPassenger != null) {
            String str = conditionPassenger.doTest(livingEntity);
            if (StringUtils.isNoneBlank(str)) {
                return IAnimationPredicate.playAnimationWithLoop(event, str, CodedLoopMode.LOOP);
            }
        }
        return HostPlayState.Decision.STOP;
    }
}
