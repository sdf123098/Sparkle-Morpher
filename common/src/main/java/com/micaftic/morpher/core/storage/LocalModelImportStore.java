package com.micaftic.morpher.core.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * R7.1 LocalModelImportStore — 本地模型导入持久化（从 ClientModelManager 抽取）。
 *
 * <p>职责：把导入的模型字节安全地写入自定义模型目录：</p>
 * <ul>
 *   <li>路径沙箱：modelId 拼出的目标必须落在 customRoot 内（`..`/绝对路径拒绝）</li>
 *   <li>原子写：同目录 .tmp + ATOMIC_MOVE（不支持时回退普通 move），失败清理临时文件</li>
 *   <li>扩展名归一：按文件名识别 .ysm/.zip/.bbmodel/.gltf/.glb，未知回退 .ysm</li>
 *   <li>sibling 清理：同一 modelId 的其他扩展名旧文件删除（如 .zip 导入后清 .ysm）</li>
 * </ul>
 *
 * <p>纯 Java（零 MC import），customRoot 构造注入 → JVM 单测可跑真实逻辑。</p>
 */
public final class LocalModelImportStore {

    private static final String[] IMPORT_EXTENSIONS = {".ysm", ".zip", ".bbmodel", ".gltf", ".glb"};

    private final Path customRoot;
    private final MoveAction preparedMove;
    private final DeleteAction siblingDelete;

    public LocalModelImportStore(Path customRoot) {
        this(customRoot, LocalModelImportStore::moveAtomically, Files::deleteIfExists);
    }

    LocalModelImportStore(Path customRoot, MoveAction preparedMove, DeleteAction siblingDelete) {
        this.customRoot = customRoot;
        this.preparedMove = preparedMove;
        this.siblingDelete = siblingDelete;
    }

    @FunctionalInterface
    interface MoveAction {
        void move(Path source, Path target) throws IOException;
    }

    @FunctionalInterface
    interface DeleteAction {
        boolean deleteIfExists(Path path) throws IOException;
    }

    /** A committed source plus any old sibling paths whose cleanup can be retried. */
    public record CommitResult(Path persistedPath, List<Path> cleanupPending) {
        public CommitResult {
            cleanupPending = List.copyOf(cleanupPending);
        }
    }

    /**
     * 持久化导入的模型字节。
     *
     * @param modelId  模型 key（用于拼目标文件名；含路径逃逸片段时拒绝）
     * @param fileName 原始文件名（用于识别扩展名）
     * @param data     模型字节
     * @return 落盘的目标路径；modelId/data 为空返回 null
     * @throws IOException 写入失败 / 目标逃逸 customRoot
     */
    public Path persist(String modelId, String fileName, byte[] data) throws IOException {
        if (modelId == null || modelId.isBlank() || data == null) {
            return null;
        }
        String extension = importExtension(fileName);
        if (extension.isBlank()) {
            extension = ".ysm";
        }
        Path target = customRoot.resolve(modelId + extension).normalize();
        if (!isInside(customRoot, target)) {
            throw new IOException("Invalid import target: " + modelId);
        }
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.write(temp, data);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            removeSiblingImportFiles(modelId, target);
            return target;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /** Stage import bytes under a recognizable extension without touching the current import. */
    public PreparedImport prepare(String modelId, String fileName, byte[] data) throws IOException {
        if (modelId == null || modelId.isBlank() || data == null) return null;
        String extension = importExtension(fileName);
        if (extension.isBlank()) extension = ".ysm";
        Path target = customRoot.resolve(modelId + extension).normalize();
        if (!isInside(customRoot, target)) throw new IOException("Invalid import target: " + modelId);
        Files.createDirectories(target.getParent());
        Path staged = Files.createTempFile(target.getParent(), target.getFileName().toString() + ".", extension);
        try {
            Files.write(staged, data);
            return new PreparedImport(this, modelId, fileName, staged);
        } catch (IOException failure) {
            Files.deleteIfExists(staged);
            throw failure;
        }
    }

    public static final class PreparedImport implements AutoCloseable {
        private final LocalModelImportStore store;
        private final String modelId;
        private final String fileName;
        private final Path stagedPath;
        private boolean finished;

        private PreparedImport(LocalModelImportStore store, String modelId, String fileName, Path stagedPath) {
            this.store = store;
            this.modelId = modelId;
            this.fileName = fileName;
            this.stagedPath = stagedPath;
        }

        /** File suitable for parsing; close without commit discards it and leaves prior imports intact. */
        public Path path() {
            if (finished) throw new IllegalStateException("Prepared import is already finished");
            return stagedPath;
        }

        /** Persist validated bytes and apply the existing sibling replacement policy. */
        public CommitResult commit() throws IOException {
            if (finished) throw new IllegalStateException("Prepared import is already finished");
            Path persisted = store.commitPrepared(modelId, fileName, stagedPath);
            finished = true;
            List<Path> cleanupPending = store.removeSiblingImportFilesBestEffort(modelId, persisted);
            try {
                Files.deleteIfExists(stagedPath);
            } catch (IOException | SecurityException cleanupFailure) {
                cleanupPending.add(stagedPath);
            }
            return new CommitResult(persisted, cleanupPending);
        }

        @Override
        public void close() throws IOException {
            if (!finished) {
                finished = true;
                Files.deleteIfExists(stagedPath);
            }
        }
    }

    private Path commitPrepared(String modelId, String fileName, Path stagedPath) throws IOException {
        String extension = importExtension(fileName);
        if (extension.isBlank()) extension = ".ysm";
        Path target = customRoot.resolve(modelId + extension).normalize();
        if (!isInside(customRoot, target)) throw new IOException("Invalid import target: " + modelId);
        preparedMove.move(stagedPath, target);
        return target;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** 识别导入文件扩展名（.ysm/.zip/.bbmodel/.gltf/.glb，大小写不敏感）；未知返回空串。 */
    public static String importExtension(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        for (String extension : IMPORT_EXTENSIONS) {
            if (lower.endsWith(extension)) {
                return extension;
            }
        }
        return "";
    }

    /** 目标路径是否落在 root 内（词法规范化比较）。 */
    public static boolean isInside(Path root, Path path) {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        Path absolutePath = path.toAbsolutePath().normalize();
        return absolutePath.startsWith(absoluteRoot);
    }

    /** 删除同一 modelId 的其他扩展名旧文件（保留 keepTarget）。 */
    private void removeSiblingImportFiles(String modelId, Path keepTarget) throws IOException {
        for (String extension : IMPORT_EXTENSIONS) {
            Path sibling = customRoot.resolve(modelId + extension).normalize();
            if (isInside(customRoot, sibling)
                    && !sibling.toAbsolutePath().normalize().equals(keepTarget.toAbsolutePath().normalize())) {
                Files.deleteIfExists(sibling);
            }
        }
    }

    private List<Path> removeSiblingImportFilesBestEffort(String modelId, Path keepTarget) {
        List<Path> cleanupPending = new ArrayList<>();
        for (String extension : IMPORT_EXTENSIONS) {
            Path sibling = customRoot.resolve(modelId + extension).normalize();
            if (isInside(customRoot, sibling)
                    && !sibling.toAbsolutePath().normalize().equals(keepTarget.toAbsolutePath().normalize())) {
                try {
                    siblingDelete.deleteIfExists(sibling);
                } catch (IOException | SecurityException cleanupFailure) {
                    cleanupPending.add(sibling);
                }
            }
        }
        return cleanupPending;
    }
}
