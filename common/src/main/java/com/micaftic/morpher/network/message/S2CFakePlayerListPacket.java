package com.micaftic.morpher.network.message;

import com.micaftic.morpher.core.api.network.PacketContext;
import com.micaftic.morpher.fakeplayer.FakePlayerListCache;
import com.micaftic.morpher.fakeplayer.FakePlayerListEntry;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-confirmed fake-player rows sent to the requesting client only. */
public final class S2CFakePlayerListPacket {

    private final List<FakePlayerListEntry> entries;

    public S2CFakePlayerListPacket(List<FakePlayerListEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static void encode(S2CFakePlayerListPacket message, FriendlyByteBuf buf) {
        buf.writeVarInt(Math.min(message.entries.size(), 128));
        for (FakePlayerListEntry entry : message.entries.subList(0, Math.min(message.entries.size(), 128))) {
            buf.writeUUID(entry.uuid());
            buf.writeUtf(entry.name(), 64);
            buf.writeUtf(entry.displayName(), 128);
            buf.writeUtf(entry.providerId(), 64);
            buf.writeUtf(entry.modelId(), 256);
            buf.writeUtf(entry.dimensionId(), 128);
            buf.writeBoolean(entry.online());
        }
    }

    public static S2CFakePlayerListPacket decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 128);
        List<FakePlayerListEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID uuid = buf.readUUID();
            String name = buf.readUtf(64);
            String displayName = buf.readUtf(128);
            String providerId = buf.readUtf(64);
            String modelId = buf.readUtf(256);
            String dimensionId = buf.readUtf(128);
            boolean online = buf.readBoolean();
            entries.add(new FakePlayerListEntry(uuid, name, displayName, providerId, modelId, dimensionId, online));
        }
        return new S2CFakePlayerListPacket(entries);
    }

    public static void handle(S2CFakePlayerListPacket message, PacketContext ctx) {
        if (ctx.isClientSide()) {
            ctx.enqueueWork(() -> FakePlayerListCache.replace(message.entries));
        }
    }
}
