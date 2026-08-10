package com.micaftic.morpher.util;

import java.util.concurrent.Future;

/**
 * 历史统一线程池门面（R2.1 已由 {@link SmExecutors} 接管）。
 *
 * 保留类与 API 兼容（调用点逐步迁移），实现全部委托 {@link SmExecutors}：
 * <ul>
 *   <li>{@link #submit}/{@link #submitCallable} → BACKGROUND 池</li>
 *   <li>{@link #submitSync} → NETWORK_IO 池（限速发包/同步任务）</li>
 * </ul>
 */
public final class YSMThreadPool {

    private YSMThreadPool() {
    }

    public static Future<?> submit(Runnable runnable) {
        return SmExecutors.pool(SmExecutors.Pool.BACKGROUND).submit(runnable);
    }

    public static <T> Future<T> submitCallable(java.util.concurrent.Callable<T> callable) {
        return SmExecutors.pool(SmExecutors.Pool.BACKGROUND).submit(callable);
    }

    public static Future<?> submitSync(Runnable runnable) {
        return SmExecutors.pool(SmExecutors.Pool.NETWORK_IO).submit(runnable);
    }

    /**
     * 退避等待毫秒数。仅用于发送重试等 backoff 场景（历史实现是 Thread.sleep）。
     * 注意：这不是线程池终止等待；真正关闭请使用 ExecutorService 的 shutdown/awaitTermination。
     */
    public static boolean sleepMillis(int i) {
        try {
            Thread.sleep(i);
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /** @deprecated 语义实为退避 sleep，非线程池终止等待；请改用 {@link #sleepMillis(int)}。 */
    @Deprecated
    public static boolean awaitTermination(int i) {
        return sleepMillis(i);
    }
}
