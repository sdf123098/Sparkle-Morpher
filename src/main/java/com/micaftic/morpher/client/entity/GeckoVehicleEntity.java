package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.geckolib3.core.controller.controllers.VehicleAnimationController;
import com.micaftic.morpher.client.model.VehicleModelBundle;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.client.entity.VehicleRotationController;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.upload.UploadManager;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public class GeckoVehicleEntity extends GeoEntity<Entity> {

    private VehicleModelBundle vehicleModel;

    private VehicleRotationController expressionBuilder;

    public GeckoVehicleEntity(Entity entity) {
        super(entity, true);
    }

    public static boolean usesVanillaRenderer(Entity entity) {
        return entity instanceof AbstractBoat || entity instanceof AbstractMinecart;
    }

    @Override
    public void registerAnimationControllers() {
        if (this.vehicleModel != null) {
            this.vehicleModel.getAnimatableConsumer().accept(this);
            this.expressionBuilder = (VehicleRotationController) getAnimationData().getAnimationControllerByName(VehicleAnimationController.ORIGIN_CONTROLLER_KEY);
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
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
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
    private Identifier getEntityTypeId() {
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
    public GeoModel getAnimationProcessor() {
        return this.vehicleModel.getModel();
    }

    @Override
    @NotNull
    public Identifier getTextureLocation() {
        return ((EntityModelWrapper) getRenderShape()).textureLocatable.getResourceLocation().orElseGet(MissingTextureAtlasSprite::getLocation);
    }

    @Override
    public Animation getAnimation(String str) {
        return this.vehicleModel.getAnimations().get(str);
    }

    @Override
    @Nullable
    public AnimationController getAnimationEntries(String str) {
        return this.vehicleModel.getAnimationControllers().get(str);
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
