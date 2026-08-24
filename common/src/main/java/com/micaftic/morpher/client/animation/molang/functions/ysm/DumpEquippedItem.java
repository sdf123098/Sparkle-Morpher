package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.core.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public class DumpEquippedItem extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        EquipmentSlot slot;
        ResourceLocation key;
        if (!context.entity().isDebugMode() || (slot = MolangUtils.parseSlotType(context.entity(), arguments.getAsString(context, 0))) == null) {
            return null;
        }
        ItemStack stack = CosmeticArmorHelper.getArmorItem(context.entity().entity(), slot);
        if (stack.isEmpty() || (key = BuiltInRegistries.ITEM.getKey(stack.getItem())) == null) {
            return null;
        }
        context.entity().logWarningComponent(Component.literal("Display ").append(ComponentUtils.copyOnClickText(stack.getItem().getName(stack).getString(99))));
        context.entity().logWarningComponent(Component.literal("Name ").append(ComponentUtils.copyOnClickText(key.toString())));
        stack.getTags().forEach(tagKey -> {
            context.entity().logWarningComponent(Component.literal("Tag ").append(ComponentUtils.copyOnClickText(tagKey.location().toString())));
        });
        ItemEnchantments enchantments = stack.getEnchantments();
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            int level = entry.getIntValue();
            context.entity().logWarningComponent(Component.literal("Enchantment: display ").append(ComponentUtils.copyOnClickText(Enchantment.getFullname(enchantment, level).getString(99))).append(Component.literal("  name ").append(ComponentUtils.copyOnClickText(enchantment.unwrapKey().map(k -> k.location().toString()).orElse("unknown")))));
        }
        return null;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
