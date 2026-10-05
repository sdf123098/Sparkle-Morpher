package com.micaftic.morpher.cloud.client;

import com.micaftic.morpher.cloud.CloudInstanceConfig;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CloudAuthCapabilitiesTest {
    private static final CloudInstanceConfig INSTANCE = CloudInstanceConfig.v1("community", URI.create("https://cloud.example"));
    private static String response(String fields) {
        return "{\"instance_id\":\"community\",\"origin\":\"https://cloud.example\",\"protocol\":\"spm.cloud.v1\"" + fields + "}";
    }

    @Test void legacyMetadataKeepsProviderProbeCompatible() {
        var info = CloudHttpClient.parseInstanceResponse(INSTANCE, response(""));
        assertNull(info.auth());
        assertFalse(info.playerMotionSupported());
    }

    @Test void explicitRegistrationPolicyDoesNotRemoveMotionOrGameIdentitySupport() {
        var info = CloudHttpClient.parseInstanceResponse(INSTANCE, response(
                ",\"capabilities\":[\"player_motion_v1\",\"game_identity_auth_v1\"],\"auth\":{\"password_login\":true,\"game_identity_login\":true,\"game_identity_link\":true,\"self_registration\":false}"));
        assertTrue(info.playerMotionSupported());
        assertTrue(info.auth().passwordLogin());
        assertTrue(info.auth().gameIdentityLogin());
        assertTrue(info.auth().gameIdentityLink());
        assertFalse(info.auth().selfRegistration());
    }

    @Test void missingAdditiveFlagsKeepOlderServersCompatible() {
        var info = CloudHttpClient.parseInstanceResponse(INSTANCE, response(",\"auth\":{\"self_registration\":false}"));
        assertTrue(info.auth().gameIdentityLogin());
        assertTrue(info.auth().gameIdentityLink());
        assertFalse(info.auth().selfRegistration());
    }

    @Test void malformedAuthCannotSilentlyEnableRegistration() {
        for (String value : new String[]{"null", "[]", "{\"self_registration\":\"false\"}", "{\"game_identity_login\":1}"})
            assertThrows(CloudHttpException.class, () -> CloudHttpClient.parseInstanceResponse(INSTANCE, response(",\"auth\":" + value)));
    }

    @Test void existingConstructorsRemainCompatible() {
        assertNull(new CloudInstanceInfo(INSTANCE, 1, 2, 3, 4, 5, 6, 7).auth());
        var info = new CloudInstanceInfo(INSTANCE, 1, 2, 3, 4, 5, 6, 7, true);
        assertNull(info.auth());
        assertTrue(info.playerMotionSupported());
    }
}
