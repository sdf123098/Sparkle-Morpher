package com.micaftic.morpher.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

/** SPM-owned vertex buffer contract for Minecraft versions using submit rendering. */
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType renderType);

    interface BufferSource extends MultiBufferSource {
        default void endBatch() {
        }

        default void endBatch(RenderType renderType) {
        }
    }
}