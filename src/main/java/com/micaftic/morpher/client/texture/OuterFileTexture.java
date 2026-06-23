package com.micaftic.morpher.client.texture;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.config.GeneralConfig;
import com.micaftic.morpher.util.ModelMemoryProfiler;
import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
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

public class OuterFileTexture extends AbstractTexture implements ITextureMap {
    private byte[] data;
    private boolean uploaded;

    private Map<ShadersTextureType, OuterFileTexture> suffixTextures = Reference2ReferenceMaps.emptyMap();

    public OuterFileTexture(byte[] data) {
        this.data = data;
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
        byte[] textureBytes = data;
        if (textureBytes == null) {
            if (!uploaded) {
                YesSteveModel.LOGGER.warn("[SM] Texture bytes are unavailable before upload.");
            }
            return;
        }
        ModelMemoryProfiler.logBytes("texture-decode-start", null, textureBytes);
        try (NativeImage imageIn = NativeImage.read(new ByteArrayInputStream(textureBytes))) {
            int width = imageIn.getWidth();
            int height = imageIn.getHeight();
            TextureUtil.prepareImage(this.getId(), 0, width, height);
            imageIn.upload(0, 0, 0, 0, 0, width, height, false, false, false, true);
            uploaded = true;
            ModelMemoryProfiler.log("texture-uploaded", null);
            if (GeneralConfig.safeGet(GeneralConfig.RELEASE_TEXTURE_BYTES_AFTER_UPLOAD, false)) {
                data = null;
                ModelMemoryProfiler.log("texture-bytes-released", null);
            }
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("[SM] Failed to upload outer file texture", e);
        }
    }

    public void setSuffixTextures(Map<ShadersTextureType, OuterFileTexture> map) {
        this.suffixTextures = Reference2ReferenceMaps.unmodifiable(new Reference2ReferenceOpenHashMap<>(map));
    }

    public Map<ShadersTextureType, ? extends AbstractTexture> getSuffixTextures() {
        return this.suffixTextures;
    }
}
