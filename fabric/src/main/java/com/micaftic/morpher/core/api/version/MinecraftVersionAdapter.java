package com.micaftic.morpher.core.api.version;

/** Stable version capability boundary for business code.
 *
 * <p>The surface flags describe whether a stable adapter boundary exists. The
 * more specific capability methods describe optional native facilities inside
 * that boundary (for example SubmitNodeCollector or Blaze3D RenderPipeline).
 * This distinction lets older branches keep the same business API while
 * accurately reporting unavailable native features.</p>
 */
public interface MinecraftVersionAdapter {
    String minecraftVersion();

    boolean supportsSubmitNodeCollector();

    boolean supportsBlaze3dGpuPipeline();

    boolean supportsGuiGraphicsExtractor();

    boolean supports(VersionAdapterSurface surface);
}
