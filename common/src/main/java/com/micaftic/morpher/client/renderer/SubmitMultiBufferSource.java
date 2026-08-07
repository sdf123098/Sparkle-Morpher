package com.micaftic.morpher.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapts the 26.2 submit renderer to APIs that still emit vertices through a
 * {@link MultiBufferSource}. The submitted callback runs after the model layer
 * has populated the recording consumer, so the complete vertex stream can be
 * replayed into Minecraft's target buffer.
 */
public final class SubmitMultiBufferSource implements MultiBufferSource {
    private final SubmitNodeCollector collector;
    private final PoseStack poseStack;
    private final Map<RenderType, RecordingVertexConsumer> buffers = new IdentityHashMap<>();

    public SubmitMultiBufferSource(SubmitNodeCollector collector, PoseStack poseStack) {
        this.collector = collector;
        this.poseStack = poseStack;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return this.buffers.computeIfAbsent(renderType, type -> {
            RecordingVertexConsumer buffer = new RecordingVertexConsumer();
            this.collector.submitCustomGeometry(this.poseStack, type, (pose, target) -> buffer.replay(target));
            return buffer;
        });
    }

    private static final class RecordingVertexConsumer implements VertexConsumer {
        private final List<Consumer<VertexConsumer>> operations = new ArrayList<>();

        private void record(Consumer<VertexConsumer> operation) {
            this.operations.add(operation);
        }

        private void replay(VertexConsumer target) {
            for (Consumer<VertexConsumer> operation : this.operations) {
                operation.accept(target);
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            record(buffer -> buffer.addVertex(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            record(buffer -> buffer.setColor(red, green, blue, alpha));
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            record(buffer -> buffer.setColor(color));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            record(buffer -> buffer.setUv(u, v));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            record(buffer -> buffer.setUv1(u, v));
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            record(buffer -> buffer.setUv2(u, v));
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            record(buffer -> buffer.setNormal(x, y, z));
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float lineWidth) {
            record(buffer -> buffer.setLineWidth(lineWidth));
            return this;
        }
    }
}
