package com.micaftic.morpher.fabric.mixin.client;

import com.micaftic.morpher.audio.YSMSoundInstance;
import net.fabricmc.fabric.api.client.sound.v1.FabricSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;

import java.util.concurrent.CompletableFuture;

@Mixin(YSMSoundInstance.class)
public abstract class YSMSoundInstanceFabricMixin implements FabricSoundInstance {

    @Override
    public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean looping) {
        return ((YSMSoundInstance) (Object) this).getStream(loader, null, looping);
    }
}
