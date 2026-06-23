package com.micaftic.morpher.capability.fabric;

import com.micaftic.morpher.capability.ProjectileModelCapability;
import org.ladysnake.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class ProjectileModelComponent implements Component {

    private final ProjectileModelCapability capability = new ProjectileModelCapability();

    public ProjectileModelCapability capability() {
        return capability;
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("ProjectileModel", Tag.TAG_COMPOUND)) {
            capability.deserializeNBT(tag.getCompound("ProjectileModel"));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("ProjectileModel", capability.serializeNBT());
    }
}
