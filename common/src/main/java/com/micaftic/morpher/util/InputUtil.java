package com.micaftic.morpher.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

import java.lang.reflect.Field;

public class InputUtil {
    private static final Field MINECRAFT_MOUSE_HANDLER_FIELD = findFieldByType(Minecraft.class, MouseHandler.class);

    public static boolean isKeyPressed(int keyCode, int scanCode, KeyMapping keyMapping) {
        return KeyMappingFactory.isActiveAndMatches(keyMapping, keyCode, scanCode);
    }

    public static boolean isKeyPressed(int keyCode, int scanCode, int modifiers, KeyMapping keyMapping) {
        return KeyMappingFactory.isActiveAndMatches(keyMapping, keyCode, scanCode, modifiers);
    }

    public static boolean isMousePressed(int button, KeyMapping keyMapping) {
        return KeyMappingFactory.isMouseActiveAndMatches(keyMapping, button);
    }

    public static boolean isMousePressed(int button, int modifiers, KeyMapping keyMapping) {
        return KeyMappingFactory.isMouseActiveAndMatches(keyMapping, button, modifiers);
    }

    public static Screen getCurrentScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.gui.screen();
    }

    public static void setScreen(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            minecraft.gui.setScreen(screen);
        }
    }

    public static boolean isPlayerReady() {
        Minecraft minecraft = Minecraft.getInstance();
        if (getCurrentScreen() != null || !isMouseGrabbed(minecraft)) {
            return false;
        }
        return minecraft.isWindowActive();
    }

    private static boolean isMouseGrabbed(Minecraft minecraft) {
        Object value = getFieldValue(minecraft, MINECRAFT_MOUSE_HANDLER_FIELD);
        return value instanceof MouseHandler mouseHandler && mouseHandler.isMouseGrabbed();
    }

    private static Field findFieldByType(Class<?> owner, Class<?> type) {
        Class<?> current = owner;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        return field;
                    } catch (RuntimeException ignored) {
                        return null;
                    }
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Object getFieldValue(Object owner, Field field) {
        if (owner == null || field == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
