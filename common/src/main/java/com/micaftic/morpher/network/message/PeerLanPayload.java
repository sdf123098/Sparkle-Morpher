package com.micaftic.morpher.network.message;

import com.micaftic.morpher.core.api.resource.ResourceApi;
import com.micaftic.morpher.peer.PeerLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Small optional handshake; model bytes remain on the bounded, pinned TLS transport. */
public record PeerLanPayload(boolean request, String invitation) implements CustomPacketPayload {
    public static final Type<PeerLanPayload> TYPE = new Type<>(ResourceApi.nativeId("sparkle_morpher", "peer_lan_bootstrap_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PeerLanPayload> CODEC = new StreamCodec<>() {
        public PeerLanPayload decode(RegistryFriendlyByteBuf buffer) {
            return new PeerLanPayload(buffer.readBoolean(), buffer.readUtf(PeerLimits.INVITE_BYTES));
        }
        public void encode(RegistryFriendlyByteBuf buffer, PeerLanPayload value) {
            buffer.writeBoolean(value.request); buffer.writeUtf(value.invitation, PeerLimits.INVITE_BYTES);
        }
    };
    public PeerLanPayload {
        if(invitation == null || invitation.length() > PeerLimits.INVITE_BYTES || request && !invitation.isEmpty())
            throw new IllegalArgumentException("Invalid LAN handshake");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
