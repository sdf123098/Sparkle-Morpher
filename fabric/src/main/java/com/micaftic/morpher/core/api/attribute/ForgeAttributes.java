package com.micaftic.morpher.core.api.attribute;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

public final class ForgeAttributes {

    private ForgeAttributes() {
    }

    @Nullable
    public static Attribute blockReach() {
        return com.micaftic.morpher.core.api.attribute.fabric.ForgeAttributesImpl.blockReach();
    }

    @Nullable
    public static Attribute entityReach() {
        return com.micaftic.morpher.core.api.attribute.fabric.ForgeAttributesImpl.entityReach();
    }

    @Nullable
    public static Attribute swimSpeed() {
        return com.micaftic.morpher.core.api.attribute.fabric.ForgeAttributesImpl.swimSpeed();
    }

    @Nullable
    public static Attribute entityGravity() {
        return com.micaftic.morpher.core.api.attribute.fabric.ForgeAttributesImpl.entityGravity();
    }

    @Nullable
    public static Attribute stepHeightAddition() {
        return com.micaftic.morpher.core.api.attribute.fabric.ForgeAttributesImpl.stepHeightAddition();
    }

    @Nullable
    public static Attribute nametagDistance() {
        return com.micaftic.morpher.core.api.attribute.fabric.ForgeAttributesImpl.nametagDistance();
    }

    public static double getValue(LivingEntity entity, @Nullable Attribute attribute, double defaultValue) {
        if (attribute == null) {
            return defaultValue;
        }
        return entity.getAttributeValue(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
    }
}
