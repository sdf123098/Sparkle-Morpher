package com.micaftic.morpher.client.texture;

import com.micaftic.morpher.core.compat.oculus.ShadersTextureType;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.Map;

public interface ITextureMap {
    Map<ShadersTextureType, ? extends AbstractTexture> getSuffixTextures();
}