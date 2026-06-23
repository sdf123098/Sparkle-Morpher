package com.micaftic.morpher.client.animation.molang.functions.ysm.curios;

import com.micaftic.morpher.core.compat.curios.CuriosCompat;
import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.util.ThreadLocalItemTagSets;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.apache.commons.lang3.StringUtils;

public class HasAnyCurios extends LivingEntityFunction {
    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        String type = arguments.getAsString(context, 0);
        if (StringUtils.isEmpty(type)) {
            return null;
        }
        ReferenceOpenHashSet<Item> referenceOpenHashSet = ThreadLocalItemTagSets.ITEM_SET.get();
        referenceOpenHashSet.clear();
        for (int i = 1; i < arguments.size(); i++) {
            Identifier name = arguments.getResourceLocation(context, i);
            if (name == null) {
                return null;
            }
            BuiltInRegistries.ITEM.get(name).ifPresent(ref -> referenceOpenHashSet.add(ref.value()));
        }
        return CuriosCompat.hasItemInSlot(context.entity().entity(), type, referenceOpenHashSet);
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size > 0;
    }
}
