package com.micaftic.morpher.core.api.version;

/** Immutable capability snapshot exposed to version-agnostic runtime code. */
public record VersionAdapterCapabilities(
        String minecraftVersion,
        boolean render,
        boolean gui,
        boolean packet,
        boolean entity,
        boolean resource,
        boolean camera,
        boolean capabilityComponent,
        boolean gpuRenderPipeline) {

    public static VersionAdapterCapabilities from(MinecraftVersionAdapter adapter) {
        return new VersionAdapterCapabilities(
                adapter.minecraftVersion(),
                adapter.supports(VersionAdapterSurface.RENDER),
                adapter.supports(VersionAdapterSurface.GUI),
                adapter.supports(VersionAdapterSurface.PACKET),
                adapter.supports(VersionAdapterSurface.ENTITY),
                adapter.supports(VersionAdapterSurface.RESOURCE),
                adapter.supports(VersionAdapterSurface.CAMERA),
                adapter.supports(VersionAdapterSurface.CAPABILITY_COMPONENT),
                adapter.supports(VersionAdapterSurface.GPU_RENDER_PIPELINE));
    }

    public boolean supports(VersionAdapterSurface surface) {
        return switch (surface) {
            case RENDER -> render;
            case GUI -> gui;
            case PACKET -> packet;
            case ENTITY -> entity;
            case RESOURCE -> resource;
            case CAMERA -> camera;
            case CAPABILITY_COMPONENT -> capabilityComponent;
            case GPU_RENDER_PIPELINE -> gpuRenderPipeline;
        };
    }
}
