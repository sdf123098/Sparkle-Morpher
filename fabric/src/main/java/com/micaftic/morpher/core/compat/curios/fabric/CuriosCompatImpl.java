package com.micaftic.morpher.core.compat.curios.fabric;

import com.micaftic.morpher.geckolib3.core.molang.binding.ContextBinding;
import com.micaftic.morpher.molang.runtime.Function;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.List;

public final class CuriosCompatImpl {

    private CuriosCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean hasItemInSlot(LivingEntity livingEntity, String str, ReferenceOpenHashSet<Item> set) {
        return false;
    }

    public static boolean hasTaggedItemInSlot(LivingEntity livingEntity, String str, List<TagKey<Item>> list) {
        return false;
    }

    public static boolean hasNoTaggedItemInSlot(LivingEntity entity, String str, List<TagKey<Item>> list) {
        return false;
    }

    public static void registerCuriosItems(ContextBinding binding) {
        binding.function("has_any_curios", Function.NOOP);
        binding.function("has_any_curios_with_all_tags", Function.NOOP);
        binding.function("has_any_curios_with_any_tag", Function.NOOP);
        binding.livingEntityVar("dump_curios", context -> {
            context.logWarning("Curios not installed.");
            return null;
        });
    }
}
