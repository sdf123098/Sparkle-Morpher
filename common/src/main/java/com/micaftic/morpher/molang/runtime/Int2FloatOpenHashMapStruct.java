package com.micaftic.morpher.molang.runtime;

import com.micaftic.morpher.geckolib3.core.molang.util.StringPool;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

public class Int2FloatOpenHashMapStruct implements Struct {

    private final Int2FloatOpenHashMap properties;

    public Int2FloatOpenHashMapStruct(Int2FloatOpenHashMap properties) {
        this.properties = properties;
    }

    @Override
    public Object getProperty(int name) {
        return this.properties.get(name);
    }

    @Override
    public void putProperty(int name, Object value) {
        this.properties.put(name, ValueConversions.asFloat(value));
    }

    public void merge(Int2FloatMap int2FloatMap) {
        this.properties.putAll(int2FloatMap);
    }

    /** Call after the owning capability has joined its render task. */
    public java.util.Map<String, Float> snapshotNumbers(int limit) {
        java.util.Map<String, Float> values = new java.util.LinkedHashMap<>();
        for (Int2FloatMap.Entry entry : this.properties.int2FloatEntrySet()) {
            String name = StringPool.getString(entry.getIntKey());
            if (values.size() >= limit) break;
            if (!name.isBlank() && name.length() <= 128 && !name.chars().anyMatch(Character::isISOControl)
                    && Float.isFinite(entry.getFloatValue())) values.put(name, entry.getFloatValue());
        }
        return java.util.Map.copyOf(values);
    }

    @Override
    public Struct copy() {
        HashMapStruct hashMapStruct = new HashMapStruct(true);
        for (Int2FloatMap.Entry entry : this.properties.int2FloatEntrySet()) {
            hashMapStruct.putProperty(entry.getIntKey(), entry.getFloatValue());
        }
        return hashMapStruct;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("roaming{");
        boolean z = true;
        for (Int2FloatMap.Entry entry : this.properties.int2FloatEntrySet()) {
            if (!z) {
                sb.append(", ");
            }
            z = false;
            sb.append(String.format("%s=%s", StringPool.getString(entry.getIntKey()), Float.valueOf(entry.getFloatValue())));
        }
        sb.append("}");
        return sb.toString();
    }
}
