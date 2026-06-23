package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.StarModelsCapability;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.ladysnake.cca.api.v3.component.Component;

public final class StarModelsComponent implements Component {

    private final StarModelsCapability capability = new StarModelsCapability();

    public StarModelsCapability capability() {
        return capability;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = tag.getList("StarModels", Tag.TAG_STRING);
        capability.deserializeNBT(list);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("StarModels", capability.serializeNBT());
    }
}
