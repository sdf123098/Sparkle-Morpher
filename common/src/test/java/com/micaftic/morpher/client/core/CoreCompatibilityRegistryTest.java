package com.micaftic.morpher.client.core;
import dev.spm.core.scene.MolangValue;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class CoreCompatibilityRegistryTest {
    @Test void realLoaderIdsAreQueriedAndAbsentDefaultsReachSnapshot() {
        var ids = new java.util.HashSet<String>();
        var registry = new CoreCompatibilityRegistry<String>();
        registry.registerPresence(id -> { ids.add(id); return false; });
        var snapshot = registry.sample("client context");
        assertEquals(java.util.Set.of("sophisticatedbackpacks", "bettercombat", "carryon", "create", "curios", "immersive_melodies", "irons_spellbooks", "parcool", "slashblade", "swem", "tacz", "touhou_little_maid"), ids);
        assertEquals(MolangValue.number(1), snapshot.values().get("tlm.gomoku_rank"));
        assertFalse(snapshot.present(CoreCompatibilityDefaults.Integration.PARCOOL));
    }
    @Test void unregisteredPresenceAndPresentWithoutProviderReject() {
        var registry = new CoreCompatibilityRegistry<String>();
        assertThrows(IllegalStateException.class, () -> registry.sample("context"));
        registry.registerPresence(id -> id.equals("touhou_little_maid"));
        assertThrows(IllegalStateException.class, () -> registry.sample("context"));
    }
    @Test void providerRunsOnlyInHostAndItsMapIsFrozen() {
        var registry = new CoreCompatibilityRegistry<String>();
        registry.registerPresence(id -> id.equals("bettercombat"));
        var live = new java.util.HashMap<String, MolangValue>();
        registry.registerProvider(CoreCompatibilityDefaults.Integration.BETTER_COMBAT, context -> {
            live.put("ctrl.bcombat_attack_animation", MolangValue.text(context)); return live;
        });
        var snapshot = registry.sample("actual attack");
        live.clear();
        assertEquals(MolangValue.text("actual attack"), snapshot.values().get("ctrl.bcombat_attack_animation"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.values().clear());
    }
}
