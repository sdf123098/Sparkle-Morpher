package com.micaftic.morpher.client.animation.molang.functions.ysm;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

public class DumpRelativeBlock extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        BlockState blockState;
        ResourceLocation key;
        if (!context.entity().isDebugMode() || (blockState = MolangUtils.getRelativeBlockState(context, arguments)) == null || (key = BuiltInRegistries.BLOCK.getKey(blockState.getBlock())) == null) {
            return null;
        }
        context.entity().logWarningComponent(Component.literal("Display ").append(ComponentUtils.copyOnClickText(blockState.getBlock().getName().getString(99))));
        context.entity().logWarningComponent(Component.literal("Name ").append(ComponentUtils.copyOnClickText(key.toString())));
        blockState.getTags().forEach(tagKey -> {
            context.entity().logWarningComponent(Component.literal("Tag ").append(ComponentUtils.copyOnClickText(tagKey.location().toString())));
        });
        return null;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 3;
    }
}
