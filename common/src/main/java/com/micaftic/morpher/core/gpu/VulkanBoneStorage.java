package com.micaftic.morpher.core.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.util.spvc.SpvcReflectedResource;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;

/**
 * Storage-buffer extension to Minecraft's Vulkan backend. UNIFORM_BUFFER is only
 * the public API carrier: the native buffer, descriptor layout and descriptor
 * writes all use STORAGE_BUFFER. Uploads, barriers and deferred destruction stay
 * on Minecraft's device and command encoder.
 */
public final class VulkanBoneStorage {
    public static final String NAME = "SpmBoneStorage";
    // Reserved by this extension; vanilla's current usage bits end at 1 << 9.
    public static final int STORAGE_USAGE = 1 << 20;
    private static final java.util.concurrent.atomic.AtomicLong usageMappings = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong storageLayouts = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong storageWrites = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong nativeDraws = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong completedNativeDraws = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Map<String, java.util.concurrent.atomic.AtomicLong> drawPhases = new java.util.concurrent.ConcurrentSkipListMap<>();

    private VulkanBoneStorage() { }

    public static int bufferUsage() {
        return GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST | STORAGE_USAGE;
    }

    public static int nativeUsage(int usage, int nativeUsage) {
        if ((usage & STORAGE_USAGE) == 0) return nativeUsage;
        usageMappings.incrementAndGet();
        return nativeUsage | VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
    }

    public static void patchLayout(VkDescriptorSetLayoutCreateInfo info, List<String> names) {
        var bindings = info.pBindings();
        if (bindings == null || bindings.remaining() != names.size()) {
            throw new IllegalStateException("Vulkan descriptor layout/name count mismatch");
        }
        for (int i = 0; i < names.size(); i++) {
            if (NAME.equals(names.get(i))) {
                bindings.get(bindings.position() + i).descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                storageLayouts.incrementAndGet();
            }
        }
    }

    public static void patchWrites(VkWriteDescriptorSet.Buffer writes, List<String> names) {
        for (int i = writes.position(); i < writes.limit(); i++) {
            var write = writes.get(i);
            int binding = write.dstBinding();
            if (binding >= 0 && binding < names.size() && NAME.equals(names.get(binding))) {
                if (write.pBufferInfo() == null) {
                    throw new IllegalStateException("Bone storage descriptor has no buffer range");
                }
                write.descriptorType(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER);
                storageWrites.incrementAndGet();
            }
        }
    }

    public static void nativeDrawSubmitted() { nativeDraws.incrementAndGet(); }
    public static void nativeDrawCompleted() { completedNativeDraws.incrementAndGet(); }
    public static void drawPhase(String phase) {
        drawPhases.computeIfAbsent(phase, key -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
    }
    public static String diagnostics() {
        return "native_buffer_mappings=" + usageMappings.get() + " storage_layouts=" + storageLayouts.get()
                + " storage_descriptor_writes=" + storageWrites.get() + " simd_gpu_draws=" + nativeDraws.get()
                + " fence_completed_simd_snapshots=" + completedNativeDraws.get() + " draw_phases=" + drawPhases;
    }

    /** Returns the SPIR-V word offset of our real SSBO's binding decoration. */
    public static int storageBindingOffset(ByteBuffer spirv) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.callocPointer(1);
            check(Spvc.spvc_context_create(pointer));
            long context = pointer.get(0);
            try {
                IntBuffer words = spirv.asIntBuffer();
                check(Spvc.spvc_context_parse_spirv(context, words, words.remaining(), pointer));
                long ir = pointer.get(0);
                check(Spvc.spvc_context_create_compiler(context, Spvc.SPVC_BACKEND_NONE, ir,
                        Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pointer));
                long compiler = pointer.get(0);
                check(Spvc.spvc_compiler_create_shader_resources(compiler, pointer));
                long resources = pointer.get(0);
                PointerBuffer count = stack.callocPointer(1);
                check(Spvc.spvc_resources_get_resource_list_for_type(resources,
                        Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER, pointer, count));
                var reflected = SpvcReflectedResource.create(pointer.get(0), Math.toIntExact(count.get(0)));
                IntBuffer offset = stack.callocInt(1);
                for (int i = 0; i < reflected.remaining(); i++) {
                    var resource = reflected.get(i);
                    if (NAME.equals(resource.nameString())) {
                        // SpvDecorationBinding. SPIRV-Cross returns a word offset.
                        if (!Spvc.spvc_compiler_get_binary_offset_for_decoration(compiler, resource.id(), 33, offset)) {
                            throw new IllegalStateException("Bone storage has no SPIR-V binding");
                        }
                        return offset.get(0);
                    }
                }
                return -1; // Fragment stages have no bone storage block.
            } finally {
                Spvc.spvc_context_destroy(context);
            }
        }
    }

    private static void check(int result) {
        if (result != Spvc.SPVC_SUCCESS) {
            throw new IllegalStateException("SPIRV-Cross storage reflection failed: " + result);
        }
    }
}
