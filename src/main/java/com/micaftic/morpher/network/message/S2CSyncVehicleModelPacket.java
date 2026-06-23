package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.VehicleCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.event.EntityJoinCallbackEvent;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.neoforged.api.distmarker.Dist;import net.neoforged.api.distmarker.OnlyIn;import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.api.network.PacketContext;

public class S2CSyncVehicleModelPacket {

    private final int entityId;

    private final VehicleModelCapability capability;

    private final Int2FloatOpenHashMap floatMap;

    public S2CSyncVehicleModelPacket(int entityId, VehicleModelCapability capability, Int2FloatOpenHashMap floatMap) {
        this.entityId = entityId;
        this.capability = capability;
        this.floatMap = floatMap;
    }

    public S2CSyncVehicleModelPacket(int entityId, VehicleModelCapability capability) {
        this(entityId, capability, new Int2FloatOpenHashMap(0));
    }

    public static void encode(S2CSyncVehicleModelPacket message, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarInt(message.entityId);
        friendlyByteBuf.writeNbt(message.capability.serializeNBT());
    }

    public static S2CSyncVehicleModelPacket decode(FriendlyByteBuf buf) {
        int varInt = buf.readVarInt();
        CompoundTag nbt = buf.readNbt();
        VehicleModelCapability cap = new VehicleModelCapability();
        if (nbt != null) {
            cap.deserializeNBT(nbt);
        }
        Object2FloatOpenHashMap<String> objectMap = cap.getMolangVars();
        Int2FloatOpenHashMap floatMap = new Int2FloatOpenHashMap();
        objectMap.object2FloatEntrySet().fastForEach(entry -> floatMap.put(StringPool.computeIfAbsent(entry.getKey()), entry.getFloatValue()));
        return new S2CSyncVehicleModelPacket(varInt, cap, floatMap);
    }

    public static void handle(S2CSyncVehicleModelPacket message, PacketContext ctx) {
        if (ctx.isClientSide()) {
            EntityJoinCallbackEvent.addCallback(message.entityId, entity -> handleCapability(entity, message.capability, message.floatMap));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void handleCapability(Entity entity, VehicleModelCapability capability, Int2FloatOpenHashMap floatMap) {
        VehicleCapability.get(entity).ifPresent(vehicleCapability -> {
            vehicleCapability.setOwnerModelId(capability.getOwnerModelId());
            vehicleCapability.setFloatMap(floatMap);
        });
    }
}