package com.micaftic.morpher.client.compat;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Thread-safe registry populated by optional renderer compatibility JARs. */
public final class ClientRenderCompatibilityRegistry {
    private static final List<ClientRenderCompatibility> MODULES = new CopyOnWriteArrayList<>();

    private ClientRenderCompatibilityRegistry() {
    }

    public static void register(ClientRenderCompatibility module) {
        if (module == null) return;
        try {
            if (module.isAvailable() && MODULES.add(module)) {
                module.initialize();
                YesSteveModel.LOGGER.info("[SM] Registered client render compatibility module: {}",
                        module.getClass().getName());
            }
        } catch (RuntimeException exception) {
            MODULES.remove(module);
            logFailure(module, "initialize", exception);
        }
    }

    public static Identifier resolveTextureLocation(OuterFileTexture texture) {
        for (ClientRenderCompatibility module : MODULES) {
            try {
                Identifier location = module.resolveTextureLocation(texture);
                if (location != null) return location;
            } catch (RuntimeException exception) {
                logFailure(module, "resolve texture location", exception);
            }
        }
        return null;
    }

    /** True when any registered module requests the emissive-bone split render pass. */
    public static boolean requiresEmissiveBoneSplit() {
        for (ClientRenderCompatibility module : MODULES) {
            try {
                if (module.requiresEmissiveBoneSplit()) return true;
            } catch (RuntimeException exception) {
                logFailure(module, "query emissive bone split", exception);
            }
        }
        return false;
    }

    public static void onModelAssemblyCreated(ModelAssembly assembly) {
        dispatch("publish model assembly", module -> module.onModelAssemblyCreated(assembly));
    }

    public static void onTextureRegistered(Identifier location, OuterFileTexture texture, boolean replaced) {
        dispatch("register texture", module -> module.onTextureRegistered(location, texture, replaced));
    }

    public static void onTextureUploaded(Identifier location, OuterFileTexture texture) {
        dispatch("upload texture", module -> module.onTextureUploaded(location, texture));
    }

    public static void onTextureInactive(Identifier location) {
        dispatch("release texture", module -> module.onTextureInactive(location));
    }

    public static void tick() {
        dispatch("tick", ClientRenderCompatibility::tick);
    }

    public static void flush() {
        dispatch("flush", ClientRenderCompatibility::flush);
    }

    private static void dispatch(String action, Consumer<ClientRenderCompatibility> call) {
        for (ClientRenderCompatibility module : MODULES) {
            try {
                call.accept(module);
            } catch (RuntimeException exception) {
                logFailure(module, action, exception);
            }
        }
    }

    private static void logFailure(ClientRenderCompatibility module, String action, RuntimeException exception) {
        YesSteveModel.LOGGER.error("[SM] Render compatibility module {} failed to {}",
                module.getClass().getName(), action, exception);
    }
}
