package com.micaftic.morpher.resource;

import com.micaftic.morpher.resource.pojo.RawYsmModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R0.2c：真实模型本机验证（license 合规：模型资产不进入仓库）。
 *
 * 通过环境变量启用（默认跳过，CI/他人 clone 不触发）：
 *   SM_CORPUS_DIR=<模型库根目录>，可选 SM_CORPUS_LIMIT=<每类上限，默认 30>
 *
 * 覆盖：.ysm（zip 容器，走 YSMFolderDeserializer zipfs 路径）、.zip、含 ysm.json 的 folder。
 * .bbmodel 非 YSM 格式（Blockbench 工程，导入器依赖 MC）→ 记录跳过。
 * 结果：统计各类成功数 + 打印失败清单；至少成功解析一个才通过。
 */
@EnabledIfEnvironmentVariable(named = "SM_CORPUS_DIR", matches = ".+")
class LocalModelCorpusVerificationTest {

    @Test
    void verifyRealModelsFromLibrary() throws Exception {
        String dir = System.getenv("SM_CORPUS_DIR");
        if (dir.startsWith("/") && dir.length() >= 3 && dir.charAt(2) == '/') {
            // MSYS 风格 /d/xxx → D:\xxx
            dir = dir.substring(1, 2) + ":" + dir.substring(2).replace('/', '\\');
        }
        Path root = Path.of(dir);
        int limit = Integer.parseInt(System.getenv().getOrDefault("SM_CORPUS_LIMIT", "30"));
        assertTrue(Files.isDirectory(root), "SM_CORPUS_DIR 必须是目录: " + root);

        int ysm = 0, folder = 0, zip = 0, skipped = 0;
        List<String> failures = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : stream.filter(Files::isRegularFile).limit(10_000).toList()) {
                String name = p.getFileName().toString().toLowerCase();
                try {
                    if (name.endsWith(".bbmodel")) {
                        skipped++;
                    } else if (name.endsWith(".ysm") && ysm < limit) {
                        // .ysm = YSGP 容器；生产路径（ClientModelManager.parseYsmImport）：
                        // v1/v2 → YesModelUtils.input → 内存 map 解析；v3+ → YsmCrypt.decryptYsmFile → 二进制解析
                        byte[] data = Files.readAllBytes(p);
                        int v = com.micaftic.morpher.core.legacy.YesModelUtils.getYsmCryptoVersion(data);
                        if (v == 1 || v == 2) {
                            try (YSMFolderDeserializer d = new YSMFolderDeserializer(com.micaftic.morpher.core.legacy.YesModelUtils.input(data))) {
                                RawYsmModel m = d.deserialize();
                                assertNotNull(m.mainEntity.mainModel, "mainModel null: " + p);
                            }
                        } else {
                            byte[] decrypted = com.micaftic.morpher.core.security.YsmCrypt.decryptYsmFile(data);
                            try (YSMBinaryDeserializer d = new YSMBinaryDeserializer(decrypted)) {
                                RawYsmModel m = d.deserializeKeepOpen();
                                assertNotNull(m.mainEntity.mainModel, "mainModel null: " + p);
                            }
                        }
                        ysm++;
                    } else if (name.endsWith(".zip") && zip < limit) {
                        try (YSMFolderDeserializer d = new YSMFolderDeserializer(p)) {
                            RawYsmModel m = d.deserialize();
                            assertNotNull(m.mainEntity.mainModel, "mainModel null: " + p);
                        }
                        zip++;
                    } else if (name.equals("ysm.json") && folder < limit) {
                        try (YSMFolderDeserializer d = new YSMFolderDeserializer(p.getParent())) {
                            RawYsmModel m = d.deserialize();
                            assertNotNull(m.mainEntity.mainModel, "mainModel null: " + p);
                        }
                        folder++;
                    }
                } catch (Throwable t) {
                    StackTraceElement top = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
                    failures.add(p + " -> " + t + (top != null ? " @ " + top : ""));
                }
                if (ysm >= limit && zip >= limit && folder >= limit) break;
            }
        }

        System.out.println("[corpus] ysm=" + ysm + " folder=" + folder + " zip=" + zip
                + " skipped(bbmodel)=" + skipped + " failed=" + failures.size());
        failures.forEach(f -> System.out.println("[corpus][FAIL] " + f));

        assertTrue(ysm + folder + zip > 0, "至少应成功解析一个真实模型（目录: " + root + "）");
        if (!failures.isEmpty()) {
            System.out.println("[corpus] 警告：存在 " + failures.size() + " 个解析失败样本（不阻断，供兼容性排查）");
        }
    }
}
