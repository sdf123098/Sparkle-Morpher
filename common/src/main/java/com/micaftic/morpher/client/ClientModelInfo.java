package com.micaftic.morpher.client;

import com.micaftic.morpher.client.core.HostAnimationAssets.ExtraResourceBundleSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ProjectileBundleSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.VehicleBundleSpec;
import com.micaftic.morpher.client.model.MainModelData;
import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.model.format.ModelMetadata;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class ClientModelInfo {

    private final MainModelData mainModelData;

    private final ProjectileBundleSpec[] projectileModelFiles;

    private final VehicleBundleSpec[] vehicleModelFiles;

    private final ExtraResourceBundleSpec extraResources;

    @NotNull
    private final ModelMetadata info;

    private final Map<String, OuterFileTexture> avatarTextures;

    private final Map<String, OuterFileTexture> guiTextures;

    public ClientModelInfo(
            MainModelData mainModelData,
            ProjectileBundleSpec[] projectileModelFiles,
            VehicleBundleSpec[] vehicleModelFiles,
            ExtraResourceBundleSpec extraResources,
            @NotNull ModelMetadata info,
            Map<String, OuterFileTexture> avatarTextures,
            Map<String, OuterFileTexture> guiTextures) {
        this.mainModelData = mainModelData;
        this.projectileModelFiles = projectileModelFiles;
        this.vehicleModelFiles = vehicleModelFiles;
        this.extraResources = extraResources;
        this.info = info;
        this.avatarTextures = avatarTextures;
        this.guiTextures = guiTextures;
    }

    public MainModelData getMainModelData() {
        return this.mainModelData;
    }

    public ProjectileBundleSpec[] getExtraItemModels() {
        return this.projectileModelFiles;
    }

    public VehicleBundleSpec[] getVehicleModelFiles() {
        return this.vehicleModelFiles;
    }

    public ExtraResourceBundleSpec getExtraResources() {
        return this.extraResources;
    }

    public Map<String, OuterFileTexture> getAvatarTextures() {
        return this.avatarTextures;
    }

    public Map<String, OuterFileTexture> getGuiTextures() {
        return this.guiTextures;
    }

    @NotNull
    public ModelMetadata getInfo() {
        return this.info;
    }
}
