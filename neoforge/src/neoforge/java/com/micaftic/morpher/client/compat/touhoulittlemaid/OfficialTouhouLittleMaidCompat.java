package com.micaftic.morpher.client.compat.touhoulittlemaid;

import com.micaftic.morpher.core.compat.touhoulittlemaid.TouhouLittleMaidAccess;

import com.micaftic.morpher.client.gui.ModernPlayerModelScreen;
import com.micaftic.morpher.util.InputUtil;
import com.micaftic.morpher.cloud.client.CloudEntityModelSync;
import com.micaftic.morpher.cloud.client.CloudEntityProvider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import org.apache.logging.log4j.Logger;

final class OfficialTouhouLittleMaidCompat {
    private static final String OPEN_SCREEN_EVENT =
            "com.github.tartaricacid.touhoulittlemaid.api.event.client.MaidContainerGuiEvent$Init";

    private OfficialTouhouLittleMaidCompat() {
    }

    static void init(Logger logger) {
        if (!TouhouLittleMaidAccess.isLoaded()
                || FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        try {
            Class<?> eventClass = Class.forName(OPEN_SCREEN_EVENT, false,
                    OfficialTouhouLittleMaidCompat.class.getClassLoader());
            registerOpenScreenListener(eventClass);
            logger.info("Enabled official Touhou Little Maid direct model selection");
        } catch (Throwable throwable) {
            logger.debug("Official Touhou Little Maid model button unavailable: {}",
                    throwable.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerOpenScreenListener(Class<?> eventClass) {
        registerOpenScreenListenerTyped((Class<? extends net.neoforged.bus.api.Event>) eventClass);
    }

    private static <T extends net.neoforged.bus.api.Event> void registerOpenScreenListenerTyped(Class<T> eventClass) {
        NeoForge.EVENT_BUS.addListener(eventClass, OfficialTouhouLittleMaidCompat::onGuiInit);
    }

    private static void onGuiInit(net.neoforged.bus.api.Event event) {
        try {
            Object gui = event.getClass().getMethod("getGui").invoke(event);
            if (!(gui instanceof Screen parent)) {
                return;
            }
            Object value = gui.getClass().getMethod("getMaid").invoke(gui);
            if (!(value instanceof Entity maid) || !TouhouLittleMaidAccess.isMaid(maid)) {
                return;
            }
            int left = ((Number) event.getClass().getMethod("getLeftPos").invoke(event)).intValue();
            int top = ((Number) event.getClass().getMethod("getTopPos").invoke(event)).intValue();
            Button button = Button.builder(Component.literal("S"), ignored ->
                    InputUtil.setScreen(new ModernPlayerModelScreen(parent,
                            (modelId, texture) -> applyModel(maid, modelId, texture),
                            "maid:" + maid.getUUID())))
                    .bounds(left + 42, top + 14, 9, 9)
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
