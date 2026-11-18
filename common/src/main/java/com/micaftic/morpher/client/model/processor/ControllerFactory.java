package com.micaftic.morpher.client.model.processor;

import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.entity.SpmEntity;

import java.util.function.Consumer;

public interface ControllerFactory<T extends SpmEntity<?>> {
    void create(T entity, Consumer<HostControllerSlot<T>> consumer);
}