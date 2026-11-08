package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Pinned d253654 absent-mod getter values, for an eligible client-player Host context.
 * No presence discovery, Minecraft objects, live callbacks or function execution belong here.
 */
public final class CoreCompatibilityDefaults {
    public enum Integration {
        SOPHISTICATED, BETTER_COMBAT, CARRY_ON, CREATE, CURIOS, IMMERSIVE_MELODIES,
        IRONS_SPELLBOOKS, PARCOOL, SLASHBLADE, SWEM, TACZ, TLM
    }
    public enum Presence { ABSENT, PRESENT, UNKNOWN }
    private static final Map<Integration, Map<String, MolangValue>> ABSENT = defaults();
    private CoreCompatibilityDefaults() { }

    /** Every integration needs an explicit loader-sampled fact. PRESENT requires a complete,
     * already-sampled getter map from its Host provider; this method never invokes a provider.
     * Functions (including Curios placeholders) are a separate contract, not getter slots.
     */
    public static Map<String, MolangValue> snapshot(Map<Integration, Presence> presence,
                                                   Map<Integration, Map<String, MolangValue>> providerValues) {
        Objects.requireNonNull(presence); Objects.requireNonNull(providerValues);
        if (presence.size() != Integration.values().length)
            throw new IllegalArgumentException("Explicit presence is required for every Core Host compatibility integration");
        for (var integration : providerValues.keySet())
            if (integration == null) throw new IllegalArgumentException("Unknown compatibility provider");
        var result = new HashMap<String, MolangValue>();
        for (var integration : Integration.values()) {
            var fact = presence.get(integration);
            if (fact == null) throw new IllegalArgumentException("Missing compatibility presence: " + integration);
            if (fact == Presence.UNKNOWN) throw new IllegalStateException("Unknown compatibility presence: " + integration);
            Map<String, MolangValue> values;
            if (fact == Presence.ABSENT) {
                if (providerValues.containsKey(integration))
                    throw new IllegalArgumentException("Provider contradicts absent compatibility fact: " + integration);
                values = ABSENT.get(integration);
            } else {
                values = providerValues.get(integration);
                if (values == null) throw new IllegalStateException("Installed compatibility integration requires a Host provider: " + integration);
                if (!values.keySet().equals(ABSENT.get(integration).keySet()))
                    throw new IllegalArgumentException("Incomplete or foreign compatibility getter keys: " + integration);
                values = Map.copyOf(values);
            }
            result.putAll(values);
        }
        return Map.copyOf(result);
    }

    private static Map<Integration, Map<String, MolangValue>> defaults() {
        var result = new EnumMap<Integration, Map<String, MolangValue>>(Integration.class);
        result.put(Integration.SOPHISTICATED, Map.of("ctrl.has_sophisticated_backpack", MolangValue.bool(false)));
        result.put(Integration.BETTER_COMBAT, Map.of("ctrl.bcombat_attack_animation", MolangValue.text("")));
        result.put(Integration.CARRY_ON, Map.of("ctrl.carryon_type", MolangValue.text(""), "ctrl.carryon_is_princess", MolangValue.bool(false)));
        result.put(Integration.CREATE, Map.of("ctrl.create_hanging_skyhook", MolangValue.bool(false)));
        // The reference getter also prints a debug message. Only its null return is represented here.
        result.put(Integration.CURIOS, Map.of("ysm.dump_curios", MolangValue.missing()));
        result.put(Integration.IMMERSIVE_MELODIES, Map.of("ctrl.im_pitch", MolangValue.number(0),
                "ctrl.im_volume", MolangValue.number(0), "ctrl.im_current", MolangValue.number(0),
                "ctrl.im_delta", MolangValue.number(0), "ctrl.im_time", MolangValue.number(0)));
        result.put(Integration.IRONS_SPELLBOOKS, Map.of("ctrl.iss_animation", MolangValue.text("")));
        result.put(Integration.PARCOOL, Map.of("ctrl.parcool_state", MolangValue.text("")));
        result.put(Integration.SLASHBLADE, Map.of("ctrl.slashblade_animation", MolangValue.text("")));
        result.put(Integration.SWEM, Map.of("ctrl.swem_is_ride", MolangValue.bool(false), "ctrl.swem_state", MolangValue.text("")));
        result.put(Integration.TACZ, Map.of("ctrl.tac_hold_gun", MolangValue.bool(false),
                "ctrl.tac_gun_type", MolangValue.text(""), "ctrl.tac_gun_id", MolangValue.text(""),
                "ctrl.tac_is_fire", MolangValue.bool(false), "ctrl.tac_is_aim", MolangValue.bool(false),
                "ctrl.tac_is_reload", MolangValue.bool(false), "ctrl.tac_is_melee", MolangValue.bool(false),
                "ctrl.tac_is_draw", MolangValue.bool(false), "ctrl.tac_fire_mode", MolangValue.text("")));
        result.put(Integration.TLM, Map.ofEntries(
                Map.entry("tlm.is_begging", MolangValue.bool(false)), Map.entry("tlm.is_sitting", MolangValue.bool(false)),
                Map.entry("tlm.has_backpack", MolangValue.bool(false)), Map.entry("tlm.favorability_point", MolangValue.number(0)),
                Map.entry("tlm.favorability_level", MolangValue.number(0)), Map.entry("tlm.task_id", MolangValue.text("")),
                Map.entry("tlm.schedule", MolangValue.text("")), Map.entry("tlm.activity", MolangValue.text("")),
                Map.entry("tlm.gomoku_win_count", MolangValue.number(0)), Map.entry("tlm.gomoku_rank", MolangValue.number(1)),
                Map.entry("tlm.game_statue", MolangValue.text("")), Map.entry("tlm.backpack_type", MolangValue.text("")),
                Map.entry("tlm.is_entity", MolangValue.bool(true)), Map.entry("tlm.is_statue", MolangValue.bool(false)),
                Map.entry("tlm.is_garage_kit", MolangValue.bool(false)), Map.entry("tlm.show_item", MolangValue.text(""))));
        return Map.copyOf(result);
    }
}
