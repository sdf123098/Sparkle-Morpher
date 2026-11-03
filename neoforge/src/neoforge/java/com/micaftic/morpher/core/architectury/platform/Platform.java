package com.micaftic.morpher.core.architectury.platform;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Minimal NeoForge-backed replacement for the Architectury platform helper.
 */
public class Platform {
    public static Path getConfigFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static Mod getMod(String modId) {
        return ModList.get().getModContainerById(modId).map(Mod::new).orElseGet(() -> new Mod(modId));
    }

    public static Collection<Mod> getMods() {
        return ModList.get().getSortedMods().stream().map(Mod::new).toList();
    }

    public static boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
