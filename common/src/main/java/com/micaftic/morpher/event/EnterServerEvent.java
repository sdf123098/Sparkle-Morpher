package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.S2CSyncAuthModelsPacket;
import com.micaftic.morpher.network.message.S2CSyncStarModelsPacket;
import com.micaftic.morpher.network.message.S2CVersionCheckPacket;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import dev.architectury.event.events.common.PlayerEvent;

public final class EnterServerEvent {

    private EnterServerEvent() {
    }

    public static void register() {
        PlayerEvent.PLAYER_JOIN.register(player -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            NetworkHandler.sendToClientPlayer(new S2CVersionCheckPacket(), player);
            CapabilityEvent.getAuthModelsCap(player).ifPresent(authModelsCap -> {
                for (String modelId : ServerModelManager.getAuthModels()) {
                    authModelsCap.addModel(modelId);
                }
                NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(authModelsCap.getAuthModels()), player);
            });
            PlayerModelSelectionStore.restore(player);
            ServerModelManager.validatePlayerModel(player);
            CapabilityEvent.syncPlayerModelToSelf(player);
            CapabilityEvent.syncPlayerModelToTracking(player, false);
            CapabilityEvent.getStarModelsCap(player).ifPresent(starModelsCap -> NetworkHandler.sendToClientPlayer(new S2CSyncStarModelsPacket(starModelsCap.getStarModels()), player));
        });
    }
}
