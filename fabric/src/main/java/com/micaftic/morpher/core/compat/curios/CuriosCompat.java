package com.micaftic.morpher.core.compat.curios;

import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
import dev.architectury.injectables.annotations.ExpectPlatform;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.List;

public final class CuriosCompat {

    private CuriosCompat() {
    }

    @ExpectPlatform
    public static boolean isLoaded() {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean hasItemInSlot(LivingEntity livingEntity, String str, ReferenceOpenHashSet<Item> set) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean hasTaggedItemInSlot(LivingEntity livingEntity, String str, List<TagKey<Item>> list) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static boolean hasNoTaggedItemInSlot(LivingEntity entity, String str, List<TagKey<Item>> list) {
        throw new AssertionError();
    }

    @ExpectPlatform
    public static void registerCuriosItems(ContextBinding binding) {
        throw new AssertionError();
    }
}
