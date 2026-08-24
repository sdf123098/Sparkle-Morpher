package com.micaftic.morpher.resource.bbmodel;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;

/**
 * 基于字段值的多态类型适配器
 * 用于处理 BBElement 的不同类型（cube、mesh、locator、null_object）
 */
public class GsonTypeByField<T> implements JsonDeserializer<T> {
    private final Class<T> baseClass;
    private final String fieldName;
    private final java.util.Map<String, Class<? extends T>> typeMap;

    public GsonTypeByField(Class<T> baseClass, String fieldName, java.util.Map<String, Class<? extends T>> typeMap) {
        this.baseClass = baseClass;
        this.fieldName = fieldName;
        this.typeMap = typeMap;
    }

    @Override
    public T deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement typeElement = jsonObject.get(fieldName);

        if (typeElement == null) {
            return context.deserialize(json, baseClass);
        }

        String typeValue = typeElement.getAsString();
        Class<? extends T> targetClass = typeMap.get(typeValue);

        if (targetClass == null) {
            return context.deserialize(json, baseClass);
        }

        return context.deserialize(json, targetClass);
    }
}
