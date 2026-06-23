package com.micaftic.morpher.client.model;

import com.micaftic.morpher.client.texture.OuterFileTexture;
import com.micaftic.morpher.geckolib3.file.AnimationControllerFile;
import com.micaftic.morpher.geckolib3.file.AnimationFile;
import com.micaftic.morpher.geckolib3.geo.render.built.GeoModel;
import com.micaftic.morpher.util.data.OrderedStringMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;

public class MainModelData {

    private final List<GeoModel> models;

    private final Map<String, AnimationFile> animations;

    private final List<AnimationControllerFile> animationControllers;

    private final OrderedStringMap<String, OuterFileTexture> textureMap;

    public MainModelData(GeoModel[] models, Map<String, AnimationFile> animations, AnimationControllerFile[] animationControllerFiles, OrderedStringMap<String, OuterFileTexture> textureMap) {
        this.models = ObjectArrayList.wrap(models);
        this.animations = animations;
        this.animationControllers = ObjectArrayList.wrap(animationControllerFiles);
        this.textureMap = textureMap;
    }

    public Map<String, AnimationFile> getAnimations() {
        return this.animations;
    }

    public List<GeoModel> getModels() {
        return this.models;
    }

    public List<AnimationControllerFile> getAnimationControllers() {
        return this.animationControllers;
    }

    public OrderedStringMap<String, OuterFileTexture> getTextureMap() {
        return this.textureMap;
    }
}