package com.micaftic.morpher.core.native;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.util.DigestUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * R1.2.2 §11 Native Runtime 信任链 — manifest 解析与制品校验（纯 Java，零 MC import）。
 *
 * <p>native-manifest.json 单份、六分支共用（JNI 层与 MC 版本无关，§11.1）：
 * <pre>
 * { "formatVersion": 1, "abi": 3, "version": "&lt;source-commit&gt;",
 *   "artifacts": [ { "platform": "windows-x64", "filename": "ysm-core.dll",
 *                    "sha256": "…", "abi": 3, "version": "…" }, … ] }
 * </pre>
 *
 * <p>行为约束（§11.2）：digest mismatch 禁止加载；离线/无 manifest 拒绝加载 +
 * 明确错误，回退 Java 路径；开发 override 保留在加载器（跳过 manifest 校验）。
 */
public final class NativeArtifactVerifier {

    private NativeArtifactVerifier() {
    }

    /** manifest 中单个平台制品的信任记录。 */
    public record NativeArtifact(String platform, String filename, String sha256, int abi, String version) {
        public boolean matchesPlatform(String platformKey) {
            return platform.equals(platformKey);
        }
    }

    /**
     * 从 JSON 输入流解析 manifest。
     *
     * @throws IOException 解析失败 / 缺少必要字段（调用方转为明确错误并回退 Java 路径）
     */
    public static List<NativeArtifact> parseManifest(InputStream json) throws IOException {
        List<NativeArtifact> artifacts = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(json, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("artifacts");
            if (arr == null) {
                throw new IOException("native-manifest: missing 'artifacts' array");
            }
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String platform = requiredString(o, "platform");
                String filename = requiredString(o, "filename");
                String sha256 = requiredString(o, "sha256");
                int abi = o.has("abi") ? o.get("abi").getAsInt() : -1;
                String version = o.has("version") ? o.get("version").getAsString() : "";
                artifacts.add(new NativeArtifact(platform, filename, sha256, abi, version));
            }
        } catch (IllegalStateException e) {
            throw new IOException("native-manifest: malformed JSON: " + e.getMessage(), e);
        }
        if (artifacts.isEmpty()) {
            throw new IOException("native-manifest: empty artifacts list");
        }
        return artifacts;
    }

    /** 按平台 key 找信任记录（如 "windows-x64"）。 */
    public static Optional<NativeArtifact> findArtifact(List<NativeArtifact> artifacts, String platformKey) {
        for (NativeArtifact a : artifacts) {
            if (a.matchesPlatform(platformKey)) {
                return Optional.of(a);
            }
        }
        return Optional.empty();
    }

    /**
     * 计算文件 SHA-256（十六进制，小写）。
     *
     * @throws IOException 文件不可读
     */
    public static String sha256(Path file) throws IOException {
        return DigestUtil.sha256Hex(Files.readAllBytes(file));
    }

    /** 字节内容 SHA-256（十六进制，小写）。 */
    public static String sha256(byte[] data) {
        return DigestUtil.sha256Hex(data);
    }

    /** 校验字节内容与信任记录是否匹配（忽略大小写）。 */
    public static boolean verify(byte[] data, NativeArtifact artifact) {
        if (artifact == null || artifact.sha256() == null || artifact.sha256().isBlank()) {
            return false;
        }
        return sha256(data).equalsIgnoreCase(artifact.sha256());
    }

    /** 校验文件内容与信任记录是否匹配（忽略大小写）。 */
    public static boolean verify(Path file, NativeArtifact artifact) {
        try {
            if (artifact == null || artifact.sha256() == null || artifact.sha256().isBlank()) {
                return false;
            }
            return sha256(file).equalsIgnoreCase(artifact.sha256());
        } catch (IOException e) {
            return false;
        }
    }

    /** 规范化平台 key（小写 + 正斜杠），便于与 manifest 比较。 */
    public static String normalizePlatform(String platformKey) {
        return platformKey == null ? "" : platformKey.toLowerCase(Locale.ROOT).replace('\\', '/');
    }

    private static String requiredString(JsonObject o, String field) throws IOException {
        if (!o.has(field) || !o.get(field).isJsonPrimitive()) {
            throw new IOException("native-manifest: missing required field '" + field + "'");
        }
        return o.get(field).getAsString();
    }
}