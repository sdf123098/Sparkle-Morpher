package com.micaftic.morpher.client.gui.metadata;

import com.micaftic.morpher.client.texture.OuterFileTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class ModelDisplayAssets {
    private final String selectedTexture;

    private boolean isAuthModel;

    private volatile Map<String, OuterFileTexture> authorAvatars;

    private volatile Map<String, AbstractTexture> guiTextures;

    public ModelDisplayAssets(String selectedTexture, boolean isAuth, Map<String, OuterFileTexture> authorAvatars, Map<String, AbstractTexture> guiTextures) {
        this.selectedTexture = selectedTexture;
        this.isAuthModel = isAuth;
        this.authorAvatars = authorAvatars;
        this.guiTextures = guiTextures;
    }

    public String getSelectedTexture() {
        return this.selectedTexture;
    }

    public boolean isAuthModel() {
        return this.isAuthModel;
    }

    public void setAuthModel(boolean isModelReady) {
        this.isAuthModel = isModelReady;
    }

    public Map<String, OuterFileTexture> getAuthorAvatars() {
        return this.authorAvatars;
    }

    @Nullable
    public AbstractTexture getGuiForeground() {
        return this.guiTextures.get("gui_foreground");
    }

    @Nullable
    public AbstractTexture getGuiBackground() {
        return this.guiTextures.get("gui_background");
    }

    public synchronized void clearTextureReferences() {
        this.authorAvatars = Map.of();
        this.guiTextures = Map.of();
    }
}
