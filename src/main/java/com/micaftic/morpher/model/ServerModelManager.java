package com.micaftic.morpher.model;

import com.micaftic.morpher.YesSteveModel;
import com.micaftic.morpher.capability.AuthModelsCapability;
import com.micaftic.morpher.capability.ModelInfoCapability;
import com.micaftic.morpher.client.ExportResult;
import com.micaftic.morpher.config.ServerConfig;
import com.micaftic.morpher.mixin.ConnectionAccessor;
import com.micaftic.morpher.mixin.ServerCommonPacketListenerImplAccessor;
import com.micaftic.morpher.model.format.ServerAnimationInfo;
import com.micaftic.morpher.model.format.ServerModelData;
import com.micaftic.morpher.model.format.ServerModelInfo;
import com.micaftic.morpher.model.format.UUIDComponentData;
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

    /**
     * 配置相关文件夹
     */
    public static final Path FOLDER = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(YesSteveModel.MOD_ID);

    /**
     * 自定义模型所放置的文件夹
     */
    public static final Path BUILT = FOLDER.resolve("built");
    public static final Path CUSTOM = FOLDER.resolve("custom");
    public static final Path AUTH = FOLDER.resolve("auth");
    public static final Path EXPORT = FOLDER.resolve("export");

    /**
     * 生成缓存文件的文件夹
     */
    public static final Path CACHE = FOLDER.resolve("cache");
    public static final Path CACHE_SERVER_INDEX_FILE = CACHE.resolve("server_index");
    public static final Path CACHE_SERVER = CACHE.resolve("server");
    public static final Path CACHE_CLIENT = CACHE.resolve("client");

    /**
     * 模型名称 -> 模型额外信息缓存
     * 可以方便的通过此缓存，来判断客户端发来的 MD5 在不在服务端
     * 从而将服务器文件发送给玩家
     * 还可以获取其他服务端模型信息
     */
    private static Map<String, ServerModelData> CACHE_NAME_INFO = Maps.newHashMap();

    private static IntOpenHashSet modelHashSet = new IntOpenHashSet();

    /**
     * 放置授权模型名称
     */
    private static Set<String> AUTH_MODELS = Sets.newHashSet();

    private static final Map<UUID, PlayerSyncState> syncStates = new ConcurrentHashMap<>();
    private static final Map<String, ServerPackData> packs = new ConcurrentHashMap<>();
    private static final Map<Long, ModelUploadState> uploadStates = new ConcurrentHashMap<>();
    private static final SecureRandom theRandom = new SecureRandom();
    public static volatile byte[] serverKey;
    private static volatile boolean initialized = false;

    private static RateLimiter bandwidthLimiter = null;
    private static Semaphore threadLimiter = null;
    private static boolean limitsInitialized = false;

    private static void initRateLimit() {
        if (!limitsInitialized) {
            try {
                int mbps = ServerConfig.BANDWIDTH_LIMIT.get();
                double bytesPerSec = Math.max(1.0, mbps * 131072.0);
                bandwidthLimiter = RateLimiter.create(bytesPerSec);

                int threads = ServerConfig.THREAD_COUNT.get();
                if (threads <= 0) {
                    threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
                }
                threadLimiter = new Semaphore(threads);

                limitsInitialized = true;
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Failed to initialize limits from config", e);
                bandwidthLimiter = RateLimiter.create(5 * 131072.0);
                threadLimiter = new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
                limitsInitialized = true;
            }
        }
    }

    public static class ServerPackData {
        public String folderPath;
        public byte[] iconData;
        public int iconWidth, iconHeight, iconFormat;
        public String name;
        public String description;
        public Map<String, Map<String, String>> lang;
    }

    public static void reloadPacks() throws IOException {
        CACHE_NAME_INFO.clear();
        AUTH_MODELS.clear();

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
            final Path assetsBuiltinFinal;
            try { assetsBuiltinFinal = net.neoforged.fml.loading.FMLPaths.MODSDIR.get().resolve("assets").resolve(YesSteveModel.MOD_ID).resolve("builtin"); } catch (Exception e) { return; }

            if (assetsBuiltinFinal == null || !Files.isDirectory(assetsBuiltinFinal)) return;

            try (Stream<Path> walker = Files.walk(assetsBuiltinFinal)) {
                walker.forEach(src -> {
                    try {
                        Path relative = assetsBuiltinFinal.relativize(src);
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

    static class PlayerSyncState {
        byte[] clientKey = new byte[56];
        byte[] key1;
        byte[] clientNextKey;
        int step = 0;
        List<ServerModelData> allowedModels = new ArrayList<>();

        // TODO: 未来可基于UUID持久化，这里目前每次加入生成固定clientKey
        PlayerSyncState() {new Random(114514).nextBytes(clientKey);}
    }

    public static void nativeSendModelData(UUID uuid, @Nullable ByteBuffer data) {
        if (data != null && !data.hasRemaining() && data.position() > 0) {
            data.flip();
        }

        if (data == null || data.remaining() == 0) {
            syncStates.remove(uuid);
            return;
        }

        PlayerSyncState state = syncStates.get(uuid);
        if (state == null) return;

        try {
            byte[] packetBytes = new byte[data.remaining()];
            data.get(packetBytes);
            System.out.println("Server Handle packet, step=" + state.step + ", length=" + packetBytes.length);

            if (state.step == 1) {
                // 等待Pong
                byte[] decrypted = YsmCrypt.decrypt(packetBytes, state.key1);
                if (decrypted == null || decrypted.length < 56) return;

                // 客戶端生成的密鑰
                state.clientNextKey = Arrays.copyOfRange(decrypted, decrypted.length - 56, decrypted.length);
                byte[] payload = Arrays.copyOfRange(decrypted, 0, decrypted.length - 56);

                try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(payload))) {
                    buf.skipGarbageHeader();
                    if (buf.getRawBuf().readByte() != 0x02) return;
                }

                // 發送可用模型
                state.step = 2;
                sendPacket03(uuid, state);
            } else if (state.step == 2) {
                byte[] decrypted = YsmCrypt.decrypt(packetBytes, state.key1);
                if (decrypted == null) return;

                try (YSMByteBuf buf = new YSMByteBuf(Unpooled.wrappedBuffer(decrypted))) {
                    buf.skipGarbageHeader();
                    if (buf.getRawBuf().readByte() != 0x04) return;

                    int numRequests = buf.readVarInt();
                    List<long[]> requestedHashes = new ArrayList<>();
                    for (int i = 0; i < numRequests; i++) {
                        requestedHashes.add(new long[]{buf.readVarLong(), buf.readVarLong()});
                    }
                    state.step = 3;
                    sendPacket05(uuid, state, requestedHashes);
                }
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Server sync error for " + uuid, e);
        }
    }

    public static boolean nativeLoadModels(Object callback) {
        try {
            Map<String, ServerModelData> loadedModels = new LinkedHashMap<>();
            Set<String> authIds = new HashSet<>();
            Set<String> validCacheFiles = new HashSet<>();

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
            AUTH_MODELS = authIds;

            onModelLoadComplete(result, callback);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Model loading failed", e);
            return false;
        }
    }

    private static void scanDirectoryModels(Path baseDir, Path cacheDir, Map<String, ServerModelData> loaded, Set<String> authIds, Set<String> validCaches, boolean isAuth) {
        if (baseDir == null || !Files.isDirectory(baseDir)) return;

        try {
            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) {
                    if (dir.equals(baseDir)) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        if (YSMFolderDeserializer.isModelFolder(dir)) {
                            String modelId = baseDir.relativize(dir).toString().replace('\\', '/');

                            RawYsmModel rawModel = null;
                            try (YSMFolderDeserializer deserializer = new YSMFolderDeserializer(dir)) {
                                rawModel = deserializer.deserialize();
                            } catch (Exception e) {
                                YesSteveModel.LOGGER.error("Failed to load model at: " + dir, e);
                            }

                            if (rawModel != null) {
                                try {
                                    ServerModelData data = processAndCacheModel(modelId, rawModel, cacheDir, isAuth, validCaches);
                                    rawModel = null;
                                    if (data != null) {
                                        loaded.put(modelId, data);
                                        if (isAuth) authIds.add(modelId);
                                    }
                                } catch (Exception e) {
                                    YesSteveModel.LOGGER.error("Failed to process model at: " + dir, e);
                                }
                            }

                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("Error checking directory: " + dir, e);
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                    ImportKind importKind = importKindFromFileName(file.getFileName().toString());
                    if (importKind == ImportKind.UNKNOWN) return FileVisitResult.CONTINUE;
                    try {
                        String modelId = stripImportExtension(baseDir.relativize(file).toString().replace('\\', '/'));
                        byte[] raw = readModelFileBytes(file);
                        RawYsmModel rawModel = parseUploadedModel(raw, file.toString(), importKind);

                        ServerModelData data = processAndCacheModel(modelId, rawModel, cacheDir, isAuth, validCaches);
                        if (data != null) {
                            loaded.put(modelId, data);
                            if (isAuth) authIds.add(modelId);
                        }
                    } catch (Exception e) {
                        YesSteveModel.LOGGER.error("Failed to load imported model at: " + file, e);
                    }
                    return FileVisitResult.CONTINUE;
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
                        ServerPackData packData = new ServerPackData();
                        packData.folderPath = baseDir.toFile().toURI().relativize(path.toFile().toURI()).getPath();

                        String jsonStr = Files.readString(packJson, StandardCharsets.UTF_8);
                        JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                        if (json.has("name")) packData.name = json.get("name").getAsString();
                        if (json.has("description")) packData.description = json.get("description").getAsString();

                        if (json.has("lang") && json.get("lang").isJsonObject()) {
                            packData.lang = new HashMap<>();
                            JsonObject langObj = json.getAsJsonObject("lang");
                            for (Map.Entry<String, JsonElement> entry : langObj.entrySet()) {
                                if (entry.getValue().isJsonObject()) {
                                    Map<String, String> translations = new HashMap<>();
                                    for (Map.Entry<String, JsonElement> transEntry : entry.getValue().getAsJsonObject().entrySet()) {
                                        translations.put(transEntry.getKey(), transEntry.getValue().getAsString());
                                    }
                                    packData.lang.put(entry.getKey(), translations);
                                }
                            }
                        }

                        Path packPng = path.resolve("ysm-pack.png");
                        if (Files.exists(packPng)) {
                            byte[] data = Files.readAllBytes(packPng);
                            int[] dims = getPngDimensions(data);
                            packData.iconData = data;
                            packData.iconWidth = dims[0];
                            packData.iconHeight = dims[1];
                            packData.iconFormat = 2; // 2=PNG
                        }
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

    private static int[] getPngDimensions(byte[] data) {
        if (data == null || data.length < 24) return new int[]{0, 0};
        if ((data[0] & 0xFF) != 0x89 || data[1] != 0x50 || data[2] != 0x4E || data[3] != 0x47) return new int[]{0, 0};
        int width = ((data[16] & 0xFF) << 24) | ((data[17] & 0xFF) << 16) | ((data[18] & 0xFF) << 8) | (data[19] & 0xFF);
        int height = ((data[20] & 0xFF) << 24) | ((data[21] & 0xFF) << 16) | ((data[22] & 0xFF) << 8) | (data[23] & 0xFF);
        return new int[]{width, height};
    }

    private static byte[] readModelFileBytes(Path file) throws IOException {
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

    private static RawYsmModel parseUploadedModel(byte[] raw, String source, ImportKind importKind) throws Exception {
        return switch (importKind) {
            case YSM -> parseBinaryModel(raw, source);
            case ZIP -> parseArchiveModel(raw, source);
            case BBMODEL -> parseBbModelImport(raw, source);
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported model import type for file: " + source);
        };
    }

    private static String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{EXT_YSM, EXT_ZIP, EXT_BBMODEL}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    private static ImportKind importKindFromFileName(String fileName) {
        if (fileName == null) {
            return ImportKind.UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_YSM)) {
            return ImportKind.YSM;
        }
        if (lower.endsWith(EXT_ZIP)) {
            return ImportKind.ZIP;
        }
        if (lower.endsWith(EXT_BBMODEL)) {
            return ImportKind.BBMODEL;
        }
        return ImportKind.UNKNOWN;
    }

    private static String extensionFor(ImportKind importKind) {
        return switch (importKind) {
            case ZIP -> EXT_ZIP;
            case BBMODEL -> EXT_BBMODEL;
            default -> EXT_YSM;
        };
    }

    private static ServerModelData processAndCacheModel(String modelId, RawYsmModel model, Path serverCacheDir, boolean isAuth, Set<String> validCacheFiles) {
        String sha256 = model.properties.sha256;
        if (sha256 == null || sha256.isEmpty()) return null;
        if (serverKey == null) {
            YesSteveModel.LOGGER.warn("[SM] serverKey not initialized yet, skipping model processing for: {}", modelId);
            return null;
        }

        try {
            long[] hashes = YsmCrypt.calculateModelHashes(sha256, serverKey);
            String cacheFileName = String.format("%016x%016x", hashes[0], hashes[1]);
            Path cacheFile = serverCacheDir.resolve(cacheFileName);
            if (!serverCacheDir.toFile().isDirectory()) {
                Files.createDirectories(serverCacheDir);
            }
            boolean needsUpdate = true;
            if (Files.exists(cacheFile)) {
                byte[] existingData = Files.readAllBytes(cacheFile);
                if (YsmCrypt.verifyServerCache(existingData, hashes[0], hashes[1]) && canReadServerCache(existingData, modelId)) {
                    needsUpdate = false;
                }
            }
            if (needsUpdate) {
                byte[] encryptedCache;
                try (YSMByteBuf serialized = YSMBinarySerializer.serialize(model, 32, true)) {
                    io.netty.buffer.ByteBuf raw = serialized.getRawBuf();
                    if (raw.hasArray()) {
                        int off = raw.arrayOffset() + raw.readerIndex();
                        int len = raw.readableBytes();
                        encryptedCache = YsmCrypt.encryptServerCache(raw.array(), off, len, serverKey, hashes[0], hashes[1]);
                    } else {
                        encryptedCache = YsmCrypt.encryptServerCache(serialized.toArray(), serverKey, hashes[0], hashes[1]);
                    }
                }
                Files.write(cacheFile, encryptedCache);
            }
            validCacheFiles.add(cacheFileName);

            boolean isCustomSkinModel = "misc/2_steve".equals(modelId) || "misc/1_alex".equals(modelId); // 对没错就是写死的

            return mapToDataClass(modelId, model, isAuth, isCustomSkinModel);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("Failed to process and cache model: " + modelId, e);
            return null;
        }
    }

    private static boolean canReadServerCache(byte[] existingData, String modelId) {
        try {
            YsmCrypt.readStrict(existingData, serverKey);
            return true;
        } catch (Exception e) {
            YesSteveModel.LOGGER.warn("[SM] Rebuilding unreadable server model cache: {}", modelId);
            return false;
        }
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

    public static void nativeSyncModels(UUID[] uuids, String[] playerNames, String[] modelIds, Object callback) {
        initRateLimit();
        YSMThreadPool.submitSync(() -> {
            try {
                MinecraftServer currentServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                if (currentServer == null) return;

                for (UUID uuid : uuids) {
                    PlayerSyncState state = syncStates.computeIfAbsent(uuid, k -> new PlayerSyncState());
                    state.allowedModels.clear();
                    state.allowedModels.addAll(CACHE_NAME_INFO.values());
                    state.step = 1;

                    // HandshakePing
//                    byte[] garbage = new byte[16 + SECURE_RANDOM_S.nextInt(48)];
//                    SECURE_RANDOM_S.nextBytes(garbage);
//                    byte[] payload = new byte[2 + garbage.length + 1];
//                    payload[0] = (byte)(garbage.length & 0xFF);
//                    payload[1] = (byte)((garbage.length >> 8) & 0xFF);
//                    System.arraycopy(garbage, 0, payload, 2, garbage.length);
//                    payload[2 + garbage.length] = 0x01;
//
//                    var result = YsmCrypt.encrypt(payload, K0_SERVER, true);
//                    state.key1 = result.nextKey();
//
//                    sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
                    int garbageLen = 16 + theRandom.nextInt(48);
                    byte[] garbage = new byte[garbageLen];
                    theRandom.nextBytes(garbage);

                    try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                        outBuf.writeGarbageHeader(garbageLen, garbage);
                        outBuf.writeByte((byte) 0x01);
                        YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), YsmCrypt.publicKey, true);
                        state.key1 = result.nextKey();

                        sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
                    }
                }
//                if (callback != null) onAuthDataReceived(null, callback);
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("[SM] Sync initiation failed", e);
            }
        });
    }

    private static void sendPacket03(UUID uuid, PlayerSyncState state) {
        int garbageLen = 16 + theRandom.nextInt(48);
        byte[] garbage = new byte[garbageLen];
        theRandom.nextBytes(garbage);

        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
            outBuf.writeGarbageHeader(garbageLen, garbage);

            outBuf.writeVarInt(3); // Type
            outBuf.writeVarLong(0L); // 這個決定了cache資料夾的名稱

            outBuf.getRawBuf().writeBytes(serverKey);
            outBuf.getRawBuf().writeBytes(state.clientKey);

            outBuf.writeVarInt(state.allowedModels.size());
            for (ServerModelData model : state.allowedModels) {
                String sha256 = model.getLoadedModelData().getModelHash();
                long[] hashes = YsmCrypt.calculateModelHashes(sha256, serverKey);
                outBuf.writeVarLong(hashes[0]);
                outBuf.writeVarLong(hashes[1]);
                outBuf.writeString(model.getModelId());
                outBuf.writeVarInt(model.isAuth() ? 1 : 0);
                outBuf.writeVarInt(model.isCustomSkinModel() ? 1 : 0);
                outBuf.writeVarInt(32); // format
            }

            outBuf.writeVarInt(packs.size());
            for (ServerPackData pack : packs.values()) {
                outBuf.writeString(pack.folderPath);

                // 寫入圖標資訊
                if (pack.iconData != null) {
                    outBuf.writeVarInt(1);
                    outBuf.writeByteArray(pack.iconData);
                    outBuf.writeVarInt(pack.iconWidth);
                    outBuf.writeVarInt(pack.iconHeight);
                    outBuf.writeVarInt(pack.iconFormat);
                    outBuf.writeVarInt(1); // unkImageData
                } else {
                    outBuf.writeVarInt(0);
                }

                // 寫入基礎資訊
                if (pack.name != null || pack.description != null) {
                    outBuf.writeVarInt(1);
                    outBuf.writeString(pack.name != null ? pack.name : "");
                    outBuf.writeString(pack.description != null ? pack.description : "");
                } else {
                    outBuf.writeVarInt(0);
                }

                // 寫入語言本地化
                if (pack.lang != null && !pack.lang.isEmpty()) {
                    outBuf.writeVarInt(pack.lang.size());
                    for (Map.Entry<String, Map<String, String>> langEntry : pack.lang.entrySet()) {
                        outBuf.writeString(langEntry.getKey());
                        outBuf.writeVarInt(langEntry.getValue().size());
                        for (Map.Entry<String, String> kv : langEntry.getValue().entrySet()) {
                            outBuf.writeString(kv.getKey());
                            outBuf.writeString(kv.getValue());
                        }
                    }
                } else {
                    outBuf.writeVarInt(0);
                }
            }

            outBuf.writeVarInt(0);  // \0

            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.clientNextKey, false);
            sendModelData(uuid, ByteBuffer.wrap(result.data()), new PendingTransfer());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void sendPacket05(UUID uuid, PlayerSyncState state, List<long[]> requestedHashes) {
        YSMThreadPool.submitSync(() -> {
            try {
                threadLimiter.acquire();

                PendingTransfer transfer = new PendingTransfer();

                for (long[] hashes : requestedHashes) {
                    long hash1 = hashes[0];
                    long hash2 = hashes[1];
                    String fileName = String.format("%016x%016x", hash1, hash2);
                    Path file = ServerModelManager.CACHE_SERVER.resolve(fileName);

                    if (!Files.exists(file)) continue;

                    byte[] fileData = Files.readAllBytes(file);
                    int totalSize = fileData.length;
                    int maxChunkSize = 30720;
                    int chunkCount = (totalSize + maxChunkSize - 1) / maxChunkSize;
                    int chunkSize = (totalSize + chunkCount - 1) / chunkCount;

                    int offset = 0;

                    while (offset < totalSize) {
                        int length = Math.min(chunkSize, totalSize - offset);

                        int garbageLen = 16 + theRandom.nextInt(48);
                        byte[] garbage = new byte[garbageLen];
                        theRandom.nextBytes(garbage);

                        try (YSMByteBuf outBuf = new YSMByteBuf(Unpooled.buffer())) {
                            outBuf.writeGarbageHeader(garbageLen, garbage);
                            outBuf.writeVarInt(5); // Type
                            outBuf.writeVarLong(hash1);
                            outBuf.writeVarLong(hash2);
                            outBuf.writeVarInt(totalSize);
                            outBuf.writeVarInt(offset);
                            outBuf.writeVarInt(length);
                            outBuf.getRawBuf().writeBytes(fileData, offset, length);
                            YsmCrypt.EncryptedPacket result = YsmCrypt.encrypt(outBuf.toArray(), state.key1, false);

//                            bandwidthLimiter.acquire(result.data().length); //TODO


                            // Stream chunks
                            boolean success = sendModelData(uuid, ByteBuffer.wrap(result.data()), transfer);
                            if (success) {
                                offset += length;
                            } else {
                                try { Thread.sleep(5); } catch (InterruptedException e) {}
                            }
                        }
                    }
                }
            } catch (Exception e) {
                YesSteveModel.LOGGER.error("Failed to send model chunks to " + uuid, e);
            } finally {
                threadLimiter.release();
            }
        });
    }

    public static void nativeExportModel(String modelID, @Nullable String extra, @Nullable Consumer<ExportResult> callback) {
        YSMThreadPool.submit(() -> {
            try {
                ServerModelData modelData = CACHE_NAME_INFO.get(modelID);
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
        return Optional.ofNullable(CACHE_NAME_INFO.get(str));
    }

    public static Map<String, ServerModelData> getServerModelInfo() {
        return CACHE_NAME_INFO;
    }

    public static Set<String> getAuthModels() {
        return AUTH_MODELS;
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
        if (!isModelUploadAllowed()) {
            return UploadStartResult.reject((byte) 6, "Model import disabled");
        }
        if (sender == null || !NetworkHandler.isPlayerConnected(sender)) {
            return UploadStartResult.reject((byte) 3, "No import permission");
        }
        String modelId = normalizeUploadedModelId(requestedModelId);
        ImportKind importKind = importKindFromFileName(fileName);
        if (modelId == null || importKind == ImportKind.UNKNOWN || sha256 == null || !sha256.matches("[0-9a-fA-F]{64}")) {
            return UploadStartResult.reject((byte) 5, "Invalid model id or hash");
        }
        int maxBytes = getModelUploadMaxBytes();
        if (totalBytes <= 0 || totalBytes > maxBytes) {
            return UploadStartResult.reject((byte) 2, "File exceeds server limit");
        }
        if (CACHE_NAME_INFO.containsKey(modelId) || uploadStates.values().stream().anyMatch(state -> state.modelId.equals(modelId))) {
            return UploadStartResult.reject((byte) 1, "Model ID already exists");
        }

        long uploadId;
        do {
            uploadId = theRandom.nextLong();
        } while (uploadId == 0L || uploadStates.containsKey(uploadId));

        ModelUploadState state = new ModelUploadState(uploadId, sender.getUUID(), modelId, fileName, importKind, totalBytes, sha256.toLowerCase(Locale.ROOT));
        uploadStates.put(uploadId, state);
        return new UploadStartResult(uploadId, (byte) 0, UPLOAD_CHUNK_SIZE, maxBytes, getModelUploadChunksPerTick(), "");
    }

    public static void receiveModelUploadChunk(ServerPlayer sender, long uploadId, int offset, byte[] data) {
        ModelUploadState state = uploadStates.get(uploadId);
        if (state == null || sender == null || !state.owner.equals(sender.getUUID())) {
            return;
        }
        state.touch();
        if (data == null || offset < 0 || offset + data.length > state.data.length || offset != state.receivedBytes) {
            state.failed = true;
            return;
        }
        System.arraycopy(data, 0, state.data, offset, data.length);
        state.receivedBytes += data.length;
    }

    public static UploadFinishResult finishModelUpload(ServerPlayer sender, long uploadId) {
        long finishPerfStart = PerformanceProfiler.start();
        ModelUploadState state = uploadStates.remove(uploadId);
        if (state == null || sender == null || !state.owner.equals(sender.getUUID())) {
            return UploadFinishResult.reject(uploadId, (byte) 4, "Session expired");
        }
        if (state.failed || state.receivedBytes != state.data.length) {
            return UploadFinishResult.reject(uploadId, (byte) 5, "Incomplete upload");
        }
        String actualSha256 = DigestUtil.sha256Hex(state.data);
        if (!state.sha256.equals(actualSha256)) {
            YesSteveModel.LOGGER.warn("[SM] Import transfer hash mismatch modelId={} file={} type={} declaredSha256={} actualSha256={} bytes={} received={}",
                    state.modelId, state.fileName, state.importKind, state.sha256, actualSha256, state.data.length, state.receivedBytes);
            return UploadFinishResult.reject(uploadId, (byte) 1, "Hash mismatch");
        }

        RawYsmModel rawModel;
        try {
            rawModel = parseUploadedModel(state.data, "import:" + state.fileName, state.importKind);
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to parse imported model modelId={} file={} type={} rawSha256={} bytes={}",
                    state.modelId, state.fileName, state.importKind, actualSha256, state.data.length, e);
            return UploadFinishResult.reject(uploadId, (byte) 2, e.getMessage());
        }

        try {
            if (processAndCacheModel(state.modelId, rawModel, CACHE_SERVER, false, new HashSet<>()) == null) {
                return UploadFinishResult.reject(uploadId, (byte) 2, "Server failed to cache model");
            }
            Path target = CUSTOM.resolve(state.modelId + extensionFor(state.importKind)).normalize();
            Path customRoot = CUSTOM.toAbsolutePath().normalize();
            Path absoluteTarget = target.toAbsolutePath().normalize();
            if (!absoluteTarget.startsWith(customRoot)) {
                return UploadFinishResult.reject(uploadId, (byte) 6, "Server rejected write");
            }
            Files.createDirectories(absoluteTarget.getParent());
            Path temp = Files.createTempFile(absoluteTarget.getParent(), absoluteTarget.getFileName().toString(), ".tmp");
            Files.write(temp, state.data);
            try {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absoluteTarget, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            YesSteveModel.LOGGER.error("[SM] Failed to store imported model: " + state.modelId, e);
            return UploadFinishResult.reject(uploadId, (byte) 3, e.getMessage());
        }

        ModelLoadResult reloadResult = reloadModelsAfterImport();
        if (!reloadResult.isSuccess()) {
            Component errorMessage = reloadResult.getErrorMessage();
            return UploadFinishResult.reject(uploadId, (byte) 8, errorMessage == null ? "Imported model scan failed" : errorMessage.getString());
        }
        if (!reloadResult.getModelDefinitions().containsKey(state.modelId)) {
            YesSteveModel.LOGGER.warn("[SM] Imported model was written but not visible after scan: modelId={} file={} type={} rawSha256={} contentHash={}",
                    state.modelId, state.fileName, state.importKind, actualSha256, rawModel.properties.sha256);
            return UploadFinishResult.reject(uploadId, (byte) 8, "Imported model is not visible after scan");
        }

        YesSteveModel.LOGGER.info("[SM] Imported model '{}' from {} as {}", state.modelId, sender.getGameProfile().getName(), state.importKind);
        PerformanceProfiler.logElapsed("server_upload_finish", state.modelId, finishPerfStart,
                "bytes=" + state.data.length + " type=" + state.importKind);
        long[] hashes = YsmCrypt.calculateModelHashes(rawModel.properties.sha256, serverKey);
        return new UploadFinishResult(uploadId, (byte) 0, state.modelId, hashes[0], hashes[1], "");
    }

    private static ModelLoadResult reloadModelsAfterImport() {
        long perfStart = PerformanceProfiler.start();
        Map<String, ServerModelData> loadedModels = new LinkedHashMap<>();
        Set<String> authIds = new HashSet<>();
        Set<String> validCacheFiles = new HashSet<>();

        try {
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

    private static void syncLoadedModelsToPlayers() {
        MinecraftServer currentServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
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
        uploadStates.entrySet().removeIf(entry -> now - entry.getValue().lastTouchedMs > UPLOAD_SESSION_TIMEOUT_MS);
    }

    public static void requestPlayerAuth(ServerPlayer serverPlayer, @Nullable Consumer<UUIDComponentData> consumer) {
        MinecraftServer currentServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
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
            MinecraftServer currentServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
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
        MinecraftServer currentServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        initialized = true;
        if (currentServer != null) {
            currentServer.execute(() -> {
                if (modelLoadResult.isSuccess()) {
                    IntOpenHashSet intOpenHashSet = new IntOpenHashSet(modelLoadResult.getModelDefinitions().size());
                    for (ServerModelData data : modelLoadResult.getModelDefinitions().values()) {
                        intOpenHashSet.add(data.getLoadedModelData().getHashId());
                    }
                    CACHE_NAME_INFO = modelLoadResult.getModelDefinitions();
                    modelHashSet = intOpenHashSet;
                    AUTH_MODELS = modelLoadResult.getAuthModelIds();
                }
                if (consumer != null) {
                    YSMThreadPool.submit(() -> consumer.accept(modelLoadResult));
                }
            });
            return;
        }
        if (modelLoadResult.isSuccess()) {
            CACHE_NAME_INFO = modelLoadResult.getModelDefinitions();
            AUTH_MODELS = modelLoadResult.getAuthModelIds();
        }
        if (consumer != null) {
            consumer.accept(modelLoadResult);
        }
    }

    public static void syncModelToPlayer(UUID uuid) {
        nativeSendModelData(uuid, null);
    }

    private static Connection getPlayerConnection(UUID uuid) {
        ServerPlayer player;
        MinecraftServer currentServer = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (currentServer == null || (player = currentServer.getPlayerList().getPlayer(uuid)) == null) {
            return null;
        }
        ServerGamePacketListenerImpl serverGamePacketListenerImpl = player.connection;
        if (!serverGamePacketListenerImpl.isAcceptingMessages() || !serverGamePacketListenerImpl.getClass().equals(ServerGamePacketListenerImpl.class)) {
            return null;
        }
        return ((ServerCommonPacketListenerImplAccessor) serverGamePacketListenerImpl).ysm$getConnection();
    }

    private static boolean sendModelData(UUID uuid, ByteBuffer byteBuffer, PendingTransfer pendingTransfer) {
        Connection connection = getPlayerConnection(uuid);
        if (connection != null) {
            // Find the ServerPlayer from UUID
            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null) return false;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) return false;
            // Send via NetworkHandler which uses PacketDistributor internally
            NetworkHandler.sendToClientPlayer(new S2CModelSyncPayload(byteBuffer), player);
            return true;
        }
        return false;
    }

    private static Object createModelPacket(ByteBuffer byteBuffer) {
        // Return the payload directly; callers should use NetworkHandler.send methods
        return new S2CModelSyncPayload(byteBuffer);
    }

    private static boolean sendPacketToPlayer(UUID uuid, Object obj, PendingTransfer pendingTransfer) {
        Connection connection = getPlayerConnection(uuid);
        if (connection != null) {
            return sendPacketReliably(connection, obj, pendingTransfer);
        }
        return false;
    }

    private static boolean sendPacketReliably(Connection connection, Object obj, PendingTransfer pendingTransfer) {
        if (!pendingTransfer.hasStarted) {
            pendingTransfer.hasStarted = true;
            pendingTransfer.pendingBytes = ((ConnectionAccessor) connection).ysm$getChannel().unsafe().outboundBuffer().totalPendingWriteBytes() + 65536;
        }

        final AtomicInteger atomicInteger = new AtomicInteger(0);
        while (connection.isConnected()) {
            if (((ConnectionAccessor) connection).ysm$getChannel().unsafe().outboundBuffer().size() > pendingTransfer.pendingBytes) {
                if (!YSMThreadPool.awaitTermination(10)) {
                    return false;
                }
            } else {
                try {
                    connection.send((Packet<?>) obj, new PacketSendListener() {
                        public void onSuccess() {
                            atomicInteger.set(1);
                            PacketSendListener.super.onSuccess();
                        }

                        @Nullable
                        public Packet<?> onFailure() {
                            atomicInteger.set(-1);
                            return null;
                        }
                    });
                    while (atomicInteger.get() == 0) {
                        if (!YSMThreadPool.awaitTermination(5)) {
                            return false;
                        }
                    }
                    if (atomicInteger.get() == 1) {
                        return true;
                    }
                    if (!YSMThreadPool.awaitTermination(100)) {
                        return false;
                    }
                    atomicInteger.set(0);
                } catch (Throwable th) {
                    th.printStackTrace();
                    return false;
                }
            }
        }
        return false;
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
        ServerModelData modelData = CACHE_NAME_INFO.get(modelId);
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
        NetworkOnlineDebugLog.info("validatePlayerModel: {} cacheEmpty={} cacheSize={}",
                serverPlayer.getName().getString(), CACHE_NAME_INFO.isEmpty(), CACHE_NAME_INFO.size());
        if (!CACHE_NAME_INFO.isEmpty()) {
            ModelInfoCapability.get(serverPlayer).ifPresent(modelInfoCap -> {
                AuthModelsCapability.get(serverPlayer).ifPresent(authModelsCap -> {
                    if (authModelsCap.getAuthModels().removeIf(str -> !CACHE_NAME_INFO.containsKey(str))) {
                        NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(authModelsCap.getAuthModels()), serverPlayer);
                    }
                    String modelId = modelInfoCap.getModelId();
                    boolean inCache = getServerModelInfo().containsKey(modelId);
                    boolean isAuth = AUTH_MODELS.contains(modelId);
                    boolean hasAuth = authModelsCap.containsModel(modelId);
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
                        } else {
                            NetworkOnlineDebugLog.info("validate: OK modelId={}", modelId);
                        }
                    }
                    if (changed) {
                        PlayerModelSelectionStore.saveCurrentSelection(serverPlayer, modelInfoCap);
                        PlayerDataSaveBridge.save(serverPlayer);
                    }
                    modelInfoCap.retainAnimationKeys(modelHashSet);
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

    private static class ModelUploadState {
        private final long uploadId;
        private final UUID owner;
        private final String modelId;
        private final String fileName;
        private final ImportKind importKind;
        private final byte[] data;
        private final String sha256;
        private int receivedBytes;
        private boolean failed;
        private long lastTouchedMs;

        private ModelUploadState(long uploadId, UUID owner, String modelId, String fileName, ImportKind importKind, int totalBytes, String sha256) {
            this.uploadId = uploadId;
            this.owner = owner;
            this.modelId = modelId;
            this.fileName = fileName == null ? "" : fileName;
            this.importKind = importKind;
            this.data = new byte[totalBytes];
            this.sha256 = sha256;
            touch();
        }

        private void touch() {
            this.lastTouchedMs = System.currentTimeMillis();
        }
    }

    private enum ImportKind {
        YSM,
        ZIP,
        BBMODEL,
        UNKNOWN
    }

    private static class PendingTransfer {
        public long pendingBytes;

        public boolean hasStarted = false;

        private PendingTransfer() {
        }
    }
}
