package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.cloud.client.CloudEntityModelSync;
import com.micaftic.morpher.cloud.client.CloudEntityProvider;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

final class OrihimeDirectModelCompat {
    private static final String GUI_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent";
    private static final String INIT_CALLBACK = GUI_EVENT + "$Init$Callback";
    private static final String OPEN_SCREEN_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.compat.ysm.event.OpenYsmMaidScreenEvent";
    private static final String OPEN_SCREEN_CALLBACK = OPEN_SCREEN_EVENT + "$Callback";

    private OrihimeDirectModelCompat() {
    }

    static void init() {
        if (!FabricLoader.getInstance().isModLoaded("touhou_little_maid")) {
            return;
        }
        try {
            ClassLoader loader = OrihimeDirectModelCompat.class.getClassLoader();
            Class<?> eventClass = Class.forName(GUI_EVENT, false, loader);
            Class<?> callbackClass = Class.forName(INIT_CALLBACK, false, loader);
            Object initEvent = eventClass.getField("INIT").get(null);
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("onInit".equals(method.getName()) && args != null && args.length == 1) {
                            onGuiInit(args[0]);
                        }
                        return null;
                    });
            register(initEvent, listener);
            YesSteveModel.LOGGER.info("Enabled Touhou Little Maid Orihime direct model selection");
        } catch (Throwable throwable) {
            YesSteveModel.LOGGER.debug("Touhou Little Maid Orihime model button unavailable: {}",
                    throwable.getMessage());
        }
        // Keep a real YSM installation's original screen listener independent.
        if (FabricLoader.getInstance().isModLoaded("yes_steve_model")) {
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
                    (modelId, texture) -> applyModel(maid, modelId, texture),
                    "maid:" + maid.getUUID()));
        } catch (Throwable ignored) {
            // Optional integration must never affect the maid screen.
        }
    }

    private static void onGuiInit(Object event) {
        try {
            Object gui = event.getClass().getMethod("getGui").invoke(event);
            if (!(gui instanceof Screen)) {
                return;
            }
            Object value = gui.getClass().getMethod("getMaid").invoke(gui);
            if (!(value instanceof Entity maid)) {
                return;
            }
            int left = ((Number) event.getClass().getMethod("getLeftPos").invoke(event)).intValue();
            int top = ((Number) event.getClass().getMethod("getTopPos").invoke(event)).intValue();
            Button button = Button.builder(Component.literal("S"), ignored ->
                    Minecraft.getInstance().setScreen(new ModernPlayerModelScreen(
                            (modelId, texture) -> applyModel(maid, modelId, texture),
                            "maid:" + maid.getUUID())))
                    .bounds(left + 32, top + 14, 9, 9)
                    .build();
            button.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.player_model.desc")));
            event.getClass().getMethod("addButton", String.class, AbstractWidget.class)
                    .invoke(event, "sparkle_morpher:model", button);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Unsupported optional maid versions must leave their GUI functional.
        }
    }

    private static void applyModel(Entity maid, String modelId, String texture) {
        CloudEntityModelSync.applySelection(CloudEntityProvider.Kind.MAID, maid.getUUID(),
                maid.getName().getString(), modelId, texture);
    }
}
