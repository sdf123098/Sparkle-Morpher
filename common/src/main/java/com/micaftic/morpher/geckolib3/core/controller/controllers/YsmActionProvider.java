package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;

import java.util.function.Consumer;

public class YsmActionProvider implements PlayerActionProvider {

    @Override
    public Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return PlayerAnimationController.buildControllers(modelBundle, resourceBundle);
    }
}
