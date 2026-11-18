package com.micaftic.morpher.client.animation.molang.struct;

import com.micaftic.morpher.client.render.BoneIds;
import dev.spm.core.scene.MolangValue;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Synchronized per-entity float variable container ({@code v.roaming.*}) with optional
 * delta change tracking and immutable snapshots for {@code spm-core}.
 */
public class RoamingStruct {

    public static final int MAX_VARS = 64;

    public static final int MAX_VAR_NAME_LENGTH = 32;

    private final Int2FloatOpenHashMap floatVars;

    private final IntOpenHashSet varNames;

    private final int modelHashId;

    private final boolean trackChanges;

    private RoamingSyncBatch pendingBoneData;

    private boolean dirty = false;

    public RoamingStruct(int modelHashId, Int2FloatOpenHashMap floatVars) {
        this(modelHashId, floatVars, true);
    }

    private RoamingStruct(int modelHashId, Int2FloatOpenHashMap floatVars, boolean trackChanges) {
        this.pendingBoneData = new RoamingSyncBatch(modelHashId, 4);
        this.floatVars = floatVars == null ? new Int2FloatOpenHashMap() : floatVars;
        this.varNames = new IntOpenHashSet(this.floatVars.keySet());
        this.modelHashId = modelHashId;
        this.trackChanges = trackChanges;
    }

    public static RoamingStruct readOnly(Int2FloatOpenHashMap floatVars) {
        return new RoamingStruct(0, floatVars, false);
    }

    public float getProperty(int id) {
        return this.floatVars.get(id);
    }

    public float getFloat(String name) {
        return this.floatVars.get(BoneIds.id(name));
    }

    public void putProperty(int nameId, float value) {
        if (!Float.isFinite(value)) {
            value = 0.0f;
        }
        float previous = this.floatVars.put(nameId, value);
        if (Float.compare(value, previous) == 0 && this.varNames.contains(nameId)) {
            return;
        }
        this.varNames.add(nameId);
        if (!this.trackChanges || this.varNames.size() > MAX_VARS) {
            return;
        }
        this.pendingBoneData.changedVariables().put(nameId, value);
        this.dirty = true;
    }

    public void putProperty(int nameId, Object value) {
        putProperty(nameId, toFloat(value));
    }

    public void putFloat(String name, float value) {
        putProperty(BoneIds.id(name), value);
    }

    public boolean isWritable() {
        return this.trackChanges;
    }

    public void merge(Int2FloatMap values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        ObjectIterator<Int2FloatMap.Entry> it = values.int2FloatEntrySet().iterator();
        while (it.hasNext()) {
            Int2FloatMap.Entry entry = it.next();
            this.floatVars.put(entry.getIntKey(), entry.getFloatValue());
            this.varNames.add(entry.getIntKey());
        }
    }

    public boolean hasPendingChanges() {
        return this.dirty;
    }

    public RoamingSyncBatch consumePendingBoneData() {
        RoamingSyncBatch syncBatch = this.pendingBoneData;
        this.pendingBoneData = new RoamingSyncBatch(this.modelHashId, syncBatch.changedVariables().size());
        this.dirty = false;
        return syncBatch;
    }

    public void forEachVar(Consumer<String> consumer) {
        IntIterator it = this.varNames.iterator();
        while (it.hasNext()) {
            String str = BoneIds.name(it.nextInt());
            if (!str.isEmpty()) {
                consumer.accept(str);
            }
        }
    }

    public Map<String, Float> snapshotNumbers(int maxEntries) {
        int limit = Math.max(0, maxEntries);
        Map<String, Float> out = new LinkedHashMap<>();
        ObjectIterator<Int2FloatMap.Entry> it = this.floatVars.int2FloatEntrySet().iterator();
        while (it.hasNext() && out.size() < limit) {
            Int2FloatMap.Entry entry = it.next();
            String name = BoneIds.name(entry.getIntKey());
            float value = entry.getFloatValue();
            if (!name.isBlank() && name.length() <= MAX_VAR_NAME_LENGTH && Float.isFinite(value)) {
                out.put(name, value);
            }
        }
        return Map.copyOf(out);
    }

    public MolangValue.StructValue toMolangStruct() {
        if (this.floatVars.isEmpty()) {
            return MolangValue.struct(Map.of());
        }
        Map<String, MolangValue> fields = new LinkedHashMap<>(this.floatVars.size());
        ObjectIterator<Int2FloatMap.Entry> it = this.floatVars.int2FloatEntrySet().iterator();
        while (it.hasNext()) {
            Int2FloatMap.Entry entry = it.next();
            String name = BoneIds.name(entry.getIntKey());
            float value = entry.getFloatValue();
            if (!name.isBlank() && Float.isFinite(value)) {
                fields.put(name, MolangValue.number(value));
            }
        }
        return MolangValue.struct(fields);
    }

    public void applyMolangDiff(MolangValue value) {
        if (!(value instanceof MolangValue.StructValue struct)) {
            return;
        }
        for (Map.Entry<String, MolangValue> entry : struct.fields().entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank() || name.length() > MAX_VAR_NAME_LENGTH) {
                continue;
            }
            float next = (float) entry.getValue().number();
            if (!Float.isFinite(next)) {
                continue;
            }
            int id = BoneIds.id(name);
            if (!this.floatVars.containsKey(id) || Float.compare(this.floatVars.get(id), next) != 0) {
                if (this.varNames.size() < MAX_VARS || this.varNames.contains(id)) {
                    putProperty(id, next);
                }
            }
        }
    }

    private static float toFloat(Object value) {
        if (value instanceof Number n) {
            float f = n.floatValue();
            return Float.isFinite(f) ? f : 0.0f;
        }
        if (value instanceof Boolean b) {
            return b ? 1.0f : 0.0f;
        }
        if (value instanceof MolangValue mv) {
            float f = (float) mv.number();
            return Float.isFinite(f) ? f : 0.0f;
        }
        return 0.0f;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("roaming{");
        boolean first = true;
        ObjectIterator<Int2FloatMap.Entry> it = this.floatVars.int2FloatEntrySet().iterator();
        while (it.hasNext()) {
            Int2FloatMap.Entry entry = it.next();
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(String.format("%s=%s", BoneIds.name(entry.getIntKey()), entry.getFloatValue()));
        }
        sb.append("}");
        return sb.toString();
    }
}
