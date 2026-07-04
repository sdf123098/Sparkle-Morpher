package com.micaftic.morpher.network.message;

import com.micaftic.morpher.network.ClientNetworkBridge;
import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

public class S2CExecuteMolangPacket {

    private final int[] entityIds;

    private final String expression;

    public S2CExecuteMolangPacket(int entityIds, String expression) {
        this.entityIds = new int[]{entityIds};
        this.expression = expression;
    }

    public S2CExecuteMolangPacket(int[] entityIds, String expression) {
        this.entityIds = entityIds;
        this.expression = expression;
    }

    public static void encode(S2CExecuteMolangPacket message, FriendlyByteBuf buf) {
        buf.writeVarIntArray(message.entityIds);
        buf.writeUtf(message.expression);
    }

    public static S2CExecuteMolangPacket decode(FriendlyByteBuf buf) {
        return new S2CExecuteMolangPacket(buf.readVarIntArray(), buf.readUtf());
    }

    public static void handle(S2CExecuteMolangPacket message, PacketContext ctx) {
        ClientNetworkBridge.handle(ctx, "handleExecuteMolang", message);
    }

    public int[] getEntityIds() {
        return this.entityIds;
    }

    public String getExpression() {
        return this.expression;
    }
}
