package com.micaftic.morpher.client.animation.condition;

import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.micaftic.morpher.core.compat.slashblade.SlashBladeCompat;
import com.micaftic.morpher.util.ItemTagsConstants;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
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
        if ((item instanceof TridentItem) || itemStack.is(ItemTagsConstants.TRIDENTS)) {
            return WeaponKind.TRIDENT;
        }
        if (itemStack.is(ItemTagsConstants.LANCES)) {
            return WeaponKind.LANCE;
        }
        if (item == Items.MACE || itemStack.is(ItemTagsConstants.MACE)) {
            return WeaponKind.MACE;
        }
        return WeaponKind.NONE;
    }

    private static String getNonWeaponItemType(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (SlashBladeCompat.isSlashBladeItem(itemStack)) {
            return "slashblade";
        }
        if ((item instanceof SwordItem) || itemStack.is(ItemTags.SWORDS) || itemStack.is(ItemTagsConstants.SWORDS)) {
            return "sword";
        }
        if (TouhouLittleMaidCompat.isMaidItem(item)) {
            return "gohei";
        }
        if ((item instanceof AxeItem) || itemStack.is(ItemTags.AXES) || itemStack.is(ItemTagsConstants.AXES)) {
            return "axe";
        }
        if ((item instanceof PickaxeItem) || itemStack.is(ItemTags.PICKAXES) || itemStack.is(ItemTagsConstants.PICKAXES)) {
            return "pickaxe";
        }
        if ((item instanceof ShovelItem) || itemStack.is(ItemTags.SHOVELS) || itemStack.is(ItemTagsConstants.SHOVELS)) {
            return SHOVEL;
        }
        if ((item instanceof HoeItem) || itemStack.is(ItemTags.HOES) || itemStack.is(ItemTagsConstants.HOES)) {
            return "hoe";
        }
        if ((item instanceof ShieldItem) || itemStack.is(ItemTagsConstants.SHIELDS)) {
            return "shield";
        }
        if ((item instanceof CrossbowItem) || itemStack.is(ItemTagsConstants.CROSSBOWS)) {
            return "crossbow";
        }
        if ((item instanceof BowItem) || itemStack.is(ItemTagsConstants.BOWS)) {
            return "bow";
        }
        if ((item instanceof FishingRodItem) || itemStack.is(ItemTagsConstants.FISHING_RODS)) {
            return "fishing_rod";
        }
        if ((item instanceof ThrowablePotionItem) || itemStack.is(ItemTagsConstants.THROWABLE_POTION)) {
            return "throwable_potion";
        }
        return EMPTY;
    }
}
