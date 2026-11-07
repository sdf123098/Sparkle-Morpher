package com.micaftic.morpher.client.core;

import dev.spm.core.scene.MolangValue;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** d253 CtrlBinding condition order. This is a Host snapshot, not a coded-controller runtime. */
public final class CoreVanillaController {
    private static final String[] ORDER = {"death", "riptide", "sleep", "swim", "climb", "climbing",
            "ladder_up", "ladder_stillness", "ladder_down", "fly", "elytra_fly", "swim_stand",
            "attacked", "jump", "sneak", "sneaking", "run", "walk", "idle"};
    private CoreVanillaController() { }
    public static Map<String, MolangValue> snapshot(Set<String> satisfied, boolean preview,
                                                   boolean livingVehicle, boolean parCoolPresent) {
        if (!preview && parCoolPresent) throw new IllegalStateException("Core Host ParCool animation suppression requires a registered live compatibility adapter");
        String selected = "";
        if (!preview && !livingVehicle) {
            for (String condition : ORDER) {
                if (condition.equals("idle") || satisfied.contains(condition)) { selected = condition; break; }
            }
        }
        var result = new LinkedHashMap<String, MolangValue>();
        for (String condition : ORDER) result.put("ctrl." + condition, MolangValue.bool(condition.equals(selected)));
        return Map.copyOf(result);
    }
}
