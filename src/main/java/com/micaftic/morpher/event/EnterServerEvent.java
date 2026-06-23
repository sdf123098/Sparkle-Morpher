package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.*;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class EnterServerEvent {
    private EnterServerEvent() {}
    public static void register() { NeoForge.EVENT_BUS.addListener(EnterServerEvent::onJoin); }
    private static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer p)) return;
        if (!YesSteveModel.isAvailable()) return;
        NetworkHandler.sendToClientPlayer(new S2CVersionCheckPacket(), p);
        CapabilityEvent.getAuthModelsCap(p).ifPresent(c -> { for (String m : ServerModelManager.getAuthModels()) c.addModel(m); NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(c.getAuthModels()), p); });
        PlayerModelSelectionStore.restore(p); ServerModelManager.validatePlayerModel(p);
        CapabilityEvent.syncPlayerModelToSelf(p); CapabilityEvent.syncPlayerModelToTracking(p, false);
        CapabilityEvent.getStarModelsCap(p).ifPresent(c -> NetworkHandler.sendToClientPlayer(new S2CSyncStarModelsPacket(c.getStarModels()), p));
    }
}