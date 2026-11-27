package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.animation.controller.VehicleAnimationController;
import com.micaftic.morpher.client.core.HostAnimationAssets;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import com.micaftic.morpher.client.render.BuiltModel;
import dev.spm.core.scene.PoseAnimationLibrary;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.client.renderer.texture.AbstractTexture;

import java.util.function.Consumer;

public class VehicleModelBundle {
    private final BuiltModel model;

    private final Object2ReferenceMap<String, ClipSpec> animations;

    private final Object2ReferenceMap<String, ControllerSpec> animationControllers;

    private final PoseAnimationLibrary animationLibrary;

    private final AbstractTexture texture;

    private final Consumer<SpmVehicleEntity> controllerInitializer;

    public VehicleModelBundle(BuiltModel model, Object2ReferenceMap<String, ClipSpec> animations, Object2ReferenceMap<String, ControllerSpec> animationControllers, AbstractTexture abstractTexture, ModelResourceBundle modelResourceBundle) {
        this.model = model;
        this.animations = animations;
        this.animationControllers = animationControllers;
        this.animationLibrary = model != null
                ? HostAnimationAssets.compileLibrary("ysm:vehicle", animations,
                HostAnimationAssets.buildSkeleton(model), HostAnimationAssets.buildBindPose(model))
                : null;
        this.texture = abstractTexture;
        this.controllerInitializer = VehicleAnimationController.buildControllers(this, modelResourceBundle);
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

    public Consumer<SpmVehicleEntity> getAnimatableConsumer() {
        return this.controllerInitializer;
    }
}
