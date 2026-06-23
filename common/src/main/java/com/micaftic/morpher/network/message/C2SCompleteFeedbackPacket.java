package com.micaftic.morpher.network.message;

import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouMaidCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import com.micaftic.morpher.core.api.network.PacketContext;

public record C2SCompleteFeedbackPacket(FeedbackData feedbackData) {

    public static void encode(C2SCompleteFeedbackPacket message, FriendlyByteBuf buf) {
        FeedbackData.writeToBuf(message.feedbackData, buf);
    }

    public static C2SCompleteFeedbackPacket decode(FriendlyByteBuf buf) {
        return new C2SCompleteFeedbackPacket(FeedbackData.readFromBuf(buf, false));
    }

    public static void handle(C2SCompleteFeedbackPacket message, PacketContext ctx) {
        if (ctx.isServerSide() && ctx.getSender() != null) {
            ServerPlayer sender = ctx.getSender();
            ctx.enqueueWork(() -> handleOnServer(message, sender.serverLevel()));
        }
    }

    public static void handleOnServer(C2SCompleteFeedbackPacket message, ServerLevel serverLevel) {
        Entity entity = serverLevel.getEntity(message.feedbackData.flags());
        if (TouhouMaidCompat.isMaidEntity(entity)) {
            TouhouMaidCompat.applyFeedback(entity, message.feedbackData);
        } else if (entity instanceof ServerPlayer serverPlayer) {
            ModelInfoCapability.get(serverPlayer).ifPresent(cap -> {
                cap.applyFeedback(serverPlayer, message.feedbackData);
                if (serverPlayer.getVehicle() != null && serverPlayer.getVehicle().getFirstPassenger() == serverPlayer) {
                    VehicleModelCapability.get(serverPlayer.getVehicle()).ifPresent(vehicleCap -> {
                        cap.getMolangVars().ifPresent(map -> vehicleCap.setModel(cap.getModelId(), map));
                    });
                }
            });
        }
    }
}