package com.micaftic.morpher.config;

import com.micaftic.morpher.YesSteveModel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(Registries.SOUND_EVENT, YesSteveModel.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> CUSTOM_SOUND = REGISTER.register("custom",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "custom"), 16.0f));
}