package com.micaftic.morpher.client.animation.controller;

import com.micaftic.morpher.client.animation.AnimationManager;
import com.micaftic.morpher.client.animation.StopAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.client.animation.predicate.ArmorPredicate;
import com.micaftic.morpher.client.animation.predicate.InteractionHandAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.ItemHoldAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.LivingMovementAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.MainHandHoldPredicate;
import com.micaftic.morpher.client.animation.predicate.NamedAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.OffHandHoldPredicate;
import com.micaftic.morpher.client.animation.predicate.OffhandAttackAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.PlayerBaseAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.PlayerCustomAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.PlayerIdleAnimationPredicate;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.entity.CustomPlayerEntity;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.model.AnimationDataProvider;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.client.model.processor.ArmorSlotProcessor;
import com.micaftic.morpher.client.model.processor.ControllerSlotBinder;
import com.micaftic.morpher.client.model.processor.ModelProcessor;
import com.micaftic.morpher.client.model.processor.NamedModelProcessor;
import com.micaftic.morpher.client.model.processor.ParallelProcessor;
import com.micaftic.morpher.client.model.processor.ProcessorPipeline;
import com.micaftic.morpher.core.compat.carryon.CarryOnCompat;
import com.micaftic.morpher.core.compat.gun.common.ItemUseAnimationPredicate;
import com.micaftic.morpher.core.compat.parcool.ParcoolCompat;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.world.entity.EquipmentSlot;
import org.apache.commons.lang3.function.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class PlayerAnimationController {

    private static final ProcessorPipeline<CustomPlayerEntity, PlayerModelBundle> REGISTRY = new ProcessorPipeline<>();

    private static final String PLAYER_PREFIX = "player";

    public static final String CAP_CONTROLLER_KEY = String.format("%s.%s", PLAYER_PREFIX, "cap");

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerControllers() {
        registerParallelController("pre_parallel", (animationEntryKey, entity, linkedAnimationName) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, linkedAnimationName != null ? new NamedAnimationPredicate<>(linkedAnimationName) : new StopAnimationPredicate<>()));
        ParcoolCompat.getControllerFactory().ifPresent(controllerFactory -> {
            registerController("parcool", controllerFactory);
        });
        registerController("vehicle", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 2.0f, new LivingMovementAnimationPredicate()));
        registerSlotController("pre_main", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerController("main", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 2.0f, new AnimationManager()));
        registerSlotController("post_main", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerSlotController("pre_hold", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerController("hold_offhand", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 2.0f, new OffHandHoldPredicate()));
        registerController("hold_mainhand", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 2.0f, new MainHandHoldPredicate()));
        registerSlotController("post_hold", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        if (ItemUseAnimationPredicate.isLoaded()) {
            registerController("fire", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 0.0f, new ItemUseAnimationPredicate()));
        }
        registerSlotController("pre_swing", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerController("swing", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 0.0f, new ItemHoldAnimationPredicate()));
        registerSlotController("post_swing", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerSlotController("pre_use", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerController("use", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 2.0f, new InteractionHandAnimationPredicate()));
        registerSlotController("post_use", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerController("passenger", (animationEntryKey, entity) -> new HostControllerSlot(entity, animationEntryKey, 2.0f, new OffhandAttackAnimationPredicate()));
        CarryOnCompat.getControllerFactory().ifPresent(controllerFactory -> registerController("carry_on", controllerFactory));
        registerController("cap", (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new PlayerBaseAnimationPredicate()));
        registerController("gui_hover", true, (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new PlayerCustomAnimationPredicate()));
        registerController("gui_focus", true, (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new PlayerIdleAnimationPredicate()));
        registerParallelController("parallel", (animationEntryKey, entity, linkedAnimationName) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, linkedAnimationName != null ? new NamedAnimationPredicate<>(linkedAnimationName) : new StopAnimationPredicate<>(), true));
        registerArmorController("armor", (animationEntryKey, entity, equipmentSlot) -> new HostControllerSlot(entity, animationEntryKey, 0.0f, new ArmorPredicate(equipmentSlot)));
    }

    public static Consumer<CustomPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        REGISTRY.initializeOnce(PlayerAnimationController::registerControllers);
        return REGISTRY.buildAll(modelBundle, resourceBundle);
    }

    public static void registerController(String controllerName, BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>> controllerFactory) {
        registerController(controllerName, false, controllerFactory);
    }

    private static void registerController(String controllerName, boolean guiOnly, BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>> controllerFactory) {
        String controllerKey = String.format("%s.%s", PLAYER_PREFIX, controllerName);
        ModelProcessor<CustomPlayerEntity, PlayerModelBundle> processor = (modelData, resourceBundle) -> (entity, consumer) -> {
            consumer.accept(controllerFactory.apply(controllerKey, entity));
        };
        if (guiOnly) {
            processor = processor.withFilter(entity -> entity instanceof IPreviewAnimatable);
        }
        REGISTRY.register(processor);
    }

    private static void registerSlotController(String slotName, BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new ControllerSlotBinder<>(PLAYER_PREFIX, slotName, PlayerAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static void registerNamedController(String slotName, String[] requiredAnimations, boolean checkAnimationEntries, BiFunction<String, CustomPlayerEntity, HostControllerSlot<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new NamedModelProcessor<>(PLAYER_PREFIX, slotName, requiredAnimations, checkAnimationEntries, PlayerAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static void registerParallelController(String slotName, TriFunction<String, CustomPlayerEntity, String, HostControllerSlot<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new ParallelProcessor<>(PLAYER_PREFIX, slotName, true, PlayerAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static void registerArmorController(String category, TriFunction<String, CustomPlayerEntity, EquipmentSlot, HostControllerSlot<CustomPlayerEntity>> controllerFactory) {
        REGISTRY.register(new ArmorSlotProcessor<>(PLAYER_PREFIX, category, PlayerAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static class PlayerAnimationDataProvider implements AnimationDataProvider<PlayerModelBundle> {

        public static final PlayerAnimationDataProvider INSTANCE = new PlayerAnimationDataProvider();

        private PlayerAnimationDataProvider() {
        }

        @Override
        public Object2ReferenceMap<String, ControllerSpec> getAnimationEntries(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimationEntries();
        }

        @Override
        public Object2ReferenceMap<String, ClipSpec> getAnimations(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getMainAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getConditionManager().getArmor();
        }
    }
}
