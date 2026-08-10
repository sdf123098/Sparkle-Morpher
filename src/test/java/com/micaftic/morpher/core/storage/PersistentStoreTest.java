package com.micaftic.morpher.core.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3.4 PersistentStore 测试：原子写 / 读取 / 损坏备份 / 注入路径（无 Platform 依赖）。
 */
class PersistentStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writeThenRead_roundTrips() throws Exception {
        Path file = tempDir.resolve("sub/store.json");
        PersistentStore store = new PersistentStore(file);
        store.write("hello-ysm");
        assertEquals("hello-ysm", store.read());
        assertTrue(Files.exists(file), "写入后文件应存在");
    }

    @Test
    void write_overwritesExisting() throws Exception {
        Path file = tempDir.resolve("store.json");
        PersistentStore store = new PersistentStore(file);
        store.write("v1");
        store.write("v2");
        assertEquals("v2", store.read());
    }

    @Test
    void write_isAtomic_NoTempLeftBehind() throws Exception {
        Path file = tempDir.resolve("store.json");
        PersistentStore store = new PersistentStore(file);
        store.write("data");
        // 原子移动后不应残留 .tmp
        try (var stream = Files.list(tempDir)) {
            assertFalse(stream.anyMatch(p -> p.getFileName().toString().endsWith(".tmp")),
                    "原子写不应残留临时文件");
        }
    }

    @Test
    void read_missingFile_returnsNull() throws Exception {
        PersistentStore store = new PersistentStore(tempDir.resolve("absent.json"));
        assertNull(store.read());
    }

    @Test
    void backupCorrupt_renamesFile() throws Exception {
        Path file = tempDir.resolve("store.json");
        Files.writeString(file, "partial-corrupt-content");
        PersistentStore store = new PersistentStore(file);
        Path backup = store.backupCorrupt();
        assertNotNull(backup, "应生成备份路径");
        assertTrue(Files.exists(backup), "备份文件应存在");
        assertFalse(Files.exists(file), "原文件应被移走");
        assertTrue(backup.getFileName().toString().startsWith("store.json.corrupt-"),
                "备份命名: " + backup.getFileName());
    }

    @Test
    void backupCorrupt_missingFile_returnsNull() throws Exception {
        PersistentStore store = new PersistentStore(tempDir.resolve("absent.json"));
        assertNull(store.backupCorrupt());
    }
}
