package com.micaftic.morpher.geckolib3.util.json;

import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.resource.YSMClientMapper;
import com.micaftic.morpher.util.data.OrderedStringMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.tuple.Pair;

import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * error.sparkle_morpher.decode_texture
 * 加载失败字段
 */
public class JsonTextureUtils {
    /**
     * 给player用的
     */
    public static OrderedStringMap<String, OuterFileTexture> getTextures(Map<String, byte[]> resource, JsonElement element) {
        if (element == null) return null;

        if (!element.isJsonArray()) return null;

        List<String> keys = Lists.newArrayList();
        List<OuterFileTexture> values = Lists.newArrayList();

        for (JsonElement rawTexture : element.getAsJsonArray()) {
            Pair<String, OuterFileTexture> texture = getTexture(resource, rawTexture);

            if (texture == null) continue;

            keys.add(texture.getKey());
            values.add(texture.getValue());
        }

        return new OrderedStringMap<>(keys.toArray(new String[0]), values.toArray(new OuterFileTexture[0]));
    }

    public static Pair<String, OuterFileTexture> getTexture(Map<String, byte[]> resource, @Nullable JsonElement element) {
        if (element == null) return null;

        if (element.isJsonObject()) {
            JsonObject jsonObj = element.getAsJsonObject();

            if (jsonObj.has("uv")) {
                String uvPath = jsonObj.get("uv").getAsString();
                String name = extractTextureName(uvPath);
                OuterFileTexture texture = createTexture(resource.get(uvPath));
                Map<ShadersTextureType, OuterFileTexture> fbo = new HashMap<>();
                if (jsonObj.has("normal")) {
                    String normalPath = jsonObj.get("normal").getAsString();
                    fbo.put(ShadersTextureType.NORMAL, createTexture(resource.get(normalPath)));
                }
                if (jsonObj.has("specular")) {
                    String specularPath = jsonObj.get("specular").getAsString();
                    fbo.put(ShadersTextureType.SPECULAR, createTexture(resource.get(specularPath)));
                }
                texture.setSuffixTextures(fbo);

                return Pair.of(name, texture);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String texPath = element.getAsString();
            String name = extractTextureName(texPath);
            return Pair.of(name, createTexture(resource.get(texPath)));
        }

        return null;
    }

    private static OuterFileTexture createTexture(byte[] data) {
        return new OuterFileTexture(data == null ? null : YSMClientMapper.toPng(data, 0, 0, 0));
    }

    private static String extractTextureName(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= lastSlash || lastDot == -1) {
            // no extension found, use the whole filename
            return path.substring(lastSlash + 1);
        }
        return path.substring(lastSlash + 1, lastDot);
    }
}
