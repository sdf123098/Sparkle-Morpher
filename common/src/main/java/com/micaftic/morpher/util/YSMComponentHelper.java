package com.micaftic.morpher.util;

import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.UUID;

public class YSMComponentHelper {
    public static Object createTranslatableComponent(String str, @Nullable Object[] objArr) {
        if (objArr == null || objArr.length == 0) {
            return Component.translatable(str);
        }
        return Component.translatable(str, objArr);
    }

    public static Object createLiteralComponent(@Nullable String str) {
        return Component.literal(str == null ? StringPool.EMPTY : str);
    }

    public static Object appendComponents(Object obj, Object obj2) {
        return ((MutableComponent) obj).append((Component) obj2);
    }

    public static int[] parseTextureIndices(String[] textureNames) {
        HashMap<String, Integer> indexMap = new HashMap<>();
        for(int i = 0; i < textureNames.length; ++i) {
            indexMap.put(textureNames[i] + ".png", i);
        }

        return indexMap.keySet().stream().mapToInt(indexMap::get).toArray();
    }
    public static UUID getClientPlayerUUID() {
        return Minecraft.getInstance().getUser().getProfileId();
    }

    public static int getAvailableCpuCores() {
        return Runtime.getRuntime().availableProcessors();
    }
}
