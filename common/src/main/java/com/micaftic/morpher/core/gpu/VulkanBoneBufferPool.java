package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;

/** Render-thread pool. A bone snapshot cannot be overwritten before its GPU fence completes. */
final class VulkanBoneBufferPool implements AutoCloseable {
    private final long bytes;
    private final List<Lease> buffers = new ArrayList<>();
    private boolean closed;

    VulkanBoneBufferPool(long bytes) { this.bytes = bytes; }

    Lease acquire() {
        if (closed) throw new IllegalStateException("Bone storage pool is closed");
        for (Lease lease : buffers) {
            if (lease.reserved) continue;
            if (lease.fence != null) {
                if (!lease.fence.awaitCompletion(0)) continue;
                if (lease.nativeComputed) VulkanBoneStorage.nativeDrawCompleted();
                lease.fence.close();
                lease.fence = null;
            }
            lease.nativeComputed = false;
            lease.reserved = true;
            return lease;
        }
        Lease lease = new Lease(RenderSystem.getDevice().createBuffer(
                () -> "sparkle_morpher_bone_storage", VulkanBoneStorage.bufferUsage(), bytes));
        buffers.add(lease);
        return lease;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (Lease lease : buffers) {
            if (lease.fence != null) lease.fence.close();
            lease.buffer.close(); // Minecraft defers the actual Vulkan destruction.
        }
        buffers.clear();
    }

    final class Lease {
        final GpuBuffer buffer;
        boolean nativeComputed;
        private GpuFence fence;
        private boolean reserved = true;
        private Lease(GpuBuffer buffer) { this.buffer = buffer; }

        void retire() {
            if (!reserved) return;
            reserved = false;
            if (!closed) fence = RenderSystem.getDevice().createCommandEncoder().createFence();
        }
    }
}
