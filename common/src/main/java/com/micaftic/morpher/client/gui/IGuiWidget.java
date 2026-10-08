package com.micaftic.morpher.client.gui;

import com.micaftic.morpher.client.model.ModelAssembly;
import java.util.Map;

public interface IGuiWidget {
    default void onLocalModelsReloadBegin() {
    }

    default void onModelsLoaded(Map<String, ModelAssembly> map) {
    }

    default void onModelsUpdated(Map<String, ModelAssembly> map) {
    }

    default void onLocalModelsReloadComplete() {
    }

}
