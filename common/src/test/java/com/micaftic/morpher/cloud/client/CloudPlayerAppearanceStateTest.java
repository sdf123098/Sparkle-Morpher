package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CloudPlayerAppearanceStateTest {
    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID();
    private static CloudPlayerSelection model(String id, String texture) {
        return new CloudPlayerSelection("official", "1".repeat(64), new CloudAssetRef(id, 2, "a".repeat(64)), "ysm", texture);
    }

    @Test void differentPlayersNeverShareSelections() {
        var state = new CloudPlayerAppearanceState();
        state.publish(A, model("cyrene", "texture")); state.publish(B, model("fuxuan", "skin"));
        assertEquals("cyrene", state.get(A).selection().ref().assetId());
        assertEquals("fuxuan", state.get(B).selection().ref().assetId());
        assertEquals("skin", state.get(B).selection().textureId());
    }
    @Test void unchangedSelectionDoesNotFloodPeers() {
        var state = new CloudPlayerAppearanceState(); var first = state.publish(A, model("cyrene", "skin"));
        assertSame(first, state.publish(A, model("cyrene", "skin")));
        assertTrue(state.publish(A, model("cyrene", "other")).revision() > first.revision());
    }
    @Test void olderSelectionCannotRollBackNewerPlayer() {
        var state = new CloudPlayerAppearanceState();
        assertTrue(state.receive(B, 3, model("fuxuan", "skin")));
        assertFalse(state.receive(B, 2, model("cyrene", "skin")));
        assertFalse(state.receive(B, 3, null));
        assertEquals("fuxuan", state.get(B).selection().ref().assetId());
    }
    @Test void switchingModelInvalidatesInFlightImport() {
        var state = new CloudPlayerAppearanceState(); var old = state.publish(A, model("cyrene", "skin"));
        state.publish(A, model("fuxuan", "skin")); assertFalse(state.isCurrent(old));
    }
    @Test void switchingTextureInvalidatesInFlightImport() {
        var state = new CloudPlayerAppearanceState(); var old = state.publish(A, model("fuxuan", "skin"));
        state.publish(A, model("fuxuan", "other")); assertFalse(state.isCurrent(old));
    }
    @Test void clearingSelectionRestoresLegacyOwnership() {
        var state = new CloudPlayerAppearanceState(); var old = state.publish(A, model("fuxuan", "skin"));
        assertTrue(state.ownsAppearance(A)); state.publish(A, null);
        assertFalse(state.ownsAppearance(A)); assertFalse(state.isCurrent(old));
        assertNull(state.get(A).selection());
    }
    @Test void reconnectCannotReuseOldImportToken() {
        var state = new CloudPlayerAppearanceState(); state.receive(A, 1, model("fuxuan", "skin")); var old = state.get(A);
        state.clear(); state.receive(A, 1, model("fuxuan", "skin")); assertFalse(state.isCurrent(old));
    }
    @Test void disconnectRemovesOnlyDepartingPlayer() {
        var state = new CloudPlayerAppearanceState(); var a = state.publish(A, model("cyrene", "skin"));
        var b = state.publish(B, model("fuxuan", "skin")); state.remove(A);
        assertFalse(state.isCurrent(a)); assertTrue(state.isCurrent(b));
    }
    @Test void zeroRevisionIsRejected() {
        var state = new CloudPlayerAppearanceState(); assertFalse(state.receive(A, 0, model("cyrene", "skin")));
        assertNull(state.get(A));
    }
    @Test void peerCannotSupplyDownloadEndpointOrParserPath() {
        var ref = new CloudAssetRef("符玄", 2, "a".repeat(64));
        assertThrows(IllegalArgumentException.class, () -> new CloudPlayerSelection("https://bad", "1".repeat(64), ref, "ysm", "skin"));
        assertThrows(IllegalArgumentException.class, () -> new CloudPlayerSelection("official", "1".repeat(64), ref, "../evil", "skin"));
        assertThrows(IllegalArgumentException.class, () -> new CloudPlayerSelection("official", "wrong", ref, "ysm", "skin"));
        assertEquals("player-model.ysm", new CloudPlayerSelection("official", "1".repeat(64), ref, "ysm", "skin").importFileName());
    }
    @Test void selectionUsesSameExactIdentityAsCloudBrowser() {
        var model = model("fuxuan", "skin");
        assertEquals(new com.micaftic.morpher.core.model.CloudAssetIdentity("official", "catalog", "fuxuan", "2", "a".repeat(64)).runtimeModelId(), model.runtimeModelId());
        assertNotEquals(model.runtimeModelId(), model("cyrene", "skin").runtimeModelId());
    }
}
