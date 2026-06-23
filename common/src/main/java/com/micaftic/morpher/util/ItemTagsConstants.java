package com.micaftic.morpher.util;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class ItemTagsConstants {

    public static final TagKey<Item> AXES = createTag("axes");

    public static final TagKey<Item> HOES = createTag("hoes");

    public static final TagKey<Item> PICKAXES = createTag("pickaxes");

    public static final TagKey<Item> SHOVELS = createTag("shovels");

    public static final TagKey<Item> SWORDS = createTag("swords");

    public static final TagKey<Item> THROWABLE_POTION = createTag("throwable_potion");

    public static final TagKey<Item> BOWS = createTag("bows");

    public static final TagKey<Item> CROSSBOWS = createTag("crossbows");

    public static final TagKey<Item> FISHING_RODS = createTag("fishing_rods");

    public static final TagKey<Item> SHIELDS = createTag("shields");

    public static final TagKey<Item> TRIDENTS = createTag("tridents");

    public static final TagKey<Item> PIKE = createTag("pike");

    public static final TagKey<Item> MACE = createTag("mace");

    public static final TagKey<Item> SLASHBLADE = createTag("slashblade");

    private static TagKey<Item> createTag(String str) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, str));
    }
}
