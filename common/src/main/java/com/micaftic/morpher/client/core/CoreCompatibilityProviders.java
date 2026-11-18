package com.micaftic.morpher.client.core;

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
        Map<String, MolangValue> sampled = new LinkedHashMap<>(defaults.size());
        for (Map.Entry<String, MolangValue> entry : defaults.entrySet()) {
            String key = entry.getKey();
            MolangValue fallback = entry.getValue();
            MolangValue value;
            try {
                value = HostMolangBindings.sampleExtensionVar(key, entity);
            } catch (Throwable ignored) {
                value = null;
            }
            sampled.put(key, coerceValue(value, fallback));
        }
        return Map.copyOf(sampled);
    }

    private static MolangValue coerceValue(@Nullable MolangValue value, MolangValue fallback) {
        if (value == null || value == MolangValue.missing()) {
            return fallback;
        }
        if (fallback instanceof MolangValue.BooleanValue) {
            return MolangValue.bool(value.truth());
        }
        if (fallback instanceof MolangValue.NumberValue) {
            double n = value.number();
            return Double.isFinite(n) ? MolangValue.number(n) : fallback;
        }
        if (fallback instanceof MolangValue.TextValue) {
            if (value instanceof MolangValue.TextValue text && text.value() != null) {
                return text;
            }
            return fallback;
        }
        return value;
    }
}
