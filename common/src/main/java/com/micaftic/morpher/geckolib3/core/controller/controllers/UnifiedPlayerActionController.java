package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;

import java.util.function.Consumer;

public class UnifiedPlayerActionController {

    public static final String CAP_CONTROLLER_KEY = PlayerAnimationController.CAP_CONTROLLER_KEY;
    public static final String GUI_HOVER_CONTROLLER_KEY = "player.gui_hover";
    public static final String GUI_FOCUS_CONTROLLER_KEY = "player.gui_focus";
    public static final String VANILLA_POSE_CONTROLLER_KEY = "player.vanilla_pose";
    public static final String VANILLA_FALLBACK_CONTROLLER_KEY = "player.vanilla_pose_fallback";

    private UnifiedPlayerActionController() {
    }

    public static Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return ModelActionProviderRegistry.get(modelBundle.getActionProfile()).buildControllers(modelBundle, resourceBundle);
    }
}
