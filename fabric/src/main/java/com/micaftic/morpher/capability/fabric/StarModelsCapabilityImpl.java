package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.StarModelsCapability;
import com.micaftic.morpher.fabric.YsmComponents;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class StarModelsCapabilityImpl {

    private StarModelsCapabilityImpl() {
    }

    public static Optional<StarModelsCapability> get(Player player) {
        StarModelsComponent component = YsmComponents.STAR_MODELS.getNullable(player);
        return component == null ? Optional.empty() : Optional.of(component.capability());
    }
}
