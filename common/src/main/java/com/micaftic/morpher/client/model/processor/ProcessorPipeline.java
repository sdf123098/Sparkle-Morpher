package com.micaftic.morpher.client.model.processor;

import com.micaftic.morpher.client.entity.GeoEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.geckolib3.core.controller.IAnimationController;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

import java.util.Objects;
import java.util.function.Consumer;

public class ProcessorPipeline<T extends GeoEntity<?>, TModel> {

    private final ReferenceArrayList<ModelProcessor<T, TModel>> processors = new ReferenceArrayList<>();

    public boolean isEmpty() {
        return this.processors.isEmpty();
    }

    public Consumer<T> buildAll(TModel modelData, ModelResourceBundle resourceBundle) {
        ReferenceArrayList<ControllerFactory<T>> installers = new ReferenceArrayList<>(this.processors.size());
        for (ModelProcessor<T, TModel> processor : this.processors) {
            installers.add(processor.process(modelData, resourceBundle));
        }
        return entity -> {
            Objects.requireNonNull(entity);
            Consumer<IAnimationController<T>> consumer = entity::addAnimationController;
            for (ControllerFactory<T> installer : installers) {
                if (installer != null) {
                    installer.create(entity, consumer);
                }
            }
        };
    }

    public ModelProcessor<T, TModel> register(ModelProcessor<T, TModel> processor) {
        this.processors.add(processor);
        return processor;
    }
}