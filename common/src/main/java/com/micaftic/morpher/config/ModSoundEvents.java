package com.micaftic.morpher.config;

import com.micaftic.morpher.YesSteveModel;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public class ModSoundEvents {

    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(YesSteveModel.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> CUSTOM_SOUND = REGISTER.register("custom",
            () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(YesSteveModel.MOD_ID, "custom"), 16.0f));
}
