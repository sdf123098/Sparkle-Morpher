package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Host-only SPI: loader and optional-mod callbacks end before immutable values reach the Core. */
public final class CoreCompatibilityRegistry<C> {
    @FunctionalInterface public interface Provider<C> { Map<String, MolangValue> sample(C context); }
    public record Snapshot(Map<CoreCompatibilityDefaults.Integration, CoreCompatibilityDefaults.Presence> presence,
                           Map<String, MolangValue> values) {
        public Snapshot { presence = Map.copyOf(presence); values = Map.copyOf(values); }
        public boolean present(CoreCompatibilityDefaults.Integration integration) {
            return presence.get(integration) == CoreCompatibilityDefaults.Presence.PRESENT;
        }
    }
    private Predicate<String> presence;
    private final Map<CoreCompatibilityDefaults.Integration, Provider<C>> providers =
            new EnumMap<>(CoreCompatibilityDefaults.Integration.class);
    public synchronized void registerPresence(Predicate<String> sampler) { presence = Objects.requireNonNull(sampler); }
    /** Read-only actual loader fact, also usable for function-only integrations. Never guesses absence. */
    public synchronized boolean isInstalled(String modId) {
        if (presence == null) throw new IllegalStateException("Core Host loader compatibility presence sampler was not registered");
        return presence.test(Objects.requireNonNull(modId));
    }
    public synchronized void registerProvider(CoreCompatibilityDefaults.Integration integration, Provider<C> provider) {
        if (providers.putIfAbsent(Objects.requireNonNull(integration), Objects.requireNonNull(provider)) != null)
            throw new IllegalStateException("Duplicate Core Host compatibility provider: " + integration);
    }
    public synchronized Snapshot sample(C context) {
        if (presence == null) throw new IllegalStateException("Core Host loader compatibility presence sampler was not registered");
        var facts = new EnumMap<CoreCompatibilityDefaults.Integration, CoreCompatibilityDefaults.Presence>(CoreCompatibilityDefaults.Integration.class);
        var values = new EnumMap<CoreCompatibilityDefaults.Integration, Map<String, MolangValue>>(CoreCompatibilityDefaults.Integration.class);
        for (var integration : CoreCompatibilityDefaults.Integration.values()) {
            boolean installed = presence.test(modId(integration));
            facts.put(integration, installed ? CoreCompatibilityDefaults.Presence.PRESENT : CoreCompatibilityDefaults.Presence.ABSENT);
            if (installed) {
                var provider = providers.get(integration);
                if (provider == null) throw new IllegalStateException("Installed compatibility integration requires a Host provider: " + integration);
                values.put(integration, Map.copyOf(Objects.requireNonNull(provider.sample(context))));
            }
        }
        return new Snapshot(facts, CoreCompatibilityDefaults.snapshot(facts, values));
    }
    /** Verified in each d253654 Compat class's loader check; these are IDs, not display names. */
    public static String modId(CoreCompatibilityDefaults.Integration integration) {
        return switch (integration) {
            case SOPHISTICATED -> "sophisticatedbackpacks";
            case BETTER_COMBAT -> "bettercombat";
            case CARRY_ON -> "carryon";
            case CREATE -> "create";
            case CURIOS -> "curios";
            case IMMERSIVE_MELODIES -> "immersive_melodies";
            case IRONS_SPELLBOOKS -> "irons_spellbooks";
            case PARCOOL -> "parcool";
            case SLASHBLADE -> "slashblade";
            case SWEM -> "swem";
            case TACZ -> "tacz";
            case TLM -> "touhou_little_maid";
        };
    }
}
