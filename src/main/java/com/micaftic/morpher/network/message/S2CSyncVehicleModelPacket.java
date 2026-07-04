package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.network.ClientNetworkBridge;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
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
        ClientNetworkBridge.handle(ctx, "handleSyncVehicleModel", message);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public VehicleModelCapability getCapability() {
        return this.capability;
    }

    public Int2FloatOpenHashMap getFloatMap() {
        return this.floatMap;
    }
}
