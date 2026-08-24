package com.micaftic.morpher.client.model.processor;

import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import com.micaftic.morpher.client.entity.GeoEntity;

import java.util.function.Consumer;

public interface ControllerFactory<T extends GeoEntity<?>> {
    void create(T entity, Consumer<IAnimationController<T>> consumer);
}