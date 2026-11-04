package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidAccess;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.cloud.client.CloudEntityModelSync;
import com.micaftic.morpher.cloud.client.CloudEntityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/** Optional maid model picker using client Cloud synchronization. */
final class OfficialTouhouLittleMaidCompat {
    private static final String GUI_INIT_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent$Init";
    private static final String OPEN_SCREEN_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.compat.ysm.event.OpenYsmMaidScreenEvent";

    private OfficialTouhouLittleMaidCompat() {
    }

    static void init(Logger logger) {
        if (!TouhouLittleMaidAccess.isLoaded()
                || FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> eventClass = Class.forName(GUI_INIT_EVENT, false,
                    OfficialTouhouLittleMaidCompat.class.getClassLoader());
            registerGuiInitListener(eventClass);
            logger.info("Enabled official Touhou Little Maid direct model selection");
        } catch (Throwable throwable) {
            logger.debug("Official Touhou Little Maid model button unavailable: {}",
                    throwable.getMessage());
        }
        // Keep a real YSM installation's original screen listener independent.
        if (ModList.get().isLoaded("yes_steve_model")) {
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

    @SuppressWarnings("unchecked")
    private static void registerGuiInitListener(Class<?> eventClass) {
        registerGuiInitListenerTyped((Class<? extends net.neoforged.bus.api.Event>) eventClass);
    }

    private static <T extends net.neoforged.bus.api.Event> void registerGuiInitListenerTyped(Class<T> eventClass) {
        NeoForge.EVENT_BUS.addListener(eventClass, OfficialTouhouLittleMaidCompat::onGuiInit);
    }

    private static void onGuiInit(net.neoforged.bus.api.Event event) {
        try {
            Object gui = event.getClass().getMethod("getGui").invoke(event);
            if (!(gui instanceof Screen)) {
                return;
            }
            Object value = gui.getClass().getMethod("getMaid").invoke(gui);
            if (!(value instanceof Entity maid) || !TouhouLittleMaidAccess.isMaid(maid)) {
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

    private static void onOpenScreen(net.neoforged.bus.api.Event event) {
        try {
            Method getMaid = event.getClass().getMethod("getMaid");
            Object value = getMaid.invoke(event);
            if (!(value instanceof Entity maid) || !TouhouLittleMaidAccess.isMaid(maid)) {
                return;
            }
            Minecraft.getInstance().setScreen(new ModernPlayerModelScreen(
                    (modelId, texture) -> applyModel(maid, modelId, texture),
                    "maid:" + maid.getUUID()));
        } catch (Throwable ignored) {
            // The event is optional and must never affect the maid screen.
        }
    }

    private static void applyModel(Entity maid, String modelId, String texture) {
        CloudEntityModelSync.applySelection(CloudEntityProvider.Kind.MAID, maid.getUUID(),
                maid.getName().getString(), modelId, texture);
    }
}
