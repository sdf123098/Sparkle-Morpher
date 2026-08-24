package com.micaftic.morpher.core.api.network.neoforge;

import net.minecraft.network.FriendlyByteBuf;
import com.micaftic.morpher.core.api.network.PacketContext;

import java.util.function.BiConsumer;
import java.util.function.Function;

record Codec<T>(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, PacketContext> handler) {
    void encode(Object packet, FriendlyByteBuf buf) {
        encoder.accept(type.cast(packet), buf);
    }
    void dispatch(FriendlyByteBuf buf, PacketContext ctx) {
        handler.accept(decoder.apply(buf), ctx);
    }
}
