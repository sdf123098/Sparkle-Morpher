package com.micaftic.morpher;

import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.config.ModSoundEvents;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.event.YsmEventBootstrap;
import com.micaftic.morpher.util.obfuscate.Keep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.micaftic.morpher.core.api.PlatformAPI;
import com.micaftic.morpher.core.api.config.ConfigRegistration;

import java.io.File;
import java.io.IOException;

/**
 * TODO:
 * 默认模型应该就在模组架加载的时候就预加载了
 * 其它模型统统都是进入世界后加载
 */
public class YesSteveModel {

    public static final String MOD_ID = "sparkle_morpher";

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private YesSteveModel() {
    }

    public static void init() {
        migrateLegacyConfigDir();
        LOGGER.info("Initializing Sparkle's Morpher, platform: " + PlatformAPI.getPlatformName());
        try {
            NativeLibLoader.init();
        } catch (IOException e) {
            LOGGER.error("Failed to initialize native lib", e);
        }
        if (!NativeLibLoader.isAvailable()) {
            LOGGER.error(getErrorMessage());
        } else {
            initConfig();
        }
        YsmEventBootstrap.register();
    }

    /** One-time backward-compat: move legacy config dir (config/yes_steve_model from Fox/YSM) to config/sparkle_morpher. */
    private static void migrateLegacyConfigDir() {
        try {
            java.nio.file.Path cfg = Platform.getConfigFolder();
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

    @SuppressWarnings({"deprecation", "removal"})
    private static void initConfig() {
        File oldConfig = Platform.getConfigFolder().resolve("sparkle_morpher-common.toml").toFile();
        if (oldConfig.isFile()) {
            File file2 = Platform.getConfigFolder().resolve("sparkle_morpher-client.toml").toFile();
            if (!file2.isFile()) {
                oldConfig.renameTo(file2);
            } else {
                oldConfig.delete();
            }
        }
        ConfigRegistration.register(MOD_ID, ModConfig.Type.CLIENT, GeneralConfig.buildSpec());
        ConfigRegistration.register(MOD_ID, ModConfig.Type.SERVER, ServerConfig.buildSpec());
        if (!PlatformAPI.isServer()) {
            ModSoundEvents.REGISTER.register();
        }
    }

    @Keep
    public static boolean isAvailable() {
        return NativeLibLoader.isAvailable();
    }

    public static boolean isOnAndroid() {
        return NativeLibLoader.isOnAndroid();
    }

    @Environment(EnvType.CLIENT)
    public static void sendUnavailableMessage() {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            localPlayer.sendSystemMessage(getUnavailableComponent());
        }
    }

    public static Component getUnavailableComponent() {
        return NativeLibLoader.getErrorComponent();
    }

    public static String getErrorMessage() {
        return NativeLibLoader.getErrorMessage();
    }
}
