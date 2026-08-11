package com.micaftic.morpher.core.model.catalog;

import com.micaftic.morpher.core.model.catalog.LocalModelScanner.Hit;
import com.micaftic.morpher.core.model.catalog.LocalModelScanner.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R8 LocalModelScanner 测试：本地模型来源遍历发现（文件夹/文件）+ id 归一 + kind 判定。
 *
 * <p>从 ServerModelManager.scanDirectoryModels 的遍历骨架抽取；解析/缓存留在调用方。
 * 模型文件夹判定以 Predicate 注入（YSMFolderDeserializer.isModelFolder 语义）。</p>
 */
class LocalModelScannerTest {

    @TempDir
    Path tempDir;

    private static final Predicate<Path> FOLDER_DETECTOR = dir -> dir != null && Files.isDirectory(dir)
            && (Files.isRegularFile(dir.resolve("ysm.json"))
            || (Files.isRegularFile(dir.resolve("main.json")) && Files.isRegularFile(dir.resolve("arm.json"))));

    private static void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static List<Hit> scanCollect(Path baseDir) throws IOException {
        List<Hit> hits = new ArrayList<>();
        LocalModelScanner.scan(baseDir, FOLDER_DETECTOR, hits::add);
        return hits;
    }

    // ===================== kind 判定 =====================

    @Test
    void kindFromFileName_recognizesKnownKinds() {
        assertEquals(Kind.YSM, LocalModelScanner.kindFromFileName("model.ysm"));
        assertEquals(Kind.YSM, LocalModelScanner.kindFromFileName("model.YSM"));
        assertEquals(Kind.ZIP, LocalModelScanner.kindFromFileName("model.zip"));
        assertEquals(Kind.BBMODEL, LocalModelScanner.kindFromFileName("model.bbmodel"));
        assertEquals(Kind.UNKNOWN, LocalModelScanner.kindFromFileName("model.txt"));
        assertEquals(Kind.UNKNOWN, LocalModelScanner.kindFromFileName(null));
        assertEquals(Kind.UNKNOWN, LocalModelScanner.kindFromFileName("noext"));
    }

    @Test
    void extensionFor_roundTripsKnownKinds() {
        assertEquals(".ysm", LocalModelScanner.extensionFor(Kind.YSM));
        assertEquals(".zip", LocalModelScanner.extensionFor(Kind.ZIP));
        assertEquals(".bbmodel", LocalModelScanner.extensionFor(Kind.BBMODEL));
        assertEquals("", LocalModelScanner.extensionFor(Kind.UNKNOWN));
        assertEquals("", LocalModelScanner.extensionFor(Kind.FOLDER));
    }

    @Test
    void stripImportExtension_removesKnownExtensionsCaseInsensitive() {
        assertEquals("cirno", LocalModelScanner.stripImportExtension("cirno.ysm"));
        assertEquals("cirno", LocalModelScanner.stripImportExtension("cirno.ZIP"));
        assertEquals("cirno", LocalModelScanner.stripImportExtension("cirno.bbmodel"));
        assertEquals("cirno.txt", LocalModelScanner.stripImportExtension("cirno.txt"));
        assertEquals("noext", LocalModelScanner.stripImportExtension("noext"));
    }

    // ===================== id 归一 =====================

    @Test
    void normalizeModelId_stripsExtensionAndNormalizes() {
        assertEquals("my_pack/cirno", LocalModelScanner.normalizeModelId("My Pack/Cirno.YSM"));
        assertEquals("a/b", LocalModelScanner.normalizeModelId("a\\b.zip"));
    }

    @Test
    void normalizeModelId_rejectsInvalidOrSymbolOnlyIds() {
        assertNull(LocalModelScanner.normalizeModelId(null));
        assertNull(LocalModelScanner.normalizeModelId("___"));
    }

    // ===================== scan =====================

    @Test
    void scan_missingBaseDir_noHits() throws IOException {
        assertTrue(scanCollect(tempDir.resolve("nonexistent")).isEmpty());
    }

    @Test
    void scan_detectsFoldersAndFilesWithKinds() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("cirno/ysm.json"), "{}");
        write(base.resolve("dai/main.json"), "{}");
        write(base.resolve("dai/arm.json"), "{}");
        write(base.resolve("standalone.ysm"), "data");
        write(base.resolve("pack.zip"), "data");
        write(base.resolve("bb.bbmodel"), "{}");
        write(base.resolve("readme.txt"), "hi");

        List<Hit> hits = scanCollect(base);
        assertEquals(5, hits.size(), "cirno/dai/standalone/pack/bb 五个命中");

        Set<String> ids = hits.stream().map(Hit::modelId).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of("cirno", "dai", "standalone", "pack", "bb")));

        Hit folderHit = hits.stream().filter(h -> h.modelId().equals("cirno")).findFirst().orElseThrow();
        assertEquals(Kind.FOLDER, folderHit.kind());
        Hit ysmHit = hits.stream().filter(h -> h.modelId().equals("standalone")).findFirst().orElseThrow();
        assertEquals(Kind.YSM, ysmHit.kind());
        assertEquals(base.resolve("standalone.ysm").toAbsolutePath().normalize(), ysmHit.source());
    }

    @Test
    void scan_skipsSubtreeOfModelFolder() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("cirno/ysm.json"), "{}");
        write(base.resolve("cirno/inner.ysm"), "data");

        List<Hit> hits = scanCollect(base);
        assertEquals(1, hits.size(), "模型文件夹子树内文件不应单独命中");
        assertEquals("cirno", hits.get(0).modelId());
    }

    @Test
    void scan_skipsInvalidModelIds() throws IOException {
        Path base = tempDir.resolve("custom");
        // 纯符号目录（Windows 上 "..." 不是合法目录名，用 "_" 替代）→ id 无效应跳过
        write(base.resolve("___/ysm.json"), "{}");
        write(base.resolve("good.ysm"), "data");

        List<Hit> hits = scanCollect(base);
        assertEquals(1, hits.size());
        assertEquals("good", hits.get(0).modelId());
    }

    @Test
    void scan_preservesHitOrderDepthFirst() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("a.ysm"), "1");
        write(base.resolve("sub/b.ysm"), "2");

        List<Hit> hits = scanCollect(base);
        assertEquals(List.of("a", "sub/b"), hits.stream().map(Hit::modelId).toList());
    }

    @Test
    void scan_sinkException_propagates() throws IOException {
        Path base = tempDir.resolve("custom");
        write(base.resolve("a.ysm"), "1");
        boolean[] thrown = {false};
        try {
            LocalModelScanner.scan(base, FOLDER_DETECTOR, hit -> {
                throw new IOException("boom");
            });
        } catch (IOException e) {
            thrown[0] = true;
        }
        assertTrue(thrown[0], "回调抛出的 IOException 应传播（由调用方决定单条目失败处理）");
    }
}
