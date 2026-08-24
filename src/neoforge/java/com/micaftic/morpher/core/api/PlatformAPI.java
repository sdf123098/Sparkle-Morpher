package com.micaftic.morpher.core.api;

import com.micaftic.morpher.core.api.loader.LoaderKind;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class PlatformAPI {
    private PlatformAPI() {}
    public static boolean isServer() { return FMLEnvironment.dist == Dist.DEDICATED_SERVER; }
    public static boolean isClient() { return FMLEnvironment.dist == Dist.CLIENT; }
    public static String getPlatformName() { return "NeoForge"; }
    public static LoaderKind loaderKind() { return LoaderKind.NEOFORGE; }
}
