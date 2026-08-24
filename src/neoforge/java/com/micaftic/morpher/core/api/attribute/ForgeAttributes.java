package com.micaftic.morpher.core.api.attribute;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

public final class ForgeAttributes {

    private ForgeAttributes() {
    }

    @Nullable
    public static Attribute blockReach() {
        return null;
    }

    @Nullable
    public static Attribute entityReach() {
        return null;
    }

    @Nullable
    public static Attribute swimSpeed() {
        return NeoForgeMod.SWIM_SPEED.value();
    }

    @Nullable
    public static Attribute entityGravity() {
        return null;
    }

    @Nullable
    public static Attribute stepHeightAddition() {
        return null;
    }

    @Nullable
    public static Attribute nametagDistance() {
        return NeoForgeMod.NAMETAG_DISTANCE.value();
    }

    public static double getValue(LivingEntity entity, @Nullable Attribute attribute, double defaultValue) {
        if (attribute == null) {
            return defaultValue;
        }
        return entity.getAttributeValue(BuiltInRegistries.ATTRIBUTE.wrapAsHolder(attribute));
    }
}
