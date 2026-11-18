package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.animation.controller.ProjectileAnimationController;
import com.micaftic.morpher.client.core.HostAnimationAssets;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.entity.SpmProjectileEntity;
import com.micaftic.morpher.client.render.BuiltModel;
import dev.spm.core.scene.PoseAnimationLibrary;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.Consumer;

public class ProjectileModelBundle {

    private final BuiltModel model;

    private final Object2ReferenceMap<String, ClipSpec> animations;

    private final Object2ReferenceMap<String, ControllerSpec> animationControllers;

    private final PoseAnimationLibrary animationLibrary;

    private final AbstractTexture texture;

    private final Consumer<SpmProjectileEntity> controllerInitializer;

    public ProjectileModelBundle(BuiltModel model, Object2ReferenceMap<String, ClipSpec> animations, Object2ReferenceMap<String, ControllerSpec> animationControllers, AbstractTexture texture, ModelResourceBundle resourceBundle) {
        this.model = model;
        this.animations = animations;
        this.animationControllers = animationControllers;
        this.animationLibrary = model != null
                ? HostAnimationAssets.compileLibrary("ysm:projectile", animations.values(),
                HostAnimationAssets.buildSkeleton(model), HostAnimationAssets.buildBindPose(model))
                : null;
        this.texture = texture;
        this.controllerInitializer = ProjectileAnimationController.buildControllers(this, resourceBundle);
    }

    public BuiltModel getModel() {
        return this.model;
    }

    public Object2ReferenceMap<String, ClipSpec> getAnimations() {
        return this.animations;
    }

    public Object2ReferenceMap<String, ControllerSpec> getAnimationControllers() {
        return this.animationControllers;
    }

    public PoseAnimationLibrary getAnimationLibrary() {
        return this.animationLibrary;
    }

    public AbstractTexture getTexture() {
        return this.texture;
    }

    public Consumer<SpmProjectileEntity> getControllerInitializer() {
        return this.controllerInitializer;
    }
}
