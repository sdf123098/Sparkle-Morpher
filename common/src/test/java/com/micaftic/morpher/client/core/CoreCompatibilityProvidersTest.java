package com.micaftic.morpher.client.core;

import dev.spm.core.api.CancellationToken;
import dev.spm.core.scene.MolangContext;
import dev.spm.core.scene.MolangProgram;
import dev.spm.core.scene.ScriptBudget;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies Host compatibility providers cover all 12 integrations and HostMolangScript compiles spm-core MolangProgram. */
final class CoreCompatibilityProvidersTest {

    @Test
    void registryWithProvidersSamplesAllPresentIntegrationsCleanly() {
        var registry = new CoreCompatibilityRegistry<MinecraftHostSnapshotSampler.CompatibilityContext>();
        registry.registerPresence(modId -> true);
        CoreCompatibilityProviders.registerInto(registry);

        var snapshot = registry.sample(null);
        for (CoreCompatibilityDefaults.Integration integration : CoreCompatibilityDefaults.Integration.values()) {
            assertTrue(snapshot.present(integration), "Expected PRESENT: " + integration);
            for (String expectedKey : CoreCompatibilityDefaults.defaultValuesFor(integration).keySet()) {
                assertTrue(snapshot.values().containsKey(expectedKey), "Missing key: " + expectedKey);
            }
        }
        assertTrue(snapshot.values().containsKey("tlm.is_begging"));
        assertTrue(snapshot.values().containsKey("ctrl.tac_hold_gun"));
        assertTrue(snapshot.values().containsKey("ctrl.bcombat_attack_animation"));
    }

    @Test
    void hostMolangScriptCompilesSpmCoreProgramForBoundedExpressions() throws Exception {
        HostMolangScript script = HostMolangScript.compile("math.clamp(math.sin(30) * 10, 0, 8)");
        MolangProgram program = script.coreProgram();
        assertNotNull(program);
        double evaluated = program.evaluate(new MolangContext(
                Map.of(), Map.of(), Map.of(), new ScriptBudget(10_000, 16, new CancellationToken())));
        assertEquals(5.0d, evaluated, 1e-4d);
    }
}
