package com.micaftic.morpher.core.compat.curios;

import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
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

    public static boolean hasItemInSlot(LivingEntity livingEntity, String str, ReferenceOpenHashSet<Item> set) {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.hasItemInSlot(livingEntity, str, set);
    }

    public static boolean hasTaggedItemInSlot(LivingEntity livingEntity, String str, List<TagKey<Item>> list) {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.hasTaggedItemInSlot(livingEntity, str, list);
    }

    public static boolean hasNoTaggedItemInSlot(LivingEntity entity, String str, List<TagKey<Item>> list) {
        return com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.hasNoTaggedItemInSlot(entity, str, list);
    }

    public static void registerCuriosItems(ContextBinding binding) {
        com.micaftic.morpher.core.compat.curios.fabric.CuriosCompatImpl.registerCuriosItems(binding);
    }
}
