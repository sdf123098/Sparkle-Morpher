package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.network.ClientNetworkBridge;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class S2CSyncProjectileModelPacket {
    private final int entityId;
    private final ProjectileModelCapability capability;
    private final Int2FloatOpenHashMap floatMap;

    public S2CSyncProjectileModelPacket(int entityId, ProjectileModelCapability capability, Int2FloatOpenHashMap floatMap) {
        this.entityId = entityId;
        this.capability = capability;
        this.floatMap = floatMap;
    }

    public S2CSyncProjectileModelPacket(int entityId, ProjectileModelCapability capability) {
        this(entityId, capability, new Int2FloatOpenHashMap());
    }

    public static void encode(S2CSyncProjectileModelPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        buf.writeNbt(message.capability.serializeNBT());
    }

    public static S2CSyncProjectileModelPacket decode(FriendlyByteBuf buf) {
        int varInt = buf.readVarInt();
        CompoundTag nbt = buf.readNbt();
        ProjectileModelCapability cap = new ProjectileModelCapability();
        if (nbt != null) {
            cap.deserializeNBT(nbt);
        }
        Object2FloatOpenHashMap<String> objectMap = cap.getMolangVars();
        Int2FloatOpenHashMap floatMap = new Int2FloatOpenHashMap();
        objectMap.object2FloatEntrySet().fastForEach(entry -> floatMap.put(StringPool.computeIfAbsent(entry.getKey()), entry.getFloatValue()));
        return new S2CSyncProjectileModelPacket(varInt, cap, floatMap);
    }

    public static void handle(S2CSyncProjectileModelPacket message, PacketContext ctx) {
        ClientNetworkBridge.handle(ctx, "handleSyncProjectileModel", message);
    }

    public int getEntityId() {
        return this.entityId;
    }

    public ProjectileModelCapability getCapability() {
        return this.capability;
    }

    public Int2FloatOpenHashMap getFloatMap() {
        return this.floatMap;
    }
}
