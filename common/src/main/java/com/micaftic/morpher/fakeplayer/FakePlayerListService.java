package com.micaftic.morpher.fakeplayer;

import com.micaftic.morpher.core.config.ConfigPolicies;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.S2CFakePlayerListPacket;
import com.micaftic.morpher.util.YSMMessageFormatter;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.List;

/** Server-side source of truth for the GUI's target list. */
public final class FakePlayerListService {

    private FakePlayerListService() {
    }

    public static void sendTo(ServerPlayer actor) {
        if (actor == null || !ConfigPolicies.network().canSwitchModel()
                || !YSMMessageFormatter.hasPermission(actor, 2)) {
            if (actor != null) {
                NetworkHandler.sendToClientPlayer(new S2CFakePlayerListPacket(List.of()), actor);
            }
            return;
        }
        List<FakePlayerListEntry> entries = actor.serverLevel().getServer().getPlayerList().getPlayers().stream()
                .filter(player -> player != actor && FakePlayerTargetRules.isFakePlayer(player))
                .map(player -> new FakePlayerListEntry(player.getUUID(), player.getScoreboardName(),
                        player.getDisplayName().getString(), FakePlayerTargetRules.providerId(player),
                        ModelInfoCapability.get(player).map(ModelInfoCapability::getModelId).orElse(""),
                        player.level().dimension().location().toString(), true))
                .sorted(Comparator.comparing(FakePlayerListEntry::name, String.CASE_INSENSITIVE_ORDER))
                .limit(128)
                .toList();
        NetworkHandler.sendToClientPlayer(new S2CFakePlayerListPacket(entries), actor);
    }
}
