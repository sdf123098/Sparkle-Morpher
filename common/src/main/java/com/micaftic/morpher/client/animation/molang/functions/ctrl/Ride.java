package com.micaftic.morpher.client.animation.molang.functions.ctrl;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.geckolib3.core.molang.funciton.entity.LivingEntityFunction;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.apache.commons.lang3.StringUtils;

public class Ride extends LivingEntityFunction {

    private static final String PREFIX_ITEM_ID = "$";

    private static final String PREFIX_ITEM_TAG = "#";

    private static final String VEHICLE_KEY = "vehicle";

    private static final String PASSENGER_KEY = "passenger";

    private static final int MODE_VEHICLE = 0;

    private static final int MODE_PASSENGER = 1;

    public static Ride create() {
        return new Ride();
    }

    @Override
    public Object eval(ExecutionContext<IContext<LivingEntity>> context, ArgumentCollection arguments) {
        Entity firstPassenger;
        String type = arguments.getAsString(context, 0);
        String id = arguments.getAsString(context, 1);
        LivingEntity entity = context.entity().entity();
        if (StringUtils.isBlank(id)) {
            return 0;
        }
        if (VEHICLE_KEY.equals(type)) {
            firstPassenger = entity.getVehicle();
        } else if (PASSENGER_KEY.equals(type)) {
            firstPassenger = entity.getFirstPassenger();
        } else {
            return 0;
        }
        if (firstPassenger == null || !firstPassenger.isAlive()) {
            return 0;
        }
        String strSubstring = id.substring(1);
        EntityType<?> entityType = firstPassenger.getType();
        if (id.startsWith(PREFIX_ITEM_ID)) {
            Identifier expected = Identifier.tryParse(strSubstring);
            if (expected == null) {
                return 0;
            }
            Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            return expected.equals(key) ? 1 : 0;
        }
        if (id.startsWith(PREFIX_ITEM_TAG)) {
            Identifier tagId = Identifier.tryParse(strSubstring);
            if (tagId == null) {
                return 0;
            }
            return entityType.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tagId)) ? 1 : 0;
        }
        return 0;
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 2 || size == 3;
    }
}
