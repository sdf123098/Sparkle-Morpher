package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.AuthModelsCapability;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class AuthModelsComponent implements Component {

    private static final String DATA_KEY = "Data";

    private final AuthModelsCapability capability = new AuthModelsCapability();

    public AuthModelsCapability capability() {
        return capability;
    }

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = tag.getList("AuthModels").orElse(new ListTag());
        capability.deserializeNBT(list);
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("AuthModels", capability.serializeNBT());
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
