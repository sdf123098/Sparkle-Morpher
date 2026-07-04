package com.micaftic.morpher.client.upload;

import com.micaftic.morpher.ResourceCleanupHelper;
import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.texture.ITextureMap;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.google.common.collect.Queues;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ReferenceIntMutablePair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.time.StopWatch;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UploadManager {

    private static final int UPLOAD_TIME_LIMIT_MS = 20;

    private static long textureCounter = 0;

    private static final IdentityHashMap<AbstractTexture, WeakReference<TextureLocatable>> textureCache = new IdentityHashMap<>();

    private static final Queue<Pair<TextureLocatable, AbstractTexture>> pendingUploads = Queues.newArrayDeque();

    private static final ConcurrentHashMap<AbstractTexture, ReferenceIntMutablePair<Identifier>> expiredTextures = new ConcurrentHashMap<>();

    private static final Queue<Identifier> pendingReleases = Queues.newArrayDeque();

    public static IResourceLocatable getOrCreateLocatable(AbstractTexture texture, boolean register) {
        return getOrCreateLocatableWithSize(texture, register, 200);
    }

    public static IResourceLocatable getOrCreateLocatableWithSize(AbstractTexture texture, boolean register, int sizeHint) {
        RenderSystem.assertOnRenderThread();
        WeakReference<TextureLocatable> weakReference = textureCache.get(texture);
        if (weakReference != null) {
            TextureLocatable locatable = weakReference.get();
            if (locatable != null) {
                if (register && !locatable.registered) {
                    registerTexture(texture, locatable);
                }
                return locatable;
            }
            textureCache.remove(texture);
        }
        ReferenceIntMutablePair<Identifier> removed = expiredTextures.remove(texture);
        TextureLocatable locatable;
        if (removed != null) {
            locatable = new TextureLocatable(removed.first(), sizeHint);
        } else {
            locatable = new TextureLocatable(sizeHint);
        }
        if (texture instanceof ITextureMap) {
            for (AbstractTexture suffixTexture : ((ITextureMap) texture).getSuffixTextures().values()) {
                if (locatable.suffixTextures == null)
                    locatable.suffixTextures = new ArrayList<>(2);

                locatable.suffixTextures.add(getOrCreateLocatableWithSize(suffixTexture, register, sizeHint));
            }
        }
        textureCache.put(texture, new WeakReference<>(locatable));
        if (register) {
            registerTexture(texture, locatable);
        } else {
            pendingUploads.add(Pair.of(locatable, texture));
        }
        return locatable;
    }

    public static void removeTexture(AbstractTexture abstractTexture) {
        RenderSystem.assertOnRenderThread();
        textureCache.remove(abstractTexture);
    }

    public static void processPendingUploads() {
        RenderSystem.assertOnRenderThread();
        // Fast idle path: most ticks have no queued GPU texture work.
        if (pendingUploads.isEmpty() && pendingReleases.isEmpty() && expiredTextures.isEmpty()) {
            return;
        }
        if (!expiredTextures.isEmpty()) {
            Iterator<Map.Entry<AbstractTexture, ReferenceIntMutablePair<Identifier>>> it = expiredTextures.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<AbstractTexture, ReferenceIntMutablePair<Identifier>> next = it.next();
                int iSecondInt = next.getValue().secondInt();
                if (iSecondInt <= 0) {
                    pendingReleases.add(next.getValue().first());
                    it.remove();
                } else {
                    next.getValue().second(iSecondInt - 1);
                }
            }
        }
        StopWatch stopWatchCreateStarted = StopWatch.createStarted();
        do {
            Pair<TextureLocatable, AbstractTexture> pairPoll = pendingUploads.poll();
            if (pairPoll != null) {
                registerTexture(pairPoll.right(), pairPoll.left());
            } else {
                TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                do {
                    Identifier resourceLocationPoll = pendingReleases.poll();
                    if (resourceLocationPoll != null) {
                        textureManager.release(resourceLocationPoll);
                    } else {
                        return;
                    }
                } while (stopWatchCreateStarted.getTime() < UPLOAD_TIME_LIMIT_MS);
                return;
            }
        } while (stopWatchCreateStarted.getTime() < UPLOAD_TIME_LIMIT_MS);
    }

    private static void registerTexture(AbstractTexture texture, TextureLocatable locatable) {
        if (!locatable.registered) {
            if (texture instanceof OuterFileTexture outerFileTexture) {
                outerFileTexture.doLoad();
            }
            Minecraft.getInstance().getTextureManager().register(locatable.textureIdentifier, texture);
            ResourceCleanupHelper.registerBiCleanup(locatable, locatable.textureIdentifier, locatable.resolution, (textureId, num) -> {
                expiredTextures.put(texture, ReferenceIntMutablePair.of(textureId, num));
            });
            locatable.markRegistered();
        }
    }

    private static class TextureLocatable implements IResourceLocatable {

        private final Identifier textureIdentifier;

        private final int resolution;

        private List<IResourceLocatable> suffixTextures;

        private volatile boolean registered;

        public TextureLocatable(Identifier textureIdentifier, int resolution) {
            this.textureIdentifier = textureIdentifier;
            this.resolution = resolution;
        }

        TextureLocatable(int resolution) {
            this.textureIdentifier = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "textures/" + ++textureCounter);
            this.resolution = resolution;
            this.registered = false;
        }

        @Override
        public Optional<Identifier> getResourceLocation() {
            return this.registered ? Optional.of(this.textureIdentifier) : Optional.empty();
        }

        @Override
        public Identifier getResourceLocationOrNull() {
            return this.registered ? this.textureIdentifier : null;
        }

        public void markRegistered() {
            this.registered = true;
        }
    }
}
