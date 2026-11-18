package com.micaftic.morpher.core.compat.curios;

import com.micaftic.morpher.client.core.HostMolangSink;
import dev.spm.core.scene.MolangValue;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

import java.util.List;

public final class CuriosCompat {

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean hasItemInSlot(LivingEntity livingEntity, String slot, ReferenceOpenHashSet<Item> set) {
        return false;
    }

    public static boolean hasTaggedItemInSlot(LivingEntity livingEntity, String slot, List<TagKey<Item>> list) {
        return false;
    }

    public static boolean hasNoTaggedItemInSlot(LivingEntity entity, String slot, List<TagKey<Item>> list) {
        return false;
    }

    public static void registerCuriosItems(HostMolangSink binding) {
        binding.function("has_any_curios", (event, args) -> MolangValue.bool(false));
        binding.function("has_any_curios_with_all_tags", (event, args) -> MolangValue.bool(false));
        binding.function("has_any_curios_with_any_tag", (event, args) -> MolangValue.bool(false));
        binding.var("dump_curios", event -> MolangValue.missing());
    }
}
