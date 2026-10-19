package com.micaftic.morpher.client.renderer.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;

/** Keeps one vanilla PIP texture per card, rather than sharing the single vanilla entity texture. */
public final class NativeCardPreviewCache {
    // PIP owns an RGBA8 and a depth32 texture: 8M pixels bounds their storage to 64 MiB.
    private static final CardPreviewTexturePool<CachedRenderer> CACHE = new CardPreviewTexturePool<>(96, 8_388_608L);

    private NativeCardPreviewCache() { }

    public static void beginFrame() { CACHE.beginFrame(); }
    public static void clear() { CACHE.clear(); }

    public static Object reserve(Object stableKey, int width, int height, boolean refreshAllowed) {
        int guiScale = Math.max(1,Minecraft.getInstance().getWindow().getGuiScale());
        var key = new TextureKey(stableKey, width, height, guiScale);
        CachedRenderer renderer = CACHE.get(key);
        if (renderer == null && refreshAllowed) {
            renderer = CACHE.acquire(key, (long) width * height * guiScale * guiScale,
                    () -> new CachedRenderer());
        }
        return renderer != null && (renderer.ready || refreshAllowed) ? key : null;
    }

    /** Called only for the ordinary shared renderer; dedicated instances must continue into vanilla prepare. */
    public static boolean isDedicatedRenderer(Object renderer) { return renderer instanceof CachedRenderer; }

    public static boolean prepare(GuiEntityRenderState state, GuiRenderState gui,
                                  FeatureRenderDispatcher dispatcher, int scale) {
        PreviewRenderBridge.CardPreviewRequest request = PreviewRenderBridge.cardPreview(state.renderState());
        if (request == null) return false;
        try {
            CachedRenderer renderer = CACHE.get(request.key());
            if (renderer != null) renderer.prepareCard(state, gui, dispatcher, scale, request);
        } finally {
            // Cached-only states and failed refreshes must not retain model/animation closures in the bridge.
            PreviewRenderBridge.finishGuiState(state.renderState());
        }
        return true;
    }

    private record TextureKey(Object stableKey, int width, int height, int guiScale) { }

    private static final class CachedRenderer extends GuiEntityRenderer {
        private boolean ready;
        private long revision;

        CachedRenderer() { super(Minecraft.getInstance().getEntityRenderDispatcher()); }

        void prepareCard(GuiEntityRenderState state, GuiRenderState gui, FeatureRenderDispatcher dispatcher,
                         int scale, PreviewRenderBridge.CardPreviewRequest request) {
            if (ready && (!request.refreshAllowed() || revision == request.revision())) {
                // Blitting retains the current bounds, layer and scissor without touching the entity/model.
                blitTexture(state, gui);
                return;
            }
            if (!request.refreshAllowed()) return;
            super.prepare(state, gui, dispatcher, scale);
            revision = request.revision();
            ready = true;
        }
    }
}
