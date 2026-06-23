package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.VehicleModelCapability;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class VehicleModelComponent implements Component {

    private final VehicleModelCapability capability = new VehicleModelCapability();

    public VehicleModelCapability capability() {
        return capability;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("VehicleModel", Tag.TAG_COMPOUND)) {
            capability.deserializeNBT(tag.getCompound("VehicleModel"));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("VehicleModel", capability.serializeNBT());
    }
}
