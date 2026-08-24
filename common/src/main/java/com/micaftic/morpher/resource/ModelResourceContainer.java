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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * R4.1/R4.3 模型资源容器：所有模型 parser 统一经此读取资源，禁止直接
 * root.resolve / Files.readAllBytes / ZipFile。
 *
 * 职责：
 * <ul>
 *   <li>sandbox：相对路径归一化后必须落在根内（词法逃逸 `..`/绝对路径 → 拒绝）</li>
 *   <li>case behavior：条目名原样保留（模型内部约定大小写敏感），读取时精确匹配，
 *       未命中时按大小写不敏感回退（YSM 模型包在 Windows 解压后大小写常不一致）</li>
 *   <li>archive limits（R4.3，防 zip bomb）：总条目数、总解压字节、单文件字节上限；
 *       超限条目跳过（warn）而非抛异常，保证单个畸形模型包不会拖垮整个解析流程</li>
 *   <li>统计：expandedSize / fileCount</li>
 *   <li>hash：内容确定性 hash（SHA-256；folder hash 的 MD5 语义由调用方保留）</li>
 * </ul>
 *
 * 三种来源：folder（目录，R4.2 起懒加载——构造只收集条目清单，读取时实时读文件，
 * 避免大模型目录全量预读进内存）、zip（归档，ZipFile API + GBK 回退 + 限额 + 模型根探测）、
 * memory（内存 map，如 YSGP 解包）。
 *
 * R4.2 迁移说明：YSMFolderDeserializer 的 zipfs / ZipFile 回退 / 根探测逻辑统一移入本类；
 * 限额语义统一为 warn-skip（R4.3 生产路径语义），folder 源保持懒加载不回归。
 */
public final class ModelResourceContainer implements AutoCloseable {

    /** R4.3 限额：单文件最大字节。 */
    public static final long MAX_PER_FILE_BYTES = 64L * 1024 * 1024;
    /** R4.3 限额：最大条目数（zip bomb / 海量小文件防御）。 */
    public static final int MAX_FILE_COUNT = 16_384;
    /** R4.3 限额：总解压字节。 */
    public static final long MAX_EXPANDED_BYTES = 512L * 1024 * 1024;

    /** 内存源（memory/zip）：归一化名 → 内容。 */
    private final Map<String, byte[]> entries = new LinkedHashMap<>();
    /** 懒加载 folder 源：归一化名 → 磁盘真实文件。 */
    private final Map<String, Path> lazyFiles = new LinkedHashMap<>();
    /** 全源大小写索引：小写归一化名 → 存储名（case-insensitive 回退）。 */
    private final Map<String, String> lowerIndex = new HashMap<>();
    private long expandedBytes;
    private long lazyExpandedBytes;

    private ModelResourceContainer() {
    }

    /**
     * folder 源（懒加载）：构造时 walk 收集条目清单（不读内容），
     * read 时实时读取磁盘文件。单文件超限 warn + 跳过（与 YSMFolderDeserializer
     * 原 folder 模式语义一致，不抛异常）。
     */
    public static ModelResourceContainer folder(Path root) throws IOException {
        ModelResourceContainer container = new ModelResourceContainer();
        try (var stream = Files.walk(root)) {
            for (Path p : stream.filter(Files::isRegularFile).toArray(Path[]::new)) {
                String relative = root.relativize(p).toString().replace('\\', '/');
                String normalized = normalize(relative);
                if (normalized == null) {
                    continue;
                }
                if (container.lazyFiles.containsKey(normalized)) {
                    continue; // 大小写归一后冲突：保留第一个（与 zip 源 putIfAbsent 一致）
                }
                container.lazyFiles.put(normalized, p);
                container.lowerIndex.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
                try {
                    container.lazyExpandedBytes += Files.size(p);
                } catch (IOException ignored) {
                    // 文件并发删除等：不计大小
                }
            }
        }
        return container;
    }

    public static ModelResourceContainer zip(Path archive) throws IOException {
        return zip(archive, StandardCharsets.UTF_8);
    }

    /**
     * zip 源：ZipFile API（java.base，不依赖 zipfs）。
     * <ul>
     *   <li>GBK 回退：UTF-8 解码条目名失败（中文 Windows 压缩工具产物）时按 GBK 重试</li>
     *   <li>限额 warn-skip：单文件 / 总条目数 / 总解压字节超限跳过（不抛异常）</li>
     *   <li>模型根探测：zip 内唯一模型根目录（含 ysm.json 或 main.json+arm.json）
     *       剥离前缀；多个不同模型目录时以整个压缩包为根</li>
     * </ul>
     */
    public static ModelResourceContainer zip(Path archive, Charset charset) throws IOException {
        ModelResourceContainer container = new ModelResourceContainer();
        try {
            readZipEntries(container, archive, charset);
        } catch (ZipException e) {
            // 中文 Windows 压缩工具生成的 zip 常用 GBK 编码条目名（jdk 按 UTF-8 解码会报
            // "invalid CEN header (bad entry name)"）。UTF-8 读取失败时回退 GBK。
            System.err.println("[SM] Warning: Zip entry names are not " + charset.name()
                    + ", retrying with GBK: " + archive);
            readZipEntries(container, archive, Charset.forName("GBK"));
        }
        return container;
    }

    private static void readZipEntries(ModelResourceContainer container, Path archive, Charset charset)
            throws IOException {
        Map<String, byte[]> rawEntries = new LinkedHashMap<>();
        long totalExpanded = 0L;
        try (ZipFile zipFile = new ZipFile(archive.toFile(), charset)) {
            java.util.Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (rawEntries.size() >= MAX_FILE_COUNT) {
                    System.err.println("[SM] Warning: Skipping rest of archive (file count limit "
                            + MAX_FILE_COUNT + "): " + archive);
                    break;
                }
                try (InputStream input = zipFile.getInputStream(entry)) {
                    byte[] bytes = input.readNBytes((int) Math.min(MAX_PER_FILE_BYTES + 1, Integer.MAX_VALUE));
                    if (bytes.length > MAX_PER_FILE_BYTES) {
                        System.err.println("[SM] Warning: Skipping oversized zip entry ("
                                + bytes.length + " bytes): " + entry.getName());
                        continue;
                    }
                    totalExpanded += bytes.length;
                    if (totalExpanded > MAX_EXPANDED_BYTES) {
                        System.err.println("[SM] Warning: Skipping rest of archive (total expanded limit "
                                + MAX_EXPANDED_BYTES + " bytes): " + archive);
                        break;
                    }
                    rawEntries.putIfAbsent(entry.getName(), bytes);
                }
            }
        }
        // 模型根目录探测（字符串层面，与 YSMFolderDeserializer 原逻辑一致）
        String rootPrefix = detectModelRoot(rawEntries.keySet());
        for (Map.Entry<String, byte[]> entry : rawEntries.entrySet()) {
            String name = entry.getKey();
            String stripped = name;
            if (rootPrefix != null && !rootPrefix.isEmpty() && name.startsWith(rootPrefix)) {
                stripped = name.substring(rootPrefix.length()).replaceFirst("^/+", "");
            }
            String normalized = normalize(stripped);
            if (normalized == null || normalized.isEmpty()) {
                continue;
            }
            if (container.entries.containsKey(normalized) || container.lowerIndex.containsKey(normalized.toLowerCase(Locale.ROOT))) {
                continue; // 大小写归一后冲突：保留第一个
            }
            container.entries.put(normalized, entry.getValue());
            container.lowerIndex.put(normalized.toLowerCase(Locale.ROOT), normalized);
            container.expandedBytes += entry.getValue().length;
        }
    }

    /** 探测 zip 条目中的模型根前缀：返回需剥离的前缀；null/"" 表示以整个包为根。 */
    private static String detectModelRoot(Set<String> names) {
        Set<String> normalized = new java.util.HashSet<>();
        for (String name : names) {
            normalized.add(name.replace('\\', '/').replaceAll("^/+|/+$", "").toLowerCase(Locale.ROOT));
        }
        if (normalized.contains("ysm.json")
                || (normalized.contains("main.json") && normalized.contains("arm.json"))) {
            return "";
        }
        String detected = null;
        for (String name : names) {
            int slash = name.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String segment = name.substring(0, slash);
            String folderKey = segment.replace('\\', '/').replaceAll("^/+|/+$", "").toLowerCase(Locale.ROOT);
            boolean modelFolder = normalized.contains(folderKey + "/ysm.json")
                    || (normalized.contains(folderKey + "/main.json") && normalized.contains(folderKey + "/arm.json"));
            if (!modelFolder) {
                continue;
            }
            if (detected == null) {
                detected = segment;
            } else if (!segment.equalsIgnoreCase(detected)) {
                // 多个不同的模型目录 → 以整个压缩包为根
                return null;
            }
        }
        return detected;
    }

    /**
     * memory 源：原样保留条目名。大小写归一后冲突（如同时存在 "Tex.png" 与 "tex.png"）
     * 抛 IllegalArgumentException（调用方数据错误，与 YSMFolderDeserializer 原语义一致）。
     */
    public static ModelResourceContainer memory(Map<String, byte[]> inMemoryFiles) {
        ModelResourceContainer container = new ModelResourceContainer();
        if (inMemoryFiles != null) {
            for (Map.Entry<String, byte[]> e : inMemoryFiles.entrySet()) {
                String normalized = normalize(e.getKey());
                if (normalized == null) {
                    throw new IllegalArgumentException("Invalid resource path: " + e.getKey());
                }
                String lower = normalized.toLowerCase(Locale.ROOT);
                String existing = container.lowerIndex.get(lower);
                if (existing != null && !existing.equals(normalized)) {
                    throw new IllegalArgumentException(
                            "Duplicate resource path after case normalization: " + e.getKey());
                }
                container.entries.put(normalized, e.getValue());
                container.lowerIndex.put(lower, normalized);
                container.expandedBytes += e.getValue().length;
            }
        }
        return container;
    }

    /** 资源名集合（不可变视图；懒加载 folder 源为 walk 收集的清单）。 */
    public Set<String> names() {
        java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>(entries.keySet());
        all.addAll(lazyFiles.keySet());
        return Collections.unmodifiableSet(all);
    }

    public int fileCount() {
        return entries.size() + lazyFiles.size();
    }

    public long expandedBytes() {
        return expandedBytes + lazyExpandedBytes;
    }

    public boolean exists(String relativePath) {
        String normalized = normalize(relativePath);
        if (normalized == null) {
            return false;
        }
        if (entries.containsKey(normalized) || lazyFiles.containsKey(normalized)) {
            return true;
        }
        String folded = lowerIndex.get(normalized.toLowerCase(Locale.ROOT));
        return folded != null;
    }

    /**
     * 读取资源（sandbox：词法逃逸拒绝——`..`、绝对路径、盘符、UNC）。
     * 先精确匹配，未命中按大小写不敏感回退（YSM 模型包在 Windows 解压后大小写常不一致）。
     *
     * @return 资源字节；不存在返回 null
     */
    public byte[] read(String relativePath) throws IOException {
        String normalized = normalize(relativePath);
        if (normalized == null) {
            throw new IOException("Rejected model resource path escaping root: " + relativePath);
        }
        byte[] data = entries.get(normalized);
        if (data != null) {
            return data;
        }
        Path file = lazyFiles.get(normalized);
        if (file == null) {
            // case-insensitive 回退
            String folded = lowerIndex.get(normalized.toLowerCase(Locale.ROOT));
            if (folded != null && !folded.equals(normalized)) {
                data = entries.get(folded);
                if (data != null) {
                    return data;
                }
                file = lazyFiles.get(folded);
            }
        }
        if (file != null) {
            return readLazyFile(file, normalized);
        }
        return null;
    }

    /** 懒加载 folder 源读取：单文件限额 warn + 跳过（不抛异常，保持解析不中断）。 */
    private byte[] readLazyFile(Path file, String normalized) {
        try {
            long size = Files.size(file);
            if (size > MAX_PER_FILE_BYTES) {
                System.err.println("[SM] Warning: Skipping oversized model resource ("
                        + size + " bytes): " + normalized);
                return null;
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            System.err.println("[SM] Warning: Failed to read model resource: " + normalized);
            return null;
        }
    }

    /** 内容确定性 hash（SHA-256 hex；按 names() 顺序读取，懒加载 folder 源读全部内容）。 */
    public String sha256() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String name : names()) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                byte[] data = null;
                try {
                    data = read(name);
                } catch (IOException ignored) {
                    // 读取失败（如懒加载文件被删）：跳过该条目
                }
                if (data != null) {
                    digest.update(data);
                }
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
        lazyFiles.clear();
        lowerIndex.clear();
    }
}
