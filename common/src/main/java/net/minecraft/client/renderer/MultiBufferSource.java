package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;

public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType renderType);

    class BufferSource implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return null;
        }

        public void endBatch() {
        }

        public void endBatch(RenderType renderType) {
        }
    }
}
