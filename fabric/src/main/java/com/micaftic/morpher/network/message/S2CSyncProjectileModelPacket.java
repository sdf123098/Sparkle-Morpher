package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.ProjectileCapability;
import com.micaftic.morpher.capability.ProjectileModelCapability;
import com.micaftic.morpher.event.EntityJoinCallbackEvent;
import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.api.network.PacketContext;

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
        if (ctx.isClientSide()) {
            EntityJoinCallbackEvent.addCallback(message.entityId, entity -> handleCapability(entity, message.capability, message.floatMap));
        }
    }

    @Environment(EnvType.CLIENT)
    public static void handleCapability(Entity entity, ProjectileModelCapability capability, Int2FloatOpenHashMap floatMap) {
        ProjectileCapability.get(entity).ifPresent(projectileCapability -> {
            projectileCapability.updateModelId(capability.getOwnerModelId());
            projectileCapability.setFloatProperties(floatMap);
        });
    }
}