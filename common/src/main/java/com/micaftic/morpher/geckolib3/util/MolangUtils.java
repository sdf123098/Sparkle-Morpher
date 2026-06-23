package com.micaftic.morpher.geckolib3.util;

import com.micaftic.morpher.geckolib3.core.molang.context.IContext;
import com.micaftic.morpher.molang.parser.ast.Expression;
import com.micaftic.morpher.molang.parser.ast.StringExpression;
import com.micaftic.morpher.molang.runtime.ExecutionContext;
import com.micaftic.morpher.molang.runtime.Function;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

public class MolangUtils {

    private static final Map<String, EquipmentSlot> SLOT_MAP = new Object2ObjectOpenHashMap<>();

    static {
        SLOT_MAP.put("chest", EquipmentSlot.CHEST);
        SLOT_MAP.put("feet", EquipmentSlot.FEET);
        SLOT_MAP.put("head", EquipmentSlot.HEAD);
        SLOT_MAP.put("legs", EquipmentSlot.LEGS);
        SLOT_MAP.put("mainhand", EquipmentSlot.MAINHAND);
        SLOT_MAP.put("offhand", EquipmentSlot.OFFHAND);
    }

    public static float normalizeTime(long timestamp) {
        return ((float) (timestamp + 6000L) / 24000) % 1;
    }

    @Nullable
    public static BlockState getRelativeBlockState(ExecutionContext<IContext<Entity>> context, Function.ArgumentCollection args) {
        return getRelativeBlockStateAt(context, args, 0);
    }

    @Nullable
    public static BlockState getRelativeBlockStateAt(ExecutionContext<IContext<Entity>> context, Function.ArgumentCollection args, int i) {
        double deltaX = args.getAsDouble(context, i);
        double deltaY = args.getAsDouble(context, i + 1);
        double deltaZ = args.getAsDouble(context, i + 2);
        if (Math.abs(deltaX) > 5.0d || Math.abs(deltaY) > 5.0d || Math.abs(deltaZ) > 5.0d) {
            return null;
        }
        Entity entity = context.entity().entity();
        return entity.level().getBlockState(new BlockPos((int) Math.round((entity.getX() + deltaX) - 0.5d), (int) Math.round((entity.getY() + deltaY) - 0.5d), (int) Math.round((entity.getZ() + deltaZ) - 0.5d)));
    }

    @Nullable
    public static EquipmentSlot parseSlotType(IContext<?> context, String value) {
        if (value == null) {
            return null;
        }
        EquipmentSlot equipmentSlot = SLOT_MAP.get(value.toLowerCase(Locale.ENGLISH));
        if (equipmentSlot == null) {
            context.logWarning("Illegal slot type: %s.", value);
        }
        return equipmentSlot;
    }

    @Nullable
    public static EquipmentSlot parseSlotType(ExecutionContext<? extends IContext<?>> ctx, Function.ArgumentCollection args, int index) {
        Expression expr = args.getExpression(index);
        if (expr instanceof StringExpression se) {
            if (se.isSlotResolved()) {
                return se.getCachedSlot();
            }
            String name = se.getName();
            EquipmentSlot slot = SLOT_MAP.get(name.toLowerCase(Locale.ENGLISH));
            if (slot == null) {
                ctx.entity().logWarning("Illegal slot type: %s.", name);
            }
            se.setCachedSlot(slot);
            return slot;
        }
        return parseSlotType(ctx.entity(), args.getAsString(ctx, index));
    }
}