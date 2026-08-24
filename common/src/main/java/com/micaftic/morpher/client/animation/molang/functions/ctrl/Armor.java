package com.micaftic.morpher.client.animation.molang.functions.ctrl;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;

public class Armor extends LivingEntityFunction {

    private static final String PREFIX_ITEM_ID = "$";

    private static final String PREFIX_ITEM_TAG = "#";

    private static final String EMPTY_ITEM = "empty";

    public static Armor create() {
        return new Armor();
    }

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slotType = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0));
        if (slotType == null || !slotType.isArmor()) {
            return null;
        }
        String id = arguments.getAsString(context, 1);
        LivingEntity entity = context.entity().entity();
        if (StringUtils.isBlank(id)) {
            return 0;
        }
        ItemStack itemBySlot = entity.getItemBySlot(slotType);
        if (itemBySlot.isEmpty() && id.equals(EMPTY_ITEM)) {
            return 1;
        }
        String strSubstring = id.substring(1);
        if (id.startsWith(PREFIX_ITEM_ID)) {
            Identifier key = BuiltInRegistries.ITEM.getKey(itemBySlot.getItem());
            if (key == null) {
                return 0;
            }
            return strSubstring.equals(key.toString()) ? 1 : 0;
        }
        if (id.startsWith(PREFIX_ITEM_TAG)) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(strSubstring));
            return itemBySlot.is(tag) ? 1 : 0;
        }
        return 0;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;
    }
}