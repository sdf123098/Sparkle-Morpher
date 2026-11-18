package com.micaftic.morpher.client.animation.controller;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;

import java.util.function.Consumer;

public class HybridActionProvider implements PlayerActionProvider {

    @Override
    public Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        Consumer<CustomPlayerEntity> authored = PlayerAnimationController.buildControllers(modelBundle, resourceBundle);
        return entity -> {
            authored.accept(entity);
            entity.addAnimationController(new ImportedVanillaPoseController(entity, UnifiedPlayerActionController.VANILLA_FALLBACK_CONTROLLER_KEY, true));
        };
    }
}
