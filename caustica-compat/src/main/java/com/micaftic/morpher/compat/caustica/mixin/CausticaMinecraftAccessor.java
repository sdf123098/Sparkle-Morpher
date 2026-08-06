package com.micaftic.morpher.compat.caustica.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface CausticaMinecraftAccessor {
    @Invoker("getResourcePackRepository")
    PackRepository sparkle_morpher_caustica$getResourcePackRepository();
}
