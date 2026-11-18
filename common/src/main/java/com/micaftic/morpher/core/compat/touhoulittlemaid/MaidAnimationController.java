package com.micaftic.morpher.core.compat.touhoulittlemaid;

import com.micaftic.morpher.client.animation.IAnimationPredicate;
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
import com.micaftic.morpher.client.core.HostAnimationAssets.ClipSpec;
import com.micaftic.morpher.client.core.HostAnimationAssets.ControllerSpec;
import com.micaftic.morpher.client.core.HostControllerSlot;
import com.micaftic.morpher.client.model.AnimationDataProvider;
import com.micaftic.morpher.client.model.ModelResourceBundle;
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

public final class MaidAnimationController {
    private static final String PLAYER_PREFIX = "player";
    private static final String MAID_PREFIX = "maid";
    private static final ProcessorPipeline<MaidCapability, PlayerModelBundle> REGISTRY = new ProcessorPipeline<>();

    private MaidAnimationController() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerControllers() {
        IAnimationPredicate<MaidCapability> stop = new StopAnimationPredicate<>();

        registerParallelController("pre_parallel", (name, cap, animation) ->
                new HostControllerSlot<>(cap, name, 0.0f,
                        animation == null ? stop : new NamedAnimationPredicate<>(animation)));
        registerController("vehicle", (name, cap) ->
                new HostControllerSlot(cap, name, 2.0f, new LivingMovementAnimationPredicate()));
        registerSlotController("pre_main", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerController("main", (name, cap) ->
                new HostControllerSlot<>(cap, name, 2.0f, new MaidMovementAnimationPredicate()));
        registerSlotController("post_main", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerSlotController("pre_hold", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerController("hold_offhand", (name, cap) ->
                new HostControllerSlot(cap, name, 2.0f, new OffHandHoldPredicate()));
        registerController("hold_mainhand", (name, cap) ->
                new HostControllerSlot(cap, name, 2.0f, new MainHandHoldPredicate()));
        registerSlotController("post_hold", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerSlotController("pre_swing", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerController("swing", (name, cap) ->
                new HostControllerSlot(cap, name, 0.0f, new ItemHoldAnimationPredicate()));
        registerSlotController("post_swing", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerSlotController("pre_use", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerController("use", (name, cap) ->
                new HostControllerSlot(cap, name, 2.0f, new InteractionHandAnimationPredicate()));
        registerSlotController("post_use", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, stop));
        registerNamedController("misc", new String[]{"game_win", "game_lost", "beg"}, true, (name, cap) ->
                new HostControllerSlot<>(cap, name, 2.0f, new MaidStateAnimationPredicate(false)));
        registerController("passenger", (name, cap) ->
                new HostControllerSlot(cap, name, 2.0f, new OffhandAttackAnimationPredicate()));
        registerController("cap", (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, new MaidRouletteAnimationPredicate()));
        registerParallelController("parallel", (name, cap, animation) ->
                new HostControllerSlot<>(cap, name, 0.0f,
                        animation == null ? stop : new NamedAnimationPredicate<>(animation), true));
        registerArmorController("armor", (name, cap, slot) ->
                new HostControllerSlot(cap, name, 0.0f, new ArmorPredicate(slot)));
        registerNamedController("statue", new String[]{"statue", "garage_kit"}, true, (name, cap) ->
                new HostControllerSlot<>(cap, name, 0.0f, new MaidStateAnimationPredicate(true)));
    }

    public static Consumer<MaidCapability> buildControllers(PlayerModelBundle model, ModelResourceBundle resources) {
        REGISTRY.initializeOnce(MaidAnimationController::registerControllers);
        return REGISTRY.buildAll(model, resources);
    }

    private static ModelProcessor<MaidCapability, PlayerModelBundle> registerController(
            String name, BiFunction<String, MaidCapability, HostControllerSlot<MaidCapability>> factory) {
        return REGISTRY.register((model, resources) -> (capability, consumer) ->
                consumer.accept(factory.apply(PLAYER_PREFIX + "." + name, capability)));
    }

    private static ModelProcessor<MaidCapability, PlayerModelBundle> registerSlotController(
            String name, BiFunction<String, MaidCapability, HostControllerSlot<MaidCapability>> factory) {
        return REGISTRY.register(new ControllerSlotBinder<>(PLAYER_PREFIX, name, DataProvider.INSTANCE, factory));
    }

    private static ModelProcessor<MaidCapability, PlayerModelBundle> registerNamedController(
            String name, String[] animations, boolean checkEntries,
            BiFunction<String, MaidCapability, HostControllerSlot<MaidCapability>> factory) {
        return REGISTRY.register(new NamedModelProcessor<>(MAID_PREFIX, name, animations, checkEntries,
                DataProvider.INSTANCE, factory));
    }

    private static ModelProcessor<MaidCapability, PlayerModelBundle> registerParallelController(
            String name, TriFunction<String, MaidCapability, String, HostControllerSlot<MaidCapability>> factory) {
        return REGISTRY.register(new ParallelProcessor<>(PLAYER_PREFIX, name, true, DataProvider.INSTANCE, factory));
    }

    private static ModelProcessor<MaidCapability, PlayerModelBundle> registerArmorController(
            String name, TriFunction<String, MaidCapability, EquipmentSlot, HostControllerSlot<MaidCapability>> factory) {
        return REGISTRY.register(new ArmorSlotProcessor<>(PLAYER_PREFIX, name, DataProvider.INSTANCE, factory));
    }

    private enum DataProvider implements AnimationDataProvider<PlayerModelBundle> {
        INSTANCE;

        @Override
        public Object2ReferenceMap<String, ControllerSpec> getAnimationEntries(
                PlayerModelBundle model, ModelResourceBundle resources) {
            return model.getAnimationEntries();
        }

        @Override
        public Object2ReferenceMap<String, ClipSpec> getAnimations(
                PlayerModelBundle model, ModelResourceBundle resources) {
            return model.getMainAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(PlayerModelBundle model, ModelResourceBundle resources) {
            return model.getConditionManager().getArmor();
        }
    }
}
