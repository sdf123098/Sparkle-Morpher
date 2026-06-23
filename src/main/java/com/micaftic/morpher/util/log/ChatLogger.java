package com.micaftic.morpher.util.log;

import net.minecraft.client.Minecraft;
import java.util.concurrent.Executor;
import net.minecraft.network.chat.Component;

public class ChatLogger implements ILogger {

    public static final ChatLogger INSTANCE = new ChatLogger();

    private ChatLogger() {
    }

    @Override
    public void logFormatted(String str, Object... objArr) {
        logComponent(Component.literal(String.format(str, objArr)));
    }

    @Override
    public void logComponent(Component component) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        ((Executor) Minecraft.getInstance()).execute(() -> {
            Minecraft.getInstance().player.sendSystemMessage(Component.translatable("message.sparkle_morpher.model.debug_animation.output").append(component));
        });
    }
}