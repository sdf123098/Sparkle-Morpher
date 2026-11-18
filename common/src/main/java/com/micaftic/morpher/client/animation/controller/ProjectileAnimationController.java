package com.micaftic.morpher.client.animation.controller;

import com.micaftic.morpher.client.animation.StopAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.client.animation.predicate.NamedAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.ProjectileAnimationPredicate;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.entity.SpmProjectileEntity;
import com.micaftic.morpher.client.model.AnimationDataProvider;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.ProjectileModelBundle;
import com.micaftic.morpher.client.model.processor.ModelProcessor;
import com.micaftic.morpher.client.model.processor.NamedModelProcessor;
import com.micaftic.morpher.client.model.processor.ParallelProcessor;
import com.micaftic.morpher.client.model.processor.ProcessorPipeline;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import org.apache.commons.lang3.function.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class ProjectileAnimationController {

    private static final String PROJECTILE_PREFIX = "projectile";

    private static final ProcessorPipeline<SpmProjectileEntity, ProjectileModelBundle> REGISTRY = new ProcessorPipeline<>();

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerControllers() {
        registerNamedController("pre_main", null, true, (animationEntryKey, entity) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate()));
        registerNamedController("main", ProjectileAnimationPredicate.ENVIRONMENT_STATES, true, (animationEntryKey, entity) ->
            new HostControllerSlot<>(entity, animationEntryKey, 2.0f, new ProjectileAnimationPredicate()));
        registerNamedController("post_main", null, true, (animationEntryKey, entity) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate()));
        registerParallelController("parallel", (animationEntryKey, entity, linkedAnimationName) ->
            new HostControllerSlot<>(entity, animationEntryKey, 0.0f, linkedAnimationName != null ? new NamedAnimationPredicate(linkedAnimationName) : StopAnimationPredicate.INSTANCE, true));
    }

    public static Consumer<SpmProjectileEntity> buildControllers(ProjectileModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        REGISTRY.initializeOnce(ProjectileAnimationController::registerControllers);
        return REGISTRY.buildAll(modelBundle, resourceBundle);
    }

    private static ModelProcessor<SpmProjectileEntity, ProjectileModelBundle> registerController(String controllerName, BiFunction<String, SpmProjectileEntity, HostControllerSlot<SpmProjectileEntity>> controllerFactory) {
        String controllerKey = String.format("%s.%s", PROJECTILE_PREFIX, controllerName);
        return REGISTRY.register((modelData, resourceBundle) -> {
            return (entity, consumer) -> {
                consumer.accept(controllerFactory.apply(controllerKey, entity));
            };
        });
    }

    private static ModelProcessor<SpmProjectileEntity, ProjectileModelBundle> registerNamedController(String slotName, String[] requiredAnimations, boolean checkAnimationEntries, BiFunction<String, SpmProjectileEntity, HostControllerSlot<SpmProjectileEntity>> controllerFactory) {
        return REGISTRY.register(new NamedModelProcessor<>(PROJECTILE_PREFIX, slotName, requiredAnimations, checkAnimationEntries, ProjectileAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static ModelProcessor<SpmProjectileEntity, ProjectileModelBundle> registerParallelController(String slotName, TriFunction<String, SpmProjectileEntity, String, HostControllerSlot<SpmProjectileEntity>> controllerFactory) {
        return REGISTRY.register(new ParallelProcessor<>(PROJECTILE_PREFIX, slotName, false, ProjectileAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static class ProjectileAnimationDataProvider implements AnimationDataProvider<ProjectileModelBundle> {

        public static final ProjectileAnimationDataProvider INSTANCE = new ProjectileAnimationDataProvider();

        private ProjectileAnimationDataProvider() {
        }

        @Override
        public Object2ReferenceMap<String, ControllerSpec> getAnimationEntries(ProjectileModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimationControllers();
        }

        @Override
        public Object2ReferenceMap<String, ClipSpec> getAnimations(ProjectileModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(ProjectileModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return null;
        }
    }
}
