package com.micaftic.morpher.cloud.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudIdentityBindingClientTest {
    @Test
    void parsesApprovalBinding() {
        var binding = CloudIdentityBindingClient.parseBindingForTest("{\"binding_id\":\"binding-1\",\"account_id\":\"alice\",\"identity_id\":\"identity-1\",\"target_id\":\"target-1\",\"scope_id\":\"scope-1\",\"world_epoch\":\"epoch-1\",\"entity_uuid\":\"12345678-1234-1234-1234-1234567890ab\",\"verification_method\":\"CLAIM_CODE\",\"status\":\"APPROVED\",\"approved_by\":\"admin\",\"revision\":1}");
        assertEquals("CLAIM_CODE", binding.verificationMethod());
        assertEquals("APPROVED", binding.status());
    }

    @Test
    void parsesOneTimeClaimCode() {
        var code = CloudIdentityBindingClient.parseClaimCodeForTest("{\"code\":\"spm_claim_1\",\"scope_id\":\"scope-1\",\"world_epoch\":\"epoch-1\",\"target_id\":\"target-1\",\"entity_uuid\":\"12345678-1234-1234-1234-1234567890ab\",\"expires_in_seconds\":600}");
        assertEquals("spm_claim_1", code.code());
        assertEquals(600, code.expiresInSeconds());
    }
}
