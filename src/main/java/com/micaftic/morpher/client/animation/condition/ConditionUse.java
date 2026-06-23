package com.micaftic.morpher.client.animation.condition;

import com.micaftic.morpher.util.EquipmentUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class ConditionUse {

    private static final String EMPTY = "";

    private static final String MC_SPEAR_ANIMATION = "lance";

    private final int preSize;

    private final String idPre;

    private final String tagPre;

    private final String extraPre;

    private final ObjectOpenHashSet<Identifier> idTest = new ObjectOpenHashSet<>();

    private final ReferenceArrayList<TagKey<Item>> tagTest = new ReferenceArrayList<>();

    private final ObjectOpenHashSet<ItemUseAnimation> extraTes = new ObjectOpenHashSet<>();

    private final ObjectOpenHashSet<String> innerTest = new ObjectOpenHashSet<>();

    public ConditionUse(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.idPre = "use_mainhand$";
            this.tagPre = "use_mainhand#";
            this.extraPre = "use_mainhand:";
            this.preSize = 13;
            return;
        }
        this.idPre = "use_offhand$";
        this.tagPre = "use_offhand#";
        this.extraPre = "use_offhand:";
        this.preSize = 12;
    }

    public void addTest(String name) {
        if (name.length() <= this.preSize) {
            return;
        }
        String strSubstring = name.substring(this.preSize);
        Identifier id = ConditionResourceUtil.parseIdentifier(strSubstring);
        if (name.startsWith(this.idPre) && id != null) {
            this.idTest.add(id);
        }
        if (name.startsWith(this.tagPre) && id != null) {
            this.tagTest.add(TagKey.create(Registries.ITEM, id));
        }
        if (!name.startsWith(this.extraPre) || strSubstring.equals(ItemUseAnimation.NONE.name().toLowerCase(Locale.US))) {
            return;
        }
        Optional<ItemUseAnimation> optional = EquipmentUtil.getUseAnimByName(strSubstring);
        Objects.requireNonNull(this.extraTes);
        optional.ifPresent(extraTes::add);
        this.innerTest.add(name);
    }

    public String doTest(LivingEntity entity, InteractionHand hand) {
        if (entity.getItemInHand(hand).isEmpty()) {
            return EMPTY;
        }
        String result = doIdTest(entity, hand);
        if (result.isEmpty()) {
            result = doTagTest(entity, hand);
            if (result.isEmpty()) {
                return doExtraTest(entity, hand);
            }
            return result;
        }
        return result;
    }

    private String doIdTest(LivingEntity livingEntity, InteractionHand interactionHand) {
        if (this.idTest.isEmpty()) {
            return EMPTY;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(livingEntity.getItemInHand(interactionHand).getItem());
        if (this.idTest.contains(key)) {
            return this.idPre + key;
        }
        return EMPTY;
    }

    private String doTagTest(LivingEntity livingEntity, InteractionHand interactionHand) {
        if (this.tagTest.isEmpty()) {
            return EMPTY;
        }
        ItemStack itemInHand = livingEntity.getItemInHand(interactionHand);
        Stream<TagKey<Item>> stream = this.tagTest.stream();
        Objects.requireNonNull(itemInHand);
        return stream.filter(itemInHand::is).findFirst().map(tagKey -> this.tagPre + tagKey.location()).orElse(EMPTY);
    }

    private String doExtraTest(LivingEntity entity, InteractionHand hand) {
        if (this.extraTes.isEmpty() && this.innerTest.isEmpty()) {
            return EMPTY;
        }
        ItemUseAnimation anim = entity.getItemInHand(hand).getUseAnimation();
        if (anim == ItemUseAnimation.TRIDENT) {
            if (this.extraTes.contains(anim)) {
                return this.extraPre + anim.name().toLowerCase(Locale.US);
            }
            String legacyTridentUse = this.extraPre + ItemUseAnimation.SPEAR.name().toLowerCase(Locale.US);
            if (this.innerTest.contains(legacyTridentUse)) {
                return legacyTridentUse;
            }
            return EMPTY;
        }
        if (anim == ItemUseAnimation.SPEAR) {
            String mcSpearUse = this.extraPre + MC_SPEAR_ANIMATION;
            if (this.innerTest.contains(mcSpearUse)) {
                return mcSpearUse;
            }
            return EMPTY;
        }
        String innerName = InnerClassify.doClassifyTest(this.extraPre, entity, hand);
        if (StringUtils.isNotBlank(innerName) && this.innerTest.contains(innerName)) {
            return innerName;
        }
        String legacyInnerName = InnerClassify.doLegacyClassifyTest(this.extraPre, entity, hand);
        if (StringUtils.isNotBlank(legacyInnerName) && this.innerTest.contains(legacyInnerName)) {
            return legacyInnerName;
        }
        if (this.extraTes.contains(anim)) {
            return this.extraPre + anim.name().toLowerCase(Locale.US);
        }
        return EMPTY;
    }
}
