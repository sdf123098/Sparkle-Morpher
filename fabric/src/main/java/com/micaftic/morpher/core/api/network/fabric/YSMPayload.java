package com.micaftic.morpher.core.api.network.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record YSMPayload(FriendlyByteBuf buf) implements CustomPacketPayload {

    public static CustomPacketPayload.Type<YSMPayload> TYPE;

    static final StreamCodec<FriendlyByteBuf, YSMPayload> CODEC = StreamCodec.of(
            (target, payload) -> {
                FriendlyByteBuf src = payload.buf;
                src.resetReaderIndex();
                target.writeBytes(src, src.readerIndex(), src.readableBytes());
            },
            source -> {
                FriendlyByteBuf copy = new FriendlyByteBuf(Unpooled.buffer(source.readableBytes()));
                source.readBytes(copy);
                return new YSMPayload(copy);
            }
    );

    static void initType(Identifier channelId) {
        TYPE = new CustomPacketPayload.Type<>(channelId);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
