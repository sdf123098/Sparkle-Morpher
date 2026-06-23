package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ModelInfoCapability;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ModelInfoComponent implements Component {

    private static final String DATA_KEY = "Data";

    private final ModelInfoCapability capability = new ModelInfoCapability();

    public ModelInfoCapability capability() {
        return capability;
    }

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.getCompound("ModelInfo").ifPresent(capability::deserializeNBT);
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("ModelInfo", capability.serializeNBT());
    }

    @Override
    public void writeData(ValueOutput output) {
        CompoundTag tag = new CompoundTag();
        writeToNbt(tag, null);
        output.store(DATA_KEY, CompoundTag.CODEC, tag);
    }

    @Override
    public void readData(ValueInput input) {
        input.read(DATA_KEY, CompoundTag.CODEC).ifPresent(tag -> readFromNbt(tag, input.lookup()));
    }
}
