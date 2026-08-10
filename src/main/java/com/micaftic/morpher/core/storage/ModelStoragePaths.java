package com.micaftic.morpher.core.storage;

import java.nio.file.Path;

/**
 * R3.1 模型存储路径集中点（从 ServerModelManager 搬出）。
 *
 * 磁盘布局（相对 mod 配置目录）：
 * <pre>
 *   sparkle_morpher/
 *     built/   模型站内置模型（只读副本）
 *     custom/  用户导入模型（.ysm/.zip/.bbmodel）
 *     auth/    授权（付费）模型
 *     export/  导出目录
 *     cache/
 *       server_index         服务器模型索引文件
 *       server/              服务器模型缓存
 *       client/              客户端模型缓存
 *       .bbmodel_import_cache_identity   bbmodel 导入缓存标识
 * </pre>
 *
 * 平台差异：Fabric/26.x 用 Platform.getConfigFolder()，NeoForge 1.21.1 用 FMLPaths.CONFIGDIR——
 * 因此本类不静态引用任何平台类，由各分支的生产入口在 mod 初始化时调用 {@link #init(Path)} 注入根目录；
 * 未初始化即访问会抛 {@link IllegalStateException}（初始化顺序违规）。
 * 测试可注入临时根目录（{@link #withRoot(Path)}）。
 */
public final class ModelStoragePaths {

    /** 生产根目录（mod 初始化时经 {@link #init} 注入）。 */
    private static volatile Path root;

    /** 测试专用覆盖（优先于 root；null 表示未覆盖）。 */
    private static volatile Path testRootOverride;

    private ModelStoragePaths() {
    }

    /** 生产初始化：注入配置根目录（config 目录下的 sparkle_morpher 文件夹）。 */
    public static void init(Path rootFolder) {
        root = rootFolder;
    }

    /** 根目录（config/sparkle_morpher）。测试可用 {@link #withRoot(Path)} 覆盖。 */
    public static Path folder() {
        Path override = testRootOverride;
        if (override != null) {
            return override;
        }
        Path r = root;
        if (r == null) {
            throw new IllegalStateException("ModelStoragePaths not initialized — call init() in mod entrypoint");
        }
        return r;
    }

    // ---- 模型仓库 ----
    public static Path built() {
        return folder().resolve("built");
    }

    public static Path custom() {
        return folder().resolve("custom");
    }

    public static Path auth() {
        return folder().resolve("auth");
    }

    public static Path export() {
        return folder().resolve("export");
    }

    // ---- 缓存 ----
    public static Path cache() {
        return folder().resolve("cache");
    }

    public static Path cacheServerIndexFile() {
        return cache().resolve("server_index");
    }

    public static Path cacheServer() {
        return cache().resolve("server");
    }

    public static Path cacheClient() {
        return cache().resolve("client");
    }

    public static Path cacheBbmodelImportIdentityFile() {
        return cache().resolve(".bbmodel_import_cache_identity");
    }

    // ---- classpath 内置模型资源 ----
    public static String builtinResourceRoot() {
        return "/assets/" + com.micaftic.morpher.YesSteveModel.MOD_ID + "/builtin/";
    }

    public static String builtinResourceIndex() {
        return builtinResourceRoot() + "index.txt";
    }

    /** 测试专用：把根目录覆盖为临时目录（传 null 恢复默认）。 */
    public static void withRoot(Path rootDir) {
        testRootOverride = rootDir;
    }
}
