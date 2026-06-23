package com.micaftic.morpher.core.api;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class PlatformAPI {
    private PlatformAPI() {}
    public static boolean isServer() { return FMLEnvironment.dist == Dist.DEDICATED_SERVER; }
    public static String getPlatformName() { return "NeoForge"; }
}