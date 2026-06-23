package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.PlayerCapability;
import com.micaftic.morpher.client.ClientModelManager;
import com.micaftic.morpher.event.EntityJoinCallbackEvent;
import com.micaftic.morpher.util.NetworkOnlineDebugLog;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.api.network.PacketContext;

public class S2CSetModelAndTexturePacket {

    private final int entityId;

    private final String modelId;

    private final String textureId;

    private final boolean disabled;

    private final S2CSyncPlayerStatePacket entityModelSync;

    public S2CSetModelAndTexturePacket(int entityId, String modelId, String textureId, boolean disabled, S2CSyncPlayerStatePacket playerState) {
        this.entityId = entityId;
        this.modelId = modelId;
        this.textureId = textureId;
        this.entityModelSync = playerState;
        this.disabled = disabled;
    }

    public static void encode(S2CSetModelAndTexturePacket other, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeVarInt(other.entityId);
        friendlyByteBuf.writeUtf(other.modelId);
        friendlyByteBuf.writeUtf(other.textureId);
        friendlyByteBuf.writeBoolean(other.disabled);
        S2CSyncPlayerStatePacket.encode(other.entityModelSync, friendlyByteBuf);
    }

    public static S2CSetModelAndTexturePacket decode(FriendlyByteBuf friendlyByteBuf) {
        return new S2CSetModelAndTexturePacket(friendlyByteBuf.readVarInt(), friendlyByteBuf.readUtf(), friendlyByteBuf.readUtf(), friendlyByteBuf.readBoolean(), S2CSyncPlayerStatePacket.decode(friendlyByteBuf));
    }

    public static void handle(S2CSetModelAndTexturePacket other, PacketContext ctx) {
        if (ctx.isClientSide()) {
            NetworkOnlineDebugLog.info("RECEIVED S2CSetModelAndTexture: entityId={} modelId={} texture={} disabled={}",
                    other.entityId, other.modelId, other.textureId, other.disabled);
            EntityJoinCallbackEvent.addCallback(other.entityId, entity -> applyOnClient(entity, other));
        }
    }

    @Environment(EnvType.CLIENT)
    public static void applyOnClient(Entity entity, S2CSetModelAndTexturePacket other) {
        NetworkOnlineDebugLog.info("applyOnClient: entity={} modelId={} texture={}",
                entity.getName().getString(), other.modelId, other.textureId);
        PlayerCapability.get(entity).ifPresent(cap -> {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            boolean keepLocalOnlyModel = entity == localPlayer && ClientModelManager.isSelectedLocalOnlyModel(cap.getModelId());
            if (!keepLocalOnlyModel) {
                NetworkOnlineDebugLog.info("applyOnClient: APPLYING modelId={}", other.modelId);
                cap.initModelWithTexture(other.modelId, other.textureId);
            } else {
                NetworkOnlineDebugLog.info("applyOnClient: SKIPPED (local-only model)");
            }
            cap.setForceDisabled(other.disabled);
            S2CSyncPlayerStatePacket.handleCapability(entity, other.entityModelSync);
        });
    }
}
