package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;

public interface AnimationDataProvider<T> {
    Object2ReferenceMap<String, ControllerSpec> getAnimationEntries(T t, ModelResourceBundle resourceBundle);

    Object2ReferenceMap<String, ClipSpec> getAnimations(T t, ModelResourceBundle resourceBundle);

    ConditionArmor getConditionArmor(T t, ModelResourceBundle resourceBundle);
}