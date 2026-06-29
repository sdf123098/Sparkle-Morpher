package com.micaftic.morpher.client.texture;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public class OuterFileTexture extends AbstractTexture implements ITextureMap {
    private byte[] data;
    private final String modelId;

    private Map<ShadersTextureType, OuterFileTexture> suffixTextures = Reference2ReferenceMaps.emptyMap();

    private boolean uploaded;
    private boolean closed;

    public OuterFileTexture(byte[] data) {
        this(data, null);
    }

    public OuterFileTexture(byte[] data, String modelId) {
        this.data = data;
        this.modelId = modelId;
    }

    public void load(@NotNull ResourceManager resourceManager) {
        doLoad();
    }

    public void doLoad() {
        RenderSystem.assertOnRenderThread();
        if (this.uploaded && this.textureView != null) {
            return;
        }
        NativeImage image = null;
        byte[] textureData = this.data;
        try {
            if (textureData == null) {
                throw new IOException("Texture source bytes were released");
            }
            ModelMemoryProfiler.logBytes("texture-decode-start", null, textureData);
            image = NativeImage.read(new ByteArrayInputStream(textureData));
        } catch (IOException e) {
            YesSteveModel.LOGGER.warn("Failed to decode YSM texture, using fallback texture", e);
            image = createFallbackImage();
        }
        uploadImage(image);
    }

    public boolean isLoaded() {
        return this.uploaded && this.textureView != null;
    }

    @Override
    public GpuTextureView getTextureView() {
        if (!isLoaded() && RenderSystem.isOnRenderThread()) {
            doLoad();
        }
        return super.getTextureView();
    }

    private void uploadImage(NativeImage image) {
        try (image) {
            int width = Math.max(1, image.getWidth());
            int height = Math.max(1, image.getHeight());
            long sourceBytes = this.data == null ? 0L : this.data.length;
            if (this.texture != null || this.textureView != null || this.sampler != null) {
                super.close();
            }
            var device = RenderSystem.getDevice();
            this.texture = device.createTexture(
                    () -> "YSM outer texture",
                    GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST,
                    TextureFormat.RGBA8,
                    width,
                    height,
                    1,
                    1);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            this.textureView = device.createTextureView(this.texture);
            device.createCommandEncoder().writeToTexture(this.texture, image);
            this.uploaded = true;
            ResourceLifecycleStats.onTextureUploaded(modelId, width, height, sourceBytes);
            ModelMemoryProfiler.log("texture-uploaded", null);
        }
    }

    private static NativeImage createFallbackImage() {
        NativeImage image = new NativeImage(1, 1, false);
        image.setPixel(0, 0, 0xFFFF00FF);
        return image;
    }

    public void setSuffixTextures(Map<ShadersTextureType, OuterFileTexture> map) {
        this.suffixTextures = Reference2ReferenceMaps.unmodifiable(new Reference2ReferenceOpenHashMap<>(map));
    }

    public Map<ShadersTextureType, ? extends AbstractTexture> getSuffixTextures() {
        return this.suffixTextures;
    }

    @Override
    public void close() {
        releaseGpuBinding();
    }

    public void closeAndReleaseSource() {
        if (closed) {
            return;
        }
        closed = true;
        releaseGpuBinding();
        for (OuterFileTexture texture : this.suffixTextures.values()) {
            if (texture != null) {
                texture.closeAndReleaseSource();
            }
        }
        this.suffixTextures = Reference2ReferenceMaps.emptyMap();
        long retainedBytes = this.data == null ? 0L : this.data.length;
        if (retainedBytes > 0L) {
            ResourceLifecycleStats.onTextureSourceBytesReleased(modelId, retainedBytes);
            this.data = null;
        }
    }

    private void releaseGpuBinding() {
        for (OuterFileTexture texture : this.suffixTextures.values()) {
            if (texture != null) {
                texture.releaseGpuBinding();
            }
        }
        boolean hadGpuBinding = this.uploaded || this.texture != null || this.textureView != null || this.sampler != null;
        super.close();
        this.uploaded = false;
        if (hadGpuBinding) {
            ResourceLifecycleStats.onTextureClosed(modelId);
        }
    }
}
