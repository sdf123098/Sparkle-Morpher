package com.micaftic.morpher.core.model.catalog;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.resource.models.Metadata;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * R7.2 LocalModelCatalog — 本地模型 catalog（从 ClientModelManager 抽取）。
 *
 * <p>职责：把本地模型目录（builtin/custom/auth）扫描成一张 {@code modelKey → Entry}
 * 的目录表，并在 reload 时对旧目录做 diff（stale/keep 判定），供调用方释放失效装配。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>纯 Java（零 MC import）：模型文件夹判定以 {@link Predicate} 注入
 *       （运行时传 {@code YSMFolderDeserializer::isModelFolder}），JVM 单测可跑真实逻辑。</li>
 *   <li>{@code scan} 支持跨 baseDir 共享同一 catalog（builtin/custom/auth 三次扫描），
 *       重复 id 先到先得（putIfAbsent），与调用方既有语义一致。</li>
 *   <li>{@code scan} 接收 {@code previousState}（上一轮 catalog）以继承 displayName /
 *       modelInfo 元数据 —— 轻量扫描不重读模型几何，名字缓存跨 reload 保留。</li>
 *   <li>{@code diff} 只产出 stale id 列表与迁移后的新 catalog，资源释放（ModelAssembly
 *       等 MC 侧重资源）由调用方按 stale id 执行。</li>
 * </ul>
 */
public final class LocalModelCatalog {

    /** 本地模型文件大小上限（与原 ClientModelManager 常量一致）。 */
    public static final long DEFAULT_MAX_FILE_BYTES = 512L * 1024L * 1024L;

    private static final String[] IMPORT_EXTENSIONS = {".ysm", ".zip", ".bbmodel"};

    private final long maxFileBytes;

    public LocalModelCatalog() {
        this(DEFAULT_MAX_FILE_BYTES);
    }

    public LocalModelCatalog(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    // ===================== Entry =====================

    /**
     * catalog 条目：本地/远程模型来源的轻量元数据（不持有模型几何）。
     * 对应原 ClientModelManager.LazyModelSource。
     */
    public static final class Entry {
        public final Path path;
        @Nullable
        public final byte[] cacheKey;
        public final boolean remote;
        public final boolean auth;
        public final long fingerprint;
        public volatile ServerModelInfo modelInfo;
        @Nullable
        public volatile String displayName;

        public Entry(Path path, @Nullable byte[] cacheKey, boolean remote, boolean auth,
                     long fingerprint, @Nullable ServerModelInfo modelInfo,
                     @Nullable String displayName) {
            this.path = path.toAbsolutePath().normalize();
            this.cacheKey = cacheKey == null ? null : cacheKey.clone();
            this.remote = remote;
            this.auth = auth;
            this.fingerprint = fingerprint;
            this.modelInfo = modelInfo;
            this.displayName = displayName;
        }

        /** 本地来源是否与另一条目指向同一文件版本（auth + fingerprint + path 全等）。 */
        public boolean sameLocalSource(@Nullable Entry other) {
            return other != null && !remote && !other.remote && auth == other.auth
                    && fingerprint == other.fingerprint && path.equals(other.path);
        }
    }

    // ===================== 结果 / diff 结构 =====================

    /** 一次 scan 的结果：是否发现条目 + 本次写入的 sources（modelKey → 绝对路径）。 */
    public record ScanResult(boolean foundAny, Map<String, Entry> entries, Map<String, Path> sources) {
    }

    /** 旧 catalog → 新 catalog 的 diff：需要释放的 stale id + 迁移后的完整新 catalog。 */
    public record Diff(List<String> staleIds, Map<String, Entry> catalog) {
    }

    // ===================== 扫描 =====================

    /**
     * 扫描单个本地模型 baseDir，条目写入共享 catalog（putIfAbsent，先到先得）。
     *
     * @param baseDir             本地模型根目录（builtin/custom/auth）
     * @param isAuth              目录内模型是否视为授权模型
     * @param modelFolderDetector 模型文件夹判定（YSMFolderDeserializer.isModelFolder）
     * @param previousState       上一轮 catalog（继承 displayName/modelInfo 元数据）
     * @param catalog             共享 catalog（多次 scan 复用实现跨 baseDir 去重）
     */
    public ScanResult scan(Path baseDir, boolean isAuth, Predicate<Path> modelFolderDetector,
                           Map<String, Entry> previousState, Map<String, Entry> catalog) throws IOException {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return new ScanResult(false, catalog, Map.of());
        }
        Map<String, Path> sources = new LinkedHashMap<>();
        boolean[] foundAny = new boolean[]{false};
        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(baseDir)) {
                    return FileVisitResult.CONTINUE;
                }
                if (modelFolderDetector.test(dir)) {
                    String modelId = normalizeLocalId(baseDir.relativize(dir).toString());
                    registerEntry(catalog, sources, modelId, dir, isAuth, previousState);
                    foundAny[0] = true;
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(".ysm") && !lower.endsWith(".zip") && !lower.endsWith(".bbmodel")) {
                    return FileVisitResult.CONTINUE;
                }
                if (attrs.size() > maxFileBytes) {
                    return FileVisitResult.CONTINUE;
                }
                String modelId = normalizeLocalId(baseDir.relativize(file).toString());
                registerEntry(catalog, sources, modelId, file, isAuth, previousState);
                foundAny[0] = true;
                return FileVisitResult.CONTINUE;
            }
        });
        return new ScanResult(foundAny[0], catalog, sources);
    }

    /** 便捷重载：单 baseDir 扫描，返回新 catalog。 */
    public ScanResult scan(Path baseDir, boolean isAuth, Predicate<Path> modelFolderDetector,
                           Map<String, Entry> previousState) throws IOException {
        return scan(baseDir, isAuth, modelFolderDetector, previousState, new LinkedHashMap<>());
    }

    private void registerEntry(Map<String, Entry> catalog, Map<String, Path> sources,
                               String modelId, Path sourcePath, boolean isAuth,
                               Map<String, Entry> previousState) throws IOException {
        String modelKey = canonicalKey(modelId);
        if (modelKey == null || modelKey.isBlank() || "default".equals(modelKey)) {
            return;
        }
        long fingerprint = fingerprint(sourcePath);
        Entry previous = previousState.get(modelKey);
        ServerModelInfo modelInfo = previous == null ? null : previous.modelInfo;
        String displayName = previous == null ? null : previous.displayName;
        if (displayName == null) {
            displayName = displayNameFromInfo(modelInfo);
        }
        if (displayName == null) {
            displayName = sniffName(sourcePath);
        }
        Entry entry = new Entry(sourcePath, null, false, isAuth, fingerprint, modelInfo, displayName);
        Entry duplicate = catalog.putIfAbsent(modelKey, entry);
        if (duplicate == null) {
            sources.put(modelKey, sourcePath.toAbsolutePath().normalize());
        }
    }

    // ===================== diff =====================

    /**
     * 计算旧 catalog → 新 catalog 的 diff。
     *
     * <p>非 remote 旧条目：新 catalog 缺失或来源变化 → 列入 stale（调用方释放资源）；
     * 来源未变 → keep 并把 displayName/modelInfo 元数据迁移到新条目。</p>
     *
     * <p>迁移直接作用于 incoming 条目对象，返回的 catalog 即迁移后的完整新目录。</p>
     */
    public static Diff diff(Map<String, Entry> previous, Map<String, Entry> incoming) {
        List<String> staleIds = new ArrayList<>();
        for (Map.Entry<String, Entry> old : new ArrayList<>(previous.entrySet())) {
            if (old.getValue().remote) {
                continue;
            }
            Entry replacement = incoming.get(old.getKey());
            if (replacement == null || !old.getValue().sameLocalSource(replacement)) {
                staleIds.add(old.getKey());
                continue;
            }
            if (old.getValue().modelInfo != null) {
                replacement.modelInfo = old.getValue().modelInfo;
            }
            if (StringUtils.isBlank(replacement.displayName) && StringUtils.isNotBlank(old.getValue().displayName)) {
                replacement.displayName = old.getValue().displayName;
            } else if (StringUtils.isBlank(replacement.displayName)) {
                String fromInfo = displayNameFromInfo(replacement.modelInfo);
                if (StringUtils.isNotBlank(fromInfo)) {
                    replacement.displayName = fromInfo;
                }
            }
        }
        return new Diff(staleIds, incoming);
    }

    // ===================== id 工具 =====================

    /** 运行时模型 key 归一：trim + 反斜杠转正斜杠 + 折叠重复斜杠 + 小写。 */
    @Nullable
    public static String canonicalKey(@Nullable String modelId) {
        if (modelId == null) {
            return null;
        }
        String key = modelId.trim().replace('\\', '/').replaceAll("/+", "/").toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : key;
    }

    /** 去掉导入文件扩展名（.ysm/.zip/.bbmodel，大小写不敏感）。 */
    public static String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : IMPORT_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    /** 本地模型 id 归一 = 扩展名剥离 + runtime key 归一。 */
    @Nullable
    public static String normalizeLocalId(@Nullable String modelId) {
        if (modelId == null) {
            return null;
        }
        return stripImportExtension(canonicalKey(modelId));
    }

    // ===================== fingerprint =====================

    /** 本地来源指纹：单文件取 mtime+size；目录递归聚合相对路径/mtime/size。 */
    public static long fingerprint(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return Files.getLastModifiedTime(path).toMillis() * 31L + Files.size(path);
        }
        final long[] fingerprint = {1L};
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                fingerprint[0] = 31L * fingerprint[0] + path.relativize(file).toString().hashCode();
                fingerprint[0] = 31L * fingerprint[0] + attrs.lastModifiedTime().toMillis();
                fingerprint[0] = 31L * fingerprint[0] + attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return fingerprint[0];
    }

    // ===================== 名称嗅探 =====================

    /**
     * 轻量名称嗅探：只读元数据，绝不解析完整模型几何。
     * 支持 YSM 文件夹（ysm.json）、.zip 包（内嵌 ysm.json）、.bbmodel 文件；加密 .ysm 跳过。
     */
    @Nullable
    public static String sniffName(@Nullable Path sourcePath) {
        if (sourcePath == null) {
            return null;
        }
        try {
            if (Files.isDirectory(sourcePath)) {
                Path ysmJson = sourcePath.resolve("ysm.json");
                if (Files.isRegularFile(ysmJson)) {
                    return parseMetadataNameFromYsmJson(Files.readString(ysmJson, StandardCharsets.UTF_8));
                }
                return null;
            }
            if (!Files.isRegularFile(sourcePath)) {
                return null;
            }
            String fileName = sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString();
            String lower = fileName.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".bbmodel")) {
                return parseBbmodelRootName(readJsonHead(sourcePath));
            }
            if (lower.endsWith(".zip")) {
                return sniffNameFromZip(sourcePath);
            }
            // Encrypted .ysm requires full decrypt — skip here.
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String sniffNameFromZip(Path zipPath) {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            // 常见布局:ysm.json 直接位于 zip 根目录 —— 用 getEntry 直取,避免遍历全部条目。
            ZipEntry rootEntry = zip.getEntry("ysm.json");
            if (rootEntry != null && !rootEntry.isDirectory()) {
                String parsed = readNameFromZipEntry(zip, rootEntry);
                if (StringUtils.isNotBlank(parsed)) {
                    return parsed;
                }
            }
            // 兼容 ysm.json 位于子目录的布局。
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                int slash = name.lastIndexOf('/');
                String base = slash < 0 ? name : name.substring(slash + 1);
                if (!"ysm.json".equalsIgnoreCase(base)) {
                    continue;
                }
                String parsed = readNameFromZipEntry(zip, entry);
                if (StringUtils.isNotBlank(parsed)) {
                    return parsed;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static String readNameFromZipEntry(ZipFile zip, ZipEntry entry) {
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            return parseMetadataNameFromYsmJson(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 只读文件头部读取 JSON 文本 —— bbmodel 名称必然位于文件头部(meta/name 字段)，
     * 完整文件可能数 MB,截断读取可避免扫描大量模型时重复全量读盘。
     */
    private static String readJsonHead(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            byte[] head = in.readNBytes(256 * 1024);
            return new String(head, StandardCharsets.UTF_8);
        }
    }

    @Nullable
    private static String parseMetadataNameFromYsmJson(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("metadata") && root.get("metadata").isJsonObject()) {
                JsonObject meta = root.getAsJsonObject("metadata");
                if (meta.has("name") && meta.get("name").isJsonPrimitive()) {
                    String name = meta.get("name").getAsString();
                    return StringUtils.isBlank(name) ? null : name.trim();
                }
            }
            // Some packs put name at root.
            if (root.has("name") && root.get("name").isJsonPrimitive()) {
                String name = root.get("name").getAsString();
                return StringUtils.isBlank(name) ? null : name.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Nullable
    private static String parseBbmodelRootName(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("name") && root.get("name").isJsonPrimitive()) {
                String name = root.get("name").getAsString();
                return StringUtils.isBlank(name) ? null : name.trim();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 从 ServerModelInfo 的 metadata 提取展示名（可空）。 */
    @Nullable
    public static String displayNameFromInfo(@Nullable ServerModelInfo modelInfo) {
        if (modelInfo == null) {
            return null;
        }
        Metadata metadata = modelInfo.getExtraInfo();
        if (metadata == null || StringUtils.isBlank(metadata.getName())) {
            return null;
        }
        return metadata.getName().trim();
    }
}
