package com.micaftic.morpher.client.entity;

import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.model.ModelAssembly;
import com.micaftic.morpher.client.model.ProjectileModelBundle;
import com.micaftic.morpher.client.render.BuiltModel;
import com.micaftic.morpher.client.upload.IResourceLocatable;
import com.micaftic.morpher.client.upload.UploadManager;
import dev.spm.core.scene.PoseAnimationLibrary;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SpmProjectileEntity extends SpmEntity<Projectile> {

    private ProjectileModelBundle projectileModelContext;
    private String displayBundleKey;

    public void setDisplayBundleKey(String key) {
        if (java.util.Objects.equals(this.displayBundleKey, key)) return;
        awaitAsyncResult();
        this.displayBundleKey = key;
        clearModel();
    }

    public SpmProjectileEntity(Projectile projectile) {
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
    public SpmEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isDefault) {
        ProjectileModelBundle modelBundle;
        if (!isDefault && (modelBundle = resolveProjectileModel(modelAssembly)) != null) {
            return new ProjectileModelWrapper(modelAssembly, false, modelBundle);
        }
        return null;
    }

    @Override
    public void onModelLoaded(ModelAssembly modelAssembly) {
        super.onModelLoaded(modelAssembly);
        this.projectileModelContext = resolveProjectileModel(modelAssembly);
    }

    @Nullable
    private ProjectileModelBundle resolveProjectileModel(ModelAssembly modelAssembly) {
        if (this.displayBundleKey != null) return modelAssembly.getProjectileModels().get(com.micaftic.morpher.core.api.resource.ResourceApi.parseNative(this.displayBundleKey));
        ProjectileModelBundle exactMatch = modelAssembly.getProjectileModels().get(getEntityTypeId());
        if (exactMatch != null) {
            return exactMatch;
        }
        if (this.entity instanceof AbstractArrow) {
            return modelAssembly.getProjectileModels().get(com.micaftic.morpher.core.api.resource.ResourceApi.nativeId("minecraft", "arrow"));
        }
        return null;
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
    public BuiltModel getAnimationProcessor() {
        return this.projectileModelContext.getModel();
    }

    @Override
    @NotNull
    public Identifier getTextureLocation() {
        return ((ProjectileModelWrapper) getRenderShape()).textureLocatable.getResourceLocation().orElseGet(MissingTextureAtlasSprite::getLocation);
    }

    @Override
    public ClipSpec getAnimation(String str) {
        return this.projectileModelContext.getAnimations().get(str);
    }

    @Nullable
    public ControllerSpec getAnimationEntries(String str) {
        return this.projectileModelContext.getAnimationControllers().get(str);
    }

    @Override
    public @Nullable ControllerSpec getControllerSpec(String name) {
        return this.projectileModelContext != null ? getAnimationEntries(name) : null;
    }

    @Override
    protected @Nullable PoseAnimationLibrary resolveAnimationLibrary() {
        return this.projectileModelContext != null ? this.projectileModelContext.getAnimationLibrary() : null;
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
