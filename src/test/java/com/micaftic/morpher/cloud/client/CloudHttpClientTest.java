package com.micaftic.morpher.cloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.micaftic.morpher.cloud.CloudInstanceConfig;

import java.net.URI;

import org.junit.jupiter.api.Test;

class CloudHttpClientTest {
    private static final CloudInstanceConfig INSTANCE = CloudInstanceConfig.v1("local-dev", URI.create("https://cloud.example.test"));

    @Test
    void acceptsOnlyMatchingTrustedInstanceAndLimits() {
        CloudInstanceInfo info = CloudHttpClient.parseInstanceResponse(INSTANCE, """
                {"instance_id":"local-dev","origin":"https://cloud.example.test","protocol":"spm.cloud.v1",
                 "limits":{"max_message_bytes":65536,"max_asset_bytes":1024}}
                """);
        assertEquals(INSTANCE, info.config());
        assertEquals(65_536, info.maxMessageBytes());
        assertEquals(1024, info.maxAssetBytes());
        assertEquals(45, info.heartbeatTtlSeconds());
    }

    @Test
    void rejectsOriginOrProtocolSubstitution() {
        CloudHttpException originFailure = assertThrows(CloudHttpException.class, () ->
                CloudHttpClient.parseInstanceResponse(INSTANCE,
                        "{\"instance_id\":\"local-dev\",\"origin\":\"https://evil.example\",\"protocol\":\"spm.cloud.v1\"}"));
        assertEquals(com.micaftic.morpher.core.api.network.state.CloudErrorCode.INSTANCE_MISMATCH, originFailure.errorCode());

        CloudHttpException protocolFailure = assertThrows(CloudHttpException.class, () ->
                CloudHttpClient.parseInstanceResponse(INSTANCE,
                        "{\"instance_id\":\"local-dev\",\"origin\":\"https://cloud.example.test\",\"protocol\":\"spm.cloud.v2\"}"));
        assertEquals(com.micaftic.morpher.core.api.network.state.CloudErrorCode.PROTOCOL_UNSUPPORTED, protocolFailure.errorCode());
    }
}
