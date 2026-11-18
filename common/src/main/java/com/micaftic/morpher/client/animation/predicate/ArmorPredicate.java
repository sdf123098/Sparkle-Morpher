package com.micaftic.morpher.client.animation.predicate;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.client.core.HostActionEvent;
import com.micaftic.morpher.client.core.HostPlayState;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.LivingAnimatable;
import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import dev.spm.core.scene.CodedLoopMode;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

public class ArmorPredicate implements IAnimationPredicate<LivingAnimatable<?>> {

    private final EquipmentSlot slot;

    public ArmorPredicate(EquipmentSlot slot) {
        this.slot = slot;
    }

    @Override
    public HostPlayState.Decision predicate(HostActionEvent<LivingAnimatable<?>> event) {
        LivingEntity entity = event.getAnimatable().getEntity();
        if (entity == null || (event.getAnimatable() instanceof IPreviewAnimatable)) {
            return HostPlayState.Decision.STOP;
        }
        if (CosmeticArmorHelper.getArmorItem(entity, this.slot).isEmpty()) {
            return HostPlayState.Decision.STOP;
        }
        ConditionArmor conditionArmor = event.getAnimatable().getModelConfig().getArmor();
        if (conditionArmor != null) {
            String name = conditionArmor.doTest(entity, this.slot);
            if (StringUtils.isNoneBlank(name)) {
                return IAnimationPredicate.playAnimationWithLoop(event, name, CodedLoopMode.LOOP);
            }
        }
        String defaultName = this.slot.getName() + ":default";
        if (event.getAnimatable().getAnimation(defaultName) != null) {
            return IAnimationPredicate.playAnimationWithLoop(event, defaultName, CodedLoopMode.LOOP);
        }
        return HostPlayState.Decision.STOP;
    }
}
