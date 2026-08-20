package com.micaftic.morpher.core.native;

import com.micaftic.morpher.core.native.NativeArtifactVerifier.NativeArtifact;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.2.2 §11 Native 信任链：manifest 解析与 digest 校验（纯 Java）。
 *
 * <p>使用打包的真实 native-manifest.json（main resources 在测试 classpath 上），
 * 验证六平台条目完整、sha256 校验正反例、平台查找与规范化。
 */
class NativeArtifactVerifierTest {

    @Test
    void packagedManifestListsAllSixPlatforms() throws Exception {
        List<NativeArtifact> artifacts = loadPackagedManifest();
        assertEquals(6, artifacts.size(), "native-manifest.json 应包含六平台条目");
        for (String platform : new String[]{"windows-x64", "windows-x86", "linux-x64", "macos-x64", "macos-arm64", "android-arm64"}) {
            Optional<NativeArtifact> found = NativeArtifactVerifier.findArtifact(artifacts, platform);
            assertTrue(found.isPresent(), "manifest 缺少平台 " + platform);
            NativeArtifact a = found.get();
            assertEquals(3, a.abi());
            assertFalse(a.sha256().isBlank(), "平台 " + platform + " 的 sha256 为空");
            assertFalse(a.filename().isBlank(), "平台 " + platform + " 的 filename 为空");
        }
    }

    @Test
    void verifyMatchesAndRejectsDigest() throws Exception {
        List<NativeArtifact> artifacts = loadPackagedManifest();
        NativeArtifact win64 = NativeArtifactVerifier.findArtifact(artifacts, "windows-x64").orElseThrow();

        // 正例：同哈希字节校验通过（大小写不敏感）
        byte[] data = "ysm-core".getBytes(StandardCharsets.UTF_8);
        NativeArtifact fake = new NativeArtifact("windows-x64", "ysm-core.dll",
                NativeArtifactVerifier.sha256(data), 3, "t");
        assertTrue(NativeArtifactVerifier.verify(data, fake));
        assertTrue(NativeArtifactVerifier.verify(data, fake));

        // 反例：内容不同 → digest mismatch
        byte[] tampered = "ysm-core-tampered".getBytes(StandardCharsets.UTF_8);
        assertFalse(NativeArtifactVerifier.verify(tampered, fake));

        // 反例：manifest 中真实条目对伪造内容必然不匹配
        assertFalse(NativeArtifactVerifier.verify(tampered, win64));
    }

    @Test
    void nullArtifactNeverVerifies() {
        assertFalse(NativeArtifactVerifier.verify(new byte[]{1}, (NativeArtifact) null));
        NativeArtifact blankSha = new NativeArtifact("p", "f", "   ", 3, "v");
        assertFalse(NativeArtifactVerifier.verify(new byte[]{1}, blankSha));
    }

    @Test
    void unknownPlatformYieldsEmpty() throws Exception {
        List<NativeArtifact> artifacts = loadPackagedManifest();
        assertTrue(NativeArtifactVerifier.findArtifact(artifacts, "plan9-x64").isEmpty());
    }

    @Test
    void platformNormalizationIsStable() {
        assertEquals("windows-x64", NativeArtifactVerifier.normalizePlatform("Windows-X64"));
        assertEquals("linux-x64", NativeArtifactVerifier.normalizePlatform("linux-x64"));
        assertEquals("", NativeArtifactVerifier.normalizePlatform(null));
    }

    private static List<NativeArtifact> loadPackagedManifest() throws Exception {
        try (InputStream in = NativeArtifactVerifierTest.class.getResourceAsStream("/native-manifest.json")) {
            assertNotNull(in, "测试 classpath 上缺少 native-manifest.json（main resources 未打包？）");
            return NativeArtifactVerifier.parseManifest(in);
        }
    }
}
