package com.micaftic.morpher.model;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.client.ExportResult;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.core.model.ModelUploadSession;
import com.micaftic.morpher.core.model.catalog.LocalModelScanner;
import com.micaftic.morpher.core.storage.ModelStoragePaths;
import com.micaftic.morpher.mixin.ConnectionAccessor;
import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import com.micaftic.morpher.model.format.ServerAnimationInfo;
import com.micaftic.morpher.model.format.ServerModelData;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.model.catalog.ServerModelCatalog;
import com.micaftic.morpher.model.cache.ServerModelCache;
import com.micaftic.morpher.model.format.UUIDComponentData;
import com.micaftic.morpher.model.validation.UploadPolicy;
import com.micaftic.morpher.network.NetworkHandler;
import com.micaftic.morpher.network.message.S2CModelSyncPayload;
import com.micaftic.morpher.network.message.S2CSyncAuthModelsPacket;
import com.micaftic.morpher.resource.YSMBinaryDeserializer;
import com.micaftic.morpher.resource.YSMBinarySerializer;
import com.micaftic.morpher.resource.YSMClientMapper;
import com.micaftic.morpher.resource.YSMFolderDeserializer;
import com.micaftic.morpher.resource.pojo.RawYsmModel;
import com.micaftic.morpher.util.DigestUtil;
import com.micaftic.morpher.util.ModelIdUtil;
import com.micaftic.morpher.util.NetworkOnlineDebugLog;
import com.micaftic.morpher.util.PerformanceProfiler;
import com.micaftic.morpher.util.PlayerDataSaveBridge;
import com.micaftic.morpher.util.PlayerModelSelectionStore;
import com.micaftic.morpher.util.YSMComponentHelper;
import com.micaftic.morpher.util.YSMThreadPool;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.architectury.platform.Platform;
import dev.architectury.utils.GameInstance;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.floats.FloatReferencePair;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.micaftic.morpher.core.legacy.YesModelUtils;
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

public final class ServerModelManager {
    private static final long UPLOAD_SESSION_TIMEOUT_MS = 120_000L;
    private static final int UPLOAD_CHUNK_SIZE = 32_000;
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
     * R8-3：服务端模型目录状态（byName/authModels/modelHashes）收敛到 ServerModelCatalog。
     * 原 CACHE_NAME_INFO / AUTH_MODELS / modelHashSet 三个静态字段的整表替换 + 归一回退查询语义。
     */
    static final ServerModelCatalog<ServerModelData> CATALOG = new ServerModelCatalog<>();

        static final Map<String, ServerPackData> packs = new ConcurrentHashMap<>();
    private static final Map<Long, ModelUploadSession> uploadStates = new ConcurrentHashMap<>();
    static final SecureRandom theRandom = new SecureRandom();
    public static byte[] serverKey;
    private static volatile boolean initialized = false;

    private static RateLimiter bandwidthLimiter = null;
    private static boolean bandwidthLimitEnabled = true;
    private static int bandwidthLimitMbps = 5;
    static Semaphore threadLimiter = null;
    private static int threadLimit = -1;
    private static boolean limitsInitialized = false;

    static void initRateLimit() {
        try {
            boolean enabled = ServerConfig.ENABLE_GLOBAL_BANDWIDTH_LIMIT.get();
            int mbps = Math.max(1, ServerConfig.BANDWIDTH_LIMIT.get());
            if (!limitsInitialized || enabled != bandwidthLimitEnabled || mbps != bandwidthLimitMbps) {
                bandwidthLimitEnabled = enabled;
                bandwidthLimitMbps = mbps;
                bandwidthLimiter = enabled ? RateLimiter.create(Math.max(1.0, mbps * 131072.0)) : null;
            }
            int threads = ServerConfig.THREAD_COUNT.get();
            if (threads <= 0) {
                threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            }
            if (!limitsInitialized || threadLimiter == null || threads != threadLimit) {
                threadLimit = threads;
                threadLimiter = new Semaphore(threads);
            }
            limitsInitialized = true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to initialize limits from config", e);
            bandwidthLimitEnabled = true;
            bandwidthLimitMbps = 5;
            bandwidthLimiter = RateLimiter.create(5 * 131072.0);
            threadLimit = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
            threadLimiter = new Semaphore(threadLimit);
            limitsInitialized = true;
        }
    }

    static void acquireGlobalBandwidth(int bytes) {
        initRateLimit();
        RateLimiter limiter = bandwidthLimiter;
        if (bandwidthLimitEnabled && limiter != null && bytes > 0) {
            limiter.acquire(bytes);
        }
    }

    public static void reloadPacks() throws IOException {
        initialized = false;
        CATALOG.clear();

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
                "This directory is cleared every time the game starts!\n" +
                        "该目录会在每次游戏启动时清空！",
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
                            "# assets/sparkle_morpher/builtin/wine_fox/01_taisho_maid/animations/arrow.animation.json\n" +
                            "# assets/sparkle_morpher/builtin/wine_fox/01_taisho_maid/avatar/nico.png\n" +
                            "# assets/sparkle_morpher/builtin/misc/2_steve/ysm.json\n" +
                            "\n" +
                            "# 配置示例：\n" +
                            "# 重要提示：下面的示例都以 # 开头，这表示它们目前是注释状态，不会生效\n" +
                            "# 如果你想要启用某个规则，请删除该行开头的 # 号和空格\n" +
                            "#\n" +
                            "# Configuration Examples:\n" +
                            "# Important Notice: All examples below start with #, meaning they are currently commented out and inactive\n" +
                            "# To enable a rule, delete the # symbol and space at the beginning of that line\n" +
                            "\n" +
                            "# 示例1：禁用所有酒狐系列模型 | Example 1: Disable all Wine Fox series models\n" +
                            "# assets/sparkle_morpher/builtin/wine_fox/.*\n" +
                            "\n" +
                            "# 示例2：禁用杂项模型文件夹下的所有模型 | Example 2: Disable all models in misc folder\n" +
                            "# assets/sparkle_morpher/builtin/misc/.*\n" +
                            "\n" +
                            "# 示例3：禁用特定的大正女仆酒狐模型 | Example 3: Disable specific Taisho Maid Wine Fox model\n" +
                            "# assets/sparkle_morpher/builtin/wine_fox/01_taisho_maid/.*\n" +
                            "\n" +
                            "# 示例4：禁用所有内置模型 | Example 4: Disable all built-in models\n" +
                            "# .*";
            Files.writeString(blacklistFile, content, StandardCharsets.UTF_8);
        }
        processBlacklist(blacklistFile);

        Path serverIndex = CACHE_SERVER_INDEX_FILE;
        byte[] serverKeyBytes;

        if (Files.exists(serverIndex)) {
            try {
                String jsonStr = Files.readString(serverIndex, StandardCharsets.UTF_8);
                JsonObject jsonElement = JsonParser.parseString(jsonStr).getAsJsonObject();

                if (jsonElement.get("server_key") != null && jsonElement.get("server_key").getAsJsonPrimitive().isString()) {
                    serverKeyBytes = Base64.getDecoder().decode(jsonElement.get("server_key").getAsString());
                    if (serverKeyBytes.length != 56) {
                        throw new IllegalStateException("ServerKey length must be 56 bytes, but got " + serverKeyBytes.length);
                    }
                } else {
                    serverKeyBytes = new byte[56];
                    new SecureRandom().nextBytes(serverKeyBytes);
                    jsonElement.addProperty("server_key", Base64.getEncoder().encodeToString(serverKeyBytes));
                    Files.writeString(serverIndex, jsonElement.toString(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                serverKeyBytes = new byte[56];
                new SecureRandom().nextBytes(serverKeyBytes);
                JsonObject jsonElement = new JsonObject();
                jsonElement.addProperty("server_key", Base64.getEncoder().encodeToString(serverKeyBytes));
                Files.writeString(serverIndex, jsonElement.toString(), StandardCharsets.UTF_8);
            }
        } else {
            serverKeyBytes = new byte[56];
            new SecureRandom().nextBytes(serverKeyBytes);
            JsonObject jsonElement = new JsonObject();
            jsonElement.addProperty("server_key", Base64.getEncoder().encodeToString(serverKeyBytes));
            Files.writeString(serverIndex, jsonElement.toString(), StandardCharsets.UTF_8);
        }

        serverKey = serverKeyBytes;
        nativeLoadModels(null);
    }

    private static void extractBuiltinModels() {
        if (Files.isDirectory(BUILT)) {
            try (var s = Files.walk(BUILT)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    if (!p.equals(BUILT)) try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            } catch (IOException ignored) {}
        }
        try {
            Path assetsBuiltin = Platform.getMod(YesSteveModel.MOD_ID).findResource("assets", YesSteveModel.MOD_ID, "builtin").orElse(null);

            if (assetsBuiltin == null || !Files.isDirectory(assetsBuiltin)) return;

            try (Stream<Path> walker = Files.walk(assetsBuiltin)) {
                walker.forEach(src -> {
                    try {
                        Path relative = assetsBuiltin.relativize(src);
                        Path dest = ServerModelManager.BUILT.resolve(relative.toString());
                        if (Files.isDirectory(src)) {
                            Files.createDirectories(dest);
                        } else {
                            Files.createDirectories(dest.getParent());
                            try (InputStream in = Files.newInputStream(src)) {
                                Files.copy(in, dest);
                            }
                        }
                    } catch (IOException e) {
                        YesSteveModel.LOGGER.warn("Failed to extract builtin: " + src.getFileName(), e);
                    }
                });
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to extract builtin models", e);
        }
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

    // R8 遗留③：legacy 握手同步协议（PlayerSyncState/step 状态机/发包）迁至 LegacyModelSyncProtocol



    public static void nativeSendModelData(UUID uuid, @Nullable ByteBuffer data) {

        LegacyModelSyncProtocol.nativeSendModelData(uuid, data);

    }




    public static boolean nativeLoadModels(Object callback) {
        try {
            Map<String, ServerModelData> loadedModels = new LinkedHashMap<>();
            Set<String> authIds = new HashSet<>();
            Set<String> validCacheFiles = new HashSet<>();

            prepareBbmodelImportCache();
            packs.clear();
            scanDirectoryPacks(BUILT);
            scanDirectoryPacks(CUSTOM);
            scanDirectoryPacks(AUTH);

            scanDirectoryModels(BUILT, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(CUSTOM, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(AUTH, CACHE_SERVER, loadedModels, authIds, validCacheFiles, true);
            try (Stream<Path> stream = Files.list(CACHE_SERVER)) {
                stream.forEach(file -> {
                    if (!validCacheFiles.contains(file.getFileName().toString())) {
                        try { Files.deleteIfExists(file); } catch (Exception ignored) {}
                    }
                });
            } catch (Exception ignored) {}

            ModelLoadResult result = new ModelLoadResult(true, null, loadedModels, authIds.toArray(new String[0]));
            CATALOG.replaceAuth(authIds);

            onModelLoadComplete(result, callback);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Model loading failed", e);
            return false;
        }
    }

    private static void scanDirectoryModels(Path baseDir, Path cacheDir, Map<String, ServerModelData> loaded, Set<String> authIds, Set<String> validCaches, boolean isAuth) {
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
                    ServerModelData data = processAndCacheModel(hit.modelId(), rawModel, cacheDir, isAuth, validCaches);
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
        if (baseDir == null || !Files.isDirectory(baseDir)) return;
        try (var stream = Files.walk(baseDir, 1)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                if (path.equals(baseDir)) return;
                Path packJson = path.resolve("ysm-pack.json");
                if (Files.exists(packJson)) {
                    try {
                        // R8-4：pack 元数据解析集中到 ServerPackReader（纯 Java 可测）
                        ServerPackData packData = ServerPackReader.read(baseDir, path);
                        packs.put(packData.folderPath, packData);
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
        int ysmCryptoVersion = YesModelUtils.getYsmCryptoVersion(raw);
        if (ysmCryptoVersion == -1) {
            throw new IllegalStateException("Unknown YSM crypto version for file: " + source);
        }

        if (ysmCryptoVersion == 1 || ysmCryptoVersion == 2) {
            Map<String, byte[]> input = YesModelUtils.input(raw);
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
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported model import type for file: " + source);
            case FOLDER -> throw new IllegalArgumentException("Folder is not a file import: " + source);
        };
    }

    private static ServerModelData processAndCacheModel(String modelId, RawYsmModel model, Path serverCacheDir, boolean isAuth, Set<String> validCacheFiles) {
        String sha256 = model.properties.sha256;
        if (sha256 == null || sha256.isEmpty()) return null;

        try {
            // R8 遗留①：缓存引擎（哈希命名/校验/加密原子写）抽到 ServerModelCache（1.21.1 用 readStrict 严格校验）
            long[] hashes = ServerModelCache.hashes(sha256, serverKey);
            String cacheFileName = ServerModelCache.fileName(hashes);
            Path cacheFile = serverCacheDir.resolve(cacheFileName);
            if (!serverCacheDir.toFile().isDirectory()) {
                Files.createDirectories(serverCacheDir);
            }
            boolean needsUpdate = true;
            if (Files.exists(cacheFile)) {
                byte[] existingData = Files.readAllBytes(cacheFile);
                if (ServerModelCache.isValid(existingData, hashes, serverKey)) {
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
                    ServerModelCache.write(cacheFile, serialized, hashes, serverKey);
                } catch (Exception e) {
                    YesSteveModel.LOGGER.warn("[SM] Failed to update server cache file {} (will be rebuilt on demand): {}", cacheFileName, e.toString());
                }
            }
            validCacheFiles.add(cacheFileName);

            boolean isCustomSkinModel = "misc/2_steve".equals(modelId) || "misc/1_alex".equals(modelId); // 对没错就是写死的

            return mapToDataClass(modelId, model, isAuth, isCustomSkinModel);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to process and cache model: " + modelId, e);
            return null;
        }
    }

    /**
     * 按内容哈希定位模型并重建服务端缓存文件（发送路径自愈：缓存损坏/缺失时从源模型重新生成）。
     */
    static boolean rebuildServerCacheByHashes(long hash1, long hash2) {
        for (Map.Entry<String, ServerModelData> entry : CATALOG.all().entrySet()) {
            ServerModelInfo info = entry.getValue().getLoadedModelData();
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


    private static ServerModelData mapToDataClass(String modelId, RawYsmModel raw, boolean isAuth, boolean isCustomSkinModel) {
        ServerModelInfo serverModelInfo = YSMClientMapper.buildModelInfo(raw);
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
        return new ServerModelData(modelId, animInfo, projectiles, vehicles, serverModelInfo, isCustomSkinModel, isAuth);
    }

    // R8 遗留③：legacy 握手同步协议（nativeSyncModels/sendPacket03/sendPacket05）迁至 LegacyModelSyncProtocol



    public static void nativeSyncModels(UUID[] uuids, String[] playerNames, String[] modelIds, Object callback) {

        LegacyModelSyncProtocol.nativeSyncModels(uuids, playerNames, modelIds, callback);

    }




    public static void nativeExportModel(String modelID, @Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                ServerModelData modelData = CATALOG.lookup(modelID);
                if (modelData == null) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, (Component) YSMComponentHelper.createTranslatableComponent("commands.sparkle_morpher.export.failure",new Object[]{": " + modelID + "\n Model not found"}), "", "", 0));
                    }
                    return;
                }

                String sha256 = modelData.getLoadedModelData().getModelHash();
                long[] hashes = YsmCrypt.calculateModelHashes(sha256, serverKey);
                String cacheFileName = String.format("%016x%016x", hashes[0], hashes[1]);
                Path cacheFile = CACHE_SERVER.resolve(cacheFileName);

                if (!Files.exists(cacheFile)) {
                    if (callback != null) {
                        callback.accept(new ExportResult(false, Component.literal("Cache file missing for: " + modelID), "", "", 0));
                    }
                    return;
                }

                byte[] cacheData = Files.readAllBytes(cacheFile);
                byte[] clearText = YsmCrypt.read(cacheData, serverKey);

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

    public static Optional<ServerModelData> getModelDefinition(String str) {
        return Optional.ofNullable(CATALOG.lookupNormalized(str));
    }

    public static Map<String, ServerModelData> getServerModelInfo() {
        return CATALOG.all();
    }

    public static boolean isModelCatalogReady() {
        return initialized;
    }

    public static Set<String> getAuthModels() {
        return CATALOG.authModels();
    }

    public static boolean isModelUploadAllowed() {
        try {
            return ServerConfig.ALLOW_MODEL_UPLOAD.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int getModelUploadMaxBytes() {
        try {
            return Math.max(1, ServerConfig.MODEL_UPLOAD_MAX_MB.get()) * 1024 * 1024;
        } catch (IllegalStateException e) {
            return 128 * 1024 * 1024;
        }
    }

    public static int getModelUploadChunksPerTick() {
        try {
            return Math.max(1, ServerConfig.MODEL_UPLOAD_CHUNKS_PER_TICK.get());
        } catch (IllegalStateException e) {
            return 4;
        }
    }

    public static UploadStartResult beginModelUpload(ServerPlayer sender, String requestedModelId, String fileName, int totalBytes, String sha256) {
        cleanupExpiredUploads();
        // R8 遗留②：上传校验链抽到 UploadPolicy（纯判定 + 拒绝码/消息）
        String modelId = normalizeUploadedModelId(requestedModelId);
        LocalModelScanner.Kind importKind = LocalModelScanner.kindFromFileName(fileName);
        int maxBytes = getModelUploadMaxBytes();
        UploadPolicy.RejectReason reason = UploadPolicy.validate(
                isModelUploadAllowed(),
                sender != null && NetworkHandler.isPlayerConnected(sender),
                modelId,
                importKind != LocalModelScanner.Kind.UNKNOWN,
                sha256,
                totalBytes,
                maxBytes,
                CATALOG.contains(modelId) || uploadStates.values().stream().anyMatch(state -> state.modelId().equals(modelId)));
        if (reason != UploadPolicy.RejectReason.NONE) {
            return UploadStartResult.reject(UploadPolicy.statusCode(reason), UploadPolicy.statusMessage(reason));
        }

        long uploadId;
        do {
            uploadId = theRandom.nextLong();
        } while (uploadId == 0L || uploadStates.containsKey(uploadId));

        ModelUploadSession state = new ModelUploadSession(uploadId, sender.getUUID(), modelId, fileName, importKind, totalBytes, sha256.toLowerCase(Locale.ROOT));
        uploadStates.put(uploadId, state);
        return new UploadStartResult(uploadId, (byte) 0, UPLOAD_CHUNK_SIZE, maxBytes, getModelUploadChunksPerTick(), "");
    }

    public static void receiveModelUploadChunk(ServerPlayer sender, long uploadId, int offset, byte[] data) {
        ModelUploadSession state = uploadStates.get(uploadId);
        if (state == null || sender == null || !state.owner().equals(sender.getUUID())) {
            return;
        }
        acquireGlobalBandwidth(data == null ? 0 : data.length);
        // R8-5：接收进度推进/校验集中到 ModelUploadSession（含 touch 与 failed 标记）
        state.appendChunk(offset, data);
    }

    public static UploadFinishResult finishModelUpload(ServerPlayer sender, long uploadId) {
        long finishPerfStart = PerformanceProfiler.start();
        ModelUploadSession state = uploadStates.remove(uploadId);
        if (state == null || sender == null || !state.owner().equals(sender.getUUID())) {
            return UploadFinishResult.reject(uploadId, (byte) 4, "Session expired");
        }
        if (!state.isComplete()) {
            return UploadFinishResult.reject(uploadId, (byte) 5, "Incomplete upload");
        }
        String actualSha256 = DigestUtil.sha256Hex(state.data());
        if (!state.sha256().equals(actualSha256)) {
            YesSteveModel.LOGGER.warn("[SM] Import transfer hash mismatch modelId={} file={} type={} declaredSha256={} actualSha256={} bytes={} received={}",
                    state.modelId(), state.fileName(), state.importKind(), state.sha256(), actualSha256, state.data().length, state.receivedBytes());
            return UploadFinishResult.reject(uploadId, (byte) 1, "Hash mismatch");
        }

        RawYsmModel rawModel;
        try {
            rawModel = parseUploadedModel(state.data(), "import:" + state.fileName(), state.importKind());
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to parse imported model modelId={} file={} type={} rawSha256={} bytes={}",
                    state.modelId(), state.fileName(), state.importKind(), actualSha256, state.data().length, e);
            return UploadFinishResult.reject(uploadId, (byte) 2, e.getMessage());
        }

        try {
            if (processAndCacheModel(state.modelId(), rawModel, CACHE_SERVER, false, new HashSet<>()) == null) {
                return UploadFinishResult.reject(uploadId, (byte) 2, "Server failed to cache model");
            }
            Path target = CUSTOM.resolve(state.modelId() + LocalModelScanner.extensionFor(state.importKind())).normalize();
            Path customRoot = CUSTOM.toAbsolutePath().normalize();
            Path absoluteTarget = target.toAbsolutePath().normalize();
            if (!absoluteTarget.startsWith(customRoot)) {
                return UploadFinishResult.reject(uploadId, (byte) 6, "Server rejected write");
            }
            Files.createDirectories(absoluteTarget.getParent());
            Path temp = Files.createTempFile(absoluteTarget.getParent(), absoluteTarget.getFileName().toString(), ".tmp");
            Files.write(temp, state.data());
            try {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to store imported model: " + state.modelId(), e);
            return UploadFinishResult.reject(uploadId, (byte) 3, e.getMessage());
        }

        ModelLoadResult reloadResult = reloadModelsAfterImport();
        if (!reloadResult.isSuccess()) {
            Component errorMessage = reloadResult.getErrorMessage();
            return UploadFinishResult.reject(uploadId, (byte) 8, errorMessage == null ? "Imported model scan failed" : errorMessage.getString());
        }
        if (!reloadResult.getModelDefinitions().containsKey(state.modelId())) {
            YesSteveModel.LOGGER.warn("[SM] Imported model was written but not visible after scan: modelId={} file={} type={} rawSha256={} contentHash={}",
                    state.modelId(), state.fileName(), state.importKind(), actualSha256, rawModel.properties.sha256);
            return UploadFinishResult.reject(uploadId, (byte) 8, "Imported model is not visible after scan");
        }

        YesSteveModel.LOGGER.info("[SM] Imported model '{}' from {} as {}", state.modelId(), sender.getGameProfile().getName(), state.importKind());
        PerformanceProfiler.logElapsed("server_upload_finish", state.modelId(), finishPerfStart,
                "bytes=" + state.data().length + " type=" + state.importKind());
        long[] hashes = YsmCrypt.calculateModelHashes(rawModel.properties.sha256, serverKey);
        return new UploadFinishResult(uploadId, (byte) 0, state.modelId(), hashes[0], hashes[1], "");
    }

    private static ModelLoadResult reloadModelsAfterImport() {
        long perfStart = PerformanceProfiler.start();
        Map<String, ServerModelData> loadedModels = new LinkedHashMap<>();
        Set<String> authIds = new HashSet<>();
        Set<String> validCacheFiles = new HashSet<>();

        try {
            prepareBbmodelImportCache();
            packs.clear();
            scanDirectoryPacks(BUILT);
            scanDirectoryPacks(CUSTOM);
            scanDirectoryPacks(AUTH);

            scanDirectoryModels(BUILT, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(CUSTOM, CACHE_SERVER, loadedModels, authIds, validCacheFiles, false);
            scanDirectoryModels(AUTH, CACHE_SERVER, loadedModels, authIds, validCacheFiles, true);
            cleanupServerCache(validCacheFiles);
            ModelLoadResult result = new ModelLoadResult(true, null, loadedModels, authIds.toArray(new String[0]));
            onModelLoadComplete(result, null);
            syncLoadedModelsToPlayers();
            PerformanceProfiler.logElapsed("server_reload_after_import", null, perfStart,
                    "models=" + loadedModels.size() + " auth=" + authIds.size());
            return result;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to reload models after import", e);
            return new ModelLoadResult(false, Component.literal(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null, null);
        }
    }

    private static void cleanupServerCache(Set<String> validCacheFiles) {
        try (Stream<Path> stream = Files.list(CACHE_SERVER)) {
            stream.forEach(file -> {
                if (!validCacheFiles.contains(file.getFileName().toString())) {
                    try {
                        Files.deleteIfExists(file);
                    } catch (Exception ignored) {
                    }
                }
            });
        } catch (Exception ignored) {
        }
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
                clearServerModelCache("bbmodel import cache identity changed");
                Files.writeString(CACHE_BBMODEL_IMPORT_IDENTITY_FILE, currentIdentity, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to prepare bbmodel import cache", e);
        }
    }

    private static void clearServerModelCache(String reason) {
        if (!Files.isDirectory(CACHE_SERVER)) {
            return;
        }
        int removed = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(CACHE_SERVER)) {
            for (Path entry : entries) {
                deleteRecursively(entry);
                removed++;
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Failed to clear server model cache ({})", reason, e);
        }
        if (removed > 0) {
            YesSteveModel.LOGGER.info("[SM] Cleared {} server model cache entries ({})", removed, reason);
        }
    }

    private static void syncLoadedModelsToPlayers() {
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer == null) {
            return;
        }
        currentServer.execute(() -> {
            List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
            for (ServerPlayer player : players) {
                PlayerModelSelectionStore.restore(player);
                validatePlayerModel(player);
            }
            nativeSyncModels(players.stream().filter(NetworkHandler::isPlayerConnected).map(ServerPlayer::getUUID).toArray(UUID[]::new),
                    players.stream().filter(NetworkHandler::isPlayerConnected).map(serverPlayer -> serverPlayer.getGameProfile().getName()).toArray(String[]::new),
                    collectPlayerModelIds(players),
                    null);
        });
    }

    @Nullable
    private static String normalizeUploadedModelId(@Nullable String modelId) {
        String normalized = ModelIdUtil.normalizeImportModelId(modelId);
        boolean stripped;
        do {
            stripped = false;
            for (String extension : new String[]{EXT_YSM, EXT_ZIP, EXT_BBMODEL}) {
                if (normalized.endsWith(extension)) {
                    normalized = normalized.substring(0, normalized.length() - extension.length());
                    stripped = true;
                }
            }
        } while (stripped);
        normalized = ModelIdUtil.normalizeImportModelId(normalized);
        if (!ModelIdUtil.isValidModelId(normalized)) {
            return null;
        }
        return normalized;
    }

    private static void cleanupExpiredUploads() {
        long now = System.currentTimeMillis();
        uploadStates.entrySet().removeIf(entry -> entry.getValue().isExpired(now, UPLOAD_SESSION_TIMEOUT_MS));
    }

    public static void requestPlayerAuth(ServerPlayer serverPlayer, @Nullable Consumer<UUIDComponentData> consumer) {
        MinecraftServer currentServer = GameInstance.getServer();
        currentServer.execute(() -> {
            List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
            ArrayList<FloatReferencePair<ServerPlayer>> arrayList = new ArrayList<>();
            for (ServerPlayer serverPlayer2 : players) {
                if (serverPlayer2.level().dimensionType() == serverPlayer.level().dimensionType()) {
                    arrayList.add(FloatReferencePair.of(serverPlayer2.distanceTo(serverPlayer), serverPlayer2));
                }
            }
            arrayList.sort((a, b) -> Float.compare(a.firstFloat(), b.firstFloat()));
            nativeSyncModels(new UUID[]{serverPlayer.getUUID()}, new String[]{serverPlayer.getGameProfile().getName()}, collectPlayerModelIds(arrayList.stream().map(it.unimi.dsi.fastutil.Pair::second).toList()), consumer);
        });
    }

    public static boolean loadModels(@Nullable Consumer<ModelLoadResult> consumer, @Nullable Consumer<UUIDComponentData> consumer2) {
        Consumer<ModelLoadResult> action = modelLoadResult -> {
            if (consumer != null) {
                consumer.accept(modelLoadResult);
            }
            MinecraftServer currentServer = GameInstance.getServer();
            if (currentServer == null) {
                return;
            }
            currentServer.execute(() -> {
                List<ServerPlayer> players = currentServer.getPlayerList().getPlayers();
                for (ServerPlayer value : players) {
                    PlayerModelSelectionStore.restore(value);
                    validatePlayerModel(value);
                }
                nativeSyncModels(players.stream().filter(NetworkHandler::isPlayerConnected).map((player) -> player.getUUID()).toArray(i -> new UUID[i]), players.stream().filter(NetworkHandler::isPlayerConnected).map(serverPlayer -> serverPlayer.getGameProfile().getName()).toArray(i2 -> new String[i2]), collectPlayerModelIds(players), consumer2);
            });
        };
        return nativeLoadModels(action);
    }

    private static String[] collectPlayerModelIds(Collection<ServerPlayer> collection) {
        return collection.stream().filter(NetworkHandler::isPlayerConnected).map(serverPlayer -> ModelInfoCapability.get(serverPlayer).map(ModelInfoCapability::getModelId)).filter(Optional::isPresent).map(Optional::get).distinct().toArray(String[]::new);
    }

    private static void onModelLoadComplete(ModelLoadResult modelLoadResult, @Nullable Object obj) {
        Consumer<ModelLoadResult> consumer = (Consumer<ModelLoadResult>) obj;
        MinecraftServer currentServer = GameInstance.getServer();
        if (currentServer != null) {
            currentServer.execute(() -> {
                if (modelLoadResult.isSuccess()) {
                    IntOpenHashSet intOpenHashSet = new IntOpenHashSet(modelLoadResult.getModelDefinitions().size());
                    for (ServerModelData data : modelLoadResult.getModelDefinitions().values()) {
                        intOpenHashSet.add(data.getLoadedModelData().getHashId());
                    }
                    CATALOG.replaceAll(modelLoadResult.getModelDefinitions(), modelLoadResult.getAuthModelIds(), intOpenHashSet);
                    initialized = true;
                }
                if (consumer != null) {
                    YSMThreadPool.submit(() -> consumer.accept(modelLoadResult));
                }
            });
            return;
        }
        if (modelLoadResult.isSuccess()) {
            CATALOG.replaceDefinitionsAndAuth(modelLoadResult.getModelDefinitions(), modelLoadResult.getAuthModelIds());
            initialized = true;
        }
        if (consumer != null) {
            consumer.accept(modelLoadResult);
        }
    }

    public static void syncModelToPlayer(UUID uuid) {
        nativeSendModelData(uuid, null);
    }

    // R8 遗留③：legacy 同步会话清理/发送 helper（getPlayerConnection/sendModelData/等）迁至 LegacyModelSyncProtocol



    public static void clearPlayerSyncState(UUID uuid) {

        LegacyModelSyncProtocol.clearPlayerSyncState(uuid);

    }




    public static Pair<String, String> getDefaultModelConfig() {
        String defaultModelId = ServerConfig.DEFAULT_MODEL_ID.get();
        String defaultTexture = normalizeTextureId(ServerConfig.DEFAULT_MODEL_TEXTURE.get());
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
        ServerModelData modelData = CATALOG.lookupNormalized(modelId);
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

    private static void onAuthDataReceived(UUIDComponentData uuidComponentData, @Nullable Object obj) {
        Consumer consumer = (Consumer) obj;
        if (consumer != null) {
            consumer.accept(uuidComponentData);
        }
    }

    public static void validatePlayerModel(ServerPlayer serverPlayer) {
        if (!initialized) {
            NetworkOnlineDebugLog.info("validatePlayerModel: SKIP catalog_not_ready");
            return;
        }
        NetworkOnlineDebugLog.info("validatePlayerModel: {} cacheEmpty={} cacheSize={}",
                serverPlayer.getName().getString(), CATALOG.isEmpty(), CATALOG.size());
        if (!CATALOG.isEmpty()) {
            ModelInfoCapability.get(serverPlayer).ifPresent(modelInfoCap -> {
                AuthModelsCapability.get(serverPlayer).ifPresent(authModelsCap -> {
                    if (authModelsCap.getAuthModels().removeIf(str -> getModelDefinition(str).isEmpty())) {
                        NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(authModelsCap.getAuthModels()), serverPlayer);
                    }
                    String selectedModelId = modelInfoCap.getModelId();
                    String modelId = CATALOG.contains(selectedModelId) ? selectedModelId : ModelIdUtil.normalizeImportModelId(selectedModelId);
                    boolean inCache = getServerModelInfo().containsKey(modelId);
                    boolean isAuth = CATALOG.authModels().contains(modelId);
                    boolean hasAuth = authModelsCap.containsModel(selectedModelId) || authModelsCap.containsModel(modelId);
                    NetworkOnlineDebugLog.info("validate: modelId={} inCache={} isAuth={} hasAuth={}",
                            modelId, inCache, isAuth, hasAuth);
                    boolean changed = false;
                    if (!inCache || (isAuth && !hasAuth)) {
                        NetworkOnlineDebugLog.info("validate: RESET_TO_DEFAULT reason={}", !inCache ? "not_in_cache" : "no_auth");
                        modelInfoCap.resetToDefault();
                        changed = true;
                    } else {
                        String resolvedTexture = resolveTextureOrDefault(modelId, modelInfoCap.getSelectTexture());
                        if (resolvedTexture == null) {
                            NetworkOnlineDebugLog.info("validate: RESET_TO_DEFAULT reason=texture_null");
                            modelInfoCap.resetToDefault();
                            changed = true;
                        } else if (!resolvedTexture.equals(modelInfoCap.getSelectTexture())) {
                            NetworkOnlineDebugLog.info("validate: TEXTURE_CHANGE {} -> {}", modelInfoCap.getSelectTexture(), resolvedTexture);
                            modelInfoCap.setModelAndTexture(modelId, resolvedTexture);
                            changed = true;
                        } else if (!modelId.equals(selectedModelId)) {
                            NetworkOnlineDebugLog.info("validate: MODEL_ID_NORMALIZE {} -> {}", selectedModelId, modelId);
                            modelInfoCap.setModelAndTexture(modelId, resolvedTexture);
                            changed = true;
                        } else {
                            NetworkOnlineDebugLog.info("validate: OK modelId={}", modelId);
                        }
                    }
                    if (changed) {
                        PlayerModelSelectionStore.saveCurrentSelection(serverPlayer, modelInfoCap);
                        PlayerDataSaveBridge.save(serverPlayer);
                    }
                    modelInfoCap.retainAnimationKeys(CATALOG.modelHashes());
                });
            });
        } else {
            NetworkOnlineDebugLog.info("validatePlayerModel: SKIP cache_empty");
        }
    }

    public record UploadStartResult(long uploadId, byte status, int chunkSize, int maxTotalBytes, int chunksPerTick, String message) {
        private static UploadStartResult reject(byte status, String message) {
            return new UploadStartResult(0L, status, UPLOAD_CHUNK_SIZE, getModelUploadMaxBytes(), getModelUploadChunksPerTick(), message);
        }
    }

    public record UploadFinishResult(long uploadId, byte status, String modelId, long hash1, long hash2, String message) {
        private static UploadFinishResult reject(long uploadId, byte status, String message) {
            return new UploadFinishResult(uploadId, status, "", 0L, 0L, message);
        }
    }

}
