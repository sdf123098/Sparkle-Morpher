package com.micaftic.morpher.model.validation;

import org.junit.jupiter.api.Test;

import static com.micaftic.morpher.model.validation.UploadPolicy.RejectReason;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R8 遗留② UploadPolicy 测试：上传请求校验链
 * （从 ServerModelManager.beginModelUpload 的 6 个拒绝分支抽出）。
 */
class UploadPolicyTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private RejectReason validate(boolean uploadAllowed, boolean playerConnected,
                                  String modelId, boolean kindKnown, String sha256,
                                  int totalBytes, int maxBytes, boolean modelExists) {
        return UploadPolicy.validate(uploadAllowed, playerConnected, modelId, kindKnown,
                sha256, totalBytes, maxBytes, modelExists);
    }

    @Test
    void passesWhenAllConditionsMet() {
        assertEquals(RejectReason.NONE, validate(true, true, "my_model", true, SHA, 1024, 4096, false));
    }

    @Test
    void rejectsWhenUploadDisabled() {
        assertEquals(RejectReason.DISABLED, validate(false, true, "m", true, SHA, 1, 4096, false));
    }

    @Test
    void rejectsWhenSenderNotConnected() {
        assertEquals(RejectReason.NO_PERMISSION, validate(true, false, "m", true, SHA, 1, 4096, false));
    }

    @Test
    void rejectsInvalidModelId() {
        assertEquals(RejectReason.INVALID_INPUT, validate(true, true, null, true, SHA, 1, 4096, false));
    }

    @Test
    void rejectsUnknownImportKind() {
        assertEquals(RejectReason.INVALID_INPUT, validate(true, true, "m", false, SHA, 1, 4096, false));
    }

    @Test
    void rejectsMalformedSha256() {
        assertEquals(RejectReason.INVALID_INPUT, validate(true, true, "m", true, "not-a-hash", 1, 4096, false));
        assertEquals(RejectReason.INVALID_INPUT, validate(true, true, "m", true, null, 1, 4096, false));
        assertEquals(RejectReason.INVALID_INPUT, validate(true, true, "m", true, "ABCDEF", 1, 4096, false));
    }

    @Test
    void rejectsZeroOrOversizedFile() {
        assertEquals(RejectReason.EXCEEDS_LIMIT, validate(true, true, "m", true, SHA, 0, 4096, false));
        assertEquals(RejectReason.EXCEEDS_LIMIT, validate(true, true, "m", true, SHA, -1, 4096, false));
        assertEquals(RejectReason.EXCEEDS_LIMIT, validate(true, true, "m", true, SHA, 4097, 4096, false));
    }

    @Test
    void rejectsExistingModelId() {
        assertEquals(RejectReason.ALREADY_EXISTS, validate(true, true, "m", true, SHA, 1, 4096, true));
    }

    @Test
    void disabledTakesPriorityOverOtherReasons() {
        assertEquals(RejectReason.DISABLED, validate(false, false, null, false, null, 0, 0, true));
    }

    @Test
    void statusCodeAndMessageMapping() {
        assertEquals((byte) 0, UploadPolicy.statusCode(RejectReason.NONE));
        assertEquals((byte) 6, UploadPolicy.statusCode(RejectReason.DISABLED));
        assertEquals((byte) 3, UploadPolicy.statusCode(RejectReason.NO_PERMISSION));
        assertEquals((byte) 5, UploadPolicy.statusCode(RejectReason.INVALID_INPUT));
        assertEquals((byte) 2, UploadPolicy.statusCode(RejectReason.EXCEEDS_LIMIT));
        assertEquals((byte) 1, UploadPolicy.statusCode(RejectReason.ALREADY_EXISTS));
        assertEquals("Model import disabled", UploadPolicy.statusMessage(RejectReason.DISABLED));
        assertEquals("No import permission", UploadPolicy.statusMessage(RejectReason.NO_PERMISSION));
        assertEquals("Invalid model id or hash", UploadPolicy.statusMessage(RejectReason.INVALID_INPUT));
        assertEquals("File exceeds server limit", UploadPolicy.statusMessage(RejectReason.EXCEEDS_LIMIT));
        assertEquals("Model ID already exists", UploadPolicy.statusMessage(RejectReason.ALREADY_EXISTS));
    }
}
