package com.micaftic.morpher.client.animation.condition;

import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.util.ItemTagsConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import com.micaftic.morpher.core.api.item.WeaponKind;

public class InnerClassify {

    private static final String EMPTY = "";
    private static final String SHOVEL = "shovel";
    private static final String LEGACY_SPADE = "spade";

    public static String doClassifyTest(String str, LivingEntity livingEntity, InteractionHand interactionHand) {
        String itemType = getItemType(livingEntity.getItemInHand(interactionHand));
        if (!itemType.equals("")) {
            return str + itemType;
        }
        return "";
    }

    public static String doLegacyClassifyTest(String str, LivingEntity livingEntity, InteractionHand interactionHand) {
        String alias = getLegacyAlias(getItemType(livingEntity.getItemInHand(interactionHand)));
        if (!alias.equals("")) {
            return str + alias;
        }
        return "";
    }

    public static String getLegacyAlias(String itemType) {
        if (SHOVEL.equals(itemType)) {
            return LEGACY_SPADE;
        }
        return EMPTY;
    }

    public static String getItemType(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return EMPTY;
        }
        return switch (getWeaponKind(itemStack)) {
            case TRIDENT -> "spear";
            case SPEAR -> "spear";
            case LANCE -> "lance";
            case MACE -> "mace";
            case NONE -> getNonWeaponItemType(itemStack);
        };
    }

    public static String getImportedItemType(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return EMPTY;
        }
        return switch (getWeaponKind(itemStack)) {
            case TRIDENT -> "trident";
            case SPEAR -> "spear";
            case LANCE -> "lance";
            case MACE -> "mace";
            case NONE -> getNonWeaponItemType(itemStack);
        };
    }

    public static WeaponKind getWeaponKind(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return WeaponKind.NONE;
        }
        Item item = itemStack.getItem();
        if (item == Items.TRIDENT || itemStack.is(ItemTagsConstants.TRIDENTS)) {
            return WeaponKind.TRIDENT;
        }
        if (isVanillaSpear(itemStack)) {
            return WeaponKind.SPEAR;
        }
        if (itemStack.is(ItemTagsConstants.PIKE)) {
            return WeaponKind.LANCE;
        }
        if (item == Items.MACE || itemStack.is(ItemTagsConstants.MACE)) {
            return WeaponKind.MACE;
        }
        return WeaponKind.NONE;
    }

    private static boolean isVanillaSpear(ItemStack itemStack) {
        if (itemStack.getUseAnimation() != ItemUseAnimation.SPEAR) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return id != null && "minecraft".equals(id.getNamespace()) && id.getPath().endsWith("_spear");
    }

    private static String getNonWeaponItemType(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (SlashBladeCompat.isSlashBladeItem(itemStack)) {
            return "slashblade";
        }
        if (itemStack.is(ItemTags.SWORDS) || itemStack.is(ItemTagsConstants.SWORDS)) {
            return "sword";
        }
        if (TouhouLittleMaidCompat.isMaidItem(item)) {
            return "gohei";
        }
        if (itemStack.is(ItemTags.AXES) || itemStack.is(ItemTagsConstants.AXES)) {
            return "axe";
        }
        if (itemStack.is(ItemTags.PICKAXES) || itemStack.is(ItemTagsConstants.PICKAXES)) {
            return "pickaxe";
        }
        if (itemStack.is(ItemTags.SHOVELS) || itemStack.is(ItemTagsConstants.SHOVELS)) {
            return SHOVEL;
        }
        if (itemStack.is(ItemTags.HOES) || itemStack.is(ItemTagsConstants.HOES)) {
            return "hoe";
        }
        if (item == Items.SHIELD || itemStack.is(ItemTagsConstants.SHIELDS)) {
            return "shield";
        }
        if (item == Items.CROSSBOW || itemStack.is(ItemTagsConstants.CROSSBOWS)) {
            return "crossbow";
        }
        if (item == Items.BOW || itemStack.is(ItemTagsConstants.BOWS)) {
            return "bow";
        }
        if (item == Items.FISHING_ROD || itemStack.is(ItemTagsConstants.FISHING_RODS)) {
            return "fishing_rod";
        }
        if (item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || itemStack.is(ItemTagsConstants.THROWABLE_POTION)) {
            return "throwable_potion";
        }
        return EMPTY;
    }
}
