package com.micaftic.morpher.client.animation.controller;

import com.micaftic.morpher.client.animation.StopAnimationPredicate;
import com.micaftic.morpher.client.animation.condition.ConditionArmor;
import com.micaftic.morpher.client.animation.predicate.EquipmentSlotAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.FirstPersonLanceAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.FirstPersonWeaponAnimationPredicate;
import com.micaftic.morpher.client.animation.predicate.NamedAnimationPredicate;
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.entity.IPreviewAnimatable;
import com.micaftic.morpher.client.entity.SpmPlayerEntity;
import com.micaftic.morpher.client.model.AnimationDataProvider;
import com.micaftic.morpher.client.model.ModelResourceBundle;
import com.micaftic.morpher.client.model.ModelSourceFormat;
import com.micaftic.morpher.client.model.PlayerModelBundle;
import com.micaftic.morpher.client.model.processor.ArmorSlotProcessor;
import com.micaftic.morpher.client.model.processor.ControllerSlotBinder;
import com.micaftic.morpher.client.model.processor.ModelProcessor;
import com.micaftic.morpher.client.model.processor.NamedModelProcessor;
import com.micaftic.morpher.client.model.processor.ParallelProcessor;
import com.micaftic.morpher.client.model.processor.ProcessorPipeline;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.minecraft.world.entity.EquipmentSlot;
import org.apache.commons.lang3.function.TriFunction;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class FirstPersonArmAnimationController {

    private static final String FP_ARM_PREFIX = "fp.arm";

    private static final String[] REQUIRED_LANCE_ANIMATIONS = {
            "hold_mainhand:lance",
            "use_mainhand:lance",
            "swing:lance",
            "lance_stand",
            "lance_charge",
            "lance_jab",
            "lance_lunge",
            "lance_riding_idle",
            "lance_riding_charge",
            "lance_fall_flying_charge"
    };

    /** bbmodel/figura 导入模型第一人称自动武器兜底槽：从 arm 表按动作播放 hold/use/swing。 */
    private static final String[] REQUIRED_WEAPON_ANIMATIONS = {
            "hold_mainhand",
            "hold_offhand",
            "use_mainhand",
            "use_offhand",
            "swing_hand",
            "swing_offhand",
            "attack_empty"
    };

    private static final ProcessorPipeline<SpmPlayerEntity, PlayerModelBundle> processorRegistry = new ProcessorPipeline<>();

    private static void registerDefaultProcessors() {
        registerNamedProcessor("misc", null, true, (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new StopAnimationPredicate<>()));
        registerParallelProcessor("parallel", (animationEntryKey, entity, linkedAnimationName) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, linkedAnimationName != null ? new NamedAnimationPredicate<>(linkedAnimationName) : new StopAnimationPredicate<>(), true));
        registerArmorProcessor("armor", (animationEntryKey, entity, equipmentSlot) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new EquipmentSlotAnimationPredicate(equipmentSlot)));
        registerNamedProcessor("lance", REQUIRED_LANCE_ANIMATIONS, false, (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new FirstPersonLanceAnimationPredicate()));
    }

    /** 仅在 bbmodel/figura 导入模型上安装第一人称自动武器兜底槽。 */
    private static void registerWeaponProcessors() {
        ModelProcessor<SpmPlayerEntity, PlayerModelBundle> weapon = new NamedModelProcessor<>(
                FP_ARM_PREFIX, "weapon", REQUIRED_WEAPON_ANIMATIONS, false,
                DefaultBoneExpressionProvider.INSTANCE,
                (animationEntryKey, entity) -> new HostControllerSlot<>(entity, animationEntryKey, 0.0f, new FirstPersonWeaponAnimationPredicate()));
        processorRegistry.register(weapon);
    }

    public static Consumer<SpmPlayerEntity> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        processorRegistry.initializeOnce(FirstPersonArmAnimationController::registerDefaultProcessors);
        if (isImportedPlayerModel(modelBundle)) {
            registerWeaponProcessors();
        }
        return processorRegistry.buildAll(modelBundle, resourceBundle);
    }

    private static boolean isImportedPlayerModel(PlayerModelBundle modelBundle) {
        ModelSourceFormat sourceFormat = modelBundle.getSourceFormat();
        return sourceFormat == ModelSourceFormat.BBMODEL || sourceFormat == ModelSourceFormat.FIGURA_BBMODEL;
    }

    private static void registerSimpleProcessor(String slotName, BiFunction<String, SpmPlayerEntity, HostControllerSlot<SpmPlayerEntity>> controllerFactory) {
        registerProcessorWithFilter(slotName, false, controllerFactory);
    }

    private static void registerProcessorWithFilter(String slotName, boolean skipOnPreview, BiFunction<String, SpmPlayerEntity, HostControllerSlot<SpmPlayerEntity>> controllerFactory) {
        String animationEntryKey = String.format("%s.%s", FP_ARM_PREFIX, slotName);
        ModelProcessor<SpmPlayerEntity, PlayerModelBundle> processor = (modelData, resourceBundle) -> {
            return (entity, consumer) -> {
                consumer.accept(controllerFactory.apply(animationEntryKey, entity));
            };
        };
        if (skipOnPreview) {
            processor = processor.withFilter(entity -> entity instanceof IPreviewAnimatable);
        }
        processorRegistry.register(processor);
    }

    private static void registerMolangProcessor(String slotName, BiFunction<String, SpmPlayerEntity, HostControllerSlot<SpmPlayerEntity>> controllerFactory) {
        processorRegistry.register(new ControllerSlotBinder<>(FP_ARM_PREFIX, slotName, DefaultBoneExpressionProvider.INSTANCE, controllerFactory));
    }

    private static void registerNamedProcessor(String slotName, String[] requiredAnimations, boolean checkAnimationEntries, BiFunction<String, SpmPlayerEntity, HostControllerSlot<SpmPlayerEntity>> controllerFactory) {
        processorRegistry.register(new NamedModelProcessor<>(FP_ARM_PREFIX, slotName, requiredAnimations, checkAnimationEntries, DefaultBoneExpressionProvider.INSTANCE, controllerFactory));
    }

    private static void registerParallelProcessor(String slotName, TriFunction<String, SpmPlayerEntity, String, HostControllerSlot<SpmPlayerEntity>> controllerFactory) {
        processorRegistry.register(new ParallelProcessor<>(FP_ARM_PREFIX, slotName, true, DefaultBoneExpressionProvider.INSTANCE, controllerFactory));
    }

    private static void registerArmorProcessor(String category, TriFunction<String, SpmPlayerEntity, EquipmentSlot, HostControllerSlot<SpmPlayerEntity>> controllerFactory) {
        processorRegistry.register(new ArmorSlotProcessor<>(FP_ARM_PREFIX, category, DefaultBoneExpressionProvider.INSTANCE, controllerFactory));
    }

    private static class DefaultBoneExpressionProvider implements AnimationDataProvider<PlayerModelBundle> {

        public static final DefaultBoneExpressionProvider INSTANCE = new DefaultBoneExpressionProvider();

        private DefaultBoneExpressionProvider() {
        }

        @Override
        public Object2ReferenceMap<String, ControllerSpec> getAnimationEntries(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimationEntries();
        }

        @Override
        public Object2ReferenceMap<String, ClipSpec> getAnimations(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getArmAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getModelProcessor().getConditionArmor();
        }
    }
}
