package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.event.CapabilityEvent;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.NetworkOnlineDebugLog;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import com.micaftic.morpher.util.PlayerStarModelsStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.micaftic.morpher.core.api.network.PacketContext;

public class C2SVersionCheckPacket {

    private final String version;

    public C2SVersionCheckPacket() {
        this(NetworkHandler.VERSION);
    }

    public C2SVersionCheckPacket(String version) {
        this.version = version;
    }

    public static C2SVersionCheckPacket decode(FriendlyByteBuf buf) {
        return new C2SVersionCheckPacket(buf.readUtf());
    }

    public static void encode(C2SVersionCheckPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.version);
    }

    public static void handle(C2SVersionCheckPacket message, PacketContext ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender != null && NetworkHandler.setChannelVersion(ctx.getConnection(), message.version)) {
            NetworkOnlineDebugLog.info("C2SVersionCheck from {} ver={}", sender.getName().getString(), message.version);
            AuthModelsCapability.get(sender).ifPresent(cap -> {
                for (String modelId : ServerModelManager.getAuthModels()) {
                    cap.addModel(modelId);
                }
            });
            PlayerStarModelsStore.restore(sender);
            PlayerModelSelectionStore.restore(sender);
            ServerModelManager.validatePlayerModel(sender);
            ModelInfoCapability.get(sender).ifPresent(cap -> {
                NetworkOnlineDebugLog.info("After validate: {} modelId={} texture={} dirty={}",
                        sender.getName().getString(), cap.getModelId(), cap.getSelectTexture(), cap.isDirty());
                cap.setMandatory(false);
                cap.stopAnimation(sender);
            });
            CapabilityEvent.syncVisiblePlayerModelsTo(sender);
            NetworkOnlineDebugLog.info("Calling syncPlayerModelToTracking for {}", sender.getName().getString());
            CapabilityEvent.syncPlayerModelToTracking(sender, false);
            AuthModelsCapability.get(sender).ifPresent(cap -> {
                NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(cap.getAuthModels()), sender);
            });
            StarModelsCapability.get(sender).ifPresent(cap -> {
                NetworkHandler.sendToClientPlayer(new S2CSyncStarModelsPacket(cap.getStarModels()), sender);
            });
            ServerModelManager.requestPlayerAuth(sender, null);
        }
    }
}
