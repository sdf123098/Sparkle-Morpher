package com.micaftic.morpher.config;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.core.architectury.registry.registries.DeferredRegister;
import com.micaftic.morpher.core.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class ModSoundEvents {

    public static final DeferredRegister<SoundEvent> REGISTER = DeferredRegister.create(YesSteveModel.MOD_ID, Registries.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> CUSTOM_SOUND = REGISTER.register("custom",
            () -> SoundEvent.createFixedRangeEvent(Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "custom"), 16.0f));
}
