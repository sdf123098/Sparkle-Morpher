package com.micaftic.morpher.client.compat;

import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import net.minecraft.resources.Identifier;

/**
 * Optional renderer integration point. Implementations live in separate compatibility mods so the
 * model and texture pipeline never needs to know which renderer consumes these notifications.
 */
public interface ClientRenderCompatibility {
    /** Allows an embedded compatibility module to stay dormant when its target renderer is absent. */
    default boolean isAvailable() {
        return true;
    }

    /**
     * True when the renderer needs glow bones submitted as a separate emissive RenderType pass
     * (e.g. to distinguish emissive material semantics at capture boundaries). Enabling this
     * splits the model into NON_GLOW + GLOW passes and therefore bypasses the GPU/SIMD fast
     * paths, so it must only be requested by renderers that actually consume the distinction.
     */
    default boolean requiresEmissiveBoneSplit() {
        return false;
    }

    /** Called immediately after discovery, before Sparkle Morpher starts loading client models. */
    default void initialize() {
    }

    /** Returns a stable texture location when the renderer needs content-addressed material assets. */
    default Identifier resolveTextureLocation(OuterFileTexture texture) {
        return null;
    }

    default void onModelAssemblyCreated(ModelAssembly assembly) {
    }

    default void onTextureRegistered(Identifier location, OuterFileTexture texture, boolean replaced) {
    }

    default void onTextureUploaded(Identifier location, OuterFileTexture texture) {
    }

    default void onTextureInactive(Identifier location) {
    }

    default void tick() {
    }

    default void flush() {
    }
}
