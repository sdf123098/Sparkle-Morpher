package com.micaftic.morpher.core.model.catalog;

import com.micaftic.morpher.util.ModelIdUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * R8 LocalModelScanner — 本地模型来源遍历发现（从 ServerModelManager.scanDirectoryModels 抽取）。
 *
 * <p>职责：遍历本地模型 baseDir（builtin/custom/auth），发现模型文件夹与模型文件
 * （.ysm/.zip/.bbmodel），归一 modelId 后逐条回调 {@link Sink}。解析与缓存由调用方
 * 在回调内完成——scanner 只负责"发现"，纯 Java 可测。</p>
 *
 * <p>判定规则（与原实现逐字节一致）：</p>
 * <ul>
 *   <li>模型文件夹（注入 detector，如 {@code YSMFolderDeserializer.isModelFolder}）
 *       → {@link Kind#FOLDER}，且 SKIP_SUBTREE（不深入其内部文件）</li>
 *   <li>文件按扩展名 → {@link Kind#YSM}/{@link Kind#ZIP}/{@link Kind#BBMODEL}；未知扩展名忽略</li>
 *   <li>modelId 经 {@link #normalizeModelId} 归一，无效（空/纯符号）跳过</li>
 * </ul>
 */
public final class LocalModelScanner {

    private static final String EXT_YSM = ".ysm";
    private static final String EXT_ZIP = ".zip";
    private static final String EXT_BBMODEL = ".bbmodel";

    private LocalModelScanner() {
    }

    /** 命中的模型来源类型。 */
    public enum Kind {
        FOLDER, YSM, ZIP, BBMODEL, UNKNOWN
    }

    /** 一次扫描命中：归一后的 modelId + 来源路径 + 类型。 */
    public record Hit(String modelId, Path source, Kind kind) {
    }

    /** 命中回调；抛出的 IOException 会传播给 scan 调用方（单条目失败处理由调用方决定）。 */
    @FunctionalInterface
    public interface Sink {
        void accept(Hit hit) throws IOException;
    }

    /**
     * 遍历 baseDir 发现本地模型来源。
     *
     * @param baseDir             本地模型根目录（不存在时静默返回）
     * @param modelFolderDetector 模型文件夹判定（YSMFolderDeserializer.isModelFolder）
     * @param sink                逐命中回调（深度优先顺序）
     */
    public static void scan(Path baseDir, Predicate<Path> modelFolderDetector, Sink sink) throws IOException {
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return;
        }
        Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.equals(baseDir)) {
                    return FileVisitResult.CONTINUE;
                }
                if (modelFolderDetector.test(dir)) {
                    String modelId = normalizeModelId(baseDir.relativize(dir).toString().replace('\\', '/'));
                    if (modelId != null) {
                        sink.accept(new Hit(modelId, dir, Kind.FOLDER));
                    }
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                Kind kind = kindFromFileName(fileName);
                if (kind == Kind.UNKNOWN) {
                    return FileVisitResult.CONTINUE;
                }
                String modelId = normalizeModelId(baseDir.relativize(file).toString().replace('\\', '/'));
                if (modelId != null) {
                    sink.accept(new Hit(modelId, file, kind));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 按文件扩展名识别导入类型（大小写不敏感）；未知/null 返回 UNKNOWN。 */
    public static Kind kindFromFileName(@Nullable String fileName) {
        if (fileName == null) {
            return Kind.UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_YSM)) {
            return Kind.YSM;
        }
        if (lower.endsWith(EXT_ZIP)) {
            return Kind.ZIP;
        }
        if (lower.endsWith(EXT_BBMODEL)) {
            return Kind.BBMODEL;
        }
        return Kind.UNKNOWN;
    }

    /** 导入类型的扩展名（FOLDER/UNKNOWN 返回空串）。 */
    public static String extensionFor(Kind kind) {
        return switch (kind) {
            case YSM -> EXT_YSM;
            case ZIP -> EXT_ZIP;
            case BBMODEL -> EXT_BBMODEL;
            case FOLDER, UNKNOWN -> "";
        };
    }

    /** 去掉导入文件扩展名（.ysm/.zip/.bbmodel，大小写不敏感）。 */
    public static String stripImportExtension(String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{EXT_YSM, EXT_ZIP, EXT_BBMODEL}) {
            if (lower.endsWith(extension)) {
                return modelId.substring(0, modelId.length() - extension.length());
            }
        }
        return modelId;
    }

    /**
     * 扫描用 modelId 归一：去扩展名 → ModelIdUtil 归一化；无效（空 / 含非法字符 / 纯符号）返回 null。
     */
    @Nullable
    public static String normalizeModelId(@Nullable String modelId) {
        String normalized = ModelIdUtil.normalizeImportModelId(stripImportExtension(modelId == null ? "" : modelId));
        if (!ModelIdUtil.isValidModelId(normalized) || !ModelIdUtil.hasLetterOrNumber(normalized)) {
            return null;
        }
        return normalized;
    }
}
