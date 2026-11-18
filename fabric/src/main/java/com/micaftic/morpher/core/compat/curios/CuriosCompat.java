package com.micaftic.morpher.core.compat.curios;

import com.micaftic.morpher.client.core.HostMolangSink;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.List;

public final class CuriosCompat {

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.isLoaded();
    }

    public static boolean hasItemInSlot(LivingEntity livingEntity, String slot, ReferenceOpenHashSet<Item> set) {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.hasItemInSlot(livingEntity, slot, set);
    }

    public static boolean hasTaggedItemInSlot(LivingEntity livingEntity, String slot, List<TagKey<Item>> list) {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.hasTaggedItemInSlot(livingEntity, slot, list);
    }

    public static boolean hasNoTaggedItemInSlot(LivingEntity entity, String slot, List<TagKey<Item>> list) {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.hasNoTaggedItemInSlot(entity, slot, list);
    }

    public static void registerCuriosItems(HostMolangSink binding) {
        com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.registerCuriosItems(binding);
    }
}
