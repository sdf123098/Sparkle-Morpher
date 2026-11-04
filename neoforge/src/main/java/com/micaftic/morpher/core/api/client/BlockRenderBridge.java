package com.micaftic.morpher.core.api.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Objects;

/** Loader-owned block preview rendering, installed during client setup. */
public final class BlockRenderBridge {
    private BlockRenderBridge() {}

    @FunctionalInterface
    public interface Renderer {
        void render(BlockState state, PoseStack poses, MultiBufferSource buffers, int light, int overlay);
    }

    private static volatile Renderer renderer;

    public static void install(Renderer renderer) {
        BlockRenderBridge.renderer = Objects.requireNonNull(renderer);
    }

    public static void render(BlockState state, PoseStack poses, MultiBufferSource buffers, int light, int overlay) {
        Renderer current = renderer;
        if (current == null) throw new IllegalStateException("Block preview renderer is not initialized");
        current.render(state, poses, buffers, light, overlay);
    }
}
