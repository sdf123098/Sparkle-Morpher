package com.micaftic.morpher.client.animation.controller;

import com.micaftic.morpher.client.animation.StopAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.client.animation.predicate.EntityMovementPredicate;
import com.micaftic.morpher.client.animation.predicate.MovementAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.NamedAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.RideStateAnimationPredicate;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.entity.SpmVehicleEntity;
import com.micaftic.morpher.client.entity.VehicleRotationController;
import com.micaftic.morpher.client.model.AnimationDataProvider;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.VehicleModelBundle;
import com.micaftic.morpher.client.model.processor.ModelProcessor;
import com.micaftic.morpher.client.model.processor.NamedModelProcessor;
import com.micaftic.morpher.client.model.processor.ParallelProcessor;
import com.micaftic.morpher.client.model.processor.ProcessorPipeline;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import org.apache.commons.lang3.function.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class VehicleAnimationController {

    private static final String VEHICLE_PREFIX = "vehicle";

    public static final String ORIGIN_CONTROLLER_KEY = "vehicle.origin";

    private static final ProcessorPipeline<SpmVehicleEntity, VehicleModelBundle> REGISTRY = new ProcessorPipeline<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerControllers() {
        registerParallelController("pre_parallel", (animationEntryKey, entity, linkedAnimationName) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, linkedAnimationName != null ? new NamedAnimationPredicate<>(linkedAnimationName) : new StopAnimationPredicate<>()));
        registerNamedController("pre_main", null, true, (animationEntryKey, entity) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerNamedController("main", EntityMovementPredicate.MOVEMENT_STATES, true, (animationEntryKey, entity) ->
            new HostControllerSlot<>(entity, animationEntryKey, 2.0f, new EntityMovementPredicate()));
        registerNamedController("move", MovementAnimationPredicate.ANIMATION_NAMES, true, (animationEntryKey, entity) ->
            new HostControllerSlot(entity, animationEntryKey, 2.0f, new MovementAnimationPredicate()));
        registerOriginController("origin", (animationEntryKey, entity) ->
            new VehicleRotationController(entity, animationEntryKey));
        registerNamedController("ride", RideStateAnimationPredicate.ANIMATION_NAMES, true, (animationEntryKey, entity) ->
            new HostControllerSlot(entity, animationEntryKey, 2.0f, new RideStateAnimationPredicate()));
        registerNamedController("post_main", null, true, (animationEntryKey, entity) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerParallelController("parallel", (animationEntryKey, entity, linkedAnimationName) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, linkedAnimationName != null ? new NamedAnimationPredicate<>(linkedAnimationName) : new StopAnimationPredicate<>(), true));
    }

    public static Consumer<SpmVehicleEntity> buildControllers(VehicleModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        REGISTRY.initializeOnce(VehicleAnimationController::registerControllers);
        return REGISTRY.buildAll(modelBundle, resourceBundle);
    }

    private static ModelProcessor<SpmVehicleEntity, VehicleModelBundle> registerOriginController(String controllerName, BiFunction<String, SpmVehicleEntity, HostControllerSlot<SpmVehicleEntity>> controllerFactory) {
        String controllerKey = String.format("%s.%s", VEHICLE_PREFIX, controllerName);
        return REGISTRY.register((modelData, resourceBundle) -> (entity, consumer) -> consumer.accept(controllerFactory.apply(controllerKey, entity)));
    }

    private static ModelProcessor<SpmVehicleEntity, VehicleModelBundle> registerNamedController(String slotName, String[] requiredAnimations, boolean checkAnimationEntries, BiFunction<String, SpmVehicleEntity, HostControllerSlot<SpmVehicleEntity>> controllerFactory) {
        return REGISTRY.register(new NamedModelProcessor<>(VEHICLE_PREFIX, slotName, requiredAnimations, checkAnimationEntries, VehicleAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static ModelProcessor<SpmVehicleEntity, VehicleModelBundle> registerParallelController(String slotName, TriFunction<String, SpmVehicleEntity, String, HostControllerSlot<SpmVehicleEntity>> controllerFactory) {
        return REGISTRY.register(new ParallelProcessor<>(VEHICLE_PREFIX, slotName, false, VehicleAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static class VehicleAnimationDataProvider implements AnimationDataProvider<VehicleModelBundle> {

        public static final VehicleAnimationDataProvider INSTANCE = new VehicleAnimationDataProvider();

        private VehicleAnimationDataProvider() {
        }

        @Override
        public Object2ReferenceMap<String, ControllerSpec> getAnimationEntries(VehicleModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimationControllers();
        }

        @Override
        public Object2ReferenceMap<String, ClipSpec> getAnimations(VehicleModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(VehicleModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return null;
        }
    }
}
