package com.micaftic.morpher.geckolib3.core.controller.controllers;

import com.micaftic.morpher.client.animation.predicate.PlayerBaseAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.PlayerCustomAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.PlayerIdleAnimationPredicate;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.geckolib3.core.controller.PredicateBasedController;

import java.util.function.Consumer;

public class ImportedPlayerAnimationController {

    public static final String MAIN_CONTROLLER_KEY = UnifiedPlayerActionController.VANILLA_POSE_CONTROLLER_KEY;

    public static final String CAP_CONTROLLER_KEY = UnifiedPlayerActionController.CAP_CONTROLLER_KEY;

    private ImportedPlayerAnimationController() {
    }

    public static Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        return entity -> {
            entity.addAnimationController(new ImportedVanillaPoseController(MAIN_CONTROLLER_KEY));
            entity.addAnimationController(new PredicateBasedController<>(entity, CAP_CONTROLLER_KEY, 0.0f, new PlayerBaseAnimationPredicate()));
            if (entity instanceof IPreviewAnimatable) {
                entity.addAnimationController(new PredicateBasedController<>(entity, UnifiedPlayerActionController.GUI_HOVER_CONTROLLER_KEY, 0.0f, new PlayerCustomAnimationPredicate()));
                entity.addAnimationController(new PredicateBasedController<>(entity, UnifiedPlayerActionController.GUI_FOCUS_CONTROLLER_KEY, 0.0f, new PlayerIdleAnimationPredicate()));
            }
        };
    }
}
