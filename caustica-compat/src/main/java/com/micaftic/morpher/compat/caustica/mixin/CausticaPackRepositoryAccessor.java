package com.micaftic.morpher.compat.caustica.mixin;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(PackRepository.class)
public interface CausticaPackRepositoryAccessor {
    @Accessor("sources")
    Set<RepositorySource> sparkle_morpher_caustica$getSources();

    @Mutable
    @Accessor("sources")
    void sparkle_morpher_caustica$setSources(Set<RepositorySource> sources);
}
