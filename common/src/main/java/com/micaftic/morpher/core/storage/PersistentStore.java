package com.micaftic.morpher.core.storage;

import com.micaftic.morpher.util.SmLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * R3.4 持久化存储基元：原子写 + schema 版本 + 损坏备份。
 *
 * 语义（不改变既有文件格式——迁移期 facade）：
 * <ul>
 *   <li>写入：先写同目录临时文件（{name}.tmp），再原子移动覆盖目标；崩溃/断电不留半截文件。</li>
 *   <li>schema 版本：文件首行约定 <code>#sparkle_morpher_schema=N</code>；不匹配时由调用方
 *       {@link #migrate} 处理，默认备份后重写。</li>
 *   <li>损坏备份：解析失败的文件在首次写入前先改名 <code>{name}.corrupt-{yyyyMMdd-HHmmss}</code> 留存。</li>
 *   <li>日志：经 {@link SmLog}（R1.4 基建）。</li>
 * </ul>
 *
 * 测试：构造传入任意 Path（无需 Platform）；见 PersistentStoreTest。
 */
public final class PersistentStore {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path file;

    public PersistentStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** 原子写入文本内容（UTF-8）。 */
    public void write(String content) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = Files.createTempFile(parent, absolute.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temp);
            }
        }
    }

    /** 读取全部内容；文件不存在返回 null。 */
    public String read() throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 损坏文件备份：把当前文件改名 <code>{name}.corrupt-{ts}</code>（不存在则忽略）。 */
    public Path backupCorrupt() throws IOException {
        if (!Files.exists(file)) {
            return null;
        }
        Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + LocalDateTime.now().format(TS));
        Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
        SmLog.warn("STORAGE", "corrupt file backed up: " + file + " -> " + backup);
        return backup;
    }
}
