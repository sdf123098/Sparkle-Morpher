package com.micaftic.morpher.event;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.model.ServerModelManager;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class PlayerLogoutEvent {
    private PlayerLogoutEvent() {}
    public static void register() { NeoForge.EVENT_BUS.addListener(PlayerLogoutEvent::onQuit); }
    private static void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (!YesSteveModel.isAvailable()) return;
        if (NetworkHandler.isPlayerConnected(p)) ServerModelManager.syncModelToPlayer(p.getUUID());
        ModelInfoCapability.get(p).ifPresent(c -> PlayerModelSelectionStore.saveCurrentSelection(p, c));
        PlayerDataSaveBridge.save(p);
    }
}