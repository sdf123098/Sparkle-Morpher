package com.micaftic.morpher.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * R4.1/R4.3 模型资源容器：所有模型 parser 统一经此读取资源，禁止直接
 * root.resolve / Files.readAllBytes / ZipFile。
 *
 * 职责：
 * <ul>
 *   <li>sandbox：相对路径归一化后必须落在根内（词法逃逸 `..`/绝对路径 → 拒绝）</li>
 *   <li>case behavior：条目名原样保留（模型内部约定大小写敏感），读取时精确匹配</li>
 *   <li>archive limits（R4.3，防 zip bomb）：总条目数、总解压字节、单文件字节上限</li>
 *   <li>统计：expandedSize / fileCount</li>
 *   <li>hash：内容确定性 hash（SHA-256；folder hash 的 MD5 语义由调用方保留）</li>
 * </ul>
 *
 * 三种来源：folder（目录）、zip（归档，ZipFile API + 限额）、memory（内存 map，如 YSGP 解包）。
 */
public final class ModelResourceContainer implements AutoCloseable {

    /** R4.3 限额：单文件最大字节。 */
    public static final long MAX_PER_FILE_BYTES = 64L * 1024 * 1024;
    /** R4.3 限额：最大条目数（zip bomb / 海量小文件防御）。 */
    public static final int MAX_FILE_COUNT = 16_384;
    /** R4.3 限额：总解压字节。 */
    public static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;

    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    private long expandedBytes;

    private ModelResourceContainer() {
    }

    public static ModelResourceContainer folder(Path root) throws IOException {
        ModelResourceContainer container = new ModelResourceContainer();
        try (var stream = Files.walk(root)) {
            for (Path p : stream.filter(Files::isRegularFile).toArray(Path[]::new)) {
                if (container.entries.size() >= MAX_FILE_COUNT) {
                    throw new IOException("Model resource exceeds file count limit (" + MAX_FILE_COUNT + ")");
                }
                Path relative = root.relativize(p);
                byte[] data = Files.readAllBytes(p);
                if (data.length > MAX_PER_FILE_BYTES) {
                    throw new IOException("Model resource exceeds per-file limit: " + relative);
                }
                container.put(relative.toString().replace('\\', '/'), data);
            }
        }
        return container;
    }

    public static ModelResourceContainer zip(Path archive) throws IOException {
        return zip(archive, StandardCharsets.UTF_8);
    }

    public static ModelResourceContainer zip(Path archive, Charset charset) throws IOException {
        ModelResourceContainer container = new ModelResourceContainer();
        try (ZipFile zipFile = new ZipFile(archive.toFile(), charset)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (container.entries.size() >= MAX_FILE_COUNT) {
                    throw new IOException("Model archive exceeds file count limit (" + MAX_FILE_COUNT + ")");
                }
                if (entry.getSize() > MAX_PER_FILE_BYTES) {
                    throw new IOException("Model archive entry exceeds per-file limit: " + entry.getName());
                }
                try (InputStream input = zipFile.getInputStream(entry)) {
                    byte[] data = input.readAllBytes();
                    if (data.length > MAX_PER_FILE_BYTES) {
                        throw new IOException("Model archive entry exceeds per-file limit: " + entry.getName());
                    }
                    if (container.expandedBytes + data.length > MAX_EXPANDED_BYTES) {
                        throw new IOException("Model archive exceeds total expanded limit ("
                                + MAX_EXPANDED_BYTES + " bytes)");
                    }
                    container.put(entry.getName(), data);
                }
            }
        }
        return container;
    }

    public static ModelResourceContainer memory(Map<String, byte[]> inMemoryFiles) {
        ModelResourceContainer container = new ModelResourceContainer();
        if (inMemoryFiles != null) {
            for (Map.Entry<String, byte[]> e : inMemoryFiles.entrySet()) {
                container.put(e.getKey(), e.getValue());
            }
        }
        return container;
    }

    private void put(String name, byte[] data) {
        entries.put(name, data);
        expandedBytes += data.length;
    }

    /** 资源名集合（不可变视图）。 */
    public Set<String> names() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    public int fileCount() {
        return entries.size();
    }

    public long expandedBytes() {
        return expandedBytes;
    }

    public boolean exists(String relativePath) {
        return entries.containsKey(relativePath);
    }

    /**
     * 读取资源（sandbox：词法逃逸拒绝——`..`、绝对路径、盘符、UNC）。
     *
     * @return 资源字节；不存在返回 null
     */
    public byte[] read(String relativePath) throws IOException {
        String normalized = normalize(relativePath);
        if (normalized == null) {
            throw new IOException("Rejected model resource path escaping root: " + relativePath);
        }
        return entries.get(normalized);
    }

    /** 内容确定性 hash（SHA-256 hex）。 */
    public String sha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                digest.update(entry.getValue());
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 归一化相对路径：拒绝绝对路径/盘符/UNC/`..` 逃逸；返回 null 表示非法。 */
    static String normalize(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        String p = relativePath.replace('\\', '/');
        if (p.startsWith("/") || p.matches("^[A-Za-z]:.*") || p.startsWith("//")) {
            return null;
        }
        String[] segments = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(segment);
        }
        return sb.toString();
    }

    @Override
    public void close() {
        entries.clear();
    }
}
