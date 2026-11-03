package com.micaftic.morpher.core.api.version;

/** Stable API surfaces which must not leak Minecraft-version details into business code. */
public enum VersionAdapterSurface {
    RENDER, GUI, PACKET, ENTITY, RESOURCE, CAMERA, CAPABILITY_COMPONENT, GPU_RENDER_PIPELINE
}
