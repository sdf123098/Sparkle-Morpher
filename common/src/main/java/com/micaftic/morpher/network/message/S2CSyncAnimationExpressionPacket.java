package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.PlayerCapability;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

public class S2CSyncAnimationExpressionPacket {

    private final int entityId;

    private final FloatArrayList floatData;

    public S2CSyncAnimationExpressionPacket(int entityId, FloatArrayList floatData) {
        this.entityId = entityId;
        this.floatData = floatData;
    }

    public static void encode(S2CSyncAnimationExpressionPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(message.entityId);
        buf.writeByte(message.floatData.size());
        for (Float floatDatum : message.floatData) {
            buf.writeFloat(floatDatum);
        }
    }

    public static S2CSyncAnimationExpressionPacket decode(FriendlyByteBuf buf) {
        int varInt = buf.readVarInt();
        int count = buf.readByte();
        FloatArrayList floatArrayList = new FloatArrayList(count);
        for (int i = 0; i < count; i++) {
            floatArrayList.add(buf.readFloat());
        }
        return new S2CSyncAnimationExpressionPacket(varInt, floatArrayList);
    }

    public static void handleCapability(S2CSyncAnimationExpressionPacket message, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> {
                PlayerCapability.get(Minecraft.getInstance().level.getEntity(message.entityId)).ifPresent(cap -> cap.executeAnimationExpression(message.floatData));
            });
        }
    }
}