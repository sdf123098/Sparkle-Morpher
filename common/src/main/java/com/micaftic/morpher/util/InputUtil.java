package com.micaftic.morpher.util;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import com.micaftic.morpher.core.api.client.KeyMappingFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class InputUtil {
    private static final Field MINECRAFT_SCREEN_FIELD = findFieldByType(Minecraft.class, Screen.class);
    private static final Field MINECRAFT_MOUSE_HANDLER_FIELD = findFieldByType(Minecraft.class, MouseHandler.class);
    private static final Method MINECRAFT_SET_SCREEN_METHOD = findSetScreenMethod();

    public static boolean isKeyPressed(int keyCode, int scanCode, KeyMapping keyMapping) {
        return KeyMappingFactory.isActiveAndMatches(keyMapping, keyCode, scanCode);
    }

    public static Screen getCurrentScreen() {
        Object value = getFieldValue(Minecraft.getInstance(), MINECRAFT_SCREEN_FIELD);
        return value instanceof Screen screen ? screen : null;
    }

    public static void setScreen(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || MINECRAFT_SET_SCREEN_METHOD == null) {
            return;
        }
        try {
            MINECRAFT_SET_SCREEN_METHOD.invoke(minecraft, screen);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
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

    private static Method findSetScreenMethod() {
        for (String name : new String[]{"setScreenAndShow", "setScreen"}) {
            try {
                Method method = Minecraft.class.getMethod(name, Screen.class);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        for (Method method : Minecraft.class.getMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getReturnType() == Void.TYPE && parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(Screen.class)) {
                try {
                    method.setAccessible(true);
                    return method;
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
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
