package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SSetMaidModelPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class OrihimeDirectModelCompat {
    private static final String OPEN_SCREEN_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.compat.ysm.event.OpenYsmMaidScreenEvent";
    private static final String OPEN_SCREEN_CALLBACK = OPEN_SCREEN_EVENT + "$Callback";
    private static final String MODEL_PACKET =
            "com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelPackage";

    private OrihimeDirectModelCompat() {
    }

    static void init() {
        if (!FabricLoader.getInstance().isModLoaded("touhou_little_maid")
                || FabricLoader.getInstance().isModLoaded("yes_steve_model")) {
            return;
        }
        try {
            ClassLoader loader = OrihimeDirectModelCompat.class.getClassLoader();
            Class<?> eventClass = Class.forName(OPEN_SCREEN_EVENT, false, loader);
            Class<?> callbackClass = Class.forName(OPEN_SCREEN_CALLBACK, false, loader);
            Field callbackField = eventClass.getField("CALLBACK");
            Object callbackEvent = callbackField.get(null);
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("post".equals(method.getName()) && args != null && args.length == 1) {
                            onOpenScreen(args[0]);
                        }
                        return null;
                    });
            register(callbackEvent, listener);
            YesSteveModel.LOGGER.info("Enabled Touhou Little Maid Orihime YSM model screen integration");
        } catch (Throwable throwable) {
            YesSteveModel.LOGGER.debug("Touhou Little Maid Orihime YSM integration unavailable: {}",
                    throwable.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void register(Object callbackEvent, Object listener) {
        ((Event) callbackEvent).register(listener);
    }

    private static void onOpenScreen(Object event) {
        try {
            Method getMaid = event.getClass().getMethod("getMaid");
            Object value = getMaid.invoke(event);
            if (!(value instanceof Entity maid)) {
                return;
            }
            Minecraft.getInstance().setScreen(new ModernPlayerModelScreen(
                    (modelId, texture) -> applyModel(maid, modelId, texture)));
        } catch (Throwable ignored) {
            // Optional integration must never affect the maid screen.
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
        try {
            Class<?> packetClass = Class.forName(MODEL_PACKET, false,
                    OrihimeDirectModelCompat.class.getClassLoader());
            Constructor<?> constructor = packetClass.getConstructor(
                    int.class, String.class, String.class, Component.class);
            Object packet = constructor.newInstance(
                    maid.getId(), modelId, texture == null ? "" : texture, Component.literal(modelId));
            if (packet instanceof CustomPacketPayload payload) {
                ClientPlayNetworking.send(payload);
            }
        } catch (Throwable ignored) {
            // An unsupported Orihime version must not affect the model screen.
        }
    }
}
