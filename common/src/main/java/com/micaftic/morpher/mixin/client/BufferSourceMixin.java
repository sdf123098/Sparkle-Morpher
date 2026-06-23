package com.micaftic.morpher.mixin.client;

import com.micaftic.morpher.util.accessors.BufferSourceAccessor;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Iterator;
import java.util.SequencedMap;

@Mixin({MultiBufferSource.BufferSource.class})
public class BufferSourceMixin implements BufferSourceAccessor {

    @Shadow
    @Final
    protected SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;

    @Override
    @Unique
    public void initialize() {
        Iterator<RenderType> it = this.fixedBuffers.keySet().iterator();
        while (it.hasNext()) {
            ((MultiBufferSource.BufferSource) (Object) this).endBatch(it.next());
        }
    }
}