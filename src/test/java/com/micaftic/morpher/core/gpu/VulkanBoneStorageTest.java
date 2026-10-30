package com.micaftic.morpher.core.gpu;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class VulkanBoneStorageTest {
    @Test
    void compilesProductionVertexShaderAndReflectsRealStorageBuffer() throws Exception {
        String source = expand("assets/sparkle_morpher/shaders/core/blaze3d_bone_skin.vsh");
        long compiler = Shaderc.shaderc_compiler_initialize();
        long options = Shaderc.shaderc_compile_options_initialize();
        long result = 0;
        try {
            Shaderc.shaderc_compile_options_set_target_env(options, Shaderc.shaderc_target_env_vulkan,
                    Shaderc.shaderc_env_version_vulkan_1_2);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            result = Shaderc.shaderc_compile_into_spv(compiler, source, Shaderc.shaderc_glsl_vertex_shader,
                    "sparkle_morpher:blaze3d_bone_skin.vsh", "main", options);
            assertEquals(Shaderc.shaderc_compilation_status_success,
                    Shaderc.shaderc_result_get_compilation_status(result), Shaderc.shaderc_result_get_error_message(result));
            ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
            int bindingOffset = VulkanBoneStorage.storageBindingOffset(spirv);
            assertTrue(bindingOffset >= 5, "Production shader must expose an actual SSBO binding");
        } finally {
            if (result != 0) Shaderc.shaderc_result_release(result);
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    @Test
    void extendsOnlyTheNamedStorageBindingAndUsageBit() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            bindings.get(0).binding(0).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            bindings.get(1).binding(1).descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER);
            var info = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings);
            List<String> names = List.of("Projection", VulkanBoneStorage.NAME);
            VulkanBoneStorage.patchLayout(info, names);
            assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, bindings.get(0).descriptorType());
            assertEquals(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, bindings.get(1).descriptorType());
            var writes = VkWriteDescriptorSet.calloc(2, stack);
            for (int i = 0; i < 2; i++) writes.get(i).sType$Default().dstBinding(i)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
                    .pBufferInfo(VkDescriptorBufferInfo.calloc(1, stack).buffer(1).offset(0).range(144));
            VulkanBoneStorage.patchWrites(writes, names);
            assertEquals(VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, writes.get(0).descriptorType());
            assertEquals(VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, writes.get(1).descriptorType());
            assertEquals(VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
                    VulkanBoneStorage.nativeUsage(8, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT));
            assertTrue((VulkanBoneStorage.nativeUsage(VulkanBoneStorage.bufferUsage(), 0)
                    & VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT) != 0);
        }
    }

    private static String expand(String path) throws IOException {
        String text;
        try (var input = VulkanBoneStorageTest.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) throw new IOException("Missing shader resource: " + path);
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var matcher = Pattern.compile("(?m)^\\s*#moj_import\\s+<([^>]+)>.*$").matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String[] id = name.contains(":") ? name.split(":", 2) : new String[]{"minecraft", name};
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(
                    expand("assets/" + id[0] + "/shaders/include/" + id[1])
                            .replaceAll("(?m)^\\s*#version[^\\r\\n]*", "")));
        }
        return matcher.appendTail(out).toString();
    }
}
