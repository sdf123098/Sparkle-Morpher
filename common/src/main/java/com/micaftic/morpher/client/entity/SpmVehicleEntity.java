package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.animation.controller.VehicleAnimationController;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.VehicleModelBundle;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import dev.spm.core.scene.PoseAnimationLibrary;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class SpmVehicleEntity extends SpmEntity<Entity> {

    private VehicleModelBundle vehicleModel;

    private VehicleRotationController expressionBuilder;

    public SpmVehicleEntity(Entity entity) {
        super(entity, true);
    }

    public static boolean usesVanillaRenderer(Entity entity) {
        return entity instanceof Boat || entity instanceof AbstractMinecart;
    }

    @Override
    public void registerAnimationControllers() {
        if (this.vehicleModel != null) {
            this.vehicleModel.getAnimatableConsumer().accept(this);
            var slot = getControllerSlot(VehicleAnimationController.ORIGIN_CONTROLLER_KEY);
            this.expressionBuilder = slot instanceof VehicleRotationController vrc ? vrc : null;
        }
    }

    @Nullable
    public Vector3f getExpressionOffset() {
        if (this.expressionBuilder != null) {
            return this.expressionBuilder.getVehicleRotation();
        }
        return null;
    }

    @Override
    @Nullable
    public SpmEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        VehicleModelBundle modelBundle = getVehicleModel(modelAssembly);
        if (modelBundle != null) {
            return new EntityModelWrapper(modelAssembly, isDefault, modelBundle);
        }
        return null;
    }

    @Override
    public void onModelLoaded(ModelAssembly modelAssembly) {
        super.onModelLoaded(modelAssembly);
        this.vehicleModel = getVehicleModel(modelAssembly);
    }

    @Nullable
    private ResourceLocation getEntityTypeId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(this.entity.getType());
    }

    @Nullable
    private VehicleModelBundle getVehicleModel(ModelAssembly modelAssembly) {
        if (usesVanillaRenderer(this.entity)) {
            return null;
        }
        return modelAssembly.getVehicleModels().get(getEntityTypeId());
    }

    @Override
    public void clearModel() {
        super.clearModel();
        this.vehicleModel = null;
        this.expressionBuilder = null;
    }

    @Override
    public BuiltModel getAnimationProcessor() {
        return this.vehicleModel.getModel();
    }

    @Override
    @NotNull
    public ResourceLocation getTextureLocation() {
        return ((EntityModelWrapper) getRenderShape()).textureLocatable.getResourceLocation().orElseGet(MissingTextureAtlasSprite::getLocation);
    }

    @Override
    public ClipSpec getAnimation(String str) {
        return this.vehicleModel.getAnimations().get(str);
    }

    @Nullable
    public ControllerSpec getAnimationEntries(String str) {
        return this.vehicleModel.getAnimationControllers().get(str);
    }

    @Override
    public @Nullable ControllerSpec getControllerSpec(String name) {
        return this.vehicleModel != null ? getAnimationEntries(name) : null;
    }

    @Override
    protected @Nullable PoseAnimationLibrary resolveAnimationLibrary() {
        return this.vehicleModel != null ? this.vehicleModel.getAnimationLibrary() : null;
    }

    @Override
    public boolean isModelReady() {
        return super.isModelReady() && this.vehicleModel != null && getRenderShape().isValid();
    }

    @Override
    public float getHeightScale() {
        return 0.7f;
    }

    @Override
    public float getWidthScale() {
        return 0.7f;
    }

    private static class EntityModelWrapper extends ModelWrapper {

        private final IResourceLocatable textureLocatable;

        public EntityModelWrapper(ModelAssembly modelAssembly, boolean isDefault, VehicleModelBundle modelBundle) {
            super(modelAssembly, isDefault);
            this.textureLocatable = UploadManager.getOrCreateLocatable(modelBundle.getTexture(), true);
        }

        @Override
        public boolean isValid() {
            return this.textureLocatable.getResourceLocation().isPresent();
        }
    }
}
