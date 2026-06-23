package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.PlayerCapability;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import com.micaftic.morpher.capability.fabric.client.PlayerCapabilityClientStore;

public final class PlayerCapabilityImpl {

    private PlayerCapabilityImpl() {
    }

    public static Optional<PlayerCapability> get(Player player) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return Optional.empty();
        }
        return PlayerCapabilityClientStore.get(player);
    }

    public static Optional<PlayerCapability> get(Entity entity) {
        if (!(entity instanceof Player player)) {
            return Optional.empty();
        }
        return get(player);
    }
}
