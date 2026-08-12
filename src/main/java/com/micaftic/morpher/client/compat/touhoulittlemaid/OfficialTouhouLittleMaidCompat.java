package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidAccess;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SSetMaidModelPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

final class OfficialTouhouLittleMaidCompat {
    private static final String OPEN_SCREEN_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.compat.ysm.event.OpenYsmMaidScreenEvent";
    private static final String[] MODEL_PACKETS = {
            "com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelPackage",
            "com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelMessage"
    };

    private OfficialTouhouLittleMaidCompat() {
    }

    static void init(Logger logger) {
        if (!TouhouLittleMaidAccess.isLoaded()
                || ModList.get().isLoaded("yes_steve_model")
                || FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> eventClass = Class.forName(OPEN_SCREEN_EVENT, false,
                    OfficialTouhouLittleMaidCompat.class.getClassLoader());
            registerOpenScreenListener(eventClass);
            logger.info("Enabled official Touhou Little Maid YSM model screen integration");
        } catch (Throwable throwable) {
            logger.debug("Official Touhou Little Maid YSM screen integration unavailable: {}",
                    throwable.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerOpenScreenListener(Class<?> eventClass) {
        registerOpenScreenListenerTyped((Class<? extends net.neoforged.bus.api.Event>) eventClass);
    }

    private static <T extends net.neoforged.bus.api.Event> void registerOpenScreenListenerTyped(Class<T> eventClass) {
        NeoForge.EVENT_BUS.addListener(eventClass, OfficialTouhouLittleMaidCompat::onOpenScreen);
    }

    private static void onOpenScreen(net.neoforged.bus.api.Event event) {
        try {
            Method getMaid = event.getClass().getMethod("getMaid");
            Object value = getMaid.invoke(event);
            if (!(value instanceof Entity maid) || !TouhouLittleMaidAccess.isMaid(maid)) {
                return;
            }
            InputUtil.setScreen(new ModernPlayerModelScreen(InputUtil.getCurrentScreen(),
                    (modelId, texture) -> applyModel(maid, modelId, texture)));
        } catch (Throwable ignored) {
            // The event is optional and must never affect the maid screen.
        }
    }

    private static void applyModel(Entity maid, String modelId, String texture) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        // SPM 自有链路：发 C2SSetMaidModelPacket → server 端 MaidModelSync.applySelectedModel（含 auth 校验）
        try {
            NetworkHandler.sendToServer(new C2SSetMaidModelPacket(maid.getId(), modelId, texture == null ? "" : texture));
            return;
        } catch (Throwable ignored) {
            // SPM 包不可用时回退官方女仆协议
        }
        for (String packetName : MODEL_PACKETS) {
            try {
                Class<?> packetClass = Class.forName(packetName, false,
                        OfficialTouhouLittleMaidCompat.class.getClassLoader());
                Constructor<?> constructor = packetClass.getConstructor(
                        int.class, String.class, String.class, Component.class);
                Object packet = constructor.newInstance(
                        maid.getId(), modelId, texture == null ? "" : texture, Component.literal(modelId));
                if (packet instanceof CustomPacketPayload payload) {
                    ClientPacketDistributor.sendToServer(payload);
                    return;
                }
            } catch (ClassNotFoundException ignored) {
                // Try the other official packet name.
            } catch (Throwable ignored) {
                // Try the other official packet name.
            }
        }
    }
}
