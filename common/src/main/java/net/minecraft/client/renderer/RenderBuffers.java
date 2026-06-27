package net.minecraft.client.renderer;

public class RenderBuffers implements AutoCloseable {
    private final MultiBufferSource.BufferSource bufferSource = new MultiBufferSource.BufferSource();

    public RenderBuffers() {
    }

    public RenderBuffers(int bufferSize) {
    }

    public MultiBufferSource.BufferSource bufferSource() {
        return bufferSource;
    }

    @Override
    public void close() {
    }
}
