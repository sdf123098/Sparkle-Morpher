package com.micaftic.morpher.client.model.processor;

import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;

import java.util.function.Predicate;

public interface ModelProcessor<T extends GeoEntity<?>, TModel> {
    ControllerFactory<T> process(TModel modelData, ModelResourceBundle resourceBundle);

    default ModelProcessor<T, TModel> withFilter(Predicate<T> predicate) {
        return (modelData, resourceBundle) -> {
            ControllerFactory<T> installer = process(modelData, resourceBundle);
            return (entity, consumer) -> {
                if (predicate.test(entity)) {
                    installer.create(entity, consumer);
                }
            };
        };
    }
}