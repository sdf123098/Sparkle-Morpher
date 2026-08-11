package com.micaftic.morpher.core.model.catalog;

import com.micaftic.morpher.core.model.catalog.LocalModelCatalog.Diff;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog.Entry;
import com.micaftic.morpher.core.model.catalog.LocalModelCatalog.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R7.2 LocalModelCatalog 测试：本地模型 catalog 扫描 / 元数据继承 / diff 应用 / 名称嗅探。
 *
 * <p>纯 JVM 测试（零 MC import）：模型文件夹判定以 Predicate 注入，
 * 与 {@code YSMFolderDeserializer.isModelFolder} 语义一致（ysm.json 或 main.json+arm.json）。</p>
 */
class LocalModelCatalogTest {

    @TempDir
    Path tempDir;

    private static final Predicate<Path> FOLDER_DETECTOR = LocalModelCatalogTest::isModelFolder;

    /** 与 YSMFolderDeserializer.isModelFolder 相同的判定语义。 */
    private static boolean isModelFolder(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return false;
        }
        if (Files.isRegularFile(dir.resolve("ysm.json"))) {
            return true;
        }
        return Files.isRegularFile(dir.resolve("main.json")) && Files.isRegularFile(dir.resolve("arm.json"));
    }

    private static LocalModelCatalog catalog() {
        return new LocalModelCatalog(Long.MAX_VALUE / 2);
    }

    private static LocalModelCatalog catalog(long maxFileBytes) {
        return new LocalModelCatalog(maxFileBytes);
    }

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    // ===================== id 工具 =====================

    @Test
    void canonicalKey_trimsAndNormalizesSeparatorsAndCase() {
        assertEquals("cirno", LocalModelCatalog.canonicalKey(" cirno "));
        assertEquals("a/b", LocalModelCatalog.canonicalKey("a\\b"));
        assertEquals("a/b/c", LocalModelCatalog.canonicalKey("a//b///c"));
        assertEquals("abc/def", LocalModelCatalog.canonicalKey("ABC\\DEF"));
        assertNull(LocalModelCatalog.canonicalKey(null));
        assertNull(LocalModelCatalog.canonicalKey("  "));
    }

    @Test
    void stripImportExtension_removesKnownExtensionsCaseInsensitive() {
        assertEquals("cirno", LocalModelCatalog.stripImportExtension("cirno.ysm"));
        assertEquals("cirno", LocalModelCatalog.stripImportExtension("cirno.YSM"));
        assertEquals("cirno", LocalModelCatalog.stripImportExtension("cirno.zip"));
        assertEquals("cirno", LocalModelCatalog.stripImportExtension("cirno.bbmodel"));
        assertEquals("cirno.txt", LocalModelCatalog.stripImportExtension("cirno.txt"));
        assertEquals("noext", LocalModelCatalog.stripImportExtension("noext"));
    }

    @Test
    void normalizeLocalId_combinesKeyNormalizationAndExtensionStrip() {
        assertEquals("a/cirno", LocalModelCatalog.normalizeLocalId("A\\Cirno.YSM"));
        assertEquals("cirno", LocalModelCatalog.normalizeLocalId("cirno.ZIP"));
        assertEquals("a/b", LocalModelCatalog.normalizeLocalId("A\\B"));
    }

    // ===================== fingerprint =====================

    @Test
    void fingerprint_stableForUnchangedFile() throws IOException {
        Path file = tempDir.resolve("m.ysm");
        write(file, "data-v1");
        long first = LocalModelCatalog.fingerprint(file);
        long second = LocalModelCatalog.fingerprint(file);
        assertEquals(first, second, "未变化文件指纹应稳定");
    }

    @Test
    void fingerprint_changesWhenContentChanges() throws IOException {
        Path file = tempDir.resolve("m.ysm");
        write(file, "data-v1");
        long first = LocalModelCatalog.fingerprint(file);
        write(file, "data-v2");
        long second = LocalModelCatalog.fingerprint(file);
        assertNotEquals(first, second, "内容变化指纹应变化");
    }

    @Test
    void fingerprint_aggregatesDirectoryContents() throws IOException {
        Path dir = tempDir.resolve("pack");
        write(dir.resolve("a.txt"), "a1");
        long first = LocalModelCatalog.fingerprint(dir);
        write(dir.resolve("a.txt"), "a2");
        long second = LocalModelCatalog.fingerprint(dir);
        assertNotEquals(first, second, "目录内文件内容变化指纹应变化");
        write(dir.resolve("b.txt"), "b1");
        long third = LocalModelCatalog.fingerprint(dir);
        assertNotEquals(second, third, "新增文件指纹应变化");
    }

    // ===================== sniffName =====================

    @Test
    void sniffName_readsYsmJsonMetadataName() throws IOException {
        Path dir = tempDir.resolve("pack");
        write(dir.resolve("ysm.json"), "{\"metadata\":{\"name\":\"  Cirno Pack  \"}}");
        assertEquals("Cirno Pack", LocalModelCatalog.sniffName(dir));
    }

    @Test
    void sniffName_fallsBackToRootName() throws IOException {
        Path dir = tempDir.resolve("pack");
        write(dir.resolve("ysm.json"), "{\"name\":\"RootName\"}");
        assertEquals("RootName", LocalModelCatalog.sniffName(dir));
    }

    @Test
    void sniffName_readsBbmodelRootName() throws IOException {
        Path file = tempDir.resolve("model.bbmodel");
        write(file, "{\"name\":\"BBModelName\",\"meta\":{}}");
        assertEquals("BBModelName", LocalModelCatalog.sniffName(file));
    }

    @Test
    void sniffName_readsYsmJsonInsideZip() throws IOException {
        Path zip = tempDir.resolve("pack.zip");
        write(zip, "not-a-zip");
        // 非 zip 文件 → 无名字（静默失败路径）
        assertNull(LocalModelCatalog.sniffName(zip));
    }

    @Test
    void sniffName_returnsNullWhenNoName() throws IOException {
        Path dir = tempDir.resolve("pack");
        write(dir.resolve("ysm.json"), "{\"metadata\":{}}");
        assertNull(LocalModelCatalog.sniffName(dir));
        assertNull(LocalModelCatalog.sniffName(null));
    }

    // ===================== scan =====================

    @Test
    void scan_missingBaseDir_returnsEmpty() throws IOException {
        ScanResult result = catalog().scan(tempDir.resolve("nonexistent"), false, FOLDER_DETECTOR, Map.of());
        assertFalse(result.foundAny());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void scan_detectsFolderAndFileSources() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("cirno/ysm.json"), "{}");
        write(base.resolve("dai/main.json"), "{}");
        write(base.resolve("dai/arm.json"), "{}");
        write(base.resolve("standalone.ysm"), "data");
        write(base.resolve("zipmodel.zip"), "data");
        write(base.resolve("bb.bmodel"), "{}");

        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of());

        assertEquals(4, result.entries().size(), "cirno/dai/standalone/zipmodel 四个条目");
        assertTrue(result.foundAny());
        assertTrue(result.entries().containsKey("cirno"));
        assertTrue(result.entries().containsKey("dai"));
        assertTrue(result.entries().containsKey("standalone"));
        assertTrue(result.entries().containsKey("zipmodel"));
        assertFalse(result.entries().containsKey("bb"), ".bmodel 非识别扩展名");
    }

    @Test
    void scan_normalizesIdsAndStripsExtensions() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("My Pack/Cirno.YSM"), "data");
        write(base.resolve("Other/Folder.ZIP"), "data");

        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of());

        assertTrue(result.entries().containsKey("my pack/cirno"));
        assertTrue(result.entries().containsKey("other/folder"));
    }

    @Test
    void scan_skipsSubtreeOfModelFolder() throws IOException {
        Path base = tempDir.resolve("custom");
        // 模型文件夹内部的 .ysm 不应再被独立索引
        write(base.resolve("cirno/ysm.json"), "{}");
        write(base.resolve("cirno/inner.ysm"), "data");

        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of());

        assertTrue(result.entries().containsKey("cirno"));
        assertFalse(result.entries().keySet().stream().anyMatch(id -> id.contains("inner")),
                "模型文件夹子树内文件不应单独成条目");
    }

    @Test
    void scan_ignoresUnrelatedFiles() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("readme.txt"), "hi");
        write(base.resolve("model.ysm.tmp"), "data");
        write(base.resolve("pack/ysm.json"), "{}");

        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of());

        assertEquals(1, result.entries().size());
        assertTrue(result.entries().containsKey("pack"));
    }

    @Test
    void scan_skipsDefaultModelId() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("default.ysm"), "data");

        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of());

        assertTrue(result.entries().isEmpty(), "default 模型不应进入 catalog");
    }

    @Test
    void scan_skipsOversizedFiles() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("big.ysm"), "x".repeat(100));
        write(base.resolve("small.ysm"), "ok");

        ScanResult result = catalog(50).scan(base, false, FOLDER_DETECTOR, Map.of());

        assertTrue(result.entries().containsKey("small"));
        assertFalse(result.entries().containsKey("big"), "超限文件应跳过");
    }

    @Test
    void scan_tracksSourcesPerEntry() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("cirno/ysm.json"), "{}");
        write(base.resolve("standalone.ysm"), "data");

        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of());

        assertEquals(base.resolve("cirno").toAbsolutePath().normalize(), result.sources().get("cirno"));
        assertEquals(base.resolve("standalone.ysm").toAbsolutePath().normalize(), result.sources().get("standalone"));
    }

    @Test
    void scan_inheritsDisplayNameFromPreviousState() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("cirno/ysm.json"), "{}");

        Entry previous = new Entry(base.resolve("cirno"), null, false, false, 1L, null, "上一轮名字");
        ScanResult result = catalog().scan(base, false, FOLDER_DETECTOR, Map.of("cirno", previous));

        assertEquals("上一轮名字", result.entries().get("cirno").displayName, "catalog 应继承上一轮 displayName");
    }

    @Test
    void scan_firstEntryWinsOnDuplicateId() throws IOException {
        // 同一 baseDir 内不允许重复；但跨 baseDir 扫描共用一个 catalog 时先到先得（putIfAbsent）。
        Path a = tempDir.resolve("a");
        Path b = tempDir.resolve("b");
        write(a.resolve("dup.ysm"), "a-data");
        write(b.resolve("dup.ysm"), "b-data");

        Map<String, Entry> catalog = new LinkedHashMap<>();
        catalog().scan(a, false, FOLDER_DETECTOR, Map.of(), catalog);
        catalog().scan(b, false, FOLDER_DETECTOR, Map.of(), catalog);

        assertEquals(1, catalog.size(), "重复 id 只保留第一个");
        Path expected = a.resolve("dup.ysm").toAbsolutePath().normalize();
        assertEquals(expected, catalog.get("dup").path, "先扫描的 baseDir 条目胜出");
    }

    // ===================== diff =====================

    @Test
    void diff_emptyPrevious_reportsIncomingAsCatalog() {
        Map<String, Entry> previous = Map.of();
        Entry incoming = new Entry(tempDir.resolve("m.ysm"), null, false, false, 1L, null, "n");
        Diff diff = LocalModelCatalog.diff(previous, Map.of("m", incoming));

        assertTrue(diff.staleIds().isEmpty());
        assertEquals(1, diff.catalog().size());
        assertTrue(diff.catalog().containsKey("m"));
    }

    @Test
    void diff_removedEntry_isStale() {
        Entry prev = new Entry(tempDir.resolve("gone.ysm"), null, false, false, 1L, null, null);
        Diff diff = LocalModelCatalog.diff(Map.of("gone", prev), Map.of());

        assertEquals(List.of("gone"), diff.staleIds());
    }

    @Test
    void diff_changedSource_isStale() throws IOException {
        Path oldPath = tempDir.resolve("old.ysm");
        write(oldPath, "v1");
        Path newPath = tempDir.resolve("new.ysm");
        write(newPath, "v2");

        Entry prev = new Entry(oldPath, null, false, false, 1L, null, null);
        Entry incoming = new Entry(newPath, null, false, false, 2L, null, null);

        Diff diff = LocalModelCatalog.diff(Map.of("m", prev), Map.of("m", incoming));

        assertEquals(List.of("m"), diff.staleIds(), "同 id 但 source 变化 → stale");
        assertTrue(diff.catalog().containsKey("m"));
    }

    @Test
    void diff_unchangedSource_isRetainedWithoutStale() throws IOException {
        Path path = tempDir.resolve("m.ysm");
        write(path, "v1");

        Entry prev = new Entry(path, null, false, false, 5L, null, null);
        Entry incoming = new Entry(path, null, false, false, 5L, null, null);

        Diff diff = LocalModelCatalog.diff(Map.of("m", prev), Map.of("m", incoming));

        assertTrue(diff.staleIds().isEmpty(), "同 source 不 stale");
        assertEquals(1, diff.catalog().size());
    }

    @Test
    void diff_retainsDisplayNameAcrossKeep() throws IOException {
        Path path = tempDir.resolve("m.ysm");
        write(path, "v1");

        Entry prev = new Entry(path, null, false, false, 5L, null, "旧名字");
        Entry incoming = new Entry(path, null, false, false, 5L, null, null);

        Diff diff = LocalModelCatalog.diff(Map.of("m", prev), Map.of("m", incoming));

        assertEquals("旧名字", diff.catalog().get("m").displayName, "keep 时 displayName 应迁移到新条目");
        assertTrue(diff.staleIds().isEmpty());
    }

    @Test
    void diff_keepsIncomingDisplayNameWhenPresent() throws IOException {
        Path path = tempDir.resolve("m.ysm");
        write(path, "v1");

        Entry prev = new Entry(path, null, false, false, 5L, null, "旧名字");
        Entry incoming = new Entry(path, null, false, false, 5L, null, "新名字");

        Diff diff = LocalModelCatalog.diff(Map.of("m", prev), Map.of("m", incoming));

        assertEquals("新名字", diff.catalog().get("m").displayName, "新条目已有名字时不覆盖");
    }

    @Test
    void diff_skipsRemotePreviousEntries() throws IOException {
        Path path = tempDir.resolve("m.ysm");
        write(path, "v1");

        Entry remote = new Entry(path, null, true, false, 5L, null, null);
        Diff diff = LocalModelCatalog.diff(Map.of("m", remote), Map.of());

        assertTrue(diff.staleIds().isEmpty(), "remote 条目不参与本地 catalog diff");
    }

    // ===================== Entry.sameLocalSource =====================

    @Test
    void sameLocalSource_requiresMatchingAuthFingerprintAndPath() throws IOException {
        Path path = tempDir.resolve("m.ysm");
        write(path, "v1");

        Entry a = new Entry(path, null, false, false, 5L, null, null);
        Entry same = new Entry(path, null, false, false, 5L, null, null);
        Entry diffAuth = new Entry(path, null, false, true, 5L, null, null);
        Entry diffFingerprint = new Entry(path, null, false, false, 6L, null, null);
        Entry diffPath = new Entry(tempDir.resolve("n.ysm"), null, false, false, 5L, null, null);

        assertTrue(a.sameLocalSource(same));
        assertFalse(a.sameLocalSource(diffAuth), "auth 不同 → 不同 source");
        assertFalse(a.sameLocalSource(diffFingerprint), "fingerprint 不同 → 不同 source");
        assertFalse(a.sameLocalSource(diffPath), "path 不同 → 不同 source");
        assertFalse(a.sameLocalSource(null));
    }

    @Test
    void sameLocalSource_neverTrueForRemote() throws IOException {
        Path path = tempDir.resolve("m.ysm");
        write(path, "v1");

        Entry local = new Entry(path, null, false, false, 5L, null, null);
        Entry remote = new Entry(path, null, true, false, 5L, null, null);

        assertFalse(local.sameLocalSource(remote));
        assertFalse(remote.sameLocalSource(local));
    }
}
