package com.micaftic.morpher.core.architectury.platform;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Minimal Fabric-backed replacement for the Architectury platform helper.
 */
public class Platform {
    public static Path getConfigFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static Mod getMod(String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(Mod::new).orElseGet(() -> new Mod(modId));
    }

    public static Collection<Mod> getMods() {
        return FabricLoader.getInstance().getAllMods().stream().map(Mod::new).collect(Collectors.toList());
    }

    public static boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
