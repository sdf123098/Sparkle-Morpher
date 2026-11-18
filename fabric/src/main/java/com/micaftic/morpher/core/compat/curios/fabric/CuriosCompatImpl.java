package com.micaftic.morpher.core.compat.curios.fabric;

import com.micaftic.morpher.client.core.HostMolangSink;
import com.micaftic.morpher.core.api.resource.ResourceApi;
import com.micaftic.morpher.util.ThreadLocalItemTagSets;
import dev.spm.core.scene.MolangValue;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public final class CuriosCompatImpl {

    private CuriosCompatImpl() {
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
        binding.function("has_any_curios", (event, args) -> {
            if (!isLoaded() || !(event.getAnimatable().getEntity() instanceof LivingEntity living) || args.isEmpty()) {
                return MolangValue.bool(false);
            }
            String slot = textArg(args, 0);
            if (StringUtils.isEmpty(slot)) return MolangValue.bool(false);
            ReferenceOpenHashSet<Item> itemSet = ThreadLocalItemTagSets.ITEM_SET.get();
            itemSet.clear();
            for (int i = 1; i < args.size(); i++) {
                Identifier id = ResourceApi.parseNative(textArg(args, i));
                if (id == null) return MolangValue.bool(false);
                BuiltInRegistries.ITEM.get(id).ifPresent(ref -> itemSet.add(ref.value()));
            }
            return MolangValue.bool(hasItemInSlot(living, slot, itemSet));
        });
        binding.function("has_any_curios_with_all_tags", (event, args) -> {
            if (!isLoaded() || !(event.getAnimatable().getEntity() instanceof LivingEntity living) || args.size() < 2) {
                return MolangValue.bool(false);
            }
            String slot = textArg(args, 0);
            if (StringUtils.isEmpty(slot)) return MolangValue.bool(false);
            ReferenceArrayList<TagKey<Item>> tags = parseTags(args);
            return tags == null ? MolangValue.bool(false) : MolangValue.bool(hasNoTaggedItemInSlot(living, slot, tags));
        });
        binding.function("has_any_curios_with_any_tag", (event, args) -> {
            if (!isLoaded() || !(event.getAnimatable().getEntity() instanceof LivingEntity living) || args.size() < 2) {
                return MolangValue.bool(false);
            }
            String slot = textArg(args, 0);
            if (StringUtils.isEmpty(slot)) return MolangValue.bool(false);
            ReferenceArrayList<TagKey<Item>> tags = parseTags(args);
            return tags == null ? MolangValue.bool(false) : MolangValue.bool(hasTaggedItemInSlot(living, slot, tags));
        });
        binding.var("dump_curios", event -> MolangValue.missing());
    }

    private static ReferenceArrayList<TagKey<Item>> parseTags(List<MolangValue> args) {
        ReferenceArrayList<TagKey<Item>> list = ThreadLocalItemTagSets.TAG_KEY_LIST.get();
        list.size(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            Identifier tagId = ResourceApi.parseNative(textArg(args, i));
            if (tagId == null) {
                return null;
            }
            list.set(i - 1, TagKey.create(Registries.ITEM, tagId));
        }
        return list;
    }

    private static String textArg(List<MolangValue> args, int idx) {
        if (idx >= args.size()) return "";
        return args.get(idx) instanceof MolangValue.TextValue tv ? tv.value() : "";
    }
}
