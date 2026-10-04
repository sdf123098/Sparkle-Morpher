package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CloudEntityPresenceClientTest {
    private final CloudEntityPresenceClient api = new CloudEntityPresenceClient(new CloudHttpClient(
            CloudInstanceConfig.v1("official", URI.create("https://cloud.example.test"))));
    private final UUID maid = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final UUID fake = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private String row(UUID id, String kind, String texture) {
        return "{\"entity_uuid\":\"" + id + "\",\"entity_kind\":\"" + kind + "\",\"target_id\":\"target_" + id
                + "\",\"binding_revision\":1,\"revision\":3,\"selection\":{\"asset_id\":\"same_asset\","
                + "\"asset_revision\":2,\"raw_sha256\":\"" + "a".repeat(64) + "\",\"format\":\"ysm\",\"texture_id\":\"" + texture + "\"}}";
    }

    @Test void preservesKindsAndDistinctTexturesForEntitiesSharingOneAsset() {
        var rows = api.parse("{\"entries\":[" + row(maid, "MAID", "贴图一") + "," + row(fake, "FAKE_PLAYER", "贴图二") + "]}");
        assertEquals(CloudEntityProvider.Kind.MAID, rows.get(maid).kind());
        assertEquals(CloudEntityProvider.Kind.FAKE_PLAYER, rows.get(fake).kind());
        assertEquals(rows.get(maid).selection().runtimeModelId(), rows.get(fake).selection().runtimeModelId());
        assertNotEquals(rows.get(maid).selection().textureId(), rows.get(fake).selection().textureId());
        assertEquals(2, rows.get(maid).selection().ref().revision());
    }

    @Test void keepsExplicitClearAndDenialAsBoundEntity() {
        var rows = api.parse("{\"entries\":[" + row(fake, "FAKE_PLAYER", "default")
                .replaceFirst("\\\"selection\\\":\\{.*", "\"selection\":null}") + "]}");
        assertTrue(rows.containsKey(fake));
        assertNull(rows.get(fake).selection());
        assertEquals(CloudEntityProvider.Kind.FAKE_PLAYER, rows.get(fake).kind());
    }

    @Test void rejectsPlayersAndDuplicateEntityBindings() {
        assertThrows(IllegalArgumentException.class, () -> api.parse("{\"entries\":[" + row(maid, "PLAYER", "default") + "]}"));
        assertThrows(IllegalArgumentException.class, () -> api.parse("{\"entries\":[" + row(maid, "MAID", "default") + "," + row(maid, "MAID", "default") + "]}"));
    }

    @Test void rejectsForgedHashNegativeRevisionAndOversizedQueries() {
        assertThrows(IllegalArgumentException.class, () -> api.parse("{\"entries\":[" + row(maid, "MAID", "default").replace("a".repeat(64), "bad") + "]}"));
        assertThrows(IllegalArgumentException.class, () -> api.parse("{\"entries\":[" + row(maid, "MAID", "default").replace("\"revision\":3", "\"revision\":-1") + "]}"));
        assertThrows(IllegalArgumentException.class, () -> api.query("a".repeat(64), java.util.Collections.nCopies(65, maid)));
    }

    @Test void isolatesWorldsAndNormalizesDefaultMinecraftPort() {
        assertEquals(CloudEntityWorldKey.remote("Example.COM"), CloudEntityWorldKey.remote("example.com:25565"));
        assertNotEquals(CloudEntityWorldKey.remote("example.com:25566"), CloudEntityWorldKey.remote("example.com"));
        assertEquals(64, CloudEntityWorldKey.remote("example.com").length());
        assertNotEquals(CloudEntityWorldKey.local("/saves/one"), CloudEntityWorldKey.local("/saves/two"));
        assertNotEquals(CloudEntityWorldKey.local("example.com"), CloudEntityWorldKey.remote("example.com"));
        assertThrows(IllegalArgumentException.class, () -> CloudEntityWorldKey.remote(""));
    }
}
