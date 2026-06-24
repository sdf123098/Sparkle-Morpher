package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.upload.UploadManager;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.geckolib3.core.builder.AnimationController;
import com.micaftic.morpher.geckolib3.core.builder.Animation;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.ProjectileModelBundle;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.Projectile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GeckoProjectileEntity extends GeoEntity<Projectile> {

    private ProjectileModelBundle projectileModelContext;

    public GeckoProjectileEntity(Projectile projectile) {
        super(projectile, true);
    }

    @Override
    public void registerAnimationControllers() {
        if (this.projectileModelContext != null) {
            this.projectileModelContext.getControllerInitializer().accept(this);
        }
    }

    @Override
    @Nullable
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        ProjectileModelBundle modelBundle;
        if (!isDefault && (modelBundle = modelAssembly.getProjectileModels().get(getEntityTypeId())) != null) {
            return new ProjectileModelWrapper(modelAssembly, false, modelBundle);
        }
        return null;
    }

    @Override
    public void onModelLoaded(ModelAssembly modelAssembly) {
        super.onModelLoaded(modelAssembly);
        this.projectileModelContext = modelAssembly.getProjectileModels().get(getEntityTypeId());
    }

    @Nullable
    private Identifier getEntityTypeId() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(this.entity.getType());
    }

    @Override
    public void clearModel() {
        super.clearModel();
        this.projectileModelContext = null;
    }

    @Override
    public GeoModel getAnimationProcessor() {
        return this.projectileModelContext.getModel();
    }

    @Override
    @NotNull
    public Identifier getTextureLocation() {
        return ((ProjectileModelWrapper) getRenderShape()).textureLocatable.getResourceLocation().orElseGet(MissingTextureAtlasSprite::getLocation);
    }

    @Override
    public Animation getAnimation(String str) {
        return this.projectileModelContext.getAnimations().get(str);
    }

    @Override
    @Nullable
    public AnimationController getAnimationEntries(String str) {
        return this.projectileModelContext.getAnimationControllers().get(str);
    }

    @Override
    public boolean isModelReady() {
        return super.isModelReady() && this.projectileModelContext != null && getRenderShape().isValid();
    }

    @Override
    public float getHeightScale() {
        return 0.7f;
    }

    @Override
    public float getWidthScale() {
        return 0.7f;
    }

    private static class ProjectileModelWrapper extends ModelWrapper {

        private final IResourceLocatable textureLocatable;

        public ProjectileModelWrapper(ModelAssembly modelAssembly, boolean isDefault, ProjectileModelBundle modelBundle) {
            super(modelAssembly, isDefault);
            this.textureLocatable = UploadManager.getOrCreateLocatable(modelBundle.getTexture(), true);
        }

        @Override
        public boolean isValid() {
            return this.textureLocatable.getResourceLocation().isPresent();
        }
    }
}
