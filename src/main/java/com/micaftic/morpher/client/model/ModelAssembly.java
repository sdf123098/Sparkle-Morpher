package com.micaftic.morpher.client.model;

import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public class ModelAssembly {

    private volatile PlayerModelBundle animationBundle;

    private volatile Map<ResourceLocation, ProjectileModelBundle> projectileModels;

    private volatile Map<ResourceLocation, VehicleModelBundle> vehicleModels;

    private volatile ModelResourceBundle expressionCache;

    private final ServerModelInfo modelData;

    private final ModelDisplayAssets textureRegistry;

    private volatile List<AbstractTexture> textures;

    public ModelAssembly(PlayerModelBundle animationBundle, Map<ResourceLocation, ProjectileModelBundle> projectileModels, Map<ResourceLocation, VehicleModelBundle> vehicleModels, ModelResourceBundle expressionCache, ServerModelInfo modelData, ModelDisplayAssets textureRegistry, List<AbstractTexture> list) {
        this.animationBundle = animationBundle;
        this.projectileModels = projectileModels;
        this.vehicleModels = vehicleModels;
        this.expressionCache = expressionCache;
        this.modelData = modelData;
        this.textureRegistry = textureRegistry;
        this.textures = list;
    }

    public PlayerModelBundle getAnimationBundle() {
        return this.animationBundle;
    }

    public List<AbstractTexture> getTextures() {
        return this.textures;
    }

    public ModelResourceBundle getExpressionCache() {
        return this.expressionCache;
    }

    public Map<ResourceLocation, ProjectileModelBundle> getProjectileModels() {
        return this.projectileModels;
    }

    public Map<ResourceLocation, VehicleModelBundle> getVehicleModels() {
        return this.vehicleModels;
    }

    public ServerModelInfo getModelData() {
        return this.modelData;
    }

    public ModelDisplayAssets getTextureRegistry() {
        return this.textureRegistry;
    }

    public boolean isRuntimeResident() {
        return animationBundle != null && expressionCache != null;
    }

    public synchronized void unloadRuntime() {
        animationBundle = null;
        projectileModels = null;
        vehicleModels = null;
        expressionCache = null;
        textures = List.of();
        textureRegistry.clearTextureReferences();
    }

    public String getDisplayName(String str) {
        Metadata name = getModelData().getExtraInfo();
        if (name != null) {
            return ModelMetadataPresenter.getLocalizedModelString(this, "metadata.name", name.getName());
        }
        return str;
    }
}
