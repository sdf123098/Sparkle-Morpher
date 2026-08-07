package com.micaftic.morpher.fabric.client;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.C2SSetMaidModelPacket;
import com.micaftic.morpher.util.InputUtil;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.loader.api.FabricLoader;
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

    private OrihimeDirectModelCompat() {
    }

    static void init() {
        if (!FabricLoader.getInstance().isModLoaded("touhou_little_maid")
                || FabricLoader.getInstance().isModLoaded("yes_steve_model")) {
            return;
        }
        try {
            ClassLoader loader = OrihimeDirectModelCompat.class.getClassLoader();
            Class<?> eventClass = Class.forName(GUI_EVENT, false, loader);
            Class<?> callbackClass = Class.forName(INIT_CALLBACK, false, loader);
            Field initField = eventClass.getField("INIT");
            Object initEvent = initField.get(null);
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass},
                    (proxy, method, args) -> {
                        if ("onInit".equals(method.getName()) && args != null && args.length == 1) {
                            onInit(args[0]);
                        }
                        return null;
                    });
            register(initEvent, listener);
            YesSteveModel.LOGGER.info("Enabled Touhou Little Maid Orihime direct model selection");
        } catch (Throwable throwable) {
            YesSteveModel.LOGGER.debug("Touhou Little Maid Orihime model selection unavailable: {}",
                    throwable.getMessage());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void register(Object event, Object listener) {
        ((Event) event).register(listener);
    }

    private static void onInit(Object event) {
        try {
            Object gui = event.getClass().getMethod("getGui").invoke(event);
            if (!(gui instanceof Screen parent)) {
                return;
            }
            Object menu = gui.getClass().getMethod("getMenu").invoke(gui);
            Object value = menu.getClass().getMethod("getMaid").invoke(menu);
            if (!(value instanceof Entity maid)) {
                return;
            }
            int left = ((Number) event.getClass().getMethod("getLeftPos").invoke(event)).intValue();
            int top = ((Number) event.getClass().getMethod("getTopPos").invoke(event)).intValue();
            Button button = Button.builder(Component.literal("S"),
                            ignored -> openModelScreen(parent, maid))
                    .bounds(left + 42, top + 14, 9, 9)
                    .build();
            button.setTooltip(Tooltip.create(Component.translatable("key.sparkle_morpher.player_model.desc")));
            Method addButton = event.getClass().getMethod("addButton", String.class, AbstractWidget.class);
            addButton.invoke(event, "sparkle_morpher:model", button);
        } catch (Throwable ignored) {
            // Optional integration must never affect the maid screen.
        }
    }

    private static void openModelScreen(Screen parent, Entity maid) {
        InputUtil.setScreen(new ModernPlayerModelScreen(parent,
                (modelId, texture) -> NetworkHandler.sendToServer(
                        new C2SSetMaidModelPacket(maid.getId(), modelId, texture))));
    }
}
