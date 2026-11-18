package com.micaftic.morpher.core.compat.create.fabric;

import com.micaftic.morpher.client.core.HostMolangSink;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.player.Player;

public final class CreateCompatImpl {

    private CreateCompatImpl() {
    }

    public static boolean isLoaded() {
        return false;
    }

    public static boolean isPlayerOnCreateContraption(Player player) {
        return false;
    }

    public static void registerCreateFunctions(HostMolangSink binding) {
        binding.var("create_hanging_skyhook", event -> MolangValue.bool(false));
    }
}
