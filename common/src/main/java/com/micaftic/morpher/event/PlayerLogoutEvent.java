package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.util.PlayerDataSaveBridge;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import com.micaftic.morpher.core.architectury.event.events.common.PlayerEvent;

public final class PlayerLogoutEvent {

    private PlayerLogoutEvent() {
    }

    public static void register() {
        PlayerEvent.PLAYER_QUIT.register(player -> {
            if (!YesSteveModel.isAvailable()) {
                return;
            }
            ServerModelManager.clearPlayerSyncState(player.getUUID());
            ModelInfoCapability.get(player).ifPresent(cap -> PlayerModelSelectionStore.saveCurrentSelection(player, cap));
            PlayerDataSaveBridge.save(player);
        });
    }
}
