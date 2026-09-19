package com.micaftic.morpher.network.message;

import com.micaftic.morpher.fakeplayer.FakePlayerAppearanceService;
import com.micaftic.morpher.core.api.network.PacketContext;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Target-aware packet used only by the independent fake-player GUI. */
public final class C2SRequestFakePlayerModelPacket {

    private final UUID targetUuid;
    private final String modelId;
    private final String textureId;

    public C2SRequestFakePlayerModelPacket(UUID targetUuid, String modelId, String textureId) {
        this.targetUuid = targetUuid;
        this.modelId = modelId;
        this.textureId = textureId;
    }

    public static void encode(C2SRequestFakePlayerModelPacket message, FriendlyByteBuf buf) {
        buf.writeUUID(message.targetUuid);
        buf.writeUtf(message.modelId, 256);
        buf.writeUtf(message.textureId, 256);
    }

    public static C2SRequestFakePlayerModelPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestFakePlayerModelPacket(buf.readUUID(), buf.readUtf(256), buf.readUtf(256));
    }

    public static void handle(C2SRequestFakePlayerModelPacket message, PacketContext ctx) {
        if (!ctx.isServerSide()) {
            return;
        }
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                FakePlayerAppearanceService.apply(sender, message.targetUuid, message.modelId, message.textureId);
            }
        });
    }
}
