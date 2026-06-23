package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.fabric.YsmComponents;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class ModelInfoCapabilityImpl {

    private ModelInfoCapabilityImpl() {
    }

    public static Optional<ModelInfoCapability> get(Player player) {
        ModelInfoComponent component = YsmComponents.MODEL_INFO.getNullable(player);
        return component == null ? Optional.empty() : Optional.of(component.capability());
    }
}
