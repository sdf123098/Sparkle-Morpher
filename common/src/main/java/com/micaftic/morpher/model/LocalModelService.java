package com.micaftic.morpher.model;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.client.ExportResult;
import com.micaftic.morpher.core.model.catalog.LocalModelScanner;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.model.format.ServerAnimationInfo;
import com.micaftic.morpher.model.format.LocalModelDefinition;
import com.micaftic.morpher.model.format.ModelMetadata;
import com.micaftic.morpher.model.catalog.LocalModelDefinitionCatalog;
import com.micaftic.morpher.model.cache.LocalModelDataCache;
import com.micaftic.morpher.model.format.UUIDComponentData;
import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.resource.YSMBinarySerializer;
import com.micaftic.morpher.resource.bundle.GuiConfigMapper;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.DigestUtil;
import com.micaftic.morpher.util.ModelIdUtil;
import com.micaftic.morpher.util.PerformanceProfiler;
import com.micaftic.morpher.util.YSMComponentHelper;
import com.micaftic.morpher.util.YSMThreadPool;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.floats.FloatReferencePair;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.legacy.compat.LegacyCompatModelFormat;
import com.micaftic.morpher.core.security.YSMByteBuf;
import com.micaftic.morpher.core.security.YsmCrypt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/** Local model catalog, extraction, parsing, cache and export. No game-server transport. */
public final class LocalModelService {
    private static final String BUILTIN_RESOURCE_ROOT = ModelStoragePaths.builtinResourceRoot();
    private static final String BUILTIN_RESOURCE_INDEX = ModelStoragePaths.builtinResourceIndex();
    private static final String EXT_YSM = ".ysm";
    private static final String EXT_ZIP = ".zip";
    private static final String EXT_BBMODEL = ".bbmodel";

    /** 单个模型文件的大小上限（字节）。超限视为垃圾/损坏文件，跳过以避免内存暴涨或启动卡顿。 */
    private static final long MAX_MODEL_FILE_BYTES = 512L * 1024L * 1024L;
    /**
     * 配置相关文件夹
     */
    // R3.1：路径定义迁移至 ModelStoragePaths（此处保留常量委托，兼容既有引用）
    public static final Path FOLDER = ModelStoragePaths.folder();
    public static final Path BUILT = ModelStoragePaths.built();
    public static final Path CUSTOM = ModelStoragePaths.custom();
    public static final Path AUTH = ModelStoragePaths.auth();
    public static final Path EXPORT = ModelStoragePaths.export();

    /**
     * 生成缓存文件的文件夹
     */
    public static final Path CACHE = ModelStoragePaths.cache();
    public static final Path CACHE_SERVER_INDEX_FILE = ModelStoragePaths.cacheServerIndexFile();
    public static final Path CACHE_SERVER = ModelStoragePaths.cacheServer();
    public static final Path CACHE_CLIENT = ModelStoragePaths.cacheClient();
    public static final Path CACHE_BBMODEL_IMPORT_IDENTITY_FILE = ModelStoragePaths.cacheBbmodelImportIdentityFile();

    /**
     * Local parsed-model definitions and their lookup hashes are held by LocalModelDefinitionCatalog.
     * 原 CATALOG / CATALOG.authModels() / CATALOG.modelHashes() 三个静态字段的整表替换 + 归一回退查询语义。
     */
    static final LocalModelDefinitionCatalog<LocalModelDefinition> CATALOG = new LocalModelDefinitionCatalog<>();

    static final Map<String, LocalPackMetadata> packs = new ConcurrentHashMap<>();
    static final SecureRandom theRandom = new SecureRandom();
    private static volatile byte[] serverKey;
    private static volatile com.micaftic.morpher.core.storage.LocalModelDefaultsStore.Defaults defaults = com.micaftic.morpher.core.storage.LocalModelDefaultsStore.FALLBACK;
    private static boolean defaultsWritable;
    private static volatile boolean initialized = false;
    private static final ModelReloadCoordinator<ModelLoadResult> MODEL_RELOAD_COORDINATOR =
            new ModelReloadCoordinator<>(
                    com.micaftic.morpher.util.SmExecutors.pool(com.micaftic.morpher.util.SmExecutors.Pool.MODEL_RELOAD),
                    Runnable::run);

    public static void reloadPacks() {
        initialized = false;
        MODEL_RELOAD_COORDINATOR.submit(() -> {
            reloadPacksSync();
            return loadModelsSnapshot();
        }, LocalModelService::publishModelLoadResult,
                error -> YesSteveModel.LOGGER.error("[SM] Failed to reload model packs", error));
    }

    private static void reloadPacksSync() throws IOException {

        // Retain previous cache identities and encrypted files for migration and rollback.
        ModelStoragePaths.checkCacheVersionAndReset();

        createFolder(FOLDER);
        createFolder(BUILT);
        createFolder(CUSTOM);
        createFolder(AUTH);
        createFolder(EXPORT);

        createFolder(CACHE);
        createFolder(CACHE_SERVER);
        createFolder(CACHE_CLIENT);

        extractBuiltinModels();

        Files.writeString(BUILT.resolve("notice.txt"),
                "Generated builtin resources are refreshed at startup; put custom models in the custom directory.\n" +
                        "此目录保存内置模型；自定义模型请放在 custom 目录。",
                StandardCharsets.UTF_8);

        Path blacklistFile = FOLDER.resolve("blacklist.txt");
        if (!Files.exists(blacklistFile)) {
            String content =
                    "# 花火火的变身器 模组 - 内置模型黑名单配置文件\n" +
                            "# Sparkle's Morpher Mod - Built-in Model Blacklist Configuration File\n" +
                            "\n" +
                            "# 功能说明：\n" +
                            "# 随着内置模型数量的增加，为了满足个性化定制需求，本模组提供了黑名单功能\n" +
                            "# 允许用户选择性地禁用不需要的内置模型，以节省存储空间和加载时间\n" +
                            "#\n" +
                            "# Feature Description:\n" +
                            "# As the number of built-in models increases, this mod provides blacklist functionality\n" +
                            "# to meet customization needs, allowing users to selectively disable unwanted built-in\n" +
                            "# models to save storage space and loading time.\n" +
                            "\n" +
                            "# 使用方法：\n" +
                            "# 1. 在游戏启动前编辑此文件\n" +
                            "# 2. 清空 <游戏目录>/config/sparkle_morpher/builtin 文件夹中的已解压模型文件\n" +
                            "# 3. 重新启动游戏，模组将根据黑名单规则跳过指定模型的解压\n" +
                            "#\n" +
                            "# Usage Instructions:\n" +
                            "# 1. Edit this file before starting the game\n" +
                            "# 2. Clear extracted model files in <game_directory>/config/sparkle_morpher/builtin folder\n" +
                            "# 3. Restart the game, the mod will skip extracting specified models based on blacklist rules\n" +
                            "\n" +
                            "# 注意事项：\n" +
                            "# - default 模型采用特殊加载机制，无法通过黑名单禁用\n" +
                            "# - 配置文件位置：<游戏目录>/config/sparkle_morpher/blacklist.txt\n" +
                            "# - 以 # 开头的行被视为注释，不会被处理\n" +
                            "# - 每行一个规则，使用正则表达式匹配模型的完整解压路径\n" +
                            "#\n" +
                            "# Important Notes:\n" +
                            "# - The default model uses special loading mechanism and cannot be disabled via blacklist\n" +
                            "# - Config file location: <game_directory>/config/sparkle_morpher/blacklist.txt\n" +
                            "# - Lines starting with # are comments and will not be processed\n" +
                            "# - One rule per line, using regular expressions to match the complete extraction path of models\n" +
                            "\n" +
                            "# 路径匹配规则：\n" +
                            "# 模组解压时会使用以下格式的路径进行正则表达式匹配：\n" +
                            "#\n" +
                            "# Path Matching Rules:\n" +
                            "# The mod will use the following path formats for regular expression matching during extraction:\n" +
                            "#\n" +
                            "# assets/sparkle_morpher/builtin/default/ysm.json\n" +
                            "\n" +
                            "# 配置示例：\n" +
                            "# 重要提示：下面的示例都以 # 开头，这表示它们目前是注释状态，不会生效\n" +
                            "# 如果你想要启用某个规则，请删除该行开头的 # 号和空格\n" +
                            "#\n" +
                            "# Configuration Examples:\n" +
                            "# Important Notice: All examples below start with #, meaning they are currently commented out and inactive\n" +
                            "# To enable a rule, delete the # symbol and space at the beginning of that line\n" +
                            "\n" +
                            "# 示例1：禁用所有内置模型 | Example 1: Disable all built-in models\n" +
                            "# assets/sparkle_morpher/builtin/default/.*\n" +
                            "\n" +
                            "# 示例2：禁用所有模型 | Example 2: Disable all models\n" +
                            "# .*";
            Files.writeString(blacklistFile, content, StandardCharsets.UTF_8);
        }
        processBlacklist(blacklistFile);

        serverKey = com.micaftic.morpher.core.storage.ModelCacheKeyStore.loadOrCreate(CACHE_SERVER_INDEX_FILE);
    }

    private static void extractBuiltinModels() {
        // Every supported artifact carries this loader-independent resource index.
        try {
            if (!extractBuiltinModelsFromResourceIndex()) YesSteveModel.LOGGER.warn("[SM] No indexed builtin models were extracted");
        } catch (IOException failure) { YesSteveModel.LOGGER.error("[SM] Builtin extraction failed", failure); }
    }

    private static boolean extractBuiltinModelsFromResourceIndex() throws IOException {
        return com.micaftic.morpher.core.storage.IndexedBuiltinResources.extract(BUILT,
                BUILTIN_RESOURCE_ROOT, BUILTIN_RESOURCE_INDEX, YesSteveModel.class::getResourceAsStream) > 0;
    }

    private static void processBlacklist(Path blacklistFile) {
        List<Pattern> rules = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(blacklistFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    rules.add(Pattern.compile(line));
                } catch (PatternSyntaxException ignored) {
                }
            }
        } catch (IOException e) {
            return;
        }

        if (rules.isEmpty() || !Files.isDirectory(BUILT)) return;

        try (DirectoryStream<Path> groups = Files.newDirectoryStream(BUILT)) {
            for (Path group : groups) {
                if (!Files.isDirectory(group)) continue;
                boolean hasRemainingModels = false;
                try (DirectoryStream<Path> models = Files.newDirectoryStream(group)) {
                    for (Path model : models) {
                        if (!Files.isDirectory(model)) continue;

                        String matchPath = "assets/sparkle_morpher/builtin/" + group.getFileName() + "/" + model.getFileName() + "/";
                        boolean deleted = false;
                        for (Pattern rule : rules) {
                            if (rule.matcher(matchPath).find()) {
                                deleteRecursively(model);
                                deleted = true;
                                break;
                            }
                        }

                        if (!deleted) {
                            hasRemainingModels = true;
                        }
                    }
                }
                if (!hasRemainingModels) {
                    deleteRecursively(group);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            Files.deleteIfExists(dir);
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                deleteRecursively(entry);
            }
        }
        Files.deleteIfExists(dir);
    }

    private static void createFolder(Path path) {
        File folder = path.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(folder.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean nativeLoadModels(Object callback) {
        try {
            ModelLoadResult result = loadModelsSnapshot();
            onModelLoadComplete(result, callback);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Model loading failed", e);
            return false;
        }
    }

    private static ModelLoadResult loadModelsSnapshot() {
        Map<String, LocalModelDefinition> loadedModels = new LinkedHashMap<>();
        Set<String> authIds = new HashSet<>();
        Set<String> validCacheFiles = new HashSet<>();
        Map<String, LocalPackMetadata> loadedPacks = new LinkedHashMap<>();

        prepareBbmodelImportCache();
        scanDirectoryPacks(BUILT, loadedPacks);
        scanDirectoryPacks(CUSTOM, loadedPacks);
        scanDirectoryPacks(AUTH, loadedPacks);

        scanDirectoryModels(BUILT, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
        scanDirectoryModels(CUSTOM, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
        scanDirectoryModels(AUTH, CACHE_SERVER, loadedModels, authIds, validCacheFiles, true);
        // Legacy cache identities remain available for offline migration and rollback.
        return new ModelLoadResult(true, null, loadedModels, authIds.toArray(new String[0]), loadedPacks);
    }

    private static void scanDirectoryModels(Path baseDir, Path cacheDir, Map<String, LocalModelDefinition> loaded, Set<String> authIds, Set<String> validCaches, boolean isAuth) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return;

        // R8：遍历发现集中到 LocalModelScanner（纯 Java 可测，id 归一/kind 判定/文件夹判定统一）；
        // 解析与缓存仍在本类（server cache 语义），单条目失败 catch 后继续。
        try {
            LocalModelScanner.scan(baseDir, YSMFolderDeserializer::isModelFolder, hit -> {
                try {
                    RawYsmModel rawModel;
                    if (hit.kind() == LocalModelScanner.Kind.FOLDER) {
                        try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(hit.source())) {
                            rawModel = deserializer.deserialize();
                        }
                    } else {
                        byte[] raw = readModelFileBytes(hit.source());
                        rawModel = parseUploadedModel(raw, hit.source().toString(), hit.kind());
                    }
                    LocalModelDefinition data = processAndCacheModel(hit.modelId(), rawModel, cacheDir, isAuth, validCaches);
                    if (data != null) {
                        loaded.put(hit.modelId(), data);
                        if (isAuth) authIds.add(hit.modelId());
                    }
                } catch (Exception e) {
                    YesSteveModel.LOGGER.error("Failed to load model at: " + hit.source(), e);
                }
            });
        } catch (IOException e) {
            YesSteveModel.LOGGER.error("Failed to walk directory tree: " + baseDir, e);
        }
    }

    private static void scanDirectoryPacks(Path baseDir) {
        scanDirectoryPacks(baseDir, packs);
    }

    private static void scanDirectoryPacks(Path baseDir, Map<String, LocalPackMetadata> target) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return;
        try (var stream = Files.walk(baseDir, 1)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                if (path.equals(baseDir)) return;
                Path packJson = path.resolve("ysm-pack.json");
                if (Files.exists(packJson)) {
                    try {
                        // R8-4：pack 元数据解析集中到 LocalPackReader（纯 Java 可测）
                        LocalPackMetadata packData = LocalPackReader.read(baseDir, path);
                        target.put(packData.folderPath, packData);
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("Failed to load pack metadata: " + packJson, e);
                    }
                }
            });
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to walk directory for packs: " + baseDir, e);
        }
    }

    private static byte[] readModelFileBytes(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_MODEL_FILE_BYTES) {
            throw new IOException("Model file too large (" + size + " bytes), skipped: " + file);
        }
        try {
            return Files.readAllBytes(file);
        } catch (AccessDeniedException accessDenied) {
            try {
                File ioFile = file.toFile();
                if (!ioFile.canRead()) {
                    ioFile.setReadable(true, false);
                }
                try (FileInputStream in = new FileInputStream(ioFile)) {
                    return in.readAllBytes();
                }
            } catch (IOException | SecurityException fallbackError) {
                accessDenied.addSuppressed(fallbackError);
                throw accessDenied;
            }
        }
    }

    private static RawYsmModel parseBinaryModel(byte[] raw, String source) throws Exception {
        int ysmCryptoVersion = LegacyCompatModelFormat.detectCryptoVersion(raw);
        if (ysmCryptoVersion == -1) {
            throw new IllegalStateException("Unknown YSM crypto version for file: " + source);
        }

        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            Map<String, byte[]> input = LegacyCompatModelFormat.read(raw);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(input)) {
                return deserializer.deserialize();
            }
        }

        byte[] decrypted = YsmCrypt.decryptYsmFile(raw);
        try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(decrypted)) {
            RawYsmModel rawModel = deserializer.deserializeKeepOpen();
            deserializer.parseYSMFooter(rawModel);
            return rawModel;
        }
    }

    private static RawYsmModel parseArchiveModel(byte[] raw, String source) throws Exception {
        // 先嗅探 zip 内容：YSM 包走老路径，Figura/纯 bbmodel 包直接走 bbmodel 解析
        com.micaftic.morpher.resource.bbmodel.ZipModelSniffer sniff =
                com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.sniff(raw, 64L * 1024L * 1024L);
        switch (sniff.kind) {
            case FIGURA_AVATAR:
            case PLAIN_BBMODEL: {
                YesSteveModel.LOGGER.info(
                        "[SM] Server detected {} zip (bbmodel={}, textures={})",
                        sniff.kind == com.micaftic.morpher.resource.bbmodel.ZipModelSniffer.Kind.FIGURA_AVATAR ? "Figura avatar" : "bbmodel",
                        sniff.bbmodelPath, sniff.sideTextures.size());
                String json = new String(sniff.bbmodelBytes, java.nio.charset.StandardCharsets.UTF_8);
                com.micaftic.morpher.resource.bbmodel.BBModelFile bbmodel =
                        com.micaftic.morpher.resource.bbmodel.BBModelParser.parse(json);
                RawYsmModel rawModel = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.convert(bbmodel, sniff.sideTextures);
                rawModel.properties.sha256 = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheSha256(raw);
                return rawModel;
            }
            case YSM_FOLDER:
            case UNKNOWN:
            default:
                break;
        }
        Path temp = Files.createTempFile("ysm-import-", ".zip");
        try {
            Files.write(temp, raw);
            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(temp)) {
                return deserializer.deserialize();
            }
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to remove temporary model archive {}", temp, e);
            }
        }
    }

    private static RawYsmModel parseBbModelImport(byte[] raw, String source) throws Exception {
        try {
            String json = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            com.micaftic.morpher.resource.bbmodel.BBModelFile bbmodel =
                    com.micaftic.morpher.resource.bbmodel.BBModelParser.parse(json);
            RawYsmModel rawModel = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.convert(bbmodel);
            rawModel.properties.sha256 = com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheSha256(raw);
            return rawModel;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid bbmodel file: " + source, e);
        }
    }

    private static RawYsmModel parseUploadedModel(byte[] raw, String source, LocalModelScanner.Kind importKind) throws Exception {
        return switch (importKind) {
            case YSM -> parseBinaryModel(raw, source);
            case ZIP -> parseArchiveModel(raw, source);
            case BBMODEL -> parseBbModelImport(raw, source);
            case GLTF, GLB -> throw new IllegalArgumentException("glTF uploads are client-local only for now: " + source);
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported model import type for file: " + source);
            case FOLDER -> throw new IllegalArgumentException("Folder is not a file import: " + source);
        };
    }

    private static LocalModelDefinition processAndCacheModel(String modelId, RawYsmModel model, Path serverCacheDir, boolean isAuth, Set<String> validCacheFiles) {
        String sha256 = model.properties.sha256;
        if (sha256 == null || sha256.isEmpty() || serverKey == null) return null;

        try {
            // R8 遗留①：缓存引擎（哈希命名/校验/加密原子写）抽到 LocalModelDataCache
            long[] hashes = LocalModelDataCache.hashes(sha256, serverKey);
            String cacheFileName = LocalModelDataCache.fileName(hashes);
            Path cacheFile = serverCacheDir.resolve(cacheFileName);
            if (!serverCacheDir.toFile().isDirectory()) {
                Files.createDirectories(serverCacheDir);
            }
            boolean needsUpdate = true;
            if (Files.exists(cacheFile)) {
                byte[] existingData = Files.readAllBytes(cacheFile);
                if (LocalModelDataCache.isValid(existingData, hashes, serverKey)) {
                    needsUpdate = false;
                } else {
                    YesSteveModel.LOGGER.warn("[SM] Rebuilding unreadable server model cache: {}", modelId);
                }
            }
            if (needsUpdate) {
                byte[] serialized;
                try (YSMByteBuf serializedBuf = YSMBinarySerializer.serialize(model, 32, true)) {
                    io.netty.buffer.ByteBuf raw = serializedBuf.getRawBuf();
                    if (raw.hasArray()) {
                        int off = raw.arrayOffset() + raw.readerIndex();
                        int len = raw.readableBytes();
                        serialized = Arrays.copyOfRange(raw.array(), off, off + len);
                    } else {
                        serialized = serializedBuf.toArray();
                    }
                }
                // 原子写：先写临时文件再改名，避免进程中断/并发写/读写竞争产生半截缓存文件；
                // 半截文件会被原样发给客户端并转成带合法 trailer 的坏缓存，导致模型永远加载失败。
                // 写入是尽力而为：Windows 上目标文件正被并发读取（发送模型给玩家）时 replace
                // 会瞬时 AccessDenied，重试后仍失败则跳过——模型目录照常发布，发送路径会按需重建。
                try {
                    LocalModelDataCache.write(cacheFile, serialized, hashes, serverKey);
                } catch (Exception e) {
                    YesSteveModel.LOGGER.warn("[SM] Failed to update server cache file {} (will be rebuilt on demand): {}", cacheFileName, e.toString());
                }
            }
            validCacheFiles.add(cacheFileName);

            boolean isCustomSkinModel = "default".equals(modelId) || "misc/2_steve".equals(modelId) || "misc/1_alex".equals(modelId);

            return mapToDataClass(modelId, model, isAuth, isCustomSkinModel);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to process and cache model: " + modelId, e);
            return null;
        }
    }

    /**
     * 按内容哈希定位模型并重建兼容缓存文件（缓存损坏或缺失时从源模型重新生成）。
     */
    static boolean rebuildServerCacheByHashes(long hash1, long hash2) {
        for (Map.Entry<String, LocalModelDefinition> entry : CATALOG.all().entrySet()) {
            ModelMetadata info = entry.getValue().getLoadedModelData();
            if (info == null) continue;
            String sha = info.getModelHash();
            if (sha == null || sha.isEmpty()) continue;
            long[] hashes;
            try {
                hashes = YsmCrypt.calculateModelHashes(sha, serverKey);
            } catch (Exception e) {
                continue;
            }
            if (hashes[0] != hash1 || hashes[1] != hash2) continue;
            String modelId = entry.getKey();
            try {
                RawYsmModel raw = readSourceModelFor(modelId);
                if (raw == null) {
                    YesSteveModel.LOGGER.warn("[SM] Cannot rebuild server cache for {}: source model not found", modelId);
                    return false;
                }
                return processAndCacheModel(modelId, raw, CACHE_SERVER, entry.getValue().isAuth(), new HashSet<>()) != null;
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to rebuild server cache for {}", modelId, e);
                return false;
            }
        }
        return false;
    }

    /** 从 BUILT/CUSTOM/AUTH 源目录重新读取指定 modelId 的原始模型。 */
    @Nullable
    private static RawYsmModel readSourceModelFor(String modelId) {
        for (Path baseDir : new Path[]{BUILT, CUSTOM, AUTH}) {
            if (!Files.isDirectory(baseDir)) continue;
            try (Stream<Path> stream = Files.walk(baseDir)) {
                Optional<Path> hit = stream
                        .filter(p -> modelId.equals(LocalModelScanner.normalizeModelId(baseDir.relativize(p).toString().replace('\\', '/'))))
                        .findFirst();
                if (!hit.isPresent()) continue;
                Path source = hit.get();
                if (Files.isDirectory(source) && YSMFolderDeserializer.isModelFolder(source)) {
                    try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(source)) {
                        return deserializer.deserialize();
                    }
                }
                LocalModelScanner.Kind kind = LocalModelScanner.kindFromFileName(source.getFileName().toString());
                if (kind == LocalModelScanner.Kind.UNKNOWN) continue;
                return parseUploadedModel(readModelFileBytes(source), source.toString(), kind);
            } catch (Exception e) {
                YesSteveModel.LOGGER.warn("[SM] Failed to read source model {} for cache rebuild", modelId, e);
            }
        }
        return null;
    }


    private static LocalModelDefinition mapToDataClass(String modelId, RawYsmModel raw, boolean isAuth, boolean isCustomSkinModel) {
        ModelMetadata serverModelInfo = GuiConfigMapper.buildModelInfo(raw);
        // Animations
        Map<String, String[]> animMap = new HashMap<>();
        for (Map.Entry<String, RawYsmModel.RawAnimationFile> e : raw.mainEntity.animationFiles.entrySet()) {
            animMap.put(e.getKey(), e.getValue().animations.keySet().toArray(new String[0]));
        }
        String[] texArr = raw.mainEntity.textures.keySet().toArray(new String[0]);
        ServerAnimationInfo animInfo = new ServerAnimationInfo(animMap, texArr);

        // Sub Entities
        Object[] projectiles = raw.projectiles.values().stream().map(v -> v.matchIds != null ? v.matchIds : new String[]{v.identifier}).toArray();
        Object[] vehicles = raw.vehicles.values().stream().map(v -> v.matchIds != null ? v.matchIds : new String[]{v.identifier}).toArray();
        return new LocalModelDefinition(modelId, animInfo, projectiles, vehicles, serverModelInfo, isCustomSkinModel, isAuth);
    }

    public static void nativeExportModel(String modelID, @Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                LocalModelDefinition modelData = CATALOG.lookup(modelID);
                if (modelData == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, (Component) YSMComponentHelper.createTranslatableComponent("commands.sparkle_morpher.export.failure",new Object[]{": " + modelID + "\n Model not found"}), "", "", 0));
                    }
                    return;
                }

                String sha256 = modelData.getLoadedModelData().getModelHash();
                byte[] clearText = LocalModelDataCache.readCompatible(CACHE_SERVER, sha256, serverKey);

                int coreDataLength;
                try (YSMBinaryDeserializer deserializer = new YSMBinaryDeserializer(clearText, 32)) {
                    deserializer.deserializeKeepOpen();
                    coreDataLength = deserializer.getReader().getOffset();
                }

                try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                    outBuf.writeDword(32);
                    outBuf.getRawBuf().writeBytes(clearText, 0, coreDataLength);
                    outBuf.writeVarInt(32); // version
                    outBuf.writeVarInt(1);
                    byte[] randBytes = new byte[8];
                    theRandom.nextBytes(randBytes);
                    StringBuilder sb = new StringBuilder(16);
                    for (byte b : randBytes) {
                        sb.append(String.format("%02x", b));
                    }
                    outBuf.writeString(sb.toString());
                    outBuf.writeVarLong(java.time.Instant.now().getEpochSecond());
                    outBuf.writeString(extra != null ? extra : "");
                    outBuf.writeVarInt(0);
                    byte[] rawBytes = new byte[outBuf.getRawBuf().readableBytes()];
                    outBuf.getRawBuf().readBytes(rawBytes);
                    byte[] finalEncrypted = YsmCrypt.encryptYsmFile(rawBytes);
                    Path exportPath = EXPORT.resolve(modelID + ".ysm");
                    Files.createDirectories(exportPath.getParent());
                    Files.write(exportPath, finalEncrypted);
                    if (callback != null) {
                        String displayPath = Paths.get("export", modelID + ".ysm").toString();
                        callback.accept(new ExportResult(true, null, displayPath, "", 0));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.accept(new ExportResult(false, Component.literal("Export failed: " + e.getMessage()), "", "", 0));
                }
            }
        });
    }

    public static Optional<LocalModelDefinition> getModelDefinition(String str) {
        return Optional.ofNullable(CATALOG.lookupNormalized(str));
    }

    public static Map<String, LocalModelDefinition> getModelMetadata() {
        return CATALOG.all();
    }

    public static boolean isModelCatalogReady() {
        return initialized;
    }

    public static Set<String> getAuthModels() {
        return CATALOG.authModels();
    }

    private static void prepareBbmodelImportCache() {
        createFolder(CACHE);
        createFolder(CACHE_SERVER);
        String currentIdentity = YsmCrypt.getModelCacheIdentity()
                + "\nbbmodelImport=" + com.micaftic.morpher.resource.bbmodel.BBToRawConverter.importCacheIdentity();
        try {
            String existingIdentity = Files.exists(CACHE_BBMODEL_IMPORT_IDENTITY_FILE)
                    ? Files.readString(CACHE_BBMODEL_IMPORT_IDENTITY_FILE, StandardCharsets.UTF_8)
                    : "";
            if (!currentIdentity.equals(existingIdentity)) {
                // BBToRawConverter.importCacheSha256 includes the converter revision; old cache files remain available.
                Files.writeString(CACHE_BBMODEL_IMPORT_IDENTITY_FILE, currentIdentity, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to prepare bbmodel import cache", e);
        }
    }

    public static void initializeDefaults(com.micaftic.morpher.core.storage.LocalModelDefaultsStore.Defaults legacy) {
        defaultsWritable=false;
        try { defaults = com.micaftic.morpher.core.storage.LocalModelDefaultsStore.loadOrSeed(FOLDER.resolve("local-model-defaults.json"), legacy); defaultsWritable=true; }
        catch (IOException invalid) { YesSteveModel.LOGGER.warn("[SM] Local defaults retained after read failure", invalid); }
    }
    public static synchronized void saveDefaultModel(String modelId,String textureId)throws IOException{
        if(!defaultsWritable)throw new IOException("Local model defaults require repair before editing");
        var next=new com.micaftic.morpher.core.storage.LocalModelDefaultsStore.Defaults(modelId,textureId);
        com.micaftic.morpher.core.storage.LocalModelDefaultsStore.save(FOLDER.resolve("local-model-defaults.json"),next);defaults=next;
    }
    public static byte[] cacheKey() { return serverKey == null ? null : serverKey.clone(); }
    public static boolean loadModels(@Nullable Consumer<ModelLoadResult> consumer) {
        initialized = false;
        MODEL_RELOAD_COORDINATOR.submit(LocalModelService::loadModelsSnapshot,
                result -> onModelLoadComplete(result, consumer),
                error -> onModelLoadComplete(failedLoadResult(error), consumer));
        return true;
    }
    private static ModelLoadResult failedLoadResult(Throwable error) {
        return new ModelLoadResult(false, Component.literal(Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName())), null, null);
    }
    private static void publishModelLoadResult(ModelLoadResult result) { onModelLoadComplete(result, null); }
    @SuppressWarnings("unchecked")
    private static void onModelLoadComplete(ModelLoadResult result, @Nullable Object callback) {
        if (result.isSuccess()) {
            IntOpenHashSet hashes = new IntOpenHashSet(result.getModelDefinitions().size());
            for (LocalModelDefinition data : result.getModelDefinitions().values()) hashes.add(data.getLoadedModelData().getHashId());
            CATALOG.replaceAll(result.getModelDefinitions(), result.getAuthModelIds(), hashes);
            packs.clear(); packs.putAll(result.getPacks()); initialized = true;
        }
        if (callback != null) ((Consumer<ModelLoadResult>) callback).accept(result);
    }

    public static Pair<String, String> getDefaultModelConfig() {
        String defaultModelId = defaults.modelId();
        String defaultTexture = normalizeTextureId(defaults.textureId());
        if (!initialized) {
            return Pair.of(defaultModelId, defaultTexture);
        }
        String resolvedTexture = resolveTextureOrDefault(defaultModelId, defaultTexture);
        if (resolvedTexture == null) {
            return Pair.of("default", "default");
        }
        return Pair.of(defaultModelId, resolvedTexture);
    }

    @Nullable
    public static String resolveTextureOrDefault(String modelId, @Nullable String requestedTexture) {
        LocalModelDefinition modelData = CATALOG.lookupNormalized(modelId);
        if (modelData == null) {
            return null;
        }
        List<String> textures = modelData.getModelInfo().getTextures();
        if (textures.isEmpty()) {
            return null;
        }
        String normalizedRequested = normalizeTextureId(requestedTexture);
        if (normalizedRequested != null && textures.contains(normalizedRequested)) {
            return normalizedRequested;
        }
        String modelDefault = normalizeTextureId(modelData.getLoadedModelData().getModelProperties().getDefaultTexture());
        if (modelDefault != null && textures.contains(modelDefault)) {
            return modelDefault;
        }
        return textures.get(0);
    }

    @Nullable
    private static String normalizeTextureId(@Nullable String textureId) {
        if (textureId == null) {
            return null;
        }
        if (textureId.toLowerCase(Locale.ROOT).endsWith(".png") && textureId.length() > 4) {
            return textureId.substring(0, textureId.length() - 4);
        }
        return textureId;
    }

}
