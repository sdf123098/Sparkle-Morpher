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
