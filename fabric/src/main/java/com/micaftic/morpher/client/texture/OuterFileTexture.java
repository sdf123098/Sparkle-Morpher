package com.micaftic.morpher.client.texture;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.util.ResourceLifecycleStats;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import com.micaftic.morpher.client.upload.UploadManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMaps;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

public class OuterFileTexture extends AbstractTexture implements ITextureMap {
    private final byte[] data;
    private final boolean peerTexture;
    private boolean uploaded;
    private boolean closed;
    private final String modelId;

    private Map<ShadersTextureType, OuterFileTexture> suffixTextures = Reference2ReferenceMaps.emptyMap();

    public OuterFileTexture(byte[] data) {
        this(data, null);
    }

    public OuterFileTexture(byte[] data, String modelId) {
        this.data = Objects.requireNonNull(data, "Texture source data must not be null");
        if (data.length == 0) throw new IllegalArgumentException("Texture source data must not be empty");
        this.modelId = modelId;
        this.peerTexture = com.micaftic.morpher.peer.PeerDecodeScope.active() || modelId != null && modelId.startsWith("peer:");
    }

    @Override
    public void load(@NotNull ResourceManager resourceManager) {
        if (!RenderSystem.isOnRenderThreadOrInit()) {
            RenderSystem.recordRenderCall(this::doLoad);
        } else {
            doLoad();
        }
    }

    public void doLoad() {
        if (peerTexture && closed) return;
        if (peerTexture && !com.micaftic.morpher.client.peer.PeerRenderFrames.executing(this)) {
            com.micaftic.morpher.client.peer.PeerRenderFrames.schedule(this, this::doLoad);
            return;
        }
        if (uploaded) {
            return;
        }
        byte[] textureBytes = data;
        ModelMemoryProfiler.logBytes("texture-decode-start", null, textureBytes);
        try (NativeImage imageIn = NativeImage.read(new ByteArrayInputStream(textureBytes))) {
            int width = imageIn.getWidth();
            int height = imageIn.getHeight();
            TextureUtil.prepareImage(this.getId(), 0, width, height);
            imageIn.upload(0, 0, 0, 0, 0, width, height, false, false, false, true);
            uploaded = true;
            closed = false;
            ResourceLifecycleStats.onTextureUploaded(modelId, width, height, textureBytes.length);
            ModelMemoryProfiler.log("texture-uploaded", null);
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("[SM] Failed to upload outer file texture", e);
        }
        UploadManager.onTextureUploaded(this);
    }

    public boolean isPeerTexture() { return peerTexture; }
    public boolean isPeerUploaded() { return uploaded; }
    public String peerModelId() { return modelId; }
    public boolean isSourceReleased() { return closed; }
    public long peerUploadBytes() {
        if (data.length >= 24 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            int w = java.nio.ByteBuffer.wrap(data, 16, 8).getInt();
            int h = java.nio.ByteBuffer.wrap(data, 20, 4).getInt();
            if (w > 0 && w <= 4096 && h > 0 && h <= 4096) return Math.max(data.length, (long) w * h * 4);
        }
        return Math.max(data.length, 64L * 1024 * 1024);
    }

    /** Original PNG data exposed to optional runtime material integrations. */
    public byte[] getResourceData() {
        return this.data;
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
        com.micaftic.morpher.peer.PeerFrameWork.PEER.cancel(this);
        releaseGpuBinding();
        for (OuterFileTexture texture : suffixTextures.values()) {
            texture.closeAndReleaseSource();
        }
        suffixTextures = Reference2ReferenceMaps.emptyMap();
        ResourceLifecycleStats.onTextureSourceBytesReleased(modelId, data.length);
    }

    private void releaseGpuBinding() {
        for (OuterFileTexture texture : suffixTextures.values()) {
            texture.releaseGpuBinding();
        }
        boolean wasUploaded = uploaded;
        super.close();
        uploaded = false;
        if (wasUploaded) {
            ResourceLifecycleStats.onTextureClosed(modelId);
        }
    }
}
