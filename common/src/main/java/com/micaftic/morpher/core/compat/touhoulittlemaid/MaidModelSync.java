package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.capability.VehicleModelCapability;
import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.core.compat.api.CompatServices;
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
                .ifPresent(state -> CompatServices.maidNetworkService().sendToTrackingEntity(
                        new S2CSyncVehicleModelPacket(maid.getId(), state), maid));
    }

    /**
     * Re-send a persisted maid override after the entity has been loaded into a level.
     * The capability/component owns the persistent data; this hook only repairs the
     * server-to-client side of the load boundary.
     */
    public static void onEntityLoaded(Entity maid) {
        if (maid.level().isClientSide() || !TouhouMaidCompat.isMaidEntity(maid)) {
            return;
        }
        VehicleModelCapability.get(maid).filter(VehicleModelCapability::isInitialized)
                .ifPresent(state -> syncNow(maid, state));
    }

    public static void handleBaseModelChanged(Entity maid) {
        // TLM also calls setModelId while deserializing a maid. At that point the
        // entity is not in the level yet; clearing here would erase the persisted
        // YSM override before the component/attachment has finished loading.
        if (maid.level().isClientSide() || maid.tickCount <= 0
                || maid.level().getEntity(maid.getId()) != maid) {
            return;
        }
        VehicleModelCapability.get(maid).filter(VehicleModelCapability::isInitialized).ifPresent(state -> {
            state.clearMaidModel();
            syncNow(maid, state);
        });
    }

    public static void applySelectedModel(Entity maid, ServerPlayer player, String modelId, String textureId) {
        if (maid == null || player == null || modelId == null || modelId.isBlank()
                || !ConfigPolicies.network().canSwitchModel()
                || !TouhouMaidCompat.isMaidEntity(maid)
                || !TouhouLittleMaidCompat.isMaidOwnedBy(maid, player)
                || !CompatServices.maidModelService().containsModel(modelId)) {
            return;
        }
        boolean authorized = CompatServices.maidModelService().isAuthorized(modelId, player);
        if (!authorized) {
            return;
        }
        String resolvedTexture = CompatServices.maidModelService().resolveTextureOrDefault(modelId, textureId);
        if (resolvedTexture == null) {
            return;
        }
        VehicleModelCapability.get(maid).ifPresent(state -> {
            state.setMaidModel(modelId, resolvedTexture, new Object2FloatOpenHashMap<>());
            syncNow(maid, state, player);
        });
    }

    public static void syncNow(Entity maid, VehicleModelCapability state) {
        CompatServices.maidNetworkService().sendToTrackingEntity(new S2CSyncVehicleModelPacket(maid.getId(), state), maid);
    }

    private static void syncNow(Entity maid, VehicleModelCapability state, Player player) {
        S2CSyncVehicleModelPacket packet = new S2CSyncVehicleModelPacket(maid.getId(), state);
        CompatServices.maidNetworkService().sendToTrackingEntity(packet, maid);
        if (player instanceof ServerPlayer serverPlayer && CompatServices.maidNetworkService().isPlayerConnected(serverPlayer)) {
            CompatServices.maidNetworkService().sendToClientPlayer(packet, serverPlayer);
        }
    }
}
