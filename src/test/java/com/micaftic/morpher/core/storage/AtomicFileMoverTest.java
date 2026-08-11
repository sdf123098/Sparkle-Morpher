package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R8-2 AtomicFileMover 测试：原子替换 + 重试退避（从 ServerModelManager.moveWithRetry 抽取）。
 *
 * <p>Windows 上目标文件被并发打开（读取/发送）时 REPLACE 会瞬时 AccessDenied，
 * 重试几次后仍失败则抛异常——目标文件始终是完整内容（旧或新）。</p>
 */
class AtomicFileMoverTest {

    @TempDir
    Path tempDir;

    private Path writeSource(String name, byte[] data) throws IOException {
        Path src = tempDir.resolve(name);
        Files.write(src, data);
        return src;
    }

    @Test
    void moveWithRetry_movesFileAtomically() throws Exception {
        byte[] data = "cache-bytes".getBytes(StandardCharsets.UTF_8);
        Path src = writeSource("src.tmp", data);
        Path target = tempDir.resolve("target.cache");

        AtomicFileMover.moveWithRetry(src, target);

        assertArrayEquals(data, Files.readAllBytes(target), "目标内容应与源一致");
        assertFalse(Files.exists(src), "源临时文件应被移走");
    }

    @Test
    void moveWithRetry_replacesExistingTarget() throws Exception {
        Path src = writeSource("src.tmp", "new".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("target.cache");
        Files.writeString(target, "old");

        AtomicFileMover.moveWithRetry(src, target);

        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(target));
    }

    @Test
    void moveWithRetry_throwsAfterExhaustingRetries() throws Exception {
        Path src = writeSource("src.tmp", "data".getBytes(StandardCharsets.UTF_8));
        // 目标已存在且为非空目录 → move 必然失败（FileAlreadyExistsException），重试耗尽后抛
        Path blocked = tempDir.resolve("blocked");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("occupied"), "x");

        assertThrows(IOException.class, () -> AtomicFileMover.moveWithRetry(src, blocked, 2, attempt -> 0L),
                "重试耗尽后应抛 IOException");
        assertTrue(Files.exists(src), "失败后源文件应保留（调用方清理）");
    }

    @Test
    void moveWithRetry_singleAttemptFailsFast() throws Exception {
        Path src = writeSource("src.tmp", "data".getBytes(StandardCharsets.UTF_8));
        Path blocked = tempDir.resolve("blocked2");
        Files.createDirectories(blocked);
        Files.writeString(blocked.resolve("occupied"), "x");

        assertThrows(IOException.class, () -> AtomicFileMover.moveWithRetry(src, blocked, 1, attempt -> 0L));
    }

    @Test
    void moveWithRetry_backoffNotInvokedOnSuccess() throws Exception {
        Path src = writeSource("src.tmp", "data".getBytes(StandardCharsets.UTF_8));
        Path target = tempDir.resolve("ok.cache");
        int[] backoffCalls = {0};

        AtomicFileMover.moveWithRetry(src, target, 6, attempt -> {
            backoffCalls[0]++;
            return 0L;
        });

        assertTrue(Files.exists(target));
        assertTrue(backoffCalls[0] == 0, "成功路径不应触发退避");
    }
}
