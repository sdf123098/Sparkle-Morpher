package com.micaftic.morpher.core.model;

import com.micaftic.morpher.core.model.catalog.LocalModelScanner.Kind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R8-5 ModelUploadSession 测试：模型上传会话状态机（从 ServerModelManager.ModelUploadState 抽取）。
 *
 * <p>覆盖 chunk 顺序推进 / 乱序与越界拒绝（标记失败）/ 完成判定 / 过期清理。</p>
 */
class ModelUploadSessionTest {

    private static ModelUploadSession session(int totalBytes) {
        return new ModelUploadSession(1L, UUID.randomUUID(), "cirno", "cirno.ysm", Kind.YSM, totalBytes, "sha");
    }

    @Test
    void appendChunk_inOrder_advancesReceivedBytes() {
        ModelUploadSession session = session(8);
        assertTrue(session.appendChunk(0, "abcd".getBytes(StandardCharsets.UTF_8)));
        assertTrue(session.appendChunk(4, "efgh".getBytes(StandardCharsets.UTF_8)));
        assertEquals(8, session.receivedBytes());
        assertArrayEquals("abcdefgh".getBytes(StandardCharsets.UTF_8), session.data());
        assertTrue(session.isComplete());
        assertFalse(session.isFailed());
    }

    @Test
    void appendChunk_outOfOrder_marksFailed() {
        ModelUploadSession session = session(8);
        assertFalse(session.appendChunk(4, "abcd".getBytes(StandardCharsets.UTF_8)), "offset 4 != receivedBytes(0) 应拒绝");
        assertTrue(session.isFailed());
        assertFalse(session.isComplete());
        // 原行为：failed 后合法 chunk 仍会写入缓冲（finish 时按 failed 拒绝）
        assertTrue(session.appendChunk(0, "abcd".getBytes(StandardCharsets.UTF_8)));
        assertFalse(session.isComplete(), "failed 标记下 isComplete 恒 false");
    }

    @Test
    void appendChunk_negativeOrOverflowOffset_rejected() {
        ModelUploadSession session = session(8);
        assertFalse(session.appendChunk(-1, new byte[1]));
        assertFalse(session.appendChunk(0, new byte[9]), "chunk 越界（offset+len > total）应拒绝");
        assertTrue(session.isFailed());
    }

    @Test
    void appendChunk_nullChunk_rejected() {
        ModelUploadSession session = session(8);
        assertFalse(session.appendChunk(0, null));
        assertTrue(session.isFailed());
    }

    @Test
    void partialUpload_notComplete() {
        ModelUploadSession session = session(8);
        session.appendChunk(0, new byte[3]);
        assertFalse(session.isComplete(), "只传部分不应视为完成");
        assertFalse(session.isFailed());
    }

    @Test
    void isExpired_afterTimeout() throws InterruptedException {
        ModelUploadSession session = session(8);
        long now = System.currentTimeMillis();
        assertFalse(session.isExpired(now, 1000), "刚 touch 不应过期");
        assertTrue(session.isExpired(now + 5000, 1000), "超过超时窗口应过期");
        session.touch();
        assertFalse(session.isExpired(System.currentTimeMillis(), 1000), "touch 后应刷新过期时间");
    }

    @Test
    void getters_exposeUploadMetadata() {
        UUID owner = UUID.randomUUID();
        ModelUploadSession session = new ModelUploadSession(99L, owner, "cirno", "cirno.ysm", Kind.ZIP, 10, "sha256");
        assertEquals(99L, session.uploadId());
        assertSame(owner, session.owner());
        assertEquals("cirno", session.modelId());
        assertEquals("cirno.ysm", session.fileName());
        assertSame(Kind.ZIP, session.importKind());
        assertEquals("sha256", session.sha256());
        assertEquals(10, session.totalBytes());
        assertEquals(0, session.receivedBytes());
    }
}
