package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.AuthModelsCapability;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class AuthModelsComponent implements Component {

    private final AuthModelsCapability capability = new AuthModelsCapability();

    public AuthModelsCapability capability() {
        return capability;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = tag.getList("AuthModels", Tag.TAG_STRING);
        capability.deserializeNBT(list);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("AuthModels", capability.serializeNBT());
    }
}
