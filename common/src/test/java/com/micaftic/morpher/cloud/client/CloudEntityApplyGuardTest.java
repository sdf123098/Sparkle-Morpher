package com.micaftic.morpher.cloud.client;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CloudEntityApplyGuardTest {
    private CloudEntityPresenceClient.Entry entry(long revision) {
        return new CloudEntityPresenceClient.Entry(UUID.fromString("11111111-1111-4111-8111-111111111111"),
                CloudEntityProvider.Kind.FAKE_PLAYER, "target", 0, revision,
                new CloudPlayerSelection("official", "0".repeat(64), new CloudAssetRef("asset", revision, "a".repeat(64)), "ysm", "default"));
    }
    @Test void lateCompletionCannotRevertNewSelection() {
        var state = new CloudEntityApplyGuard(); var entity = new Object();
        var a = state.begin(entry(1), entity, 5);
        var b = state.begin(entry(2), entity, 5);
        assertFalse(state.accept(a, entry(2), entity, 5, false, false));
        assertTrue(state.accept(b, entry(2), entity, 5, false, false));
    }
    @Test void sameUuidRespawnRejectsOldEntityAndAppliesNewEntity() {
        var state = new CloudEntityApplyGuard(); var oldEntity = new Object(); var newEntity = new Object();
        var old = state.begin(entry(1), oldEntity, 5);
        assertFalse(state.accept(old, entry(1), null, 5, false, false));
        var respawned = state.begin(entry(1), newEntity, 5);
        assertFalse(state.accept(old, entry(1), newEntity, 5, false, false));
        assertTrue(state.accept(respawned, entry(1), newEntity, 5, false, false));
    }
    @Test void worldPrivacyAndVerifiedPlayerPreventFakeOverride() {
        var state = new CloudEntityApplyGuard(); var entity = new Object(); var token = state.begin(entry(1), entity, 5);
        assertFalse(state.accept(token, entry(1), entity, 6, false, false));
        assertFalse(state.accept(token, entry(1), entity, 5, true, false));
        assertFalse(state.accept(token, entry(1), entity, 5, false, true));
        state.clear();
        assertFalse(state.accept(token, entry(1), entity, 5, false, false));
    }
}
