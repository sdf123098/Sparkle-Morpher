package com.micaftic.morpher.client.model;

import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;

public interface AnimationDataProvider<T> {
    Object2ReferenceMap<String, AnimationController> getAnimationEntries(T t, ModelResourceBundle resourceBundle);

    Object2ReferenceMap<String, Animation> getAnimations(T t, ModelResourceBundle resourceBundle);

    ConditionArmor getConditionArmor(T t, ModelResourceBundle resourceBundle);
}