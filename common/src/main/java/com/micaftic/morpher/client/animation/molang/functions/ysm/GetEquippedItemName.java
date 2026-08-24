package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Bedrock {@code query.get_equipped_item_name(hand)}：返回主手/副手物品的注册名
 * （如 {@code crossbow}、{@code shield}），空手返回 {@code empty}。
 * 基岩版动画（attack.rotations / item_fixing 等）用它判断手持物品类型。
 */
public class GetEquippedItemName extends LivingEntityFunction {

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        if (arguments.size() < 1) {
            return "empty";
        }
        String hand = arguments.getAsString(context, 0);
        LivingEntity entity = context.entity().entity();
        ItemStack stack = "off_hand".equals(hand) ? entity.getOffhandItem() : entity.getMainHandItem();
        if (stack.isEmpty()) {
            return "empty";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }
}
