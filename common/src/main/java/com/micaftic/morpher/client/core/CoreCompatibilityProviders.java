package com.micaftic.morpher.client.core;

import com.micaftic.morpher.client.animation.molang.CtrlBinding;
import com.micaftic.morpher.client.animation.molang.TLMBinding;
import com.micaftic.morpher.geckolib3.core.molang.context.AnimationContext;
import com.micaftic.morpher.molang.runtime.ExpressionEvaluator;
import com.micaftic.morpher.molang.runtime.Variable;
import com.micaftic.morpher.molang.runtime.binding.ObjectBinding;
import com.micaftic.morpher.molang.runtime.binding.ValueConversions;
import dev.spm.core.scene.MolangValue;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registers Host compatibility providers for all 12 {@link CoreCompatibilityDefaults.Integration}
 * entries (including TLM, TaCZ, BetterCombat, CarryOn, ParCool, Create, Curios, etc.) so
 * {@link MinecraftHostSnapshotSampler} feeds live mod state into {@code spm-core}.
 */
public final class CoreCompatibilityProviders {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private CoreCompatibilityProviders() {
    }

    public static void registerAll() {
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        for (CoreCompatibilityDefaults.Integration integration : CoreCompatibilityDefaults.Integration.values()) {
            MinecraftHostSnapshotSampler.registerCompatibilityProvider(
                    integration,
                    context -> sampleIntegration(integration, context == null ? null : context.entity()));
        }
    }

    public static void registerInto(CoreCompatibilityRegistry<MinecraftHostSnapshotSampler.CompatibilityContext> registry) {
        for (CoreCompatibilityDefaults.Integration integration : CoreCompatibilityDefaults.Integration.values()) {
            registry.registerProvider(
                    integration,
                    context -> sampleIntegration(integration, context == null ? null : context.entity()));
        }
    }

    public static Map<String, MolangValue> sampleIntegration(
            CoreCompatibilityDefaults.Integration integration,
            @Nullable LivingEntity entity) {
        Map<String, MolangValue> defaults = CoreCompatibilityDefaults.defaultValuesFor(integration);
        if (entity == null) {
            return defaults;
        }
        AnimationContext<LivingEntity> animContext = new AnimationContext<>(entity, null, null, null);
        ExpressionEvaluator<AnimationContext<LivingEntity>> evaluator = ExpressionEvaluator.evaluator(animContext);
        Map<String, MolangValue> sampled = new LinkedHashMap<>(defaults.size());
        for (Map.Entry<String, MolangValue> entry : defaults.entrySet()) {
            String key = entry.getKey();
            MolangValue fallback = entry.getValue();
            Object binding = resolveBinding(key);
            if (binding == null) {
                sampled.put(key, fallback);
                continue;
            }
            sampled.put(key, evaluateAsCoreValue(binding, evaluator, fallback));
        }
        return Map.copyOf(sampled);
    }

    private static @Nullable Object resolveBinding(String key) {
        int dot = key.indexOf('.');
        if (dot <= 0 || dot + 1 >= key.length()) {
            return null;
        }
        String namespace = key.substring(0, dot);
        String name = key.substring(dot + 1);
        ObjectBinding source;
        try {
            source = switch (namespace) {
                case "ctrl" -> CtrlBinding.INSTANCE.get();
                case "tlm" -> TLMBinding.INSTANCE.get();
                default -> null;
            };
        } catch (org.apache.commons.lang3.concurrent.ConcurrentException ignored) {
            return null;
        }
        return source == null ? null : source.getProperty(name);
    }

    private static MolangValue evaluateAsCoreValue(
            Object binding,
            ExpressionEvaluator<AnimationContext<LivingEntity>> evaluator,
            MolangValue fallback) {
        try {
            Object raw;
            if (binding instanceof Variable variable) {
                raw = variable.evaluate(evaluator);
            } else {
                raw = binding;
            }
            if (raw == null) {
                return fallback;
            }
            if (fallback instanceof MolangValue.BooleanValue) {
                return MolangValue.bool(ValueConversions.asBoolean(raw));
            }
            if (fallback instanceof MolangValue.NumberValue) {
                double n = ValueConversions.asDouble(raw);
                return Double.isFinite(n) ? MolangValue.number(n) : fallback;
            }
            if (fallback instanceof MolangValue.TextValue) {
                String text = ValueConversions.asString(raw);
                return text != null ? MolangValue.text(text) : fallback;
            }
            return fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }
}
