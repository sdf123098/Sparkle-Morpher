package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.fabric.YsmComponents;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class AuthModelsCapabilityImpl {

    private AuthModelsCapabilityImpl() {
    }

    public static Optional<AuthModelsCapability> get(Player player) {
        AuthModelsComponent component = YsmComponents.AUTH_MODELS.getNullable(player);
        return component == null ? Optional.empty() : Optional.of(component.capability());
    }
}
