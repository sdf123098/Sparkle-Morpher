package com.micaftic.morpher.client.model;

import com.micaftic.morpher.audio.AudioStreamCache;
import com.micaftic.morpher.audio.AudioTrackData;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.resource.models.Metadata;
import com.micaftic.morpher.resource.gltf.GltfModel;
import com.micaftic.morpher.client.gui.metadata.ModelDisplayAssets;
import com.micaftic.morpher.model.format.ModelMetadata;
import com.micaftic.morpher.client.gui.ModelMetadataPresenter;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import it.unimi.dsi.fastutil.objects.Object2ReferenceOpenHashMap;

public class ModelAssembly {

    private volatile PlayerModelBundle animationBundle;

    private volatile Map<Identifier, ProjectileModelBundle> projectileModels;

    private volatile Map<Identifier, VehicleModelBundle> vehicleModels;

    private volatile ModelResourceBundle expressionCache;

    private final ModelMetadata modelData;

    private final ModelDisplayAssets textureRegistry;

    private volatile List<AbstractTexture> textures;
    private volatile List<String> textureNames;

    private volatile GltfModel gltfModel;
    private volatile boolean gltfBackend;
    private volatile ModelPresentationCapabilities presentationCapabilities;

    public ModelAssembly(PlayerModelBundle animationBundle, Map<Identifier, ProjectileModelBundle> projectileModels, Map<Identifier, VehicleModelBundle> vehicleModels, ModelResourceBundle expressionCache, ModelMetadata modelData, ModelDisplayAssets textureRegistry, List<AbstractTexture> list) {
        this.animationBundle = animationBundle;
        this.projectileModels = projectileModels;
        this.vehicleModels = vehicleModels;
        this.expressionCache = expressionCache;
        this.modelData = modelData;
        this.textureRegistry = textureRegistry;
        this.textures = list;
        this.gltfModel = null;
        this.textureNames = legacyTextureNames(animationBundle);
        refreshPresentationCapabilities();
    }

    /** Creates a runtime assembly for the independent glTF path. */
    public static ModelAssembly forGltf(GltfModel model, List<AbstractTexture> imageTextures) {
        if (model == null) throw new IllegalArgumentException("glTF model must not be null");
        ModelResourceBundle resources = new ModelResourceBundle(
                Map.of(), new Object2ReferenceOpenHashMap<>(), new Object2ReferenceOpenHashMap<>(), Map.of());
        ModelAssembly assembly = new ModelAssembly(null, Map.of(), Map.of(), resources, null,
                new ModelDisplayAssets(null, false, Map.of(), Map.of()), imageTextures);
        assembly.gltfBackend = true;
        assembly.gltfModel = model;
        assembly.textureNames = gltfTextureNames(model);
        assembly.refreshPresentationCapabilities();
        return assembly;
    }

    private static List<String> legacyTextureNames(PlayerModelBundle bundle) {
        return bundle == null || bundle.getTextures() == null
                ? List.of()
                : List.copyOf(bundle.getTextures().keySet());
    }

    private static List<String> gltfTextureNames(GltfModel model) {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < model.images().size(); i++) {
            String name = model.images().get(i).name();
            names.add(name == null || name.isBlank() ? "image" + i : name);
        }
        return List.copyOf(names);
    }

    public PlayerModelBundle getAnimationBundle() {
        return this.animationBundle;
    }

    public List<AbstractTexture> getTextures() {
        return this.textures;
    }

    /** Returns stable texture labels for both legacy YSM and glTF assemblies. */
    public List<String> getTextureNames() {
        return textureNames;
    }

    public ModelResourceBundle getExpressionCache() {
        return this.expressionCache;
    }

    public Map<Identifier, ProjectileModelBundle> getProjectileModels() {
        return this.projectileModels;
    }

    public Map<Identifier, VehicleModelBundle> getVehicleModels() {
        return this.vehicleModels;
    }

    public GltfModel getGltfModel() {
        return this.gltfModel;
    }

    public boolean isGltf() {
        return this.gltfModel != null;
    }

    /** Resolves a glTF material texture index to the corresponding image texture. */
    public AbstractTexture getGltfTexture(int textureIndex) {
        if (gltfModel == null || textureIndex < 0 || textureIndex >= gltfModel.textures().size()) return null;
        int imageIndex = gltfModel.textures().get(textureIndex).imageIndex();
        return imageIndex >= 0 && imageIndex < textures.size() ? textures.get(imageIndex) : null;
    }

    public ModelMetadata getModelData() {
        return this.modelData;
    }

    public ModelDisplayAssets getTextureRegistry() {
        return this.textureRegistry;
    }

    public boolean isRuntimeResident() {
        return (animationBundle != null || gltfModel != null) && expressionCache != null;
    }

    /** Returns stable presentation capabilities, including after runtime payload release. */
    public ModelPresentationCapabilities getPresentationCapabilities() {
        return presentationCapabilities;
    }

    private void refreshPresentationCapabilities() {
        boolean resident = isRuntimeResident();
        boolean hasLegacyRuntime = resident && !gltfBackend && animationBundle != null;
        boolean hasTextureSelection = hasLegacyRuntime && !textureNames.isEmpty();
        presentationCapabilities = new ModelPresentationCapabilities(
                resident,
                modelData != null,
                !textureNames.isEmpty(),
                hasTextureSelection,
                hasLegacyRuntime,
                hasLegacyRuntime,
                hasLegacyRuntime,
                hasLegacyRuntime,
                hasLegacyRuntime);
    }

    public synchronized void unloadRuntime() {
        animationBundle = null;
        projectileModels = null;
        vehicleModels = null;
        expressionCache = null;
        textures = List.of();
        gltfModel = null;
        textureRegistry.clearTextureReferences();
        refreshPresentationCapabilities();
    }

    // ==================== R10.4 资源统一 ownership ====================
    // 释放逻辑从 ClientModelManager 收拢到装配自身：装配 owns 纹理 / audio provider /
    // GPU+原生 mesh / expression runtime，销毁走确定性 close()，GC Cleaner 仅是兜底。

    /** 释放本装配持有的全部纹理（UploadManager 注销 + close）。需渲染线程。 */
    public void releaseTextures() {
        for (AbstractTexture tex : textures) {
            if (tex == null) {
                continue;
            }
            UploadManager.removeTexture(tex);
            if (tex instanceof OuterFileTexture outerFileTexture) {
                outerFileTexture.closeAndReleaseSource();
            } else {
                tex.close();
            }
        }
    }

    /** 释放本装配的 audio provider（AudioStreamCache 立即清空，随装配消亡）。 */
    public void releaseAudioProvider() {
        AudioStreamCache.clearForModel(this);
    }

    /** 释放本装配的 GPU mesh（保留 native 缓存，供 {@link #releaseGpuOnly} 之外的重渲染）。需渲染线程。 */
    public void releaseGpuMeshes() {
        if (projectileModels != null) {
            for (ProjectileModelBundle bundle : projectileModels.values()) {
                if (bundle.getModel() != null) {
                    bundle.getModel().freeGpuCache();
                }
            }
        }
        if (vehicleModels != null) {
            for (VehicleModelBundle bundle : vehicleModels.values()) {
                if (bundle.getModel() != null) {
                    bundle.getModel().freeGpuCache();
                }
            }
        }
        if (animationBundle != null) {
            if (animationBundle.getMainModel() != null) {
                animationBundle.getMainModel().freeGpuCache();
            }
            if (animationBundle.getArmModel() != null) {
                animationBundle.getArmModel().freeGpuCache();
            }
        }
    }

    /**
     * R10.4：释放本装配的 GPU + native mesh（main/arm/projectile/vehicle）。需渲染线程。
     * 完整释放（close）使用本方法；仅 GPU 修剪用 {@link #releaseGpuMeshes()}。
     */
    public void releaseGpuOnly() {
        if (projectileModels != null) {
            for (ProjectileModelBundle bundle : projectileModels.values()) {
                if (bundle.getModel() != null) {
                    bundle.getModel().freeNativeCache();
                }
            }
        }
        if (vehicleModels != null) {
            for (VehicleModelBundle bundle : vehicleModels.values()) {
                if (bundle.getModel() != null) {
                    bundle.getModel().freeNativeCache();
                }
            }
        }
        if (animationBundle != null) {
            if (animationBundle.getMainModel() != null) {
                animationBundle.getMainModel().freeNativeCache();
            }
            if (animationBundle.getArmModel() != null) {
                animationBundle.getArmModel().freeNativeCache();
            }
        }
    }

    /** 释放 CPU runtime（表达式缓存 + 声音数据 + bundle 引用清空）。不碰 GL。 */
    public void releaseRuntime() {
        if (expressionCache != null) {
            for (AudioTrackData trackData : expressionCache.getSoundEffects().values()) {
                if (trackData != null) {
                    trackData.close();
                }
            }
        }
        unloadRuntime();
    }

    /**
     * R10.4：完整确定性释放（纹理 + audio + GPU/native + runtime）。
     * 需渲染线程（GPU/native 释放需要 GL 上下文）；调用后装配不可再渲染。
     */
    public void close() {
        releaseTextures();
        releaseAudioProvider();
        releaseGpuOnly();
        releaseRuntime();
    }

    public String getDisplayName(String str) {
        if (getModelData() == null) {
            return str;
        }
        Metadata name = getModelData().getExtraInfo();
        if (name != null) {
            return ModelMetadataPresenter.getLocalizedModelString(this, "metadata.name", name.getName());
        }
        return str;
    }
}
