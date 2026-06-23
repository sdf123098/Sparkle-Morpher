package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.util.NetworkOnlineDebugLog;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class ModelInfoComponent implements Component {

    private final ModelInfoCapability capability = new ModelInfoCapability();

    public ModelInfoCapability capability() {
        return capability;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("ModelInfo", Tag.TAG_COMPOUND)) {
            readModelInfo(tag.getCompound("ModelInfo"), "nested");
        } else if (tag.contains("model_id", Tag.TAG_STRING) || tag.contains("select_texture", Tag.TAG_STRING)) {
            readModelInfo(tag, "flat");
        } else if (!tag.isEmpty()) {
            NetworkOnlineDebugLog.warn("ModelInfoComponent read skipped: no model info keys, keys={}", tag.getAllKeys());
        } else {
            NetworkOnlineDebugLog.info("ModelInfoComponent read empty tag");
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag data = capability.serializeNBT();
        tag.put("ModelInfo", data);
        NetworkOnlineDebugLog.info("ModelInfoComponent write: modelId={} texture={} keys={}",
                capability.getModelId(), capability.getSelectTexture(), data.getAllKeys());
    }

    private void readModelInfo(CompoundTag tag, String format) {
        capability.deserializeNBT(tag);
        NetworkOnlineDebugLog.info("ModelInfoComponent read {}: modelId={} texture={} keys={}",
                format, capability.getModelId(), capability.getSelectTexture(), tag.getAllKeys());
    }
}
