package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.S2CSyncVehicleModelPacket;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class MaidModelSync {
    private MaidModelSync() {
    }

    public static boolean handleInteraction(Entity maid, Player player, InteractionHand hand) {
        if (maid.level().isClientSide() || hand != InteractionHand.MAIN_HAND
                || !TouhouMaidCompat.isMaidEntity(maid)
                || !TouhouLittleMaidCompat.isMaidItem(player.getItemInHand(hand).getItem())
                || !TouhouLittleMaidCompat.isMaidOwnedBy(maid, player)) {
            return false;
        }
        VehicleModelCapability.get(maid).ifPresent(state -> {
            if (player.isShiftKeyDown()) {
                state.clearMaidModel();
                syncNow(maid, state, player);
                return;
            }
            ModelInfoCapability.get(player).ifPresent(playerState ->
                    playerState.withMolangVars(values -> {
                        state.setMaidModel(playerState.getModelId(), playerState.getSelectTexture(),
                                new Object2FloatOpenHashMap<>(values));
                        syncNow(maid, state, player);
                    }));
        });
        return true;
    }

    public static void periodicSync(Entity maid) {
        if (maid.level().isClientSide() || maid.tickCount % 20 != 0) {
            return;
        }
        VehicleModelCapability.get(maid).filter(VehicleModelCapability::isInitialized)
                .ifPresent(state -> NetworkHandler.sendToTrackingEntity(
                        new S2CSyncVehicleModelPacket(maid.getId(), state), maid));
    }

    public static void handleBaseModelChanged(Entity maid) {
        if (maid.level().isClientSide() || maid.tickCount <= 0) {
            return;
        }
        VehicleModelCapability.get(maid).filter(VehicleModelCapability::isInitialized).ifPresent(state -> {
            state.clearMaidModel();
            syncNow(maid, state);
        });
    }

    public static void syncNow(Entity maid, VehicleModelCapability state) {
        NetworkHandler.sendToTrackingEntity(new S2CSyncVehicleModelPacket(maid.getId(), state), maid);
    }

    private static void syncNow(Entity maid, VehicleModelCapability state, Player player) {
        S2CSyncVehicleModelPacket packet = new S2CSyncVehicleModelPacket(maid.getId(), state);
        NetworkHandler.sendToTrackingEntity(packet, maid);
        if (player instanceof ServerPlayer serverPlayer && NetworkHandler.isPlayerConnected(serverPlayer)) {
            NetworkHandler.sendToClientPlayer(packet, serverPlayer);
        }
    }
}
