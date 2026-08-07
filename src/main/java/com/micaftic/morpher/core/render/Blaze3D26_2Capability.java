package com.micaftic.morpher.core.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;

public final class Blaze3D26_2Capability {
    private Blaze3D26_2Capability() {
    }

    public static Report report() {
        return new Report(
                true,
                hasMethod(GpuDevice.class, "createBuffer"),
                hasMethod(GpuDevice.class, "precompilePipeline"),
                hasMethod(CommandEncoder.class, "createRenderPass"),
                hasMethod(RenderPass.class, "drawIndexed"),
                hasMethod(CommandEncoder.class, "dispatch"),
                hasClass("com.mojang.blaze3d.systems.ComputePass"),
                hasClass("com.mojang.blaze3d.pipeline.ComputePipeline"),
                hasRenderPipelineBuilderMethod("withVertexShader"),
                hasRenderPipelineBuilderMethod("withFragmentShader"),
                hasRenderPipelineBuilderMethod("withComputeShader")
        );
    }

    private static boolean hasClass(String className) {
        try {
            Class.forName(className, false, Blaze3D26_2Capability.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static boolean hasMethod(Class<?> type, String name) {
        for (var method : type.getMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRenderPipelineBuilderMethod(String name) {
        for (Class<?> nested : RenderPipeline.class.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("Builder")) {
                return hasMethod(nested, name);
            }
        }
        return false;
    }

    public record Report(
            boolean stableGraphicsApiPresent,
            boolean createBufferPresent,
            boolean precompilePipelinePresent,
            boolean createRenderPassPresent,
            boolean drawIndexedPresent,
            boolean commandDispatchPresent,
            boolean computePassPresent,
            boolean computePipelinePresent,
            boolean vertexShaderBuilderPresent,
            boolean fragmentShaderBuilderPresent,
            boolean computeShaderBuilderPresent
    ) {
        public boolean stableComputeDispatchPresent() {
            return commandDispatchPresent
                    && computePassPresent
                    && computePipelinePresent
                    && computeShaderBuilderPresent;
        }

        public String summary() {
            return "stableGraphicsApiPresent=" + stableGraphicsApiPresent
                    + ", createBufferPresent=" + createBufferPresent
                    + ", precompilePipelinePresent=" + precompilePipelinePresent
                    + ", createRenderPassPresent=" + createRenderPassPresent
                    + ", drawIndexedPresent=" + drawIndexedPresent
                    + ", stableComputeDispatchPresent=" + stableComputeDispatchPresent()
                    + ", commandDispatchPresent=" + commandDispatchPresent
                    + ", computePassPresent=" + computePassPresent
                    + ", computePipelinePresent=" + computePipelinePresent
                    + ", vertexShaderBuilderPresent=" + vertexShaderBuilderPresent
                    + ", fragmentShaderBuilderPresent=" + fragmentShaderBuilderPresent
                    + ", computeShaderBuilderPresent=" + computeShaderBuilderPresent;
        }
    }
}
