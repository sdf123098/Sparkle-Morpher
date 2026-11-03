package com.micaftic.morpher.mixin.client;

import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandEncoder.class)
public interface CommandEncoderAccessor {
    @Invoker("backend")
    CommandEncoderBackend sparkleMorpher$backend();
}
