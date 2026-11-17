package com.micaftic.morpher.geckolib3.file;

import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.geckolib3.file.AnimationControllerFile;
import com.micaftic.morpher.client.render.BuiltModel;

public class ProjectileModelFiles {
    private final String[] textureNames;

    private final BuiltModel model;

    private final AnimationFile animations;

    private final AnimationControllerFile animationController;

    private final OuterFileTexture texture;

    public ProjectileModelFiles(String[] textureNames, BuiltModel model, AnimationFile animations, AnimationControllerFile animationControllers, OuterFileTexture texture) {
        this.textureNames = textureNames;
        this.model = model;
        this.animations = animations;
        this.animationController = animationControllers;
        this.texture = texture;
    }

    public String[] getTextureNames() {
        return this.textureNames;
    }

    public BuiltModel getModel() {
        return this.model;
    }

    public AnimationFile getAnimations() {
        return this.animations;
    }

    public AnimationControllerFile getAnimationController() {
        return this.animationController;
    }

    public OuterFileTexture getTexture() {
        return this.texture;
    }
}
