package com.micaftic.morpher;

import com.micaftic.morpher.config.*;
import com.micaftic.morpher.event.CommonEvent;
import com.micaftic.morpher.event.YsmEventBootstrap;
import com.micaftic.morpher.util.obfuscate.Keep;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.*;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.*;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.config.ConfigRegistration;
import java.io.IOException;

public class YesSteveModel {
    public static final String MOD_ID = "sparkle_morpher";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private YesSteveModel() {}
    public static void init() {
        migrateLegacyConfigDir();
        LOGGER.info("Initializing Sparkle's Morpher, platform: " + PlatformAPI.getPlatformName());
        try { NativeLibLoader.init(); } catch (IOException e) { LOGGER.error("Failed to initialize native lib", e); }
        if (!NativeLibLoader.isAvailable()) LOGGER.error(getErrorMessage());
        CommonEvent.init();
        YsmEventBootstrap.register();
    }
    /** One-time backward-compat: move legacy config dir (config/yes_steve_model from Fox/YSM) to config/sparkle_morpher. */
    private static void migrateLegacyConfigDir() {
        try {
            java.nio.file.Path cfg = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
            java.nio.file.Path neu = cfg.resolve(MOD_ID);
            java.nio.file.Path legacy = cfg.resolve("yes_steve_model");
            if (!java.nio.file.Files.exists(neu) && java.nio.file.Files.isDirectory(legacy)) {
                java.nio.file.Files.move(legacy, neu);
                LOGGER.info("Migrated legacy config dir {} -> {}", legacy, neu);
            }
        } catch (Exception e) {
            LOGGER.warn("Legacy config dir migration skipped: {}", e.toString());
        }
    }
    private static void initConfig() {
        java.io.File old = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("sparkle_morpher-common.toml").toFile();
        if (old.isFile()) { java.io.File f2 = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("sparkle_morpher-client.toml").toFile(); if (!f2.isFile()) old.renameTo(f2); else old.delete(); }
        ConfigRegistration.register(MOD_ID, ModConfig.Type.CLIENT, GeneralConfig.buildSpec());
        ConfigRegistration.register(MOD_ID, ModConfig.Type.SERVER, ServerConfig.buildSpec());
    }
    public static void registerModBusEvents(net.neoforged.bus.api.IEventBus bus) {
        ModSoundEvents.REGISTER.register(bus);
        com.micaftic.morpher.neoforge.capability.NeoForgeCapabilities.register(bus);
    }
    @Keep public static boolean isAvailable() { return NativeLibLoader.isAvailable(); }
    public static boolean isOnAndroid() { return NativeLibLoader.isOnAndroid(); }
    @OnlyIn(Dist.CLIENT) public static void sendUnavailableMessage() { LocalPlayer p = Minecraft.getInstance().player; if (p != null) p.sendSystemMessage(getUnavailableComponent()); }
    public static Component getUnavailableComponent() { return NativeLibLoader.getErrorComponent(); }
    public static String getErrorMessage() { return NativeLibLoader.getErrorMessage(); }
}