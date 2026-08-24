package com.micaftic.morpher.geckolib3.core.molang.builtin.query;

import net.minecraft.core.registries.Registries;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.util.MolangUtils;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.EntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class RelativeBlockHasAllTags extends EntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<Entity>> context, ArgumentCollection arguments) {
        BlockState block = MolangUtils.getRelativeBlockState(context, arguments);
        if (block == null) {
            return null;
        }
        for (int i = 3; i < arguments.size(); i++) {
            ResourceLocation tagId = arguments.getResourceLocation(context, i);
            if (tagId == null) {
                return null;
            }

            TagKey<Block> tag = TagKey.create(Registries.BLOCK, tagId);
            if (!block.is(tag)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size >= 4;
    }
}
