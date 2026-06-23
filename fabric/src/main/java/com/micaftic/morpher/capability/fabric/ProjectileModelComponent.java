package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ProjectileModelCapability;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class ProjectileModelComponent implements Component {

    private static final String DATA_KEY = "Data";

    private final ProjectileModelCapability capability = new ProjectileModelCapability();

    public ProjectileModelCapability capability() {
        return capability;
    }

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.getCompound("ProjectileModel").ifPresent(capability::deserializeNBT);
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("ProjectileModel", capability.serializeNBT());
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
