package com.micaftic.morpher.core.nativeutil;

import com.micaftic.morpher.core.nativeutil.NativeArtifactVerifier.NativeArtifact;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R1.2.2 §11 Native 信任链：manifest 解析与 digest 校验（纯 Java）。
 *
 * <p>使用内存中的六平台 manifest 夹具验证解析、sha256 校验、平台查找与规范化。
 * native/CurseForge 是否打包该资源由发行物审计覆盖，避免单测依赖某一分发变体的资源布局。
 */
class NativeArtifactVerifierTest {

    @Test
    void manifestListsAllSixPlatforms() throws Exception {
        List<NativeArtifact> artifacts = loadManifest();
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
        List<NativeArtifact> artifacts = loadManifest();
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
        List<NativeArtifact> artifacts = loadManifest();
        assertTrue(NativeArtifactVerifier.findArtifact(artifacts, "plan9-x64").isEmpty());
    }

    @Test
    void platformNormalizationIsStable() {
        assertEquals("windows-x64", NativeArtifactVerifier.normalizePlatform("Windows-X64"));
        assertEquals("linux-x64", NativeArtifactVerifier.normalizePlatform("linux-x64"));
        assertEquals("", NativeArtifactVerifier.normalizePlatform(null));
    }

    private static List<NativeArtifact> loadManifest() throws Exception {
        String platforms = java.util.Arrays.stream(new String[]{"windows-x64", "windows-x86", "linux-x64", "macos-x64", "macos-arm64", "android-arm64"})
                .map(platform -> "{\"platform\":\"" + platform + "\",\"filename\":\"test.bin\",\"sha256\":\"" + "0".repeat(64) + "\",\"abi\":3,\"version\":\"test\"}")
                .collect(Collectors.joining(","));
        String manifest = "{\"formatVersion\":1,\"abi\":3,\"version\":\"test\",\"artifacts\":[" + platforms + "]}";
        try (InputStream in = new ByteArrayInputStream(manifest.getBytes(StandardCharsets.UTF_8))) {
            return NativeArtifactVerifier.parseManifest(in);
        }
    }
}
