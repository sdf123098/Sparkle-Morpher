package com.micaftic.morpher.core.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.LongUnaryOperator;

/**
 * R8-2 AtomicFileMover — 原子替换目标文件（从 ServerModelManager.moveWithRetry 抽取）。
 *
 * <p>Windows 上目标文件被并发打开（读取/发送）时 REPLACE 会瞬时 AccessDenied，
 * 按指数退避重试几次；目标文件始终是完整内容（旧或新），不会出现半截文件。
 * 重试耗尽后抛 IOException，由调用方决定（如跳过缓存写入、按需重建）。</p>
 *
 * <p>纯 Java（零 MC import），退避策略与重试次数可注入供测试。</p>
 */
public final class AtomicFileMover {

    private static final int DEFAULT_MAX_ATTEMPTS = 6;

    private AtomicFileMover() {
    }

    /** 默认配置：最多 6 次尝试，退避 20/40/80/160/320/640ms。 */
    public static void moveWithRetry(Path source, Path target) throws IOException {
        moveWithRetry(source, target, DEFAULT_MAX_ATTEMPTS, attempt -> 20L << attempt);
    }

    /**
     * 原子替换 target（失败按退避重试，耗尽后抛 IOException）。
     *
     * @param maxAttempts  最多尝试次数（含首次）
     * @param backoffMillis 每次失败后的等待毫秒数（按 attempt 序号 0 起）；null 表示不等待
     */
    static void moveWithRetry(Path source, Path target, int maxAttempts,
                              @org.jetbrains.annotations.Nullable LongUnaryOperator backoffMillis) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException e) {
                last = e;
                if (backoffMillis == null) {
                    continue;
                }
                try {
                    Thread.sleep(backoffMillis.applyAsLong(attempt));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }
}
